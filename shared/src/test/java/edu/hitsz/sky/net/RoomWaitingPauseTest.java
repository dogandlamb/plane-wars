package edu.hitsz.sky.net;

import edu.hitsz.sky.core.Difficulty;
import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.Mode;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A prepared player can pause while the other is still watching the story. */
public final class RoomWaitingPauseTest {
    public static void main(String[] arguments) throws Exception {
        checkMode(Mode.COOP);
        checkMode(Mode.VERSUS);
        System.out.println("RoomWaitingPauseTest: co-op and versus preserve pre-ready manual pause; shared resume and pre-ready cancellation passed");
    }

    private static void checkMode(Mode mode) throws Exception {
        try (RoomServer server = new RoomServer(0)) {
            server.start();
            Probe a = new Probe(), b = new Probe();
            try (NetClient host = connect(server.port(), "Host", a);
                 NetClient guest = connect(server.port(), "Guest", b)) {
                host.create(new RoomOptions(2, Difficulty.NORMAL, mode, true, 82));
                String code = take(a.lobbies).code;
                guest.join(code); take(a.lobbies); take(b.lobbies);
                host.start(); take(a.stories); take(b.stories);
                host.ready();
                host.pause(true);
                receiveBarrier(host, a, code);
                check(a.frames.poll(160, TimeUnit.MILLISECONDS) == null,
                        "one ready player must still wait for the other movie");
                guest.ready();
                GameState firstA = take(a.frames), firstB = take(b.frames);
                Thread.sleep(350);
                check(a.last.get().tick == firstA.tick && b.last.get().tick == firstB.tick
                                && a.last.get().elapsed == firstA.elapsed && b.last.get().elapsed == firstB.elapsed,
                        "Prepared player was unpaused when the other story finished: " + mode);
                check(firstA.tick == 0 && firstB.tick == 0, "paused world starts at its initial tick");
                check(Boolean.TRUE.equals(a.paused.get()) && Boolean.TRUE.equals(b.paused.get()),
                        "both clients receive the retained manual pause");
                check(!a.pauseReason.get().contains("离开"), "manual pause must not claim a player left");

                guest.pause(false);
                awaitTickAfter(a, firstA.tick); awaitTickAfter(b, firstB.tick);
                check(Boolean.FALSE.equals(a.paused.get()), "either member can clear a shared manual pause");

                host.retry(); take(a.stories); take(b.stories);
                a.frames.clear(); b.frames.clear(); a.last.set(null); b.last.set(null);
                host.ready();
                host.pause(true); host.pause(false);
                receiveBarrier(host, a, code);
                check(a.frames.poll(160, TimeUnit.MILLISECONDS) == null, "cancelled pause does not bypass the ready barrier");
                guest.ready();
                take(a.frames); take(b.frames);
                awaitTickAfter(a, 0); awaitTickAfter(b, 0);
            }
        }
    }

    private static NetClient connect(int port, String name, Probe probe) throws Exception {
        NetClient client = new NetClient(probe);
        client.connect("127.0.0.1", port, false, name); take(probe.lobbies); return client;
    }

    private static void receiveBarrier(NetClient host, Probe probe, String code) throws Exception {
        // A rejected repeat join acknowledges preceding messages on the same TCP connection.
        host.join(code);
        check(take(probe.errors).contains("已经在房间"), "receive barrier must reject only the repeated join");
    }

    private static void awaitTickAfter(Probe probe, long tick) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            GameState state = probe.last.get();
            if (state != null && state.tick > tick) return;
            Thread.sleep(10);
        }
        throw new AssertionError("World did not resume");
    }

    private static <T> T take(BlockingQueue<T> queue) throws Exception {
        T value = queue.poll(4, TimeUnit.SECONDS);
        if (value == null) throw new AssertionError("Timed out waiting for room event");
        return value;
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Probe implements NetClient.Listener {
        final BlockingQueue<Lobby> lobbies = new LinkedBlockingQueue<>();
        final BlockingQueue<RoomOptions> stories = new LinkedBlockingQueue<>();
        final BlockingQueue<GameState> frames = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        final AtomicReference<GameState> last = new AtomicReference<>();
        final AtomicReference<Boolean> paused = new AtomicReference<>();
        final AtomicReference<String> pauseReason = new AtomicReference<>("");
        public void onLobby(Lobby lobby) { lobbies.add(lobby); }
        public void onStory(RoomOptions options) { stories.add(options); }
        public void onFrame(GameState state, GameState rival) { last.set(state); frames.add(state); }
        public void onPaused(boolean value, String reason) { paused.set(value); pauseReason.set(reason); }
        public void onError(String message) { errors.add(message); }
        public void onDisconnected(String message) { errors.add(message); }
    }
}
