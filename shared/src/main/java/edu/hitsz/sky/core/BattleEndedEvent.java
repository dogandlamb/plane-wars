package edu.hitsz.sky.core;

import java.util.Objects;

public final class BattleEndedEvent {
    public final BattleResult result;
    public BattleEndedEvent(BattleResult result) { this.result = Objects.requireNonNull(result, "result"); }
}
