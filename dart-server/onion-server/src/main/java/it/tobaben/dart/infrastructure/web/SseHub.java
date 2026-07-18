package it.tobaben.dart.infrastructure.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent-Events fan-out for the management UI: every state change is
 * pushed as one "state" event carrying the full state JSON (no diffing, the
 * state is small). Dead connections are dropped on the next broadcast.
 */
public class SseHub {

    private final List<OutputStream> clients = new CopyOnWriteArrayList<>();

    /** Takes over the exchange as an SSE stream and sends the initial state. */
    public void subscribe(HttpExchange exchange, String initialStateJson) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        send(out, initialStateJson);
        this.clients.add(out);
    }

    public void broadcast(String stateJson) {
        for (OutputStream client : this.clients) {
            try {
                send(client, stateJson);
            } catch (IOException e) {
                this.clients.remove(client);
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void send(OutputStream out, String json) throws IOException {
        out.write(("event: state\ndata: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
