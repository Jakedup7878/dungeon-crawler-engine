package crawler.ui;

import crawler.audio.MusicPlayer;
import crawler.battle.Battle;
import crawler.battle.BattleEvent;
import crawler.battle.Fighter;
import crawler.core.AttackAnimation;
import crawler.core.GameContent;
import crawler.game.Game;
import crawler.render.ObjectLooks;
import crawler.render.Sprite;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The battle, drawn over the first-person view. It takes each party member's order (Attack,
 * Defend or Flee), then plays the round's {@link BattleEvent}s one at a time: the attack
 * animation over whoever is hit, its sound, the damage, and a line of text.
 *
 * <p>HP on screen lags behind the real numbers until each hit is shown, so the bars drop
 * when the animation lands rather than all at once.
 */
final class BattleScreen {
    private enum Phase { INTRO, COMMAND, TARGET, PLAYING, OUTCOME }

    private static final String[] COMMANDS = {"Attack", "Defend", "Flee"};
    private static final double INTRO_SECONDS = 1.3;
    private static final double TEXT_SECONDS = 0.9;
    /** How far through an attack animation the hit lands. */
    private static final double IMPACT_AT = 0.6;

    private record Popup(Fighter target, String text, Color color, double start) {
    }

    private final Game game;
    private final Battle battle;
    private final GameContent content;
    private final ObjectLooks looks;
    private final MusicPlayer music;

    private Phase phase = Phase.INTRO;
    private double phaseTime;
    private double clock;
    private int heroIndex = -1;
    private int menuIndex;
    private int targetIndex;
    private final List<Battle.Order> orders = new ArrayList<>();

    private final Deque<BattleEvent> queue = new ArrayDeque<>();
    private BattleEvent current;
    private double eventTime;
    private double eventLength;
    private double impactAt;
    private boolean impactDone;
    private BattleEvent.Victory victory;

    private String caption;
    private final List<String> outcomeLines = new ArrayList<>();
    private final Map<Fighter, Integer> shownHp = new HashMap<>();
    private final Map<Fighter, Double> fallenAt = new HashMap<>();
    private final List<Popup> popups = new ArrayList<>();
    private Fighter flashHero;
    private double flashUntil;
    private double shakeUntil;
    private boolean finished;

    BattleScreen(Game game, ObjectLooks looks, MusicPlayer music) {
        this.game = game;
        this.battle = game.battle();
        this.content = game.content();
        this.looks = looks;
        this.music = music;
        rememberHp();
        List<Fighter> enemies = battle.enemies();
        List<String> names = enemies.stream().map(Fighter::name).toList();
        caption = names.size() == 1 ? names.get(0) + " appears!"
                : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1) + " appear!";
    }

    boolean isFinished() {
        return finished;
    }

    // ------------------------------------------------------------ what the panel asks

    /** The party member giving orders, to highlight in the party list; null while the round plays. */
    Fighter activeHero() {
        return (phase == Phase.COMMAND || phase == Phase.TARGET) && heroIndex >= 0 ? battle.party().get(heroIndex) : null;
    }

    /** A party member who was just hit, to flash in the party list; or null. */
    Fighter hurtHero() {
        return clock < flashUntil ? flashHero : null;
    }

    /** HP as the screen shows it (behind the real value until the hit is shown). */
    int shownHp(Fighter fighter) {
        return shownHp.getOrDefault(fighter, fighter.hp());
    }

    /** Asks the music player for the right track: battle music, then the fanfare or silence. */
    void updateMusic() {
        String battleMusic = content.settings().battleMusic();
        String victoryMusic = content.settings().victoryMusic();
        if (phase == Phase.OUTCOME && battle.outcome() == Battle.Outcome.DEFEAT) {
            music.play(null);
        } else if (phase == Phase.OUTCOME && battle.outcome() == Battle.Outcome.VICTORY && victoryMusic != null) {
            music.playOnce(victoryMusic);
        } else if (phase != Phase.OUTCOME || battle.outcome() != Battle.Outcome.VICTORY) {
            music.play(battleMusic != null ? battleMusic : game.map().music());
        }
    }

    // ------------------------------------------------------------ flow

    void update(double dt) {
        clock += dt;
        phaseTime += dt;
        if (phase == Phase.INTRO && phaseTime >= INTRO_SECONDS) {
            startRound();
        } else if (phase == Phase.PLAYING) {
            updatePlayback(dt);
        }
        popups.removeIf(p -> clock - p.start() > 1.1);
    }

    private void rememberHp() {
        for (Fighter f : battle.party()) {
            shownHp.put(f, f.hp());
        }
        for (Fighter f : battle.enemies()) {
            shownHp.put(f, f.hp());
        }
    }

    private void startRound() {
        orders.clear();
        heroIndex = -1;
        nextHero();
    }

    /** On to the next living party member; once everyone has an order, the round plays. */
    private void nextHero() {
        int i = heroIndex + 1;
        while (i < battle.party().size() && !battle.party().get(i).alive()) {
            i++;
        }
        if (i >= battle.party().size()) {
            playRound();
            return;
        }
        heroIndex = i;
        menuIndex = 0;
        phase = Phase.COMMAND;
        phaseTime = 0;
        caption = battle.party().get(i).name() + ": what will you do?";
    }

    private void previousHero() {
        int i = heroIndex - 1;
        while (i >= 0 && !battle.party().get(i).alive()) {
            i--;
        }
        if (i < 0) {
            return;
        }
        Fighter hero = battle.party().get(i);
        orders.removeIf(o -> o.actor() == hero);
        heroIndex = i;
        menuIndex = 0;
        phase = Phase.COMMAND;
        caption = hero.name() + ": what will you do?";
    }

    private void choose() {
        Fighter hero = battle.party().get(heroIndex);
        switch (menuIndex) {
            case 0 -> {
                List<Fighter> targets = battle.livingEnemies();
                if (targets.size() == 1) {
                    orders.add(new Battle.Order(hero, Battle.Command.ATTACK, targets.get(0)));
                    nextHero();
                } else {
                    targetIndex = Math.min(targetIndex, targets.size() - 1);
                    phase = Phase.TARGET;
                    caption = "Attack which enemy?";
                }
            }
            case 1 -> {
                orders.add(new Battle.Order(hero, Battle.Command.DEFEND, null));
                nextHero();
            }
            default -> {
                orders.add(new Battle.Order(hero, Battle.Command.FLEE, null));
                nextHero();
            }
        }
    }

    private void playRound() {
        rememberHp();
        queue.addAll(battle.playRound(List.copyOf(orders)));
        phase = Phase.PLAYING;
        nextEvent();
    }

    private void nextEvent() {
        current = queue.pollFirst();
        eventTime = 0;
        impactDone = false;
        if (current == null) {
            if (battle.outcome() == Battle.Outcome.ONGOING) {
                startRound();
            } else {
                showOutcome();
            }
            return;
        }
        if (current instanceof BattleEvent.Attack attack) {
            AttackAnimation animation = content.animation(attack.attacker().attackAnimation());
            double length = Math.max(0.3, Math.min(1.6, animationOf(attack.attacker()).duration()));
            impactAt = length * IMPACT_AT;
            eventLength = length + 0.45;
            caption = attack.attacker().name() + " attacks " + attack.target().name() + "!";
            music.playEffect(animation == null ? null : animation.sound());
        } else if (current instanceof BattleEvent.Defend defend) {
            caption = defend.actor().name() + " stands guard.";
            eventLength = TEXT_SECONDS;
        } else if (current instanceof BattleEvent.FleeFailed) {
            caption = "The party tries to run, but can't get away!";
            eventLength = TEXT_SECONDS + 0.2;
        } else if (current instanceof BattleEvent.CantFlee) {
            caption = "There is no running from this fight!";
            eventLength = TEXT_SECONDS + 0.2;
        } else if (current instanceof BattleEvent.Fled) {
            caption = "The party runs away!";
            eventLength = TEXT_SECONDS + 0.3;
        } else if (current instanceof BattleEvent.Victory won) {
            victory = won;
            eventLength = 0;
        } else {
            eventLength = 0; // Defeat
        }
    }

    private void updatePlayback(double dt) {
        if (current == null) {
            return;
        }
        eventTime += dt;
        if (current instanceof BattleEvent.Attack attack && !impactDone && eventTime >= impactAt) {
            land(attack);
        }
        if (eventTime >= eventLength) {
            nextEvent();
        }
    }

    /** The moment an attack hits: HP drops, the number pops up, the screen shakes. */
    private void land(BattleEvent.Attack attack) {
        impactDone = true;
        Fighter target = attack.target();
        shownHp.put(target, Math.max(0, shownHp(target) - attack.damage()));
        if (attack.missed()) {
            popups.add(new Popup(target, "Miss", Hud.DIM, clock));
            caption = attack.attacker().name() + " misses!";
            return;
        }
        popups.add(new Popup(target, Integer.toString(attack.damage()), target.isEnemy() ? Hud.HIGHLIGHT : Hud.BAD, clock));
        if (!target.isEnemy()) {
            flashHero = target;
            flashUntil = clock + 0.45;
            shakeUntil = clock + 0.3;
        }
        if (attack.killed()) {
            fallenAt.put(target, clock);
            caption = target.name() + (target.isEnemy() ? " is defeated!" : " falls!");
        } else {
            caption = target.name() + " takes " + attack.damage() + " damage.";
        }
    }

    private void showOutcome() {
        phase = Phase.OUTCOME;
        phaseTime = 0;
        outcomeLines.clear();
        switch (battle.outcome()) {
            case VICTORY -> {
                outcomeLines.add("Victory!");
                if (victory != null) {
                    outcomeLines.add("Each survivor earns " + victory.expEach() + " experience.");
                    outcomeLines.add("You find " + victory.gold() + " gold.");
                    for (BattleEvent.LevelUp up : victory.levelUps()) {
                        outcomeLines.add(up.hero().name() + " reaches level " + up.level() + "!");
                    }
                }
            }
            case DEFEAT -> outcomeLines.add("The party has fallen...");
            case FLED -> outcomeLines.add("You got away safely.");
            case ONGOING -> { }
        }
        caption = outcomeLines.isEmpty() ? "" : outcomeLines.get(0);
    }

    // ------------------------------------------------------------ keys

    void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        boolean confirm = key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE;
        boolean back = key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_BACK_SPACE;
        boolean up = key == KeyEvent.VK_UP || key == KeyEvent.VK_W;
        boolean down = key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S;
        boolean left = key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A || key == KeyEvent.VK_Q;
        boolean right = key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D || key == KeyEvent.VK_E;
        switch (phase) {
            case INTRO -> {
                if (confirm) {
                    startRound();
                }
            }
            case COMMAND -> {
                if (up) {
                    menuIndex = (menuIndex + COMMANDS.length - 1) % COMMANDS.length;
                } else if (down) {
                    menuIndex = (menuIndex + 1) % COMMANDS.length;
                } else if (back) {
                    previousHero();
                } else if (confirm) {
                    choose();
                }
            }
            case TARGET -> {
                List<Fighter> targets = battle.livingEnemies();
                if (left) {
                    targetIndex = (targetIndex + targets.size() - 1) % targets.size();
                } else if (right) {
                    targetIndex = (targetIndex + 1) % targets.size();
                } else if (back) {
                    phase = Phase.COMMAND;
                    caption = battle.party().get(heroIndex).name() + ": what will you do?";
                } else if (confirm) {
                    orders.add(new Battle.Order(battle.party().get(heroIndex), Battle.Command.ATTACK, targets.get(targetIndex)));
                    nextHero();
                }
            }
            case PLAYING -> {
                if (confirm && current != null) { // hurry along
                    if (current instanceof BattleEvent.Attack attack && !impactDone) {
                        land(attack);
                    }
                    nextEvent();
                }
            }
            case OUTCOME -> {
                if (confirm && phaseTime > 0.4) {
                    game.endBattle();
                    finished = true;
                }
            }
        }
    }

    // ------------------------------------------------------------ drawing

    private Sprite animationOf(Fighter attacker) {
        return looks.attack(content.animation(attacker.attackAnimation()), attacker.isEnemy());
    }

    private int animationSize(Fighter attacker) {
        AttackAnimation animation = content.animation(attacker.attackAnimation());
        return animation == null ? 100 : animation.size();
    }

    /** Draws the battle over the 3D view, which the panel has already drawn into {@code view}. */
    void draw(Graphics2D graphics, Rectangle view) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.clip(view);
            g.setColor(new Color(0, 0, 0, 120));
            g.fill(view);
            if (clock < shakeUntil) {
                g.translate(Math.sin(clock * 90) * 5, Math.cos(clock * 70) * 3);
            }
            drawEnemies(g, view);
            drawAttackOnParty(g, view);
            if (clock < flashUntil) {
                int alpha = (int) (90 * (flashUntil - clock) / 0.45);
                g.setColor(new Color(220, 20, 20, Math.max(0, Math.min(255, alpha))));
                g.fill(view);
            }
        } finally {
            g.dispose();
        }
        drawCaption(graphics, view);
        if (phase == Phase.COMMAND || phase == Phase.TARGET) {
            drawCommands(graphics, view);
        } else if (phase == Phase.OUTCOME) {
            drawOutcome(graphics, view);
        }
    }

    private void drawEnemies(Graphics2D g, Rectangle view) {
        List<Fighter> enemies = battle.enemies();
        int count = enemies.size();
        int slot = Math.min(200, (view.width - 40) / count);
        int left = view.x + (view.width - slot * count) / 2;
        int baseline = view.y + view.height - 190; // leaves room below for the command box
        int maxHeight = 200;
        List<Fighter> targets = battle.livingEnemies();
        Fighter targeted = phase == Phase.TARGET && !targets.isEmpty() ? targets.get(Math.min(targetIndex, targets.size() - 1)) : null;

        for (int i = 0; i < count; i++) {
            Fighter enemy = enemies.get(i);
            float alpha = 1f;
            if (shownHp(enemy) <= 0) {
                Double fell = fallenAt.get(enemy);
                double gone = fell == null ? 1 : (clock - fell) / 0.6;
                if (gone >= 1) {
                    continue;
                }
                alpha = (float) (1 - gone);
            }
            int centreX = left + slot * i + slot / 2;
            Rectangle box = new Rectangle(centreX - (slot - 16) / 2, baseline - maxHeight, slot - 16, maxHeight);
            // The attacker lunges forward; whoever is hit shakes.
            if (current instanceof BattleEvent.Attack attack) {
                if (attack.attacker() == enemy && eventTime < 0.3) {
                    box.grow(box.width / 12, 0);
                    box.y += 12;
                }
                if (attack.target() == enemy && impactDone && eventTime - impactAt < 0.3 && !attack.missed()) {
                    box.x += (int) Math.round(Math.sin(eventTime * 80) * 6);
                }
            }
            Graphics2D e = (Graphics2D) g.create();
            e.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
            BufferedImage picture = looks.enemy(enemy.enemyId(), enemy.sprite()).image(clock);
            Rectangle drawn = Hud.drawFitted(e, picture, box, 4);
            e.dispose();

            if (current instanceof BattleEvent.Attack attack && attack.target() == enemy) {
                drawAnimation(g, attack.attacker(), new Rectangle(drawn.x, drawn.y, drawn.width, drawn.height));
            }

            g.setFont(Hud.SMALL_FONT);
            g.setColor(enemy == targeted ? Hud.HIGHLIGHT : Hud.TEXT);
            Hud.centered(g, Hud.fit(g, enemy.name(), slot - 8), centreX, baseline + 20);
            Hud.bar(g, centreX - (slot - 40) / 2, baseline + 28, slot - 40, 7, shownHp(enemy), enemy.stats().hp());
            if (enemy == targeted && (int) (clock * 3) % 2 == 0) {
                g.setColor(Hud.HIGHLIGHT);
                g.fillPolygon(new int[]{centreX - 9, centreX + 9, centreX},
                        new int[]{drawn.y - 22, drawn.y - 22, drawn.y - 8}, 3);
            }
            for (Popup popup : popups) {
                if (popup.target() == enemy) {
                    drawPopup(g, popup, centreX, drawn.y + drawn.height / 3);
                }
            }
        }
    }

    /** An enemy's attack lands on the party: the animation over the middle of the view. */
    private void drawAttackOnParty(Graphics2D g, Rectangle view) {
        if (current instanceof BattleEvent.Attack attack && !attack.target().isEnemy()) {
            int size = 240 * animationSize(attack.attacker()) / 100;
            Rectangle box = new Rectangle(view.x + view.width / 2 - size / 2, view.y + view.height / 2 - size / 2 + 20, size, size);
            drawAnimation(g, attack.attacker(), box);
        }
        for (Popup popup : popups) {
            if (!popup.target().isEnemy()) {
                drawPopup(g, popup, view.x + view.width / 2, view.y + view.height / 2 + 60);
                g.setFont(Hud.SMALL_FONT);
                g.setColor(Hud.TEXT);
                Hud.centered(g, popup.target().name(), view.x + view.width / 2, view.y + view.height / 2 + 84);
            }
        }
    }

    /** One frame of an attacker's animation, sized as the animation says, centred on a box. */
    private void drawAnimation(Graphics2D g, Fighter attacker, Rectangle target) {
        Sprite animation = animationOf(attacker);
        double length = Math.max(0.3, Math.min(1.6, animation.duration()));
        if (eventTime > length) {
            return;
        }
        BufferedImage frame = animation.imageOnce(eventTime * animation.duration() / length);
        int size = Math.max(16, target.height * (attacker.isEnemy() ? 100 : animationSize(attacker)) / 100);
        Rectangle box = new Rectangle(target.x + target.width / 2 - size / 2, target.y + target.height / 2 - size / 2, size, size);
        Hud.drawFitted(g, frame, box, 8);
    }

    private void drawPopup(Graphics2D g, Popup popup, int x, int y) {
        double age = clock - popup.start();
        int rise = (int) (age * 40);
        g.setFont(Hud.BIG_FONT);
        g.setColor(Color.BLACK);
        Hud.centered(g, popup.text(), x + 2, y - rise + 2);
        g.setColor(popup.color());
        Hud.centered(g, popup.text(), x, y - rise);
    }

    private void drawCaption(Graphics2D g, Rectangle view) {
        if (caption == null || caption.isEmpty()) {
            return;
        }
        Rectangle strip = new Rectangle(view.x + 24, view.y + 14, view.width - 48, 34);
        Hud.panel(g, strip);
        g.setFont(Hud.FONT);
        g.setColor(Hud.TEXT);
        Hud.centered(g, Hud.fit(g, caption, strip.width - 20), strip.x + strip.width / 2, strip.y + 23);
    }

    private void drawCommands(Graphics2D g, Rectangle view) {
        Fighter hero = battle.party().get(heroIndex);
        Rectangle box = new Rectangle(view.x + 24, view.y + view.height - 124, 230, 104);
        Hud.panel(g, box);
        g.setFont(Hud.FONT);
        g.setColor(Hud.HIGHLIGHT);
        g.drawString(Hud.fit(g, hero.name(), box.width - 24), box.x + 12, box.y + 22);
        g.setFont(Hud.SMALL_FONT);
        for (int i = 0; i < COMMANDS.length; i++) {
            boolean selected = i == menuIndex;
            boolean unavailable = i == 2 && !battle.canFlee();
            g.setColor(unavailable ? Hud.DIM : selected ? Hud.HIGHLIGHT : Hud.TEXT);
            g.drawString((selected ? "> " : "  ") + COMMANDS[i], box.x + 12, box.y + 46 + i * 19);
        }
        g.setColor(Hud.DIM);
        g.drawString(phase == Phase.TARGET ? "Left/Right, Enter" : "Esc: back", box.x + 120, box.y + box.height - 10);
    }

    private void drawOutcome(Graphics2D g, Rectangle view) {
        int height = 70 + outcomeLines.size() * 22;
        Rectangle box = new Rectangle(view.x + view.width / 2 - 220, view.y + view.height / 2 - height / 2, 440, height);
        Hud.panel(g, box);
        int y = box.y + 34;
        for (int i = 0; i < outcomeLines.size(); i++) {
            g.setFont(i == 0 ? Hud.BIG_FONT : Hud.FONT);
            g.setColor(i == 0 ? (battle.outcome() == Battle.Outcome.DEFEAT ? Hud.BAD : Hud.HIGHLIGHT) : Hud.TEXT);
            Hud.centered(g, outcomeLines.get(i), box.x + box.width / 2, y);
            y += i == 0 ? 30 : 22;
        }
        g.setFont(Hud.SMALL_FONT);
        g.setColor(Hud.DIM);
        Hud.centered(g, "Press Enter", box.x + box.width / 2, box.y + box.height - 12);
    }
}
