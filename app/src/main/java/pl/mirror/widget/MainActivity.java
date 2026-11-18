package pl.mirror.widget;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF161318);
        getWindow().setNavigationBarColor(0xFF161318);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(dp(24), dp(40), dp(24), dp(24));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF161318);

        TextView headline = label("Muzyka Widget", 31, Color.WHITE, true);
        root.addView(headline);
        TextView description = label("Okładka na całym widżecie, nazwa utworu, pasek postępu i przyciski odtwarzania. Bez konta, reklam i wysyłania danych.", 16, 0xFFDFD7D7, false);
        space(root, 13);
        root.addView(description);
        space(root, 25);
        status = label("Sprawdzanie dostępu…", 15, 0xFFF6D2BD, true);
        root.addView(status);
        space(root, 15);

        Button permission = new Button(this);
        permission.setText("1. Zezwól na dostęp do powiadomień");
        permission.setAllCaps(false);
        permission.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });
        root.addView(permission);
        space(root, 10);

        Button pin = new Button(this);
        pin.setText("2. Dodaj widżet na ekran");
        pin.setAllCaps(false);
        pin.setOnClickListener(v -> pinWidget());
        root.addView(pin);
        space(root, 22);

        TextView tip = label("Jeśli dodawanie nie zadziała: przytrzymaj puste miejsce na pulpicie → Widżety → Muzyka Widget. Następnie uruchom utwór w Spotify.", 15, 0xFFBBB0B5, false);
        root.addView(tip);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (status == null) return;
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean granted = enabled != null && enabled.contains(new ComponentName(this, MusicListener.class).flattenToString());
        status.setText(granted ? "✓ Dostęp przyznany" : "● Wymagany dostęp do powiadomień");
        MusicWidget.refreshAll(this);
    }

    private void pinWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, MusicWidget.class);
        if (manager.isRequestPinAppWidgetSupported()) {
            boolean started = manager.requestPinAppWidget(provider, null, null);
            if (!started) Toast.makeText(this, "Dodaj widżet ręcznie z menu pulpitu.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Przytrzymaj pulpit → Widżety → Muzyka Widget", Toast.LENGTH_LONG).show();
        }
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private void space(LinearLayout root, int dp) {
        View gap = new View(this);
        root.addView(gap, new LinearLayout.LayoutParams(1, dp(dp)));
    }
    private int dp(int val) { return (int)(val * getResources().getDisplayMetrics().density + 0.5f); }
}
