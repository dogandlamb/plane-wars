package edu.hitsz.sky.android;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import edu.hitsz.sky.core.Difficulty;
import edu.hitsz.sky.core.Mode;

/** Small local preferences and score records; game rules remain in the shared core. */
public final class GamePreferences {
    private final SharedPreferences store;

    public GamePreferences(Context context) {
        store = context.getApplicationContext().getSharedPreferences("sky_squad", Context.MODE_PRIVATE);
    }

    public boolean developer() { return store.getBoolean("developer", false); }
    public boolean muted() { return store.getBoolean("muted", false); }
    public boolean vibration() { return store.getBoolean("vibration", true); }
    public boolean effects() { return store.getBoolean("effects", true); }
    public float bgmVolume() { return store.getFloat("bgm", .65f); }
    public float sfxVolume() { return store.getFloat("sfx", .8f); }
    public void setDeveloper(boolean value) { store.edit().putBoolean("developer", value).apply(); }
    public void setMuted(boolean value) { store.edit().putBoolean("muted", value).apply(); }
    public void setVibration(boolean value) { store.edit().putBoolean("vibration", value).apply(); }
    public void setEffects(boolean value) { store.edit().putBoolean("effects", value).apply(); }
    public void setBgmVolume(float value) { store.edit().putFloat("bgm", clamp(value)).apply(); }
    public void setSfxVolume(float value) { store.edit().putFloat("sfx", clamp(value)).apply(); }

    public Difficulty difficulty() {
        try { return Difficulty.valueOf(store.getString("difficulty", "NORMAL")); }
        catch (IllegalArgumentException ignored) { return Difficulty.NORMAL; }
    }

    public void setDifficulty(Difficulty difficulty) {
        store.edit().putString("difficulty", difficulty.name()).apply();
    }

    public int unlocked(Difficulty difficulty) {
        return Math.max(1, Math.min(5, store.getInt("unlocked_" + difficulty.name(), 1)));
    }

    public synchronized void complete(int stage, Difficulty difficulty, boolean developer) {
        int current = unlocked(difficulty);
        if (developer || stage < 1 || stage > 5 || stage > current) return;
        store.edit().putInt("unlocked_" + difficulty.name(), Math.max(current, Math.min(5, stage + 1)))
                .putBoolean("cleared_" + difficulty.name() + "_" + stage, true).apply();
    }

    public boolean cleared(int stage, Difficulty difficulty) {
        return store.getBoolean("cleared_" + difficulty.name() + "_" + stage, false);
    }

    public synchronized void record(String name, int score, int stage, Difficulty difficulty,
                                    boolean developer, Mode mode) {
        String displayName = name == null ? "" : name.trim();
        if (displayName.isEmpty()) displayName = "飞行员";
        if (displayName.length() > 24) displayName = displayName.substring(0, 24);
        List<Record> entries = records(difficulty, developer);
        String date = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date());
        entries.add(new Record(UUID.randomUUID().toString(), displayName, Math.max(0, score), stage,
                date, mode, System.currentTimeMillis()));
        entries.sort(RECORD_ORDER);
        saveRecords(difficulty, developer, entries);
    }

    public synchronized List<Record> records(Difficulty difficulty, boolean developer) {
        List<Record> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(store.getString(recordKey(difficulty, developer), "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry == null) continue;
                Mode mode;
                try { mode = Mode.valueOf(entry.optString("mode", "SOLO")); }
                catch (IllegalArgumentException ignored) { mode = Mode.SOLO; }
                result.add(new Record(entry.optString("id"), entry.optString("name", "飞行员"),
                        entry.optInt("score"), entry.optInt("stage", 1), entry.optString("date"),
                        mode, entry.optLong("time")));
            }
        } catch (JSONException ignored) {
        }
        result.sort(RECORD_ORDER);
        return result;
    }

    public synchronized void deleteRecord(Difficulty difficulty, boolean developer, int index) {
        List<Record> entries = records(difficulty, developer);
        if (index < 0 || index >= entries.size()) return;
        entries.remove(index);
        saveRecords(difficulty, developer, entries);
    }

    void listen(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        store.registerOnSharedPreferenceChangeListener(listener);
    }

    void stopListening(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        store.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private void saveRecords(Difficulty difficulty, boolean developer, List<Record> records) {
        JSONArray array = new JSONArray();
        for (Record record : records) {
            JSONObject entry = new JSONObject();
            try {
                entry.put("id", record.id).put("name", record.name).put("score", record.score)
                        .put("stage", record.stage).put("date", record.date)
                        .put("mode", record.mode.name()).put("time", record.time);
                array.put(entry);
            } catch (JSONException ignored) { /* Values above are all finite primitives or strings. */ }
        }
        store.edit().putString(recordKey(difficulty, developer), array.toString()).apply();
    }

    private static String recordKey(Difficulty difficulty, boolean developer) {
        return "records_" + difficulty.name() + (developer ? "_dev" : "_normal");
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

    private static final Comparator<Record> RECORD_ORDER = Comparator
            .comparingInt((Record record) -> record.score).reversed()
            .thenComparing(Comparator.comparingLong((Record record) -> record.time).reversed());

    public static final class Record {
        public final String name, date;
        public final int score, stage;
        public final Mode mode;
        private final String id;
        private final long time;

        private Record(String id, String name, int score, int stage, String date, Mode mode, long time) {
            this.id = id; this.name = name; this.score = score; this.stage = stage;
            this.date = date; this.mode = mode; this.time = time;
        }
    }
}
