package ru.awaymode.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends Activity {
    static final String PREFS = "awaymode_rules";
    static final String WHITE = "white_ssids";
    static final String BLACK = "black_ssids";
    static final String AUTO = "automation_enabled";
    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout whiteRows;
    private LinearLayout blackRows;
    private TextView currentNetwork;
    private TextView serviceStatus;
    private EditText search;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) refresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        root.setBackgroundColor(0xFF101827);
        scroll.addView(root);

        TextView title = text("AwayMode", 29, 0xFFFFFFFF, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, full());
        TextView subtitle = text("Автоматизация INCY по Wi-Fi", 15, 0xFFB7C2D3, false);
        subtitle.setGravity(Gravity.CENTER);
        addGap(subtitle, 8, 22);

        currentNetwork = text("Текущая сеть: определяем…", 15, 0xFFFFFFFF, true);
        addCard(currentNetwork);
        Button addWhite = button("Добавить текущую сеть → VPN выключен");
        addWhite.setOnClickListener(v -> addCurrent(WHITE));
        addGap(addWhite, 12, 4);
        Button addBlack = button("Добавить текущую сеть → VPN включён");
        addBlack.setOnClickListener(v -> addCurrent(BLACK));
        root.addView(addBlack, full());

        TextView explain = text("Сохранённые Android сети целиком прочитать нельзя. AwayMode запоминает выбранные сети: добавляй текущую одним нажатием или введи имя вручную.", 12, 0xFF9AA8BD, false);
        addGap(explain, 12, 18);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(0xFFFFFFFF);
        search.setHintTextColor(0xFF8A96A8);
        search.setHint("Поиск по твоим спискам");
        search.setBackgroundColor(0xFF253247);
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(search, full());
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderLists(); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        TextView whiteTitle = text("VPN ВЫКЛЮЧЕН", 16, 0xFF8FE0B0, true);
        addGap(whiteTitle, 18, 5);
        TextView whiteHint = text("Белый список — доверенные сети", 12, 0xFFB7C2D3, false);
        root.addView(whiteHint, full());
        whiteRows = new LinearLayout(this); whiteRows.setOrientation(LinearLayout.VERTICAL);
        addGap(whiteRows, 8, 12);

        TextView blackTitle = text("VPN ВКЛЮЧЁН", 16, 0xFFFFC078, true);
        addGap(blackTitle, 14, 5);
        TextView blackHint = text("Чёрный список — сети, где нужен VPN", 12, 0xFFB7C2D3, false);
        root.addView(blackHint, full());
        blackRows = new LinearLayout(this); blackRows.setOrientation(LinearLayout.VERTICAL);
        addGap(blackRows, 8, 18);

        Button manualWhite = button("+ Ввести сеть в белый список");
        manualWhite.setOnClickListener(v -> askForSsid(WHITE));
        root.addView(manualWhite, full());
        Button manualBlack = button("+ Ввести сеть в чёрный список");
        manualBlack.setOnClickListener(v -> askForSsid(BLACK));
        addGap(manualBlack, 8, 18);

        serviceStatus = text("Автоматизация выключена", 14, 0xFFFFFFFF, true);
        addCard(serviceStatus);
        Button start = button("Включить автоматизацию");
        start.setOnClickListener(v -> startAutomation());
        addGap(start, 12, 5);
        Button stop = button("Остановить автоматизацию");
        stop.setOnClickListener(v -> stopService(new Intent(this, WifiMonitorService.class)));
        root.addView(stop, full());

        TextView rules = text("Правила: белый список → отключить INCY; чёрный список → подключить INCY; неизвестная сеть → ничего не менять. Если отключился от белой сети, через 12 секунд включить INCY. При выходе из чёрной сети состояние не меняем.", 12, 0xFF9AA8BD, false);
        addGap(rules, 18, 12);
        TextView caveat = text("Это тест фонового режима. Android/HyperOS может блокировать запуск INCY из фона. Если команда не сработает автоматически, проверим уведомление и ограничения системы. Держи AwayMode без ограничений батареи и разреши автозапуск, если он доступен.", 12, 0xFFFFD28A, false);
        root.addView(caveat, full());

        setContentView(scroll);
    }

    private void refresh() {
        String ssid = getCurrentSsid();
        currentNetwork.setText("Текущая сеть: " + (ssid == null ? "не определена (нет Wi-Fi или нет разрешения)" : ssid));
        serviceStatus.setText(prefs.getBoolean(AUTO, false) ? "Автоматизация включена (состояние сохранено)" : "Автоматизация выключена");
        renderLists();
    }

    private void renderLists() {
        if (whiteRows == null || blackRows == null) return;
        whiteRows.removeAllViews(); blackRows.removeAllViews();
        String filter = search == null ? "" : search.getText().toString().trim().toLowerCase();
        addRows(whiteRows, WHITE, filter);
        addRows(blackRows, BLACK, filter);
    }

    private void addRows(LinearLayout target, String key, String filter) {
        List<String> items = new ArrayList<>(prefs.getStringSet(key, Collections.emptySet()));
        Collections.sort(items, String.CASE_INSENSITIVE_ORDER);
        boolean shown = false;
        for (String ssid : items) {
            if (!ssid.toLowerCase().contains(filter)) continue;
            shown = true;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(5), dp(4), dp(5));
            row.setBackgroundColor(0xFF202D40);
            TextView name = text(ssid, 14, 0xFFFFFFFF, false);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button remove = button("Удалить");
            remove.setTextSize(12);
            remove.setOnClickListener(v -> { removeSsid(key, ssid); renderLists(); });
            row.addView(remove);
            LinearLayout.LayoutParams p = full(); p.bottomMargin = dp(5);
            target.addView(row, p);
        }
        if (!shown) {
            TextView empty = text(items.isEmpty() ? "Пока пусто" : "Ничего не найдено", 12, 0xFF8A96A8, false);
            target.addView(empty, full());
        }
    }

    private void addCurrent(String targetList) {
        String ssid = getCurrentSsid();
        if (ssid == null) {
            Toast.makeText(this, "Не удалось прочитать текущую сеть. Проверь разрешение геолокации и включение геолокации телефона.", Toast.LENGTH_LONG).show();
            requestLocationPermission();
            return;
        }
        addSsid(targetList, ssid);
    }

    private void askForSsid(String targetList) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Точное имя Wi-Fi (SSID)");
        new android.app.AlertDialog.Builder(this)
                .setTitle(targetList.equals(WHITE) ? "Добавить доверенную сеть" : "Добавить сеть с VPN")
                .setView(input)
                .setPositiveButton("Добавить", (d, w) -> addSsid(targetList, input.getText().toString()))
                .setNegativeButton("Отмена", null).show();
    }

    private void addSsid(String targetList, String raw) {
        String ssid = raw == null ? "" : raw.trim();
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) ssid = ssid.substring(1, ssid.length()-1);
        if (ssid.isEmpty() || ssid.equals("<unknown ssid>")) { Toast.makeText(this, "Имя сети не распознано", Toast.LENGTH_SHORT).show(); return; }
        String other = targetList.equals(WHITE) ? BLACK : WHITE;
        Set<String> otherSet = new TreeSet<>(prefs.getStringSet(other, Collections.emptySet()));
        otherSet.remove(ssid);
        Set<String> target = new TreeSet<>(prefs.getStringSet(targetList, Collections.emptySet()));
        target.add(ssid);
        prefs.edit().putStringSet(other, otherSet).putStringSet(targetList, target).apply();
        renderLists();
        Toast.makeText(this, "Сеть добавлена", Toast.LENGTH_SHORT).show();
    }

    private void removeSsid(String key, String ssid) {
        Set<String> items = new TreeSet<>(prefs.getStringSet(key, Collections.emptySet()));
        items.remove(ssid);
        prefs.edit().putStringSet(key, items).apply();
    }

    private String getCurrentSsid() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null) return null;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return null;
            String s = info.getSSID();
            if (s == null || s.equals(WifiManager.UNKNOWN_SSID) || s.equals("<unknown ssid>")) return null;
            if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length()-1);
            return s;
        } catch (Exception e) { return null; }
    }

    private void startAutomation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission(); return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 102); return;
        }
        prefs.edit().putBoolean(AUTO, true).apply();
        Intent i = new Intent(this, WifiMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "Мониторинг Wi-Fi запущен", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 101 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) refresh();
        if (requestCode == 102 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startAutomation();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(14); return b; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private void addGap(View v, int top, int bottom) { LinearLayout.LayoutParams p = full(); p.topMargin=dp(top); p.bottomMargin=dp(bottom); root.addView(v,p); }
    private void addCard(View v) { v.setPadding(dp(14),dp(14),dp(14),dp(14)); v.setBackgroundColor(0xFF202D40); addGap(v,14,4); }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
}
