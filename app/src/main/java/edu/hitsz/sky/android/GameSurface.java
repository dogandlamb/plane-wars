package edu.hitsz.sky.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.InputCommand;
import edu.hitsz.sky.core.Mode;
import edu.hitsz.sky.core.Phase;
import edu.hitsz.sky.core.StageCatalog;

/** Frame-driven presentation and multitouch input. Simulation belongs to its host. */
public final class GameSurface extends View {
    public interface Callback {
        void onInput(InputCommand command);
        void onPause();
        void onRadio();
    }

    private static final int INK = AircraftArt.INK, CREAM = AircraftArt.CREAM;
    private static final String[] SKILLS = {"冲击波", "护盾", "冲刺", "光波", "炸弹"};
    private static final String[] CAMERAS = {"零件间", "运输带", "充电角"};
    private static final String[] NIGHT_ACTIONS = {"左门", "左灯", "右灯", "右门"};
    private static final InputCommand.Type[] NIGHT_TYPES = {InputCommand.Type.LEFT_DOOR,
            InputCommand.Type.LEFT_LIGHT, InputCommand.Type.RIGHT_LIGHT, InputCommand.Type.RIGHT_DOOR};
    private final Callback callback;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface bold = Typeface.create("sans-serif-rounded", Typeface.BOLD);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final RectF scratch = new RectF();
    private final RectF pauseButton = new RectF(), radioButton = new RectF(), tabletButton = new RectF();
    private final RectF[] skillButtons = new RectF[5], nightButtons = new RectF[7];
    private final WorldPainter worldPainter = new WorldPainter();
    private int nightDescriptionState = -1;
    private final SnapshotMotion snapshotMotion = new SnapshotMotion();
    private final PlayfieldViewport viewport = new PlayfieldViewport();
    private final float density;
    private final Choreographer.FrameCallback frameCallback = this::onFrameTime;
    private volatile Frame frame = new Frame(null, null, null);
    private volatile int localPlayer;
    private volatile boolean lowEffects;
    private volatile String connectionLabel = "";
    private boolean attached, callbackPosted;
    private volatile boolean disposed;
    private int dragPointer = -1;
    private float dragOffsetX, dragOffsetY, animationTime;
    private long animationStart;
    private boolean nightLayout;
    private int cachedScore = Integer.MIN_VALUE, cachedHp = Integer.MIN_VALUE, cachedSeconds = -1;
    private int cachedRivalScore = Integer.MIN_VALUE;
    private String scoreText = "0", hpText = "", timeText = "0:00", rivalText = "";
    private int cachedStage = -1, cachedDifficulty = -1, cachedNightHour = -1, cachedEnergy = -1;
    private boolean cachedDeveloper;
    private Mode cachedMode;
    private String stageTitle = "", stageDetail = "", nightHourText = "", energyText = "";
    private int cachedBombs = Integer.MIN_VALUE;
    private String bombsText = "";
    private final int[] cooldownSeconds = {-1, -1, -1, -1};
    private final String[] cooldownLabels = {"就绪", "就绪", "就绪", "就绪"};

    public GameSurface(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        density = context.getResources().getDisplayMetrics().density;
        for (int i = 0; i < skillButtons.length; i++) skillButtons[i] = new RectF();
        for (int i = 0; i < nightButtons.length; i++) nightButtons[i] = new RectF();
        setFocusable(true); setClickable(true);
        setContentDescription("拖动飞机移动，底部按钮释放技能，顶部可以暂停和切换音乐");
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public synchronized void setFrame(GameState state, GameState rival) {
        if (!disposed) frame = new Frame(state, rival, snapshotMotion.accept(state, System.nanoTime()));
    }

    public void setLocalPlayer(int player) { localPlayer = player; }
    public void setLowEffects(boolean enabled) { lowEffects = enabled; }
    public void setConnectionLabel(String label) {
        connectionLabel = label == null ? "" : label;
    }

    public synchronized void dispose() {
        disposed = true; dragPointer = -1;
        if (callbackPosted) Choreographer.getInstance().removeFrameCallback(frameCallback);
        callbackPosted = false;
        snapshotMotion.clear(); frame = new Frame(null, null, null);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true;
        animationStart = System.nanoTime(); postNextFrame();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false; dragPointer = -1;
        if (callbackPosted) Choreographer.getInstance().removeFrameCallback(frameCallback);
        callbackPosted = false;
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) postNextFrame();
        else if (callbackPosted) {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            callbackPosted = false;
        }
    }

    private void onFrameTime(long nanos) {
        callbackPosted = false;
        if (!attached || disposed || getWindowVisibility() != VISIBLE) return;
        animationTime = (nanos - animationStart) / 1_000_000_000f;
        invalidate(); postNextFrame();
    }

    private void postNextFrame() {
        if (attached && !disposed && !callbackPosted && getWindowVisibility() == VISIBLE) {
            Choreographer.getInstance().postFrameCallback(frameCallback); callbackPosted = true;
        }
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); updateLayout(w, h);
    }

    private void updateLayout(int w, int h) {
        float edge = dp(12), gap = dp(6);
        pauseButton.set(w - dp(108), dp(10), w - dp(64), dp(54));
        radioButton.set(w - dp(56), dp(10), w - dp(12), dp(54));
        float controlsTop = h - dp(nightLayout ? 203 : 100);
        float availableHeight = Math.max(dp(80), controlsTop - dp(121));
        viewport.fit(edge, dp(115), Math.max(dp(80), w - edge * 2), availableHeight,
                GameState.WIDTH, GameState.HEIGHT);
        float skillWidth = (w - edge * 2 - gap * 4) / 5f;
        for (int i = 0; i < 5; i++) {
            float x = edge + i * (skillWidth + gap);
            skillButtons[i].set(x, h - dp(90), x + skillWidth, h - dp(29));
        }
        float cameraWidth = (w - edge * 2 - gap * 2) / 3f;
        tabletButton.set(edge, h - dp(196), w - edge, h - dp(154));
        for (int i = 0; i < 3; i++) {
            float x = edge + i * (cameraWidth + gap);
            nightButtons[i].set(x, h - dp(144), x + cameraWidth, h - dp(102));
        }
        float actionWidth = (w - edge * 2 - gap * 3) / 4f;
        for (int i = 0; i < 4; i++) {
            float x = edge + i * (actionWidth + gap);
            nightButtons[i + 3].set(x, h - dp(92), x + actionWidth, h - dp(34));
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(CREAM);
        Frame current = frame;
        GameState state = current.state;
        boolean isNight = state != null && state.night != null;
        if (nightLayout != isNight) {
            nightLayout = isNight; updateLayout(getWidth(), getHeight()); dragPointer = -1;
        }
        paintHeader(canvas, state, current.rival);
        rounded(canvas, viewport.left - dp(2), viewport.top - dp(2),
                viewport.left + viewport.width + dp(2), viewport.top + viewport.height + dp(2),
                dp(15), INK);
        int save = canvas.save();
        scratch.set(viewport.left, viewport.top, viewport.left + viewport.width,
                viewport.top + viewport.height);
        canvas.clipRect(scratch);
        canvas.translate(viewport.left, viewport.top); canvas.scale(viewport.scale, viewport.scale);
        if (state != null) worldPainter.paintWorld(canvas, state, current.motion,
                current.motion.fraction(System.nanoTime()), lowEffects, animationTime, localPlayer);
        else {
            canvas.drawColor(0xffc9e9ed);
            text(canvas, "准备出发…", 240, 400, 26, INK, Paint.Align.CENTER, true);
        }
        canvas.restoreToCount(save);
        if (state != null && state.night == null && state.mode != Mode.ENDLESS) {
            float duration = StageCatalog.get(state.stage).duration;
            bar(canvas, viewport.left, viewport.top + viewport.height + dp(7),
                    viewport.left + viewport.width, viewport.top + viewport.height + dp(10),
                    state.elapsed / Math.max(1, duration), AircraftArt.BLUE);
        }
        if (isNight) paintNightControls(canvas, state.night);
        else paintSkillControls(canvas, findPilot(state));
    }

    private void paintHeader(Canvas canvas, GameState state, GameState rival) {
        int stage = state == null ? 1 : Math.max(1, Math.min(6, state.stage));
        int difficulty = state == null || state.difficulty == null ? -1 : state.difficulty.ordinal();
        boolean developer = state != null && state.developer;
        Mode mode = state == null ? null : state.mode;
        if (cachedStage != stage || cachedDifficulty != difficulty || cachedDeveloper != developer || cachedMode != mode) {
            cachedStage = stage; cachedDifficulty = difficulty; cachedDeveloper = developer;
            cachedMode = mode;
            stageTitle = mode == Mode.ENDLESS ? "无尽飞行" : StageCatalog.get(stage).title;
            stageDetail = stage == 6 ? "番外 · 夜班伙伴" : mode == Mode.ENDLESS ? "持续飞行" : "第 " + stage + " 关";
            if (state != null && state.difficulty != null && stage != 6) stageDetail += " · " + state.difficulty.label;
            if (developer) stageDetail += " · 开发者";
        }
        text(canvas, stageTitle, dp(16), dp(30), dp(20), INK, Paint.Align.LEFT, true);
        text(canvas, stageDetail, dp(17), dp(50), dp(11), 0xff61737d, Paint.Align.LEFT, false);
        button(canvas, pauseButton, 0xffe8e4d9, false);
        button(canvas, radioButton, 0xffe8e4d9, false);
        paint.setColor(INK); paint.setStrokeWidth(dp(3));
        float px = pauseButton.centerX(), py = pauseButton.centerY();
        canvas.drawLine(px - dp(4), py - dp(7), px - dp(4), py + dp(7), paint);
        canvas.drawLine(px + dp(4), py - dp(7), px + dp(4), py + dp(7), paint);
        float rx = radioButton.centerX(), ry = radioButton.centerY();
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
        scratch.set(rx - dp(10), ry - dp(7), rx + dp(10), ry + dp(8));
        canvas.drawRoundRect(scratch, dp(3), dp(3), paint);
        canvas.drawLine(rx - dp(4), ry - dp(8), rx + dp(7), ry - dp(13), paint);
        canvas.drawCircle(rx - dp(3), ry + dp(1), dp(3), paint);
        canvas.drawLine(rx + dp(4), ry - dp(1), rx + dp(7), ry - dp(1), paint);
        paint.setStyle(Paint.Style.FILL);
        if (state == null) return;
        GameState.Pilot pilot = findPilot(state);
        if (stage == 6) {
            GameState.Night night = state.night;
            if (cachedNightHour != night.hour) {
                cachedNightHour = night.hour; nightHourText = Math.min(6, night.hour) + ":00 / 6:00";
            }
            int energy = Math.max(0, (int) night.energy);
            if (cachedEnergy != energy || (developer && !"电量 ∞".equals(energyText)) ||
                    (!developer && "电量 ∞".equals(energyText))) {
                cachedEnergy = energy; energyText = developer ? "电量 ∞" : "电量 " + energy + "%";
            }
            text(canvas, nightHourText, dp(17), dp(76),
                    dp(17), INK, Paint.Align.LEFT, true);
            text(canvas, energyText,
                    getWidth() - dp(17), dp(76), dp(14), INK, Paint.Align.RIGHT, true);
            bar(canvas, dp(17), dp(86), getWidth() - dp(17), dp(94),
                    state.developer ? 1 : night.energy / 100f,
                    night.energy < 20 ? AircraftArt.RED : AircraftArt.GREEN);
        } else {
            if (cachedScore != state.score) { cachedScore = state.score; scoreText = Integer.toString(state.score); }
            int hp = pilot == null ? 0 : pilot.hp;
            if (cachedHp != hp) { cachedHp = hp; hpText = Integer.toString(hp); }
            int seconds = Math.max(0, (int) state.elapsed);
            if (cachedSeconds != seconds) {
                cachedSeconds = seconds; timeText = seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
            }
            text(canvas, "得分 " + scoreText, dp(17), dp(75), dp(15), INK, Paint.Align.LEFT, true);
            text(canvas, timeText, getWidth() - dp(17), dp(75), dp(13), INK, Paint.Align.RIGHT, false);
            float healthRatio = pilot == null ? 0 : pilot.hp / (float) Math.max(1, pilot.maxHp);
            bar(canvas, dp(17), dp(85), dp(143), dp(95), healthRatio,
                    healthRatio < .3f ? AircraftArt.RED : AircraftArt.GREEN);
            text(canvas, state.developer ? "∞" : hpText, dp(150), dp(95), dp(12), INK, Paint.Align.LEFT, true);
            if (state.mode == Mode.VERSUS && rival != null) {
                if (cachedRivalScore != rival.score) {
                    cachedRivalScore = rival.score; rivalText = "对手 " + rival.score;
                }
                text(canvas, rivalText, getWidth() - dp(17), dp(95), dp(12),
                        INK, Paint.Align.RIGHT, true);
            } else if (state.mode == Mode.COOP) {
                text(canvas, "一起飞", getWidth() - dp(17), dp(95), dp(12),
                        INK, Paint.Align.RIGHT, true);
            }
        }
        String label = connectionLabel;
        if (!label.isEmpty()) {
            if (label.length() > 30) label = label.substring(0, 30);
            text(canvas, label, getWidth() * .5f, dp(109), dp(10),
                    0xff61737d, Paint.Align.CENTER, false);
        }
    }

    private void paintSkillControls(Canvas canvas, GameState.Pilot pilot) {
        for (int i = 0; i < 5; i++) {
            RectF box = skillButtons[i];
            float cooldown = pilot == null || i == 4 ? 0 : pilot.cooldowns[i];
            boolean unavailable = pilot == null || pilot.downed || cooldown > .05f;
            int color = unavailable ? 0xffe2e3db : i == 1 ? 0xffb9dedb : i == 4 ? 0xfff0c49d : 0xfff3db9e;
            button(canvas, box, color, false);
            text(canvas, SKILLS[i], box.centerX(), box.top + dp(25),
                    dp(12), INK, Paint.Align.CENTER, true);
            String value;
            if (pilot == null) value = "—";
            else if (pilot.downed) value = frame.state.mode == Mode.COOP && frame.state.phase == Phase.RUNNING
                    ? "待救援" : "已结束";
            else if (frame.state != null && frame.state.developer) value = "∞";
            else if (i == 4) {
                if (cachedBombs != pilot.bombs) { cachedBombs = pilot.bombs; bombsText = "×" + pilot.bombs; }
                value = bombsText;
            } else {
                int seconds = cooldown > .05f ? (int) Math.ceil(cooldown) : 0;
                if (cooldownSeconds[i] != seconds) {
                    cooldownSeconds[i] = seconds;
                    cooldownLabels[i] = cooldown > .05f ? seconds + "s" : "就绪";
                }
                value = cooldownLabels[i];
            }
            text(canvas, value, box.centerX(), box.top + dp(45),
                    dp(11), unavailable ? 0xff71808a : INK, Paint.Align.CENTER, false);
        }
        text(canvas, "拖动飞行 · 两指可以同时放技能", getWidth() * .5f, getHeight() - dp(10),
                dp(10), 0xff71808a, Paint.Align.CENTER, false);
    }

    private void paintNightControls(Canvas canvas, GameState.Night night) {
        int descriptionState = (int)night.energy + night.hour * 128 + night.camera * 1024
                + (night.tabletOpen ? 4096 : 0) + (night.leftDoor ? 8192 : 0)
                + (night.rightDoor ? 16384 : 0) + (night.leftLight ? 32768 : 0) + (night.rightLight ? 65536 : 0);
        if (descriptionState != nightDescriptionState) {
            nightDescriptionState = descriptionState;
            setContentDescription("机库夜班，" + night.hour + "时，电量" + (int)night.energy + "%，平板"
                    + (night.tabletOpen ? "已拿起，" + CAMERAS[Math.max(0, Math.min(2, night.camera))] : "已收起")
                    + "，左门" + (night.leftDoor ? "已关闭" : "开着") + "，右门" + (night.rightDoor ? "已关闭" : "开着")
                    + "，左灯" + (night.leftLight ? "亮着" : "关闭") + "，右灯" + (night.rightLight ? "亮着" : "关闭"));
        }
        button(canvas, tabletButton, night.tabletOpen ? 0xffb9dedb : 0xffcbdde8, night.tabletOpen);
        text(canvas, night.energy <= 0 ? "平板没电了" : night.tabletOpen ? "收起平板  ↓" : "拿起平板看监控  ↑",
                tabletButton.centerX(), tabletButton.centerY() + dp(5), dp(14), INK, Paint.Align.CENTER, true);
        for (int i = 0; i < 3; i++) {
            RectF box = nightButtons[i]; boolean selected = night.tabletOpen && night.camera == i;
            button(canvas, box, selected ? 0xffb9dedb : 0xffe8e4d9, selected);
            text(canvas, CAMERAS[i], box.centerX(), box.centerY() + dp(4),
                    dp(12), night.tabletOpen ? INK : 0xff87928e, Paint.Align.CENTER, true);
        }
        for (int i = 0; i < 4; i++) {
            boolean active = nightActive(night, i);
            RectF box = nightButtons[i + 3];
            button(canvas, box, active ? 0xfff3db9e : 0xffe8e4d9, active);
            text(canvas, NIGHT_ACTIONS[i], box.centerX(), box.top + dp(23),
                    dp(13), INK, Paint.Align.CENTER, true);
            text(canvas, (i == 0 || i == 3) ? (active ? "已关闭" : "开着") : (active ? "亮着" : "已关灯"),
                    box.centerX(), box.top + dp(43), dp(11), 0xff61737d, Paint.Align.CENTER, false);
        }
        text(canvas, "门和灯会用电，记得轮流看看监控。", getWidth() * .5f, getHeight() - dp(12),
                dp(10), 0xff71808a, Paint.Align.CENTER, false);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (disposed) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex();
            float x = event.getX(index), y = event.getY(index);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            if (!pressControl(x, y) && dragPointer == -1 && !nightLayout && viewport.contains(x, y)) {
                GameState.Pilot pilot = findPilot(frame.state);
                if (pilot != null && !pilot.downed) {
                    dragPointer = event.getPointerId(index);
                    dragOffsetX = pilot.x - viewport.worldX(x);
                    dragOffsetY = pilot.y - viewport.worldY(y);
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && dragPointer != -1) {
            int index = event.findPointerIndex(dragPointer);
            if (index >= 0) {
                float x = clamp(viewport.worldX(event.getX(index)) + dragOffsetX, 20, 460);
                float y = clamp(viewport.worldY(event.getY(index)) + dragOffsetY, 30, 775);
                callback.onInput(InputCommand.move(localPlayer, x, y));
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (event.getPointerId(event.getActionIndex()) == dragPointer) dragPointer = -1;
            if (action == MotionEvent.ACTION_UP) {
                performClick();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            dragPointer = -1;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private boolean pressControl(float x, float y) {
        if (pauseButton.contains(x, y)) { callback.onPause(); return true; }
        if (radioButton.contains(x, y)) { callback.onRadio(); return true; }
        GameState state = frame.state;
        if (state == null) return false;
        if (state.night != null) {
            if (tabletButton.contains(x,y)) {
                callback.onInput(InputCommand.action(localPlayer, InputCommand.Type.TABLET, state.night.tabletOpen ? 0 : 1));
                return true;
            }
            for (int i = 0; i < nightButtons.length; i++) {
                if (!nightButtons[i].contains(x, y)) continue;
                if (i < 3 && !state.night.tabletOpen) return true;
                InputCommand.Type type = i < 3 ? InputCommand.Type.CAMERA : NIGHT_TYPES[i - 3];
                int value = i < 3 ? i : (nightActive(state.night, i - 3) ? 0 : 1);
                callback.onInput(InputCommand.action(localPlayer, type, value)); return true;
            }
        } else {
            for (int i = 0; i < skillButtons.length; i++) {
                if (!skillButtons[i].contains(x, y)) continue;
                callback.onInput(i < 4 ? InputCommand.skill(localPlayer, i) :
                        InputCommand.action(localPlayer, InputCommand.Type.BOMB, 0));
                return true;
            }
        }
        return false;
    }

    private GameState.Pilot findPilot(GameState state) {
        if (state == null || state.pilots.isEmpty()) return null;
        for (GameState.Pilot pilot : state.pilots) if (pilot.id == localPlayer) return pilot;
        return state.pilots.get(0);
    }

    private static boolean nightActive(GameState.Night night, int index) {
        switch (index) {
            case 0: return night.leftDoor;
            case 1: return night.leftLight;
            case 2: return night.rightLight;
            default: return night.rightDoor;
        }
    }

    private void button(Canvas canvas, RectF box, int color, boolean selected) {
        rounded(canvas, box.left, box.top + dp(3), box.right, box.bottom + dp(3), dp(12), 0xffd4d3c8);
        rounded(canvas, box.left, box.top, box.right, box.bottom, dp(12), color);
        if (selected) {
            paint.setStyle(Paint.Style.STROKE); paint.setColor(INK); paint.setStrokeWidth(dp(1.5f));
            canvas.drawRoundRect(box, dp(12), dp(12), paint); paint.setStyle(Paint.Style.FILL);
        }
    }

    private void bar(Canvas canvas, float l, float t, float r, float b, float ratio, int color) {
        rounded(canvas, l, t, r, b, (b - t) / 2, 0xffdedfd4);
        rounded(canvas, l, t, l + (r - l) * clamp(ratio, 0, 1), b, (b - t) / 2, color);
    }

    private void rounded(Canvas canvas, float l, float t, float r, float b, float radius, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); scratch.set(l, t, r, b);
        canvas.drawRoundRect(scratch, radius, radius, paint);
    }

    private void text(Canvas canvas, String text, float x, float y, float size, int color,
                      Paint.Align align, boolean heavy) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTypeface(heavy ? bold : regular);
        paint.setTextSize(size); paint.setTextAlign(align); canvas.drawText(text, x, y, paint);
    }

    private float dp(float value) { return value * density; }
    private static float clamp(float value, float low, float high) { return Math.max(low, Math.min(high, value)); }
    private static final class Frame {
        final GameState state, rival;
        final SnapshotMotion.Frame motion;
        Frame(GameState state, GameState rival, SnapshotMotion.Frame motion) {
            this.state = state; this.rival = rival; this.motion = motion;
        }
    }
}
