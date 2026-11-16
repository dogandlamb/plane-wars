package edu.hitsz.sky.core;

/** Live enemy behavior; snapshots still expose plain Body values. */
public class Enemy extends Aircraft {
    private final EnemyManager manager;
    private boolean dead;
    private float phaseProtectedUntil;
    private CombatContext combat;
    private AttackBehavior attackBehavior;

    Enemy(EnemyManager manager) { super(CollisionType.ENEMY); this.manager = manager; }
    void bindCombat(CombatContext combat) { this.combat = combat; }
    void bindAttackBehavior(AttackBehavior behavior) { attackBehavior = behavior; }
    protected final CombatContext combat() { return combat; }
    @Override protected float timeScale(GameObjectUpdateContext context) {
        return context.frozen && kind != 4 ? kind == 3 ? .35f : 0 : 1;
    }
    @Override protected void move(GameObjectUpdateContext context, float seconds) {
        y += vy * seconds;
        if (kind == 2 || kind == 3) x += (float) Math.sin(age * 2.2f + id) * vx * seconds;
        else if (kind == 7) x += (float) Math.cos(age * 1.3f) * vx * seconds;
        x = WorldMath.clamp(x, 24, 456);
    }
    @Override protected void afterMove(GameObjectUpdateContext context, float movementSeconds) {
        if (attackBehavior != null) attackBehavior.updateAttacks(this, movementSeconds);
    }
    @Override protected boolean isOutOfBounds() { return y > 850 || x < -95 || x > 575; }
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

    interface AttackBehavior { void updateAttacks(Enemy enemy, float seconds); }
}
