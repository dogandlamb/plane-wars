package edu.hitsz.sky.net;

import edu.hitsz.sky.core.GameState;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Run in a separate JVM with a local test certificate containing DNS:localhost. */
public final class TlsRoomServerTest {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) throw new IllegalArgumentException("keystore path and truststore path required");
        String password = System.getenv("SKY_TEST_KEYSTORE_PASSWORD");
        if (password == null) throw new IllegalArgumentException("SKY_TEST_KEYSTORE_PASSWORD required");
        System.setProperty("javax.net.ssl.keyStore", arguments[0]);
        System.setProperty("javax.net.ssl.keyStorePassword", password);
        System.setProperty("javax.net.ssl.trustStore", arguments[1]);
        System.setProperty("javax.net.ssl.trustStorePassword", password);
        CountDownLatch connected = new CountDownLatch(1);
        NetClient.Listener listener = new NetClient.Listener() {
            public void onLobby(Lobby lobby) { connected.countDown(); }
            public void onStory(RoomOptions options) { }
            public void onFrame(GameState state, GameState rival) { }
            public void onError(String message) { }
            public void onDisconnected(String message) { }
        };
        try (RoomServer server = new RoomServer(0, true)) {
            server.start();
            try (NetClient valid = new NetClient(listener)) {
                valid.connect("localhost", server.port(), true, "TLS测试");
                if (!connected.await(4, TimeUnit.SECONDS)) throw new AssertionError("TLS lobby did not connect");
            }
            boolean mismatchRejected = false;
            try (NetClient mismatch = new NetClient(listener)) {
                mismatch.connect("127.0.0.1", server.port(), true, "错误主机名");
            } catch (IOException expected) { mismatchRejected = expected.getMessage().contains("安全连接失败"); }
            if (!mismatchRejected) throw new AssertionError("Hostname mismatch must be rejected");
        }
        System.out.println("TlsRoomServerTest: trusted TLS connection and hostname mismatch rejection passed");
    }
}
