package edu.hitsz.sky.net;

import edu.hitsz.sky.core.*;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

/** Authoritative two-player rooms. The same simulation runs offline and here. */
public final class RoomServer implements AutoCloseable {
    private static final float STEP = 1f / 60f;
    private static final int MAX_CLIENTS = 32;
    private static final int MAX_CONTROL_FRAME = 4096;
    private static final long HELLO_TIMEOUT_NANOS = 5_000_000_000L;
    private static final long TICK_NANOS = 16_666_667L;
    private final int requestedPort;
    private final boolean tls;
    private final Object roomsLock = new Object();
    private final Set<Peer> peers = ConcurrentHashMap.newKeySet();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final AtomicLong peerIds = new AtomicLong();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(r -> daemon("Sky-room-clock", r));
    private volatile boolean running, closed;
    private volatile ServerSocket server;
    private long nextTick;

    public RoomServer(int port) { this(port, false); }
    public RoomServer(int port, boolean tls) {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("端口不正确");
        this.requestedPort = port; this.tls = tls;
    }
    public synchronized void start() throws IOException {
        if (running) return;
        if (closed) throw new IOException("服务端已经关闭");
        ServerSocketFactory factory = tls ? SSLServerSocketFactory.getDefault() : ServerSocketFactory.getDefault();
        ServerSocket listener = factory.createServerSocket();
        try {
            listener.setReuseAddress(true); listener.bind(new InetSocketAddress(requestedPort));
            server = listener; running = true;
            daemon("Sky-room-accept", this::acceptLoop).start();
            nextTick = System.nanoTime();
            clock.execute(this::runClock);
        } catch (IOException error) { listener.close(); throw error; }
    }
    public int port() { ServerSocket current = server; return current == null ? requestedPort : current.getLocalPort(); }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                if (peers.size() >= MAX_CLIENTS) { socket.close(); continue; }
                socket.setTcpNoDelay(true); socket.setSoTimeout(12000); socket.setKeepAlive(true);
                Peer peer = new Peer(socket); peers.add(peer); peer.start();
            } catch (IOException error) { if (!running) return; }
        }
    }

    private void handle(Peer peer, Packet packet) throws IOException {
        if (!peer.greeted) {
            if (packet.type != WireCodec.HELLO) throw WireCodec.protocol("expected-hello", "请先连接大厅");
            peer.name = PlayerNames.normalize(packet.text); peer.greeted = true;
            sendLobby(peer, null); return;
        }
        switch (packet.type) {
            case WireCodec.PING: peer.offer(new Packet(WireCodec.PONG)); return;
            case WireCodec.CREATE: create(peer, packet.options); return;
            case WireCodec.JOIN: join(peer, packet.text); return;
            case WireCodec.START: case WireCodec.READY: case WireCodec.INPUT: case WireCodec.PAUSE:
            case WireCodec.SUSPEND: case WireCodec.RETRY:
                Room room = peer.room;
                if (room == null) { peer.error("请先创建或加入房间"); return; }
                synchronized (room) {
                    if (packet.type == WireCodec.START) beginStory(room, peer);
                    else if (packet.type == WireCodec.READY) ready(room, peer);
                    else if (packet.type == WireCodec.PAUSE) pause(room, peer, packet.flag);
                    else if (packet.type == WireCodec.SUSPEND) suspend(room, peer, packet.flag);
                    else if (packet.type == WireCodec.RETRY) retry(room, peer);
                    else if (room.flow.running && !room.flow.finished && !room.flow.paused()) {
                        InputCommand command = packet.input;
                        int enginePlayer = room.flow.options.mode == Mode.COOP ? peer.slot : 0;
                        InputCommand bound = new InputCommand(enginePlayer, command.type, command.x, command.y, command.value);
                        if (room.flow.options.mode == Mode.COOP) room.engines[0].accept(bound);
                        else room.engines[peer.slot].accept(bound);
                    }
                }
                return;
            default: throw WireCodec.protocol("client-message", "不支持的客户端消息");
        }
    }

    private void create(Peer peer, RoomOptions options) {
        synchronized (roomsLock) {
            if (peer.room != null) { peer.error("已经在房间里了"); return; }
            if (rooms.size() >= MAX_CLIENTS / 2) { peer.error("房间暂时满了，稍后再试"); return; }
            String code;
            do { code = newCode(); } while (rooms.containsKey(code));
            Room room = new Room(code, options); room.members[0] = peer;
            peer.room = room; peer.slot = 0; rooms.put(code, room); sendLobby(peer, room);
        }
    }
    private void join(Peer peer, String code) {
        synchronized (roomsLock) {
            if (peer.room != null) { peer.error("已经在房间里了"); return; }
            Room room = rooms.get(code);
            if (room == null) { peer.error("没找到这个房间，请核对房间码"); return; }
            synchronized (room) {
                if (room.members[1] != null || room.flow.storySent) { peer.error("房间已经满员或已经出发"); return; }
                room.members[1] = peer; peer.room = room; peer.slot = 1;
                sendLobby(room.members[0], room); sendLobby(peer, room);
            }
        }
    }
    private void beginStory(Room room, Peer peer) {
        if (peer.slot != 0) { peer.error("等房主点出发"); return; }
        if (room.members[1] == null) { peer.error("还差一位飞行员"); return; }
        if (room.flow.beginStory()) room.sendStory();
    }
    private void ready(Room room, Peer peer) {
        RoomLifecycle.ReadyAction action = room.flow.ready(peer.slot);
        if (action == RoomLifecycle.ReadyAction.SHOW_STORY) { room.sendStory(); return; }
        if (action != RoomLifecycle.ReadyAction.BEGIN_BATTLE) return;
        RoomOptions options = room.flow.options;
        room.engines[0] = new BattleEngine(options.stage, options.difficulty, options.mode, options.developer, options.seed);
        if (options.mode == Mode.VERSUS) {
            room.engines[1] = new BattleEngine(options.stage, options.difficulty, options.mode, options.developer, options.seed);
        }
        if (room.flow.paused()) room.sendPaused(room.flow.hasSuspendedMember()
                ? "有飞行员暂时离开了，等他回来再出发" : "飞行已暂停，准备好后继续");
        room.sendFrames();
    }
    private void pause(Room room, Peer peer, boolean paused) {
        if (!room.flow.storySent || room.flow.finished) return;
        room.flow.pause(paused);
        room.sendPaused(room.flow.hasSuspendedMember() ? "有飞行员暂时离开了，等他回来再出发"
                : paused ? peer.name + "暂停了游戏" : "继续飞行");
        if (room.flow.running) room.sendFrames();
    }
    private void suspend(Room room, Peer peer, boolean suspended) {
        room.flow.suspend(peer.slot, suspended);
        if (!room.flow.running || room.flow.finished) return;
        room.sendPaused(room.flow.paused() ? "飞行暂歇，等大家回来再继续" : "继续飞行");
        room.sendFrames();
    }
    private void retry(Room room, Peer peer) {
        if (peer.slot != 0) { peer.error("等房主重新出发"); return; }
        if (!room.flow.running) return;
        room.flow.retry(); room.sendStory();
    }
    private void runClock() {
        if (!running) return;
        tick();
        nextTick += TICK_NANOS;
        long now = System.nanoTime();
        if (now - nextTick > TICK_NANOS * 4) nextTick = now + TICK_NANOS;
        if (running) {
            try { clock.schedule(this::runClock, Math.max(0, nextTick - now), TimeUnit.NANOSECONDS); }
            catch (RejectedExecutionException stopped) { if (running) throw stopped; }
        }
    }

    private void tick() {
        if (!running) return;
        long now = System.nanoTime();
        for (Peer peer : peers) {
            long writeStarted = peer.writeStarted;
            if (!peer.greeted && now - peer.acceptedAt > HELLO_TIMEOUT_NANOS) peer.close("hello-deadline");
            else if (writeStarted != 0 && now - writeStarted > 5_000_000_000L) peer.close("write-deadline");
        }
        for (Room room : rooms.values()) {
            synchronized (room) {
                if (!room.flow.running) continue;
                try {
                    if (!room.flow.paused() && !room.flow.finished) {
                        room.engines[0].step(STEP);
                        if (room.engines[1] != null) room.engines[1].step(STEP);
                    }
                    if (++room.clockTicks % 3 == 0) room.sendFrames();
                } catch (RuntimeException failure) {
                    room.flow.running = false;
                    System.err.println("Sky-room simulation stopped: " + failure.getClass().getSimpleName());
                    room.broadcast(Packet.text(WireCodec.END, "本局中断了，回大厅再开一局吧"));
                }
            }
        }
    }
    private void sendLobby(Peer recipient, Room room) {
        Lobby lobby = new Lobby();
        if (room != null) {
            lobby.code = room.code; lobby.options = room.flow.options;
            lobby.yourId = recipient.slot; lobby.host = recipient.slot == 0;
            for (Peer member : room.members) if (member != null) lobby.players.add(member.name);
        }
        Packet packet = new Packet(WireCodec.LOBBY); packet.lobby = lobby; recipient.offer(packet);
    }
    private void peerGone(Peer peer) {
        peers.remove(peer);
        synchronized (roomsLock) {
            Room room = peer.room; peer.room = null;
            if (room == null) return;
            synchronized (room) {
                rooms.remove(room.code); room.flow.running = false;
                for (Peer member : room.members) {
                    if (member == null || member == peer) continue;
                    member.room = null;
                    member.frame.set(null);
                    member.offer(Packet.text(WireCodec.END, peer.name + "离开了，回大厅再约一局吧"));
                }
            }
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true; running = false; clock.shutdownNow();
        ServerSocket listener = server;
        if (listener != null) try { listener.close(); } catch (IOException ignored) { }
        for (Peer peer : peers) peer.close();
        rooms.clear();
    }
    private String newCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return code.toString();
    }
    private static Thread daemon(String name, Runnable task) {
        Thread thread = new Thread(task, name); thread.setDaemon(true); return thread;
    }

    private final class Room {
        final String code;
        final RoomLifecycle flow;
        final Peer[] members = new Peer[2];
        final BattleEngine[] engines = new BattleEngine[2];
        long clockTicks;
        Room(String code, RoomOptions options) { this.code = code; this.flow = new RoomLifecycle(options); }
        void broadcast(Packet packet) { for (Peer member : members) if (member != null) member.offer(packet); }
        void sendStory() {
            Arrays.fill(engines, null); clockTicks = 0;
            for (Peer member : members) {
                if (member != null) { member.frame.set(null); sendLobby(member, this); }
            }
            Packet story = new Packet(WireCodec.STORY); story.options = flow.options; broadcast(story);
        }
        void sendPaused(String reason) {
            Packet message = Packet.text(WireCodec.PAUSED, reason); message.flag = flow.paused(); broadcast(message);
        }
        void sendFrames() {
            GameState first = engines[0].snapshot();
            if (flow.options.mode == Mode.COOP) {
                for (GameState.Pilot pilot : first.pilots) pilot.name = members[pilot.id].name;
                flow.finish(first.phase, first.phase);
                Packet packet = new Packet(WireCodec.FRAME); packet.state = first; broadcast(packet);
            } else {
                GameState second = engines[1].snapshot();
                first.pilots.get(0).name = members[0].name; second.pilots.get(0).name = members[1].name;
                flow.finish(first.phase, second.phase);
                if (!flow.finished) { waitForRival(first); waitForRival(second); }
                Packet a = new Packet(WireCodec.FRAME); a.state = first; a.rival = second; members[0].offer(a);
                Packet b = new Packet(WireCodec.FRAME); b.state = second; b.rival = first; members[1].offer(b);
            }
        }
        void waitForRival(GameState state) {
            if (state.phase != Phase.RUNNING) {
                state.phase = Phase.RUNNING; state.notice = "本轮已完成，等另一位飞行员";
            }
        }
    }

    private final class Peer {
        final Socket socket;
        final long id = peerIds.incrementAndGet();
        final long acceptedAt = System.nanoTime();
        final ArrayBlockingQueue<Packet> control = new ArrayBlockingQueue<>(32);
        final AtomicReference<Packet> frame = new AtomicReference<>();
        final AtomicBoolean active = new AtomicBoolean(true);
        final Object wake = new Object();
        volatile Room room;
        volatile int slot = -1;
        volatile long writeStarted;
        String name = "飞行员";
        volatile boolean greeted;
        Peer(Socket socket) { this.socket = socket; }
        void start() {
            daemon("Sky-peer-write", this::writeLoop).start();
            daemon("Sky-peer-read", this::readLoop).start();
        }
        void offer(Packet packet) {
            if (!active.get()) return;
            if (packet.type == WireCodec.FRAME) frame.set(packet);
            else if (!control.offer(packet)) { close("control-queue-full"); return; }
            synchronized (wake) { wake.notifyAll(); }
        }
        void error(String message) { offer(Packet.text(WireCodec.ERROR, message)); }
        void readLoop() {
            long window = System.nanoTime(); int messages = 0;
            try {
                FrameInputStream frames = new FrameInputStream(new BufferedInputStream(socket.getInputStream()), socket);
                DataInputStream in = new DataInputStream(frames);
                while (active.get()) {
                    frames.beginFrame();
                    Packet packet = WireCodec.read(in, MAX_CONTROL_FRAME);
                    long now = System.nanoTime();
                    if (now - window >= 1_000_000_000L) { window = now; messages = 0; }
                    if (++messages > 240) throw WireCodec.protocol("message-rate", "操作频率过高");
                    handle(this, packet);
                }
            } catch (IOException | RuntimeException problem) {
                close(readFailure(problem));
            } finally { close(); peerGone(this); }
        }
        void writeLoop() {
            try {
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                while (active.get()) {
                    Packet packet = control.poll();
                    if (packet == null) packet = frame.getAndSet(null);
                    if (packet == null) {
                        synchronized (wake) { if (active.get()) wake.wait(250); }
                        continue;
                    }
                    writeStarted = System.nanoTime(); WireCodec.write(out, packet); writeStarted = 0;
                }
            } catch (IOException | RuntimeException failure) { close("write-" + failure.getClass().getSimpleName()); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); close("writer-interrupted"); }
        }
        void close() { close("server-close"); }
        void close(String reason) {
            if (!active.compareAndSet(true, false)) return;
            System.err.println("Sky-room connection " + id + " closed: " + reason);
            frame.set(null); control.clear();
            try { socket.close(); } catch (IOException ignored) { }
            synchronized (wake) { wake.notifyAll(); }
        }
    }
    private static String readFailure(Exception problem) {
        if (problem instanceof WireCodec.ProtocolFailure) return "protocol-" + ((WireCodec.ProtocolFailure)problem).reason;
        if (problem instanceof SocketTimeoutException) {
            return "Frame receive deadline exceeded".equals(problem.getMessage()) ? "frame-deadline" : "read-idle-deadline";
        }
        if (problem instanceof EOFException) return "peer-eof";
        if (problem instanceof javax.net.ssl.SSLException) return "tls-" + problem.getClass().getSimpleName();
        if (problem instanceof SocketException) return "read-transport";
        return "protocol-" + problem.getClass().getSimpleName();
    }
}
