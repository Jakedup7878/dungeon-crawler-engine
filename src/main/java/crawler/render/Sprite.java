package crawler.render;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;

/**
 * A picture drawn standing in the dungeon (a chest, a character...), with transparency,
 * optionally animated. Any image size works; very large images are scaled down.
 */
public final class Sprite {
    /** Larger images are scaled down to this many pixels on their longer side. */
    private static final int MAX_SIZE = 256;

    public final int width;
    public final int height;
    private final BufferedImage[] frames;
    private final int[][] pixels;
    private final double framesPerSecond;

    private Sprite(List<BufferedImage> images, double framesPerSecond) {
        if (images.isEmpty()) {
            throw new IllegalArgumentException("A sprite needs at least one frame");
        }
        this.width = images.get(0).getWidth();
        this.height = images.get(0).getHeight();
        this.frames = new BufferedImage[images.size()];
        this.pixels = new int[images.size()][];
        for (int i = 0; i < images.size(); i++) {
            BufferedImage frame = images.get(i);
            if (frame.getWidth() != width || frame.getHeight() != height) {
                throw new IllegalArgumentException("All frames of a sprite must be the same size");
            }
            frames[i] = argb(frame);
            pixels[i] = ((DataBufferInt) frames[i].getRaster().getDataBuffer()).getData();
        }
        this.framesPerSecond = framesPerSecond;
    }

    /** A still sprite from any image (such as a PNG with a transparent background). */
    public static Sprite of(BufferedImage image) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest > MAX_SIZE) {
            double factor = MAX_SIZE / (double) longest;
            image = scale(image, Math.max(1, (int) Math.round(image.getWidth() * factor)),
                    Math.max(1, (int) Math.round(image.getHeight() * factor)), BufferedImage.TYPE_INT_ARGB);
        }
        return new Sprite(List.of(image), 0);
    }

    /** An animation that loops through equally sized frames. */
    public static Sprite animated(List<BufferedImage> frames, double framesPerSecond) {
        return new Sprite(frames, framesPerSecond);
    }

    /**
     * An animation from a strip: {@code frames} pictures side by side, left to right, all the
     * same width. A strip of 1 frame is a still picture. Big frames are scaled down.
     */
    public static Sprite strip(BufferedImage image, int frames, double framesPerSecond) {
        int count = Math.max(1, Math.min(frames, image.getWidth()));
        if (count == 1) {
            return of(image);
        }
        int frameWidth = image.getWidth() / count;
        int height = image.getHeight();
        double factor = Math.min(1.0, MAX_SIZE / (double) Math.max(frameWidth, height));
        int outWidth = Math.max(1, (int) Math.round(frameWidth * factor));
        int outHeight = Math.max(1, (int) Math.round(height * factor));
        List<BufferedImage> pictures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // Copy each frame: a sub-image would share the whole strip's pixels.
            BufferedImage frame = new BufferedImage(frameWidth, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = frame.createGraphics();
            g.drawImage(image.getSubimage(i * frameWidth, 0, frameWidth, height), 0, 0, null);
            g.dispose();
            pictures.add(factor < 1 ? scale(frame, outWidth, outHeight, BufferedImage.TYPE_INT_ARGB) : frame);
        }
        return new Sprite(pictures, framesPerSecond);
    }

    /** How long one pass through all the frames takes, in seconds. */
    public double duration() {
        return framesPerSecond <= 0 ? 0 : frames.length / framesPerSecond;
    }

    /** The frame for a time, without looping: the last frame once the animation is over. */
    public BufferedImage imageOnce(double seconds) {
        int index = framesPerSecond <= 0 ? 0 : (int) Math.floor(seconds * framesPerSecond);
        return frames[Math.max(0, Math.min(frames.length - 1, index))];
    }

    public int frameCount() {
        return frames.length;
    }

    private int frameIndex(double seconds) {
        if (frames.length == 1 || framesPerSecond <= 0) {
            return 0;
        }
        return (int) Math.floorMod((long) Math.floor(seconds * framesPerSecond), (long) frames.length);
    }

    /** ARGB pixels of the frame showing at a time, row by row. Don't modify. */
    public int[] pixels(double seconds) {
        return pixels[frameIndex(seconds)];
    }

    /** The frame showing at a time, for drawing in a user interface. */
    public BufferedImage image(double seconds) {
        return frames[frameIndex(seconds)];
    }

    /** Resizes an image smoothly, averaging pixels when shrinking a lot. */
    static BufferedImage scale(BufferedImage image, int width, int height, int type) {
        BufferedImage out = new BufferedImage(width, height, type);
        Graphics2D g = out.createGraphics();
        try {
            if (width * 2 < image.getWidth() || height * 2 < image.getHeight()) {
                g.drawImage(image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
            } else {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, 0, 0, width, height, null);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage argb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }
}
