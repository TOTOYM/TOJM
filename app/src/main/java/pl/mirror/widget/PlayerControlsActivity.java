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
        } else if("queue".equals(getIntent().getAction())) showQueue();
        else finish();
    }
    private void showQueue(){
        android.media.session.MediaController c=MediaBridge.preferred(this);
        java.util.List<android.media.session.MediaSession.QueueItem> queue=c==null?null:c.getQueue();
        android.app.AlertDialog.Builder b=new android.app.AlertDialog.Builder(this).setTitle("Kolejka odtwarzania").setNegativeButton("Zamknij",null);
        if(queue==null||queue.isEmpty()) b.setMessage("Odtwarzacz nie udostępnia kolejki przez Androida. Otwórz kolejkę bezpośrednio w Spotify.");
        else {
            String[] titles=new String[queue.size()];
            for(int i=0;i<titles.length;i++){android.media.MediaDescription m=queue.get(i).getDescription();titles[i]=String.valueOf(m.getTitle())+(m.getSubtitle()==null?"":" · "+m.getSubtitle());}
            android.media.session.PlaybackState st=c.getPlaybackState();
            boolean canSkip=st!=null&&(st.getActions()&android.media.session.PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM)!=0;
            if(canSkip)b.setItems(titles,(dialog,index)->{try{c.getTransportControls().skipToQueueItem(queue.get(index).getQueueId());}catch(RuntimeException ignored){}});
            else b.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,titles),null);
        }
        android.app.AlertDialog dialog=b.create();dialog.setOnDismissListener(x->finish());dialog.show();
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
