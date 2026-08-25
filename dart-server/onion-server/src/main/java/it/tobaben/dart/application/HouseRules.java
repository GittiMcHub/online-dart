package it.tobaben.dart.application;

/**
 * House rules applied on top of every game mode (see root README): repdigit
 * scores (Schnapszahlen), wall hits and final placement cost penalty points.
 * Each rule can be switched off.
 */
public record HouseRules(
        boolean schnapszahlPenalty,
        boolean wallHitPenalty,
        boolean placementPenalty
) {

    public static HouseRules allOn() {
        return new HouseRules(true, true, true);
    }

    public static HouseRules allOff() {
        return new HouseRules(false, false, false);
    }

    /**
     * @return true if the score is a Schnapszahl: at least two digits, all equal
     *         (11, 22, ..., 99, 111, 222, ...)
     */
    public static boolean isSchnapszahl(int score) {
        String digits = Integer.toString(score);
        if (digits.length() < 2) {
            return false;
        }
        for (int i = 1; i < digits.length(); i++) {
            if (digits.charAt(i) != digits.charAt(0)) {
                return false;
            }
        }
        return true;
    }
}
