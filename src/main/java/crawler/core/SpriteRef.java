package crawler.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A picture for something standing in the dungeon, which may be an animation strip:
 * the frames side by side, left to right, all the same width.
 *
 * <p>Written in files as {@code assets/slime.png} for a still picture, or
 * {@code assets/slime.png, 4 frames, 8 fps} for a strip of 4 frames played 8 times a second.
 *
 * @param image  asset reference, relative to the project (or map) folder
 * @param frames how many frames the image holds, side by side (1 = a still picture)
 * @param fps    frames per second
 */
public record SpriteRef(String image, int frames, double fps) {
    public static final int MAX_FRAMES = 64;
    public static final double DEFAULT_FPS = 8;
    private static final Pattern TEXT = Pattern.compile(
            "(.*?)(?:\\s*,\\s*(\\d+)\\s*frames?)?(?:\\s*,\\s*(\\d+(?:\\.\\d+)?)\\s*fps)?\\s*");

    public SpriteRef {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("A sprite needs an image");
        }
        image = image.strip();
        frames = Math.max(1, Math.min(MAX_FRAMES, frames));
        fps = fps > 0 ? Math.max(0.5, Math.min(60, fps)) : DEFAULT_FPS;
    }

    /** A still picture, or null for a blank reference. */
    public static SpriteRef still(String image) {
        return image == null || image.isBlank() ? null : new SpriteRef(image, 1, DEFAULT_FPS);
    }

    /** Reads the written form; null for blank text. */
    public static SpriteRef parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = TEXT.matcher(text.strip());
        if (!m.matches() || m.group(1).isBlank()) {
            return still(text);
        }
        int frames = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
        double fps = m.group(3) == null ? DEFAULT_FPS : Double.parseDouble(m.group(3));
        return new SpriteRef(m.group(1), frames, fps);
    }

    public boolean animated() {
        return frames > 1;
    }

    public SpriteRef withImage(String newImage) {
        return new SpriteRef(newImage, frames, fps);
    }

    /** The written form, as read by {@link #parse}. */
    @Override
    public String toString() {
        if (frames == 1) {
            return image;
        }
        String speed = fps == Math.rint(fps) ? Integer.toString((int) fps) : String.format(Locale.ROOT, "%.1f", fps);
        return image + ", " + frames + " frames, " + speed + " fps";
    }
}
