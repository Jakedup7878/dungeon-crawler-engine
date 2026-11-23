package crawler.core;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A published game's project packed into one encrypted file, {@code game.pak}, so players
 * can't open, copy or change its maps, pictures, sounds and data.
 *
 * <p>The layout: {@code DCPK}, a version byte, the masked key (32 bytes), the IV (12 bytes),
 * then the project's files as a ZIP, encrypted with AES-256-GCM. GCM also notices any change
 * to the file, so a pack that has been tampered with refuses to load.
 *
 * <p><b>How strong is it?</b> The game has to decrypt its own data to run, so the key travels
 * with the game (masked, not secret). That stops people from simply opening or editing the
 * files, but someone determined who studies this engine's code can get them out. No game that
 * runs offline can do better; this is the kind of protection game engines offer.
 */
public final class GamePack {
    public static final String FILE_NAME = "game.pak";
    private static final byte[] MAGIC = {'D', 'C', 'P', 'K'};
    private static final int VERSION = 1;
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int HEADER_BYTES = MAGIC.length + 1 + KEY_BYTES + IV_BYTES;
    private static final byte[] MASK_SEED = "crawler.GamePack.v1".getBytes(StandardCharsets.UTF_8);

    private GamePack() {
    }

    /** Packs every file in a project folder, except hidden ones (names starting with a dot). */
    public static byte[] pack(Path projectRoot) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                Path relative = projectRoot.relativize(file);
                boolean hidden = false;
                for (Path part : relative) {
                    hidden |= part.toString().startsWith(".");
                }
                if (!hidden) {
                    files.put(relative.toString().replace('\\', '/'), Files.readAllBytes(file));
                }
            }
        }
        if (!files.containsKey(GameProject.FILE_NAME)) {
            throw new IOException(projectRoot + " isn't a project: it has no " + GameProject.FILE_NAME + ".");
        }
        return pack(files);
    }

    /** Packs files (by path, with forward slashes) with a new random key. */
    public static byte[] pack(Map<String, byte[]> files) throws IOException {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[KEY_BYTES];
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(key);
        random.nextBytes(iv);
        try {
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, key, iv).doFinal(zip(files));
            ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_BYTES + encrypted.length);
            out.write(MAGIC);
            out.write(VERSION);
            out.write(xor(key, mask(iv)));
            out.write(iv);
            out.write(encrypted);
            return out.toByteArray();
        } catch (GeneralSecurityException e) {
            throw new IOException("Couldn't encrypt the game: " + e.getMessage(), e);
        }
    }

    /**
     * Unpacks a pack's files.
     *
     * @throws IOException if it isn't a pack, comes from a newer version, or was damaged or changed
     */
    public static Map<String, byte[]> unpack(byte[] pack) throws IOException {
        if (pack.length < HEADER_BYTES + TAG_BITS / 8 || !Arrays.equals(Arrays.copyOf(pack, MAGIC.length), MAGIC)) {
            throw new IOException("This isn't a game data file.");
        }
        if (pack[MAGIC.length] != VERSION) {
            throw new IOException("This game data file was made by a newer version of the engine.");
        }
        int at = MAGIC.length + 1;
        byte[] maskedKey = Arrays.copyOfRange(pack, at, at + KEY_BYTES);
        byte[] iv = Arrays.copyOfRange(pack, at + KEY_BYTES, HEADER_BYTES);
        try {
            byte[] zip = cipher(Cipher.DECRYPT_MODE, xor(maskedKey, mask(iv)), iv)
                    .doFinal(pack, HEADER_BYTES, pack.length - HEADER_BYTES);
            return unzip(zip);
        } catch (AEADBadTagException e) {
            throw new IOException("The game's data file is damaged or has been changed, so it can't be loaded.");
        } catch (GeneralSecurityException e) {
            throw new IOException("Couldn't decrypt the game: " + e.getMessage(), e);
        }
    }

    /** A published game's files, read from its pack. */
    public static ProjectFiles read(Path packFile) throws IOException {
        return ProjectFiles.of(unpack(Files.readAllBytes(packFile)));
    }

    private static Cipher cipher(int mode, byte[] key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(MAGIC);
        return cipher;
    }

    /** Hides the key in the file; anyone with this engine's code can undo it (see the class comment). */
    private static byte[] mask(byte[] iv) throws IOException {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(MASK_SEED);
            return sha.digest(iv);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i]);
        }
        return out;
    }

    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory()) {
                    files.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        return files;
    }
}
