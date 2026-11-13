package edu.hitsz.sky.net;

import edu.hitsz.sky.core.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Real sockets, real room loop, and the production battle engine; no mocks. */
public final class RoomServerTest {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1) {
            run(Integer.parseInt(arguments[0]));
        } else {
            try (RoomServer server = new RoomServer(0)) { server.start(); run(server.port()); }
        }
        System.out.println("RoomServerTest: real-socket co-op, versus, ready, pause, identity, night and disconnect passed");
    }
    private static void run(int port) throws Exception {
        cooperation(port); versus(port); nightShift(port);
    }

    private static void cooperation(int port) throws Exception {
        Probe a = new Probe(), b = new Probe(), outsider = new Probe();
        try (NetClient host = connected(port, "小夏", a);
             NetClient guest = connected(port, "阿蓝", b);
             NetClient third = connected(port, "路过", outsider)) {
            RoomOptions options = new RoomOptions(1, Difficulty.NORMAL, Mode.COOP, false, 12345);
            host.create(options);
            Lobby created = a.room();
            check(created.host && created.yourId == 0 && created.players.size() == 1, "host room");
            guest.join(created.code);
            Lobby joined = b.room();
            check(!joined.host && joined.yourId == 1 && joined.players.size() == 2, "guest room");
            third.join(created.code);
            check(take(outsider.errors, "full-room rejection").contains("满员"), "full room rejected");
            guest.start();
            check(take(b.errors, "host authority").contains("房主"), "only host starts");
            host.start(); a.story(); b.story();
            host.ready();
            check(a.frames.poll(180, TimeUnit.MILLISECONDS) == null, "one ready cannot bypass story barrier");
            guest.ready();
            GameState first = take(a.frames, "first co-op frame").state;
            check(first.stage == 1 && first.mode == Mode.COOP && first.pilots.size() == 2, "shared content");
            BattleEngine expected = new BattleEngine(1, Difficulty.NORMAL, Mode.COOP, false, 12345);
            for (long i = 0; i < first.tick; i++) expected.step(1f / 60f);
            GameState offline = expected.snapshot();
            check(first.shots.size() == offline.shots.size() && first.enemies.size() == offline.enemies.size(), "same offline simulation");
            guest.send(InputCommand.move(0, 420, 650));
            guest.send(InputCommand.skill(0, 1));
            await(() -> a.last.get() != null && a.last.get().state.pilots.get(1).x > 390, "guest movement");
            GameState moved = a.last.get().state;
            check(Math.abs(moved.pilots.get(0).x - 180) < 1, "spoofed id cannot move host");
            check(moved.pilots.get(1).cooldowns[1] > 0, "guest skill synced");
            await(() -> sharedTick(a, b) >= moved.tick, "same-tick snapshots");
            long common = sharedTick(a, b);
            check(a.byTick.get(common).pilots.get(1).x == b.byTick.get(common).pilots.get(1).x, "both clients see same world");
            host.pause(true); take(a.pauses, "host pause"); take(b.pauses, "guest pause");
            a.frames.clear(); GameState paused = take(a.frames, "paused snapshot").state;
            Thread.sleep(200);
            check(a.last.get().state.tick == paused.tick, "pause freezes simulation time");
            guest.pause(false); take(a.pauses, "resume notification");
            await(() -> a.last.get().state.tick > paused.tick, "resumed ticking");
            guest.close();
            check(take(a.disconnects, "peer disconnect").contains("离开"), "disconnect is explicit");
        }
    }

    private static void versus(int port) throws Exception {
        Probe a = new Probe(), b = new Probe();
        try (NetClient host = connected(port, "红队", a); NetClient guest = connected(port, "蓝队", b)) {
            host.create(new RoomOptions(3, Difficulty.HARD, Mode.VERSUS, true, 7788));
            guest.join(a.room().code); Lobby joined = b.room();
            check(joined.yourId == 1 && joined.options.stage == 3 && joined.options.difficulty == Difficulty.HARD, "versus options");
            host.start(); a.story(); b.story(); host.ready(); guest.ready();
            Frame first = take(a.frames, "versus frame");
            check(first.rival != null && first.state.pilots.size() == 1 && first.rival.pilots.size() == 1, "two separate worlds");
            check(first.state.tick == first.rival.tick && first.state.enemies.size() == first.rival.enemies.size(), "same seed and time");
            guest.send(InputCommand.move(0, 410, 650));
            await(() -> a.last.get().rival.pilots.get(0).x > 390, "versus guest movement");
            Frame current = a.last.get();
            check(Math.abs(current.state.pilots.get(0).x - 240) < 1, "versus input stays in own world");
            check(current.rival.developer && current.state.developer, "developer flags agree");
        }
    }

    private static void nightShift(int port) throws Exception {
        Probe a = new Probe(), b = new Probe();
        try (NetClient host = connected(port, "值班员一", a); NetClient guest = connected(port, "值班员二", b)) {
            host.create(new RoomOptions(6, Difficulty.EASY, Mode.COOP, false, 999));
            guest.join(a.room().code); b.room();
            host.start(); a.story(); b.story(); host.ready(); guest.ready();
            Frame first = take(a.frames, "night frame");
            check(first.state.stage == 6 && first.state.night != null, "night uses shared engine");
            guest.send(InputCommand.action(0, InputCommand.Type.LEFT_DOOR, 1));
            host.send(InputCommand.action(0, InputCommand.Type.CAMERA, 2));
            guest.send(InputCommand.action(1, InputCommand.Type.TABLET, 1));
            await(() -> a.last.get().state.night.leftDoor && a.last.get().state.night.camera == 2, "shared night controls");
            await(() -> a.last.get().state.night.tabletOpen && b.last.get().state.night.tabletOpen, "shared tablet visibility");
            check(!a.last.get().state.developer && a.last.get().state.night.energy < 100, "ordinary night uses finite resources");
        }
    }

    private static NetClient connected(int port, String name, Probe probe) throws Exception {
        NetClient client = new NetClient(probe); client.connect("127.0.0.1", port, false, name);
        Lobby hello = take(probe.lobbies, "connected lobby"); check(hello.code.isEmpty(), "first lobby has no room"); return client;
    }
    private static long sharedTick(Probe a, Probe b) {
        long latest = -1;
        for (Long tick : a.byTick.keySet()) if (b.byTick.containsKey(tick) && tick > latest) latest = tick;
        return latest;
    }
    private static void await(BooleanSupplier condition, String label) throws Exception {
        long deadline = System.nanoTime() + 4_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        check(condition.getAsBoolean(), label);
    }
    private static <T> T take(BlockingQueue<T> queue, String label) throws Exception {
        T value = queue.poll(4, TimeUnit.SECONDS); if (value == null) throw new AssertionError("Timeout: " + label); return value;
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    private static final class Frame {
        final GameState state, rival;
        Frame(GameState state, GameState rival) { this.state = state; this.rival = rival; }
    }
    private static final class Probe implements NetClient.Listener {
        final BlockingQueue<Lobby> lobbies = new LinkedBlockingQueue<>();
        final BlockingQueue<RoomOptions> stories = new LinkedBlockingQueue<>();
        final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>(), disconnects = new LinkedBlockingQueue<>();
        final BlockingQueue<Boolean> pauses = new LinkedBlockingQueue<>();
        final AtomicReference<Frame> last = new AtomicReference<>();
        final ConcurrentMap<Long, GameState> byTick = new ConcurrentHashMap<>();
        public void onLobby(Lobby lobby) { lobbies.add(lobby); }
        public void onStory(RoomOptions options) { stories.add(options); }
        public void onFrame(GameState state, GameState rival) {
            Frame frame = new Frame(state, rival); last.set(frame); frames.add(frame); byTick.put(state.tick, state);
        }
        public void onError(String message) { errors.add(message); }
        public void onDisconnected(String message) { disconnects.add(message); }
        public void onPaused(boolean paused, String reason) { pauses.add(paused); }
        Lobby room() throws Exception { Lobby lobby; do { lobby = take(lobbies, "room lobby"); } while (lobby.code.isEmpty()); return lobby; }
        void story() throws Exception { take(stories, "story notification"); }
    }
}
