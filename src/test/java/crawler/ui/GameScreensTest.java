package crawler.ui;

import crawler.Main;
import crawler.battle.Battle;
import crawler.game.Game;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game screen driven by key presses, without a window: party creation, a battle from
 * the first order to victory, and starting again after defeat.
 */
class GameScreensTest {
    private GamePanel panel;
    private Game game;

    private void start(long seed, boolean partyCreation) throws IOException {
        Main.Loaded sample = Main.load(null);
        panel = new GamePanel(() -> game = new Game(sample.map(), sample.content(), new Random(seed)),
                sample.assets(), partyCreation);
        panel.setSize(960, 640);
    }

    private void key(int code) {
        panel.getKeyListeners()[0].keyPressed(new KeyEvent(panel, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED));
        panel.getKeyListeners()[0].keyReleased(new KeyEvent(panel, KeyEvent.KEY_RELEASED, 0, 0, code, KeyEvent.CHAR_UNDEFINED));
        run(0.02);
    }

    private void type(String text) {
        for (char c : text.toCharArray()) {
            panel.getKeyListeners()[0].keyTyped(new KeyEvent(panel, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c));
        }
    }

    private void run(double seconds) {
        for (double t = 0; t < seconds; t += 0.02) {
            panel.update(0.02);
        }
    }

    /** Painting must work in every state; this also catches exceptions in drawing code. */
    private void paint() {
        BufferedImage image = new BufferedImage(960, 640, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();
    }

    @Test
    void theSampleGameLoadsAsAProject() throws IOException {
        Main.Loaded sample = Main.load(null);
        assertEquals("Sample Quest - B1F - The Old Cellars", sample.title());
        assertEquals(3, sample.content().enemies().size());
        assertTrue(sample.map().encounters().active());
    }

    @Test
    void playersCanCreateTheirOwnParty() throws IOException {
        start(1, true);
        paint();
        key(KeyEvent.VK_ENTER);           // Add a member
        type("Kira");
        key(KeyEvent.VK_RIGHT);           // Mage
        paint();
        key(KeyEvent.VK_ENTER);           // add her
        key(KeyEvent.VK_DOWN);            // Remove
        key(KeyEvent.VK_DOWN);            // Use the ready-made party
        key(KeyEvent.VK_DOWN);            // Begin the adventure
        key(KeyEvent.VK_ENTER);
        assertEquals(1, game.heroes().size());
        assertEquals("Kira", game.heroes().get(0).name());
        assertEquals("Mage", game.heroes().get(0).characterClass());
        paint();
    }

    @Test
    void theReadyMadePartyCanBeChosen() throws IOException {
        start(1, true);
        key(KeyEvent.VK_DOWN);            // with nobody added: Add, Use the ready-made party
        key(KeyEvent.VK_ENTER);
        assertEquals(List.of("Aldric", "Mira", "Tobin"), game.heroes().stream().map(h -> h.name()).toList());
    }

    @Test
    void aBattleIsPlayedThroughToVictory() throws IOException {
        start(5, false);
        game.startBattle(List.of(game.content().enemy("giant-rat")), true);
        run(0.1);
        paint();
        Battle battle = game.battle();
        assertNotNull(battle);
        for (int i = 0; i < 400 && game.battle() != null; i++) {
            key(KeyEvent.VK_ENTER); // attack whoever's there; hurry the animations; close the result
            if (i % 25 == 0) {
                paint();
            }
            run(0.1);
        }
        assertNull(game.battle(), "the battle screen closed");
        assertEquals(Battle.Outcome.VICTORY, battle.outcome());
        assertFalse(game.isGameOver());
        assertEquals(22, game.gold(), "20 to start with, plus 2 from the rat");
    }

    @Test
    void afterDefeatEnterStartsAgain() throws IOException {
        start(5, false);
        Game first = game;
        game.startBattle(List.of(game.content().enemy("rat-king"), game.content().enemy("rat-king"),
                game.content().enemy("rat-king"), game.content().enemy("rat-king")), true);
        run(0.1);
        for (int i = 0; i < 2000 && !first.isGameOver(); i++) {
            key(KeyEvent.VK_DOWN);  // Defend: the party just holds on, and loses
            key(KeyEvent.VK_ENTER);
            run(0.05);
        }
        assertTrue(first.isGameOver());
        paint();
        key(KeyEvent.VK_ENTER);
        assertTrue(game != first, "a new game started");
        assertFalse(game.isGameOver());
        assertEquals(3, game.heroes().size());
    }
}
