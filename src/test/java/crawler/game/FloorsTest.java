package crawler.game;

import crawler.Main;
import crawler.core.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Moving between floors. */
class FloorsTest {
    static final String GRID = "+---+---+---+---+\n| >             |\n+---+---+---+---+\n\n";
    static final String TOP = "name: Top\n" + GRID + "[settings]\nencounters = slime\nencounter_rate = 100\n\n"
            + "[stairs 0,0]\nname = Down you go\nto = maps/low.txt\narrive = 2,0,west\ntext = It's cold.\nwall = north\n";
    /** Stairs up in the middle of the floor at 1,0, a chest at 3,0, and a teleporter on the south wall of 2,0. */
    static final String LOW = "name: Low\n" + GRID + "[chest 3,0]\ncontents = Dust\n\n"
            + "[stairs 1,0]\nto = maps/top.txt\narrive = 0,0,s\nway = up\n\n[stairs 2,0]\nto = maps/low.txt\nwall = south\n";

    static ProjectFiles files(String top, String low) {
        return ProjectFiles.of(low == null ? Map.of("maps/top.txt", top.getBytes(StandardCharsets.UTF_8))
                : Map.of("maps/top.txt", top.getBytes(StandardCharsets.UTF_8), "maps/low.txt", low.getBytes(StandardCharsets.UTF_8)));
    }

    static Floors load(String top, String low) throws IOException {
        return Floors.load(files(top, low), "maps/top.txt", MapLoader.parse("top", top.lines().toList()));
    }

    @Test
    void stairsGoBothWaysAndFloorsRemember() throws IOException {
        Floors floors = load(TOP, LOW);
        assertEquals(2, floors.size());
        Game game = new Game(floors, GameTest.content(0), new Random(1));
        DungeonMap top = game.map();
        game.movement().perform(MoveAction.STRAFE_LEFT); // into the stairs on the north wall
        assertEquals("Low", game.map().name());
        assertEquals(1, game.travels());
        assertEquals("2,0,WEST", where(game));
        assertNull(game.battle(), "no battle on the stairs, even at 100%");
        assertEquals("It's cold.", game.message().text());
        game.dismissMessage();
        assertTrue(game.exploration().isVisited(2, 0));
        assertFalse(game.exploration().isVisited(0, 0), "a new floor is unexplored");

        game.movement().perform(MoveAction.TURN_AROUND);
        game.interact();
        assertTrue(game.isOpened(game.map().object(3, 0)));
        game.dismissMessage();
        game.movement().perform(MoveAction.TURN_AROUND);
        MoveResult into = game.movement().perform(MoveAction.FORWARD); // stairs in the middle of the floor
        assertEquals(MoveResult.Outcome.BLOCKED, into.outcome(), "they can't be walked onto");
        assertSame(top, game.map());
        assertEquals("0,0,SOUTH", where(game));
        assertTrue(game.exploration().isVisited(0, 0), "the first floor is remembered");

        game.movement().perform(MoveAction.TURN_AROUND);
        assertTrue(game.interact(), "Space takes the stairs in front");
        game.dismissMessage();
        assertTrue(game.isOpened(game.map().object(3, 0)), "the chest stays open");
        game.movement().perform(MoveAction.STRAFE_LEFT);
        assertEquals("0,0,EAST", where(game), "stairs to the same floor are a teleporter, here to its start");
        assertEquals("Low", game.map().name());
    }

    private static String where(Game game) {
        return game.party().x() + "," + game.party().y() + "," + game.party().facing();
    }

    @Test
    void theOtherWallsAreJustWalls() throws IOException {
        Game game = new Game(load(TOP, LOW), GameTest.content(0), new Random(1));
        game.movement().perform(MoveAction.STRAFE_RIGHT);
        assertEquals(0, game.travels());
        assertTrue(!game.map().isBlocked(0, 0), "the cell of stairs on a wall is free");
    }

    @Test
    void stairsOnAWallThatIsntThereDoNothing() throws IOException {
        Game game = new Game(load(TOP.replace("wall = north", "wall = east"), LOW), GameTest.content(0), new Random(1));
        game.movement().perform(MoveAction.FORWARD);
        assertEquals("1,0,EAST", where(game));
    }

    @Test
    void arrivingOnStairsInTheMiddleStepsOffThem() {
        DungeonMap low = MapLoader.parse("low", LOW.lines().toList());
        assertEquals(new Floors.Arrival(2, 0, Direction.EAST), Floors.arrival(low, Destination.parse("x", "1,0,east", false)));
        assertEquals(new Floors.Arrival(0, 0, Direction.WEST), Floors.arrival(low, Destination.parse("x", "1,0,south", false)));
        assertNull(Floors.arrival(low, Destination.parse("x", "3,0,west", false)), "a chest is in the way");
    }

    @Test
    void stairsThatLeadNowhereBlockTheWay() throws IOException {
        Floors floors = load("name: T\n" + GRID + "[stairs 1,0]\n", null);
        Game game = new Game(floors, GameTest.content(0), new Random(1));
        game.movement().perform(MoveAction.FORWARD);
        assertEquals(0, game.travels());
        assertEquals("The way is blocked.", game.log().recent(1).get(0));
        assertEquals(0, game.party().x());
    }

    @Test
    void brokenStairsAreFoundWhenTheGameLoads() {
        assertTrue(assertThrows(IOException.class, () -> load(TOP, null)).getMessage().contains("maps/low.txt, which is missing"));
        assertTrue(assertThrows(IOException.class, () -> load(TOP.replace("2,0,west", "3,0,west"), LOW))
                .getMessage().contains("can't stand"));
        assertTrue(assertThrows(IOException.class, () -> load(TOP.replace("2,0,west", "9,9,west"), LOW))
                .getMessage().contains("outside"));
    }

    @Test
    void npcsCanBeWalkedThroughIfTheyAllowIt() {
        for (boolean through : new boolean[]{true, false}) {
            DungeonMap map = MapLoader.parse("t", (GRID + "[npc 1,0]\ntext = Boo" + (through ? "" : "\nwalk_through = no"))
                    .lines().toList());
            assertTrue(map.contentEquals(MapLoader.parse("t", MapWriter.write(map).lines().toList())));
            Game game = new Game(map, GameTest.content(0), new Random(1));
            if (through) {
                assertTrue(game.interact(), "Space talks to a ghost in front");
                game.dismissMessage();
            }
            game.movement().perform(MoveAction.FORWARD);
            assertEquals(through ? 1 : 0, game.party().x());
            assertEquals(through, game.message() == null);
            if (through) {
                assertTrue(game.interact(), "and to one in the same cell");
                assertEquals("Boo", game.message().text());
            }
        }
    }

    @Test
    void stairsSurviveTheMapFile() {
        for (String text : List.of(TOP, LOW)) {
            DungeonMap map = MapLoader.parse("t", text.lines().toList());
            DungeonMap back = MapLoader.parse("t", MapWriter.write(map).lines().toList());
            assertTrue(map.contentEquals(back));
        }
        for (String wrong : List.of("arrive = here", "arrive = 1,2", "arrive = 1,2,up", "way = left", "wall = up")) {
            assertTrue(assertThrows(IllegalArgumentException.class, () -> MapLoader.parse("t",
                    (GRID + "[stairs 1,0]\n" + wrong).lines().toList())).getMessage().contains("line 6"));
        }
    }

    @Test
    void theSampleGameHasTwoFloors() throws IOException {
        Main.Loaded sample = Main.load(null);
        assertEquals(2, sample.floors().size());
        for (String path : List.of("maps/b1f.txt", "maps/b2f.txt")) {
            DungeonMap map = sample.floors().get(path);
            boolean[] reached = Reachability.from(map, map.startX(), map.startY());
            for (MapObject o : map.objects()) {
                if (!o.solid()) {
                    assertTrue(reached[o.y() * map.width() + o.x()], path + " " + o.name());
                }
            }
        }
    }
}
