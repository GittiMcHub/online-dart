package it.tobaben.dart.infrastructure.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import it.tobaben.dart.infrastructure.AppContext;
import it.tobaben.dart.infrastructure.MqttConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The embedded HTTP server (JDK HttpServer, no extra dependency):
 * <ul>
 *   <li>/ — management UI (classpath web/management)</li>
 *   <li>/display/ — score display for any browser in the LAN (web/display)</li>
 *   <li>/config.js — runtime broker settings for the display</li>
 *   <li>/api/ — management REST + SSE</li>
 * </ul>
 */
public class WebServer {

    private final AppContext context;
    private final SseHub sseHub = new SseHub();
    private HttpServer server;
    private ExecutorService executor;

    public WebServer(AppContext context) {
        this.context = context;
        context.addChangeListener(() -> this.sseHub.broadcast(StateJsonMapper.toJson(context)));
    }

    public void start() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(this.context.getConfig().webPort()), 0);
        // SSE holds one thread per open stream, so a bounded pool would starve
        this.executor = Executors.newCachedThreadPool();
        this.server.setExecutor(this.executor);
        this.server.createContext("/", new StaticResourceHandler("", "web/management"));
        this.server.createContext("/display", new StaticResourceHandler("/display", "web/display"));
        this.server.createContext("/config.js", this::handleConfigJs);
        this.server.createContext("/api/", new ApiHandler(this.context, this.sseHub));
        this.server.start();
    }

    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
            this.executor.shutdownNow();
        }
    }

    private void handleConfigJs(HttpExchange exchange) throws IOException {
        MqttConfig mqtt = this.context.getActiveMqttConfig() != null
                ? this.context.getActiveMqttConfig()
                : this.context.getConfig().mqtt();
        // embedded broker: the WebSocket runs on this machine, so the display
        // connects back to the host it loaded the page from
        String hostExpr = this.context.isEmbeddedActive() || !this.context.isBrokerConfigured()
                ? "location.hostname"
                : "'" + mqtt.host() + "'";
        String js = "window.DART_CONFIG = {\n"
                + "  configured: " + this.context.isBrokerConfigured() + ",\n"
                + "  wsHost: " + hostExpr + ",\n"
                + "  wsPort: " + this.context.getEmbeddedWsPort() + ",\n"
                + "  username: '" + mqtt.username() + "',\n"
                + "  password: '" + mqtt.password() + "',\n"
                + "  defaultDartboardTopic: 'dartboard/1'\n"
                + "};\n";
        byte[] body = js.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/javascript; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
