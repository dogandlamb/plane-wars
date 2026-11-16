package edu.hitsz.sky.core;

/** Live hero; legacy Pilot remains the detached rendering/wire shape. */
public final class Hero extends GameState.Pilot {
    private final BattleConfig config;
    private CombatContext combat;

    Hero(BattleConfig config) { this.config = config; configurationTag = "hero"; }
    void bindCombat(CombatContext combat) { this.combat = combat; }
    @Override public boolean isCollidable() { return active && !downed && hp > 0; }
    @Override CollisionBody collisionBody() {
        return new CollisionBody(entityId, collisionType, configurationTag, id, x, y, vx, vy, 0,
                config.value("hero.hurtRadius"), config.value("hero.bodyRadius"), config.value("hero.pickupRadius"));
    }
    @Override public void onCollision(CollisionInfo other) {
        if (downed) return;
        switch (other.otherType) {
            case BULLET: case ENEMY: receiveDamage(config.damage(other.configurationTag)); break;
            case PICKUP: receivePickup(other.configurationTag); break;
            default: break;
        }
    }
    private void receiveDamage(int amount) {
        if (invulnerable > 0 || combat.developer()) return;
        hp = Math.max(0, hp - Math.max(1, Math.round(amount * combat.damageScale())));
        invulnerable = config.value("hero.hitInvulnerability");
        combat.effect(1, id, x, y, 18);
        if (hp == 0) {
            downed = true; invulnerable = 0;
            combat.effect(0, id, x, y, 34);
            combat.notice(combat.cooperative() ? "队友需要帮助，靠近停留就能救援。" : "飞机需要修一修。再来一次吧！");
        }
    }
    private void receivePickup(String tag) {
        combat.effect(2, id, x, y, 27);
        switch (tag) {
            case "pickup.repair": hp = Math.min(maxHp, hp + Math.round(config.value(tag + ".health"))); combat.notice("修好了，继续飞！"); break;
            case "pickup.spread": case "pickup.ring":
                weapon = tag.equals("pickup.spread") ? 1 : 2;
                weaponSeconds = config.value("pickup.weapon.duration");
                combat.notice(weapon == 1 ? "三路火力 · 5秒" : "环形火力 · 5秒"); break;
            case "pickup.bomb":
                bombs = Math.min(Math.round(config.value(tag + ".maximum")), bombs + 1);
                combat.detonate(this); combat.notice("炸弹清屏！头目和王牌更结实些。"); break;
            case "pickup.freeze":
                combat.freeze(config.value(tag + ".duration")); combat.notice("冻结 · 5秒（头目免疫，王牌减速）"); break;
            default: throw new IllegalArgumentException("Unknown pickup: " + tag);
        }
    }
}
