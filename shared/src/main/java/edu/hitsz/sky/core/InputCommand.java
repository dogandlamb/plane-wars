package edu.hitsz.sky.core;

public final class InputCommand {
    public enum Type { MOVE, SKILL, BOMB, CAMERA, LEFT_DOOR, RIGHT_DOOR, LEFT_LIGHT, RIGHT_LIGHT, TABLET }
    public final int playerId, value;
    public final Type type;
    public final float x, y;
    public InputCommand(int playerId, Type type, float x, float y, int value) {
        this.playerId = playerId; this.type = type; this.x = x; this.y = y; this.value = value;
    }
    public static InputCommand move(int playerId, float x, float y) {
        return new InputCommand(playerId, Type.MOVE, x, y, 0);
    }
    public static InputCommand skill(int playerId, int slot) {
        return new InputCommand(playerId, Type.SKILL, 0, 0, slot);
    }
    public static InputCommand action(int playerId, Type type, int value) {
        return new InputCommand(playerId, type, 0, 0, value);
    }
}
