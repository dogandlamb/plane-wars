package edu.hitsz.sky.net;

import edu.hitsz.sky.core.*;
import java.io.*;

public final class RoomProtocolExtensionTest {
    public static void main(String[] arguments) throws Exception {
        for (boolean value : new boolean[]{false, true}) {
            Packet suspended = new Packet(WireCodec.SUSPEND); suspended.flag = value;
            Packet decoded = WireCodec.read(stream(suspended));
            check(decoded.type == WireCodec.SUSPEND && decoded.flag == value, "suspension round trip");
        }
        check(WireCodec.read(stream(new Packet(WireCodec.RETRY))).type == WireCodec.RETRY, "retry round trip");
        GameState state = new BattleEngine(1, Difficulty.NORMAL, Mode.COOP, true, 1).snapshot();
        for (int i = 0; i < 100; i++) state.enemies.add(new GameState.Body());
        Packet frame = new Packet(WireCodec.FRAME); frame.state = state;
        check(WireCodec.read(stream(frame)).state.enemies.size() == 100, "client still accepts snapshots larger than control cap");
        try {
            WireCodec.read(stream(frame), 4096);
            throw new AssertionError("Server accepted an oversized inbound control frame");
        } catch (WireCodec.ProtocolFailure expected) { check("frame-size".equals(expected.reason), "diagnostic reason is non-sensitive"); }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(5); out.writeInt(1); out.writeByte(WireCodec.PING);
        try {
            WireCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            throw new AssertionError("Old wire version was accepted");
        } catch (WireCodec.ProtocolFailure expected) { check("version".equals(expected.reason), "old version diagnostic"); }
        System.out.println("RoomProtocolExtensionTest: version, suspension/retry commands and separate frame limits passed");
    }
    private static DataInputStream stream(Packet packet) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); WireCodec.write(new DataOutputStream(bytes), packet);
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
