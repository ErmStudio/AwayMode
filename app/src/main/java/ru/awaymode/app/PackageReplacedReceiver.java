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
        if (!prefs.getBoolean(MainActivity.AUTO, false)) return;

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
