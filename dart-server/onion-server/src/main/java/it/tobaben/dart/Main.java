package it.tobaben.dart;

import it.tobaben.dart.infrastructure.AppConfig;
import it.tobaben.dart.infrastructure.AppContext;
import it.tobaben.dart.infrastructure.AppSetup;
import it.tobaben.dart.infrastructure.ServerSetup;
import it.tobaben.dart.infrastructure.web.WebServer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import java.util.concurrent.CountDownLatch;

/**
 * Composition root. Two paths:
 * <ul>
 *   <li>--player given: the original one-shot tournament run ({@link LegacyRun}),
 *       exact same behavior as before</li>
 *   <li>otherwise: long-lived web mode — management UI on --web-port (default
 *       8420), broker/lobby/tournaments configured at runtime, any number of
 *       tournaments without restart</li>
 * </ul>
 * Ends with System.exit: native (BLE) and broker threads are non-daemon and
 * would keep the JVM alive otherwise.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Options options = ServerSetup.buildOptions();
        CommandLine cmd = new DefaultParser().parse(options, args);
        if (cmd.hasOption("help")) {
            new HelpFormatter().printHelp("onion-server", options, true);
            return;
        }

        if (cmd.hasOption("player")) {
            LegacyRun.run(args);
            System.exit(0);
        }

        AppConfig config = AppSetup.fromCommandLine(cmd);
        AppContext context = new AppContext(config);
        WebServer webServer = new WebServer(context);
        webServer.start();
        System.out.println("[MAIN] Modus " + config.mode() + " – Web-Interface: http://localhost:"
                + config.webPort() + "/");

        if (config.autoBroker()) {
            String error = context.configureBrokerFromConfig();
            if (error != null) {
                System.err.println("[MAIN] --auto-broker fehlgeschlagen: " + error);
            }
        }

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[MAIN] Fahre herunter...");
            webServer.stop();
            context.shutdown();
        }, "shutdown"));
        shutdown.await(); // runs until Ctrl+C / SIGTERM
    }
}
