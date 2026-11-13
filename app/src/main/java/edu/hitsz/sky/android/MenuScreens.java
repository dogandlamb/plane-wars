package edu.hitsz.sky.android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

import edu.hitsz.sky.core.Difficulty;
import edu.hitsz.sky.core.Mode;
import edu.hitsz.sky.core.StageCatalog;

/** Native, scrollable menu screens. Activities own navigation and session lifetime. */
public final class MenuScreens {
    public interface Actions {
        void showHome();
        void showCampaign();
        void showOnline();
        void showSettings();
        void showScores();
        void startSolo(int stage, Difficulty difficulty, boolean endless);
        void replayStory(int stage);
        void showRadio();
    }

    private static final int CREAM = AircraftArt.CREAM, INK = AircraftArt.INK, RED = AircraftArt.RED;
    private static final int BLUE = AircraftArt.BLUE, GREEN = AircraftArt.GREEN, MUTED = 0xFF73818B;
    private final Context context;
    private final GamePreferences preferences;
    private final Actions actions;
    private boolean developerScores;

    public MenuScreens(Context context, GamePreferences preferences, Actions actions) {
        this.context = context; this.preferences = preferences; this.actions = actions;
    }

    public View home() {
        LinearLayout page = page();
        TextView stamp = text("SKY SQUAD  /  飞机大战", 12, MUTED, true);
        stamp.setLetterSpacing(.12f);
        page.addView(stamp);
        page.addView(text("晴空小队", 38, INK, true), gap(10));
        page.addView(text("航线乱了。一起把天空找回来。", 15, MUTED, false), gap(6));
        page.addView(new HomeSky(context), new LinearLayout.LayoutParams(-1, dp(206)));
        page.addView(button("开始冒险", RED, Color.WHITE, actions::showCampaign), gap(8));
        LinearLayout pair = row();
        pair.addView(button("双人出发", BLUE, INK, actions::showOnline), weighted(1, 0));
        pair.addView(button("无尽飞行", 0xFFE6EBDD, INK,
                () -> actions.startSolo(1, preferences.difficulty(), true)), weighted(1, 10));
        page.addView(pair, gap(10));
        LinearLayout links = row();
        links.addView(button("飞行记录", Color.WHITE, INK, actions::showScores), weighted(1, 0));
        links.addView(button("设置", Color.WHITE, INK, actions::showSettings), weighted(1, 10));
        page.addView(links, gap(10));
        page.addView(button("音乐电台", 0xFFE6EBDD, INK, actions::showRadio), gap(10));
        TextView footer = text(preferences.developer() ? "开发者模式已开启 · 随时去机库逛逛"
                : "拖动飞机移动 · 自动开火 · 技能随手放", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        page.addView(footer, gap(22));
        return scroll(page);
    }

    public View campaign() {
        LinearLayout page = headedPage("选择航线", "五片空域，一趟冒险。" );
        page.addView(difficultyPicker(actions::showCampaign), gap(18));
        Difficulty difficulty = preferences.difficulty();
        int unlocked = preferences.unlocked(difficulty);
        String[] descriptions = {"找回海面上的第一盏航标", "借着风向穿过风车原野", "看准电云的亮光再通过",
                "绕开护盾，找准发光的弱点", "登上运输舰，让航线重新亮起"};
        for (int stage = 1; stage <= 5; stage++) {
            StageCatalog.Stage data = StageCatalog.get(stage);
            boolean available = StageCatalog.available(stage, unlocked, preferences.developer());
            LinearLayout card = card();
            LinearLayout heading = row();
            TextView number = text(String.format(java.util.Locale.ROOT, "%02d", stage), 25,
                    available ? RED : MUTED, true);
            heading.addView(number, new LinearLayout.LayoutParams(dp(50), -2));
            LinearLayout names = column();
            names.addView(text(data.title, 20, available ? INK : MUTED, true));
            names.addView(text(descriptions[stage - 1], 13, MUTED, false), gap(4));
            heading.addView(names, weighted(1, 0));
            card.addView(heading);
            if (preferences.cleared(stage, difficulty)) {
                card.addView(text("已通关  ✓", 12, GREEN, true), gap(10));
            }
            final int selected = stage;
            LinearLayout controls = row();
            Button enter = button(available ? "出发" : "先通过上一关", available ? BLUE : 0xFFE2E4DF,
                    available ? INK : MUTED, () -> actions.startSolo(selected, difficulty, false));
            enter.setEnabled(available);
            controls.addView(enter, weighted(1.5f, 0));
            if (available) controls.addView(button("看故事", CREAM, INK,
                    () -> actions.replayStory(selected)), weighted(1, 8));
            card.addView(controls, gap(12));
            page.addView(card, gap(12));
        }
        {
            LinearLayout extra = card();
            extra.addView(text("番外  ·  机库夜班", 20, INK, true));
            extra.addView(text("拿起平板看监控，守到早晨交班。", 14, MUTED, false), gap(6));
            extra.addView(button("今晚我值班", GREEN, INK,
                    () -> actions.startSolo(6, difficulty, false)), gap(12));
            extra.addView(button("看故事", CREAM, INK, () -> actions.replayStory(6)), gap(8));
            page.addView(extra, gap(12));
        }
        return scroll(page);
    }

    public View settings() {
        LinearLayout page = headedPage("设置", "调到顺手，就出发。" );
        LinearLayout sound = card();
        sound.addView(text("声音", 19, INK, true));
        sound.addView(toggle("静音", preferences.muted(), preferences::setMuted), gap(8));
        addVolume(sound, "音乐", preferences.bgmVolume(), preferences::setBgmVolume);
        addVolume(sound, "音效", preferences.sfxVolume(), preferences::setSfxVolume);
        page.addView(sound, gap(20));
        LinearLayout feel = card();
        feel.addView(text("手感与画面", 19, INK, true));
        feel.addView(toggle("震动反馈", preferences.vibration(), preferences::setVibration), gap(8));
        feel.addView(toggle("丰富特效", preferences.effects(), preferences::setEffects));
        feel.addView(text("关掉丰富特效，可以少一点粒子和震屏。", 12, MUTED, false));
        page.addView(feel, gap(12));
        LinearLayout developer = card();
        developer.addView(toggle("开发者模式", preferences.developer(), preferences::setDeveloper));
        developer.addView(text("自由选关 · 无限生命与技能", 13, MUTED, false), gap(4));
        developer.addView(text("开发者成绩单独保存，联机时整间房一起启用。", 12, MUTED, false), gap(8));
        page.addView(developer, gap(12));
        TextView credit = text("晴空小队\n原创图形与合成音乐 · 电台另收录三首曲目", 12, MUTED, false);
        credit.setGravity(Gravity.CENTER);
        page.addView(credit, gap(24));
        return scroll(page);
    }

    public View scores() {
        LinearLayout page = headedPage("飞行记录", "每一趟，都算数。" );
        page.addView(difficultyPicker(actions::showScores), gap(18));
        LinearLayout tabs = row();
        tabs.addView(button("正式记录", developerScores ? Color.WHITE : BLUE, INK, () -> {
            developerScores = false; actions.showScores();
        }), weighted(1, 0));
        tabs.addView(button("开发者", developerScores ? BLUE : Color.WHITE, INK, () -> {
            developerScores = true; actions.showScores();
        }), weighted(1, 8));
        page.addView(tabs, gap(10));
        Difficulty difficulty = preferences.difficulty();
        List<GamePreferences.Record> records = preferences.records(difficulty, developerScores);
        if (records.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("还没有飞行记录", 21, INK, true));
            empty.addView(text("先去飞一趟，回来再看。", 15, MUTED, false), gap(8));
            page.addView(empty, gap(20));
        }
        for (int i = 0; i < records.size(); i++) {
            GamePreferences.Record record = records.get(i);
            LinearLayout entry = card();
            entry.addView(text((i + 1) + "  " + record.name + "    " + record.score, 20, INK, true));
            String place = record.stage == 6 ? "机库夜班" : "第 " + record.stage + " 关";
            entry.addView(text(place + "  ·  " + modeName(record.mode) + "  ·  " + record.date,
                    12, MUTED, false), gap(6));
            final int index = i;
            final boolean dev = developerScores;
            entry.addView(button("删除这条记录", CREAM, MUTED, () -> new AlertDialog.Builder(context)
                    .setTitle("删除记录？").setMessage(record.name + " · " + record.score + " 分")
                    .setNegativeButton("留下", null).setPositiveButton("删除", (dialog, which) -> {
                        preferences.deleteRecord(difficulty, dev, index); actions.showScores();
                    }).show()), gap(12));
            page.addView(entry, gap(12));
        }
        return scroll(page);
    }

    private View difficultyPicker(Runnable refresh) {
        LinearLayout picker = row();
        for (Difficulty difficulty : Difficulty.values()) {
            boolean selected = preferences.difficulty() == difficulty;
            Button button = button(difficulty.label, selected ? INK : Color.WHITE,
                    selected ? Color.WHITE : INK, () -> {
                        preferences.setDifficulty(difficulty); refresh.run();
                    });
            picker.addView(button, weighted(1, difficulty.ordinal() == 0 ? 0 : 8));
        }
        return picker;
    }

    private LinearLayout headedPage(String title, String subtitle) {
        LinearLayout page = page();
        Button back = button("‹  返回", CREAM, INK, actions::showHome);
        LinearLayout.LayoutParams backSize = new LinearLayout.LayoutParams(dp(96), dp(48));
        page.addView(back, backSize);
        page.addView(text(title, 30, INK, true), gap(12));
        page.addView(text(subtitle, 14, MUTED, false), gap(6));
        return page;
    }

    private void addVolume(LinearLayout parent, String title, float value, FloatSetter setter) {
        TextView label = text(title + "  " + Math.round(value * 100) + "%", 14, INK, false);
        parent.addView(label, gap(12));
        SeekBar bar = new SeekBar(context);
        bar.setMax(100); bar.setProgress(Math.round(value * 100));
        bar.setProgressTintList(ColorStateList.valueOf(BLUE));
        bar.setThumbTintList(ColorStateList.valueOf(INK));
        bar.setContentDescription(title + "音量");
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) { setter.set(progress / 100f); label.setText(title + "  " + progress + "%"); }
            }
            public void onStartTrackingTouch(SeekBar seekBar) { }
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        parent.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private Switch toggle(String title, boolean checked, BooleanSetter setter) {
        Switch control = new Switch(context);
        control.setText(title); control.setTextColor(INK); control.setTextSize(16);
        control.setMinHeight(dp(52)); control.setChecked(checked);
        control.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{BLUE, 0xFFBBC5C5}));
        control.setOnCheckedChangeListener((button, value) -> setter.set(value));
        return control;
    }

    private View scroll(LinearLayout contents) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true); scroll.setBackgroundColor(CREAM);
        scroll.setClipToPadding(false); scroll.addView(contents);
        return scroll;
    }

    private LinearLayout page() {
        LinearLayout page = column();
        page.setPadding(dp(22), dp(20), dp(22), dp(28));
        return page;
    }

    private LinearLayout card() {
        LinearLayout card = column(); card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE); background.setCornerRadius(dp(22));
        card.setBackground(background);
        return card;
    }

    private LinearLayout column() {
        LinearLayout result = new LinearLayout(context); result.setOrientation(LinearLayout.VERTICAL); return result;
    }

    private LinearLayout row() {
        LinearLayout result = new LinearLayout(context); result.setOrientation(LinearLayout.HORIZONTAL);
        result.setGravity(Gravity.CENTER_VERTICAL); return result;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView label = new TextView(context); label.setText(value); label.setTextSize(size); label.setTextColor(color);
        label.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        label.setLineSpacing(dp(3), 1f); return label;
    }

    private Button button(String title, int color, int textColor, Runnable action) {
        Button button = new Button(context);
        button.setText(title); button.setAllCaps(false); button.setTextSize(16); button.setTextColor(textColor);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(56)); button.setMinimumHeight(dp(56));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable fill = new GradientDrawable(); fill.setColor(color); fill.setCornerRadius(dp(18));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), fill, null));
        button.setOnClickListener(view -> action.run()); return button;
    }

    private LinearLayout.LayoutParams gap(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); return params;
    }

    private LinearLayout.LayoutParams weighted(float weight, int left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, weight); params.leftMargin = dp(left); return params;
    }

    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private interface BooleanSetter { void set(boolean value); }
    private interface FloatSetter { void set(float value); }

    private static String modeName(Mode mode) {
        switch (mode) {
            case COOP: return "双人合作";
            case VERSUS: return "比分对战";
            case ENDLESS: return "无尽飞行";
            default: return "冒险";
        }
    }

    private final class HomeSky extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path wing = new Path();
        private boolean attached;
        HomeSky(Context context) { super(context); setContentDescription("晴空中飞过的红色小飞机"); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; invalidate(); }
        @Override protected void onDetachedFromWindow() { attached = false; super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = getWidth() / 360f;
            canvas.save(); canvas.scale(scale, scale);
            float h = getHeight() / scale;
            paint.setColor(0xFFD8EBE9); canvas.drawRoundRect(0, 14, 360, h - 12, 70, 70, paint);
            float t = preferences.effects() ? SystemClock.uptimeMillis() / 1000f : 0f;
            paint.setColor(0xFFFDFBF4);
            for (int i = 0; i < 5; i++) {
                float x = ((i * 101 + t * (8 + i)) % 440) - 40;
                float y = 45 + (i % 3) * 41;
                canvas.drawRoundRect(x, y, x + 70, y + 19, 12, 12, paint);
                canvas.drawCircle(x + 22, y - 1, 15, paint);
                canvas.drawCircle(x + 43, y - 3, 19, paint);
            }
            canvas.save(); canvas.translate(185, h * .48f + (float)Math.sin(t * 1.7f) * 6);
            canvas.rotate(-12 + (float)Math.sin(t) * 3);
            paint.setColor(0x18000000); canvas.drawOval(-72, 46, 85, 70, paint);
            paint.setColor(RED);
            wing.reset(); wing.moveTo(-13, -38); wing.lineTo(-95, 28); wing.quadTo(-94, 40, -76, 36);
            wing.lineTo(-7, 10); wing.close(); canvas.drawPath(wing, paint);
            wing.reset(); wing.moveTo(13, -38); wing.lineTo(95, 28); wing.quadTo(94, 40, 76, 36);
            wing.lineTo(7, 10); wing.close(); canvas.drawPath(wing, paint);
            paint.setColor(0xFFDF483F); canvas.drawRoundRect(-31, 36, 31, 50, 8, 8, paint);
            paint.setColor(0xFFF9F6E9); canvas.drawRoundRect(-19, -68, 19, 55, 20, 20, paint);
            paint.setColor(INK); canvas.drawRoundRect(-12, -39, 12, -9, 12, 12, paint);
            paint.setColor(BLUE); canvas.drawRoundRect(-7, -34, 7, -20, 7, 7, paint);
            paint.setColor(RED); canvas.drawRoundRect(-9, 24, 9, 64, 6, 6, paint);
            paint.setColor(INK); canvas.drawRoundRect(-32, -70, 32, -64, 3, 3, paint);
            canvas.restore(); canvas.restore();
            if (attached && isShown() && preferences.effects()) postInvalidateOnAnimation();
        }
    }
}
