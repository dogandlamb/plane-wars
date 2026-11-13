package edu.hitsz.sky.core;

public enum Difficulty {
    EASY("轻松", 0.78f, 0.72f, 0.82f),
    NORMAL("标准", 1f, 1f, 1f),
    HARD("挑战", 1.3f, 1.25f, 1.2f);

    public final String label;
    public final float healthScale, damageScale, paceScale;
    Difficulty(String label, float healthScale, float damageScale, float paceScale) {
        this.label = label; this.healthScale = healthScale;
        this.damageScale = damageScale; this.paceScale = paceScale;
    }
}
