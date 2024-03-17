enum GameState {
    RUNNING("RUNNING"),
    WAITING("WAITING"),
    FINISHED("FINISHED"),
    UNDEFINED("UNDEFINED");

    private final String stateString;

    GameState(String stateString) {
        this.stateString = stateString;
    }
}