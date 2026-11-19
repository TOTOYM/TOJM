package pl.mirror.widget;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaRouter2;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

/** Public system audio output switcher. Seeking is handled in the widget itself. */
public class PlayerControlsActivity extends Activity {
    private boolean output, opened;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        output="output".equals(getIntent().getAction());
        if (output) {
            TextView view=new TextView(this);
            view.setText("Wybór urządzenia audio");
            setContentView(view);
        } else finish();
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!output || !focus || opened) return;
        opened=true;
        boolean shown=false;
        if (Build.VERSION.SDK_INT>=34) {
            try { shown=MediaRouter2.getInstance(this).showSystemOutputSwitcher(); }
            catch (RuntimeException ignored) { }
        }
        if (!shown) {
            try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
            catch (RuntimeException ignored) { }
        }
        finish();
    }
}
