package crawler.ui;

import crawler.audio.MusicPlayer;
import crawler.battle.Fighter;
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static crawler.ui.Hud.BACKGROUND;
import static crawler.ui.Hud.DIM;
import static crawler.ui.Hud.FONT;
import static crawler.ui.Hud.FRAME;
import static crawler.ui.Hud.FRAME_INNER;
import static crawler.ui.Hud.HIGHLIGHT;
import static crawler.ui.Hud.SMALL_FONT;
import static crawler.ui.Hud.TEXT;
import static crawler.ui.Hud.drawFrame;
import static crawler.ui.Hud.fit;

/**
 * The game screen: first-person view, automap, status, message log and party. Messages from
 * chests, characters and events appear in a box over the view until dismissed; battles take
 * over the view ({@link BattleScreen}); a new game may start with {@link PartyCreationScreen}.
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
    private static final Rectangle LOG_AREA = new Rectangle(16, 512, 384, 112);
    private static final Rectangle PARTY_AREA = new Rectangle(416, 512, 240, 112);
    /** Internal 3D resolution; scaled up 2x into VIEW. */
    private static final int RENDER_W = 320;
    private static final int RENDER_H = 240;

    private static final double STEP_SECONDS = 0.18;
    private static final double TURN_SECONDS = 0.16;
    private static final double BUMP_SECONDS = 0.16;

    private final Supplier<Game> newGame;
    private final AssetSource assets;
    private final boolean offerPartyCreation;
    private final Camera camera = new Camera();
    private final DungeonRenderer renderer;
    private final ObjectLooks looks;
    private final AutomapRenderer automap = new AutomapRenderer();
    private final InputHandler input = new InputHandler();
    private final MusicPlayer music;
    private final Timer timer;
    private Game game;
    private int walkableCells;
    private long lastTick;
    private boolean smoothMovement = true;
    private boolean revealMap;
    /** Seconds since the game started, for animated sprites. */
    private double clock;
    private GameMessage shownMessage;
    private int messagePage;
    private int messagePages = 1;
    private BattleScreen battleScreen;
    private PartyCreationScreen creation;

    /** A game drawn with the built-in look only (used by tests). */
    public GamePanel(Game game) {
        this(restartable(game), AssetSource.NONE, false);
    }

    /**
     * @param newGame            makes a fresh game: at the start, and again after the party falls
     * @param assets             where the game's own pictures and music come from
     * @param offerPartyCreation let the player make their own party, if the project allows it
     */
    public GamePanel(Supplier<Game> newGame, AssetSource assets, boolean offerPartyCreation) {
        this.newGame = newGame;
        this.assets = assets;
        this.offerPartyCreation = offerPartyCreation;
        this.game = newGame.get();
        this.renderer = new DungeonRenderer(RENDER_W, RENDER_H, Textures.forMap(game.map(), assets));
        this.looks = new ObjectLooks(assets);
        this.music = new MusicPlayer(assets);
        looks.setDefaults(game.content().settings());
        setPreferredSize(new Dimension(SCREEN_W, SCREEN_H));
        setBackground(BACKGROUND);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

        input.bindCommand(() -> revealMap = !revealMap, KeyEvent.VK_M);
        input.bindCommand(this::toggleSmoothMovement, KeyEvent.VK_F);
        input.bindCommand(this::toggleSound, KeyEvent.VK_V);
        input.bindCommand(() -> game.interact(), KeyEvent.VK_SPACE, KeyEvent.VK_ENTER);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                route(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                input.keyReleased(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                if (creation != null) {
                    creation.keyTyped(e);
                }
            }
        });
        addFocusListener(input);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });

        beginGame();
        timer = new Timer(1000 / 60, e -> tick());
        timer.setCoalesce(true);
    }

    /** The given game first, then fresh copies of it for restarts. */
    private static Supplier<Game> restartable(Game first) {
        Game[] next = {first};
        return () -> {
            Game game = next[0] != null ? next[0] : new Game(first.map(), first.content(), new Random());
            next[0] = null;
            return game;
        };
    }

    private void beginGame() {
        camera.snapTo(game.party());
        walkableCells = countWalkable(game.map());
        battleScreen = null;
        creation = offerPartyCreation && game.content().settings().partyCreation()
                ? new PartyCreationScreen(game.content(), assets, party -> {
                    game.replaceParty(party);
                    creation = null;
                })
                : null;
    }

    public void start() {
        lastTick = System.nanoTime();
        timer.start();
        requestFocusInWindow();
    }

    /** Stops the game loop and the music; call when the window closes. */
    public void stop() {
        timer.stop();
        music.stop();
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastTick) / 1e9);
        lastTick = now;
        update(dt);
        repaint();
    }

    /** Whoever should hear a key: party creation, a message, the battle, game over, or walking about. */
    private void route(KeyEvent e) {
        int key = e.getKeyCode();
        boolean confirm = key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER;
        if (creation != null) {
            creation.keyPressed(e);
        } else if (game.message() != null) {
            if (confirm) {
                continueMessage();
            }
        } else if (battleScreen != null) {
            battleScreen.keyPressed(e);
        } else if (game.isGameOver()) {
            if (confirm) {
                restart();
            }
        } else {
            input.keyPressed(e);
        }
    }

    /** Advances input and animation by {@code dt} seconds. */
    void update(double dt) {
        clock += dt;
        input.update(dt);
        camera.update(dt);
        if (creation == null && battleScreen == null && game.battle() != null && game.message() == null) {
            battleScreen = new BattleScreen(game, looks, music);
            input.cancelRepeat();
        }
        if (battleScreen != null && game.message() == null) {
            battleScreen.update(dt);
            if (battleScreen.isFinished()) {
                battleScreen = null;
            }
        }
        if (battleScreen != null) {
            battleScreen.updateMusic();
        } else {
            music.play(game.isGameOver() ? null : game.map().music());
        }

        boolean busy = creation != null || battleScreen != null || game.battle() != null
                || game.isGameOver() || game.message() != null;
        if (busy) {
            input.poll(); // movement keys do nothing meanwhile
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

    /** Space / Enter on a message: next page, or close it. */
    private void continueMessage() {
        if (messagePage + 1 < messagePages) {
            messagePage++;
        } else {
            game.dismissMessage();
        }
    }

    private void restart() {
        game = newGame.get();
        renderer.setTextures(Textures.forMap(game.map(), assets));
        beginGame();
    }

    private void toggleSmoothMovement() {
        smoothMovement = !smoothMovement;
        game.log().add("Smooth movement " + (smoothMovement ? "on." : "off."));
    }

    private void toggleSound() {
        music.setMuted(!music.isMuted());
        game.log().add("Sound " + (music.isMuted() ? "off." : "on."));
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
            if (creation != null) {
                creation.draw(g, SCREEN_W, SCREEN_H, clock);
                return;
            }

            renderer.render(game.map(), camera.viewX(), camera.viewY(), camera.viewAngle(),
                    looks.billboards(game.map(), game::isOpened), clock);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(renderer.frame(), VIEW.x, VIEW.y, VIEW.width, VIEW.height, null);
            drawFrame(g, VIEW);
            if (battleScreen != null) {
                battleScreen.draw(g, VIEW);
            } else {
                drawCompass(g);
                if (game.isGameOver()) {
                    drawGameOver(g);
                }
            }
            drawMessage(g);

            automap.draw(g, MAP_AREA, game.map(), game.exploration(),
                    camera.viewX(), camera.viewY(), camera.viewAngle(), revealMap);
            drawFrame(g, MAP_AREA);

            drawStatus(g);
            drawControls(g);
            drawLog(g);
            drawParty(g);
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

    private void drawGameOver(Graphics2D g) {
        g.setColor(new Color(40, 0, 0, 160));
        g.fill(VIEW);
        Rectangle box = new Rectangle(VIEW.x + VIEW.width / 2 - 200, VIEW.y + VIEW.height / 2 - 60, 400, 110);
        Hud.panel(g, box);
        g.setFont(Hud.BIG_FONT);
        g.setColor(Hud.BAD);
        Hud.centered(g, "Game Over", box.x + box.width / 2, box.y + 44);
        g.setFont(FONT);
        g.setColor(TEXT);
        Hud.centered(g, "Press Enter to start again", box.x + box.width / 2, box.y + 82);
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
        g.drawString("Gold      " + game.gold(), x, y + 60);
        g.drawString("Explored  " + game.exploration().visitedCount() + " / " + walkableCells, x, y + 80);
    }

    private void drawControls(Graphics2D g) {
        drawFrame(g, CONTROLS_AREA);
        String[][] rows = battleScreen != null ? new String[][]{
                {"Up / Down", "Choose"},
                {"Left/Right", "Target"},
                {"Enter", "Confirm"},
                {"Esc", "Back"},
                {"V", "Sound: " + (music.isMuted() ? "off" : "on")},
        } : new String[][]{
                {"W / Up", "Forward"},
                {"S / Down", "Back"},
                {"A / D", "Strafe"},
                {"Q / Left", "Turn left"},
                {"E / Right", "Turn right"},
                {"X", "Turn around"},
                {"Space", "Open / talk"},
                {"M", "Reveal map: " + (revealMap ? "on" : "off")},
                {"F", "Smooth: " + (smoothMovement ? "on" : "off")},
                {"V", "Sound: " + (music.isMuted() ? "off" : "on")},
        };
        g.setFont(SMALL_FONT);
        int x = CONTROLS_AREA.x + 12;
        int y = CONTROLS_AREA.y + 22;
        for (String[] row : rows) {
            g.setColor(HIGHLIGHT);
            g.drawString(row[0], x, y);
            g.setColor(TEXT);
            g.drawString(row[1], x + 104, y);
            y += 17;
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
        Hud.panel(g, box);

        int textX = box.x + 16;
        Sprite portrait = message.source() == null ? null
                : looks.look(message.source(), game.isOpened(message.source()));
        if (portrait != null) {
            int size = boxH - 32;
            Rectangle frame = new Rectangle(box.x + 16, box.y + 16, size, size);
            g.setColor(new Color(22, 26, 40));
            g.fill(frame);
            Hud.drawFitted(g, portrait.image(clock), new Rectangle(frame.x + 4, frame.y + 4, size - 8, size - 8), 8);
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
        List<String> lines = Hud.wrap(message.text(), fm, box.x + box.width - 16 - textX);
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

    private void drawLog(Graphics2D g) {
        g.setColor(BACKGROUND);
        g.fill(LOG_AREA);
        drawFrame(g, LOG_AREA);
        List<String> lines = game.log().recent(5);
        g.setFont(FONT);
        int y = LOG_AREA.y + 24;
        for (int i = 0; i < lines.size(); i++) {
            g.setColor(i == lines.size() - 1 ? TEXT : DIM);
            g.drawString(fit(g, lines.get(i), LOG_AREA.width - 24), LOG_AREA.x + 12, y);
            y += 20;
        }
    }

    /** Each party member: name, level and HP, with the one giving orders (or just hit) marked. */
    private void drawParty(Graphics2D g) {
        g.setColor(BACKGROUND);
        g.fill(PARTY_AREA);
        drawFrame(g, PARTY_AREA);
        List<Fighter> heroes = game.heroes();
        Fighter active = battleScreen == null ? null : battleScreen.activeHero();
        Fighter hurt = battleScreen == null ? null : battleScreen.hurtHero();
        int rowHeight = Math.min(18, (PARTY_AREA.height - 6) / Math.max(1, heroes.size()));
        int y = PARTY_AREA.y + 4;
        g.setFont(SMALL_FONT);
        for (Fighter hero : heroes) {
            int hp = battleScreen != null ? battleScreen.shownHp(hero) : hero.hp();
            if (hero == hurt) {
                g.setColor(new Color(160, 20, 20, 140));
                g.fillRect(PARTY_AREA.x + 2, y, PARTY_AREA.width - 4, rowHeight);
            } else if (hero == active) {
                g.setColor(new Color(255, 214, 92, 40));
                g.fillRect(PARTY_AREA.x + 2, y, PARTY_AREA.width - 4, rowHeight);
            }
            int baseline = y + rowHeight - 5;
            g.setColor(hp <= 0 ? Hud.BAD : hero == active ? HIGHLIGHT : TEXT);
            g.drawString(fit(g, hero.name(), 84), PARTY_AREA.x + 8, baseline);
            g.setColor(DIM);
            g.drawString("L" + hero.level(), PARTY_AREA.x + 96, baseline);
            g.setColor(hp <= 0 ? Hud.BAD : TEXT);
            String hpText = hp + "/" + hero.stats().hp();
            g.drawString(hpText, PARTY_AREA.x + 180 - g.getFontMetrics().stringWidth(hpText), baseline);
            Hud.bar(g, PARTY_AREA.x + 186, y + rowHeight / 2 - 3, 46, 6, hp, hero.stats().hp());
            y += rowHeight;
        }
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
