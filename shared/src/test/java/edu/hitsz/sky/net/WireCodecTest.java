package edu.hitsz.sky.net;

import edu.hitsz.sky.core.*;
import java.io.*;

public final class WireCodecTest {
    public static void main(String[] args) throws Exception {
        GameState state = new GameState();
        state.stage = 6; state.tick = 42; state.difficulty = Difficulty.HARD;
        state.mode = Mode.COOP; state.phase = Phase.RUNNING; state.developer = true;
        state.notice = "机库夜班"; state.elapsed = 12.5f;
        GameState.Pilot pilot = new GameState.Pilot();
        pilot.id = 1; pilot.name = "小夏"; pilot.hp = 100; pilot.maxHp = 100;
        pilot.x = 110; pilot.y = 650; pilot.cooldowns[3] = 3.5f;
        state.pilots.add(pilot);
        GameState.Body bullet = new GameState.Body();
        bullet.id = 91; bullet.owner = -1; bullet.x = 220; bullet.radius = 4;
        state.shots.add(bullet);
        state.night = new GameState.Night();
        state.night.leftDoor = true; state.night.camera = 2; state.night.intruders[1] = .75f;
        state.night.tabletOpen = true;
        Packet frame = new Packet(WireCodec.FRAME); frame.state = state;
        Packet result = roundtrip(frame);
        check(result.state.tick == 42 && result.state.stage == 6, "frame identity");
        check(result.state.pilots.get(0).name.equals("小夏"), "UTF-8 names");
        check(result.state.pilots.get(0).cooldowns[3] == 3.5f, "skill state");
        check(result.state.shots.get(0).owner == -1, "hostile ownership");
        check(result.state.night.leftDoor && result.state.night.intruders[1] == .75f, "night state");
        check(result.state.night.tabletOpen, "tablet visibility shared with both clients");
        check(result.rival == null, "co-op has no rival world");
        result.state.pilots.get(0).cooldowns[3] = 0;
        check(pilot.cooldowns[3] == 3.5f, "detached data");
        Packet create = new Packet(WireCodec.CREATE);
        create.options = new RoomOptions(5, Difficulty.EASY, Mode.VERSUS, true, 9876);
        check(roundtrip(create).options.seed == 9876, "room options");
        Packet input = new Packet(WireCodec.INPUT);
        input.input = InputCommand.move(1, 150, 710);
        check(roundtrip(input).input.y == 710, "move input");
        expectBad(new byte[] {127, -1, -1, -1}, "oversize frame");
        ByteArrayOutputStream corrupt = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(corrupt);
        out.writeInt(5); out.writeInt(999); out.writeByte(WireCodec.PING);
        expectBad(corrupt.toByteArray(), "protocol version");
        boolean rejected = false;
        input.input = InputCommand.move(0, Float.NaN, 100);
        try { roundtrip(input); } catch (IOException expected) { rejected = true; }
        check(rejected, "non-finite input rejected");
        input.input = InputCommand.action(0, InputCommand.Type.TABLET, 1);
        check(roundtrip(input).input.type == InputCommand.Type.TABLET, "tablet input roundtrip");
        input.input = InputCommand.action(0, InputCommand.Type.TABLET, 2);
        rejected = false;
        try { roundtrip(input); } catch (IOException expected) { rejected = true; }
        check(rejected, "invalid tablet switch rejected");
        corrupt.reset(); out.writeInt(5); out.writeInt(2); out.writeByte(WireCodec.PING);
        expectBad(corrupt.toByteArray(), "old clients cannot silently misread tablet state");
        System.out.println("WireCodecTest: 16 checks passed");
    }
    private static Packet roundtrip(Packet packet) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        WireCodec.write(new DataOutputStream(bytes), packet);
        return WireCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
    private static void expectBad(byte[] bytes, String label) throws Exception {
        try { WireCodec.read(new DataInputStream(new ByteArrayInputStream(bytes))); }
        catch (IOException expected) { return; }
        throw new AssertionError(label);
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
