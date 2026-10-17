package it.tobaben.dart.game.bot;

import it.tobaben.dart.game.board.Segment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Double-out checkout routes of the bot (resource bot/checkouts.txt). The bot
 * may pick another route than the TV finish hint — this table is bot code.
 */
public final class Checkouts {

    private static final String RESOURCE = "/bot/checkouts.txt";
    private static final Map<Integer, List<Segment>> ROUTES = load();

    private Checkouts() {
    }

    /** @return the route to finish rest with at most dartsLeft darts; first element is the next dart */
    public static Optional<List<Segment>> route(int rest, int dartsLeft) {
        List<Segment> route = ROUTES.get(rest);
        if (route != null && route.size() <= dartsLeft) {
            return Optional.of(route);
        }
        if (dartsLeft >= 1) {
            // e.g. 50 with one dart: the table prefers S10 D20, but the bullseye finishes too
            Segment oneDart = oneDartFinish(rest);
            if (oneDart != null) {
                return Optional.of(List.of(oneDart));
            }
        }
        return Optional.empty();
    }

    static Map<Integer, List<Segment>> routes() {
        return ROUTES;
    }

    private static Segment oneDartFinish(int rest) {
        if (rest == 50) {
            return Segment.BULLS_EYE;
        }
        if (rest >= 2 && rest <= 40 && rest % 2 == 0) {
            return BoardGeometry.segment(2, rest / 2);
        }
        return null;
    }

    private static Map<Integer, List<Segment>> load() {
        Map<String, Segment> byCode = new HashMap<>();
        for (Segment segment : Segment.values()) {
            byCode.put(segment.getCode(), segment);
        }
        Map<Integer, List<Segment>> routes = new HashMap<>();
        try (InputStream in = Checkouts.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Ressource fehlt: " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                List<Segment> route = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) {
                    Segment segment = byCode.get(parts[i]);
                    if (segment == null) {
                        throw new IllegalStateException("Unbekanntes Feld in " + RESOURCE + ": " + parts[i]);
                    }
                    route.add(segment);
                }
                routes.put(Integer.parseInt(parts[0]), List.copyOf(route));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Collections.unmodifiableMap(routes);
    }
}
