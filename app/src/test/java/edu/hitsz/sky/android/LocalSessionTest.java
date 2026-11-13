package edu.hitsz.sky.android;

import edu.hitsz.sky.core.BattleEngine;
import edu.hitsz.sky.core.Difficulty;
import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.Mode;
import java.util.concurrent.atomic.AtomicReference;

/** Executable JVM check for the real session clock, without Android or mocked time. */
public final class LocalSessionTest {
    public static void main(String[] arguments) throws Exception {
        AtomicReference<GameState> latest = new AtomicReference<>();
        LocalSession session = new LocalSession(new BattleEngine(1, Difficulty.NORMAL, Mode.SOLO, true, 42), latest::set);
        try {
            session.start();
            awaitTick(latest, 3);
            session.pause();
            Thread.sleep(80);
            long paused = latest.get().tick;
            Thread.sleep(160);
            if (latest.get().tick != paused) throw new AssertionError("Paused session advanced its game clock");
            session.resume();
            awaitTick(latest, paused + 3);
        } finally {
            session.close();
        }
        Thread.sleep(80);
        long closed = latest.get().tick;
        Thread.sleep(100);
        if (latest.get().tick != closed) throw new AssertionError("Closed session still published frames");
        System.out.println("LocalSessionTest: running, pause, resume and close passed");
    }

    private static void awaitTick(AtomicReference<GameState> latest, long tick) throws InterruptedException {
        long deadline = System.nanoTime() + 4_000_000_000L;
        while (System.nanoTime() < deadline) {
            GameState frame = latest.get();
            if (frame != null && frame.tick >= tick) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Session did not reach tick " + tick);
    }
}
