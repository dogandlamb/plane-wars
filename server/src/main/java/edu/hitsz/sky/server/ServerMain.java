package edu.hitsz.sky.server;

import edu.hitsz.sky.net.RoomServer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

public final class ServerMain {
    private ServerMain() { }
    public static void main(String[] arguments) {
        try {
            int port = Integer.parseInt(environment("SKY_PORT", environment("PORT", "8888")));
            boolean tls = Boolean.parseBoolean(environment("SKY_TLS", "false"));
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i].equals("--port") && i + 1 < arguments.length) port = Integer.parseInt(arguments[++i]);
                else if (arguments[i].equals("--tls")) tls = true;
                else if (arguments[i].equals("--help")) {
                    System.out.println("Usage: java -jar sky-room-server.jar [--port 8888] [--tls]");
                    System.out.println("TLS: SKY_KEYSTORE_PATH, SKY_KEYSTORE_PASSWORD, optional SKY_KEYSTORE_TYPE=PKCS12");
                    return;
                } else throw new IllegalArgumentException("Unknown or incomplete argument: " + arguments[i]);
            }
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1..65535");
            if (tls) configureTls();
            RoomServer server = new RoomServer(port, tls); server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "Sky-shutdown"));
            System.out.println("Sky room server listening on port " + server.port() + (tls ? " (TLS)" : " (LAN/plain TCP)"));
            new CountDownLatch(1).await();
        } catch (Exception problem) {
            System.err.println("Server did not start: " + problem.getMessage()); System.exit(2);
        }
    }
    private static void configureTls() {
        String path = environment("SKY_KEYSTORE_PATH", "");
        String password = environment("SKY_KEYSTORE_PASSWORD", "");
        if (path.isEmpty() || password.isEmpty() || !Files.isRegularFile(Paths.get(path))) {
            throw new IllegalArgumentException("TLS needs SKY_KEYSTORE_PATH and SKY_KEYSTORE_PASSWORD");
        }
        System.setProperty("javax.net.ssl.keyStore", path);
        System.setProperty("javax.net.ssl.keyStorePassword", password);
        System.setProperty("javax.net.ssl.keyStoreType", environment("SKY_KEYSTORE_TYPE", "PKCS12"));
    }
    private static String environment(String name, String fallback) {
        String value = System.getenv(name); return value == null || value.isEmpty() ? fallback : value;
    }
}
