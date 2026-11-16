package edu.hitsz.sky.core;

/** Published once by the boss, in every flight mode. */
public final class BossDiedEvent extends EnemyDeath {
    BossDiedEvent(Boss boss, int creditedHeroId) { super(boss, creditedHeroId); }
}
