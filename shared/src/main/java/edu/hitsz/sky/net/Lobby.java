package edu.hitsz.sky.net;

import java.util.ArrayList;
import java.util.List;

public final class Lobby {
    public String code = "";
    public RoomOptions options;
    public int yourId = -1;
    public boolean host;
    public final List<String> players = new ArrayList<>();
}
