package com.lvn.app;

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

/**
 * 주행 중 백그라운드 기록용 포그라운드 서비스.
 * 운전자가 내비 화면을 보고 있어 Lv.N 이 화면 뒤로 가도 프로세스가 유지되어
 * 차량 신호 수신과 판정(효과음 포함)이 계속된다. 상단에 '주행 기록 중' 알림이 표시된다.
 */
public class DriveRecordService extends Service {
    private static final String CHANNEL = "lvn_drive";
    private static final int NOTI_ID = 7;

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, DriveRecordService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable ignored) { }
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, DriveRecordService.class)); } catch (Throwable ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Lv.N 주행 기록 중")
                .setContentText("판정은 효과음으로 알려드려요 · 결과는 정차 후 확인")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTI_ID, n);
        } catch (Throwable e) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
