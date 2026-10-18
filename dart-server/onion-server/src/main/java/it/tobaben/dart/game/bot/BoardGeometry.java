package it.tobaben.dart.game.bot;

import it.tobaben.dart.game.board.Segment;

/**
 * Standard dartboard proportions (radii in mm). Only the proportions matter for
 * the bot: the difficulty calibration maps an average to a scatter σ in the
 * same units, so soft-tip vs. steel size does not change the result.
 */
public final class BoardGeometry {

    public static final double BULLSEYE_RADIUS = 6.35;
    public static final double BULL_RADIUS = 15.9;
    public static final double TRIPLE_INNER = 99.0;
    public static final double TRIPLE_OUTER = 107.0;
    public static final double DOUBLE_INNER = 162.0;
    public static final double DOUBLE_OUTER = 170.0;

    /** Clockwise from the top, 18° per sector. */
    static final int[] SECTORS = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};
    private static final double SECTOR_DEGREES = 18.0;

    private BoardGeometry() {
    }

    /** @return the segment hit at (x, y); everything beyond the double ring is BOARD_HIT (miss) */
    public static Segment segmentAt(double x, double y) {
        double radius = Math.hypot(x, y);
        if (radius <= BULLSEYE_RADIUS) {
            return Segment.BULLS_EYE;
        }
        if (radius <= BULL_RADIUS) {
            return Segment.BULL;
        }
        if (radius > DOUBLE_OUTER) {
            return Segment.BOARD_HIT;
        }
        double degrees = Math.toDegrees(Math.atan2(x, y)); // clockwise from top
        double shifted = ((degrees + SECTOR_DEGREES / 2) % 360 + 360) % 360;
        int number = SECTORS[(int) Math.floor(shifted / SECTOR_DEGREES)];
        if (radius > TRIPLE_INNER && radius <= TRIPLE_OUTER) {
            return segment(3, number);
        }
        if (radius > DOUBLE_INNER) {
            return segment(2, number);
        }
        return segment(1, number);
    }

    /** @return the middle of the segment's field; bull and bullseye are aimed at the centre */
    public static AimPoint centerOf(Segment segment) {
        if (segment == Segment.BULL || segment == Segment.BULLS_EYE) {
            return AimPoint.CENTER;
        }
        int multiplier = segment.getMultiplier();
        if (multiplier == 0) {
            throw new IllegalArgumentException("Kein Zielfeld: " + segment);
        }
        int number = segment.getScore() / multiplier;
        double radius = switch (multiplier) {
            case 3 -> (TRIPLE_INNER + TRIPLE_OUTER) / 2;
            case 2 -> (DOUBLE_INNER + DOUBLE_OUTER) / 2;
            default -> (TRIPLE_OUTER + DOUBLE_INNER) / 2; // outer single field, the larger one
        };
        double radians = Math.toRadians(sectorIndexOf(number) * SECTOR_DEGREES);
        return new AimPoint(radius * Math.sin(radians), radius * Math.cos(radians));
    }

    /** @param number 1–20, or 25 for bull (multiplier 1) / bullseye (multiplier 2) */
    public static Segment segment(int multiplier, int number) {
        if (number == 25) {
            return switch (multiplier) {
                case 1 -> Segment.BULL;
                case 2 -> Segment.BULLS_EYE;
                default -> throw new IllegalArgumentException("Kein Feld: " + multiplier + "x" + number);
            };
        }
        String prefix = switch (multiplier) {
            case 1 -> "SINGLE_";
            case 2 -> "DOUBLE_";
            case 3 -> "TRIPLE_";
            default -> throw new IllegalArgumentException("Kein Feld: " + multiplier + "x" + number);
        };
        return Segment.valueOf(prefix + number);
    }

    private static int sectorIndexOf(int number) {
        for (int i = 0; i < SECTORS.length; i++) {
            if (SECTORS[i] == number) {
                return i;
            }
        }
        throw new IllegalArgumentException("Keine Sektorzahl: " + number);
    }
}
