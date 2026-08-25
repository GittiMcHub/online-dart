package it.tobaben.dart.application.client;

import it.tobaben.dart.application.client.port.LobbyClientPort;
import it.tobaben.dart.application.lobby.LobbyResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Client-mode state: this instance's identity in remote lobbies, the last seen
 * lobby/state and the request/response correlation for join/leave. Requests
 * are answered on lobby/response/&lt;clientId&gt;; a missing answer (server
 * down) resolves as timeout after {@link #RESPONSE_TIMEOUT_SECONDS}.
 */
public class ClientSession {

    static final int RESPONSE_TIMEOUT_SECONDS = 5;

    private final String clientId = UUID.randomUUID().toString();
    private final Map<String, CompletableFuture<LobbyResult>> pending = new ConcurrentHashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private volatile LobbyClientPort port;
    private volatile LobbyStateView remoteLobby;

    public String getClientId() {
        return this.clientId;
    }

    /** Wired (and re-wired on broker change) by the infrastructure. */
    public void setPort(LobbyClientPort port) {
        this.port = port;
    }

    public void addChangeListener(Runnable listener) {
        this.changeListeners.add(listener);
    }

    public LobbyStateView getRemoteLobby() {
        return this.remoteLobby;
    }

    public LobbyResult join(String playerName, int dartboardId) {
        return request((requestId, client) ->
                client.publishJoin(requestId, this.clientId, playerName, dartboardId));
    }

    public LobbyResult leave(String playerName) {
        return request((requestId, client) ->
                client.publishLeave(requestId, this.clientId, playerName));
    }

    private LobbyResult request(java.util.function.BiConsumer<String, LobbyClientPort> send) {
        LobbyClientPort client = this.port;
        if (client == null) {
            return LobbyResult.failure("Bitte zuerst den MQTT-Broker konfigurieren");
        }
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<LobbyResult> future = new CompletableFuture<>();
        this.pending.put(requestId, future);
        try {
            send.accept(requestId, client);
            return future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return LobbyResult.failure("Keine Antwort vom Server (Timeout)");
        } catch (ExecutionException e) {
            return LobbyResult.failure("Fehler: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LobbyResult.failure("Unterbrochen");
        } finally {
            this.pending.remove(requestId);
        }
    }

    /** Called by the infrastructure for every lobby/state message. */
    public void onLobbyState(LobbyStateView state) {
        LobbyStateView previous = this.remoteLobby;
        if (previous != null && state.seq() < previous.seq()) {
            return; // stale retained message from an older server run
        }
        this.remoteLobby = state;
        fireChange();
    }

    /** Called by the infrastructure for every lobby/response/&lt;clientId&gt; message. */
    public void onResponse(String requestId, boolean ok, String error) {
        CompletableFuture<LobbyResult> future = this.pending.remove(requestId);
        if (future != null) {
            future.complete(ok ? LobbyResult.success() : LobbyResult.failure(error));
        }
    }

    private void fireChange() {
        for (Runnable listener : this.changeListeners) {
            listener.run();
        }
    }
}
