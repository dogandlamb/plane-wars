package edu.hitsz.sky.android;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class AircraftArt {
    static final int INK = 0xff26384a;
    static final int CREAM = 0xfff8f3e8;
    static final int RED = 0xffef5b4c;
    static final int BLUE = 0xff53b8d0;
    static final int GREEN = 0xff79b885;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wing = new Path();
    private final Path delta = new Path();
    private final RectF oval = new RectF();

    AircraftArt() {
        wing.moveTo(0, -14); wing.lineTo(31, 10); wing.quadTo(35, 16, 27, 18);
        wing.lineTo(0, 10); wing.lineTo(-27, 18); wing.quadTo(-35, 16, -31, 10); wing.close();
        delta.moveTo(0, -31); delta.quadTo(6, -28, 30, 22);
        delta.quadTo(0, 12, -30, 22); delta.quadTo(-6, -28, 0, -31); delta.close();
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    void plane(Canvas canvas, float x, float y, float size, int color, boolean enemy, int kind,
               float seconds) {
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.scale(size / 31f, size / 31f);
        if (enemy) canvas.rotate(180);
        if (kind == 2 || kind == 3) {
            fill(delta, canvas, color);
            paint.setColor(CREAM); paint.setStrokeWidth(4f);
            canvas.drawLine(0, -10, 0, 13, paint);
            ellipse(canvas, -5, -23, 5, -12, INK);
        } else {
            fill(wing, canvas, color);
            round(canvas, -13, 21, 13, 27, 3, color);
            round(canvas, -8, -30, 8, 27, 8, color);
            ellipse(canvas, -5, -20, 5, -6, 0xffd8f2ee);
            paint.setColor(INK); paint.setStrokeWidth(2f);
            canvas.drawLine(-4, -21, 4, -21, paint);
            if (kind == 1 || kind == 5) {
                round(canvas, -25, 1, -17, 23, 4, 0xfff4bd64);
                round(canvas, 17, 1, 25, 23, 4, 0xfff4bd64);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xfff4bd64);
        float flame = 5f + (float) Math.sin(seconds * 36f) * 3f;
        canvas.drawOval(-4, 27, 4, 32 + flame, paint);
        paint.setColor(CREAM); canvas.drawCircle(0, 1, 3, paint);
        if (kind == 7) {
            paint.setStyle(Paint.Style.STROKE); paint.setColor(0xfff4bd64); paint.setStrokeWidth(3);
            canvas.drawCircle(0, 0, 36, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        canvas.restoreToCount(save);
    }

    void boss(Canvas canvas, float x, float y, float radius, boolean phaseTwo, float seconds) {
        int save = canvas.save();
        canvas.translate(x, y); canvas.scale(radius / 64f, radius / 64f);
        round(canvas, -75, -21, 75, 27, 15, phaseTwo ? RED : BLUE);
        round(canvas, -37, -39, 37, 38, 23, 0xfff4bd64);
        round(canvas, -25, -27, 25, -9, 7, INK);
        paint.setColor(0xffe0f5ef);
        for (int i = -1; i <= 1; i++) canvas.drawCircle(i * 14, -18, 4, paint);
        round(canvas, -27, 16, 27, 35, 7, CREAM);
        paint.setColor(INK); canvas.drawCircle(-9, 23, 3, paint); canvas.drawCircle(9, 23, 3, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
        canvas.drawArc(-9, 21, 9, 32, 15, 150, false, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int side = -1; side <= 1; side += 2) {
            int prop = canvas.save(); canvas.translate(side * 58, 7);
            ellipse(canvas, -14, -14, 14, 14, CREAM);
            canvas.rotate(seconds * (phaseTwo ? 430 : 290));
            round(canvas, -21, -4, 21, 4, 4, INK);
            round(canvas, -4, -21, 4, 21, 4, INK);
            paint.setColor(0xfff4bd64); canvas.drawCircle(0, 0, 5, paint);
            canvas.restoreToCount(prop);
        }
        canvas.restoreToCount(save);
    }

    void robot(Canvas canvas, float x, float y, float scale, int color, float wobble) {
        int save = canvas.save(); canvas.translate(x, y); canvas.scale(scale, scale);
        canvas.rotate(wobble);
        round(canvas, -17, -7, 17, 22, 7, color);
        round(canvas, -23, -33, 23, 0, 9, color);
        round(canvas, -17, -26, 17, -8, 6, INK);
        paint.setColor(CREAM); canvas.drawCircle(-8, -17, 3, paint); canvas.drawCircle(8, -17, 3, paint);
        paint.setColor(INK); paint.setStrokeWidth(3);
        canvas.drawLine(0, -41, 0, -34, paint);
        paint.setColor(0xfff4bd64); canvas.drawCircle(0, -44, 4, paint);
        round(canvas, -14, 20, -3, 29, 3, INK); round(canvas, 3, 20, 14, 29, 3, INK);
        paint.setColor(CREAM); canvas.drawCircle(0, 6, 4, paint);
        paint.setColor(INK); paint.setStrokeWidth(6);
        canvas.drawLine(-20, 3, -29, 14, paint); canvas.drawLine(20, 3, 29, 14, paint);
        canvas.restoreToCount(save);
    }

    private void fill(Path path, Canvas canvas, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f); paint.setColor(INK);
        canvas.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
    }

    private void round(Canvas canvas, float l, float t, float r, float b, float radius, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); oval.set(l, t, r, b);
        canvas.drawRoundRect(oval, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(INK); paint.setStrokeWidth(2f);
        canvas.drawRoundRect(oval, radius, radius, paint); paint.setStyle(Paint.Style.FILL);
    }

    private void ellipse(Canvas canvas, float l, float t, float r, float b, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); oval.set(l, t, r, b);
        canvas.drawOval(oval, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(INK); paint.setStrokeWidth(2f);
        canvas.drawOval(oval, paint); paint.setStyle(Paint.Style.FILL);
    }
}
