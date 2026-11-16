package edu.hitsz.sky.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Validated immutable combat parameters, loaded by the framework before entities are created. */
public final class BattleConfig {
    private final Map<String, Float> values;

    private BattleConfig(Properties properties) {
        Map<String, Float> parsed = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            float value = Float.parseFloat(properties.getProperty(key));
            if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid combat setting: " + key);
            parsed.put(key, value);
        }
        values = Collections.unmodifiableMap(parsed);
    }
    static BattleConfig loadDefault() {
        try (InputStream stream = BattleConfig.class.getResourceAsStream("/combat.properties")) {
            if (stream == null) throw new IllegalStateException("Missing combat.properties");
            Properties properties = new Properties(); properties.load(stream);
            BattleConfig config = new BattleConfig(properties);
            for (String tag : new String[]{"hero.basic", "hero.spread", "hero.ring", "enemy.body", "boss.body"}) config.damage(tag);
            for (int damage : new int[]{9, 10, 11, 12, 13, 20, 22, 23, 24, 25, 26}) config.damage("hostile." + damage);
            for (String key : new String[]{"hero.hurtRadius", "hero.bodyRadius", "hero.pickupRadius",
                    "hero.hitInvulnerability", "boss.phaseProtection", "shield.sideSpeed",
                    "pickup.repair.health", "pickup.weapon.duration", "pickup.bomb.maximum", "pickup.freeze.duration"}) config.value(key);
            return config;
        } catch (IOException | NumberFormatException error) {
            throw new IllegalStateException("Cannot load combat configuration", error);
        }
    }
    public float value(String key) {
        Float value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown combat setting: " + key);
        return value;
    }
    public int damage(String tag) { return Math.round(value(tag + ".damage")); }
    String legacyBulletTag(int owner, int damage) {
        if (owner >= 0) {
            if (damage == damage("hero.basic")) return "hero.basic";
            if (damage == damage("hero.spread")) return "hero.spread";
            if (damage == damage("hero.ring")) return "hero.ring";
        }
        String tag = "hostile." + damage;
        damage(tag);
        return tag;
    }
}
