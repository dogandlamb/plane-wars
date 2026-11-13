package edu.hitsz.sky.android;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import edu.hitsz.sky.core.GameState;

/** A desk, two doorways and a handheld monitor. All switches use authoritative state. */
final class NightWatchPainter {
    private static final int INK = AircraftArt.INK;
    private static final String[] ROOMS = {"零件间", "运输带", "充电角"};
    private static final int[] COLORS = {0xff79b885, 0xffe9b05f, 0xffa99ac6};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final AircraftArt art = new AircraftArt();
    private final Typeface typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD);
    private float lift, lastTime = -1;

    void paint(Canvas canvas, GameState state, float time) {
        GameState.Night night = state.night;
        float delta = lastTime < 0 ? 0 : Math.max(0, Math.min(.05f, time - lastTime));
        lastTime = time;
        lift = night.tabletOpen ? Math.min(1, lift + delta * 7) : Math.max(0, lift - delta * 7);
        office(canvas, night, time);
        if (lift > 0) {
            paint.setColor(((int)(lift * 95) << 24)); canvas.drawRect(0,0,480,800,paint);
            int save = canvas.save();
            float eased = 1 - (1-lift)*(1-lift);
            canvas.translate(0, (1-eased) * 820);
            tablet(canvas, night, time);
            canvas.restoreToCount(save);
        }
    }

    private void office(Canvas canvas, GameState.Night night, float time) {
        canvas.drawColor(0xffc8d8dc);
        box(canvas, 155, 70, 325, 225, 22, 0xff4d6c87);
        paint.setColor(0xffffe5a8); canvas.drawCircle(284,111,23,paint);
        paint.setColor(0xffb0d1dc);
        for(int i=0;i<7;i++)canvas.drawCircle(176+(i*37)%123,96+(i*31)%103,2.4f,paint);
        box(canvas, 232, 70, 242, 225, 3, 0xff6f8a95);
        text(canvas, "机库值班室",240,43,22,INK);
        box(canvas, 170,260,310,291,11,0xfff5e4b7);
        text(canvas, "夜班 · 请轻声",240,281,13,INK);
        doorway(canvas, night, 18, 334, true, time);
        doorway(canvas, night, 350, 334, false, time);
        paint.setColor(0xffa5b8b7);canvas.drawRect(0,558,480,800,paint);
        box(canvas, 98, 534, 382, 774, 24, 0xffc29e75);
        box(canvas, 88, 520, 392, 566, 14, 0xffe1be8c);
        art.robot(canvas, 240, 437, .95f, 0xfff4bd64, (float)Math.sin(time*1.7f)*3);
        box(canvas, 126, 594, 354, 711, 18, 0xff334856);
        box(canvas, 138, 606, 342, 695, 11, night.energy > 0 ? 0xffb9d8cc : 0xff7a8a89);
        text(canvas, night.energy > 0 ? "豆丁的监控平板" : "电量用完了",240,644,16,INK);
        text(canvas, "CAM 01 · 02 · 03",240,675,13,0xff537267);
        paint.setColor(0xffeef0df);canvas.drawCircle(240,703,3,paint);
        text(canvas, "拿起平板，看一眼朋友们",240,752,15,INK);
        boolean close = false;
        for(float progress:night.intruders) if(progress > .78f) close=true;
        text(canvas, close ? "有朋友到门口啦，看看两边" : "门和灯不用时，记得关掉",240,788,14,INK);
    }

    private void doorway(Canvas canvas, GameState.Night night, float x, float y, boolean left, float time) {
        boolean closed = left ? night.leftDoor : night.rightDoor;
        boolean light = left ? night.leftLight : night.rightLight;
        box(canvas,x,y,x+112,y+223,17,0xff79918f);
        box(canvas,x+9,y+10,x+103,y+212,12,closed ? 0xffb8c4b9 : light ? 0xffffe7aa : 0xff698a90);
        if(closed) {
            paint.setColor(0xff8da6a2);paint.setStrokeWidth(4);
            for(int i=0;i<7;i++)canvas.drawLine(x+16,y+26+i*27,x+96,y+26+i*27,paint);
            text(canvas,"门已关闭",x+56,y+120,14,INK);
        } else if(light) {
            for(int i=0;i<3;i++)if((night.routes[i]==0)==left && night.intruders[i]>.78f)
                art.robot(canvas,x+56,y+158,1.1f,COLORS[i],(float)Math.sin(time*3+i)*4);
        }
        box(canvas,x+35,y-37,x+77,y-16,8,light?0xffffd37c:0xff9aafab);
        text(canvas,left?"左门":"右门",x+56,y-51,17,INK);
    }

    private void tablet(Canvas canvas, GameState.Night night, float time) {
        int camera = Math.max(0,Math.min(2,night.camera));
        box(canvas,17,39,469,757,30,0x33000000);
        box(canvas,11,31,463,748,30,0xff283c4c);
        box(canvas,22,42,452,737,24,0xff3d5362);
        box(canvas,37,75,437,682,12,0xffc5dfd2);
        paint.setColor(0xff96b6ac);canvas.drawCircle(239,57,4,paint);
        text(canvas,"CAM 0"+(camera+1),92,113,18,INK);
        text(canvas,ROOMS[camera],260,113,23,INK);
        paint.setColor(((int)(time*2)&1)==0?0xffdc9072:0xffa0b8a2);canvas.drawCircle(408,104,5,paint);
        int save=canvas.save();canvas.clipRect(47,132,427,548);
        room(canvas,camera,time);
        float progress=Math.max(0,Math.min(1,night.intruders[camera]));
        art.robot(canvas,240+(float)Math.sin(time*1.5f+camera)*15,
                226+progress*235,.5f+progress*1.2f,COLORS[camera],(float)Math.sin(time*2)*4);
        paint.setColor(0x1192a795);paint.setStrokeWidth(1);
        for(int y=140;y<548;y+=13)canvas.drawLine(47,y,427,y,paint);
        canvas.restoreToCount(save);
        text(canvas,progress>.78f?"到门口了":"正在溜达",240,574,18,INK);
        text(canvas,night.routes[camera]==0?"← 左侧通道":"右侧通道 →",240,598,13,0xff58756f);
        for(int i=0;i<3;i++) {
            float x=106+i*130;
            box(canvas,x-51,620,x+51,660,10,i==camera?0xff78ae9f:0xffdce6d6);
            text(canvas,"0"+(i+1)+" "+ROOMS[i],x,646,12,INK);
        }
        text(canvas,"豆丁 · 值班终端",240,713,14,0xffd4e0de);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0xff9cb7b8);
        canvas.drawCircle(410,711,9,paint);paint.setStyle(Paint.Style.FILL);
        box(canvas,-9,450,18,606,11,0xffefc8a3);
        box(canvas,456,450,484,606,11,0xffefc8a3);
    }

    private void room(Canvas canvas,int camera,float time) {
        paint.setColor(camera==0?0xffb0c9b5:camera==1?0xffbfd0ce:0xffafc5d3);
        canvas.drawRect(47,132,427,548,paint);
        path.reset();path.moveTo(180,132);path.lineTo(300,132);path.lineTo(411,548);path.lineTo(64,548);path.close();
        paint.setColor(0xffd6dfc5);canvas.drawPath(path,paint);
        for(int i=0;i<3;i++) {
            float y=166+i*113;
            box(canvas,57,y,146,y+71,8,0xff8eafa5);
            box(canvas,333,y,418,y+71,8,0xff8eafa5);
            if(camera==0) {
                box(canvas,67,y+20,95,y+53,4,0xffe9bf79);box(canvas,105,y+11,133,y+53,4,0xffdca393);
            } else if(camera==1) {
                float offset=(time*28)%40;
                paint.setColor(0xff71948e);paint.setStrokeWidth(4);
                for(int j=0;j<3;j++)canvas.drawLine(65,y+14+j*19,137,y+14+j*19,paint);
                box(canvas,347,y+offset,401,y+offset+27,5,0xffeac67f);
            } else {
                box(canvas,73,y+13,128,y+58,9,0xffdee8d7);
                box(canvas,82,y+23,119,y+49,4,0xff87ba88);
                paint.setColor(0xffffdd8f);canvas.drawCircle(373,y+34,13,paint);
            }
        }
    }

    private void box(Canvas canvas,float l,float t,float r,float b,float radius,int color) {
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);rect.set(l,t,r,b);canvas.drawRoundRect(rect,radius,radius,paint);
    }
    private void text(Canvas canvas,String text,float x,float y,float size,int color) {
        paint.setStyle(Paint.Style.FILL);paint.setTypeface(typeface);paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size);paint.setColor(color);canvas.drawText(text,x,y,paint);
    }
}
