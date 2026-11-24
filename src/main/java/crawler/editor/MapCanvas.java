package crawler.editor;

import crawler.core.CellType;
import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.MapObject;
import crawler.core.ObjectType;
import crawler.render.ObjectLooks;
import crawler.render.Sprite;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * The editable top-down map. Mouse input becomes edits on the {@link MapDocument}
 * according to the current {@link Tool}.
 *
 * <ul>
 *   <li>Left button: the tool's main action. Right button: its opposite.</li>
 *   <li>Mouse wheel scrolls, Ctrl + wheel zooms, middle-drag pans.</li>
 * </ul>
 */
final class MapCanvas extends JComponent implements Scrollable {
    static final int MIN_ZOOM = 10;
    static final int MAX_ZOOM = 64;
    static final int DEFAULT_ZOOM = 28;
    /** Space around the map for the coordinate rulers. */
    private static final int MARGIN = 34;
    /** While dragging along a grid line, stay on lines of that direction until the mouse is this far (in cells) from one. */
    private static final double STICKY = 0.3;
    /** A fast drag is sampled at least this often (in cells), so no edge in between is skipped. */
    private static final double SAMPLE_STEP = 0.2;

    private static final Color BACKGROUND = new Color(12, 13, 18);
    private static final Color FLOOR = new Color(40, 55, 80);
    private static final Color ROCK = new Color(23, 23, 30);
    private static final Color ROCK_HATCH = new Color(34, 34, 44);
    private static final Color GRID = new Color(110, 130, 170, 60);
    private static final Color OUTLINE = new Color(70, 80, 105);
    private static final Color WALL = new Color(210, 220, 240);
    private static final Color DOOR = new Color(236, 168, 64);
    private static final Color DOOR_EDGE = new Color(120, 70, 20);
    private static final Color START = new Color(255, 214, 92);
    private static final Color UNREACHABLE = new Color(230, 60, 60, 90);
    private static final Color HOVER = new Color(110, 215, 255);
    private static final Color ERASE = new Color(255, 105, 105);
    private static final Color RULER = new Color(115, 123, 145);
    private static final Font RULER_FONT = new Font(Font.DIALOG, Font.PLAIN, 11);
    private static final Font LABEL_FONT = new Font(Font.DIALOG, Font.BOLD, 12);
    private static final Color EVENT = new Color(196, 92, 236);

    /** What the canvas tells the rest of the editor. */
    interface Listener {
        /** The cell (or -1, -1) and edge (or null) under the mouse changed. */
        void hoverChanged(int cellX, int cellY, EdgeRef edge);

        void message(String text);

        /** A click (no drag) with an object tool: add an object there, or edit the one there. */
        void objectClicked(int cellX, int cellY);
    }

    private final MapDocument doc;
    private final ObjectLooks looks;
    private final TexturePaint rockPaint;
    private Listener listener = new Listener() {
        @Override
        public void hoverChanged(int cellX, int cellY, EdgeRef edge) {
        }

        @Override
        public void message(String text) {
        }

        @Override
        public void objectClicked(int cellX, int cellY) {
        }
    };
    private Tool tool = Tool.ROOM;
    private int zoom = DEFAULT_ZOOM;
    private boolean showUnreachable = true;
    private Direction previewFacing = Direction.NORTH;

    private int hoverX = -1;
    private int hoverY = -1;
    private EdgeRef hoverEdge;

    /** MouseEvent.BUTTON1 or BUTTON3 while a drag is editing the map, otherwise NOBUTTON. */
    private int strokeButton = MouseEvent.NOBUTTON;
    private int anchorX;
    private int anchorY;
    private int lastCellX;
    private int lastCellY;
    private double lastFx;
    private double lastFy;
    private EdgeRef lastEdge;
    /** The object being dragged to a new cell, or null. */
    private MapObject movingObject;
    private Point panFrom;

    MapCanvas(MapDocument doc, ObjectLooks looks) {
        this.doc = doc;
        this.looks = looks;
        this.rockPaint = hatch();
        setOpaque(true);
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                released(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                dragged(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (strokeButton == MouseEvent.NOBUTTON) {
                    setHover(-1, -1, null);
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                wheel(e);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);

        doc.addListener(() -> {
            DungeonMap map = doc.map();
            if (!map.inBounds(hoverX, hoverY)) {
                hoverX = hoverY = -1;
            }
            revalidate();
            repaint();
        });
    }

    void setListener(Listener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    Tool tool() {
        return tool;
    }

    void setTool(Tool tool) {
        this.tool = tool;
        hoverEdge = null;
        repaint();
    }

    int zoom() {
        return zoom;
    }

    void setShowUnreachable(boolean show) {
        showUnreachable = show;
        repaint();
    }

    void setPreviewFacing(Direction facing) {
        previewFacing = facing;
        repaint();
    }

    // ------------------------------------------------------------ coordinates

    int originX() {
        return Math.max(MARGIN, (getWidth() - doc.map().width() * zoom) / 2);
    }

    int originY() {
        return Math.max(MARGIN, (getHeight() - doc.map().height() * zoom) / 2);
    }

    /** Mouse x to map units (cell x spans x .. x + 1). */
    private double mapX(int px) {
        return (px - originX()) / (double) zoom;
    }

    private double mapY(int py) {
        return (py - originY()) / (double) zoom;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The grid edge nearest to a map point, or null if the point is well outside the map.
     *
     * @param preferHorizontal while dragging, the direction of the last edge: lines in that
     *                         direction win near corners, so a drag along a wall doesn't
     *                         catch the walls crossing it. Null when not dragging.
     */
    EdgeRef pickEdge(double fx, double fy, Boolean preferHorizontal) {
        DungeonMap map = doc.map();
        if (fx < -0.5 || fy < -0.5 || fx > map.width() + 0.5 || fy > map.height() + 0.5) {
            return null;
        }
        double toHorizontal = Math.abs(fy - Math.round(fy));
        double toVertical = Math.abs(fx - Math.round(fx));
        boolean horizontal;
        if (preferHorizontal != null && (preferHorizontal ? toHorizontal : toVertical) < STICKY) {
            horizontal = preferHorizontal;
        } else {
            horizontal = toHorizontal <= toVertical;
        }
        if (horizontal) {
            return new EdgeRef(true, clamp((int) Math.floor(fx), 0, map.width() - 1),
                    clamp((int) Math.round(fy), 0, map.height()));
        }
        return new EdgeRef(false, clamp((int) Math.round(fx), 0, map.width()),
                clamp((int) Math.floor(fy), 0, map.height() - 1));
    }

    // ------------------------------------------------------------ mouse

    private void pressed(MouseEvent e) {
        requestFocusInWindow();
        if (SwingUtilities.isMiddleMouseButton(e)) {
            panFrom = e.getLocationOnScreen();
            return;
        }
        int button = e.getButton();
        if (strokeButton != MouseEvent.NOBUTTON || (button != MouseEvent.BUTTON1 && button != MouseEvent.BUTTON3)) {
            return;
        }
        DungeonMap map = doc.map();
        double fx = mapX(e.getX());
        double fy = mapY(e.getY());
        int cx = (int) Math.floor(fx);
        int cy = (int) Math.floor(fy);
        EdgeRef edge = tool.editsEdges() ? pickEdge(fx, fy, null) : null;
        if (tool.editsEdges() ? edge == null : !map.inBounds(cx, cy)) {
            return; // clicked in the margin
        }

        boolean primary = button == MouseEvent.BUTTON1;
        strokeButton = button;
        doc.beginEdit();
        switch (tool) {
            case ROOM -> {
                anchorX = lastCellX = cx;
                anchorY = lastCellY = cy;
            }
            case CORRIDOR -> {
                lastCellX = cx;
                lastCellY = cy;
                if (primary) {
                    MapEdits.dig(map, cx, cy);
                } else {
                    MapEdits.fillRock(map, cx, cy, cx, cy);
                }
                doc.edited();
            }
            case WALL, DOOR -> {
                lastFx = fx;
                lastFy = fy;
                lastEdge = edge;
                applyEdge(edge, primary);
            }
            case START -> clickStart(cx, cy, primary);
            case EVENT, CHEST, NPC, STAIRS -> {
                anchorX = lastCellX = cx;
                anchorY = lastCellY = cy;
                if (primary) {
                    movingObject = map.object(cx, cy);
                } else {
                    removeObject(cx, cy);
                }
            }
        }
        hover(e.getX(), e.getY());
    }

    private void dragged(MouseEvent e) {
        if (panFrom != null) {
            pan(e.getLocationOnScreen());
            return;
        }
        if (strokeButton == MouseEvent.NOBUTTON) {
            hover(e.getX(), e.getY());
            return;
        }
        boolean primary = strokeButton == MouseEvent.BUTTON1;
        DungeonMap map = doc.map();
        double fx = mapX(e.getX());
        double fy = mapY(e.getY());
        int cx = clamp((int) Math.floor(fx), 0, map.width() - 1);
        int cy = clamp((int) Math.floor(fy), 0, map.height() - 1);
        switch (tool) {
            case ROOM -> {
                lastCellX = cx;
                lastCellY = cy;
            }
            case CORRIDOR -> corridorTo(cx, cy, primary);
            case WALL, DOOR -> dragEdges(fx, fy, primary);
            case START -> { }
            case EVENT, CHEST, NPC, STAIRS -> {
                if (!primary && (cx != lastCellX || cy != lastCellY)) {
                    removeObject(cx, cy);
                }
                lastCellX = cx;
                lastCellY = cy;
            }
        }
        scrollRectToVisible(new Rectangle(e.getX() - 8, e.getY() - 8, 16, 16));
        hover(e.getX(), e.getY());
    }

    private void released(MouseEvent e) {
        if (SwingUtilities.isMiddleMouseButton(e)) {
            panFrom = null;
            return;
        }
        if (e.getButton() != strokeButton) {
            return;
        }
        boolean clicked = false;
        if (tool == Tool.ROOM) {
            if (strokeButton == MouseEvent.BUTTON1) {
                MapEdits.room(doc.map(), anchorX, anchorY, lastCellX, lastCellY);
            } else {
                MapEdits.fillRock(doc.map(), anchorX, anchorY, lastCellX, lastCellY);
            }
        } else if (tool.placesObjects() && strokeButton == MouseEvent.BUTTON1) {
            boolean moved = lastCellX != anchorX || lastCellY != anchorY;
            if (!moved) {
                clicked = true;
            } else if (movingObject != null) {
                moveObject(movingObject, lastCellX, lastCellY);
            }
        }
        strokeButton = MouseEvent.NOBUTTON;
        lastEdge = null;
        movingObject = null;
        doc.endEdit();
        hover(e.getX(), e.getY());
        if (clicked) {
            listener.objectClicked(anchorX, anchorY); // may open a dialog, so after the edit is closed
        }
    }

    private void moveObject(MapObject object, int x, int y) {
        DungeonMap map = doc.map();
        map.removeObject(object.x(), object.y());
        String problem = MapEdits.objectProblem(map, object.type(), object.solid(), x, y);
        if (problem != null) {
            map.putObject(object); // put it back
            listener.message(problem);
            return;
        }
        map.putObject(object.at(x, y));
        doc.edited();
        listener.message("Moved the " + object.type().lowerName() + " to " + x + ", " + y + ".");
    }

    private void removeObject(int x, int y) {
        MapObject removed = doc.map().removeObject(x, y);
        if (removed != null) {
            doc.edited();
            listener.message("Removed the " + removed.type().lowerName() + " at " + x + ", " + y + ". Ctrl+Z brings it back.");
        }
    }

    /** Digs (or fills) cell by cell from the last corridor cell to (tx, ty), so fast drags leave no gaps. */
    private void corridorTo(int tx, int ty, boolean dig) {
        DungeonMap map = doc.map();
        boolean changed = false;
        while (lastCellX != tx || lastCellY != ty) {
            int dx = tx - lastCellX;
            int dy = ty - lastCellY;
            int nx = lastCellX;
            int ny = lastCellY;
            if (Math.abs(dx) >= Math.abs(dy)) {
                nx += Integer.signum(dx);
            } else {
                ny += Integer.signum(dy);
            }
            if (dig) {
                MapEdits.dig(map, lastCellX, lastCellY, nx, ny);
            } else {
                MapEdits.fillRock(map, nx, ny, nx, ny);
            }
            lastCellX = nx;
            lastCellY = ny;
            changed = true;
        }
        if (changed) {
            doc.edited();
        }
    }

    private void dragEdges(double fx, double fy, boolean primary) {
        double dx = fx - lastFx;
        double dy = fy - lastFy;
        int samples = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / SAMPLE_STEP));
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            EdgeRef edge = pickEdge(lastFx + dx * t, lastFy + dy * t, lastEdge == null ? null : lastEdge.horizontal());
            if (edge != null && !edge.equals(lastEdge)) {
                applyEdge(edge, primary);
                lastEdge = edge;
            }
        }
        lastFx = fx;
        lastFy = fy;
    }

    private void applyEdge(EdgeRef edge, boolean primary) {
        DungeonMap map = doc.map();
        if (!edge.isInterior(map)) {
            return; // the map border is always wall
        }
        EdgeType current = edge.get(map);
        EdgeType next = switch (tool) {
            case WALL -> primary ? EdgeType.WALL : EdgeType.OPEN;
            case DOOR -> primary ? EdgeType.DOOR : (current == EdgeType.DOOR ? EdgeType.WALL : current);
            default -> current;
        };
        if (next != current) {
            edge.set(map, next);
            doc.edited();
        }
    }

    private void clickStart(int x, int y, boolean primary) {
        DungeonMap map = doc.map();
        if (x == map.startX() && y == map.startY()) {
            Direction facing = primary ? map.startFacing().turnRight() : map.startFacing().turnLeft();
            map.setStart(x, y, facing);
        } else if (map.cell(x, y).walkable && !map.isBlocked(x, y)) {
            map.setStart(x, y, map.startFacing());
        } else {
            listener.message(map.isBlocked(x, y)
                    ? "There's " + map.object(x, y).type().withArticle() + " there, so the party can't start there."
                    : "The start has to be on a floor cell.");
            return;
        }
        doc.edited();
    }

    private void hover(int px, int py) {
        double fx = mapX(px);
        double fy = mapY(py);
        int cx = (int) Math.floor(fx);
        int cy = (int) Math.floor(fy);
        if (!doc.map().inBounds(cx, cy)) {
            cx = cy = -1;
        }
        Boolean prefer = strokeButton != MouseEvent.NOBUTTON && lastEdge != null ? lastEdge.horizontal() : null;
        setHover(cx, cy, tool.editsEdges() ? pickEdge(fx, fy, prefer) : null);
    }

    private void setHover(int cx, int cy, EdgeRef edge) {
        if (cx != hoverX || cy != hoverY || !Objects.equals(edge, hoverEdge)) {
            hoverX = cx;
            hoverY = cy;
            hoverEdge = edge;
            listener.hoverChanged(cx, cy, edge);
        }
        repaint();
    }

    private void wheel(MouseWheelEvent e) {
        if (e.isControlDown()) {
            zoomBy(e.getWheelRotation() < 0 ? 1 : -1, e.getPoint());
            return;
        }
        // Without this the scroll pane never sees the wheel, because this component listens to it.
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (scroll != null) {
            scroll.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, scroll));
        }
    }

    private void pan(Point screen) {
        JViewport viewport = viewport();
        if (viewport == null) {
            return;
        }
        Point view = viewport.getViewPosition();
        Dimension extent = viewport.getExtentSize();
        int x = clamp(view.x - (screen.x - panFrom.x), 0, Math.max(0, getWidth() - extent.width));
        int y = clamp(view.y - (screen.y - panFrom.y), 0, Math.max(0, getHeight() - extent.height));
        viewport.setViewPosition(new Point(x, y));
        panFrom = screen;
    }

    // ------------------------------------------------------------ zoom

    /** Zooms one notch in (+1) or out (-1), keeping the map point under {@code at} where it is. */
    void zoomBy(int notches, Point at) {
        int next = notches > 0
                ? Math.max(zoom + 1, (int) Math.round(zoom * 1.2))
                : Math.min(zoom - 1, (int) Math.round(zoom / 1.2));
        setZoom(clamp(next, MIN_ZOOM, MAX_ZOOM), at);
    }

    /** Sets the cell size in pixels, keeping the map point under {@code at} (or the view centre) still. */
    void setZoom(int cellSize, Point at) {
        cellSize = clamp(cellSize, MIN_ZOOM, MAX_ZOOM);
        JViewport viewport = viewport();
        if (viewport == null) {
            zoom = cellSize;
            revalidate();
            repaint();
            return;
        }
        Rectangle visible = viewport.getViewRect();
        if (at == null) {
            at = new Point(visible.x + visible.width / 2, visible.y + visible.height / 2);
        }
        double fx = mapX(at.x);
        double fy = mapY(at.y);
        int offsetX = at.x - visible.x;
        int offsetY = at.y - visible.y;

        zoom = cellSize;
        Dimension preferred = getPreferredSize();
        Dimension extent = viewport.getExtentSize();
        setSize(Math.max(preferred.width, extent.width), Math.max(preferred.height, extent.height));
        int x = (int) Math.round(originX() + fx * zoom) - offsetX;
        int y = (int) Math.round(originY() + fy * zoom) - offsetY;
        viewport.setViewPosition(new Point(
                clamp(x, 0, Math.max(0, getWidth() - extent.width)),
                clamp(y, 0, Math.max(0, getHeight() - extent.height))));
        revalidate();
        repaint();
        listener.message("Zoom: " + zoom + " pixels per cell.");
    }

    /** Picks the zoom that shows the whole map (within limits) and centres it. */
    void zoomToFit() {
        JViewport viewport = viewport();
        if (viewport == null || viewport.getWidth() <= 0) {
            return;
        }
        DungeonMap map = doc.map();
        Dimension extent = viewport.getExtentSize();
        int fit = Math.min((extent.width - 2 * MARGIN) / map.width(), (extent.height - 2 * MARGIN) / map.height());
        zoom = clamp(fit, MIN_ZOOM, MAX_ZOOM);
        Dimension preferred = getPreferredSize();
        setSize(Math.max(preferred.width, extent.width), Math.max(preferred.height, extent.height));
        viewport.setViewPosition(new Point(
                Math.max(0, (getWidth() - extent.width) / 2),
                Math.max(0, (getHeight() - extent.height) / 2)));
        revalidate();
        repaint();
    }

    private JViewport viewport() {
        return getParent() instanceof JViewport viewport ? viewport : null;
    }

    // ------------------------------------------------------------ Scrollable

    @Override
    public Dimension getPreferredSize() {
        DungeonMap map = doc.map();
        return new Dimension(map.width() * zoom + 2 * MARGIN, map.height() * zoom + 2 * MARGIN);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return zoom;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        int size = orientation == SwingConstants.HORIZONTAL ? visibleRect.width : visibleRect.height;
        return Math.max(zoom, size - zoom * 2);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        JViewport viewport = viewport();
        return viewport != null && viewport.getWidth() > getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        JViewport viewport = viewport();
        return viewport != null && viewport.getHeight() > getPreferredSize().height;
    }

    // ------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            DungeonMap map = doc.map();
            int z = zoom;
            int ox = originX();
            int oy = originY();
            int width = map.width();
            int height = map.height();
            Rectangle clip = g.getClipBounds() != null ? g.getClipBounds() : new Rectangle(getSize());
            g.setColor(BACKGROUND);
            g.fill(clip);

            // Only the cells that are on screen.
            int x0 = clamp(Math.floorDiv(clip.x - ox, z), 0, width - 1);
            int x1 = clamp(Math.floorDiv(clip.x + clip.width - ox, z), 0, width - 1);
            int y0 = clamp(Math.floorDiv(clip.y - oy, z), 0, height - 1);
            int y1 = clamp(Math.floorDiv(clip.y + clip.height - oy, z), 0, height - 1);

            boolean[] unreachable = showUnreachable ? doc.check().unreachable() : null;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int px = ox + x * z;
                    int py = oy + y * z;
                    if (map.cell(x, y) == CellType.ROCK) {
                        g.setPaint(rockPaint);
                        g.fillRect(px, py, z, z);
                    } else {
                        g.setColor(FLOOR);
                        g.fillRect(px, py, z, z);
                        if (unreachable != null && unreachable[y * width + x]) {
                            g.setColor(UNREACHABLE);
                            g.fillRect(px, py, z, z);
                        }
                    }
                }
            }

            g.setColor(GRID);
            for (int x = x0; x <= x1 + 1; x++) {
                g.drawLine(ox + x * z, oy + y0 * z, ox + x * z, oy + (y1 + 1) * z);
            }
            for (int y = y0; y <= y1 + 1; y++) {
                g.drawLine(ox + x0 * z, oy + y * z, ox + (x1 + 1) * z, oy + y * z);
            }
            g.setColor(OUTLINE);
            g.drawRect(ox - 1, oy - 1, width * z + 2, height * z + 2);

            float stroke = Math.max(2f, z / 9f);
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            for (int line = y0; line <= y1 + 1; line++) {
                for (int x = x0; x <= x1; x++) {
                    drawEdge(g, map, new EdgeRef(true, x, line), ox, oy);
                }
            }
            for (int y = y0; y <= y1; y++) {
                for (int line = x0; line <= x1 + 1; line++) {
                    drawEdge(g, map, new EdgeRef(false, line, y), ox, oy);
                }
            }

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for (MapObject object : map.objects()) {
                if (object.x() >= x0 && object.x() <= x1 && object.y() >= y0 && object.y() <= y1) {
                    drawObject(g, object, ox + object.x() * z, oy + object.y() * z, 1f);
                }
            }
            drawArrow(g, ox + (map.startX() + 0.5) * z, oy + (map.startY() + 0.5) * z,
                    map.startFacing().angle(), z, START, 255);
            drawToolOverlay(g, map, ox, oy);
            drawRulers(g, map, ox, oy, x0, x1, y0, y1);
        } finally {
            g.dispose();
        }
    }

    private void drawEdge(Graphics2D g, DungeonMap map, EdgeRef edge, int ox, int oy) {
        EdgeType type = edge.get(map);
        if (type == EdgeType.OPEN) {
            return;
        }
        boolean floorBefore = map.cell(edge.beforeX(), edge.beforeY()).walkable;
        boolean floorAfter = map.cell(edge.afterX(), edge.afterY()).walkable;
        if (type == EdgeType.WALL && !floorBefore && !floorAfter) {
            return; // inside solid rock: nothing to see
        }
        int z = zoom;
        int x0 = ox + edge.x() * z;
        int y0 = oy + edge.y() * z;
        int x1 = edge.horizontal() ? x0 + z : x0;
        int y1 = edge.horizontal() ? y0 : y0 + z;
        g.setColor(WALL);
        g.drawLine(x0, y0, x1, y1);
        if (type == EdgeType.DOOR) {
            int length = Math.max(6, z / 2);
            int thick = Math.max(5, z / 4);
            Rectangle door = edge.horizontal()
                    ? new Rectangle(x0 + (z - length) / 2, y0 - thick / 2, length, thick)
                    : new Rectangle(x0 - thick / 2, y0 + (z - length) / 2, thick, length);
            g.setColor(DOOR);
            g.fill(door);
            Graphics2D thin = (Graphics2D) g.create();
            thin.setStroke(new BasicStroke(1f));
            thin.setColor(DOOR_EDGE);
            thin.drawRect(door.x, door.y, door.width - 1, door.height - 1);
            thin.dispose();
        }
    }

    private void drawToolOverlay(Graphics2D g, DungeonMap map, int ox, int oy) {
        int z = zoom;
        boolean erasing = strokeButton == MouseEvent.BUTTON3;
        Color accent = erasing ? ERASE : HOVER;

        if (tool == Tool.ROOM && strokeButton != MouseEvent.NOBUTTON) {
            int left = Math.min(anchorX, lastCellX);
            int top = Math.min(anchorY, lastCellY);
            int cols = Math.abs(anchorX - lastCellX) + 1;
            int rows = Math.abs(anchorY - lastCellY) + 1;
            Rectangle area = new Rectangle(ox + left * z, oy + top * z, cols * z, rows * z);
            g.setColor(withAlpha(accent, 50));
            g.fill(area);
            g.setColor(accent);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
            g.draw(area);
            drawLabel(g, cols + " x " + rows + (erasing ? " rock" : " room"), area.x + area.width + 6, area.y + area.height + 4);
            return;
        }

        if (tool.placesObjects() && movingObject != null && strokeButton == MouseEvent.BUTTON1
                && (lastCellX != anchorX || lastCellY != anchorY)) {
            DungeonMap copy = map.copy();
            copy.removeObject(movingObject.x(), movingObject.y());
            boolean ok = MapEdits.objectProblem(copy, movingObject.type(), movingObject.solid(), lastCellX, lastCellY) == null;
            int px = ox + lastCellX * z;
            int py = oy + lastCellY * z;
            drawObject(g, movingObject, px, py, 0.6f);
            g.setColor(ok ? HOVER : ERASE);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(px + 1, py + 1, z - 2, z - 2);
            return;
        }

        if (tool.editsEdges()) {
            if (hoverEdge != null && hoverEdge.isInterior(map)) {
                Color color = tool == Tool.DOOR && !erasing ? DOOR : accent;
                int x0 = ox + hoverEdge.x() * z;
                int y0 = oy + hoverEdge.y() * z;
                g.setColor(withAlpha(color, 170));
                g.setStroke(new BasicStroke(Math.max(5f, z / 5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x0, y0, hoverEdge.horizontal() ? x0 + z : x0, hoverEdge.horizontal() ? y0 : y0 + z);
            }
        } else if (hoverX >= 0) {
            g.setColor(accent);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(ox + hoverX * z + 1, oy + hoverY * z + 1, z - 2, z - 2);
            if (tool == Tool.START && (hoverX != map.startX() || hoverY != map.startY())
                    && map.cell(hoverX, hoverY).walkable) {
                drawArrow(g, ox + (hoverX + 0.5) * z, oy + (hoverY + 0.5) * z,
                        map.startFacing().angle(), z, START, 110);
            }
        }

        // A small tick on the hovered floor cell shows which way the 3D preview is looking.
        if (hoverX >= 0 && map.cell(hoverX, hoverY).walkable && strokeButton == MouseEvent.NOBUTTON) {
            double cx = ox + (hoverX + 0.5) * z;
            double cy = oy + (hoverY + 0.5) * z;
            Graphics2D t = (Graphics2D) g.create();
            t.translate(cx, cy);
            t.rotate(previewFacing.angle());
            double s = z / 2.0;
            Polygon tick = new Polygon(
                    new int[]{(int) Math.round(-s * 0.3), (int) Math.round(s * 0.3), 0},
                    new int[]{(int) Math.round(-s * 0.55), (int) Math.round(-s * 0.55), (int) Math.round(-s * 0.85)},
                    3);
            t.setColor(withAlpha(HOVER, 200));
            t.fillPolygon(tick);
            t.dispose();
        }
    }

    /** An object's picture squeezed into its cell; events also get a "!" badge. */
    private void drawObject(Graphics2D graphics, MapObject object, int px, int py, float alpha) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            if (alpha < 1f) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }
            int z = zoom;
            Sprite sprite = looks.look(object, false);
            boolean hasPicture = sprite != null;
            if (hasPicture) {
                BufferedImage image = sprite.image(0);
                int pad = Math.max(2, z / 8);
                int box = z - 2 * pad;
                double scale = Math.min(box / (double) image.getWidth(), box / (double) image.getHeight());
                int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 1
                        ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (object.wall() == null) {
                    g.drawImage(image, px + (z - w) / 2, py + pad + (box - h), w, h, null);
                } else { // half size, against its wall, with the wall picked out
                    Direction side = object.wall();
                    int cx = px + z / 2 + side.dx * z / 5;
                    int cy = py + z / 2 + side.dy * z / 5;
                    g.drawImage(image, cx - w / 4, cy - h / 4, w / 2, h / 2, null);
                    g.setColor(DOOR);
                    g.setStroke(new BasicStroke(Math.max(3f, z / 9f)));
                    int ex = px + z / 2 + side.dx * z / 2;
                    int ey = py + z / 2 + side.dy * z / 2;
                    g.drawLine(ex - Math.abs(side.dy) * z / 3, ey - Math.abs(side.dx) * z / 3,
                            ex + Math.abs(side.dy) * z / 3, ey + Math.abs(side.dx) * z / 3);
                }
            }
            if (object.type() == ObjectType.EVENT) {
                // Big in the middle when the event is invisible, a small badge in the corner otherwise.
                double size = hasPicture ? Math.max(9, z * 0.36) : Math.max(10, z * 0.62);
                double cx = hasPicture ? px + z - size / 2 - 1 : px + z / 2.0;
                double cy = hasPicture ? py + size / 2 + 1 : py + z / 2.0;
                g.setColor(EVENT);
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - size / 2, cy - size / 2, size, size));
                g.setColor(new Color(40, 10, 60));
                g.setStroke(new BasicStroke(1f));
                g.draw(new java.awt.geom.Ellipse2D.Double(cx - size / 2, cy - size / 2, size, size));
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(8, (int) Math.round(size * 0.75))));
                FontMetrics fm = g.getFontMetrics();
                g.drawString("!", (float) (cx - fm.stringWidth("!") / 2.0), (float) (cy + fm.getAscent() * 0.36));
            }
        } finally {
            g.dispose();
        }
    }

    private void drawRulers(Graphics2D g, DungeonMap map, int ox, int oy, int x0, int x1, int y0, int y1) {
        int z = zoom;
        int step = z >= 22 ? 1 : z >= 13 ? 2 : 5;
        g.setFont(RULER_FONT);
        FontMetrics fm = g.getFontMetrics();
        for (int x = x0; x <= x1; x++) {
            if (x % step != 0 && x != hoverX) {
                continue;
            }
            String text = Integer.toString(x);
            g.setColor(x == hoverX ? START : RULER);
            g.drawString(text, ox + x * z + (z - fm.stringWidth(text)) / 2, oy - 8);
        }
        for (int y = y0; y <= y1; y++) {
            if (y % step != 0 && y != hoverY) {
                continue;
            }
            String text = Integer.toString(y);
            g.setColor(y == hoverY ? START : RULER);
            g.drawString(text, ox - 8 - fm.stringWidth(text), oy + y * z + (z + fm.getAscent()) / 2 - 2);
        }
    }

    private static void drawLabel(Graphics2D g, String text, int x, int y) {
        g.setFont(LABEL_FONT);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 10;
        int h = fm.getHeight() + 4;
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 5, y + 2 + fm.getAscent());
    }

    private static void drawArrow(Graphics2D graphics, double px, double py, double angle, int cell, Color color, int alpha) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(px, py);
            g.rotate(angle); // positive angles turn clockwise on screen, matching map headings
            double s = cell / 2.0;
            Polygon arrow = new Polygon(
                    new int[]{0, (int) Math.round(s * 0.6), 0, (int) Math.round(-s * 0.6)},
                    new int[]{(int) Math.round(-s * 0.75), (int) Math.round(s * 0.6),
                            (int) Math.round(s * 0.3), (int) Math.round(s * 0.6)},
                    4);
            g.setColor(withAlpha(color, alpha));
            g.fillPolygon(arrow);
            g.setColor(new Color(0, 0, 0, alpha));
            g.setStroke(new BasicStroke(1f));
            g.drawPolygon(arrow);
        } finally {
            g.dispose();
        }
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /** Diagonal hatching for solid rock. */
    private static TexturePaint hatch() {
        int size = 8;
        BufferedImage tile = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                tile.setRGB(x, y, ((x + y) % size == 0 ? ROCK_HATCH : ROCK).getRGB());
            }
        }
        return new TexturePaint(tile, new Rectangle(0, 0, size, size));
    }
}
