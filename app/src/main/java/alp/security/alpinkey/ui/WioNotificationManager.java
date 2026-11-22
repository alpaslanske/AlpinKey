package alp.security.alpinkey.ui;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import alp.security.alpinkey.MainActivity;
import alp.security.alpinkey.R;

import static android.app.Notification.DEFAULT_ALL;

public class WioNotificationManager {

    // Notification Params
    public static final String CHANNEL_MAX = "CHANNEL_MAX";
    public static final String CHANNEL_HIGH = "CHANNEL_HIGH";
    public static final String CHANNEL_MEDIUM = "CHANNEL_MEDIUM";
    public static final String CHANNEL_LOW = "CHANNEL_LOW";
    public static final String KEY_URL = "URL";
    public static final String KEY_ACTION = "ACTION";
    public static final String VALUE_SUBSCRIBE = "SUBSCRIBE";
    public static final String PUSHNOTIFICATIONSTOPIC = "NOTIFICATIONS_ENABLED";

    public static void displayNotification(Context ctx, String title, String body, String channel, String key, String value, String sound){
        cancelNotification(ctx, 3);

        PendingIntent pendingIntent = null;
        String channelId = channel;

        Bundle optionsBundle = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+ (Android 14+)
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            optionsBundle = options.toBundle();
        }

        Intent notifyIntent = null;
        if (key != null) {
            if (key.equals(KEY_URL)) {
                notifyIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(value));
                notifyIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            } else if (key.equals(KEY_ACTION)) {
                notifyIntent = new Intent(ctx, MainActivity.class);
                notifyIntent.putExtra(KEY_ACTION, value);
                notifyIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
        }

        if (notifyIntent == null) {
            notifyIntent = new Intent(ctx, MainActivity.class);
            notifyIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }

        pendingIntent = PendingIntent.getActivity(
                ctx, 0, notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                optionsBundle);

        if (channel == null) {
            channelId = CHANNEL_HIGH;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(R.drawable.logo_small)
                .setContentTitle(title)
                .setContentText(body)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (sound != null && sound.equals("default")) {
            builder.setDefaults(DEFAULT_ALL);
        } else {
            builder.setDefaults(0);
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ctx);
        try {
            notificationManager.notify(3, builder.build());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public static void createNotificationChannel(Context ctx, String channelid, int priority) {
        NotificationChannel serviceChannel = new NotificationChannel(
                channelid,
                "AlpinKey Remote Messaging Channel",
                priority
        );
        serviceChannel.setShowBadge(true);
        serviceChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = ctx.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }
    
    public static void cancelNotification(Context ctx, int notificationId) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ctx);
        notificationManager.cancel(notificationId);
    }
}
