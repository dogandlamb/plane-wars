package edu.hitsz.sky.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public final class RoomSecurityDeadlineTest {
    public static void main(String[] arguments) throws Exception {
        try (RoomServer server = new RoomServer(0)) {
            server.start();
            slowCompleteFrameIsClosed(server.port());
            missingHelloIsClosed(server.port());
            oversizedControlIsClosed(server.port());
            try (Socket healthy = new Socket("127.0.0.1", server.port())) {
                healthy.setSoTimeout(2000);
                WireCodec.write(new DataOutputStream(healthy.getOutputStream()), Packet.text(WireCodec.HELLO, "Healthy"));
                if (WireCodec.read(new DataInputStream(healthy.getInputStream())).type != WireCodec.LOBBY) {
                    throw new AssertionError("Healthy peer could not connect after incomplete peers were closed");
                }
            }
        }
        try (RoomServer tls = new RoomServer(0, true)) {
            tls.start();
            try (Socket socket = new Socket("127.0.0.1", tls.port())) {
                socket.setSoTimeout(6500);
                long deadline = System.nanoTime() + 6_500_000_000L;
                while (socket.getInputStream().read() != -1) {
                    if (System.nanoTime() >= deadline) throw new AssertionError("TLS handshake never closed");
                }
            }
        }
        System.out.println("RoomSecurityDeadlineTest: incomplete frame, HELLO and stalled TLS handshake expire; healthy peer still connects");
    }

    private static void slowCompleteFrameIsClosed(int port) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(6500);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            WireCodec.write(out, Packet.text(WireCodec.HELLO, "Slow"));
            WireCodec.read(new DataInputStream(socket.getInputStream()));
            out.writeInt(100); out.flush();
            Thread drip = new Thread(() -> {
                try {
                    for (int i = 0; i < 40; i++) {
                        out.writeByte(0); out.flush(); Thread.sleep(200);
                    }
                } catch (IOException ignored) {
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, "slow-frame-test");
            drip.setDaemon(true); drip.start();
            expectEof(socket, "Slow progress kept an incomplete frame alive");
        }
    }

    private static void missingHelloIsClosed(int port) throws Exception {
        List<Socket> sockets = new ArrayList<>();
        try {
            for (int i = 0; i < 32; i++) {
                Socket socket = new Socket("127.0.0.1", port); socket.setSoTimeout(6500); sockets.add(socket);
            }
            for (Socket socket : sockets) expectEof(socket, "Accepted peer remained without a HELLO");
        } finally { for (Socket socket : sockets) socket.close(); }
        sockets.clear();
        try {
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (sockets.size() < 32) {
                Socket socket = new Socket("127.0.0.1", port); socket.setSoTimeout(1500);
                try {
                    WireCodec.write(new DataOutputStream(socket.getOutputStream()), Packet.text(WireCodec.HELLO, "Healthy"));
                    if (WireCodec.read(new DataInputStream(socket.getInputStream())).type != WireCodec.LOBBY) {
                        throw new AssertionError("Expected healthy lobby");
                    }
                    sockets.add(socket);
                } catch (IOException closed) {
                    socket.close();
                    if (System.nanoTime() >= deadline) throw new AssertionError("Expired HELLO connections did not release slots", closed);
                    Thread.sleep(10);
                }
            }
        } finally { for (Socket socket : sockets) socket.close(); }
    }

    private static void oversizedControlIsClosed(int port) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(2000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(4097); out.flush();
            expectEof(socket, "Oversized client control frame was accepted");
        }
    }

    private static void expectEof(Socket socket, String failure) throws Exception {
        try {
            if (socket.getInputStream().read() != -1) throw new AssertionError("Unexpected data before close");
        } catch (SocketTimeoutException timeout) {
            throw new AssertionError(failure, timeout);
        }
    }
}
