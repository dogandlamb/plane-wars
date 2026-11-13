package edu.hitsz.sky.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicIntegerArray;

/** App-owned audio resources. No activity or game entity is retained. */
public final class SoundRack implements AutoCloseable {
    public static final int SHOT = 0, HIT = 1, EXPLOSION = 2, PICKUP = 3, SKILL = 4, WIN = 5, FAIL = 6;
    private static final String TAG = "SkyAudio";
    private static final String[] TRACK_FILES = {"music_0.wav", "music_1.wav", "music_boss.wav",
            "music_travelers_encore.m4a", "music_hopes_and_dreams.m4a", "music_undertale.m4a"};
    private static final String[] TRACK_NAMES = {"海风来信", "云上追逐", "运输舰出发",
            "Travelers’ Encore", "Hopes and Dreams（五周年混音）", "Undertale"};
    private static final String[] EFFECT_FILES = {"shot.wav", "hit.wav", "explosion.wav", "pickup.wav",
            "skill.wav", "win.wav", "fail.wav"};
    private final Context context;
    private final GamePreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final SoundPool sounds = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sky-audio-loader"); thread.setDaemon(true); return thread;
    });
    private final AtomicIntegerArray effects = new AtomicIntegerArray(EFFECT_FILES.length);
    private final long[] lastEffect = new long[EFFECT_FILES.length];
    private final Set<Integer> loaded = ConcurrentHashMap.newKeySet();
    private final SharedPreferences.OnSharedPreferenceChangeListener volumeListener = (store, key) -> refresh();
    private MediaPlayer music;
    private boolean prepared, wantsMusic, bossActive;
    private int selectedTrack;
    private volatile int playingTrack = -1;
    private volatile boolean closed;

    public SoundRack(Context context, GamePreferences preferences) {
        this.context = context.getApplicationContext(); this.preferences = preferences;
        preferences.listen(volumeListener);
        sounds.setOnLoadCompleteListener((pool, id, status) -> { if (status == 0) loaded.add(id); });
        loader.execute(() -> {
            for (int i = 0; i < EFFECT_FILES.length && !closed; i++) {
                try (AssetFileDescriptor descriptor = this.context.getAssets().openFd("sky-audio/" + EFFECT_FILES[i])) {
                    synchronized (sounds) {
                        if (!closed) effects.set(i, sounds.load(descriptor, 1));
                    }
                } catch (IOException | IllegalStateException error) {
                    Log.w(TAG, "Sound effect unavailable: " + EFFECT_FILES[i], error);
                }
            }
        });
    }

    public void startMusic(boolean boss) {
        onMain(() -> {
            if (closed) return;
            wantsMusic = true;
            int target = boss && !bossActive ? 2 : playingTrack < 0 ? selectedTrack : playingTrack;
            if (!boss && bossActive) target = selectedTrack;
            bossActive = boss;
            if (music == null || playingTrack != target) loadTrack(target);
            else startIfReady();
        });
    }

    public void pauseMusic() {
        onMain(() -> {
            wantsMusic = false;
            if (music != null && prepared && music.isPlaying()) music.pause();
        });
    }

    public void resumeMusic() {
        onMain(() -> {
            if (closed) return;
            wantsMusic = true;
            if (music == null) loadTrack(selectedTrack); else startIfReady();
        });
    }

    public void nextTrack() { chooseTrack(1); }
    public void previousTrack() { chooseTrack(-1); }

    public String trackName() {
        int index = playingTrack;
        return TRACK_NAMES[index < 0 ? selectedTrack : index];
    }

    public void refresh() {
        onMain(() -> {
            if (!closed && music != null && prepared) {
                float volume = preferences.muted() ? 0 : preferences.bgmVolume();
                music.setVolume(volume, volume);
            }
        });
    }

    public void play(int effect) {
        if (effect < 0 || effect >= effects.length() || closed || preferences.muted()) return;
        onMain(() -> {
            if (closed || preferences.muted()) return;
            long now = SystemClock.uptimeMillis();
            long interval = effect == SHOT ? 95 : 55;
            if (now - lastEffect[effect] < interval) return;
            int id = effects.get(effect);
            if (id == 0 || !loaded.contains(id)) return;
            lastEffect[effect] = now;
            float volume = preferences.sfxVolume();
            sounds.play(id, volume, volume, effect == SHOT ? 1 : 2, 0, 1f);
        });
    }

    private void chooseTrack(int step) {
        onMain(() -> {
            if (closed) return;
            int current = playingTrack < 0 ? selectedTrack : playingTrack;
            selectedTrack = (current + step + TRACK_FILES.length) % TRACK_FILES.length;
            loadTrack(selectedTrack);
        });
    }

    private void loadTrack(int index) {
        releaseMusic();
        playingTrack = index;
        MediaPlayer player = new MediaPlayer(); music = player;
        player.setAudioAttributes(attributes); player.setLooping(true);
        player.setOnPreparedListener(ready -> {
            if (closed || ready != music) return;
            prepared = true; refresh(); startIfReady();
        });
        player.setOnErrorListener((failed, what, extra) -> {
            if (failed == music) releaseMusic();
            Log.w(TAG, "Music playback error: " + what + "/" + extra); return true;
        });
        try (AssetFileDescriptor descriptor = context.getAssets().openFd("sky-audio/" + TRACK_FILES[index])) {
            player.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            player.prepareAsync();
        } catch (IOException | IllegalStateException error) {
            releaseMusic(); Log.w(TAG, "Music track unavailable: " + TRACK_FILES[index], error);
        }
    }

    private void startIfReady() {
        if (!closed && wantsMusic && prepared && music != null && !music.isPlaying()) music.start();
    }

    private void releaseMusic() {
        if (music != null) {
            music.setOnPreparedListener(null); music.setOnErrorListener(null); music.release(); music = null;
        }
        prepared = false;
    }

    @Override public void close() {
        if (closed) return;
        closed = true; preferences.stopListening(volumeListener); loader.shutdownNow();
        onMain(() -> {
            wantsMusic = false; releaseMusic();
            synchronized (sounds) { sounds.release(); loaded.clear(); }
        });
    }

    private void onMain(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) task.run(); else main.post(task);
    }
}
