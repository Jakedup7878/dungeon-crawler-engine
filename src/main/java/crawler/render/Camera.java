package crawler.render;

import crawler.core.Direction;
import crawler.core.Party;

/**
 * The view position the renderer draws from.
 *
 * <p>The party jumps from cell to cell instantly; the camera follows with a short
 * tween so steps and turns animate smoothly. Positions are in map units, with
 * (x + 0.5, y + 0.5) being the centre of cell (x, y). Angles are radians clockwise
 * from north, matching {@link Direction#angle()}.
 */
public final class Camera {
    private static final double TWO_PI = Math.PI * 2;
    /** How far the camera lurches toward a wall it bumps into, in cells. */
    private static final double BUMP_DISTANCE = 0.08;

    private double x;
    private double y;
    private double angle;

    private double fromX;
    private double fromY;
    private double fromAngle;
    private double toX;
    private double toY;
    private double toAngle;
    private double elapsed;
    private double duration;

    private double bumpX;
    private double bumpY;
    private double bumpElapsed;
    private double bumpDuration;

    /** Jumps straight to the party with no animation. */
    public void snapTo(Party party) {
        x = toX = party.x() + 0.5;
        y = toY = party.y() + 0.5;
        angle = toAngle = party.facing().angle();
        elapsed = duration = 0;
        bumpElapsed = bumpDuration = 0;
    }

    /** Glides from the current view to the party's position and facing. */
    public void moveTo(Party party, double seconds) {
        if (seconds <= 0) {
            snapTo(party);
            return;
        }
        fromX = x;
        fromY = y;
        fromAngle = angle;
        toX = party.x() + 0.5;
        toY = party.y() + 0.5;
        // Rotate the short way round; a 180 turn goes clockwise.
        toAngle = angle + Math.IEEEremainder(party.facing().angle() - angle, TWO_PI);
        elapsed = 0;
        duration = seconds;
    }

    /** Lurches briefly toward {@code direction} and back, for walking into a wall. */
    public void bump(Direction direction, double seconds) {
        bumpX = direction.dx;
        bumpY = direction.dy;
        bumpElapsed = 0;
        bumpDuration = seconds;
    }

    public void update(double dt) {
        if (elapsed < duration) {
            elapsed = Math.min(duration, elapsed + dt);
            if (elapsed >= duration) {
                x = toX;
                y = toY;
                angle = normalize(toAngle);
            } else {
                double t = smoothstep(elapsed / duration);
                x = fromX + (toX - fromX) * t;
                y = fromY + (toY - fromY) * t;
                angle = fromAngle + (toAngle - fromAngle) * t;
            }
        }
        if (bumpElapsed < bumpDuration) {
            bumpElapsed = Math.min(bumpDuration, bumpElapsed + dt);
        }
    }

    public boolean isAnimating() {
        return elapsed < duration || bumpElapsed < bumpDuration;
    }

    public double viewX() {
        return x + bumpX * bumpOffset();
    }

    public double viewY() {
        return y + bumpY * bumpOffset();
    }

    public double viewAngle() {
        return angle;
    }

    private double bumpOffset() {
        if (bumpElapsed >= bumpDuration) {
            return 0;
        }
        return BUMP_DISTANCE * Math.sin(Math.PI * bumpElapsed / bumpDuration);
    }

    private static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double normalize(double a) {
        a %= TWO_PI;
        return a < 0 ? a + TWO_PI : a;
    }
}
