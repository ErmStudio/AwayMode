package ru.awaymode.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.Collections;
import java.util.Set;

public class WifiMonitorService extends Service {
    private static final String CHANNEL = "awaymode_monitor";
    private static final int NOTIFICATION_ID = 42;
    private static final String LAST_SSID = "last_observed_ssid";
    private static final String LAST_ACTION = "last_vpn_action";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback callback;
    private SharedPreferences prefs;
    private String statusText = "Ожидание изменения Wi-Fi";
    private boolean lastConnectRequested = true;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Мониторинг Wi-Fi запущен"));
        registerNetworkCallback();
        scheduleEvaluation(3000);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        prefs.edit().putBoolean(MainActivity.AUTO, true).apply();
        return START_STICKY;
    }

    private void registerNetworkCallback() {
        if (connectivity == null) { setStatus("Не удалось получить ConnectivityManager"); return; }
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                scheduleEvaluation(3000);
            }
            @Override public void onLost(Network network) {
                scheduleEvaluation(3000);
            }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                scheduleEvaluation(3000);
            }
        };
        try {
            connectivity.registerDefaultNetworkCallback(callback);
        } catch (Exception e) {
            setStatus("Не удалось включить мониторинг: " + e.getClass().getSimpleName());
        }
    }

    private void scheduleEvaluation(long delayMs) {
        handler.removeCallbacks(evaluateRunnable);
        handler.postDelayed(evaluateRunnable, delayMs);
    }
    private final Runnable evaluateRunnable = this::evaluateNetwork;

    private void evaluateNetwork() {
        String current = readSsid();
        String previous = prefs.getString(LAST_SSID, "");
        Set<String> white = prefs.getStringSet(MainActivity.WHITE, Collections.emptySet());
        Set<String> black = prefs.getStringSet(MainActivity.BLACK, Collections.emptySet());

        if (current == null) {
            if (isWifiConnected()) {
                setStatus("Wi-Fi подключён, но имя сети недоступно; VPN не трогаем");
            } else if (!previous.isEmpty() && white.contains(previous)) {
                requestVpnAction(true, "Потеряли доверенную сеть «" + previous + "»: включаем INCY");
                prefs.edit().putString(LAST_SSID, "").apply();
            } else {
                setStatus("Wi-Fi отключён; правило не требует действий");
                prefs.edit().putString(LAST_SSID, "").apply();
            }
            return;
        }
        if (current.equals(previous)) {
            setStatus("Сеть «" + current + "» без изменений; VPN не трогаем");
            return;
        }

        if (white.contains(current)) {
            requestVpnAction(false, "Доверенная сеть «" + current + "»: отключаем INCY");
        } else if (black.contains(current)) {
            requestVpnAction(true, "Сеть «" + current + "» в чёрном списке: включаем INCY");
        } else if (!previous.isEmpty() && white.contains(previous)) {
            requestVpnAction(true, "Покинули доверенную сеть; после 3 секунд проверки включаем INCY");
        } else {
            setStatus("Неизвестная сеть «" + current + "»: состояние VPN не меняем");
        }
        prefs.edit().putString(LAST_SSID, current).apply();
    }

    private boolean isWifiConnected() {
        try {
            Network active = connectivity == null ? null : connectivity.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : connectivity.getNetworkCapabilities(active);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) { return false; }
    }

    private String readSsid() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null) return null;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return null;
            String ssid = info.getSSID();
            if (ssid == null || ssid.equals(WifiManager.UNKNOWN_SSID) || ssid.equals("<unknown ssid>")) return null;
            if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) ssid = ssid.substring(1, ssid.length() - 1);
            return ssid;
        } catch (Exception e) { return null; }
    }

    private void requestVpnAction(boolean connect, String reason) {
        String action = connect ? "connect" : "disconnect";
        lastConnectRequested = connect;
        String previousAction = prefs.getString(LAST_ACTION, "");
        if (action.equals(previousAction)) {
            setStatus(reason + " (команда уже отправлялась)");
            return;
        }
        String uri = connect ? "incy://connect" : "incy://disconnect";
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            prefs.edit().putString(LAST_ACTION, action).apply();
            setStatus(reason + ". Команда передана Android; проверь фактический статус INCY.");
        } catch (ActivityNotFoundException e) {
            setStatus("Не найден обработчик " + uri + ". Проверь установку INCY.");
        } catch (Exception e) {
            setStatus("Android не разрешил запуск INCY из фона: " + e.getClass().getSimpleName() + ". Используй кнопку в уведомлении.");
        }
        updateNotification();
    }

    private void setStatus(String value) { statusText = value; updateNotification(); }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "AwayMode — автоматизация", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Состояние фонового мониторинга Wi-Fi");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 1, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String deepLink = lastConnectRequested ? "incy://connect" : "incy://disconnect";
        Intent openIncy = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
        PendingIntent action = PendingIntent.getActivity(this, 2, openIncy, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("AwayMode работает")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text + "\n" + statusText))
                .setContentIntent(content).setOngoing(true)
                .addAction(new Notification.Action.Builder(0, "Открыть INCY", action).build());
        return b.build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(statusText));
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (connectivity != null && callback != null) {
            try { connectivity.unregisterNetworkCallback(callback); } catch (Exception ignored) {}
        }
        prefs.edit().putBoolean(MainActivity.AUTO, false).apply();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
