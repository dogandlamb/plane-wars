package edu.hitsz.sky.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import edu.hitsz.sky.core.StageCatalog;

/** A short animated scene, replayed before every entry into a stage. */
public final class StoryView extends View implements Choreographer.FrameCallback {
    private static final int INK = AircraftArt.INK, CREAM = AircraftArt.CREAM, RED = AircraftArt.RED;
    private static final float BEAT_SECONDS = 4.4f;
    private static final int[] SKIES = {0xFFD7EDEB, 0xFFE3ECCC, 0xFFDDE1F2, 0xFFDBE8E7, 0xFFD8E5EF, 0xFFEDE5D2};
    private static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final String[][] SCRIPT = {
            {"豆丁|昨晚的风把航标吹散了，邮船全堵在港口。", "小夏|我去找。你替我看着那片海。", "豆丁|收到！前面的小飞机，好像也迷路了。"},
            {"豆丁|第一盏亮了！下一枚落在风车那边。", "小夏|风可不小。贴着风向走，别硬撞。", "豆丁|右边有飞机绕过来了，我帮你盯着。"},
            {"小夏|云里怎么有这么多闪光？", "豆丁|是气象无人机。先看亮光，再找空隙。", "小夏|明白。等这阵电过去，我们就穿过去。"},
            {"豆丁|工厂还在往天上送错包裹，护盾也没关。", "小夏|那就先找开关。发光的地方，是弱点吧？", "豆丁|对！注意传送带，上面又来了一批。"},
            {"小夏|航标找齐了。大块头怎么还在封锁航线？", "豆丁|它以为暴风雨还没停，得重启导航。", "小夏|那就让它看清楚。天已经晴了。"},
            {"豆丁|运输舰修好了。不过，今夜得有人看机库。", "小夏|那几个小家伙又在拆门把手？", "豆丁|看监控、开灯、关好门。到早晨就交班！"}
    };

    private final int stage;
    private final String stageLabel;
    private final Runnable finished;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final String[] speakers = new String[3];
    private final List<List<String>> lines = new ArrayList<>();
    private float elapsed;
    private long lastFrame;
    private boolean attached, completed;
    private float viewScale = 1f, offsetX, offsetY;

    public StoryView(Context context, int stage, Runnable finished) {
        super(context);
        this.stage = Math.max(1, Math.min(6, stage)); this.finished = finished;
        stageLabel = this.stage == 6 ? "番外" : "第 " + this.stage + " 关";
        setFocusable(true); setClickable(true);
        setContentDescription("关卡剧情。轻点继续，右上角跳过。");
        paint.setTypeface(REGULAR);
        paint.setTextSize(21);
        for (String beat : SCRIPT[this.stage - 1]) {
            int divider = beat.indexOf('|');
            speakers[lines.size()] = beat.substring(0, divider);
            lines.add(wrap(beat.substring(divider + 1), 374));
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true; lastFrame = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        attached = false; Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        lastFrame = 0;
        if (attached && !completed && visibility == VISIBLE) {
            Choreographer.getInstance().removeFrameCallback(this);
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!attached || completed || getWindowVisibility() != VISIBLE) return;
        if (lastFrame != 0) elapsed += Math.min(.05f, (frameTimeNanos - lastFrame) / 1_000_000_000f);
        lastFrame = frameTimeNanos;
        if (elapsed >= BEAT_SECONDS * 3) { finish(); return; }
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(CREAM);
        viewScale = Math.min(getWidth() / 480f, getHeight() / 800f);
        if (viewScale <= 0) return;
        offsetX = (getWidth() - 480 * viewScale) * .5f;
        offsetY = (getHeight() - 800 * viewScale) * .5f;
        canvas.save(); canvas.translate(offsetX, offsetY); canvas.scale(viewScale, viewScale);
        drawScene(canvas);
        paint.setColor(INK); paint.setTypeface(MEDIUM);
        paint.setTextSize(13); canvas.drawText(stageLabel, 30, 42, paint);
        paint.setTextSize(30); canvas.drawText(StageCatalog.get(stage).title, 30, 82, paint);
        paint.setColor(0xEEFFFFFF); canvas.drawRoundRect(368, 22, 454, 67, 21, 21, paint);
        paint.setColor(INK); paint.setTextSize(16); canvas.drawText("跳过  ›", 385, 51, paint);
        drawDialogue(canvas);
        for (int i = 0; i < 3; i++) {
            paint.setColor(0xFFDADFD9); canvas.drawRoundRect(184 + i * 39, 763, 211 + i * 39, 768, 3, 3, paint);
            float filled = Math.max(0, Math.min(1, elapsed / BEAT_SECONDS - i));
            if (filled > 0) {
                paint.setColor(RED); canvas.drawRoundRect(184 + i * 39, 763,
                        184 + i * 39 + 27 * filled, 768, 3, 3, paint);
            }
        }
        canvas.restore();
    }

    private void drawScene(Canvas canvas) {
        paint.setColor(SKIES[stage - 1]); canvas.drawRoundRect(16, 103, 464, 543, 32, 32, paint);
        canvas.save(); canvas.clipRect(16, 103, 464, 543);
        float t = elapsed;
        if (stage == 6) {
            drawHangar(canvas, t);
        } else {
            drawClouds(canvas, t);
            switch (stage) {
                case 1: drawHarbor(canvas, t); break;
                case 2: drawWindmills(canvas, t); break;
                case 3: drawStorm(canvas, t); break;
                case 4: drawFactory(canvas, t); break;
                case 5: drawCarrier(canvas, t); break;
                default: break;
            }
            float enter = Math.min(1f, t / 2.4f);
            float x = -75 + 310 * ease(enter) + (float)Math.sin(t * .7f) * 22;
            float y = 315 + (float)Math.sin(t * 1.4f) * 14;
            drawPlane(canvas, x, y, .92f, -12 + (float)Math.cos(t) * 4);
            drawRobot(canvas, x + 92, y + 47 + (float)Math.sin(t * 2) * 5, .52f, 0xFF79B885);
        }
        canvas.restore();
    }

    private void drawClouds(Canvas canvas, float t) {
        paint.setColor(0xEFFFFFF9);
        for (int i = 0; i < 7; i++) {
            float x = ((i * 131 - t * (12 + i % 3 * 7)) % 640 + 640) % 640 - 100;
            float y = 148 + (i % 4) * 76;
            cloud(canvas, x, y, .8f + (i % 3) * .2f);
        }
    }

    private void cloud(Canvas canvas, float x, float y, float scale) {
        canvas.drawRoundRect(x, y, x + 100 * scale, y + 25 * scale, 16, 16, paint);
        canvas.drawCircle(x + 32 * scale, y, 22 * scale, paint);
        canvas.drawCircle(x + 59 * scale, y - 5 * scale, 29 * scale, paint);
    }

    private void drawHarbor(Canvas canvas, float t) {
        paint.setColor(0xFF79C8CF); canvas.drawRect(16, 433, 464, 543, paint);
        paint.setColor(0xFFDDF2EE);
        for (int i = 0; i < 7; i++) {
            float x = ((i * 89 + t * 18) % 540) - 40;
            canvas.drawRoundRect(x, 458 + (i % 3) * 25, x + 52, 461 + (i % 3) * 25, 2, 2, paint);
        }
        paint.setColor(0xFFD6B990); canvas.drawRoundRect(280, 405, 520, 446, 8, 8, paint);
        paint.setColor(INK); canvas.drawRect(361, 277, 369, 416, paint);
        canvas.drawRect(312, 273, 449, 281, paint); canvas.drawRect(314, 281, 318, 330, paint);
        paint.setColor(RED); canvas.drawRoundRect(299, 329, 334, 357, 5, 5, paint);
        paint.setColor(0xFFE6BE61); canvas.drawRoundRect(394, 375, 431, 405, 4, 4, paint);
        paint.setColor(RED); canvas.drawRoundRect(60, 485, 141, 509, 10, 10, paint);
        paint.setColor(CREAM); canvas.drawRoundRect(80, 466, 118, 486, 4, 4, paint);
    }

    private void drawWindmills(Canvas canvas, float t) {
        paint.setColor(0xFFA8CF95); canvas.drawOval(-100, 417, 360, 650, paint);
        paint.setColor(0xFF82B38B); canvas.drawOval(220, 435, 650, 620, paint);
        for (int i = 0; i < 3; i++) {
            float x = 77 + i * 168, y = 375 + (i % 2) * 50;
            paint.setColor(CREAM); canvas.drawRoundRect(x - 9, y, x + 9, y + 115, 7, 7, paint);
            canvas.save(); canvas.translate(x, y); canvas.rotate(t * (28 + i * 5));
            for (int blade = 0; blade < 3; blade++) {
                paint.setColor(0xFFFDFBF4); canvas.drawRoundRect(-5, -67, 5, 7, 6, 6, paint); canvas.rotate(120);
            }
            paint.setColor(RED); canvas.drawCircle(0, 0, 12, paint); canvas.restore();
        }
        paint.setColor(0xFF88B0A0); paint.setStrokeWidth(3);
        canvas.drawLine(30, 233, 90, 233, paint); canvas.drawLine(368, 202, 433, 202, paint);
    }

    private void drawStorm(Canvas canvas, float t) {
        paint.setColor(0xFFABBAD4); cloud(canvas, 295, 365, 1.5f); cloud(canvas, -44, 434, 1.4f);
        paint.setColor(0xFFF3CD69); paint.setStrokeWidth(5); paint.setStyle(Paint.Style.STROKE);
        float shift = (float)Math.sin(t * 1.1f) * 4;
        path.reset(); path.moveTo(368, 376); path.lineTo(355 + shift, 408);
        path.lineTo(373, 405); path.lineTo(357 + shift, 438); canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 6; i++) {
            float y = 455 + ((t * 39 + i * 21) % 90);
            canvas.drawRoundRect(50 + i * 16, y, 53 + i * 16, y + 13, 2, 2, paint);
        }
    }

    private void drawFactory(Canvas canvas, float t) {
        paint.setColor(0xFFA5C5C3); canvas.drawRoundRect(32, 397, 449, 550, 15, 15, paint);
        paint.setColor(0xFF709897); canvas.drawRoundRect(65, 369, 125, 426, 8, 8, paint);
        canvas.drawRoundRect(335, 350, 390, 425, 8, 8, paint);
        paint.setColor(INK); canvas.drawRoundRect(15, 483, 465, 515, 15, 15, paint);
        for (int i = 0; i < 7; i++) {
            float x = ((i * 88 + t * 30) % 560) - 50;
            paint.setColor(i % 2 == 0 ? RED : 0xFFF0CC79);
            canvas.drawRoundRect(x, 452, x + 39, 482, 5, 5, paint);
        }
        paint.setColor(0xFFFFEDAD);
        for (int i = 0; i < 7; i++) canvas.drawCircle(61 + i * 59, 426, 7, paint);
    }

    private void drawCarrier(Canvas canvas, float t) {
        canvas.save(); canvas.translate(306, 391 + (float)Math.sin(t * .8f) * 5); canvas.rotate(-9);
        paint.setColor(0xFF9CB6C5); canvas.drawRoundRect(-128, -59, 165, 58, 45, 45, paint);
        paint.setColor(0xFF718F9F); canvas.drawRoundRect(-89, 20, 144, 76, 20, 20, paint);
        paint.setColor(CREAM); canvas.drawRoundRect(-106, -43, 142, 5, 23, 23, paint);
        paint.setColor(0xFF53B8D0);
        for (int i = 0; i < 6; i++) canvas.drawRoundRect(-84 + i * 37, -29, -61 + i * 37, -8, 5, 5, paint);
        paint.setColor(RED); canvas.drawRoundRect(-38, -83, 22, -55, 10, 10, paint);
        paint.setColor(0xFFF8CD72); canvas.drawCircle(-9, -72, 7 + (float)Math.sin(t * 2), paint);
        canvas.restore();
    }

    private void drawHangar(Canvas canvas, float t) {
        paint.setColor(0xFFCCDAD9); canvas.drawRoundRect(43, 145, 437, 453, 19, 19, paint);
        paint.setColor(0xFF90ADBC); canvas.drawRoundRect(64, 167, 416, 382, 10, 10, paint);
        paint.setColor(0xFFFFE5A1); canvas.drawCircle(372, 206, 23, paint);
        paint.setColor(0xFFAEC8C5); canvas.drawRect(235, 165, 243, 384, paint);
        canvas.drawRect(62, 270, 420, 278, paint);
        drawPlane(canvas, 245, 279, .6f, 0);
        drawRobot(canvas, 90 + (float)Math.sin(t * .9f) * 20, 379, .7f, 0xFF79B885);
        drawRobot(canvas, 380 + (float)Math.sin(t * .7f + 2) * 17, 396, .65f, 0xFFF0BE6D);
        paint.setColor(0xFFD5B48B); canvas.drawRoundRect(16, 450, 464, 555, 13, 13, paint);
        paint.setColor(INK); canvas.drawRoundRect(174, 389, 306, 474, 13, 13, paint);
        paint.setColor(0xFFA6D3BF); canvas.drawRoundRect(184, 399, 296, 459, 7, 7, paint);
        paint.setColor(INK); paint.setTextSize(15); canvas.drawText("CAM 01", 208, 433, paint);
        paint.setColor(RED); canvas.drawCircle(145, 485, 13, paint);
        paint.setColor(0xFF79B885); canvas.drawCircle(333, 485, 13, paint);
    }

    private void drawPlane(Canvas canvas, float x, float y, float scale, float angle) {
        canvas.save(); canvas.translate(x, y); canvas.rotate(angle); canvas.scale(scale, scale);
        paint.setColor(RED);
        path.reset(); path.moveTo(-10, -22); path.lineTo(-77, 27); path.quadTo(-78, 38, -59, 32);
        path.lineTo(0, 12); path.lineTo(59, 32); path.quadTo(78, 38, 77, 27); path.lineTo(10, -22); path.close();
        canvas.drawPath(path, paint); canvas.drawRoundRect(-29, 32, 29, 45, 7, 7, paint);
        paint.setColor(CREAM); canvas.drawRoundRect(-15, -59, 15, 51, 17, 17, paint);
        paint.setColor(INK); canvas.drawRoundRect(-10, -34, 10, -6, 10, 10, paint);
        paint.setColor(0xFF53B8D0); canvas.drawRoundRect(-5, -29, 5, -16, 5, 5, paint);
        paint.setColor(RED); canvas.drawRoundRect(-6, 24, 6, 58, 5, 5, paint);
        paint.setColor(INK); canvas.drawRoundRect(-28, -62, 28, -56, 3, 3, paint);
        canvas.restore();
    }

    private void drawRobot(Canvas canvas, float x, float y, float scale, int color) {
        canvas.save(); canvas.translate(x, y); canvas.scale(scale, scale);
        paint.setColor(color); canvas.drawRoundRect(-32, -24, 32, 29, 16, 16, paint);
        paint.setColor(CREAM); canvas.drawRoundRect(-24, -16, 24, 11, 9, 9, paint);
        paint.setColor(INK); canvas.drawCircle(-10, -3, 4, paint); canvas.drawCircle(10, -3, 4, paint);
        canvas.drawRoundRect(-13, 29, -3, 39, 4, 4, paint); canvas.drawRoundRect(3, 29, 13, 39, 4, 4, paint);
        paint.setColor(RED); canvas.drawCircle(0, -33, 5, paint);
        paint.setColor(INK); canvas.drawRect(-1.5f, -32, 1.5f, -24, paint);
        canvas.restore();
    }

    private void drawDialogue(Canvas canvas) {
        int beat = Math.min(2, (int)(elapsed / BEAT_SECONDS));
        float fade = Math.min(1f, (elapsed - beat * BEAT_SECONDS) / .32f);
        float slide = (1 - ease(fade)) * 13;
        canvas.save(); canvas.translate(0, slide);
        paint.setColor(0xFFFFFFFF); canvas.drawRoundRect(23, 566, 457, 738, 25, 25, paint);
        paint.setAlpha(Math.round(255 * fade));
        paint.setColor(speakers[beat].equals("小夏") ? RED : 0xFF528B6F);
        paint.setAlpha(Math.round(255 * fade));
        paint.setTextSize(17); paint.setTypeface(MEDIUM);
        canvas.drawText(speakers[beat], 48, 599, paint);
        paint.setColor(INK); paint.setAlpha(Math.round(255 * fade));
        paint.setTypeface(REGULAR); paint.setTextSize(21);
        float y = 633;
        for (String line : lines.get(beat)) { canvas.drawText(line, 48, y, paint); y += 29; }
        paint.setColor(0xFF8A9699); paint.setAlpha(Math.round(255 * fade)); paint.setTextSize(12);
        canvas.drawText("轻点继续  ›", 353, 719, paint); paint.setAlpha(255);
        canvas.restore();
    }

    private List<String> wrap(String text, float width) {
        List<String> wrapped = new ArrayList<>();
        while (!text.isEmpty()) {
            int count = Math.max(1, paint.breakText(text, true, width, null));
            wrapped.add(text.substring(0, count)); text = text.substring(count);
        }
        return wrapped;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float x = (event.getX() - offsetX) / viewScale;
            float y = (event.getY() - offsetY) / viewScale;
            if (x >= 355 && y < 90) finish();
            else performClick();
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        if (!completed) {
            elapsed = ((int)(elapsed / BEAT_SECONDS) + 1) * BEAT_SECONDS;
            if (elapsed >= BEAT_SECONDS * 3) finish(); else invalidate();
        }
        return true;
    }

    private void finish() {
        if (completed) return;
        completed = true; Choreographer.getInstance().removeFrameCallback(this);
        if (finished != null) finished.run();
    }

    private static float ease(float value) { return value * value * (3 - 2 * value); }
}
