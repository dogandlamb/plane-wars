package edu.hitsz.sky.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The route is the same in every difficulty and every play mode. */
public final class StageCatalog {
    public static final class Stage {
        public final int number, color;
        public final String title, subtitle;
        public final float duration;
        private Stage(int number, String title, String subtitle, float duration, int color) {
            this.number = number; this.title = title; this.subtitle = subtitle;
            this.duration = duration; this.color = color;
        }
    }

    private static final List<Stage> CAMPAIGN = Collections.unmodifiableList(Arrays.asList(
            new Stage(1, "海邮港", "跟紧航标，先把第一封信送到。", 60f, 0xff63bed0),
            new Stage(2, "风车原野", "侧风来了！留心两翼的快递机。", 60f, 0xff8cbe87),
            new Stage(3, "积雨云海", "看到闪光就绕开，云里可不止有雨。", 60f, 0xff8e9ccf),
            new Stage(4, "星灯工厂", "等护盾张开，再给运输线让条路。", 60f, 0xffd7a5b8),
            new Stage(5, "中央运输舰", "航标到齐了。把大块头带回家！", 60f, 0xffe6ac69)
    ));
    private static final Stage NIGHT = new Stage(6, "机库夜班", "豆丁说今晚很安静。你最好看一眼监控。", 240f, 0xff7797b6);

    private StageCatalog() { }
    public static List<Stage> all() { return CAMPAIGN; }
    public static boolean available(int number, int unlocked, boolean developer) {
        return number >= 1 && number <= 6 && (number == 6 || developer || number <= unlocked);
    }
    public static int[] selectable(int unlocked, boolean developer) {
        int count = developer ? 5 : Math.max(1, Math.min(5, unlocked));
        int[] stages = new int[count + 1];
        for (int i = 0; i < count; i++) stages[i] = i + 1;
        stages[count] = 6;
        return stages;
    }
    public static Stage get(int number) {
        if (number == 6) return NIGHT;
        if (number < 1 || number > 5) throw new IllegalArgumentException("Unknown stage: " + number);
        return CAMPAIGN.get(number - 1);
    }
}
