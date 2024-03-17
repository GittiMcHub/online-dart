public enum Sounds {
    SPIELSTART("SPIELSTART"),
    TREFFER("TREFFER"),
    DOUBLE("DOUBLE"),
    TRIPLE("TRIPLE"),
    BULLSEYE("BULLSEYE"),
    WINNER("WINNER"),
    STRAFE("STRAFE"),
    RESET("RESET"),
    UEBERWORFEN("UEBERWORFEN"),
    MAXPOINTS("MAXPOINTS");

    private final String soundString;

    Sounds(String soundString) {
        this.soundString = soundString;
    }
}
