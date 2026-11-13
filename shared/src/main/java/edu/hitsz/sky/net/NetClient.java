package edu.hitsz.sky.net;

import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.InputCommand;
import java.io.*;
import java.net.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.*;

/** One reader and one writer per connection; UI calls never perform socket I/O. */
public final class NetClient implements AutoCloseable {
    public interface Listener {
        void onLobby(Lobby lobby);
        void onStory(RoomOptions options);
        void onFrame(GameState state, GameState rival);
        void onError(String message);
        void onDisconnected(String message);
        default void onPaused(boolean paused, String reason) { }
    }

    private final Listener listener;
    private final ArrayBlockingQueue<Packet> commands = new ArrayBlockingQueue<>(32);
    private final AtomicReference<Packet> latestMove = new AtomicReference<>();
    private final AtomicBoolean disconnected = new AtomicBoolean();
    private final Object wakeWriter = new Object();
    private volatile boolean running, closed;
    private volatile String queueError;
    private volatile Socket socket;
    private DataInputStream input;
    private DataOutputStream output;

    public NetClient(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("listener");
        this.listener = listener;
    }

    /** Call from a background thread. Create a new client after closing this one. */
    public synchronized void connect(String host, int port, boolean tls, String name) throws IOException {
        if (running || closed) throw new IOException("请重新打开联机大厅");
        if (host == null || host.trim().isEmpty() || port < 1 || port > 65535) throw new IOException("请填写正确的服务器地址和端口");
        Socket raw = new Socket(); socket = raw;
        try {
            raw.connect(new InetSocketAddress(host.trim(), port), 5000);
            raw.setTcpNoDelay(true); raw.setSoTimeout(12000); raw.setKeepAlive(true);
            if (tls) {
                SSLSocket secure = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(raw, host.trim(), port, true);
                SSLParameters parameters = secure.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS"); secure.setSSLParameters(parameters);
                secure.setSoTimeout(12000); socket = secure; secure.startHandshake();
            }
            if (closed) throw new IOException("已取消连接");
            input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            running = true;
            commands.offer(Packet.text(WireCodec.HELLO, PlayerNames.normalize(name)));
            thread("Sky-client-write", this::writeLoop).start();
            thread("Sky-client-read", this::readLoop).start();
        } catch (IOException problem) {
            close(); throw new IOException(connectionMessage(problem), problem);
        }
    }

    public void create(RoomOptions options) {
        Packet packet = new Packet(WireCodec.CREATE); packet.options = options;
        if (options == null) { reportQueueError("请先选择关卡"); return; }
        enqueue(packet);
    }
    public void join(String code) {
        String value = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
        if (!value.matches("[A-Z2-9]{6}")) { reportQueueError("房间码是6位字母或数字"); return; }
        enqueue(Packet.text(WireCodec.JOIN, value));
    }
    public void start() { enqueue(new Packet(WireCodec.START)); }
    public void ready() { enqueue(new Packet(WireCodec.READY)); }
    public void retry() { enqueue(new Packet(WireCodec.RETRY)); }
    public void pause(boolean paused) {
        Packet packet = new Packet(WireCodec.PAUSE); packet.flag = paused; enqueue(packet);
    }
    public void suspend(boolean suspended) {
        Packet packet = new Packet(WireCodec.SUSPEND); packet.flag = suspended; enqueue(packet);
    }
    public void send(InputCommand input) {
        if (input == null) return;
        Packet packet = new Packet(WireCodec.INPUT); packet.input = input;
        if (input.type == InputCommand.Type.MOVE) {
            if (running) { latestMove.set(packet); wake(); }
        } else enqueue(packet);
    }

    private void enqueue(Packet packet) {
        if (!running) return;
        if (!commands.offer(packet)) { reportQueueError("操作太快了，请稍等一下"); return; }
        wake();
    }
    private void reportQueueError(String error) { queueError = error; wake(); }
    private void wake() { synchronized (wakeWriter) { wakeWriter.notifyAll(); } }

    private void writeLoop() {
        long lastPing = 0;
        try {
            while (running) {
                String error = queueError;
                if (error != null) { queueError = null; listener.onError(error); }
                Packet command = commands.poll();
                if (command != null) WireCodec.write(output, command);
                Packet move = latestMove.getAndSet(null);
                if (move != null) WireCodec.write(output, move);
                long now = System.nanoTime();
                if (now - lastPing >= 2_000_000_000L) {
                    WireCodec.write(output, new Packet(WireCodec.PING)); lastPing = now;
                }
                synchronized (wakeWriter) {
                    if (running && commands.isEmpty() && latestMove.get() == null && queueError == null) {
                        long remaining = 2_000_000_000L - (System.nanoTime() - lastPing);
                        if (remaining > 0) wakeWriter.wait(Math.max(1L, (remaining + 999_999L) / 1_000_000L));
                    }
                }
            }
        } catch (IOException error) { fail(connectionMessage(error)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); close(); }
        catch (RuntimeException problem) { fail("联机回调出错，请重新进房"); }
    }

    private void readLoop() {
        try {
            while (running) {
                Packet packet = WireCodec.read(input);
                switch (packet.type) {
                    case WireCodec.LOBBY: listener.onLobby(packet.lobby); break;
                    case WireCodec.STORY: latestMove.set(null); listener.onStory(packet.options); break;
                    case WireCodec.FRAME: listener.onFrame(packet.state, packet.rival); break;
                    case WireCodec.ERROR: listener.onError(packet.text); break;
                    case WireCodec.PAUSED: listener.onPaused(packet.flag, packet.text); break;
                    case WireCodec.END: fail(packet.text); return;
                    case WireCodec.PONG: break;
                    default: throw new IOException("服务器发来了未知消息");
                }
            }
        } catch (IOException problem) { fail(connectionMessage(problem)); }
        catch (RuntimeException problem) { fail("联机回调出错，请重新进房"); }
    }

    private void fail(String reason) {
        if (closed || !disconnected.compareAndSet(false, true)) return;
        close(); listener.onDisconnected(reason);
    }
    @Override public void close() {
        closed = true; running = false; latestMove.set(null); commands.clear();
        Socket current = socket;
        if (current != null) try { current.close(); } catch (IOException ignored) { }
        wake();
    }
    private static Thread thread(String name, Runnable action) {
        Thread thread = new Thread(action, name); thread.setDaemon(true); return thread;
    }
    private static String connectionMessage(IOException problem) {
        if (problem instanceof SocketTimeoutException) return "连接超时，请检查网络和服务器地址";
        if (problem instanceof SSLException) return "安全连接失败，请检查服务器证书和域名";
        if (problem instanceof ConnectException) return "没连上服务器，请确认房主已开房、端口可访问";
        if (problem instanceof UnknownHostException) return "找不到服务器，请检查地址";
        if (problem instanceof EOFException || problem instanceof SocketException) return "连接断开了，回大厅再约一局吧";
        String message = problem.getMessage();
        return message == null || message.isEmpty() ? "连接中断，请重新进入房间" : message;
    }
}
