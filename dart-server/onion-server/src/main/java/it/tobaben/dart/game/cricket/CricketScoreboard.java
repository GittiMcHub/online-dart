package it.tobaben.dart.game.cricket;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.board.Segment;

import java.util.*;

/**
 * Scoreboard for American Cricket. Players collect marks on 15-20 and bull
 * (single = 1, double = 2, triple = 3 marks; bullseye = 2 marks). Three marks
 * close a number. Hits on a number the player has closed score points as long
 * as at least one opponent has it still open; a number closed by everyone is
 * dead. A player wins by closing all numbers while having at least as many
 * points as every opponent.
 */
public class CricketScoreboard {

    public static final Set<Integer> CRICKET_NUMBERS = Set.of(15, 16, 17, 18, 19, 20, 25);
    public static final int MARKS_TO_CLOSE = 3;

    private final List<Player> players;
    private final Map<Player, Map<Integer, Integer>> marks;
    private final Map<Player, Integer> points;

    public CricketScoreboard(List<Player> players) {
        this.players = List.copyOf(players);
        this.marks = new HashMap<>();
        this.points = new HashMap<>();
        for (Player player : this.players) {
            Map<Integer, Integer> playerMarks = new HashMap<>();
            for (Integer number : CRICKET_NUMBERS) {
                playerMarks.put(number, 0);
            }
            this.marks.put(player, playerMarks);
            this.points.put(player, 0);
        }
    }

    /**
     * Applies a dart hit for the given player.
     *
     * @return the points scored by this hit (0 for non-cricket segments,
     *         marks that only close, or hits on dead numbers)
     */
    public int applyHit(Player player, Segment segment) {
        int number = cricketNumberOf(segment);
        if (number < 0) {
            return 0;
        }
        int current = this.marks.get(player).get(number);
        int newMarks = current + segment.getMultiplier();
        this.marks.get(player).put(number, Math.min(MARKS_TO_CLOSE, newMarks));

        int overflow = newMarks - MARKS_TO_CLOSE;
        if (overflow <= 0 || isDead(number, player)) {
            return 0;
        }
        int scored = overflow * number;
        this.points.merge(player, scored, Integer::sum);
        return scored;
    }

    /**
     * @return the cricket number (15-20, 25) targeted by the segment, or -1 if
     *         the segment does not take part in cricket (1-14, wall/board hit)
     */
    public static int cricketNumberOf(Segment segment) {
        if (segment.getMultiplier() == 0) {
            return -1;
        }
        int number = segment.getScore() / segment.getMultiplier();
        return CRICKET_NUMBERS.contains(number) ? number : -1;
    }

    public boolean isClosed(Player player, int number) {
        return this.marks.get(player).get(number) >= MARKS_TO_CLOSE;
    }

    /**
     * A number is dead for the hitting player when every opponent has closed it,
     * i.e. no points can be scored on it anymore.
     */
    private boolean isDead(int number, Player hittingPlayer) {
        return this.players.stream()
                .filter(player -> !player.equals(hittingPlayer))
                .allMatch(player -> isClosed(player, number));
    }

    public boolean hasClosedAll(Player player) {
        return CRICKET_NUMBERS.stream().allMatch(number -> isClosed(player, number));
    }

    public int getPoints(Player player) {
        return this.points.get(player);
    }

    public Map<Player, Integer> getPoints() {
        return new HashMap<>(this.points);
    }

    public int getMarks(Player player, int number) {
        return this.marks.get(player).get(number);
    }

    /**
     * Immutable copy of one player's marks and points, used to void or undo a turn.
     */
    public record PlayerState(Map<Integer, Integer> marks, int points) {
    }

    public PlayerState snapshotOf(Player player) {
        return new PlayerState(Map.copyOf(this.marks.get(player)), this.points.get(player));
    }

    public void restore(Player player, PlayerState state) {
        this.marks.put(player, new HashMap<>(state.marks()));
        this.points.put(player, state.points());
    }

    public boolean isGameOver() {
        return findWinner() != null;
    }

    private Player findWinner() {
        int maxPoints = this.points.values().stream().max(Integer::compareTo).orElse(0);
        return this.players.stream()
                .filter(this::hasClosedAll)
                .filter(player -> getPoints(player) >= maxPoints)
                .findFirst()
                .orElse(null);
    }

    /**
     * @return final ranking: winner first, the rest ordered by points, then by
     *         total marks, then by turn order. Empty while the game is running.
     */
    public List<Player> getRanking() {
        Player winner = findWinner();
        if (winner == null) {
            return List.of();
        }
        List<Player> ranking = new ArrayList<>();
        ranking.add(winner);
        this.players.stream()
                .filter(player -> !player.equals(winner))
                .sorted(Comparator
                        .comparingInt((Player player) -> getPoints(player)).reversed()
                        .thenComparing(Comparator.comparingInt(this::totalMarks).reversed()))
                .forEach(ranking::add);
        return ranking;
    }

    private int totalMarks(Player player) {
        return this.marks.get(player).values().stream().mapToInt(Integer::intValue).sum();
    }

}
