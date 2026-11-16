package edu.hitsz.sky.core;

/** Live enemy behavior; snapshots still expose plain Body values. */
public class Enemy extends Aircraft {
    private final EnemyManager manager;
    private boolean dead;
    private float phaseProtectedUntil;
    private CombatContext combat;

    Enemy(EnemyManager manager) { super(CollisionType.ENEMY); this.manager = manager; }
    void bindCombat(CombatContext combat) { this.combat = combat; }
    @Override public boolean isCollidable() { return active && !dead; }
    @Override public void onCollision(CollisionInfo other) {
        if (other.otherType == CollisionType.HERO) {
            if (kind != 4) die(other.creditedHeroId);
        } else if (other.otherType == CollisionType.BULLET) {
            if ((flags & 1) == 0 || Math.abs(other.vx) > combat.config.value("shield.sideSpeed"))
                receiveDamage(combat.config.damage(other.configurationTag), other.creditedHeroId);
            else combat.effect(1, -1, x, y, 12);
        }
    }
    void receiveDamage(int amount, int creditedHeroId) {
        if (!isCollidable()) return;
        hp -= amount;
        if (kind == 4 && hp <= maxHp / 2 && (flags & 2) == 0) {
            flags |= 2; combat.notice("它换了个飞法！留心下一轮。");
            combat.effect(3, -1, x, y, 90);
            if (!combat.endless()) phaseProtectedUntil = combat.elapsed() + combat.config.value("boss.phaseProtection");
        }
        if (kind == 4 && !combat.endless() && combat.elapsed() < phaseProtectedUntil) hp = Math.max(1, hp);
        if (hp <= 0) die(creditedHeroId);
    }
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
