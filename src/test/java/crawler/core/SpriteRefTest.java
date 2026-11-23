package crawler.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteRefTest {

    @Test
    void aPlainPathIsAStillPicture() {
        SpriteRef sprite = SpriteRef.parse("assets/old-man.png");
        assertEquals("assets/old-man.png", sprite.image());
        assertEquals(1, sprite.frames());
        assertFalse(sprite.animated());
        assertEquals("assets/old-man.png", sprite.toString());
    }

    @Test
    void framesAndSpeedAreRead() {
        SpriteRef sprite = SpriteRef.parse("assets/slime.png, 4 frames, 7.5 fps");
        assertEquals("assets/slime.png", sprite.image());
        assertEquals(4, sprite.frames());
        assertEquals(7.5, sprite.fps());
        assertTrue(sprite.animated());
    }

    @Test
    void speedIsOptional() {
        SpriteRef sprite = SpriteRef.parse("assets/slime.png, 1 frame");
        assertEquals(1, sprite.frames());
        assertEquals(SpriteRef.DEFAULT_FPS, sprite.fps());
    }

    @ParameterizedTest
    @ValueSource(strings = {"assets/a.png", "assets/a.png, 4 frames, 8 fps", "assets/a.png, 2 frames, 12.5 fps"})
    void textFormSurvivesARoundTrip(String text) {
        assertEquals(text, SpriteRef.parse(text).toString());
    }

    @Test
    void blankMeansNone() {
        assertNull(SpriteRef.parse(""));
        assertNull(SpriteRef.parse(null));
        assertNull(SpriteRef.still(" "));
    }

    @Test
    void sillyNumbersAreKeptInRange() {
        SpriteRef sprite = new SpriteRef("a.png", 500, 1000);
        assertEquals(SpriteRef.MAX_FRAMES, sprite.frames());
        assertEquals(60, sprite.fps());
    }
}
