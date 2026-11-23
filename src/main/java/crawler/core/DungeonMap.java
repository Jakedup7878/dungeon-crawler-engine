package crawler.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * One floor of the dungeon: a grid of cells plus the edges between them.
 *
 * <p>Edges are stored on grid lines so that the wall between two cells is a single
 * shared value; there is no way for cell A to think there is a wall to the east
 * while cell B thinks the way west is open.
 *
 * <ul>
 *   <li>Horizontal line {@code y} (0..height) runs along the north side of row {@code y}.</li>
 *   <li>Vertical line {@code x} (0..width) runs along the west side of column {@code x}.</li>
 * </ul>
 *
 * Anything outside the map reads as {@link CellType#ROCK} / {@link EdgeType#WALL}.
 *
 * <p>A map can also name its own textures (see {@link Surface}) and hold one
 * {@link MapObject} (event, chest or NPC) per cell. Images are stored as asset
 * references, paths relative to the map file; loading the pictures is the renderer's job.
 */
public final class DungeonMap {
    private String name;
    private final int width;
    private final int height;
    private final CellType[] cells;
    private final EdgeType[] horizontal; // (height + 1) lines of width segments
    private final EdgeType[] vertical;   // height rows of (width + 1) segments

    private int startX;
    private int startY;
    private Direction startFacing = Direction.NORTH;

    private final EnumMap<Surface, String> textures = new EnumMap<>(Surface.class);
    /** Objects by cell index (y * width + x), so they come out in reading order. */
    private final TreeMap<Integer, MapObject> objects = new TreeMap<>();
    /** Background music (a WAV or MIDI asset reference), or null for silence. */
    private String music;
    private Encounters encounters = Encounters.NONE;

    /** Creates a map of open floor with no walls. */
    public DungeonMap(String name, int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Map must be at least 1x1, got " + width + "x" + height);
        }
        this.name = Objects.requireNonNull(name, "name");
        this.width = width;
        this.height = height;
        this.cells = new CellType[width * height];
        this.horizontal = new EdgeType[(height + 1) * width];
        this.vertical = new EdgeType[height * (width + 1)];
        Arrays.fill(cells, CellType.FLOOR);
        Arrays.fill(horizontal, EdgeType.OPEN);
        Arrays.fill(vertical, EdgeType.OPEN);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    // ---------------------------------------------------------------- cells

    public CellType cell(int x, int y) {
        return inBounds(x, y) ? cells[y * width + x] : CellType.ROCK;
    }

    public void setCell(int x, int y, CellType type) {
        if (!inBounds(x, y)) {
            throw new IndexOutOfBoundsException("Cell (" + x + ", " + y + ") is outside the map");
        }
        cells[y * width + x] = Objects.requireNonNull(type);
    }

    // ---------------------------------------------------------------- edges

    /** The edge on the given side of cell (x, y). */
    public EdgeType edge(int x, int y, Direction side) {
        return switch (side) {
            case NORTH -> horizontalEdge(x, y);
            case SOUTH -> horizontalEdge(x, y + 1);
            case WEST -> verticalEdge(x, y);
            case EAST -> verticalEdge(x + 1, y);
        };
    }

    /** Sets the edge on the given side of cell (x, y); the neighbour sees the same edge. */
    public void setEdge(int x, int y, Direction side, EdgeType type) {
        Objects.requireNonNull(type);
        switch (side) {
            case NORTH -> horizontal[horizontalIndex(x, y)] = type;
            case SOUTH -> horizontal[horizontalIndex(x, y + 1)] = type;
            case WEST -> vertical[verticalIndex(x, y)] = type;
            case EAST -> vertical[verticalIndex(x + 1, y)] = type;
        }
    }

    /** Segment {@code x} of horizontal grid line {@code line}. */
    public EdgeType horizontalEdge(int x, int line) {
        if (x < 0 || x >= width || line < 0 || line > height) {
            return EdgeType.WALL;
        }
        return horizontal[line * width + x];
    }

    /** Segment {@code y} of vertical grid line {@code line}. */
    public EdgeType verticalEdge(int line, int y) {
        if (line < 0 || line > width || y < 0 || y >= height) {
            return EdgeType.WALL;
        }
        return vertical[y * (width + 1) + line];
    }

    private int horizontalIndex(int x, int line) {
        if (x < 0 || x >= width || line < 0 || line > height) {
            throw new IndexOutOfBoundsException("Horizontal edge (" + x + ", line " + line + ") is outside the map");
        }
        return line * width + x;
    }

    private int verticalIndex(int line, int y) {
        if (line < 0 || line > width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Vertical edge (line " + line + ", " + y + ") is outside the map");
        }
        return y * (width + 1) + line;
    }

    /**
     * Whether a party standing in (x, y) could step one cell in the given direction:
     * nothing solid on the edge, floor beyond it, and no chest or NPC standing there.
     */
    public boolean canStep(int x, int y, Direction direction) {
        int nx = x + direction.dx;
        int ny = y + direction.dy;
        return edge(x, y, direction).passable && cell(nx, ny).walkable && !isBlocked(nx, ny);
    }

    /**
     * Turns every open edge that touches solid rock into a wall, including the map
     * border. Map loaders call this so that a sloppy map file can never let the party
     * walk off the map or into rock; it also keeps rock looking like one solid block.
     */
    public void sealEdges() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (Direction side : Direction.values()) {
                    boolean here = cell(x, y).walkable;
                    boolean there = cell(x + side.dx, y + side.dy).walkable;
                    if (!(here && there) && edge(x, y, side) == EdgeType.OPEN) {
                        setEdge(x, y, side, EdgeType.WALL);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- start

    public int startX() {
        return startX;
    }

    public int startY() {
        return startY;
    }

    public Direction startFacing() {
        return startFacing;
    }

    public void setStart(int x, int y, Direction facing) {
        if (!inBounds(x, y)) {
            throw new IndexOutOfBoundsException("Start (" + x + ", " + y + ") is outside the map");
        }
        this.startX = x;
        this.startY = y;
        this.startFacing = Objects.requireNonNull(facing);
    }

    // ---------------------------------------------------------------- textures

    /** The asset reference of this map's own texture for a surface, or null for the default. */
    public String texture(Surface surface) {
        return textures.get(surface);
    }

    /** Sets a surface's texture; null or blank goes back to the default. */
    public void setTexture(Surface surface, String reference) {
        if (reference == null || reference.isBlank()) {
            textures.remove(surface);
        } else {
            textures.put(surface, reference.strip());
        }
    }

    /** The surfaces that have their own texture. */
    public Map<Surface, String> textures() {
        return Collections.unmodifiableMap(textures);
    }

    // ---------------------------------------------------------------- objects

    /** The event, chest or NPC in cell (x, y), or null. */
    public MapObject object(int x, int y) {
        return inBounds(x, y) ? objects.get(y * width + x) : null;
    }

    /** Places an object in its cell, replacing whatever was there. */
    public void putObject(MapObject object) {
        if (!inBounds(object.x(), object.y())) {
            throw new IndexOutOfBoundsException("Object at (" + object.x() + ", " + object.y() + ") is outside the map");
        }
        objects.put(object.y() * width + object.x(), object);
    }

    /** Removes and returns the object in cell (x, y), if any. */
    public MapObject removeObject(int x, int y) {
        return inBounds(x, y) ? objects.remove(y * width + x) : null;
    }

    /** All objects, row by row from the north-west corner. */
    public Collection<MapObject> objects() {
        return Collections.unmodifiableCollection(objects.values());
    }

    /** Whether a chest or NPC stands in (x, y), so the party can't enter it. */
    public boolean isBlocked(int x, int y) {
        MapObject object = object(x, y);
        return object != null && object.solid();
    }

    // ---------------------------------------------------------------- settings

    /** The map's background music (an asset reference to a WAV or MIDI file), or null. */
    public String music() {
        return music;
    }

    public void setMusic(String reference) {
        music = reference == null || reference.isBlank() ? null : reference.strip();
    }

    public Encounters encounters() {
        return encounters;
    }

    public void setEncounters(Encounters encounters) {
        this.encounters = Objects.requireNonNull(encounters);
    }

    /** Every file this map refers to: textures, object pictures, then music. */
    public Set<String> assetReferences() {
        Set<String> references = new LinkedHashSet<>(textures.values());
        for (MapObject object : objects.values()) {
            if (object.sprite() != null) {
                references.add(object.sprite().image());
            }
            if (object.openSprite() != null) {
                references.add(object.openSprite().image());
            }
        }
        if (music != null) {
            references.add(music);
        }
        return references;
    }

    /** A copy with asset references changed as the map says (old reference to new); others stay. */
    public DungeonMap withRenamedAssets(Map<String, String> renamed) {
        DungeonMap copy = copy();
        textures.forEach((surface, reference) -> copy.textures.put(surface, renamed.getOrDefault(reference, reference)));
        for (MapObject o : objects.values()) {
            copy.putObject(o.withSprites(rename(o.sprite(), renamed), rename(o.openSprite(), renamed)));
        }
        if (music != null) {
            copy.music = renamed.getOrDefault(music, music);
        }
        return copy;
    }

    private static SpriteRef rename(SpriteRef sprite, Map<String, String> renamed) {
        return sprite == null ? null : sprite.withImage(renamed.getOrDefault(sprite.image(), sprite.image()));
    }

    // ---------------------------------------------------------------- copies

    /** An independent copy: changing one never affects the other. */
    public DungeonMap copy() {
        DungeonMap copy = new DungeonMap(name, width, height);
        System.arraycopy(cells, 0, copy.cells, 0, cells.length);
        System.arraycopy(horizontal, 0, copy.horizontal, 0, horizontal.length);
        System.arraycopy(vertical, 0, copy.vertical, 0, vertical.length);
        copy.startX = startX;
        copy.startY = startY;
        copy.startFacing = startFacing;
        copy.textures.putAll(textures);
        copy.objects.putAll(objects);
        copy.music = music;
        copy.encounters = encounters;
        return copy;
    }

    /** Whether both maps have the same name, size, cells, edges, start, textures, objects and settings. */
    public boolean contentEquals(DungeonMap other) {
        return other != null
                && name.equals(other.name)
                && width == other.width
                && height == other.height
                && startX == other.startX
                && startY == other.startY
                && startFacing == other.startFacing
                && Arrays.equals(cells, other.cells)
                && Arrays.equals(horizontal, other.horizontal)
                && Arrays.equals(vertical, other.vertical)
                && textures.equals(other.textures)
                && objects.equals(other.objects)
                && Objects.equals(music, other.music)
                && encounters.equals(other.encounters);
    }

    /**
     * A copy of this map on a grid of a different size. Cell (x, y) of this map lands
     * on (x + offsetX, y + offsetY); anything that falls off the new grid is dropped,
     * and new space is solid rock. The start is pulled back inside the map if needed.
     * Textures and the objects still on the map come along.
     */
    public DungeonMap resized(int newWidth, int newHeight, int offsetX, int offsetY) {
        DungeonMap result = new DungeonMap(name, newWidth, newHeight);
        Arrays.fill(result.cells, CellType.ROCK);
        Arrays.fill(result.horizontal, EdgeType.WALL);
        Arrays.fill(result.vertical, EdgeType.WALL);
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                if (inBounds(x - offsetX, y - offsetY)) {
                    result.cells[y * newWidth + x] = cell(x - offsetX, y - offsetY);
                }
            }
        }
        for (int line = 0; line <= newHeight; line++) {
            for (int x = 0; x < newWidth; x++) {
                int oldX = x - offsetX;
                int oldLine = line - offsetY;
                if (oldX >= 0 && oldX < width && oldLine >= 0 && oldLine <= height) {
                    result.horizontal[line * newWidth + x] = horizontal[oldLine * width + oldX];
                }
            }
        }
        for (int y = 0; y < newHeight; y++) {
            for (int line = 0; line <= newWidth; line++) {
                int oldY = y - offsetY;
                int oldLine = line - offsetX;
                if (oldY >= 0 && oldY < height && oldLine >= 0 && oldLine <= width) {
                    result.vertical[y * (newWidth + 1) + line] = vertical[oldY * (width + 1) + oldLine];
                }
            }
        }
        result.setStart(clamp(startX + offsetX, newWidth), clamp(startY + offsetY, newHeight), startFacing);
        result.textures.putAll(textures);
        result.music = music;
        result.encounters = encounters;
        for (MapObject object : objects.values()) {
            int x = object.x() + offsetX;
            int y = object.y() + offsetY;
            if (result.inBounds(x, y)) {
                result.putObject(object.at(x, y));
            }
        }
        result.sealEdges();
        return result;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }
}
