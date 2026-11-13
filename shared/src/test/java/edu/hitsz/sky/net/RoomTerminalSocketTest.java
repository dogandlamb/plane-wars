package edu.hitsz.sky.net;

import edu.hitsz.sky.core.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Deliberately takes a full stage: real engine time, sockets and result confirmations. */
public final class RoomTerminalSocketTest {
    public static void main(String[] arguments) throws Exception {
        List<Pair> pairs = new ArrayList<>();
        try (RoomServer server = new RoomServer(0)) {
            server.start();
            try {
                pairs.add(new Pair(server.port(), Mode.COOP, true));
                pairs.add(new Pair(server.port(), Mode.VERSUS, true));
                pairs.add(new Pair(server.port(), Mode.COOP, false));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(210);
                long reportAt = System.nanoTime();
                while (!pairs.stream().allMatch(Pair::terminal)) {
                    if (System.nanoTime() >= deadline) throw new AssertionError("Full stage did not reach its expected terminal state");
                    for (Pair pair : pairs) pair.play();
                    if (System.nanoTime() >= reportAt) {
                        System.out.println("RoomTerminalSocketTest: " + pairs.get(0).first.last.get().elapsed + "s of real stage time");
                        reportAt += TimeUnit.SECONDS.toNanos(30);
                    }
                    Thread.sleep(200);
                }
                for (Pair pair : pairs) pair.continueTogether();
            } finally { for (Pair pair : pairs) pair.close(); }
        }
        System.out.println("RoomTerminalSocketTest: co-op and both-versus victories advance; defeat retries; same-room result and story barriers passed");
    }

    private static final class Pair implements AutoCloseable {
        final Probe first = new Probe(), second = new Probe();
        final NetClient host = new NetClient(first), guest = new NetClient(second);
        final boolean victory;
        final Mode mode;
        final String code;
        Pair(int port, Mode mode, boolean victory) throws Exception {
            this.victory = victory; this.mode = mode;
            host.connect("127.0.0.1", port, false, "Host"); take(first.lobbies);
            guest.connect("127.0.0.1", port, false, "Guest"); take(second.lobbies);
            host.create(new RoomOptions(victory ? 1 : 5, victory ? Difficulty.EASY : Difficulty.HARD, mode, victory, 616));
            code = take(first.lobbies).code;
            guest.join(code); take(first.lobbies); take(second.lobbies);
            host.start(); take(first.lobbies); take(second.lobbies); take(first.stories); take(second.stories);
            host.ready(); guest.ready();
            awaitFrames(first); awaitFrames(second);
        }
        void play() {
            if (victory) {
                host.send(InputCommand.action(0, InputCommand.Type.BOMB, 1));
                guest.send(InputCommand.action(0, InputCommand.Type.BOMB, 1));
            } else {
                host.send(InputCommand.move(0, 240, 110)); guest.send(InputCommand.move(0, 240, 110));
            }
        }
        boolean terminal() {
            return first.last.get().phase != Phase.RUNNING && second.last.get().phase != Phase.RUNNING;
        }
        void continueTogether() throws Exception {
            Phase expected = victory ? Phase.VICTORY : Phase.DEFEAT;
            check(first.last.get().phase == expected && second.last.get().phase == expected, "expected real engine outcome");
            host.ready();
            check(first.stories.poll(200, TimeUnit.MILLISECONDS) == null, "one result confirmation cannot advance");
            guest.ready();
            RoomOptions next = take(first.stories); take(second.stories);
            check(next.stage == (victory ? 2 : 5) && next.mode == mode && next.developer == victory, "terminal progression settings");
            check(code.equals(take(first.lobbies).code) && code.equals(take(second.lobbies).code), "terminal keeps room code");
            check(!first.staleFrame && !second.staleFrame, "no terminal frame follows the new story");
            host.ready();
            Thread.sleep(180);
            check(!first.staleFrame && !second.staleFrame, "next round still waits for second movie");
            first.awaitingStory = false; second.awaitingStory = false;
            guest.ready();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            while (first.last.get().stage != next.stage || first.last.get().phase != Phase.RUNNING) {
                if (System.nanoTime() >= deadline) throw new AssertionError("New engine did not start");
                Thread.sleep(10);
            }
            check(first.last.get().tick < 30, "new round starts from a fresh clock");
        }
        public void close() { guest.close(); host.close(); }
    }
    private static <T> T take(BlockingQueue<T> queue) throws Exception {
        T value = queue.poll(4, TimeUnit.SECONDS);
        if (value == null) throw new AssertionError("Timed out waiting for room event"); return value;
    }
    private static void awaitFrames(Probe probe) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (probe.last.get() == null) {
            if (System.nanoTime() >= deadline) throw new AssertionError("No first frame"); Thread.sleep(10);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class Probe implements NetClient.Listener {
        final BlockingQueue<Lobby> lobbies = new LinkedBlockingQueue<>();
        final BlockingQueue<RoomOptions> stories = new LinkedBlockingQueue<>();
        final AtomicReference<GameState> last = new AtomicReference<>();
        volatile boolean awaitingStory, staleFrame;
        int storyCount;
        public void onLobby(Lobby lobby) { lobbies.add(lobby); }
        public void onStory(RoomOptions options) { if (++storyCount > 1) awaitingStory = true; stories.add(options); }
        public void onFrame(GameState state, GameState rival) { if (awaitingStory) staleFrame = true; last.set(state); }
        public void onError(String message) { throw new AssertionError(message); }
        public void onDisconnected(String message) { }
    }
}
