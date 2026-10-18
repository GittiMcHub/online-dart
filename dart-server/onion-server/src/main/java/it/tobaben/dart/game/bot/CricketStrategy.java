package it.tobaben.dart.game.bot;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.GameSnapshot;

import java.util.List;

/**
 * Cricket tactics. Naive bots close 20 → 15 → bull in order. Smart bots score
 * on their closed numbers while behind, block numbers an opponent already
 * closed, and otherwise close the highest open number.
 */
public class CricketStrategy implements BotStrategy {

    static final List<Integer> NUMBERS = List.of(20, 19, 18, 17, 16, 15, 25);
    private static final int CLOSED = 3;

    private final boolean smart;

    public CricketStrategy(boolean smart) {
        this.smart = smart;
    }

    @Override
    public AimPoint aim(GameSnapshot snapshot, Player self) {
        int number = targetNumber(snapshot, self);
        return number == 25 ? AimPoint.CENTER : BoardGeometry.centerOf(BoardGeometry.segment(3, number));
    }

    int targetNumber(GameSnapshot snapshot, Player self) {
        if (!this.smart) {
            return NUMBERS.stream().filter(n -> marks(snapshot, self, n) < CLOSED).findFirst().orElse(20);
        }
        List<Player> opponents = snapshot.playerOrder().stream().filter(p -> !p.equals(self)).toList();
        int myPoints = snapshot.scores().getOrDefault(self, 0);
        int bestOpponent = opponents.stream()
                .mapToInt(p -> snapshot.scores().getOrDefault(p, 0)).max().orElse(0);
        if (myPoints < bestOpponent) {
            for (int number : NUMBERS) {
                if (marks(snapshot, self, number) >= CLOSED && anyOpen(snapshot, opponents, number)) {
                    return number;
                }
            }
        }
        for (int number : NUMBERS) {
            if (marks(snapshot, self, number) < CLOSED
                    && opponents.stream().anyMatch(o -> marks(snapshot, o, number) >= CLOSED)) {
                return number;
            }
        }
        for (int number : NUMBERS) {
            if (marks(snapshot, self, number) < CLOSED) {
                return number;
            }
        }
        for (int number : NUMBERS) {
            if (anyOpen(snapshot, opponents, number)) {
                return number; // everything closed but still behind: keep scoring
            }
        }
        return 20;
    }

    private static boolean anyOpen(GameSnapshot snapshot, List<Player> players, int number) {
        return players.stream().anyMatch(p -> marks(snapshot, p, number) < CLOSED);
    }

    private static int marks(GameSnapshot snapshot, Player player, int number) {
        return Integer.parseInt(snapshot.modeData()
                .getOrDefault("marks:" + player.getName() + ":" + number, "0"));
    }
}
