package edu.hitsz.sky.net;

import edu.hitsz.sky.core.*;
import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class NetworkContractTest {
    public static void main(String[] arguments) throws Exception {
        rejectIncomingNaN();
        try (RoomServer server = new RoomServer(0)) {
            server.start();
            progressedParity(server.port(), 1, Difficulty.EASY);
            progressedParity(server.port(), 2, Difficulty.NORMAL);
            progressedParity(server.port(), 4, Difficulty.HARD);
        }
        System.out.println("NetworkContractTest: incoming NaN rejected; progressed offline/server parity passed for three difficulties");
    }

    private static void rejectIncomingNaN() throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(payload);
        out.writeInt(WireCodec.VERSION); out.writeByte(WireCodec.INPUT);
        out.writeInt(0); out.writeByte(InputCommand.Type.MOVE.ordinal());
        out.writeFloat(Float.NaN); out.writeFloat(600); out.writeInt(0);
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        DataOutputStream wire = new DataOutputStream(framed);
        wire.writeInt(payload.size()); payload.writeTo(wire);
        try {
            WireCodec.read(new DataInputStream(new ByteArrayInputStream(framed.toByteArray())));
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Incoming NaN passed the decoder");
    }

    private static void progressedParity(int port, int stage, Difficulty difficulty) throws Exception {
        Probe a = new Probe(), b = new Probe();
        long seed = 820041L + stage;
        try (NetClient host = new NetClient(a); NetClient guest = new NetClient(b)) {
            host.connect("127.0.0.1", port, false, "Host");
            guest.connect("127.0.0.1", port, false, "Guest");
            host.create(new RoomOptions(stage, difficulty, Mode.COOP, false, seed));
            guest.join(a.room().code); b.room();
            host.start();
            if (!a.story.await(4, TimeUnit.SECONDS) || !b.story.await(4, TimeUnit.SECONDS)) throw new AssertionError("Story did not reach both peers");
            host.ready(); guest.ready();
            if (!a.progress.await(8, TimeUnit.SECONDS)) throw new AssertionError("No progressed frame with enemies");
            GameState remote = a.sample.get();
            BattleEngine offline = new BattleEngine(stage, difficulty, Mode.COOP, false, seed);
            for (long tick = 0; tick < remote.tick; tick++) offline.step(1f / 60f);
            GameState expected = offline.snapshot();
            check(remote.stage == stage && remote.difficulty == difficulty, "room content selection");
            check(remote.enemies.size() > 0, "parity sample must contain spawned enemies");
            check(remote.score == expected.score && remote.phase == expected.phase, "score and phase");
            check(remote.elapsed == expected.elapsed && remote.freezeSeconds == expected.freezeSeconds, "simulation time");
            bodies(remote.enemies, expected.enemies); bodies(remote.shots, expected.shots);
            bodies(remote.pickups, expected.pickups); bodies(remote.effects, expected.effects);
            for (int i = 0; i < expected.pilots.size(); i++) {
                GameState.Pilot actual = remote.pilots.get(i), pilot = expected.pilots.get(i);
                check(actual.x == pilot.x && actual.y == pilot.y && actual.hp == pilot.hp, "pilot position and health");
                check(actual.weapon == pilot.weapon && actual.bombs == pilot.bombs, "pilot equipment");
                for (int skill = 0; skill < 4; skill++) check(actual.cooldowns[skill] == pilot.cooldowns[skill], "skill clock");
            }
        }
    }

    private static void bodies(List<GameState.Body> actual, List<GameState.Body> expected) {
        check(actual.size() == expected.size(), "entity count");
        for (int i = 0; i < expected.size(); i++) {
            GameState.Body a = actual.get(i), b = expected.get(i);
            check(a.id == b.id && a.kind == b.kind && a.owner == b.owner && a.flags == b.flags, "entity identity");
            check(a.x == b.x && a.y == b.y && a.vx == b.vx && a.vy == b.vy, "entity movement");
            check(a.hp == b.hp && a.maxHp == b.maxHp && a.age == b.age, "entity health and age");
        }
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static final class Probe implements NetClient.Listener {
        final BlockingQueue<Lobby> lobbies = new LinkedBlockingQueue<>();
        final CountDownLatch story = new CountDownLatch(1), progress = new CountDownLatch(1);
        final AtomicReference<GameState> sample = new AtomicReference<>();
        public void onLobby(Lobby lobby) { lobbies.offer(lobby); }
        public void onStory(RoomOptions options) { story.countDown(); }
        public void onFrame(GameState state, GameState rival) {
            if (state.tick >= 180 && !state.enemies.isEmpty() && sample.compareAndSet(null, state)) progress.countDown();
        }
        public void onError(String message) { throw new AssertionError(message); }
        public void onDisconnected(String message) { }
        Lobby room() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            while (System.nanoTime() < deadline) {
                Lobby lobby = lobbies.poll(100, TimeUnit.MILLISECONDS);
                if (lobby != null && !lobby.code.isEmpty()) return lobby;
            }
            throw new AssertionError("Room was not created/joined");
        }
    }
}
