package edu.hitsz.sky.net;

import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.InputCommand;

final class Packet {
    final int type;
    String text = "";
    RoomOptions options;
    Lobby lobby;
    InputCommand input;
    GameState state, rival;
    boolean flag;
    Packet(int type) { this.type = type; }
    static Packet text(int type, String text) {
        Packet packet = new Packet(type); packet.text = text; return packet;
    }
}
