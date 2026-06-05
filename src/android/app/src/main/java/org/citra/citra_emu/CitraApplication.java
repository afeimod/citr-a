// Copyright 2019 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import org.citra.citra_emu.model.GameDatabase;
import org.citra.citra_emu.utils.DirectoryInitialization;
import org.citra.citra_emu.utils.PermissionsHandler;

public class CitraApplication extends Application {
    public static GameDatabase databaseHelper;
    private static CitraApplication application;

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_notification_channel_name);
            String description = getString(R.string.app_notification_channel_description);
            NotificationChannel channel = new NotificationChannel(getString(R.string.app_notification_channel_id), name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(description);
            channel.setSound(null, null);
            channel.setVibrationPattern(null);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;

        if (PermissionsHandler.hasWriteAccess(getApplicationContext())) {
            DirectoryInitialization.start(getApplicationContext());
        }

        createNotificationChannel();

        try {
            databaseHelper = new GameDatabase(this);
        } catch (Throwable t) {
            // 启动早期任何数据库异常都打到 logcat(MIUI 智能助手能看到 Log.e),
            // 不要让一个非关键依赖把整个 app 带崩
            android.util.Log.e("CitraApp",
                    "GameDatabase init failed: " + t.getClass().getName() + ": " + t.getMessage(), t);
        }
    }

    public static Context getAppContext() {
        return application.getApplicationContext();
    }
}
