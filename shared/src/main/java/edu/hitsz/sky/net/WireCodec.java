package edu.hitsz.sky.net;

import edu.hitsz.sky.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class WireCodec {
    static final int VERSION = 3, MAX_FRAME = 1024 * 1024;
    static final int HELLO = 1, CREATE = 2, JOIN = 3, START = 4, READY = 5,
            INPUT = 6, PING = 7, PAUSE = 8, SUSPEND = 9, RETRY = 10, LOBBY = 20, STORY = 21, FRAME = 22,
            ERROR = 23, END = 24, PONG = 25, PAUSED = 26;

    static void write(DataOutputStream target, Packet packet) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(packet.type == FRAME ? 8192 : 128);
        DataOutputStream out = new DataOutputStream(buffer);
        out.writeInt(VERSION); out.writeByte(packet.type);
        switch (packet.type) {
            case HELLO: case JOIN: case ERROR: case END: text(out, packet.text); break;
            case CREATE: case STORY: options(out, packet.options); break;
            case LOBBY:
                Lobby lobby = packet.lobby;
                text(out, lobby.code); out.writeBoolean(lobby.options != null);
                if (lobby.options != null) options(out, lobby.options);
                out.writeInt(lobby.yourId); out.writeBoolean(lobby.host);
                out.writeInt(lobby.players.size());
                for (String name : lobby.players) text(out, name);
                break;
            case INPUT:
                InputCommand input = packet.input;
                out.writeInt(input.playerId); out.writeByte(input.type.ordinal());
                finite(out, input.x); finite(out, input.y); out.writeInt(input.value);
                break;
            case FRAME:
                state(out, packet.state); out.writeBoolean(packet.rival != null);
                if (packet.rival != null) state(out, packet.rival);
                break;
            case PAUSE: case SUSPEND: out.writeBoolean(packet.flag); break;
            case PAUSED: out.writeBoolean(packet.flag); text(out, packet.text); break;
            case START: case READY: case RETRY: case PING: case PONG: break;
            default: throw protocol("unknown-message", "未知消息");
        }
        out.flush();
        if (buffer.size() > MAX_FRAME) throw protocol("frame-size", "消息过大");
        target.writeInt(buffer.size()); buffer.writeTo(target); target.flush();
    }

    static Packet read(DataInputStream source) throws IOException {
        return read(source, MAX_FRAME);
    }

    static Packet read(DataInputStream source, int maximumFrame) throws IOException {
        int size = source.readInt();
        if (size < 5 || size > maximumFrame) throw protocol("frame-size", "消息长度不正确");
        byte[] bytes = new byte[size]; source.readFully(bytes);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != VERSION) throw protocol("version", "游戏版本不同，请安装相同版本");
        Packet packet = new Packet(in.readUnsignedByte());
        switch (packet.type) {
            case HELLO: case JOIN: case ERROR: case END: packet.text = text(in); break;
            case CREATE: case STORY: packet.options = options(in); break;
            case LOBBY:
                Lobby lobby = new Lobby(); lobby.code = text(in);
                if (in.readBoolean()) lobby.options = options(in);
                lobby.yourId = in.readInt(); lobby.host = in.readBoolean();
                int players = count(in, 2);
                for (int i = 0; i < players; i++) lobby.players.add(text(in));
                packet.lobby = lobby;
                break;
            case INPUT:
                int player = in.readInt();
                InputCommand.Type type = value(InputCommand.Type.values(), in.readUnsignedByte());
                float x = finite(in), y = finite(in); int action = in.readInt();
                if (type == InputCommand.Type.MOVE && (x < 0 || x > GameState.WIDTH || y < 0 || y > GameState.HEIGHT)) {
                    throw protocol("move-bounds", "移动坐标超出战场");
                }
                if (type == InputCommand.Type.SKILL && (action < 0 || action > 3)) throw protocol("skill-bounds", "技能编号不正确");
                if (type == InputCommand.Type.CAMERA && (action < 0 || action > 2)) throw protocol("camera-bounds", "监控编号不正确");
                if ((type == InputCommand.Type.LEFT_DOOR || type == InputCommand.Type.RIGHT_DOOR
                        || type == InputCommand.Type.LEFT_LIGHT || type == InputCommand.Type.RIGHT_LIGHT || type == InputCommand.Type.TABLET)
                        && action != 0 && action != 1) throw protocol("switch-value", "开关状态不正确");
                packet.input = new InputCommand(player, type, x, y, action);
                break;
            case FRAME:
                packet.state = state(in); if (in.readBoolean()) packet.rival = state(in); break;
            case PAUSE: case SUSPEND: packet.flag = in.readBoolean(); break;
            case PAUSED: packet.flag = in.readBoolean(); packet.text = text(in); break;
            case START: case READY: case RETRY: case PING: case PONG: break;
            default: throw protocol("unknown-message", "未知消息");
        }
        if (in.available() != 0) throw protocol("trailing-data", "消息包含多余数据");
        return packet;
    }

    private static void options(DataOutputStream out, RoomOptions options) throws IOException {
        out.writeInt(options.stage); out.writeByte(options.difficulty.ordinal());
        out.writeByte(options.mode.ordinal()); out.writeBoolean(options.developer); out.writeLong(options.seed);
    }
    private static RoomOptions options(DataInputStream in) throws IOException {
        int stage = in.readInt(); Difficulty difficulty = value(Difficulty.values(), in.readUnsignedByte());
        Mode mode = value(Mode.values(), in.readUnsignedByte()); boolean developer = in.readBoolean(); long seed = in.readLong();
        try { return new RoomOptions(stage, difficulty, mode, developer, seed); }
        catch (IllegalArgumentException invalid) { throw protocol("room-options", invalid.getMessage()); }
    }
    private static void state(DataOutputStream out, GameState state) throws IOException {
        out.writeLong(state.tick); out.writeInt(state.stage); out.writeInt(state.score);
        finite(out, state.elapsed); finite(out, state.freezeSeconds);
        out.writeByte(state.difficulty.ordinal()); out.writeByte(state.mode.ordinal());
        out.writeBoolean(state.developer); out.writeByte(state.phase.ordinal()); text(out, state.notice);
        out.writeInt(state.pilots.size());
        for (GameState.Pilot pilot : state.pilots) {
            out.writeInt(pilot.id); out.writeInt(pilot.hp); out.writeInt(pilot.maxHp); out.writeInt(pilot.score);
            out.writeInt(pilot.weapon); out.writeInt(pilot.bombs); text(out, pilot.name);
            finite(out, pilot.x); finite(out, pilot.y); finite(out, pilot.invulnerable);
            finite(out, pilot.weaponSeconds); finite(out, pilot.reviveProgress); finite(out, pilot.charge);
            out.writeBoolean(pilot.downed); for (float cooldown : pilot.cooldowns) finite(out, cooldown);
        }
        bodies(out, state.enemies); bodies(out, state.shots); bodies(out, state.pickups); bodies(out, state.effects);
        out.writeBoolean(state.night != null);
        if (state.night != null) {
            GameState.Night night = state.night;
            finite(out, night.energy); finite(out, night.elapsed); out.writeInt(night.camera); out.writeInt(night.hour);
            out.writeBoolean(night.leftDoor); out.writeBoolean(night.rightDoor);
            out.writeBoolean(night.leftLight); out.writeBoolean(night.rightLight);
            out.writeBoolean(night.tabletOpen);
            for (float intruder : night.intruders) finite(out, intruder);
            for (int route : night.routes) out.writeInt(route);
        }
    }
    private static GameState state(DataInputStream in) throws IOException {
        GameState state = new GameState();
        state.tick = in.readLong(); state.stage = in.readInt(); state.score = in.readInt();
        state.elapsed = finite(in); state.freezeSeconds = finite(in);
        state.difficulty = value(Difficulty.values(), in.readUnsignedByte());
        state.mode = value(Mode.values(), in.readUnsignedByte()); state.developer = in.readBoolean();
        state.phase = value(Phase.values(), in.readUnsignedByte()); state.notice = text(in);
        int pilots = count(in, 2);
        for (int i = 0; i < pilots; i++) {
            GameState.Pilot pilot = new GameState.Pilot();
            pilot.id = in.readInt(); pilot.hp = in.readInt(); pilot.maxHp = in.readInt(); pilot.score = in.readInt();
            pilot.weapon = in.readInt(); pilot.bombs = in.readInt(); pilot.name = text(in);
            pilot.x = finite(in); pilot.y = finite(in); pilot.invulnerable = finite(in);
            pilot.weaponSeconds = finite(in); pilot.reviveProgress = finite(in); pilot.charge = finite(in);
            pilot.downed = in.readBoolean(); for (int j = 0; j < pilot.cooldowns.length; j++) pilot.cooldowns[j] = finite(in);
            state.pilots.add(pilot);
        }
        bodies(in, state.enemies); bodies(in, state.shots); bodies(in, state.pickups); bodies(in, state.effects);
        if (in.readBoolean()) {
            GameState.Night night = new GameState.Night();
            night.energy = finite(in); night.elapsed = finite(in); night.camera = in.readInt(); night.hour = in.readInt();
            night.leftDoor = in.readBoolean(); night.rightDoor = in.readBoolean();
            night.leftLight = in.readBoolean(); night.rightLight = in.readBoolean();
            night.tabletOpen = in.readBoolean();
            for (int i = 0; i < night.intruders.length; i++) night.intruders[i] = finite(in);
            for (int i = 0; i < night.routes.length; i++) night.routes[i] = in.readInt();
            state.night = night;
        }
        return state;
    }
    private static void bodies(DataOutputStream out, List<GameState.Body> bodies) throws IOException {
        out.writeInt(bodies.size());
        for (GameState.Body body : bodies) {
            out.writeInt(body.id); out.writeInt(body.kind); out.writeInt(body.owner);
            out.writeInt(body.hp); out.writeInt(body.maxHp); out.writeInt(body.flags);
            finite(out, body.x); finite(out, body.y); finite(out, body.vx); finite(out, body.vy);
            finite(out, body.radius); finite(out, body.age); finite(out, body.angle);
        }
    }
    private static void bodies(DataInputStream in, List<GameState.Body> bodies) throws IOException {
        int count = count(in, 4096);
        for (int i = 0; i < count; i++) {
            GameState.Body body = new GameState.Body();
            body.id = in.readInt(); body.kind = in.readInt(); body.owner = in.readInt();
            body.hp = in.readInt(); body.maxHp = in.readInt(); body.flags = in.readInt();
            body.x = finite(in); body.y = finite(in); body.vx = finite(in); body.vy = finite(in);
            body.radius = finite(in); body.age = finite(in); body.angle = finite(in); bodies.add(body);
        }
    }
    private static void text(DataOutputStream out, String text) throws IOException {
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1024) throw protocol("text-size", "文字太长");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String text(DataInputStream in) throws IOException {
        int size = count(in, 1024); byte[] bytes = new byte[size]; in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static int count(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt(); if (count < 0 || count > maximum) throw protocol("field-count", "消息字段长度不正确"); return count;
    }
    private static void finite(DataOutputStream out, float value) throws IOException {
        if (!Float.isFinite(value)) throw protocol("non-finite", "坐标或计时不正确"); out.writeFloat(value);
    }
    private static float finite(DataInputStream in) throws IOException {
        float value = in.readFloat(); if (!Float.isFinite(value)) throw protocol("non-finite", "坐标或计时不正确"); return value;
    }
    private static <T> T value(T[] values, int index) throws IOException {
        if (index < 0 || index >= values.length) throw protocol("enum-value", "消息字段不正确"); return values[index];
    }
    static ProtocolFailure protocol(String reason, String message) { return new ProtocolFailure(reason, message); }
    static final class ProtocolFailure extends IOException {
        final String reason;
        ProtocolFailure(String reason, String message) { super(message); this.reason = reason; }
    }
}
