package crawler.ui;

import crawler.core.CellType;
import crawler.core.DungeonMap;
import crawler.core.MoveAction;
import crawler.core.MoveResult;
import crawler.game.Game;
import crawler.game.GameMessage;
import crawler.render.AssetSource;
import crawler.render.AutomapRenderer;
import crawler.render.Camera;
import crawler.render.DungeonRenderer;
import crawler.render.ObjectLooks;
import crawler.render.Sprite;
import crawler.render.Textures;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The game screen: first-person view, automap, status and message log. Messages from
 * chests, characters and events appear in a box over the view until dismissed.
 *
 * <p>Runs a 60 Hz loop on the Swing event thread, so game state, input and
 * painting all happen on one thread and need no locking.
 */
public final class GamePanel extends JPanel {
    // Everything is laid out on a fixed logical canvas, then scaled to fit the window.
    private static final int SCREEN_W = 960;
    private static final int SCREEN_H = 640;
    private static final Rectangle VIEW = new Rectangle(16, 16, 640, 480);
    private static final Rectangle MAP_AREA = new Rectangle(672, 16, 272, 272);
    private static final Rectangle STATUS_AREA = new Rectangle(672, 304, 272, 112);
    private static final Rectangle CONTROLS_AREA = new Rectangle(672, 432, 272, 192);
    private static final Rectangle LOG_AREA = new Rectangle(16, 512, 640, 112);
    /** Internal 3D resolution; scaled up 2x into VIEW. */
    private static final int RENDER_W = 320;
    private static final int RENDER_H = 240;

    private static final double STEP_SECONDS = 0.18;
    private static final double TURN_SECONDS = 0.16;
    private static final double BUMP_SECONDS = 0.16;

    private static final Color BACKGROUND = new Color(8, 8, 12);
    private static final Color FRAME = new Color(118, 128, 160);
    private static final Color FRAME_INNER = new Color(40, 44, 58);
    private static final Color TEXT = new Color(222, 224, 232);
    private static final Color DIM = new Color(128, 134, 152);
    private static final Color HIGHLIGHT = new Color(255, 214, 92);
    private static final Font FONT = new Font(Font.MONOSPACED, Font.BOLD, 15);
    private static final Font SMALL_FONT = new Font(Font.MONOSPACED, Font.BOLD, 13);

    private final Game game;
    private final Camera camera = new Camera();
    private final DungeonRenderer renderer;
    private final ObjectLooks looks;
    private final AutomapRenderer automap = new AutomapRenderer();
    private final InputHandler input = new InputHandler();
    private final Timer timer;
    private final int walkableCells;
    private long lastTick;
    private boolean smoothMovement = true;
    private boolean revealMap;
    /** Seconds since the game started, for animated sprites. */
    private double clock;
    private GameMessage shownMessage;
    private int messagePage;
    private int messagePages = 1;

    /** A game drawn with the built-in look only. */
    public GamePanel(Game game) {
        this(game, AssetSource.NONE);
    }

    /** A game drawn with the map's own textures and object images, read from {@code assets}. */
    public GamePanel(Game game, AssetSource assets) {
        this.game = game;
        this.renderer = new DungeonRenderer(RENDER_W, RENDER_H, Textures.forMap(game.map(), assets));
        this.looks = new ObjectLooks(assets);
        this.walkableCells = countWalkable(game.map());
        setPreferredSize(new Dimension(SCREEN_W, SCREEN_H));
        setBackground(BACKGROUND);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

        input.bindCommand(() -> revealMap = !revealMap, KeyEvent.VK_M);
        input.bindCommand(this::toggleSmoothMovement, KeyEvent.VK_F);
        input.bindCommand(this::interactOrContinue, KeyEvent.VK_SPACE, KeyEvent.VK_ENTER);
        addKeyListener(input);
        addFocusListener(input);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });

        camera.snapTo(game.party());
        timer = new Timer(1000 / 60, e -> tick());
        timer.setCoalesce(true);
    }

    public void start() {
        lastTick = System.nanoTime();
        timer.start();
        requestFocusInWindow();
    }

    /** Stops the game loop; call when the window closes. */
    public void stop() {
        timer.stop();
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastTick) / 1e9);
        lastTick = now;
        update(dt);
        repaint();
    }

    /** Advances input and animation by {@code dt} seconds. */
    void update(double dt) {
        clock += dt;
        input.update(dt);
        camera.update(dt);
        if (game.message() != null) {
            input.poll(); // read the message first: movement keys do nothing meanwhile
        } else if (!camera.isAnimating()) {
            MoveAction action = input.poll();
            if (action != null) {
                perform(action);
            }
        }
    }

    private void perform(MoveAction action) {
        MoveResult result = game.movement().perform(action);
        switch (result.outcome()) {
            case MOVED -> camera.moveTo(game.party(), smoothMovement ? STEP_SECONDS : 0);
            case TURNED -> camera.moveTo(game.party(), smoothMovement ? TURN_SECONDS : 0);
            case BLOCKED -> {
                camera.bump(result.travel(), BUMP_SECONDS);
                input.cancelRepeat(); // one "Ouch!" per key press, not a stream of them
            }
        }
    }

    /** Space / Enter: turn the page of a message, close it, or open / talk to what's ahead. */
    private void interactOrContinue() {
        if (game.message() != null) {
            if (messagePage + 1 < messagePages) {
                messagePage++;
            } else {
                game.dismissMessage();
            }
        } else {
            game.interact();
        }
    }

    private void toggleSmoothMovement() {
        smoothMovement = !smoothMovement;
        game.log().add("Smooth movement " + (smoothMovement ? "on." : "off."));
    }

    // ------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            double scale = Math.min(getWidth() / (double) SCREEN_W, getHeight() / (double) SCREEN_H);
            g.translate((getWidth() - SCREEN_W * scale) / 2, (getHeight() - SCREEN_H * scale) / 2);
            g.scale(scale, scale);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            renderer.render(game.map(), camera.viewX(), camera.viewY(), camera.viewAngle(),
                    looks.billboards(game.map(), game::isOpened), clock);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(renderer.frame(), VIEW.x, VIEW.y, VIEW.width, VIEW.height, null);
            drawFrame(g, VIEW);
            drawCompass(g);
            drawMessage(g);

            automap.draw(g, MAP_AREA, game.map(), game.exploration(),
                    camera.viewX(), camera.viewY(), camera.viewAngle(), revealMap);
            drawFrame(g, MAP_AREA);

            drawStatus(g);
            drawControls(g);
            drawLog(g);
        } finally {
            g.dispose();
        }
    }

    /** A strip of compass points that slides as you turn. */
    private void drawCompass(Graphics2D g) {
        int width = 180;
        int height = 24;
        int cx = VIEW.x + VIEW.width / 2;
        Rectangle strip = new Rectangle(cx - width / 2, VIEW.y + 8, width, height);
        String[] labels = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double pixelsPerQuarterTurn = 64;

        Graphics2D c = (Graphics2D) g.create();
        try {
            c.setColor(new Color(0, 0, 0, 160));
            c.fill(strip);
            c.setColor(FRAME);
            c.draw(strip);
            c.clip(strip);
            for (int i = 0; i < labels.length; i++) {
                double delta = Math.IEEEremainder(i * Math.PI / 4 - camera.viewAngle(), Math.PI * 2);
                int x = cx + (int) Math.round(delta / (Math.PI / 2) * pixelsPerQuarterTurn);
                boolean cardinal = i % 2 == 0;
                c.setFont(cardinal ? FONT : SMALL_FONT);
                c.setColor(cardinal ? (Math.abs(delta) < 0.3 ? HIGHLIGHT : TEXT) : DIM);
                FontMetrics fm = c.getFontMetrics();
                c.drawString(labels[i], x - fm.stringWidth(labels[i]) / 2, strip.y + 17);
            }
            c.setColor(HIGHLIGHT);
            c.fillPolygon(new int[]{cx - 4, cx + 4, cx}, new int[]{strip.y, strip.y, strip.y + 5}, 3);
        } finally {
            c.dispose();
        }
    }

    private void drawStatus(Graphics2D g) {
        drawFrame(g, STATUS_AREA);
        int x = STATUS_AREA.x + 12;
        int y = STATUS_AREA.y + 24;
        g.setFont(FONT);
        g.setColor(HIGHLIGHT);
        g.drawString(fit(g, game.map().name(), STATUS_AREA.width - 24), x, y);
        g.setColor(TEXT);
        g.drawString("Position  " + game.party().x() + ", " + game.party().y(), x, y + 20);
        g.drawString("Facing    " + game.party().facing().displayName(), x, y + 40);
        g.drawString("Steps     " + game.steps(), x, y + 60);
        g.drawString("Explored  " + game.exploration().visitedCount() + " / " + walkableCells, x, y + 80);
    }

    private void drawControls(Graphics2D g) {
        drawFrame(g, CONTROLS_AREA);
        String[][] rows = {
                {"W / Up", "Forward"},
                {"S / Down", "Back"},
                {"A / D", "Strafe"},
                {"Q / Left", "Turn left"},
                {"E / Right", "Turn right"},
                {"X", "Turn around"},
                {"Space", "Open / talk"},
                {"M", "Reveal map: " + (revealMap ? "on" : "off")},
                {"F", "Smooth: " + (smoothMovement ? "on" : "off")},
        };
        g.setFont(SMALL_FONT);
        int x = CONTROLS_AREA.x + 12;
        int y = CONTROLS_AREA.y + 24;
        for (String[] row : rows) {
            g.setColor(HIGHLIGHT);
            g.drawString(row[0], x, y);
            g.setColor(TEXT);
            g.drawString(row[1], x + 104, y);
            y += 19;
        }
    }

    /** The current message in a box over the lower part of the view, with a portrait for objects. */
    private void drawMessage(Graphics2D g) {
        GameMessage message = game.message();
        if (message == null) {
            shownMessage = null;
            return;
        }
        if (message != shownMessage) {
            shownMessage = message;
            messagePage = 0;
        }
        int boxH = 160;
        Rectangle box = new Rectangle(VIEW.x + 20, VIEW.y + VIEW.height - boxH - 16, VIEW.width - 40, boxH);
        g.setColor(new Color(6, 8, 16, 232));
        g.fill(box);
        drawFrame(g, box);

        int textX = box.x + 16;
        Sprite portrait = message.source() == null ? null
                : looks.look(message.source(), game.isOpened(message.source()));
        if (portrait != null) {
            int size = boxH - 32;
            Rectangle frame = new Rectangle(box.x + 16, box.y + 16, size, size);
            g.setColor(new Color(22, 26, 40));
            g.fill(frame);
            BufferedImage image = portrait.image(clock);
            double scale = Math.min((size - 8) / (double) image.getWidth(), (size - 8) / (double) image.getHeight());
            int w = (int) Math.round(image.getWidth() * scale);
            int h = (int) Math.round(image.getHeight() * scale);
            Graphics2D p = (Graphics2D) g.create();
            p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 1
                    ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            p.drawImage(image, frame.x + (size - w) / 2, frame.y + (size - h) / 2, w, h, null);
            p.dispose();
            g.setColor(FRAME_INNER);
            g.draw(frame);
            textX = frame.x + size + 18;
        }

        g.setFont(FONT);
        FontMetrics fm = g.getFontMetrics();
        int y = box.y + 30;
        if (!message.title().isEmpty()) {
            g.setColor(HIGHLIGHT);
            g.drawString(fit(g, message.title(), box.x + box.width - 16 - textX), textX, y);
            y += 26;
        }
        int lineHeight = 20;
        int bottom = box.y + box.height - 30;
        int perPage = Math.max(1, (bottom - y) / lineHeight + 1);
        List<String> lines = wrap(message.text(), fm, box.x + box.width - 16 - textX);
        messagePages = Math.max(1, (lines.size() + perPage - 1) / perPage);
        messagePage = Math.min(messagePage, messagePages - 1);
        g.setColor(TEXT);
        for (int i = messagePage * perPage; i < Math.min(lines.size(), (messagePage + 1) * perPage); i++) {
            g.drawString(lines.get(i), textX, y);
            y += lineHeight;
        }
        String hint = messagePage + 1 < messagePages ? "Space: more" : "Space: close";
        g.setFont(SMALL_FONT);
        g.setColor(DIM);
        g.drawString(hint, box.x + box.width - 14 - g.getFontMetrics().stringWidth(hint), box.y + box.height - 12);
    }

    /** Word-wraps text to a pixel width; line breaks in the text are kept. */
    private static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(candidate) > width && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private void drawLog(Graphics2D g) {
        g.setColor(BACKGROUND);
        g.fill(LOG_AREA);
        drawFrame(g, LOG_AREA);
        List<String> lines = game.log().recent(5);
        g.setFont(FONT);
        int y = LOG_AREA.y + 24;
        for (int i = 0; i < lines.size(); i++) {
            g.setColor(i == lines.size() - 1 ? TEXT : DIM);
            g.drawString(lines.get(i), LOG_AREA.x + 12, y);
            y += 20;
        }
    }

    private static void drawFrame(Graphics2D g, Rectangle r) {
        g.setStroke(new BasicStroke(2f));
        g.setColor(FRAME);
        g.drawRect(r.x - 4, r.y - 4, r.width + 7, r.height + 7);
        g.setStroke(new BasicStroke(1f));
        g.setColor(FRAME_INNER);
        g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);
    }

    private static String fit(Graphics2D g, String text, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && fm.stringWidth(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private static int countWalkable(DungeonMap map) {
        int count = 0;
        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) {
                if (map.cell(x, y) == CellType.FLOOR && !map.isBlocked(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }
}
