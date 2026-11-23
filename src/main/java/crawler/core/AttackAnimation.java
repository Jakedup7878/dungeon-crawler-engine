package crawler.core;

/**
 * How an attack looks and sounds: an animation strip played over whoever is hit, and a
 * sound effect. Classes and enemies refer to one by its id.
 *
 * @param image the animation strip, or null for the built-in look (a slash, or a claw for enemies)
 * @param sound a WAV file played as the attack lands, or null for silence
 * @param size  how big it is drawn, in percent of the target's height (100 = as tall as the target)
 */
public record AttackAnimation(String id, String name, SpriteRef image, String sound, int size) {
    public static final int MIN_SIZE = 25;
    public static final int MAX_SIZE = 400;

    public AttackAnimation {
        id = id == null ? "" : id.strip();
        name = name == null ? "" : name.strip();
        sound = sound == null || sound.isBlank() ? null : sound.strip();
        size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }

    public static AttackAnimation create(String id, String name) {
        return new AttackAnimation(id, name, null, null, 100);
    }

    public AttackAnimation withFiles(SpriteRef newImage, String newSound) {
        return new AttackAnimation(id, name, newImage, newSound, size);
    }
}
