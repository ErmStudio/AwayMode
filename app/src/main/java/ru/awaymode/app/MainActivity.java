package ru.awaymode.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(16, 24, 39));

        TextView title = new TextView(this);
        title.setText("AwayMode");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Проверка управления INCY через deep links");
        subtitle.setTextColor(Color.rgb(183, 194, 211));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(10);
        subParams.bottomMargin = dp(36);
        root.addView(subtitle, subParams);

        Button connect = makeButton("Подключить INCY");
        connect.setOnClickListener(v -> sendCommand("incy://connect", "Команда подключения передана Android"));
        root.addView(connect, matchWrap());

        Button disconnect = makeButton("Отключить INCY");
        LinearLayout.LayoutParams disParams = matchWrap();
        disParams.topMargin = dp(14);
        disconnect.setOnClickListener(v -> sendCommand("incy://disconnect", "Команда отключения передана Android"));
        root.addView(disconnect, disParams);

        status = new TextView(this);
        status.setText("Готов к проверке. Нажми одну из кнопок.");
        status.setTextColor(Color.rgb(210, 220, 235));
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(28), dp(12), dp(12));
        root.addView(status, matchWrap());

        TextView note = new TextView(this);
        note.setText("Это тест отправки команды. Само по себе сообщение об успехе ещё не доказывает, что VPN изменил состояние — проверь значок/статус INCY.");
        note.setTextColor(Color.rgb(145, 158, 178));
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(24);
        root.addView(note, noteParams);

        setContentView(root);
    }

    private void sendCommand(String uri, String successMessage) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            startActivity(intent);
            status.setText(successMessage + ". Теперь проверь состояние VPN в INCY.");
        } catch (ActivityNotFoundException e) {
            status.setText("Android не нашёл приложение, которое обрабатывает " + uri + ". Проверь, что установлен именно INCY.");
        } catch (Exception e) {
            status.setText("Не удалось отправить команду: " + e.getClass().getSimpleName());
        }
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setMinHeight(dp(58));
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
