package edu.hitsz.sky.core;

/** Live enemy behavior; snapshots still expose plain Body values. */
public class Enemy extends GameState.Body {
    private final EnemyManager manager;
    private boolean dead;

    Enemy(EnemyManager manager) { this.manager = manager; }
    public final boolean isDead() { return dead; }

    final void die(int creditedHeroId) {
        if (dead) return;
        dead = true;
        notifyDeath(creditedHeroId);
    }

    protected void notifyDeath(int creditedHeroId) {
        manager.onEnemyDied(new EnemyDeath(this, creditedHeroId));
    }
}
