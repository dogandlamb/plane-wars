package edu.hitsz.sky.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import edu.hitsz.aircraftwar.R;

/** Keeps an explicitly opened room connected while its activity is temporarily hidden. */
public final class RoomConnectionService extends Service {
    private static final String CHANNEL = "sky-room";
    private static final int NOTIFICATION = 17;

    public static void start(Context context) {
        context.startForegroundService(new Intent(context, RoomConnectionService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, RoomConnectionService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "联机房间", NotificationManager.IMPORTANCE_LOW));
        Intent returnToGame = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 0, returnToGame,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_sky)
                .setContentTitle("晴空小队 · 联机中")
                .setContentText("房间还在，点这里继续飞。")
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
        // Android 14+ requires the declared foreground-service type at promotion.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION, notification);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
