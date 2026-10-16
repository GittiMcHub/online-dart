package it.tobaben.dart.game.bot;

/**
 * A point on the dartboard in mm: origin is the centre of the bull, x points
 * to the right, y points up (the 20 is on top).
 */
public record AimPoint(double x, double y) {

    public static final AimPoint CENTER = new AimPoint(0, 0);
}
