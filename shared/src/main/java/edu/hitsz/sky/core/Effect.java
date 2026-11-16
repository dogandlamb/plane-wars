package edu.hitsz.sky.core;

public final class Effect extends NonEntity {
    @Override protected float lifetime() {
        if (kind == 4) return owner < 0 ? 1.15f : .48f;
        return kind == 1 ? .22f : kind == 5 ? .32f : .65f;
    }
    @Override protected boolean isLifetimeExpired() { return age >= lifetime(); }
}
