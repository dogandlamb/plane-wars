package edu.hitsz.sky.android;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.Mode;
import edu.hitsz.sky.core.Phase;

final class WorldPainter {
    private final NightWatchPainter nightPainter = new NightWatchPainter();
    private static final int INK = AircraftArt.INK, CREAM = AircraftArt.CREAM;
    private static final int[] ENEMY_COLORS = {
            0xffe8ac66, 0xffb49acb, 0xffef8b79, 0xff647eb2,
            0xff53b8d0, 0xff7db7a6, 0xffd9a16b, 0xff9ba6d2};
    private static final String[] PICKUP_LABELS = {"+", "Ⅱ", "✦", "B", "❄"};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final AircraftArt art = new AircraftArt();
    private final Typeface typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD);
    private String lastNotice = "", clippedNotice = "";

    WorldPainter() { paint.setTypeface(typeface); paint.setStrokeCap(Paint.Cap.ROUND); }

    void paintWorld(Canvas canvas, GameState state, SnapshotMotion.Frame motion, float fraction,
                    boolean lowEffects, float animationTime, int localPlayer) {
        if (state.night != null) {
            nightPainter.paint(canvas, state, animationTime); return;
        }
        float worldTime = motion.elapsed(fraction);
        backdrop(canvas, state.stage, worldTime, lowEffects);
        for (int i = 0; i < state.pickups.size(); i++) {
            paintPickup(canvas, state.pickups.get(i), motion.pickupX(i, fraction),
                    motion.pickupY(i, fraction), animationTime);
        }
        for (int i = 0; i < state.enemies.size(); i++) {
            paintEnemy(canvas, state.enemies.get(i), motion.enemyX(i, fraction),
                    motion.enemyY(i, fraction), worldTime);
        }
        for (int i = 0; i < state.shots.size(); i++) {
            paintShot(canvas, state.shots.get(i), motion.shotX(i, fraction), motion.shotY(i, fraction));
        }
        for (int i = 0; i < state.pilots.size(); i++) {
            paintPilot(canvas, state.pilots.get(i), motion.pilotX(i, fraction),
                    motion.pilotY(i, fraction), localPlayer, animationTime,
                    state.mode == Mode.COOP && state.phase == Phase.RUNNING);
        }
        for (GameState.Body effect : state.effects) paintEffect(canvas, effect, lowEffects);
        if (state.freezeSeconds > 0) {
            paint.setColor(0x1553b8d0); canvas.drawRect(0, 0, 480, 800, paint);
            text(canvas, "慢一点，也挺好", 240, 41, 16, INK, Paint.Align.CENTER);
        }
        if (state.notice != null && !state.notice.isEmpty()) {
            rounded(canvas, 64, 70, 416, 109, 16, 0xeef8f3e8);
            if (!lastNotice.equals(state.notice)) {
                lastNotice = state.notice;
                clippedNotice = lastNotice.length() > 22 ? lastNotice.substring(0, 22) : lastNotice;
            }
            text(canvas, clippedNotice, 240, 96, 16, INK, Paint.Align.CENTER);
        }
        bossBar(canvas, state);
    }

    private void paintEnemy(Canvas canvas, GameState.Body enemy, float x, float y, float time) {
        if (enemy.kind == 4) {
            art.boss(canvas, x, y, Math.max(48, enemy.radius),
                    enemy.hp * 2 <= enemy.maxHp, time);
        } else {
            int color = ENEMY_COLORS[Math.max(0, Math.min(enemy.kind, ENEMY_COLORS.length - 1))];
            art.plane(canvas, x, y, Math.max(14, enemy.radius * 1.25f),
                    color, true, enemy.kind, time);
            if (enemy.kind == 5 && (enemy.flags & 1) != 0) {
                paint.setStyle(Paint.Style.STROKE); paint.setColor(0xff6bcbcf); paint.setStrokeWidth(4);
                canvas.drawArc(x - 32, y - 34, x + 32, y + 34,
                        190, 160, false, paint); paint.setStyle(Paint.Style.FILL);
            }
            if (enemy.maxHp > 70 && enemy.hp < enemy.maxHp) {
                rounded(canvas, x - 22, y - enemy.radius - 14,
                        x + 22, y - enemy.radius - 10, 2, 0x5526384a);
                rounded(canvas, x - 22, y - enemy.radius - 14,
                        x - 22 + 44f * Math.max(0, enemy.hp) / enemy.maxHp,
                        y - enemy.radius - 10, 2, AircraftArt.GREEN);
            }
        }
    }

    private void paintPilot(Canvas canvas, GameState.Pilot pilot, float x, float y, int localPlayer,
                            float time, boolean rescueAvailable) {
        if (pilot.downed) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(0xffef8b79);
            canvas.drawCircle(x, y, 31, paint);
            canvas.drawArc(x - 36, y - 36, x + 36, y + 36,
                    -90, 360 * Math.min(1, pilot.reviveProgress), false, paint);
            paint.setStyle(Paint.Style.FILL);
            text(canvas, "+", x, y + 9, 31, AircraftArt.RED, Paint.Align.CENTER);
            text(canvas, rescueAvailable ? "靠近救援" : "本轮结束", x, y + 52, 13, INK, Paint.Align.CENTER);
            return;
        }
        boolean blink = pilot.invulnerable > 0 && ((int) (time * 12) & 1) == 0;
        if (!blink || pilot.invulnerable > 1) {
            art.plane(canvas, x, y, 26,
                    pilot.id == 0 ? AircraftArt.RED : AircraftArt.BLUE, false, 0, time);
        }
        if (pilot.invulnerable > 0) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(0xaa53b8d0);
            canvas.drawCircle(x, y, 35 + (float) Math.sin(time * 7) * 2, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (pilot.id == localPlayer) {
            paint.setColor(INK); canvas.drawCircle(x, y, 3, paint);
        }
        if (pilot.weaponSeconds > 0) {
            text(canvas, pilot.weapon == 2 ? "环射" : "火力+", x, y + 47,
                    13, INK, Paint.Align.CENTER);
        }
        if (pilot.id != localPlayer) {
            text(canvas, pilot.name, x, y + 48, 12, INK, Paint.Align.CENTER);
        }
    }

    private void paintShot(Canvas canvas, GameState.Body shot, float x, float y) {
        boolean hostile = shot.owner < 0;
        paint.setStyle(Paint.Style.FILL); paint.setColor(hostile ? 0xffef746c : 0xfff4bd64);
        float radius = Math.max(3, shot.radius);
        if (hostile) {
            canvas.drawCircle(x, y, radius + 1.5f, paint);
            paint.setColor(INK); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
            canvas.drawCircle(x, y, radius + 1.5f, paint);
        } else {
            rect.set(x - 3.5f, y - 9, x + 3.5f, y + 7);
            canvas.drawRoundRect(rect, 4, 4, paint);
            paint.setColor(0xfffffff6); canvas.drawCircle(x, y - 4, 2, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void paintPickup(Canvas canvas, GameState.Body pickup, float x, float y, float time) {
        int kind = Math.max(0, Math.min(4, pickup.kind));
        float bob = (float) Math.sin(time * 4 + pickup.id) * 3;
        int save = canvas.save(); canvas.translate(x, y + bob);
        paint.setColor(0x2526384a); canvas.drawOval(-16, 14, 16, 22, paint);
        rounded(canvas, -18, -18, 18, 18, 10, CREAM);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
        paint.setColor(kind == 0 ? AircraftArt.RED : kind == 4 ? AircraftArt.BLUE : INK);
        rect.set(-18, -18, 18, 18); canvas.drawRoundRect(rect, 10, 10, paint);
        paint.setStyle(Paint.Style.FILL);
        text(canvas, PICKUP_LABELS[kind], 0, 8, 23,
                kind == 0 ? AircraftArt.RED : kind == 4 ? AircraftArt.BLUE : INK, Paint.Align.CENTER);
        canvas.restoreToCount(save);
    }

    private void paintEffect(Canvas canvas, GameState.Body effect, boolean lowEffects) {
        float life = effect.kind == 3 ? .65f : effect.kind == 4 ? .45f : .55f;
        float progress = Math.max(0, Math.min(1, effect.age / life));
        if (effect.kind == 4 && effect.owner < 0) {
            paint.setColor(((int) (effect.age * 9) & 1) == 0 ? 0x66ef5b4c : 0x22ef5b4c);
            canvas.drawRect(effect.x - Math.max(20, effect.radius), 0,
                    effect.x + Math.max(20, effect.radius), 800, paint);
            text(canvas, "注意落雷", effect.x, 125, 15, INK, Paint.Align.CENTER);
        } else if (effect.kind == 4) {
            paint.setColor(0x6653b8d0);
            float radius = effect.radius * (1 - progress * .6f);
            canvas.drawRect(effect.x - radius, 0, effect.x + radius, effect.y, paint);
            paint.setColor(0xccfff8d0);
            canvas.drawRect(effect.x - radius * .3f, 0, effect.x + radius * .3f, effect.y, paint);
        } else if (effect.kind == 3) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(10 * (1 - progress) + 2);
            paint.setColor(alpha(AircraftArt.BLUE, 1 - progress));
            canvas.drawCircle(effect.x, effect.y, Math.max(8, effect.radius * progress), paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            int count = lowEffects ? 4 : 9;
            float radius = Math.max(12, effect.radius);
            for (int i = 0; i < count; i++) {
                float angle = (float) (i * Math.PI * 2 / count + effect.id);
                float distance = radius * (progress * 1.7f + .2f);
                paint.setColor(alpha(i % 2 == 0 ? 0xfff4bd64 : CREAM, 1 - progress));
                canvas.drawCircle(effect.x + (float) Math.cos(angle) * distance,
                        effect.y + (float) Math.sin(angle) * distance,
                        Math.max(1, 5 * (1 - progress)), paint);
            }
        }
    }

    private void bossBar(Canvas canvas, GameState state) {
        for (GameState.Body body : state.enemies) {
            if (body.kind != 4) continue;
            rounded(canvas, 48, 13, 432, 33, 10, 0xddf8f3e8);
            rounded(canvas, 52, 17, 52 + 376f * Math.max(0, body.hp) / Math.max(1, body.maxHp),
                    29, 6, body.hp * 2 <= body.maxHp ? AircraftArt.RED : AircraftArt.BLUE);
            text(canvas, body.hp * 2 <= body.maxHp ? "有点急了！" : "拦路的大块头", 240, 56,
                    14, INK, Paint.Align.CENTER);
            break;
        }
    }

    private void backdrop(Canvas canvas, int stage, float seconds, boolean lowEffects) {
        int background = stage == 2 ? 0xffd8eac4 : stage == 3 ? 0xffd7e2ef :
                stage == 4 ? 0xffece1ce : stage == 5 ? 0xff8cabc2 : 0xffc9e9ed;
        canvas.drawColor(background);
        float scroll = seconds * 22;
        switch (stage) {
            case 2: fields(canvas, scroll, seconds); break;
            case 3: clouds(canvas, scroll, true); break;
            case 4: factory(canvas, scroll); break;
            case 5: carrier(canvas, scroll); break;
            default: harbor(canvas, scroll); break;
        }
        if (stage != 3 && !lowEffects) clouds(canvas, scroll * .65f, false);
    }

    private void harbor(Canvas canvas, float scroll) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(0x5553b8d0);
        for (int i = 0; i < 14; i++) {
            float y = wrap(i * 68 + scroll, 900) - 50;
            for (int j = 0; j < 4; j++) canvas.drawArc(j * 138 - 30, y, j * 138 + 45,
                    y + 15, 10, 160, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) {
            float x = i % 2 == 0 ? -35 : 445, y = wrap(i * 340 + scroll * .8f, 1100) - 100;
            paint.setColor(0xfff0d9a8); canvas.drawOval(x - 88, y - 65, x + 88, y + 65, paint);
            paint.setColor(0xff93c49a); canvas.drawOval(x - 70, y - 52, x + 70, y + 52, paint);
            rounded(canvas, x - 12, y + 20, x + 12, y + 115, 5, 0xffb99b7b);
        }
    }

    private void fields(Canvas canvas, float scroll, float seconds) {
        for (int i = 0; i < 7; i++) {
            float y = wrap(i * 150 + scroll, 1050) - 150;
            rounded(canvas, 12, y, 142, y + 106, 19, i % 2 == 0 ? 0xffb4d59f : 0xffe7dca6);
            rounded(canvas, 338, y + 45, 470, y + 165, 19, i % 2 == 0 ? 0xffe7dca6 : 0xffb4d59f);
            paint.setColor(0x4486ac7f); paint.setStrokeWidth(2);
            for (int k = 0; k < 4; k++) canvas.drawLine(27, y + 21 + k * 20,
                    125, y + 21 + k * 20, paint);
        }
        for (int i = 0; i < 3; i++) {
            float x = i % 2 == 0 ? 56 : 423, y = wrap(i * 350 + scroll, 1100) - 120;
            rounded(canvas, x - 5, y, x + 5, y + 55, 4, 0xfff8f3e8);
            int save = canvas.save(); canvas.translate(x, y); canvas.rotate(seconds * 45 + i * 30);
            rounded(canvas, -34, -4, 34, 4, 4, 0xfff8f3e8);
            rounded(canvas, -4, -34, 4, 34, 4, 0xfff8f3e8);
            paint.setColor(0xffef8b79); canvas.drawCircle(0, 0, 7, paint); canvas.restoreToCount(save);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setColor(0x556a9e9e); paint.setStrokeWidth(2);
        for (int i = 0; i < 5; i++) {
            float y = wrap(i * 200 + scroll * 2, 1000) - 100;
            canvas.drawLine(150, y, 215, y - 13, paint); canvas.drawLine(261, y + 30, 310, y + 20, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void clouds(Canvas canvas, float scroll, boolean storm) {
        for (int i = 0; i < (storm ? 12 : 6); i++) {
            float x = (i * 173 % 570) - 40;
            float y = wrap(i * 157 + scroll, 1020) - 100;
            float size = storm ? 1.1f : .8f;
            paint.setColor(storm ? 0x887d97b0 : 0x99fffdf2);
            canvas.drawOval(x - 46 * size, y, x + 70 * size, y + 32 * size, paint);
            canvas.drawCircle(x - 4 * size, y + 3 * size, 24 * size, paint);
            canvas.drawCircle(x + 31 * size, y + 3 * size, 31 * size, paint);
        }
    }

    private void factory(Canvas canvas, float scroll) {
        for (int i = 0; i < 6; i++) {
            float y = wrap(i * 190 + scroll, 1140) - 200;
            rounded(canvas, -15, y, 93, y + 135, 15, 0xffbdc9bc);
            rounded(canvas, 387, y + 70, 495, y + 205, 15, 0xffb8c9cd);
            paint.setColor(0xfff4bd64);
            for (int k = 0; k < 3; k++) {
                canvas.drawCircle(26 + k * 22, y + 36, 5, paint);
                canvas.drawCircle(408 + k * 22, y + 108, 5, paint);
            }
            paint.setColor(0x33969e91); paint.setStrokeWidth(8);
            canvas.drawLine(108, y + 45, 108, y + 170, paint);
            canvas.drawLine(372, y + 45, 372, y + 170, paint);
        }
        paint.setStrokeWidth(2); paint.setColor(0x33ffffff);
        for (int i = 0; i < 14; i++) {
            float y = wrap(i * 70 + scroll, 980) - 100;
            canvas.drawLine(130, y, 350, y, paint);
        }
    }

    private void carrier(Canvas canvas, float scroll) {
        rounded(canvas, 64, -60, 416, 860, 35, 0xffd4dad6);
        rounded(canvas, 92, -60, 388, 860, 24, 0xffb7c6c7);
        paint.setColor(0xfff1e2ad);
        for (int i = 0; i < 10; i++) {
            float y = wrap(i * 110 + scroll, 1100) - 100;
            canvas.drawRect(235, y, 245, y + 48, paint);
            canvas.drawCircle(105, y + 18, 4, paint); canvas.drawCircle(375, y + 18, 4, paint);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(0x55ffffff);
        canvas.drawCircle(240, wrap(scroll * .7f, 1100) - 100, 72, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void rounded(Canvas canvas, float l, float t, float r, float b, float radius, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); rect.set(l, t, r, b);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    private void text(Canvas canvas, String value, float x, float y, float size, int color,
                      Paint.Align align) {
        paint.setStyle(Paint.Style.FILL); paint.setTypeface(typeface); paint.setColor(color);
        paint.setTextSize(size); paint.setTextAlign(align); canvas.drawText(value, x, y, paint);
    }

    private static float wrap(float value, float period) { return value % period; }
    private static int alpha(int color, float amount) {
        return (Math.max(0, Math.min(255, (int) (amount * 255))) << 24) | (color & 0x00ffffff);
    }
}
