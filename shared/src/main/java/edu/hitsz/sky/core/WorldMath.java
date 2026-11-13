package edu.hitsz.sky.core;

final class WorldMath {
    private WorldMath() { }
    static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    static float distance(float x, float y, float otherX, float otherY) {
        float dx = x - otherX, dy = y - otherY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    static float approach(float value, float target, float amount) {
        return value < target ? Math.min(value + amount, target) : Math.max(value - amount, target);
    }
    static boolean crossed(float age, float step, float period) {
        return (int) (age / period) > (int) (Math.max(0, age - step) / period);
    }
}
