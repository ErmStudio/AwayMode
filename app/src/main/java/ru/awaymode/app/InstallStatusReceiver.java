package ru.awaymode.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

public class InstallStatusReceiver extends BroadcastReceiver {
    static final String ACTION = "ru.awaymode.app.INSTALL_STATUS";

    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                //noinspection deprecation
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmation);
                MainActivity.appendLog(context, "Android открыл подтверждение установки обновления");
            } else {
                MainActivity.appendLog(context, "Android запросил подтверждение, но не передал экран установки");
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            MainActivity.appendLog(context, "Обновление AwayMode установлено");
        } else {
            context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                    .remove(MainActivity.RESTORE_AUTO_AFTER_UPDATE)
                    .remove(MainActivity.RESTORE_AUTO_TIMESTAMP)
                    .apply();
            MainActivity.appendLog(context, "Установка обновления не выполнена: " + status + " " + (message == null ? "" : message));
        }
    }
}
