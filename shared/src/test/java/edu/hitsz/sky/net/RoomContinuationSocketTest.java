package edu.hitsz.sky.net;

import edu.hitsz.sky.core.Difficulty;
import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.Mode;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the production client, codec and server through actual TCP sockets. */
public final class RoomContinuationSocketTest {
    public static void main(String[] arguments) throws Exception {
        try (RoomServer server = new RoomServer(0)) {
            server.start();
            Probe a = new Probe(), b = new Probe();
            try (NetClient host = connect(server.port(), "Host", a);
                 NetClient guest = connect(server.port(), "Guest", b)) {
                host.create(new RoomOptions(3, Difficulty.HARD, Mode.VERSUS, true, 71));
                Lobby created = room(a);
                guest.join(created.code); room(b); room(a);
                host.suspend(true);
                host.start(); take(a.stories); take(b.stories);
                host.ready(); guest.ready();
                check(take(a.pauses), "background request before READY must survive start");
                take(a.frames);
                long pausedTick = a.last.get().tick;
                guest.pause(false); take(a.pauses);
                Thread.sleep(180);
                check(a.last.get().tick == pausedTick, "guest manual resume cannot clear host background state");
                guest.suspend(true); take(a.pauses);
                host.suspend(false); take(a.pauses);
                Thread.sleep(120);
                check(a.last.get().tick == pausedTick, "all background owners must return");
                guest.suspend(false);
                check(!take(a.pauses), "last background owner resumes room");
                awaitTickAfter(a, pausedTick);
                guest.retry();
                take(b.errors);
                check(a.stories.poll(160, TimeUnit.MILLISECONDS) == null, "guest cannot restart room");
                host.pause(true); take(a.pauses);
                host.retry();
                RoomOptions next = take(a.stories); take(b.stories);
                Lobby keptA = room(a), keptB = room(b);
                check(created.code.equals(keptA.code) && created.code.equals(keptB.code), "retry retains room code");
                check(keptA.host && !keptB.host && keptA.players.size() == 2 && keptB.players.size() == 2,
                        "retry retains members and authority");
                check(next.stage == 3 && next.difficulty == Difficulty.HARD && next.mode == Mode.VERSUS && next.developer,
                        "retry retains stage and room options");
                check(!a.staleFrame && !b.staleFrame, "old round frames must precede fresh story");
                a.frames.clear(); b.frames.clear();
                host.ready();
                check(a.frames.poll(180, TimeUnit.MILLISECONDS) == null, "retry waits for both fresh story confirmations");
                a.allowFrames = true; b.allowFrames = true;
                guest.ready();
                check(take(a.frames).tick < pausedTick + 10, "retry starts a fresh engine");
                take(b.frames);
            }
        }
        System.out.println("RoomContinuationSocketTest: pre-ready background pause, ownership, resume, host retry and story barrier passed");
    }

    private static NetClient connect(int port, String name, Probe probe) throws Exception {
        NetClient client = new NetClient(probe);
        client.connect("127.0.0.1", port, false, name); take(probe.lobbies); return client;
    }
    private static Lobby room(Probe probe) throws Exception {
        Lobby lobby; do { lobby = take(probe.lobbies); } while (lobby.code.isEmpty()); return lobby;
    }
    private static void awaitTickAfter(Probe probe, long tick) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (probe.last.get().tick <= tick && System.nanoTime() < deadline) Thread.sleep(10);
        check(probe.last.get().tick > tick, "simulation resumes after return");
    }
    private static <T> T take(BlockingQueue<T> queue) throws Exception {
        T result = queue.poll(4, TimeUnit.SECONDS);
        if (result == null) throw new AssertionError("Timed out waiting for network event");
        return result;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Probe implements NetClient.Listener {
        final BlockingQueue<Lobby> lobbies = new LinkedBlockingQueue<>();
        final BlockingQueue<RoomOptions> stories = new LinkedBlockingQueue<>();
        final BlockingQueue<GameState> frames = new LinkedBlockingQueue<>();
        final BlockingQueue<Boolean> pauses = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        final AtomicReference<GameState> last = new AtomicReference<>();
        volatile boolean allowFrames = true, staleFrame;
        int storyCount;
        public void onLobby(Lobby lobby) { lobbies.add(lobby); }
        public void onStory(RoomOptions options) {
            if (++storyCount > 1) allowFrames = false;
            stories.add(options);
        }
        public void onFrame(GameState state, GameState rival) {
            if (!allowFrames) staleFrame = true;
            last.set(state); frames.add(state);
        }
        public void onPaused(boolean paused, String reason) { pauses.add(paused); }
        public void onError(String message) { errors.add(message); }
        public void onDisconnected(String message) { errors.add(message); }
    }
}
