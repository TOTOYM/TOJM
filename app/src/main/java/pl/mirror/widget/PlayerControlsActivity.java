package pl.mirror.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.MediaRouter2;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** An actual interactive seek bar / public output-switcher entry from a RemoteViews widget. */
public class PlayerControlsActivity extends Activity {
    private boolean output, opened;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        output="output".equals(getIntent().getAction());
        if (output) {
            TextView view=new TextView(this);
            view.setText("Wybór urządzenia audio");
            setContentView(view);
        } else showSeek();
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
    private void showSeek() {
        MediaController c=MediaBridge.preferred(this);
        MediaMetadata m=c==null?null:c.getMetadata();
        PlaybackState s=c==null?null:c.getPlaybackState();
        long duration=m==null?0:m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (c==null || s==null || duration<=0 || (s.getActions()&PlaybackState.ACTION_SEEK_TO)==0) {
            Toast.makeText(this,"Odtwarzacz nie udostępnia przewijania tego utworu.",Toast.LENGTH_SHORT).show();
            finish(); return;
        }
        MediaSession.Token token=c.getSessionToken();
        String track=identity(m);
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(24*getResources().getDisplayMetrics().density);
        panel.setPadding(pad,pad,pad,pad);
        TextView times=new TextView(this);
        SeekBar seek=new SeekBar(this);
        seek.setMax(1000);
        seek.setProgress(MediaBridge.progress(c));
        times.setText(time(duration*seek.getProgress()/1000L)+"  /  "+time(duration));
        panel.addView(times); panel.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int value,boolean user) {
                times.setText(time(duration*value/1000L)+"  /  "+time(duration));
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(m.getString(MediaMetadata.METADATA_KEY_TITLE))
            .setView(panel).setNegativeButton("Anuluj",null)
            .setPositiveButton("Przewiń",(d,which)-> {
                MediaController current=MediaBridge.preferred(this);
                if (current!=null && token.equals(current.getSessionToken())
                        && track.equals(identity(current.getMetadata()))) {
                    try { current.getTransportControls().seekTo(duration*seek.getProgress()/1000L); }
                    catch (RuntimeException ignored) { }
                }
                MusicWidget.refreshAll(this);
            }).create();
        dialog.setOnDismissListener(d->finish());
        dialog.show();
    }
    private static String identity(MediaMetadata m) {
        if (m==null) return "";
        return m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)+"|"+m.getString(MediaMetadata.METADATA_KEY_TITLE)+"|"+m.getString(MediaMetadata.METADATA_KEY_ARTIST);
    }
    private static String time(long ms) {
        long sec=ms/1000;
        return String.format(Locale.ROOT,"%d:%02d",sec/60,sec%60);
    }
}
