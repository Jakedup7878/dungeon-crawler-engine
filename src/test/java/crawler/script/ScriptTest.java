package crawler.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The item script language by itself. */
class ScriptTest {
    /** Writes down what a script does. */
    private static final class Recorder implements Script.Host {
        final List<String> did = new ArrayList<>();
        int roll;

        @Override
        public int number(String name) {
            return switch (name) {
                case "user.level" -> 3;
                case "target.hp" -> 7;
                case "damage" -> 10;
                default -> 0;
            };
        }

        @Override
        public String name(String who) {
            return who.toUpperCase();
        }

        @Override
        public int random(int bound) {
            return Math.min(roll, bound - 1);
        }

        @Override
        public void command(String command, String who, String stat, int[] n) {
            did.add((command + " " + who + " " + stat + " " + java.util.Arrays.toString(n)).replaceAll(" +", " "));
        }

        @Override
        public void say(String text) {
            did.add("say " + text);
        }
    }

    private static List<String> run(String body, int roll) {
        Recorder recorder = new Recorder();
        recorder.roll = roll;
        Script.parse("on use\n" + body).run("use", recorder);
        return recorder.did;
    }

    static Stream<Arguments> sums() {
        return Stream.of(Arguments.of("2 + 3 * 4", 14), Arguments.of("(2 + 3) * 4", 20), Arguments.of("10 - 3 - 2", 5),
                Arguments.of("-5 + 2", -3), Arguments.of("7 / 2", 3), Arguments.of("7 % 4", 3), Arguments.of("7 / 0", 0),
                Arguments.of("user.level * 2", 6), Arguments.of("1 < 2 and not 3 < 2", 1), Arguments.of("0 or 2 == 3", 0),
                Arguments.of("999999 * 999999", 1_000_000), Arguments.of("random(5, 9)", 5), Arguments.of("random(9, 5)", 5),
                Arguments.of("damage / 4", 2));
    }

    @ParameterizedTest
    @MethodSource("sums")
    void sumsWorkAsExpected(String sum, int expected) {
        assertEquals(List.of("heal user [" + expected + "]"), run("heal user " + sum, 0));
    }

    @Test
    void commandsTakeTheirArguments() {
        assertEquals(List.of("poison target [3, 4]", "boost user attack [5, 2]", "cure party []", "gold [9]"),
                run("poison target 3 for 4\nboost user attack 5, 2\ncure party\ngold 9", 0));
    }

    @Test
    void ifElseRepeatAndVariables() {
        String body = "if chance(30)\n  heal user 1\nelse\n  heal user 2\nend\nset n = 1\nrepeat 3\n  set n = n * 2\nend\n"
                + "say \"{user} # {n} {item} {1 + 1}\"  # a comment\nstop\nheal user 99";
        assertEquals(List.of("heal user [1]", "say USER # 8 ITEM 2"), run(body, 29));
        assertEquals("heal user [2]", run(body, 30).get(0));
    }

    @Test
    void aRunawayScriptIsStopped() {
        List<String> did = run("repeat 1000000\n  repeat 1000000\n    heal user 1\n  end\nend", 0);
        assertTrue(did.size() <= Script.MAX_STEPS);
        assertTrue(run("repeat 1000000\n repeat 1000000\n end\nend\nheal user 1", 0).isEmpty());
    }

    @Test
    void blocksAreFoundByTrigger() {
        Script script = Script.parse("on hit\n heal user 1\n\non hurt\n heal user 2");
        assertTrue(script.has("hit") && script.has("hurt"));
        assertFalse(script.has("use"));
        assertFalse(Script.parse("  ").has("use"));
    }

    static Stream<Arguments> mistakes() {
        return Stream.of(
                Arguments.of("heal user 5", "line 1: a script is made of blocks"),
                Arguments.of("on jump", "line 1: expected what the block is for (use"),
                Arguments.of("on use\n\n  fly user", "line 3: unknown command 'fly'"),
                Arguments.of("on use\nheal everyone 5", "line 2: expected who to heal"),
                Arguments.of("on use\nheal user", "line 2: expected a number"),
                Arguments.of("on use\nheal user 5 6", "line 2: didn't expect '6'"),
                Arguments.of("on use\nheal user x", "'x' isn't a number"),
                Arguments.of("on use\nheal user user.beauty", "there is no 'user.beauty'"),
                Arguments.of("on use\nif 1\nheal user 2", "line 2: there is no 'end'"),
                Arguments.of("on use\nif 1\non hit", "line 2: there is no 'end'"),
                Arguments.of("on use\nend", "unknown command 'end'"),
                Arguments.of("on use\nsay \"oops", "closing quote"),
                Arguments.of("on use\nsay \"{oops\"", "{ without a }"),
                Arguments.of("on use\npoison target 3", "needs a number of rounds"),
                Arguments.of("on use\nboost user hp 3 for 2", "expected a stat"),
                Arguments.of("on use\nheal user 99999999", "too big"),
                Arguments.of("on use\non use", "there twice"),
                Arguments.of("on use\nset damage = 3", "can't be set"),
                Arguments.of("on use\nheal user " + "(".repeat(500), "too many parentheses"),
                Arguments.of("on use\n" + "if 1\n".repeat(500), "inside each other"));
    }

    @ParameterizedTest
    @MethodSource("mistakes")
    void mistakesNameTheLine(String script, String message) {
        String said = assertThrows(IllegalArgumentException.class, () -> Script.parse(script)).getMessage();
        assertTrue(said.startsWith("line ") && said.contains(message), said);
    }
}
