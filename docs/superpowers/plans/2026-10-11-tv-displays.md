# TV-Anzeigen (Finish-Hilfe, Trefferbild, Blitztabelle) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Anzeigen" submenu to the TV game menu with three toggleable overlays — Finish-Hilfe (X01 checkout hint), Trefferbild (hit heatmap) and Blitztabelle (live penalty ranking).

**Architecture:** Server (onion-server, Java) gets small additive JSON changes: X01 updates now also carry `gameMode`/`modeData`; every player object gets `platzProjektion`/`strafpunkteProjektion` (projected place / placement penalty of the running game) and `statistik.summePlatzierungen` (placement sum over finished games). All rendering and the checkout calculation live in the TV web client (`web/tv/`, plain JS, no build step) in focused new files; `app.js` only gets hooks.

**Tech Stack:** Java 21, Gradle, JUnit 5, Gson (server); plain ES2020 JS, inline SVG, CSS (TV client); `node --test` (Node ≥18, built-in runner) for the pure JS logic.

**Spec:** `docs/superpowers/specs/2026-10-11-tv-displays-design.md`

## Global Constraints

- **Never run `git commit`.** The user commits. Each task ends with "hand over for commit" — stop there, list changed files.
- Work on branch `tv-displays` (already exists).
- Code identifiers in Java: English (existing style); UI texts, JSON field names and JS comments: German, matching surrounding code.
- `dart-server/schema.json` changes must be backwards compatible: new fields optional, never added to `required`.
- `onion-server/build.gradle` `version`: `2.1.1` → `2.2.0`.
- Finish-Hilfe: cost order Single 0 < Triple 1 < Double 2 < Bull 3 < Bullseye 4; best way = fewest darts, then lowest cost sum, then highest first dart, then second. Bullseye counts as double.
- Finish-Hilfe visible only if `punktestand <= n × 60` (no double-out) or `<= 50 + (n−1) × 60` (double-out), `n = min(mode, freieWuerfe)`.
- Display settings persisted per TV in `localStorage` key `tvDisplays` = `{ finish: 0|1|2|3, heatmap: bool, ranking: bool }`, default all off; every access in try/catch.
- Trefferbild: tournament-cumulative `statistik.getroffeneFelder` of `currentPlayer`; 0 hits = white, otherwise blue scaled to the player's most-hit field.
- Blitztabelle: cost > 0 → Σ = `statistik.anzStrafpunkte + (strafpunkteProjektion ?? 0)` with € line; cost 0/missing → Σ = `statistik.summePlatzierungen + platzProjektion` (both `?? 0`), header "Σ Plätze", no €. Sorted ascending, ties by turn order.
- Blitztabelle in places mode against a server without `statistik.summePlatzierungen`: show hint "Platzierungs-Wertung erfordert neueren Server" instead of a misleading all-zero table (presence check, no version field).
- Finish-Hilfe hidden during WAITING (`freieWuerfe == 0`) — no preview for the next player.
- Layout: `#finishHint` between gallery and `#gameButtons`; `#displayRow` below `#gameButtons`, Blitztabelle left, Trefferbild right, a single active panel is centred.
- Commands are run from `dart-server/onion-server` unless stated otherwise.

## Review Focus

1. **Remote/spectating TV against an anthrax server or older onion-server** (no `gameMode`, `modeData`, `strafpunkteProjektion`): must behave as X01 without double-out and show booked penalties only — no `undefined`/`NaN` in the UI. → tests in Task 4 (`hintFor` without `modeData`) and Task 7 (`rows` without `strafpunkteProjektion`).
2. **Double-in player not yet opened** (`punktestand == startScore`): no hint even if start score is ≤ 170 (e.g. free start score 101). → Task 4 test.
3. **WAITING state / turn over** (`freieWuerfe == 0`): hint hidden, not "–". → Task 4 test.
4. **Missing `getroffeneFelder`** (optional in schema, e.g. anthrax) or a player without any hit (max 0): board stays white, no division by zero. → Task 6 test.
5. **Penalty cost 0 Cent** (default in TV settings): ranking switches to placement sums — wall hits/Schnapszahl must not change the order; first game with everyone at Σ 0 before projection → no bar, no division by zero. → Task 7 test.

---

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/it/tobaben/dart/game/DartLikeGame.java` | + `getProjectedRanking()` contract |
| `src/main/java/it/tobaben/dart/game/x01/X01Game.java` | X01 projection (finished, then remaining score asc) |
| `src/main/java/it/tobaben/dart/game/cricket/CricketScoreboard.java` / `CricketGame.java` | Cricket projection (points desc, marks desc) |
| `src/main/java/it/tobaben/dart/application/PlayerStatistics.java` | + `summePlatzierungen` (placement sum, rule-independent) |
| `src/main/java/it/tobaben/dart/application/EngineUpdate.java` | + `projectedPlaces`, `projectedPenalties` (player id → value) |
| `src/main/java/it/tobaben/dart/application/GameEngine.java` | books placement sum at game end; computes projections per publish |
| `src/main/java/it/tobaben/dart/infrastructure/AnthraxJsonMapper.java` | always writes `gameMode`/`modeData`; writes `platzProjektion`, `strafpunkteProjektion` |
| `dart-server/schema.json` | documents both changes (optional fields) |
| `src/main/resources/web/tv/js/checkout.js` | **new** — pure checkout search + hint decision (Node-loadable) |
| `src/main/resources/web/tv/js/displays.js` | **new** — `tvDisplays` settings + menu entries |
| `src/main/resources/web/tv/js/heatmap.js` | **new** — SVG board + colouring |
| `src/main/resources/web/tv/js/ranking.js` | **new** — ranking rows + table rendering |
| `src/main/resources/web/tv/js/app.js` | hooks: menu entry, modal cycling, render calls |
| `src/main/resources/web/tv/index.html` / `tv.css` | new containers, script tags, styles |
| `src/test/js/*.test.js` | **new** — `node --test` tests for checkout/heatmap/ranking logic |
| `README.md` (onion-server) | JS test command, TV displays note |

---

### Task 1: X01 updates carry `gameMode` + `modeData`

**Files:**
- Modify: `src/main/java/it/tobaben/dart/infrastructure/AnthraxJsonMapper.java:14-20` (javadoc), `:51-58` (condition)
- Modify: `src/test/java/it/tobaben/dart/infrastructure/AnthraxJsonMapperTest.java:74-76`
- Modify: `dart-server/schema.json` (`gameMode`, `modeData` descriptions)

**Interfaces:**
- Produces: every gameUpdate has `gameMode` (`"X01"`/`"CRICKET"`) and `modeData`; X01 keys `startScore`, `doubleIn`, `doubleOut` as strings `"true"`/`"false"`/number string.

- [ ] **Step 1: Change the test to expect the fields**

In `AnthraxJsonMapperTest.x01UpdateContainsAllSchemaFields` replace

```java
        // X01 stays byte-compatible: no extension fields
        assertFalse(root.has("gameMode"));
        assertFalse(root.has("modeData"));
```

with

```java
        // X01 carries its rules too, so clients can compute e.g. checkout hints
        assertEquals("X01", root.get("gameMode").getAsString());
        JsonObject modeData = root.getAsJsonObject("modeData");
        assertEquals("301", modeData.get("startScore").getAsString());
        assertEquals("false", modeData.get("doubleIn").getAsString());
        assertEquals("false", modeData.get("doubleOut").getAsString());
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "it.tobaben.dart.infrastructure.AnthraxJsonMapperTest"`
Expected: FAIL in `x01UpdateContainsAllSchemaFields` (`gameMode` is null → NullPointerException / assertion).

- [ ] **Step 3: Always write the fields**

In `AnthraxJsonMapper.toJson` replace

```java
        if (!X01Game.GAME_MODE.equals(update.snapshot().gameMode())) {
            root.addProperty("gameMode", update.snapshot().gameMode());
            JsonObject modeData = new JsonObject();
            for (Map.Entry<String, String> entry : update.snapshot().modeData().entrySet()) {
                modeData.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("modeData", modeData);
        }
```

with

```java
        root.addProperty("gameMode", update.snapshot().gameMode());
        JsonObject modeData = new JsonObject();
        for (Map.Entry<String, String> entry : update.snapshot().modeData().entrySet()) {
            modeData.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("modeData", modeData);
```

Remove the now unused `import it.tobaben.dart.game.x01.X01Game;`. Update the class javadoc sentence to:

```java
 * with embedded statistik, gameState as string. The schema extension fields
 * "gameMode" and "modeData" are always appended (X01: startScore, doubleIn,
 * doubleOut); "punktestand" carries the mode's main score (Cricket: points).
```

- [ ] **Step 4: Update `dart-server/schema.json` descriptions** (no structural change, still not required)

```json
    "gameMode": {
      "type": "string",
      "description": "optional (onion-server, immer gesendet): Spielmodus X01 oder CRICKET; fehlt das Feld (anthrax), gilt X01"
    },
    "modeData": {
      "type": "object",
      "description": "optional (onion-server, immer gesendet): modusspezifische Zusatzdaten als String-Map. X01: startScore, doubleIn, doubleOut (\"true\"/\"false\"). Cricket: marks:<Spielername>:<Feld 15-20|25> = Anzahl Marks 0-3",
      "additionalProperties": {
        "type": "string"
      }
    }
```

Also change the root `description` to: `"status/gameUpdate Nachricht der Dart-Server (anthrax + onion). Basisfelder sind X01; gameMode/modeData/strafpunkteProjektion sind optionale Erweiterungen des onion-servers (fehlen sie, gilt X01 wie bei anthrax)."`

- [ ] **Step 5: Run all tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. If another test asserted the absence of `gameMode` for X01, update it the same way as Step 1.

- [ ] **Step 6: Hand over for commit** — changed: `AnthraxJsonMapper.java`, `AnthraxJsonMapperTest.java`, `dart-server/schema.json`.

---

### Task 2: Projected ranking in the game domain

**Files:**
- Modify: `src/main/java/it/tobaben/dart/game/DartLikeGame.java` (after `getRanking()`)
- Modify: `src/main/java/it/tobaben/dart/game/x01/X01Game.java` (after `getRanking()`, ~line 246)
- Modify: `src/main/java/it/tobaben/dart/game/cricket/CricketScoreboard.java` (after `getRanking()`, ~line 151)
- Modify: `src/main/java/it/tobaben/dart/game/cricket/CricketGame.java` (after `getRanking()`, ~line 188)
- Test: `src/test/java/it/tobaben/dart/game/x01/X01GameTest.java`, `src/test/java/it/tobaben/dart/game/cricket/CricketGameTest.java`

**Interfaces:**
- Produces: `List<Player> DartLikeGame.getProjectedRanking()` — all players, best first, "as if the game ended now". Never empty when players exist.

- [ ] **Step 1: Write failing X01 tests** (append to `X01GameTest`)

```java
    @Test
    void projectedRankingShouldOrderOpenPlayersByRemainingScore() throws GameOverException, InvalidStateException {
        X01Game game = default301();
        Player p1 = new Player("P1");
        Player p2 = new Player("P2");
        Player p3 = new Player("P3");
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.addPlayer(p3);
        // before the first dart everybody has 301: turn order
        assertEquals(List.of(p1, p2, p3), game.getProjectedRanking());

        game.playTurn(Segment.SINGLE_1);
        game.playTurn(Segment.SINGLE_1);
        game.playTurn(Segment.SINGLE_1);   // P1 298
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20);  // P2 121
        game.playTurn(Segment.TRIPLE_20);  // P3 mid-turn, staged 241
        assertEquals(List.of(p2, p3, p1), game.getProjectedRanking());
    }

    @Test
    void projectedRankingShouldPutFinishedPlayersFirst() throws GameOverException, InvalidStateException {
        X01Game game = new X01Game(40, false, false);
        Player p1 = new Player("P1");
        Player p2 = new Player("P2");
        Player p3 = new Player("P3");
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.addPlayer(p3);

        game.playTurn(Segment.DOUBLE_20);  // P1 finished
        game.playTurn(Segment.SINGLE_1);
        game.playTurn(Segment.SINGLE_1);
        game.playTurn(Segment.SINGLE_1);   // P2 37
        game.playTurn(Segment.SINGLE_20);  // P3 mid-turn 20
        assertEquals(List.of(p1, p3, p2), game.getProjectedRanking());
    }
```

- [ ] **Step 2: Write failing Cricket test** (append to `CricketGameTest`)

```java
    @Test
    void projectedRankingShouldFollowPointsWhileRunning() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);
        // not started yet: turn order
        assertEquals(List.of(player1, player2), game.getProjectedRanking());

        game.playTurn(Segment.SINGLE_1);
        game.playTurn(Segment.SINGLE_1);
        game.playTurn(Segment.SINGLE_1);   // Player1: nothing
        game.playTurn(Segment.TRIPLE_20);  // Player2 closes 20
        game.playTurn(Segment.TRIPLE_20);  // Player2 scores 60
        assertEquals(List.of(player2, player1), game.getProjectedRanking());
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "it.tobaben.dart.game.x01.X01GameTest" --tests "it.tobaben.dart.game.cricket.CricketGameTest"`
Expected: compilation FAIL — `cannot find symbol: method getProjectedRanking()`.

- [ ] **Step 4: Add the interface method** (in `DartLikeGame`, below `getRanking()`)

```java
    /**
     * Returns the ranking as if the game ended now: finished players in their
     * final order, then the remaining players by the mode's current standing,
     * ties in turn order. Used for live penalty projections.
     *
     * @return all players, best first
     */
    List<Player> getProjectedRanking();
```

- [ ] **Step 5: Implement X01** (in `X01Game`, below `getRanking()`; add `import java.util.Comparator;` if missing)

```java
    @Override
    public List<Player> getProjectedRanking() {
        List<Player> projected = new ArrayList<>(getRanking());
        Map<Player, Integer> scores = getSnapshot().scores();
        // stable sort: equal scores keep the turn order
        this.players.stream()
                .filter(player -> !projected.contains(player))
                .sorted(Comparator.comparingInt(scores::get))
                .forEach(projected::add);
        return projected;
    }
```

- [ ] **Step 6: Implement Cricket** — in `CricketScoreboard`, below `getRanking()`:

```java
    /**
     * @return the final ranking once decided, otherwise the current standing:
     *         points descending, then total marks descending, then turn order
     */
    public List<Player> getProjectedRanking() {
        List<Player> ranking = getRanking();
        if (!ranking.isEmpty()) {
            return ranking;
        }
        return this.players.stream()
                .sorted(Comparator
                        .comparingInt((Player player) -> getPoints(player)).reversed()
                        .thenComparing(Comparator.comparingInt(this::totalMarks).reversed()))
                .toList();
    }
```

In `CricketGame`, below `getRanking()`:

```java
    @Override
    public List<Player> getProjectedRanking() {
        if (this.scoreboard == null) {
            return List.copyOf(this.players);
        }
        return this.scoreboard.getProjectedRanking();
    }
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (any other `DartLikeGame` implementation, e.g. a test double, must now implement the method — `./gradlew test` compile errors show it; implement as `return getRanking();`).

- [ ] **Step 8: Hand over for commit** — changed: `DartLikeGame.java`, `X01Game.java`, `CricketScoreboard.java`, `CricketGame.java`, `X01GameTest.java`, `CricketGameTest.java`.

---

### Task 3: Placement sum + projected places/penalties in engine, JSON, schema; version bump

**Files:**
- Modify: `src/main/java/it/tobaben/dart/application/PlayerStatistics.java`
- Modify: `src/main/java/it/tobaben/dart/application/EngineUpdate.java`
- Modify: `src/main/java/it/tobaben/dart/application/GameEngine.java:183-189` (`finishTurn` game-over block), `:244-258` (`publish()`)
- Modify: `src/main/java/it/tobaben/dart/infrastructure/AnthraxJsonMapper.java` (`spieler(...)`)
- Modify: `dart-server/schema.json` (`definitions.statistik.properties`, `definitions.spieler.properties`)
- Modify: `build.gradle:11`
- Test: `src/test/java/it/tobaben/dart/application/PlayerStatisticsTest.java`, `GameEngineTest.java`, `src/test/java/it/tobaben/dart/infrastructure/AnthraxJsonMapperTest.java`

**Interfaces:**
- Consumes: `DartLikeGame.getProjectedRanking()` (Task 2).
- Produces:
  - `PlayerStatistics.getSummePlatzierungen()` / `addPlatzierung(int place)`; JSON `statistik.summePlatzierungen` (Gson serialises all fields automatically).
  - `EngineUpdate.projectedPlaces()` → `Map<Integer, Integer>` (RegisteredPlayer `id` → projected place 1…n; empty once the game is FINISHED).
  - `EngineUpdate.projectedPenalties()` → `Map<Integer, Integer>` (= `projectedPlaces` if the placement rule is on, else empty).
  - JSON per player: `platzProjektion`, `strafpunkteProjektion` (int, 0 if the id is missing from the map).

- [ ] **Step 1: Write failing statistics test** (append to `PlayerStatisticsTest`)

```java
    @Test
    void placementsShouldBeSummedAndSurviveGameReset() {
        PlayerStatistics statistics = new PlayerStatistics();
        statistics.addPlatzierung(2);
        statistics.resetGameData();
        statistics.addPlatzierung(1);
        assertEquals(3, statistics.getSummePlatzierungen());
        assertEquals(0, statistics.getAnzStrafpunkte()); // independent of penalties
    }
```

- [ ] **Step 2: Write failing engine tests** (append to `GameEngineTest`; add `import java.util.Map;`)

```java
    @Test
    void projectionsShouldFollowCurrentStandings() {
        GameEngine engine = startedEngine(301);
        // nobody has thrown: turn order
        assertEquals(Map.of(0, 1, 1, 2), ports.lastUpdate().projectedPlaces());
        assertEquals(Map.of(0, 1, 1, 2), ports.lastUpdate().projectedPenalties());

        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_1));
        engine.handle(DartboardInput.next(1));  // P1 300, WAITING
        engine.handle(DartboardInput.next(2));  // P2's turn
        engine.handle(DartboardInput.ofThrow(2, Segment.TRIPLE_20)); // P2 241
        assertEquals(Map.of(0, 2, 1, 1), ports.lastUpdate().projectedPlaces());
        assertEquals(Map.of(0, 2, 1, 1), ports.lastUpdate().projectedPenalties());
    }

    @Test
    void placesAreProjectedEvenWithoutPlacementRule() {
        GameEngine engine = new GameEngine(
                new X01Game(301, false, false), List.of(player1, player2),
                1, 1, 10, new HouseRules(true, true, false), ports, ports);
        engine.start();
        assertEquals(Map.of(0, 1, 1, 2), ports.lastUpdate().projectedPlaces());
        assertTrue(ports.lastUpdate().projectedPenalties().isEmpty());
    }

    @Test
    void projectionsShouldBeEmptyOnceGameIsFinished() {
        GameEngine engine = startedEngine(40);
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20)); // win, booked
        assertEquals(EngineState.FINISHED, engine.getState());
        assertTrue(ports.lastUpdate().projectedPlaces().isEmpty());
        assertTrue(ports.lastUpdate().projectedPenalties().isEmpty());
    }

    @Test
    void placementSumIsBookedRegardlessOfHouseRules() {
        GameEngine engine = new GameEngine(
                new X01Game(40, false, false), List.of(player1, player2),
                1, 1, 0, HouseRules.allOff(), ports, ports);
        engine.start();
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20)); // P1 wins
        assertEquals(1, player1.statistics().getSummePlatzierungen());
        assertEquals(2, player2.statistics().getSummePlatzierungen());
        assertEquals(0, player1.statistics().getAnzStrafpunkte());
        assertEquals(0, player2.statistics().getAnzStrafpunkte());
    }
```

- [ ] **Step 3: Write failing mapper test** (append to `AnthraxJsonMapperTest`; add `import com.google.gson.JsonArray;`)

```java
    @Test
    void spielerCarryProjectionsAndPlacementSum() {
        // Alice threw T20 (241) vs Bob 301: Alice place 1, Bob place 2
        JsonObject root = JsonParser.parseString(AnthraxJsonMapper.toJson(playX01Update().lastUpdate())).getAsJsonObject();
        JsonArray order = root.getAsJsonArray("spielerReihenfolge");
        JsonObject alice = order.get(0).getAsJsonObject();
        JsonObject bob = order.get(1).getAsJsonObject();
        assertEquals(1, alice.get("platzProjektion").getAsInt());
        assertEquals(2, bob.get("platzProjektion").getAsInt());
        assertEquals(1, alice.get("strafpunkteProjektion").getAsInt());
        assertEquals(2, bob.get("strafpunkteProjektion").getAsInt());
        assertEquals(1, root.getAsJsonObject("currentPlayer").get("platzProjektion").getAsInt());
        assertEquals(0, alice.getAsJsonObject("statistik").get("summePlatzierungen").getAsInt());
    }
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew test --tests "it.tobaben.dart.application.*" --tests "it.tobaben.dart.infrastructure.AnthraxJsonMapperTest"`
Expected: compilation FAIL — `cannot find symbol: addPlatzierung / projectedPlaces()`.

- [ ] **Step 5: `PlayerStatistics`** — add field after `anzStrafpunkte` and method after `addStrafpunkte`:

```java
    // sum of final places (1 = winner) over all finished games, independent of
    // the house rules — tournament ranking when penalty points cost nothing
    private int summePlatzierungen = 0;
```

```java
    public void addPlatzierung(int place) {
        this.summePlatzierungen += place;
    }
```

- [ ] **Step 6: Book placements in `GameEngine.finishTurn`** — replace

```java
        if (gameOver) {
            if (this.houseRules.placementPenalty()) {
                List<RegisteredPlayer> ranking = getRanking();
                for (int place = 0; place < ranking.size(); place++) {
                    ranking.get(place).statistics().addStrafpunkte(place + 1);
                }
            }
```

with

```java
        if (gameOver) {
            List<RegisteredPlayer> ranking = getRanking();
            for (int place = 0; place < ranking.size(); place++) {
                ranking.get(place).statistics().addPlatzierung(place + 1);
                if (this.houseRules.placementPenalty()) {
                    ranking.get(place).statistics().addStrafpunkte(place + 1);
                }
            }
```

- [ ] **Step 7: Extend `EngineUpdate`** — add as last record components (add `import java.util.Map;`) and document them in the record javadoc:

```java
        List<RegisteredPlayer> ranking,
        // RegisteredPlayer id -> place if the game ended now; empty once finished
        Map<Integer, Integer> projectedPlaces,
        // projectedPlaces if the placement penalty rule is on, otherwise empty
        Map<Integer, Integer> projectedPenalties
) {
```

- [ ] **Step 8: Compute in `GameEngine`** — in `publish()` compute once before `new EngineUpdate(`:

```java
        Map<Integer, Integer> places = projectedPlaces();
```

and pass as new last arguments after `getRanking()`:

```java
                getRanking(),
                places,
                this.houseRules.placementPenalty() ? places : Map.of()
```

Add below `publish()`:

```java
    private Map<Integer, Integer> projectedPlaces() {
        if (this.state == EngineState.FINISHED) {
            // final places are already booked into the statistics
            return Map.of();
        }
        Map<Integer, Integer> places = new HashMap<>();
        List<Player> projected = this.game.getProjectedRanking();
        for (int place = 0; place < projected.size(); place++) {
            places.put(this.byPlayer.get(projected.get(place)).id(), place + 1);
        }
        return places;
    }
```

(`byPlayer` is `Map<Player, RegisteredPlayer>`; add `HashMap`/`Map`/`Player` imports if the compiler asks.)

- [ ] **Step 9: Write the JSON fields** — in `AnthraxJsonMapper.spieler(...)` after the `freieWuerfe` line:

```java
        spieler.addProperty("platzProjektion",
                update.projectedPlaces().getOrDefault(registered.id(), 0));
        spieler.addProperty("strafpunkteProjektion",
                update.projectedPenalties().getOrDefault(registered.id(), 0));
```

`summePlatzierungen` needs no mapper change (`GSON.toJsonTree(statistics)`).

- [ ] **Step 10: Document in `dart-server/schema.json`** (none of them in `required`):

In `definitions.statistik.properties` after `getroffeneFelder`:

```json
        "summePlatzierungen": {
          "type": "integer",
          "description": "optional (onion-server): Summe der Endplätze aller beendeten Spiele des Turniers (Platz 1 = 1, ...), unabhängig von den Hausregeln"
        },
```

In `definitions.spieler.properties` after `freieWuerfe`:

```json
        "platzProjektion": {
          "type": "integer",
          "description": "optional (onion-server): Platz, falls das laufende Spiel jetzt endete (1 = vorn); 0 wenn das Spiel beendet ist (dann in statistik.summePlatzierungen gebucht)"
        },
        "strafpunkteProjektion": {
          "type": "integer",
          "description": "optional (onion-server): prognostizierte Platzierungsstrafpunkte, falls das laufende Spiel jetzt endete (= platzProjektion); 0 wenn die Platzierungsstrafe aus ist oder das Spiel beendet ist (dann bereits in statistik.anzStrafpunkte gebucht)"
        },
```

Mind the JSON commas (the previously last property needs a trailing comma, the new last one none). Validate: `python3 -m json.tool ../schema.json > /dev/null && echo ok` → `ok`.

- [ ] **Step 11: Version bump** — `build.gradle` line 11: `version = '2.2.0' + (findProperty('versionSuffix') ?: '')`.

- [ ] **Step 12: Run all tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 13: Hand over for commit** — changed: `PlayerStatistics.java`, `EngineUpdate.java`, `GameEngine.java`, `AnthraxJsonMapper.java`, `PlayerStatisticsTest.java`, `GameEngineTest.java`, `AnthraxJsonMapperTest.java`, `dart-server/schema.json`, `build.gradle`.

---

### Task 4: Checkout logic (`checkout.js`) with Node tests

**Files:**
- Create: `src/main/resources/web/tv/js/checkout.js`
- Create: `src/test/js/checkout.test.js`

**Interfaces:**
- Produces (global `Checkout` in browser, `module.exports` in Node):
  - `Checkout.best(score: number, maxDarts: number, doubleOut: boolean) → Array<{code, value, type}> | null`
  - `Checkout.format(segments | null) → string` (`"T19 + 10 + D16"`, `"Bull"`, `"B-Eye"`, `"–"`)
  - `Checkout.hintFor(gameUpdate, mode: 0|1|2|3) → string | null` (`null` = hide line)

- [ ] **Step 1: Write the failing tests** — `src/test/js/checkout.test.js`:

```js
// Aufruf: node --test src/test/js/   (aus dart-server/onion-server)
const test = require('node:test');
const assert = require('node:assert');
const Checkout = require('../../main/resources/web/tv/js/checkout.js');

const hint = (score, darts, doubleOut) => Checkout.format(Checkout.best(score, darts, doubleOut));

test('1-Dart-Referenzfälle ohne Double-Out', () => {
  assert.strictEqual(hint(45, 1, false), 'T15');
  assert.strictEqual(hint(30, 1, false), 'T10');   // Triple vor Double
  assert.strictEqual(hint(18, 1, false), '18');    // Single vor Double/Triple
  assert.strictEqual(hint(40, 1, false), 'D20');
  assert.strictEqual(hint(50, 1, false), 'B-Eye');
  assert.strictEqual(hint(25, 1, false), 'Bull');
  assert.strictEqual(hint(59, 1, false), '–');
});

test('wenigste Darts vor Feldtyp', () => {
  assert.strictEqual(hint(30, 2, false), 'T10');   // nicht 20 + 10
  assert.strictEqual(hint(18, 3, false), '18');
});

test('Mehr-Dart-Wege', () => {
  assert.strictEqual(hint(61, 2, false), 'T20 + 1');
  assert.strictEqual(hint(171, 3, false), 'T20 + T20 + T17');
  assert.strictEqual(hint(180, 3, false), 'T20 + T20 + T20');
  assert.strictEqual(hint(181, 3, false), '–');
});

test('Double-Out', () => {
  assert.strictEqual(hint(99, 3, true), 'T20 + 19 + D10');
  assert.strictEqual(hint(2, 1, true), 'D1');
  assert.strictEqual(hint(1, 3, true), '–');
  assert.strictEqual(hint(50, 1, true), 'B-Eye');  // Bullseye zählt als Double
  assert.strictEqual(hint(45, 1, true), '–');
  assert.strictEqual(hint(170, 3, true), 'T20 + T20 + B-Eye');
  assert.strictEqual(hint(171, 3, true), '–');
});

test('ungültige Eingaben', () => {
  assert.strictEqual(Checkout.best(0, 3, false), null);
  assert.strictEqual(Checkout.best(-5, 3, false), null);
  assert.strictEqual(Checkout.best(20, 0, false), null);
});

const update = (punktestand, freieWuerfe, modeData) => ({
  gameMode: modeData ? 'X01' : undefined,
  modeData,
  currentPlayer: { punktestand, freieWuerfe }
});
const X01 = (doubleOut, doubleIn, startScore) =>
  ({ startScore: String(startScore || 301), doubleIn: String(!!doubleIn), doubleOut: String(!!doubleOut) });

test('hintFor: Modus und freie Darts', () => {
  assert.strictEqual(Checkout.hintFor(update(45, 3, X01(false)), 0), null);         // aus
  assert.strictEqual(Checkout.hintFor(update(45, 3, X01(false)), 1), 'T15');
  assert.strictEqual(Checkout.hintFor(update(99, 3, X01(true)), 3), 'T20 + 19 + D10');
  assert.strictEqual(Checkout.hintFor(update(99, 1, X01(true)), 3), null);         // 1 Dart übrig, > 50
  assert.strictEqual(Checkout.hintFor(update(30, 2, X01(false)), 3), 'T10');
  assert.strictEqual(Checkout.hintFor(update(61, 3, X01(false)), 1), null);        // > 60 bei 1-Dart
});

test('hintFor: unmöglich innerhalb Reichweite zeigt Strich', () => {
  assert.strictEqual(Checkout.hintFor(update(59, 1, X01(false)), 1), '–');
  assert.strictEqual(Checkout.hintFor(update(1, 3, X01(true)), 3), '–');
});

test('hintFor: Spielzug beendet (WAITING) blendet aus', () => {
  assert.strictEqual(Checkout.hintFor(update(40, 0, X01(false)), 3), null);
});

test('hintFor: Double-In noch nicht eröffnet', () => {
  assert.strictEqual(Checkout.hintFor(update(101, 3, X01(false, true, 101)), 3), null);
  assert.strictEqual(Checkout.hintFor(update(100, 3, X01(false, true, 101)), 3), 'T20 + D20');
});

test('hintFor: ohne modeData (anthrax) = X01 ohne Double-Out', () => {
  assert.strictEqual(Checkout.hintFor(update(40, 3, undefined), 3), 'D20');
  assert.strictEqual(Checkout.hintFor(update(41, 3, undefined), 3), 'T13 + 2');
});

test('hintFor: Cricket und fehlende Daten', () => {
  assert.strictEqual(Checkout.hintFor({ gameMode: 'CRICKET', currentPlayer: { punktestand: 20, freieWuerfe: 3 } }, 3), null);
  assert.strictEqual(Checkout.hintFor(null, 3), null);
  assert.strictEqual(Checkout.hintFor({}, 3), null);
});
```

Expected values checked by hand against the rule (fewest darts → cost sum → highest first dart):
`100` (2+ darts, no DO): no 2-dart way with cost < 3 (T+S ≤ 80, T+T needs a multiple of 3, S+D ≤ 60) → T20 + D20.
`41` (no DO): S+S ≤ 40, so cost 1 (T+S) with the highest triple 21…39 → T13 + 2.
`99` (DO): no 2-dart way; S+S+D ≤ 80 → cost 3 (T+S+D), highest T20, then 39 = 19 + D10.
`170` (DO): only T20 + T20 + Bullseye.

- [ ] **Step 2: Run tests to verify they fail**

Run: `node --test src/test/js/`
Expected: FAIL — `Cannot find module '../../main/resources/web/tv/js/checkout.js'`.

- [ ] **Step 3: Implement** — `src/main/resources/web/tv/js/checkout.js`:

```js
/* Finish-Hilfe: bester Weg zum Checkout mit höchstens n Darts.
   Reihenfolge: wenigste Darts, dann geringste Feldtyp-Kosten (Single < Triple
   < Double < Bull < Bullseye — Doubles liegen am Rand, Bull ist klein), dann
   höchster erster Dart. Reine Logik ohne DOM, auch unter Node ladbar. */
const Checkout = (() => {

  const TYPE_COST = { S: 0, T: 1, D: 2, B: 3, BE: 4 };

  const SEGMENTS = [];
  for (let n = 1; n <= 20; n++) {
    SEGMENTS.push({ code: 'S' + n, value: n, type: 'S' });
    SEGMENTS.push({ code: 'T' + n, value: 3 * n, type: 'T' });
    SEGMENTS.push({ code: 'D' + n, value: 2 * n, type: 'D' });
  }
  SEGMENTS.push({ code: 'B', value: 25, type: 'B' });
  SEGMENTS.push({ code: 'BE', value: 50, type: 'BE' });

  // Bullseye zählt wie im Server (Segment.isDouble) als Double
  const isDouble = (segment) => segment.type === 'D' || segment.type === 'BE';

  const cost = (path) => path.reduce((sum, segment) => sum + TYPE_COST[segment.type], 0);

  // Anzeige-Reihenfolge: absteigend nach Wert, bei Double-Out das Double zuletzt
  function normalize(path, doubleOut) {
    if (!doubleOut) {
      return [...path].sort((a, b) => b.value - a.value);
    }
    const last = path[path.length - 1];
    return [...path.slice(0, -1).sort((a, b) => b.value - a.value), last];
  }

  function better(a, b) {
    const costDiff = cost(a) - cost(b);
    if (costDiff !== 0) {
      return costDiff < 0;
    }
    for (let i = 0; i < a.length; i++) {
      if (a[i].value !== b[i].value) {
        return a[i].value > b[i].value;
      }
    }
    return false;
  }

  function search(rest, dartsLeft, path, doubleOut, onFound) {
    if (dartsLeft === 1) {
      for (const segment of SEGMENTS) {
        if (segment.value === rest && (!doubleOut || isDouble(segment))) {
          onFound([...path, segment]);
        }
      }
      return;
    }
    for (const segment of SEGMENTS) {
      if (segment.value < rest) {
        search(rest - segment.value, dartsLeft - 1, [...path, segment], doubleOut, onFound);
      }
    }
  }

  function best(score, maxDarts, doubleOut) {
    if (!Number.isInteger(score) || score <= 0 || !(maxDarts >= 1)) {
      return null;
    }
    for (let darts = 1; darts <= Math.min(maxDarts, 3); darts++) {
      let winner = null;
      search(score, darts, [], doubleOut, (path) => {
        const candidate = normalize(path, doubleOut);
        if (!winner || better(candidate, winner)) {
          winner = candidate;
        }
      });
      if (winner) {
        return winner;
      }
    }
    return null;
  }

  function label(segment) {
    switch (segment.type) {
      case 'S': return String(segment.value);
      case 'B': return 'Bull';
      case 'BE': return 'B-Eye';
      default: return segment.code;
    }
  }

  function format(path) {
    return path ? path.map(label).join(' + ') : '–';
  }

  // höchste mit n Darts erreichbare Restpunktzahl
  function maxReach(darts, doubleOut) {
    return doubleOut ? 50 + (darts - 1) * 60 : darts * 60;
  }

  /* Text für die Finish-Hilfe-Zeile oder null (Zeile ausblenden).
     Ohne gameMode/modeData (anthrax) gilt X01 ohne Double-In/-Out. */
  function hintFor(data, mode) {
    if (!mode || !data || !data.currentPlayer) {
      return null;
    }
    if (data.gameMode && data.gameMode !== 'X01') {
      return null;
    }
    const rules = data.modeData || {};
    const player = data.currentPlayer;
    const darts = Math.min(mode, player.freieWuerfe || 0);
    if (darts < 1) {
      return null;
    }
    const score = player.punktestand;
    // Double-In: der Stand sinkt erst nach dem eröffnenden Double
    if (rules.doubleIn === 'true' && String(score) === rules.startScore) {
      return null;
    }
    const doubleOut = rules.doubleOut === 'true';
    if (!(score > 0) || score > maxReach(darts, doubleOut)) {
      return null;
    }
    return format(best(score, darts, doubleOut));
  }

  return { best, format, hintFor };
})();

if (typeof module !== 'undefined') {
  module.exports = Checkout;
}
```

Note: `search` only recurses with `segment.value < rest`, so no intermediate score reaches 0 or below; with double-out the remainder before the last dart is ≥ 2 automatically because the last dart is a double.

- [ ] **Step 4: Run tests to verify they pass**

Run: `node --test src/test/js/`
Expected: all tests pass. Runtime: the 3-dart search is ≤ 62² × 62 ≈ 240k leaf checks — must finish well under 100 ms; if `node --test` reports a test > 100 ms, prune in `search` by `segment.value <= rest - (dartsLeft - 1)`.

- [ ] **Step 5: Hand over for commit** — new: `js/checkout.js`, `src/test/js/checkout.test.js`.

---

### Task 5: "Anzeigen" menu, settings, layout containers, Finish-Hilfe wiring

**Files:**
- Create: `src/main/resources/web/tv/js/displays.js`
- Modify: `src/main/resources/web/tv/index.html` (containers + script tags)
- Modify: `src/main/resources/web/tv/js/app.js` (`renderModal` ~line 112, `modalHandle` ~line 128, `gameAction` `case 'menu'` ~line 900, `renderGameScreen` ~line 698)
- Modify: `src/main/resources/web/tv/tv.css`

**Interfaces:**
- Consumes: `Checkout.hintFor(data, mode)` (Task 4).
- Produces: global `Displays` with `Displays.get() → {finish, heatmap, ranking}`, `Displays.cycle(key, dir)`, `Displays.entries(x01: boolean) → [{key, label: () => string}]`. DOM: `#finishHint`, `#displayRow`, `#rankingPanel`, `#heatmapPanel`. `app.js` function `renderDisplays(data)` (later tasks add calls inside it).
- Modal entries may now carry `label` as function and an optional `onCycle(dir)`; such entries stay open on Ⓐ/◀▶.

- [ ] **Step 1: Create `js/displays.js`**

```js
/* Anzeigen-Einstellungen der Spielanzeige (Finish-Hilfe, Trefferbild,
   Blitztabelle) — pro TV in localStorage, kein Server-Zustand. */
const Displays = (() => {

  const KEY = 'tvDisplays';
  const DEFAULTS = { finish: 0, heatmap: false, ranking: false };
  const FINISH_LABELS = ['Aus', '1-Dart', '2-Dart', '3-Dart'];

  let state = load();

  function load() {
    try {
      const raw = localStorage.getItem(KEY);
      return raw ? { ...DEFAULTS, ...JSON.parse(raw) } : { ...DEFAULTS };
    } catch (e) {
      return { ...DEFAULTS };
    }
  }

  function save() {
    try {
      localStorage.setItem(KEY, JSON.stringify(state));
    } catch (e) {
      // ohne Speicher gilt die Wahl nur bis zum Neuladen
    }
  }

  function get() {
    return { ...state };
  }

  function cycle(key, dir) {
    if (key === 'finish') {
      state.finish = (state.finish + dir + FINISH_LABELS.length) % FINISH_LABELS.length;
    } else {
      state[key] = !state[key];
    }
    save();
  }

  // Finish-Hilfe nur bei X01
  function entries(x01) {
    const list = [];
    if (x01) {
      list.push({ key: 'finish', label: () => 'Finish-Hilfe: ' + FINISH_LABELS[state.finish] });
    }
    list.push({ key: 'heatmap', label: () => 'Trefferbild: ' + (state.heatmap ? 'An' : 'Aus') });
    list.push({ key: 'ranking', label: () => 'Blitztabelle: ' + (state.ranking ? 'An' : 'Aus') });
    return list;
  }

  return { get, cycle, entries };
})();
```

- [ ] **Step 2: Add containers + scripts to `index.html`**

In `#gameArea`, directly after `<div id="galleryViewport">…</div>`:

```html
      <div id="finishHint" hidden></div>
```

Directly after the closing `</div>` of `#gameButtons` (still inside `#screen-game`):

```html
    <!-- Anzeigen (Spielmenü → Anzeigen): Blitztabelle links, Trefferbild rechts -->
    <div id="displayRow" hidden>
      <section id="rankingPanel" hidden></section>
      <section id="heatmapPanel" hidden></section>
    </div>
```

Script tags: before `<script src="js/app.js"></script>` add

```html
  <script src="js/checkout.js"></script>
  <script src="js/displays.js"></script>
```

- [ ] **Step 3: Modal supports cycling entries** — in `app.js`:

`renderModal`: replace `const button = item(escapeHtml(entry.label), 'modal-' + i);` with

```js
      const text = typeof entry.label === 'function' ? entry.label() : entry.label;
      const button = item(escapeHtml(text), 'modal-' + i);
```

`modalHandle`: replace the `} else if (action === 'confirm') {` branch head with a cycling branch in front of it:

```js
    } else if (modal.buttons[modal.index].onCycle
        && (action === 'confirm' || action === 'left' || action === 'right')) {
      // Umschalt-Einträge (Anzeigen-Menü): Modal bleibt offen
      modal.buttons[modal.index].onCycle(action === 'left' ? -1 : 1);
      renderModal();
    } else if (action === 'confirm') {
```

(The mouse click handler calls `modalHandle('confirm')`, so clicks cycle too.)

- [ ] **Step 4: Menu entry** — in `gameAction`, `case 'menu'`, right after `const buttons = [{ label: 'Weiter spielen' }];`:

```js
        buttons.push({ label: 'Anzeigen', onSelect: openDisplaysMenu });
```

and add next to `reconnectBoards`:

```js
  function openDisplaysMenu() {
    const x01 = !lastGame || lastGame.gameMode !== 'CRICKET';
    const buttons = Displays.entries(x01).map((entry) => ({
      label: entry.label,
      onCycle: (dir) => {
        Displays.cycle(entry.key, dir);
        if (lastGame) {
          renderDisplays(lastGame);
        }
      }
    }));
    buttons.push({ label: 'Fertig' });
    openModal('Anzeigen', buttons);
  }
```

- [ ] **Step 5: Render hook** — in `renderGameScreen`, add as first lines after `$('gameMeta').hidden = !running;`:

```js
    if (!running) {
      $('finishHint').hidden = true;
      $('displayRow').hidden = true;
    }
```

and in the running `else` branch after `renderGameMeta(lastGame);` add `renderDisplays(lastGame);`. Add below `renderGameMeta`:

```js
  /* Spielmenü → Anzeigen: Finish-Hilfe, Blitztabelle, Trefferbild */
  function renderDisplays(data) {
    const options = Displays.get();
    const hint = Checkout.hintFor(data, options.finish);
    $('finishHint').hidden = hint === null;
    $('finishHint').textContent = hint === null ? '' : 'Finish: ' + hint;

    $('rankingPanel').hidden = !options.ranking;
    $('heatmapPanel').hidden = !options.heatmap;
    $('displayRow').hidden = !options.ranking && !options.heatmap;
  }
```

- [ ] **Step 6: Styles** — append to the "Spielanzeige" section in `tv.css`:

```css
/* Finish-Hilfe zwischen aktiver Kachel und Aktionsleiste */
#finishHint {
  text-align: center;
  font-size: 1.4em;
  font-weight: bold;
  color: var(--accent);
  margin-top: 1vh;
}

/* Anzeigen-Zeile unter der Aktionsleiste: Blitztabelle links, Trefferbild
   rechts; ist nur eines aktiv, steht es mittig */
#displayRow {
  display: flex;
  justify-content: center;
  gap: 2vw;
  height: 35vh;
  margin-top: 2vh;
}

#displayRow > section {
  flex: 0 1 45%;
  min-width: 0;
  background: var(--panel);
  border-radius: 0.8vw;
  padding: 1.5vh 1.5vw;
  overflow: hidden;
}
```

- [ ] **Step 7: Manual check** (no automated UI tests in this repo)

Terminal 1: `./gradlew run` · Terminal 2: `./gradlew processResources --continuous`. Open `http://localhost:8420/tv/`, set up X01 301 **with Double-Out** and two players, start. Then:
1. ☰/F → "Anzeigen" exists, second entry. Ⓐ/◀▶ on "Finish-Hilfe" cycles Aus → 1-Dart → 2-Dart → 3-Dart → Aus; menu stays open; "Fertig" closes.
2. Reload page → choice persisted.
3. Throw (MQTT or real board) T20, T20, T20, then next player down to 99 with 3 darts and Finish-Hilfe 3-Dart → line shows "Finish: T20 + 19 + D10" between tiles and buttons.
4. Trefferbild/Blitztabelle toggles show/hide empty panels below the buttons; one panel alone is centred.
5. Cricket game: "Finish-Hilfe" entry absent.
6. Check with the browser devtools console: no errors.

Simulate throws without board: `mosquitto_pub -h localhost -p 1883 -u dartboard -P smartness -t dartboard/1 -m 320` (= T20; see top-level README for codes) — or use a real board.

- [ ] **Step 8: Hand over for commit** — new `js/displays.js`; changed `index.html`, `js/app.js`, `tv.css`.

---

### Task 6: Trefferbild (`heatmap.js`)

**Files:**
- Create: `src/main/resources/web/tv/js/heatmap.js`
- Create: `src/test/js/heatmap.test.js`
- Modify: `index.html` (script tag), `js/app.js` (`renderDisplays`), `tv.css`

**Interfaces:**
- Consumes: `#heatmapPanel`, `renderDisplays(data)` (Task 5).
- Produces: global `Heatmap` with `Heatmap.countFor(felder, mult, field) → number`, `Heatmap.colour(count, max) → string`, `Heatmap.render(container, spieler)`.

- [ ] **Step 1: Write failing tests** — `src/test/js/heatmap.test.js`:

```js
const test = require('node:test');
const assert = require('node:assert');
const Heatmap = require('../../main/resources/web/tv/js/heatmap.js');

const empty = () => [0, 1, 2].map(() => new Array(21).fill(0));

test('countFor liest [mult-1][feld-1], Bull/Bullseye auf Index 20', () => {
  const felder = empty();
  felder[2][19] = 4;  // T20
  felder[0][20] = 2;  // Bull
  felder[1][20] = 1;  // Bullseye
  assert.strictEqual(Heatmap.countFor(felder, 3, 20), 4);
  assert.strictEqual(Heatmap.countFor(felder, 1, 25), 2);
  assert.strictEqual(Heatmap.countFor(felder, 2, 25), 1);
  assert.strictEqual(Heatmap.countFor(felder, 1, 5), 0);
});

test('countFor ohne Daten ist 0', () => {
  assert.strictEqual(Heatmap.countFor(undefined, 3, 20), 0);
  assert.strictEqual(Heatmap.countFor([], 3, 20), 0);
});

test('colour: weiß ohne Treffer, kräftiger mit mehr Anteil', () => {
  assert.strictEqual(Heatmap.colour(0, 0), '#ffffff');
  assert.strictEqual(Heatmap.colour(0, 5), '#ffffff');
  assert.strictEqual(Heatmap.colour(5, 5), 'hsl(210, 85%, 35%)');
  assert.notStrictEqual(Heatmap.colour(1, 5), Heatmap.colour(5, 5));
});
```

- [ ] **Step 2: Run to verify failure**

Run: `node --test src/test/js/`
Expected: FAIL — `Cannot find module '…/heatmap.js'`.

- [ ] **Step 3: Implement** — `src/main/resources/web/tv/js/heatmap.js`:

```js
/* Trefferbild: SVG-Dartboard des aktiven Spielers, Felder blau nach Anzahl
   Treffer (meistgetroffenes Feld = volles Blau). Datenbasis ist
   statistik.getroffeneFelder [mult-1][feld-1], Index 20 = Bull — ganzes
   Turnier; inneres und äußeres Single teilen einen Zähler. */
const Heatmap = (() => {

  const ORDER = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5];
  // Radien in mm nach Turnier-Norm
  const R = { bullseye: 6.35, bull: 15.9, tripleIn: 99, tripleOut: 107, doubleIn: 162, doubleOut: 170, numbers: 186 };
  const NS = 'http://www.w3.org/2000/svg';

  function countFor(felder, mult, field) {
    const row = felder && felder[mult - 1];
    if (!row) {
      return 0;
    }
    return row[field === 25 ? 20 : field - 1] || 0;
  }

  function colour(count, max) {
    if (!count || !max) {
      return '#ffffff';
    }
    const lightness = Math.round(92 - 57 * (count / max));
    return 'hsl(210, 85%, ' + lightness + '%)';
  }

  function point(r, deg) {
    const a = deg * Math.PI / 180;
    return (r * Math.cos(a)).toFixed(2) + ' ' + (r * Math.sin(a)).toFixed(2);
  }

  function ringSegment(r1, r2, a1, a2) {
    return 'M ' + point(r2, a1) + ' A ' + r2 + ' ' + r2 + ' 0 0 1 ' + point(r2, a2)
      + ' L ' + point(r1, a2) + ' A ' + r1 + ' ' + r1 + ' 0 0 0 ' + point(r1, a1) + ' Z';
  }

  function svgEl(name, attrs) {
    const el = document.createElementNS(NS, name);
    Object.entries(attrs).forEach(([key, value]) => el.setAttribute(key, value));
    return el;
  }

  function build(container) {
    const svg = svgEl('svg', { viewBox: '-200 -200 400 400', class: 'heatmap-board' });
    ORDER.forEach((field, i) => {
      // 20 oben, im Uhrzeigersinn, je 18°
      const center = -90 + i * 18;
      [[R.bull, R.tripleIn, 1], [R.tripleIn, R.tripleOut, 3],
       [R.tripleOut, R.doubleIn, 1], [R.doubleIn, R.doubleOut, 2]].forEach(([r1, r2, mult]) => {
        svg.appendChild(svgEl('path', {
          d: ringSegment(r1, r2, center - 9, center + 9),
          'data-mult': mult, 'data-field': field
        }));
      });
      const [x, y] = point(R.numbers, center).split(' ');
      const label = svgEl('text', { x, y, class: 'heatmap-number' });
      label.textContent = field;
      svg.appendChild(label);
    });
    svg.appendChild(svgEl('circle', { r: R.bull, 'data-mult': 1, 'data-field': 25 }));
    svg.appendChild(svgEl('circle', { r: R.bullseye, 'data-mult': 2, 'data-field': 25 }));
    const caption = document.createElement('div');
    caption.className = 'heatmap-caption';
    container.innerHTML = '';
    container.appendChild(svg);
    container.appendChild(caption);
  }

  function render(container, spieler) {
    if (!container.querySelector('svg')) {
      build(container);
    }
    const felder = spieler && spieler.statistik ? spieler.statistik.getroffeneFelder : undefined;
    const max = Array.isArray(felder) ? Math.max(0, ...felder.flat()) : 0;
    container.querySelectorAll('[data-mult]').forEach((el) => {
      const count = countFor(felder, Number(el.dataset.mult), Number(el.dataset.field));
      el.setAttribute('fill', colour(count, max));
    });
    container.querySelector('.heatmap-caption').textContent =
      'Trefferbild: ' + (spieler ? spieler.name : '');
  }

  return { countFor, colour, render };
})();

if (typeof module !== 'undefined') {
  module.exports = Heatmap;
}
```

- [ ] **Step 4: Run tests** — `node --test src/test/js/` → all pass.

- [ ] **Step 5: Wire up** — `index.html`: add `<script src="js/heatmap.js"></script>` after `displays.js`. In `renderDisplays` append:

```js
    if (options.heatmap) {
      Heatmap.render($('heatmapPanel'), data.currentPlayer);
    }
```

`tv.css` append:

```css
#heatmapPanel {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.heatmap-board {
  flex: 1;
  min-height: 0;
  width: 100%;
}

.heatmap-board path,
.heatmap-board circle {
  stroke: #9aa4b5;
  stroke-width: 0.8;
}

.heatmap-number {
  fill: var(--muted);
  font-size: 18px;
  text-anchor: middle;
  dominant-baseline: central;
}

.heatmap-caption {
  font-size: 0.6em;
  color: var(--muted);
}
```

- [ ] **Step 6: Manual check** (same setup as Task 5 Step 7): enable Trefferbild → white board with numbers 20 at top; throw T20 twice and S5 once with the current player → T20 dark blue, both single-5 areas light blue; switch player → board shows the other player's hits; reload mid-game → board rebuilt.

- [ ] **Step 7: Hand over for commit** — new `js/heatmap.js`, `src/test/js/heatmap.test.js`; changed `index.html`, `js/app.js`, `tv.css`.

---

### Task 7: Blitztabelle (`ranking.js`)

**Files:**
- Create: `src/main/resources/web/tv/js/ranking.js`
- Create: `src/test/js/ranking.test.js`
- Modify: `index.html` (script tag), `js/app.js` (`renderDisplays`), `tv.css`

**Interfaces:**
- Consumes: `spieler.strafpunkteProjektion`, `spieler.platzProjektion`, `statistik.summePlatzierungen` (Task 3), `#rankingPanel`, `renderDisplays(data)` (Task 5).
- Produces: global `Ranking` with `Ranking.placesMode(data) → boolean` (true when cost is 0/missing), `Ranking.rows(data) → [{id, name, booked, projected, total, euro, order}]` (sorted best first; `euro` is `null` in places mode), `Ranking.supported(data) → boolean` (false in places mode when the server sends no `summePlatzierungen`), `Ranking.formatEuro(euro)` and `Ranking.render(container, data)`.

- [ ] **Step 1: Write failing tests** — `src/test/js/ranking.test.js`:

```js
const test = require('node:test');
const assert = require('node:assert');
const Ranking = require('../../main/resources/web/tv/js/ranking.js');

const spieler = (id, name, stat, projections) =>
  ({ id, name, statistik: stat, ...projections });

test('Kosten > 0: Strafpunkte gebucht + Prognose, aufsteigend sortiert', () => {
  const data = {
    kostenStrafpunkte: 50,
    spielerReihenfolge: [
      spieler(1, 'Alice', { anzStrafpunkte: 4, summePlatzierungen: 1 }, { strafpunkteProjektion: 1, platzProjektion: 1 }),
      spieler(2, 'Bob', { anzStrafpunkte: 2, summePlatzierungen: 2 }, { strafpunkteProjektion: 2, platzProjektion: 2 })
    ]
  };
  assert.strictEqual(Ranking.placesMode(data), false);
  const rows = Ranking.rows(data);
  assert.deepStrictEqual(rows.map((r) => [r.name, r.booked, r.projected, r.total]),
    [['Bob', 2, 2, 4], ['Alice', 4, 1, 5]]);
  assert.strictEqual(rows[0].euro, 2);
});

test('Kosten 0: nur Platzierungen zählen, Wand/Schnapszahl egal', () => {
  const data = {
    kostenStrafpunkte: 0,
    spielerReihenfolge: [
      // Alice: viele Strafpunkte, aber bisher immer vorn
      spieler(1, 'Alice', { anzStrafpunkte: 9, summePlatzierungen: 2 }, { strafpunkteProjektion: 0, platzProjektion: 2 }),
      spieler(2, 'Bob', { anzStrafpunkte: 0, summePlatzierungen: 4 }, { strafpunkteProjektion: 0, platzProjektion: 1 })
    ]
  };
  assert.strictEqual(Ranking.placesMode(data), true);
  const rows = Ranking.rows(data);
  assert.deepStrictEqual(rows.map((r) => [r.name, r.booked, r.projected, r.total]),
    [['Alice', 2, 2, 4], ['Bob', 4, 1, 5]]);
  assert.strictEqual(rows[0].euro, null);
});

test('fehlende Kosten gelten als 0 (Platzierungs-Wertung)', () => {
  assert.strictEqual(Ranking.placesMode({ spielerReihenfolge: [] }), true);
});

test('Gleichstand behält Wurfreihenfolge', () => {
  const rows = Ranking.rows({
    kostenStrafpunkte: 10,
    spielerReihenfolge: [
      spieler(1, 'A', { anzStrafpunkte: 3 }, {}),
      spieler(2, 'B', { anzStrafpunkte: 3 }, {})
    ]
  });
  assert.deepStrictEqual(rows.map((r) => r.name), ['A', 'B']);
});

test('ohne neue Felder (anthrax/ältere Server) keine NaN', () => {
  const strafen = Ranking.rows({
    kostenStrafpunkte: 10,
    spielerReihenfolge: [{ id: 1, name: 'A', statistik: { anzStrafpunkte: 3 } }]
  });
  assert.strictEqual(strafen[0].total, 3);
  const plaetze = Ranking.rows({
    kostenStrafpunkte: 0,
    spielerReihenfolge: [{ id: 1, name: 'A', statistik: { anzStrafpunkte: 3 } }]
  });
  assert.strictEqual(plaetze[0].total, 0);
  assert.deepStrictEqual(Ranking.rows({}), []);
});

test('Platzierungs-Wertung braucht summePlatzierungen vom Server', () => {
  const alt = { kostenStrafpunkte: 0, spielerReihenfolge: [{ id: 1, name: 'A', statistik: { anzStrafpunkte: 3 } }] };
  const neu = { kostenStrafpunkte: 0, spielerReihenfolge: [{ id: 1, name: 'A', statistik: { anzStrafpunkte: 3, summePlatzierungen: 0 } }] };
  const strafen = { kostenStrafpunkte: 10, spielerReihenfolge: alt.spielerReihenfolge };
  assert.strictEqual(Ranking.supported(alt), false);
  assert.strictEqual(Ranking.supported(neu), true);    // 0 ist ein echter Wert
  assert.strictEqual(Ranking.supported(strafen), true); // Strafpunkte gibt es immer
  assert.strictEqual(Ranking.supported({}), true);      // keine Spieler: nichts Irreführendes
});

test('Euro-Format deutsch', () => {
  assert.strictEqual(Ranking.formatEuro(2), '2,00 €');
  assert.strictEqual(Ranking.formatEuro(0.5), '0,50 €');
});
```

- [ ] **Step 2: Run to verify failure** — `node --test src/test/js/` → FAIL, module missing.

- [ ] **Step 3: Implement** — `src/main/resources/web/tv/js/ranking.js`:

```js
/* Blitztabelle: Turnier-Ranking, weniger ist besser, Gleichstand nach
   Wurfreihenfolge. Kosten > 0: gebuchte Strafpunkte (statistik.anzStrafpunkte)
   + Platzierungs-Prognose (strafpunkteProjektion). Kosten 0: nur Plätze —
   Summe der Endplätze (statistik.summePlatzierungen) + Platz im laufenden
   Spiel (platzProjektion). Fehlende Felder (anthrax) zählen 0. */
const Ranking = (() => {

  function placesMode(data) {
    return !(data && data.kostenStrafpunkte > 0);
  }

  function rows(data) {
    const places = placesMode(data);
    const cents = (data && data.kostenStrafpunkte) || 0;
    const players = (data && data.spielerReihenfolge) || [];
    return players
      .map((spieler, order) => {
        const statistik = spieler.statistik || {};
        const booked = (places ? statistik.summePlatzierungen : statistik.anzStrafpunkte) || 0;
        const projected = (places ? spieler.platzProjektion : spieler.strafpunkteProjektion) || 0;
        const total = booked + projected;
        return {
          id: spieler.id, name: spieler.name, booked, projected, total,
          euro: places ? null : total * cents / 100, order
        };
      })
      .sort((a, b) => a.total - b.total || a.order - b.order);
  }

  // Feature-Erkennung statt Versionsnummer: ältere Server (anthrax, onion < 2.2)
  // senden summePlatzierungen nicht — dann wäre die Platz-Tabelle nur Nullen
  function supported(data) {
    const players = (data && data.spielerReihenfolge) || [];
    if (!placesMode(data) || players.length === 0) {
      return true;
    }
    return players.some((spieler) => spieler.statistik && 'summePlatzierungen' in spieler.statistik);
  }

  function formatEuro(euro) {
    return euro.toFixed(2).replace('.', ',') + ' €';
  }

  function escapeHtml(text) {
    return String(text).replace(/[&<>"]/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }

  function render(container, data) {
    if (!supported(data)) {
      container.innerHTML = '<p class="hint">Platzierungs-Wertung erfordert neueren Server</p>';
      return;
    }
    const list = rows(data);
    const max = Math.max(1, ...list.map((row) => row.total));
    const currentId = data.currentPlayer ? data.currentPlayer.id : null;
    container.innerHTML =
      '<table class="ranking-table"><thead><tr><th>Spieler</th><th>'
      + (placesMode(data) ? 'Σ Plätze' : 'Σ') + '</th><th></th></tr></thead><tbody>'
      + list.map((row) =>
        '<tr' + (row.id === currentId ? ' class="active-player"' : '') + '>'
        + '<td class="ranking-name">' + escapeHtml(row.name) + '</td>'
        + '<td class="ranking-sum">' + row.total
        + (row.euro === null ? '' : '<div class="ranking-euro">' + formatEuro(row.euro) + '</div>')
        + '</td><td class="ranking-bar">'
        + '<span class="bar-booked" style="width:' + (100 * row.booked / max) + '%"></span>'
        + '<span class="bar-projected" style="width:' + (100 * row.projected / max) + '%"></span>'
        + '</td></tr>').join('')
      + '</tbody></table>';
  }

  return { placesMode, rows, supported, formatEuro, render };
})();

if (typeof module !== 'undefined') {
  module.exports = Ranking;
}
```

- [ ] **Step 4: Run tests** — `node --test src/test/js/` → all pass.

- [ ] **Step 5: Wire up** — `index.html`: add `<script src="js/ranking.js"></script>` after `heatmap.js`. In `renderDisplays` append:

```js
    if (options.ranking) {
      Ranking.render($('rankingPanel'), data);
    }
```

`tv.css` append:

```css
.ranking-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.75em;
}

.ranking-table th {
  color: var(--muted);
  text-align: left;
  font-weight: normal;
}

.ranking-table td {
  padding: 0.6vh 0.6vw;
  vertical-align: middle;
}

.ranking-table tr.active-player .ranking-name {
  color: var(--accent);
  font-weight: bold;
}

.ranking-sum {
  text-align: right;
  white-space: nowrap;
}

.ranking-euro {
  font-size: 0.7em;
  color: var(--muted);
}

.ranking-bar {
  width: 50%;
  white-space: nowrap;
}

.ranking-bar span {
  display: inline-block;
  height: 2.2vh;
}

.bar-booked { background: var(--warn); }
.bar-projected { background: var(--warn); opacity: 0.35; }
```

- [ ] **Step 6: Manual check**:
  1. Cost 50 Cent, all house rules on, 2+ players, Blitztabelle on. Before any throw: projected 1 and 2 → light bars, Σ 1/2, € line. Throw so player 2 leads → order and bars swap live. Wall hit (Ⓨ) → booked part +1. Leave a Schnapszahl (e.g. 301 → 222) → +1 booked after the turn. Finish a game → next game: booked includes placement points, projection restarts.
  2. New tournament with cost 0 Cent, 2 games: header "Σ Plätze", no € line. Wall hit → table unchanged. After game 1 the winner has booked 1, the other 2; projection of game 2 adds on top.
  3. Optional, if an older onion-server (< 2.2.0) or anthrax is at hand: TV joined as client, cost 0 → panel shows "Platzierungs-Wertung erfordert neueren Server".

- [ ] **Step 7: Hand over for commit** — new `js/ranking.js`, `src/test/js/ranking.test.js`; changed `index.html`, `js/app.js`, `tv.css`.

---

### Task 8: Docs + layout pass

**Files:**
- Modify: `dart-server/onion-server/README.md` (section "Entwicklung" ~line 248; TV section near "Web-Interface" ~line 67)
- Modify: `tv.css` (only if the layout check fails)

- [ ] **Step 1: README "Entwicklung"** — below the `./gradlew test` line add:

```bash
node --test src/test/js/   # Logik der TV-Anzeigen (Finish-Hilfe, Trefferbild, Blitztabelle); Node ≥ 18, nicht in CI
./gradlew processResources --continuous   # parallel zu ./gradlew run: Web-UI-Änderungen ohne Neustart
```

- [ ] **Step 2: README TV section** — add one bullet where `/tv/` is described:

```markdown
- Spielmenü (☰/F) → **Anzeigen**: Finish-Hilfe (X01, 1/2/3-Dart), Trefferbild (Treffer des aktiven Spielers im Turnier), Blitztabelle (Strafpunkte inkl. Prognose des laufenden Spiels). Einstellung pro TV im Browser gespeichert.
```

- [ ] **Step 3: Layout check on 1920×1080** (Electron AppImage or browser fullscreen F11) with 4 players, all three displays on, X01 at a checkout score: nothing scrolls, tiles readable, buttons fully visible. If the screen scrolls: reduce `#displayRow` `height` to `30vh` and `#galleryViewport` padding to `1vh 0`; recheck.

- [ ] **Step 4: Full verification**

Run: `./gradlew test && node --test src/test/js/`
Expected: BUILD SUCCESSFUL and all node tests pass.

- [ ] **Step 5: Hand over for commit** — changed `README.md` (and `tv.css` if adjusted). PR into `master` by the user; PR description must mention the JSON additions (`gameMode`/`modeData` for X01, `strafpunkteProjektion`).
