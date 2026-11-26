package ru.awaymode.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class MainActivity extends Activity {
    static final String PREFS = "awaymode_rules";
    static final String WHITE = "white_ssids";
    static final String BLACK = "black_ssids";
    static final String AUTO = "automation_enabled";
    private static final int REQ_LOCATION = 101;
    private static final int REQ_NOTIFICATIONS = 102;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private WifiManager wifi;
    private LinearLayout root, networkRows;
    private EditText search;
    private TextView currentLabel, scanLabel, automationLabel;
    private Switch automationSwitch;
    private String currentSsid;
    private List<ScanResult> scanResults = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
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
            if (hasLocationPermission()) scanNetworks();
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
        TextView note = text("Выбирай режим прямо у каждой сети. В списке — сети рядом и уже добавленные тобой. Полный список сохранённых Android-сетей система сторонним приложениям не открывает.", 12, 0xFF9AA8BD, false);
        add(note, 0, 12);

        LinearLayout scanBar = new LinearLayout(this);
        scanBar.setOrientation(LinearLayout.HORIZONTAL);
        scanBar.setGravity(Gravity.CENTER_VERTICAL);
        Button scan = button("Обновить сети");
        scan.setOnClickListener(v -> scanNetworks());
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

        scanLabel = text("Нажми «Обновить сети», чтобы найти Wi-Fi поблизости", 12, 0xFF9AA8BD, false);
        add(scanLabel, 0, 10);
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

        TextView rules = text("Белый список — отключить INCY. Чёрный список — включить INCY. Не учитывать — ничего не менять. При уходе из белой сети VPN включается через 12 секунд.", 12, 0xFF9AA8BD, false);
        add(rules, 0, 8);
        TextView battery = text("Для стабильной работы на Xiaomi отключи ограничения батареи для AwayMode и разреши автозапуск в настройках HyperOS.", 12, 0xFFFFD28A, false);
        add(battery, 0, 8);
        setContentView(scroll);
    }

    private void requestStartupPermissions() {
        if (!hasLocationPermission()) {
            new AlertDialog.Builder(this)
                    .setTitle("Разрешение для Wi-Fi")
                    .setMessage("AwayMode нужно разрешение на местоположение, чтобы Android разрешил читать имя подключённой сети и находить Wi-Fi поблизости. Геоданные приложение никуда не отправляет.")
                    .setPositiveButton("Разрешить", (d, w) -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION))
                    .setNegativeButton("Пока нет", (d, w) -> updatePermissionHint())
                    .show();
            return;
        }
        requestNotificationPermissionIfNeeded();
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
                refreshCurrent(); scanNetworks(); requestNotificationPermissionIfNeeded();
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
        if (scanLabel != null) scanLabel.setText("Для поиска Wi-Fi и определения текущей сети нужно разрешение на местоположение. Нажми «Обновить сети» после выдачи разрешения.");
    }

    private void refreshCurrent() {
        currentSsid = readCurrentSsid();
        if (currentLabel != null) currentLabel.setText("Сейчас подключено: " + (currentSsid == null ? "не определено" : currentSsid));
        renderNetworks();
    }

    private String readCurrentSsid() {
        if (!hasLocationPermission() || wifi == null) return null;
        try {
            WifiInfo info = wifi.getConnectionInfo();
            if (info == null) return null;
            return cleanSsid(info.getSSID());
        } catch (Exception e) { return null; }
    }

    private String cleanSsid(String raw) {
        if (raw == null || raw.equals(WifiManager.UNKNOWN_SSID) || raw.equals("<unknown ssid>")) return null;
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) raw = raw.substring(1, raw.length() - 1);
        return raw.trim().isEmpty() ? null : raw.trim();
    }

    private void scanNetworks() {
        if (!hasLocationPermission()) {
            requestStartupPermissions();
            return;
        }
        if (wifi == null) { scanLabel.setText("Wi-Fi недоступен на устройстве"); return; }
        if (Build.VERSION.SDK_INT >= 28) {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm != null && !lm.isLocationEnabled()) {
                scanLabel.setText("Включи системную геолокацию — Android без неё не отдаёт результаты Wi-Fi-сканирования.");
                new AlertDialog.Builder(this).setMessage("Для поиска сетей рядом включи геолокацию телефона.")
                        .setPositiveButton("Открыть настройки", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                        .setNegativeButton("Отмена", null).show();
                return;
            }
        }
        scanLabel.setText("Ищем сети… (Android может ограничивать частоту сканирования)");
        try { wifi.startScan(); } catch (SecurityException ignored) {}
        handler.postDelayed(() -> {
            try {
                List<ScanResult> found = wifi.getScanResults();
                scanResults = found == null ? new ArrayList<>() : new ArrayList<>(found);
                scanLabel.setText("Найдено сетей поблизости: " + scanResults.size());
            } catch (SecurityException e) {
                scanLabel.setText("Android не разрешил сканирование. Проверь разрешение местоположения.");
            }
            refreshCurrent();
        }, 1600);
    }

    private void renderNetworks() {
        if (networkRows == null) return;
        networkRows.removeAllViews();
        Map<String, ScanResult> all = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ScanResult result : scanResults) {
            String ssid = cleanSsid(result.SSID);
            if (ssid != null && !ssid.isEmpty()) all.put(ssid, result);
        }
        for (String ssid : prefs.getStringSet(WHITE, Collections.emptySet())) all.putIfAbsent(ssid, null);
        for (String ssid : prefs.getStringSet(BLACK, Collections.emptySet())) all.putIfAbsent(ssid, null);
        if (currentSsid != null) all.putIfAbsent(currentSsid, null);

        String filter = search == null ? "" : search.getText().toString().trim().toLowerCase();
        int shown = 0;
        for (Map.Entry<String, ScanResult> entry : all.entrySet()) {
            String ssid = entry.getKey();
            if (!ssid.toLowerCase().contains(filter)) continue;
            shown++;
            addNetworkCard(ssid, entry.getValue());
        }
        if (shown == 0) {
            TextView empty = text(all.isEmpty() ? "Сети пока не найдены. Нажми «Обновить сети» или введи имя вручную." : "По этому запросу ничего не найдено.", 13, 0xFF9AA8BD, false);
            card(empty, 12, 8);
        }
    }

    private void addNetworkCard(String ssid, ScanResult result) {
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
        } else if (result != null) {
            TextView signal = text(signalLabel(result.level), 10, 0xFF9AA8BD, false);
            heading.addView(signal);
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
        addModeButton(choices, ssid, "Без VPN", "white", mode);
        addModeButton(choices, ssid, "С VPN", "black", mode);
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

    private String signalLabel(int level) {
        if (level >= -55) return "Сигнал отличный";
        if (level >= -67) return "Сигнал хороший";
        if (level >= -75) return "Сигнал слабый";
        return "Далеко";
    }

    private String getMode(String ssid) {
        if (prefs.getStringSet(WHITE, Collections.emptySet()).contains(ssid)) return "white";
        if (prefs.getStringSet(BLACK, Collections.emptySet()).contains(ssid)) return "black";
        return "none";
    }

    private void setMode(String ssid, String mode) {
        Set<String> white = new TreeSet<>(prefs.getStringSet(WHITE, Collections.emptySet()));
        Set<String> black = new TreeSet<>(prefs.getStringSet(BLACK, Collections.emptySet()));
        white.remove(ssid); black.remove(ssid);
        if ("white".equals(mode)) white.add(ssid);
        if ("black".equals(mode)) black.add(ssid);
        prefs.edit().putStringSet(WHITE, white).putStringSet(BLACK, black).apply();
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
