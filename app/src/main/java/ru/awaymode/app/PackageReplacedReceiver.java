package ru.awaymode.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class PackageReplacedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        long timestamp = prefs.getLong(MainActivity.RESTORE_AUTO_TIMESTAMP, 0);
        boolean hasRecentSnapshot = timestamp > 0 && System.currentTimeMillis() - timestamp < 24L * 60 * 60 * 1000;
        boolean shouldRestore = hasRecentSnapshot
                ? prefs.getBoolean(MainActivity.RESTORE_AUTO_AFTER_UPDATE, false)
                : prefs.getBoolean(MainActivity.AUTO, false);
        prefs.edit().remove(MainActivity.RESTORE_AUTO_AFTER_UPDATE)
                .remove(MainActivity.RESTORE_AUTO_TIMESTAMP)
                .putBoolean(MainActivity.AUTO, shouldRestore)
                .putBoolean(MainActivity.SERVICE_ALIVE, false)
                .apply();
        if (!shouldRestore) return;

        try {
            Intent service = new Intent(context, WifiMonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
            MainActivity.appendLog(context, "Мониторинг автоматически восстановлен после обновления");
        } catch (Exception e) {
            MainActivity.appendLog(context, "Не удалось восстановить мониторинг после обновления: " + e.getClass().getSimpleName());
        }
    }
}
