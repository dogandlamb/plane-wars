package edu.hitsz.sky.core;

/** Value-only death information; valid even after the entity has been removed. */
public class EnemyDeath {
    public final int enemyId, kind, creditedHeroId;
    public final float x, y, radius;

    EnemyDeath(GameState.Body enemy, int creditedHeroId) {
        enemyId = enemy.id; kind = enemy.kind; this.creditedHeroId = creditedHeroId;
        x = enemy.x; y = enemy.y; radius = enemy.radius;
    }
}
