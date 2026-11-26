package ru.awaymode.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends Activity {
    static final String PREFS = "awaymode_rules";
    static final String WHITE = "white_ssids";
    static final String BLACK = "black_ssids";
    static final String KNOWN = "known_ssids";
    static final String AUTO = "automation_enabled";
    static final String LOG = "event_log";
    private static final String PENDING_APK = "pending_update_apk";
    private static final int REQ_LOCATION = 101;
    private static final int REQ_NOTIFICATIONS = 102;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private WifiManager wifi;
    private LinearLayout root, networkRows;
    private EditText search;
    private TextView currentLabel, automationLabel, logView;
    private Switch automationSwitch;
    private String currentSsid;
    private File pendingApk;
    private final SharedPreferences.OnSharedPreferenceChangeListener logListener = (shared, key) -> {
        if (LOG.equals(key)) refreshLog();
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(logListener);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        buildUi();
        refreshCurrent();
        renderNetworks();
        requestStartupPermissions();
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) {
            refreshCurrent();
            updateAutomationUi();
            refreshLog();
            installPendingApkIfAllowed(false);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(30));
        root.setBackgroundColor(0xFF101827);
        scroll.addView(root);

        TextView title = text("AwayMode", 29, 0xFFFFFFFF, true);
        title.setGravity(Gravity.CENTER);
        add(title, 0, 2);
        TextView subtitle = text("Автоматизация INCY по Wi-Fi", 14, 0xFFB7C2D3, false);
        subtitle.setGravity(Gravity.CENTER);
        add(subtitle, 0, 18);

        currentLabel = text("Текущая сеть: определяем…", 15, 0xFFFFFFFF, true);
        card(currentLabel, 14, 12);

        TextView section = text("СЕТИ WI-FI", 15, 0xFF91A9FF, true);
        add(section, 0, 6);
        TextView note = text("Здесь отображаются сети, добавленные в AwayMode, и текущая сеть. Полный список сохранённых Wi-Fi сетей Android сторонним приложениям не открывает. Добавь сеть вручную или подключись к ней и нажми «Добавить текущую».", 12, 0xFF9AA8BD, false);
        add(note, 0, 12);

        LinearLayout scanBar = new LinearLayout(this);
        scanBar.setOrientation(LinearLayout.HORIZONTAL);
        scanBar.setGravity(Gravity.CENTER_VERTICAL);
        Button scan = button("Добавить текущую");
        scan.setOnClickListener(v -> addCurrentNetwork());
        scanBar.addView(scan, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button manual = button("Ввести вручную");
        manual.setOnClickListener(v -> askForSsid());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(46), 1);
        mp.leftMargin = dp(8);
        scanBar.addView(manual, mp);
        add(scanBar, 0, 10);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(0xFFFFFFFF);
        search.setHintTextColor(0xFF8795AA);
        search.setHint("Поиск по названию сети");
        search.setTextSize(15);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setBackground(round(0xFF202D40, 12));
        add(search, 0, 8);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { renderNetworks(); }
            public void afterTextChanged(Editable e) {}
        });

        networkRows = new LinearLayout(this);
        networkRows.setOrientation(LinearLayout.VERTICAL);
        add(networkRows, 0, 18);

        TextView automationTitle = text("АВТОМАТИЗАЦИЯ", 15, 0xFF91A9FF, true);
        add(automationTitle, 0, 8);
        LinearLayout autoCard = new LinearLayout(this);
        autoCard.setOrientation(LinearLayout.HORIZONTAL);
        autoCard.setGravity(Gravity.CENTER_VERTICAL);
        autoCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        autoCard.setBackground(round(0xFF202D40, 14));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        automationLabel = text("Мониторинг выключен", 15, 0xFFFFFFFF, true);
        labels.addView(automationLabel);
        TextView autoHint = text("Работа в фоне через уведомление", 11, 0xFF9AA8BD, false);
        labels.addView(autoHint);
        autoCard.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        automationSwitch = new Switch(this);
        automationSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (checked) startAutomation(); else stopAutomation();
        });
        autoCard.addView(automationSwitch);
        add(autoCard, 0, 12);

        TextView rules = text("Выключить VPN — доверенная сеть. Включить VPN — остальные выбранные сети. Не учитывать — оставить без правила. Без Wi-Fi INCY должен быть включён; после потери Wi-Fi AwayMode проверит подключение через 3 секунды.", 12, 0xFF9AA8BD, false);
        add(rules, 0, 8);
        TextView battery = text("Для стабильной работы на Xiaomi отключи ограничения батареи для AwayMode и разреши автозапуск в настройках HyperOS.", 12, 0xFFFFD28A, false);
        add(battery, 0, 8);

        Button update = button("Проверить обновление");
        update.setOnClickListener(v -> checkForUpdate());
        add(update, 14, 8);

        TextView logTitle = text("ЖУРНАЛ СОБЫТИЙ", 15, 0xFF91A9FF, true);
        add(logTitle, 0, 8);
        Button shareLogs = button("Отправить логи разработчику");
        shareLogs.setOnClickListener(v -> confirmShareLogs());
        add(shareLogs, 0, 8);
        logView = text("", 11, 0xFFB7C2D3, false);
        logView.setTextIsSelectable(true);
        card(logView, 0, 8);
        refreshLog();
        setContentView(scroll);
    }

    private void requestStartupPermissions() {
        if (!hasLocationPermission()) {
            new AlertDialog.Builder(this)
                    .setTitle("Разрешение для Wi-Fi")
                    .setMessage("AwayMode нужно разрешение на местоположение, чтобы Android разрешил читать имя подключённой сети. Геоданные приложение никуда не отправляет.")
                    .setPositiveButton("Разрешить", (d, w) -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION))
                    .setNegativeButton("Пока нет", (d, w) -> updatePermissionHint())
                    .show();
            return;
        }
        requestNotificationPermissionIfNeeded();
    }

    private void checkForUpdate() {
        Toast.makeText(this, "Проверяю GitHub Releases…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL api = new URL("https://api.github.com/repos/EugeneErmakov/AwayMode/releases/latest");
                connection = (HttpURLConnection) api.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "AwayMode-Android");
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IllegalStateException("GitHub HTTP " + connection.getResponseCode());
                JSONObject release;
                try (InputStream in = connection.getInputStream()) {
                    release = new JSONObject(new String(readAll(in), java.nio.charset.StandardCharsets.UTF_8));
                }
                String tag = release.optString("tag_name", "");
                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = null;
                String apkName = null;
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null && asset.optString("name", "").toLowerCase(Locale.ROOT).endsWith(".apk")) {
                        apkName = asset.optString("name");
                        apkUrl = asset.optString("browser_download_url");
                        break;
                    }
                }
                if (apkUrl == null || apkUrl.isEmpty()) throw new IllegalStateException("В последнем релизе нет APK");
                if (compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) {
                    runOnUiThread(() -> Toast.makeText(this, "Установлена актуальная версия " + BuildConfig.VERSION_NAME, Toast.LENGTH_LONG).show());
                    appendLog(this, "Обновление: актуальная версия " + BuildConfig.VERSION_NAME);
                    return;
                }
                HttpURLConnection download = (HttpURLConnection) new URL(apkUrl).openConnection();
                download.setConnectTimeout(20000);
                download.setReadTimeout(30000);
                download.setRequestProperty("User-Agent", "AwayMode-Android");
                if (download.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IllegalStateException("Не удалось скачать APK: HTTP " + download.getResponseCode());
                File destination = new File(getCacheDir(), apkName.replaceAll("[^A-Za-z0-9._-]", "_"));
                try (InputStream in = download.getInputStream(); FileOutputStream out = new FileOutputStream(destination)) {
                    copy(in, out);
                } finally { download.disconnect(); }
                pendingApk = destination;
                getPreferences(MODE_PRIVATE).edit().putString(PENDING_APK, destination.getAbsolutePath()).apply();
                appendLog(this, "Обновление " + tag + " скачано с GitHub");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Скачана версия " + tag, Toast.LENGTH_LONG).show();
                    installPendingApkIfAllowed(true);
                });
            } catch (Exception e) {
                appendLog(this, "Ошибка проверки обновления: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Не удалось обновить: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "AwayMode-updater").start();
    }

    private void installPendingApkIfAllowed(boolean openSettingsIfNeeded) {
        if (pendingApk == null) {
            String path = getPreferences(MODE_PRIVATE).getString(PENDING_APK, null);
            if (path != null) pendingApk = new File(path);
        }
        if (pendingApk == null || !pendingApk.isFile()) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            if (!openSettingsIfNeeded) return;
            Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            return;
        }
        final File apk = pendingApk;
        pendingApk = null;
        getPreferences(MODE_PRIVATE).edit().remove(PENDING_APK).apply();
        new Thread(() -> {
            int sessionId = -1;
            try {
                PackageInstaller installer = getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(getPackageName());
                sessionId = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    try (FileInputStream in = new FileInputStream(apk);
                         OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                        copy(in, out);
                        session.fsync(out);
                    }
                    Intent callback = new Intent(this, InstallStatusReceiver.class).setAction(InstallStatusReceiver.ACTION);
                    callback.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                    PendingIntent pending = PendingIntent.getBroadcast(this, sessionId, callback, flags);
                    session.commit(pending.getIntentSender());
                }
                appendLog(this, "Передал обновление установщику Android; требуется подтвердить установку");
            } catch (Exception e) {
                if (sessionId >= 0) try { getPackageManager().getPackageInstaller().abandonSession(sessionId); } catch (Exception ignored) {}
                appendLog(this, "Ошибка запуска установщика: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Не удалось запустить установку: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "AwayMode-installer").start();
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        copy(in, out);
        return out.toByteArray();
    }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buffer = new byte[32768];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
    }

    private static int compareVersions(String first, String second) {
        String[] a = first.replaceFirst("^[^0-9]*", "").split("\\.");
        String[] b = second.replaceFirst("^[^0-9]*", "").split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? parseVersionPart(a[i]) : 0;
            int y = i < b.length ? parseVersionPart(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parseVersionPart(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9].*$", "")); }
        catch (Exception e) { return 0; }
    }

    static synchronized void appendLog(Context context, String message) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String current = prefs.getString(LOG, "");
        String[] old = current.isEmpty() ? new String[0] : current.split("\\n");
        StringBuilder next = new StringBuilder();
        int start = Math.max(0, old.length - 119);
        for (int i = start; i < old.length; i++) next.append(old[i]).append('\n');
        next.append(stamp).append("  ").append(message.replace('\n', ' '));
        prefs.edit().putString(LOG, next.toString()).apply();
    }

    private void confirmShareLogs() {
        new AlertDialog.Builder(this)
                .setTitle("Отправить журнал событий?")
                .setMessage("Журнал содержит время событий и имена Wi-Fi сетей. Он не отправится автоматически: дальше выбери приложение и получателя.")
                .setPositiveButton("Продолжить", (dialog, which) -> shareLogs())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void shareLogs() {
        String logs = prefs.getString(LOG, "");
        String report = "AwayMode " + BuildConfig.VERSION_NAME
                + "\nAndroid " + Build.VERSION.RELEASE
                + " · " + Build.MANUFACTURER + " " + Build.MODEL
                + "\n\nЖурнал событий:\n"
                + (logs.isEmpty() ? "(журнал пуст)" : logs);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "AwayMode — журнал событий");
        send.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(send, "Отправить логи разработчику"));
        appendLog(this, "Пользователь открыл меню отправки журнала");
    }

    private void refreshLog() {
        if (logView == null) return;
        String[] lines = prefs.getString(LOG, "").split("\\n");
        StringBuilder visible = new StringBuilder();
        for (int i = Math.max(0, lines.length - 40); i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            if (visible.length() > 0) visible.append('\n');
            visible.append(lines[i]);
        }
        logView.setText(visible.toString());
    }

    @Override protected void onDestroy() {
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(logListener);
        super.onDestroy();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("Уведомление мониторинга")
                    .setMessage("Разреши уведомления: Android использует постоянное уведомление для работающего в фоне мониторинга Wi-Fi.")
                    .setPositiveButton("Разрешить", (d, w) -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS))
                    .setNegativeButton("Позже", (d, w) -> updatePermissionHint())
                    .show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LOCATION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                refreshCurrent(); requestNotificationPermissionIfNeeded();
            } else updatePermissionHint();
        } else if (requestCode == REQ_NOTIFICATIONS) {
            updateAutomationUi();
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "Уведомления разрешены", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasLocationPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void updatePermissionHint() {
        Toast.makeText(this, "Для определения имени Wi-Fi нужно разрешение на местоположение", Toast.LENGTH_LONG).show();
    }

    private void refreshCurrent() {
        currentSsid = readCurrentSsid();
        if (currentLabel != null) currentLabel.setText("Сейчас подключено: " + (currentSsid == null ? "не определено" : currentSsid));
        renderNetworks();
    }

    private String readCurrentSsid() {
        if (!hasLocationPermission() || wifi == null || !hasConnectedWifi()) return null;
        try {
            WifiInfo info = wifi.getConnectionInfo();
            if (info == null) return null;
            return cleanSsid(info.getSSID());
        } catch (Exception e) { return null; }
    }

    private boolean hasConnectedWifi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String cleanSsid(String raw) {
        if (raw == null || raw.equals(WifiManager.UNKNOWN_SSID) || raw.equals("<unknown ssid>")) return null;
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) raw = raw.substring(1, raw.length() - 1);
        return raw.trim().isEmpty() ? null : raw.trim();
    }

    private void addCurrentNetwork() {
        refreshCurrent();
        if (currentSsid == null) {
            Toast.makeText(this, "Сейчас не удалось определить подключённую сеть", Toast.LENGTH_SHORT).show();
            return;
        }
        setMode(currentSsid, "none");
    }

    private void renderNetworks() {
        if (networkRows == null) return;
        networkRows.removeAllViews();
        Map<String, Boolean> all = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String ssid : prefs.getStringSet(KNOWN, Collections.emptySet())) all.putIfAbsent(ssid, Boolean.TRUE);
        for (String ssid : prefs.getStringSet(WHITE, Collections.emptySet())) all.putIfAbsent(ssid, Boolean.TRUE);
        for (String ssid : prefs.getStringSet(BLACK, Collections.emptySet())) all.putIfAbsent(ssid, Boolean.TRUE);
        if (currentSsid != null) all.putIfAbsent(currentSsid, Boolean.TRUE);

        String filter = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Map.Entry<String, Boolean> entry : all.entrySet()) {
            String ssid = entry.getKey();
            if (!ssid.toLowerCase(Locale.ROOT).contains(filter)) continue;
            shown++;
            addNetworkCard(ssid);
        }
        networkRows.setVisibility(shown == 0 ? View.GONE : View.VISIBLE);
    }

    private void addNetworkCard(String ssid) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(0xFF202D40, 13));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(ssid, 14, 0xFFFFFFFF, true);
        heading.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (ssid.equals(currentSsid)) {
            TextView current = text("ПОДКЛЮЧЕНО", 9, 0xFF8FE0B0, true);
            heading.addView(current);
        } else {
            TextView saved = text("ДОБАВЛЕНА", 9, 0xFF9AA8BD, false);
            heading.addView(saved);
        }
        card.addView(heading);

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setPadding(0, dp(8), 0, 0);
        String mode = getMode(ssid);
        addModeButton(choices, ssid, "Не учитывать", "none", mode);
        addModeButton(choices, ssid, "Выключить VPN", "white", mode);
        addModeButton(choices, ssid, "Включить VPN", "black", mode);
        card.addView(choices);

        LinearLayout.LayoutParams p = full();
        p.bottomMargin = dp(7);
        networkRows.addView(card, p);
    }

    private void addModeButton(LinearLayout parent, String ssid, String label, String value, String selected) {
        Button b = button(label);
        b.setTextSize(10);
        boolean active = value.equals(selected);
        b.setTextColor(active ? 0xFFFFFFFF : 0xFFCBD4E2);
        b.setBackground(round(active ? (value.equals("white") ? 0xFF267653 : value.equals("black") ? 0xFF9A5724 : 0xFF526078) : 0xFF2B3A50, 9));
        b.setOnClickListener(v -> setMode(ssid, value));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), 1);
        p.rightMargin = dp(4);
        parent.addView(b, p);
    }

    private String getMode(String ssid) {
        if (prefs.getStringSet(WHITE, Collections.emptySet()).contains(ssid)) return "white";
        if (prefs.getStringSet(BLACK, Collections.emptySet()).contains(ssid)) return "black";
        return "none";
    }

    private void setMode(String ssid, String mode) {
        Set<String> white = new TreeSet<>(prefs.getStringSet(WHITE, Collections.emptySet()));
        Set<String> black = new TreeSet<>(prefs.getStringSet(BLACK, Collections.emptySet()));
        Set<String> known = new TreeSet<>(prefs.getStringSet(KNOWN, Collections.emptySet()));
        known.add(ssid);
        white.remove(ssid); black.remove(ssid);
        if ("white".equals(mode)) white.add(ssid);
        if ("black".equals(mode)) black.add(ssid);
        prefs.edit().putStringSet(KNOWN, known).putStringSet(WHITE, white).putStringSet(BLACK, black).apply();
        String modeName = "white".equals(mode) ? "выключить VPN" : "black".equals(mode) ? "включить VPN" : "не учитывать";
        appendLog(this, "Правило сети «" + ssid + "»: " + modeName);
        renderNetworks();
        Toast.makeText(this, "Настройка сети обновлена", Toast.LENGTH_SHORT).show();
    }

    private void askForSsid() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Точное имя Wi-Fi (SSID)");
        new AlertDialog.Builder(this).setTitle("Добавить сеть")
                .setMessage("После добавления выбери режим у этой сети.")
                .setView(input)
                .setPositiveButton("Добавить", (d, w) -> {
                    String ssid = input.getText().toString().trim();
                    if (ssid.isEmpty()) { Toast.makeText(this, "Имя сети пустое", Toast.LENGTH_SHORT).show(); return; }
                setMode(ssid, "none");
                }).setNegativeButton("Отмена", null).show();
    }

    private void startAutomation() {
        if (!hasLocationPermission()) {
            automationSwitch.setChecked(false);
            requestStartupPermissions();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            automationSwitch.setChecked(false);
            requestNotificationPermissionIfNeeded();
            return;
        }
        prefs.edit().putBoolean(AUTO, true).apply();
        Intent i = new Intent(this, WifiMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        updateAutomationUi();
        Toast.makeText(this, "Мониторинг Wi-Fi запущен", Toast.LENGTH_SHORT).show();
    }

    private void stopAutomation() {
        stopService(new Intent(this, WifiMonitorService.class));
        prefs.edit().putBoolean(AUTO, false).apply();
        updateAutomationUi();
    }

    private void updateAutomationUi() {
        if (automationSwitch == null) return;
        boolean enabled = prefs.getBoolean(AUTO, false);
        automationSwitch.setOnCheckedChangeListener(null);
        automationSwitch.setChecked(enabled);
        automationSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (checked) startAutomation(); else stopAutomation();
        });
        automationLabel.setText(enabled ? "Мониторинг включён" : "Мониторинг выключен");
        automationLabel.setTextColor(enabled ? 0xFF8FE0B0 : 0xFFFFFFFF);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private Button button(String value) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(12);
        b.setTextColor(0xFFFFFFFF); b.setBackground(round(0xFF354967, 10));
        return b;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void add(View view, int top, int bottom) {
        LinearLayout.LayoutParams p = full(); p.topMargin = dp(top); p.bottomMargin = dp(bottom); root.addView(view, p);
    }
    private void card(View view, int top, int bottom) {
        view.setPadding(dp(14), dp(14), dp(14), dp(14)); view.setBackground(round(0xFF202D40, 14)); add(view, top, bottom);
    }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
