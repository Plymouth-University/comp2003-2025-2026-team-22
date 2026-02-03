package com.example.flatflex;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;

public class ReminderWorker extends Worker {

    public static final String UNIQUE_NAME = "flatflex_daily_reminder";
    private static final String PREFS = "flatflex_prefs";
    private static final String KEY_NOTIF_HOUR = "notif_hour";
    private static final String KEY_NOTIF_MIN = "notif_min";

    private static final String CHANNEL_ID = "flatflex_reminders";

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int hour = prefs.getInt(KEY_NOTIF_HOUR, 19);
        int min = prefs.getInt(KEY_NOTIF_MIN, 0);

        // Only notify if we're within a 30-minute window after the configured time.
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, min);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        long diffMs = now.getTimeInMillis() - target.getTimeInMillis();
        if (diffMs < 0 || diffMs > 30L * 60L * 1000L) {
            return Result.success();
        }

        showNotification();
        return Result.success();
    }

    private void showNotification() {
        Context ctx = getApplicationContext();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FlatFlex reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                ctx,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_home)
                .setContentTitle("FlatFlex")
                .setContentText("Reminder: check today's chores.")
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify(1001, b.build());
    }
}
