package crawler.render;

import java.awt.image.BufferedImage;

/** A square, power-of-two RGB texture held as a flat pixel array. */
public final class Texture {
    public final int size;
    public final int mask;
    public final int[] pixels;

    public Texture(int size, int[] pixels) {
        if (size < 1 || Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("Texture size must be a power of two, got " + size);
        }
        if (pixels.length != size * size) {
            throw new IllegalArgumentException("Expected " + size * size + " pixels, got " + pixels.length);
        }
        this.size = size;
        this.mask = size - 1;
        this.pixels = pixels;
    }

    /** Wraps coordinates, so any integer is a valid texel position. */
    public int texel(int x, int y) {
        return pixels[(y & mask) * size + (x & mask)];
    }

    /**
     * Makes a texture from any image. It is resized to a square power of two (between 16
     * and 256 pixels a side, the nearest at or above its larger side), so pixel art of
     * 16, 32 or 64 pixels keeps its crisp pixels and other sizes are smoothly scaled.
     * Transparency is dropped.
     */
    public static Texture fromAnyImage(BufferedImage image) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        int size = Math.max(16, Math.min(256, Integer.highestOneBit(Math.max(1, longest - 1)) << 1));
        if (image.getWidth() == size && image.getHeight() == size) {
            return fromImage(image);
        }
        return fromImage(Sprite.scale(image, size, size, BufferedImage.TYPE_INT_RGB));
    }

    /** The texture as an image, for showing it in a user interface. */
    public BufferedImage toImage() {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, size, size, pixels, 0, size);
        return image;
    }

    /** Copies a square power-of-two image (such as a PNG you drew) into a texture. */
    public static Texture fromImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w != h) {
            throw new IllegalArgumentException("Texture images must be square, got " + w + "x" + h);
        }
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] &= 0xFFFFFF;
        }
        return new Texture(w, pixels);
    }
}
