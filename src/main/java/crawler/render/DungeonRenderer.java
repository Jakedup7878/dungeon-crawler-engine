package crawler.render;

import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.MapObject;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Software renderer for the first-person view.
 *
 * <p>A column raycaster adapted to thin walls: each screen column casts a ray through
 * the grid, and at every grid line it crosses it checks the edge stored there instead
 * of checking whether the next cell is solid. Floor and ceiling are drawn first with
 * per-row floor casting, then walls are drawn over them.
 *
 * <p>Objects (chests, characters) are drawn last as billboards: flat pictures that always
 * face the viewer, hidden behind nearer walls using each column's wall distance.
 *
 * <p>Renders into a small {@link BufferedImage} (320x240 by default) that the UI scales
 * up with nearest-neighbour filtering for a chunky retro look.
 */
public final class DungeonRenderer {
    /** Half-width of the view plane at distance 1. 1.0 gives a 90 degree field of view. */
    private static final double PLANE = 1.0;
    /**
     * How far behind the cell centre the eye sits, in cells. Standing near the back of
     * the cell lets you see the walls to your left and right, as in classic crawlers.
     */
    private static final double EYE_BACK_OFFSET = 0.4;
    /** Larger is darker: light falls off as exp(-density * distance). */
    private static final double FOG_DENSITY = 0.26;
    /** Walls facing east or west are drawn a little darker so corners read clearly (out of 256). */
    private static final int SIDE_LIGHT = 205;
    private static final int CEILING_LIGHT = 220;
    private static final int MAX_RAY_STEPS = 128;
    /**
     * Billboards nearer than this (in cells, along the view) are not drawn. The party stands
     * 0.4 behind the centre of its own cell, so this hides an object in the party's own cell
     * (an event it is standing on) instead of filling the screen with it.
     */
    private static final double NEAR_CLIP = 0.75;
    /** Billboards are never wider than this many cells. */
    private static final double MAX_BILLBOARD_WIDTH = 0.95;

    /** How a wall looks where something is on it. */
    public interface WallPictures {
        /** The wall with the object's picture on it; {@code wall} is how it looks without. */
        Texture picture(MapObject object, Texture wall, double seconds);
    }


    private final int width;
    private final int height;
    private final BufferedImage frame;
    private final int[] pixels;
    private final double projection;
    /** Distance to the wall drawn in each column, for hiding billboards behind walls. */
    private final double[] depth;
    private Textures textures;

    public DungeonRenderer(int width, int height, Textures textures) {
        if (width < 2 || height < 2 || height % 2 != 0) {
            throw new IllegalArgumentException("View must be at least 2x2 with an even height, got " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.textures = textures;
        this.frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        this.pixels = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
        this.projection = (width / 2.0) / PLANE;
        this.depth = new double[width];
    }

    public Textures textures() {
        return textures;
    }

    /** Switches texture set, for example when moving to a map with its own textures. */
    public void setTextures(Textures textures) {
        this.textures = textures;
    }

    public BufferedImage frame() {
        return frame;
    }

    /**
     * Draws the view from a camera position (map units, cell centres at +0.5) and
     * heading (radians clockwise from north) into {@link #frame()}.
     */
    public void render(DungeonMap map, double cameraX, double cameraY, double angle) {
        render(map, cameraX, cameraY, angle, List.of(), 0);
    }

    /**
     * Draws the view as above, then the billboards in front of it.
     *
     * @param seconds the time, for animated sprites
     */
    public void render(DungeonMap map, double cameraX, double cameraY, double angle,
                       List<Billboard> billboards, double seconds) {
        render(map, cameraX, cameraY, angle, billboards, seconds, (object, wall, time) -> wall);
    }

    /** Draws the view as above, with pictures on the walls that have something on them (such as stairs). */
    public void render(DungeonMap map, double cameraX, double cameraY, double angle,
                       List<Billboard> billboards, double seconds, WallPictures pictures) {
        double dirX = Math.sin(angle);
        double dirY = -Math.cos(angle);
        double planeX = -dirY * PLANE; // the view plane points to the camera's right
        double planeY = dirX * PLANE;
        double eyeX = cameraX - dirX * EYE_BACK_OFFSET;
        double eyeY = cameraY - dirY * EYE_BACK_OFFSET;

        drawFloorAndCeiling(eyeX, eyeY, dirX, dirY, planeX, planeY);
        drawWalls(map, eyeX, eyeY, dirX, dirY, planeX, planeY, pictures, seconds);
        if (!billboards.isEmpty()) {
            drawBillboards(billboards, seconds, eyeX, eyeY, dirX, dirY);
        }
    }

    private void drawFloorAndCeiling(double eyeX, double eyeY,
                                     double dirX, double dirY, double planeX, double planeY) {
        Texture floor = textures.floor;
        Texture ceiling = textures.ceiling;
        double leftX = dirX - planeX;
        double leftY = dirY - planeY;
        int half = height / 2;

        for (int row = 0; row < half; row++) {
            // Ceiling row `row` and floor row `height - 1 - row` are the same distance away.
            double pixelsFromHorizon = half - row - 0.5;
            double distance = 0.5 * projection / pixelsFromHorizon; // the eye is half a cell up
            int light = light(distance);
            int ceilingLight = light * CEILING_LIGHT >> 8;
            double stepX = distance * 2 * planeX / width;
            double stepY = distance * 2 * planeY / width;
            double worldX = eyeX + distance * leftX + stepX * 0.5;
            double worldY = eyeY + distance * leftY + stepY * 0.5;
            int ceilingRow = row * width;
            int floorRow = (height - 1 - row) * width;

            for (int col = 0; col < width; col++) {
                double fx = worldX - Math.floor(worldX);
                double fy = worldY - Math.floor(worldY);
                pixels[floorRow + col] = shade(
                        floor.texel((int) (fx * floor.size), (int) (fy * floor.size)), light);
                pixels[ceilingRow + col] = shade(
                        ceiling.texel((int) (fx * ceiling.size), (int) (fy * ceiling.size)), ceilingLight);
                worldX += stepX;
                worldY += stepY;
            }
        }
    }

    private void drawWalls(DungeonMap map, double eyeX, double eyeY,
                           double dirX, double dirY, double planeX, double planeY,
                           WallPictures pictures, double seconds) {
        int half = height / 2;

        for (int col = 0; col < width; col++) {
            double screenX = 2.0 * (col + 0.5) / width - 1.0;
            double rayX = dirX + planeX * screenX;
            double rayY = dirY + planeY * screenX;

            int cellX = (int) Math.floor(eyeX);
            int cellY = (int) Math.floor(eyeY);
            double deltaX = rayX == 0 ? 1e30 : Math.abs(1 / rayX);
            double deltaY = rayY == 0 ? 1e30 : Math.abs(1 / rayY);
            int stepX = rayX < 0 ? -1 : 1;
            int stepY = rayY < 0 ? -1 : 1;
            depth[col] = Double.POSITIVE_INFINITY;
            // Distance (along the view direction) to the next vertical / horizontal grid line.
            double nextX = rayX < 0 ? (eyeX - cellX) * deltaX : (cellX + 1 - eyeX) * deltaX;
            double nextY = rayY < 0 ? (eyeY - cellY) * deltaY : (cellY + 1 - eyeY) * deltaY;

            EdgeType hit = null;
            boolean verticalLine = false;
            double distance = 0;
            for (int i = 0; i < MAX_RAY_STEPS; i++) {
                if (nextX < nextY) {
                    EdgeType edge = map.verticalEdge(stepX > 0 ? cellX + 1 : cellX, cellY);
                    if (edge.blocksSight) {
                        hit = edge;
                        distance = nextX;
                        verticalLine = true;
                        break;
                    }
                    nextX += deltaX;
                    cellX += stepX;
                } else {
                    EdgeType edge = map.horizontalEdge(cellX, stepY > 0 ? cellY + 1 : cellY);
                    if (edge.blocksSight) {
                        hit = edge;
                        distance = nextY;
                        verticalLine = false;
                        break;
                    }
                    nextY += deltaY;
                    cellY += stepY;
                }
            }
            if (hit == null) {
                continue;
            }

            distance = Math.max(distance, 1e-3);
            depth[col] = distance;
            double along = verticalLine ? eyeY + distance * rayY : eyeX + distance * rayX;
            along -= Math.floor(along);

            Texture texture = textures.forEdge(hit);
            MapObject object = hit == EdgeType.WALL ? map.object(cellX, cellY) : null;
            if (object != null && object.wall() != null) {
                Direction side = verticalLine ? (stepX > 0 ? Direction.EAST : Direction.WEST)
                        : (stepY > 0 ? Direction.SOUTH : Direction.NORTH);
                if (object.wall() == side) {
                    texture = pictures.picture(object, texture, seconds);
                }
            }
            int texX = Math.min(texture.size - 1, (int) (along * texture.size));
            // Keep textures the right way round whichever side of the wall you see.
            if ((verticalLine && rayX < 0) || (!verticalLine && rayY > 0)) {
                texX = texture.size - 1 - texX;
            }

            double lineHeight = projection / distance;
            double top = half - lineHeight / 2;
            int y0 = Math.max(0, (int) Math.ceil(top - 0.5));
            int y1 = Math.min(height, (int) Math.ceil(top + lineHeight - 0.5));
            double texStep = texture.size / lineHeight;
            double texY = (y0 + 0.5 - top) * texStep;
            int light = light(distance);
            if (verticalLine) {
                light = light * SIDE_LIGHT >> 8;
            }

            for (int y = y0; y < y1; y++) {
                int ty = Math.min(texture.size - 1, (int) texY);
                pixels[y * width + col] = shade(texture.pixels[ty * texture.size + texX], light);
                texY += texStep;
            }
        }
    }

    /** Draws billboards far to near, skipping columns where a nearer wall hides them. */
    private void drawBillboards(List<Billboard> billboards, double seconds,
                                double eyeX, double eyeY, double dirX, double dirY) {
        // The camera's right-hand direction; dirX/dirY is a unit vector.
        double rightX = -dirY;
        double rightY = dirX;
        List<double[]> visible = new ArrayList<>(); // {depth, lateral, index}
        for (int i = 0; i < billboards.size(); i++) {
            Billboard b = billboards.get(i);
            double rx = b.x() - eyeX;
            double ry = b.y() - eyeY;
            double along = rx * dirX + ry * dirY;
            if (along >= NEAR_CLIP) {
                visible.add(new double[]{along, rx * rightX + ry * rightY, i});
            }
        }
        visible.sort(Comparator.comparingDouble((double[] v) -> v[0]).reversed());

        int half = height / 2;
        for (double[] v : visible) {
            Billboard b = billboards.get((int) v[2]);
            double along = v[0];
            Sprite sprite = b.sprite();
            int[] texels = sprite.pixels(seconds);
            double worldH = b.height();
            double worldW = worldH * sprite.width / sprite.height;
            if (worldW > MAX_BILLBOARD_WIDTH) {
                worldH *= MAX_BILLBOARD_WIDTH / worldW;
                worldW = MAX_BILLBOARD_WIDTH;
            }
            double scale = projection / along; // screen pixels per cell at this distance
            double screenW = worldW * scale;
            double screenH = worldH * scale;
            double centreX = width / 2.0 * (1 + v[1] / (along * PLANE));
            double bottom = half + 0.5 * scale; // the floor at this distance (the eye is half a cell up)
            double top = bottom - screenH;
            double left = centreX - screenW / 2;
            int x0 = Math.max(0, (int) Math.ceil(left - 0.5));
            int x1 = Math.min(width, (int) Math.ceil(left + screenW - 0.5));
            int y0 = Math.max(0, (int) Math.ceil(top - 0.5));
            int y1 = Math.min(height, (int) Math.ceil(bottom - 0.5));
            int light = light(along);

            for (int col = x0; col < x1; col++) {
                if (along >= depth[col]) {
                    continue;
                }
                int tx = Math.min(sprite.width - 1, (int) ((col + 0.5 - left) / screenW * sprite.width));
                for (int y = y0; y < y1; y++) {
                    int ty = Math.min(sprite.height - 1, (int) ((y + 0.5 - top) / screenH * sprite.height));
                    int argb = texels[ty * sprite.width + tx];
                    int alpha = argb >>> 24;
                    if (alpha == 0) {
                        continue;
                    }
                    int index = y * width + col;
                    int shaded = shade(argb, light);
                    pixels[index] = alpha == 255 ? shaded : blend(pixels[index], shaded, alpha);
                }
            }
        }
    }

    private static int blend(int under, int over, int alpha) {
        int inverse = 255 - alpha;
        int r = (((over >> 16) & 0xFF) * alpha + ((under >> 16) & 0xFF) * inverse) / 255;
        int g = (((over >> 8) & 0xFF) * alpha + ((under >> 8) & 0xFF) * inverse) / 255;
        int b = ((over & 0xFF) * alpha + (under & 0xFF) * inverse) / 255;
        return (r << 16) | (g << 8) | b;
    }

    /** Light level 0..256 at a distance. */
    private static int light(double distance) {
        return (int) (256 * Math.exp(-FOG_DENSITY * distance));
    }

    private static int shade(int rgb, int light) {
        int r = ((rgb >> 16) & 0xFF) * light >> 8;
        int g = ((rgb >> 8) & 0xFF) * light >> 8;
        int b = (rgb & 0xFF) * light >> 8;
        return (r << 16) | (g << 8) | b;
    }
}
