package crawler.script;

import crawler.script.Script.Expr;
import crawler.script.Script.Statement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Turns a script's text into statements, or says what's wrong and on which line. */
final class ScriptParser {
    private static final int MAX_NESTING = 50;

    /** What follows a command's name: who, a stat, and some numbers. */
    private record Form(boolean who, boolean stat, int numbers) {
    }

    private static final Map<String, Form> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("heal", new Form(true, false, 1));
        COMMANDS.put("mana", new Form(true, false, 1));
        COMMANDS.put("damage", new Form(true, false, 1));
        COMMANDS.put("revive", new Form(true, false, 1));
        COMMANDS.put("poison", new Form(true, false, 2));
        COMMANDS.put("stun", new Form(true, false, 1));
        COMMANDS.put("cure", new Form(true, false, 0));
        COMMANDS.put("boost", new Form(true, true, 2));
        COMMANDS.put("gold", new Form(false, false, 1));
    }

    /** @param kind 'a' a word, '0' a number, '"' text, or '+' a symbol */
    private record Token(char kind, String text) {
    }

    private final String[] lines;
    private int lineNumber;
    private final Set<String> variables = new HashSet<>();
    private List<Token> tokens = List.of();
    private int pos;
    private int parentheses;

    ScriptParser(String text) {
        lines = text.replace("\r", "").split("\n", -1);
    }

    Map<String, List<Statement>> parse() {
        Map<String, List<Statement>> blocks = new LinkedHashMap<>();
        boolean more = nextLine();
        while (more) {
            if (!accept("on")) {
                throw error("a script is made of blocks that start like 'on use' (use "
                        + String.join(", ", Script.TRIGGERS) + ")");
            }
            String trigger = choice("what the block is for", Script.TRIGGERS);
            if (blocks.containsKey(trigger)) {
                throw error("'on " + trigger + "' is there twice");
            }
            endOfLine();
            variables.clear();
            List<Statement> body = new ArrayList<>();
            while ((more = nextLine()) && !peek("on")) {
                body.add(statement(0));
            }
            blocks.put(trigger, body);
        }
        return blocks;
    }

    /** Moves to the next line with something on it; false at the end. */
    private boolean nextLine() {
        pos = 0;
        while (lineNumber < lines.length) {
            lineNumber++;
            tokens = tokenize(lines[lineNumber - 1]);
            if (!tokens.isEmpty()) {
                return true;
            }
        }
        tokens = List.of();
        return false;
    }

    // ------------------------------------------------------------ statements

    private Statement statement(int depth) {
        if (depth > MAX_NESTING) {
            throw error("too many ifs and repeats inside each other");
        }
        String command = word("a command");
        return switch (command) {
            case "if" -> ifStatement(depth);
            case "repeat" -> repeat(depth);
            case "set" -> set();
            case "say" -> {
                Token token = next();
                if (token == null || token.kind() != '"') {
                    throw error("say needs text in quotes, like say \"Ouch!\"");
                }
                endOfLine();
                yield message(token.text());
            }
            case "stop" -> {
                endOfLine();
                yield run -> run.stopped = true;
            }
            default -> command(command);
        };
    }

    private Statement ifStatement(int depth) {
        int opened = lineNumber;
        Expr condition = expression();
        endOfLine();
        List<Statement> yes = body(depth, opened, true);
        List<Statement> no = List.of();
        if (accept("else")) {
            endOfLine();
            no = body(depth, opened, false);
        }
        expect("end");
        endOfLine();
        List<Statement> otherwise = no;
        return run -> run.block(condition.eval(run) != 0 ? yes : otherwise);
    }

    private Statement repeat(int depth) {
        int opened = lineNumber;
        Expr times = expression();
        endOfLine();
        List<Statement> body = body(depth, opened, false);
        expect("end");
        endOfLine();
        return run -> {
            int n = times.eval(run);
            for (int i = 0; i < n && !run.stopped; i++) {
                run.step();
                run.block(body);
            }
        };
    }

    private Statement set() {
        String name = word("a variable's name");
        if (Script.NUMBERS.contains(name) || name.contains(".") || name.equals("chance") || name.equals("random")) {
            throw error("'" + name + "' can't be set; choose another name");
        }
        expect("=");
        Expr value = expression();
        endOfLine();
        variables.add(name);
        return run -> run.variables.put(name, value.eval(run));
    }

    private Statement command(String command) {
        Form form = COMMANDS.get(command);
        if (form == null) {
            throw error("unknown command '" + command + "' (use if, repeat, set, say, stop, "
                    + String.join(", ", COMMANDS.keySet()) + ")");
        }
        String who = form.who() ? choice("who to " + command, Script.TARGETS) : "";
        String stat = form.stat() ? choice("a stat", Script.STATS) : "";
        Expr[] numbers = new Expr[form.numbers()];
        for (int i = 0; i < numbers.length; i++) {
            if (i > 0 && !accept(",") && !accept("for")) {
                throw error(command + " needs a number of rounds, like '" + command + " " + who + " "
                        + (form.stat() ? stat + " " : "") + "3 for 4'");
            }
            numbers[i] = expression();
        }
        endOfLine();
        return run -> {
            int[] values = new int[numbers.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = numbers[i].eval(run);
            }
            run.host.command(command, who, stat, values);
        };
    }

    /** Statements up to {@code end} (or {@code else}), which is left as the next token. */
    private List<Statement> body(int depth, int openedOn, boolean allowElse) {
        List<Statement> into = new ArrayList<>();
        while (nextLine() && !peek("on")) {
            if (peek("end") || (allowElse && peek("else"))) {
                return into;
            }
            into.add(statement(depth + 1));
        }
        throw new IllegalArgumentException("line " + openedOn + ": there is no 'end' for this");
    }

    /** Text with {user}, {damage} or {1 + 2} in it. */
    private Statement message(String text) {
        List<Object> parts = new ArrayList<>();
        int at = 0;
        while (at < text.length()) {
            int open = text.indexOf('{', at);
            if (open < 0) {
                parts.add(text.substring(at));
                break;
            }
            int close = text.indexOf('}', open);
            if (close < 0) {
                throw error("there is a { without a }");
            }
            parts.add(text.substring(at, open));
            String inside = text.substring(open + 1, close).strip();
            if (inside.equals("user") || inside.equals("target") || inside.equals("item")) {
                parts.add(new String[]{inside});
            } else {
                tokens = tokenize(inside);
                pos = 0;
                parts.add(expression());
                endOfLine();
            }
            at = close + 1;
        }
        return run -> {
            StringBuilder out = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Expr e) {
                    out.append(e.eval(run));
                } else if (part instanceof String[] who) {
                    out.append(run.host.name(who[0]));
                } else {
                    out.append(part);
                }
            }
            run.host.say(out.toString());
        };
    }

    // ------------------------------------------------------------ expressions
    // Loosest first: or, and, not, comparison, + -, * / %, minus, values.

    private Expr expression() {
        if (++parentheses > MAX_NESTING) {
            throw error("there are too many parentheses inside each other");
        }
        Expr left = and();
        while (accept("or")) {
            Expr a = left;
            Expr b = and();
            left = run -> a.eval(run) != 0 || b.eval(run) != 0 ? 1 : 0;
        }
        parentheses--;
        return left;
    }

    private Expr and() {
        Expr left = not();
        while (accept("and")) {
            Expr a = left;
            Expr b = not();
            left = run -> a.eval(run) != 0 && b.eval(run) != 0 ? 1 : 0;
        }
        return left;
    }

    private Expr not() {
        boolean flip = false;
        while (accept("not")) {
            flip = !flip;
        }
        Expr value = comparison();
        return flip ? run -> value.eval(run) == 0 ? 1 : 0 : value;
    }

    private Expr comparison() {
        Expr a = sum();
        for (String op : new String[]{"<=", ">=", "==", "!=", "<", ">"}) {
            if (accept(op)) {
                Expr b = sum();
                return run -> {
                    int c = Integer.compare(a.eval(run), b.eval(run));
                    boolean yes = switch (op) {
                        case "<=" -> c <= 0;
                        case ">=" -> c >= 0;
                        case "==" -> c == 0;
                        case "!=" -> c != 0;
                        case "<" -> c < 0;
                        default -> c > 0;
                    };
                    return yes ? 1 : 0;
                };
            }
        }
        return a;
    }

    private Expr sum() {
        Expr left = product();
        while (true) {
            Expr a = left;
            if (accept("+")) {
                Expr b = product();
                left = run -> Script.limit((long) a.eval(run) + b.eval(run));
            } else if (accept("-")) {
                Expr b = product();
                left = run -> Script.limit((long) a.eval(run) - b.eval(run));
            } else {
                return left;
            }
        }
    }

    private Expr product() {
        Expr left = unary();
        while (true) {
            Expr a = left;
            if (accept("*")) {
                Expr b = unary();
                left = run -> Script.limit((long) a.eval(run) * b.eval(run));
            } else if (accept("/")) {
                Expr b = unary();
                left = run -> { // dividing by nothing gives nothing
                    int by = b.eval(run);
                    return by == 0 ? 0 : a.eval(run) / by;
                };
            } else if (accept("%")) {
                Expr b = unary();
                left = run -> {
                    int by = b.eval(run);
                    return by == 0 ? 0 : a.eval(run) % by;
                };
            } else {
                return left;
            }
        }
    }

    private Expr unary() {
        boolean negative = false;
        while (accept("-")) {
            negative = !negative;
        }
        Expr value = value();
        return negative ? run -> -value.eval(run) : value;
    }

    private Expr value() {
        Token token = next();
        if (token == null) {
            throw error("expected a number at the end of the line");
        }
        if (token.kind() == '0') {
            int n = Integer.parseInt(token.text());
            return run -> n;
        }
        if (token.kind() == '+' && token.text().equals("(")) {
            Expr inside = expression();
            expect(")");
            return inside;
        }
        if (token.kind() != 'a') {
            throw error("expected a number, not '" + token.text() + "'");
        }
        String name = token.text();
        if (name.equals("chance")) {
            expect("(");
            Expr percent = expression();
            expect(")");
            return run -> run.host.random(100) < percent.eval(run) ? 1 : 0;
        }
        if (name.equals("random")) {
            expect("(");
            Expr first = expression();
            expect(",");
            Expr second = expression();
            expect(")");
            return run -> {
                int a = first.eval(run);
                int b = second.eval(run);
                int low = Math.min(a, b);
                return low + run.host.random(Math.max(a, b) - low + 1);
            };
        }
        int dot = name.indexOf('.');
        if (dot >= 0) {
            String who = name.substring(0, dot);
            if ((who.equals("user") || who.equals("target")) && Script.PROPERTIES.contains(name.substring(dot + 1))) {
                return run -> run.host.number(name);
            }
            throw error("there is no '" + name + "' (use user. or target. and one of "
                    + String.join(", ", Script.PROPERTIES) + ")");
        }
        if (Script.NUMBERS.contains(name)) {
            return run -> run.host.number(name);
        }
        if (variables.contains(name)) {
            return run -> run.variables.getOrDefault(name, 0);
        }
        throw error("'" + name + "' isn't a number, or a variable set earlier in this block");
    }

    // ------------------------------------------------------------ tokens

    private Token next() {
        return pos < tokens.size() ? tokens.get(pos++) : null;
    }

    private boolean peek(String text) {
        return pos < tokens.size() && tokens.get(pos).kind() != '"' && tokens.get(pos).text().equals(text);
    }

    private boolean accept(String text) {
        boolean found = peek(text);
        if (found) {
            pos++;
        }
        return found;
    }

    private void expect(String text) {
        if (!accept(text)) {
            throw error("expected '" + text + "'" + (pos < tokens.size()
                    ? ", not '" + tokens.get(pos).text() + "'" : " here"));
        }
    }

    private String word(String what) {
        Token token = next();
        if (token == null || token.kind() != 'a') {
            throw error("expected " + what + (token == null ? " here" : ", not '" + token.text() + "'"));
        }
        return token.text();
    }

    private String choice(String what, Set<String> allowed) {
        Token token = next();
        if (token == null || token.kind() != 'a' || !allowed.contains(token.text())) {
            throw error("expected " + what + " (" + String.join(", ", allowed) + ")"
                    + (token == null ? "" : ", not '" + token.text() + "'"));
        }
        return token.text();
    }

    private void endOfLine() {
        if (pos < tokens.size()) {
            throw error("didn't expect '" + tokens.get(pos).text() + "' here");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("line " + Math.max(1, lineNumber) + ": " + message);
    }

    private List<Token> tokenize(String line) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            int start = i;
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '#') {
                break;
            } else if (c == '"') {
                int close = line.indexOf('"', i + 1);
                if (close < 0) {
                    throw error("the text is missing its closing quote");
                }
                out.add(new Token('"', line.substring(i + 1, close)));
                i = close + 1;
            } else if (c >= '0' && c <= '9') {
                while (i < line.length() && line.charAt(i) >= '0' && line.charAt(i) <= '9') {
                    i++;
                }
                String digits = line.substring(start, i);
                if (digits.length() > 7 || Integer.parseInt(digits) > Script.LIMIT) {
                    throw error(digits + " is too big (the most is " + Script.LIMIT + ")");
                }
                out.add(new Token('0', digits));
            } else if (Character.isLetter(c) || c == '_') {
                while (i < line.length() && (Character.isLetterOrDigit(line.charAt(i))
                        || line.charAt(i) == '_' || line.charAt(i) == '.')) {
                    i++;
                }
                out.add(new Token('a', line.substring(start, i).toLowerCase(Locale.ROOT)));
            } else {
                String two = i + 1 < line.length() ? line.substring(i, i + 2) : "";
                if (Set.of("<=", ">=", "==", "!=").contains(two)) {
                    out.add(new Token('+', two));
                    i += 2;
                } else if ("+-*/%()<>=,".indexOf(c) >= 0) {
                    out.add(new Token('+', String.valueOf(c)));
                    i++;
                } else {
                    throw error("didn't expect '" + c + "'");
                }
            }
        }
        return out;
    }
}
