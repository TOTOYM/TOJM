package pl.mirror.widget;

import android.app.Notification;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.List;

/** Reads media notifications only for matching music sessions. No data leaves the device. */
public class MusicListener extends NotificationListenerService {
    private static volatile MusicListener active;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<MediaController> watched=new ArrayList<>();
    private boolean connected;
    private int ticks;
    private final Runnable refresh=()->MusicWidget.refreshAll(this);

    private void changed() {
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh,150L);
    }
    private final MediaController.Callback callback=new MediaController.Callback() {
        @Override public void onMetadataChanged(android.media.MediaMetadata m) { changed(); }
        @Override public void onPlaybackStateChanged(android.media.session.PlaybackState s) { changed(); }
        @Override public void onAudioInfoChanged(MediaController.PlaybackInfo info) { changed(); }
        @Override public void onSessionDestroyed() { handler.post(()->watchSessions()); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged=list->handler.post(()->watchSessions());
    private final Runnable pulse=new Runnable() {
        @Override public void run() {
            if (!connected) return;
            PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
            if (power!=null&&power.isInteractive()&&MusicWidget.hasWidgets(MusicListener.this)) {
                if (++ticks%6==0) MusicWidget.refreshAll(MusicListener.this);
                else MusicWidget.refreshProgress(MusicListener.this);
            }
            handler.postDelayed(this,5000L);
        }
    };
    @Override public void onListenerConnected() {
        super.onListenerConnected(); connected=true; active=this;
        try {
            MediaSessionManager manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
            if (manager!=null) manager.addOnActiveSessionsChangedListener(sessionsChanged,new ComponentName(this,MusicListener.class),handler);
        } catch(SecurityException ignored) { }
        watchSessions(); handler.removeCallbacks(pulse); handler.postDelayed(pulse,5000L);
    }
    @Override public void onListenerDisconnected() {
        connected=false; if(active==this) active=null; dispose(); super.onListenerDisconnected();
    }
    @Override public void onNotificationPosted(StatusBarNotification n) {
        if (isMedia(n)) changed();
    }
    @Override public void onNotificationRemoved(StatusBarNotification n) {
        if (isMedia(n)) changed();
    }
    private static boolean isMedia(StatusBarNotification n) {
        return n!=null && n.getNotification()!=null && n.getNotification().extras!=null
            && n.getNotification().extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) instanceof MediaSession.Token;
    }
    private static Notification notification(MediaController c) {
        MusicListener self=active;
        if (self==null||c==null) return null;
        try {
            StatusBarNotification[] notifications=self.getActiveNotifications();
            if (notifications==null) return null;
            for (StatusBarNotification n:notifications) {
                if (!isMedia(n)||!c.getPackageName().equals(n.getPackageName())) continue;
                Notification item=n.getNotification();
                MediaSession.Token token=item.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
                if (c.getSessionToken().equals(token)) return item;
            }
        } catch(RuntimeException ignored) { }
        return null;
    }
    static Notification.Action[] notificationActions(MediaController c) {
        Notification n=notification(c);
        return n==null||n.actions==null?new Notification.Action[0]:n.actions;
    }
    static Bitmap notificationArtwork(String pkg) {
        MusicListener self=active;
        if(self==null) return null;
        MediaController c=MediaBridge.preferred(self);
        if(c==null||!pkg.equals(c.getPackageName())) return null;
        Notification n=notification(c);
        if(n==null||n.getLargeIcon()==null) return null;
        try {
            Drawable d=n.getLargeIcon().loadDrawable(self);
            if (d==null) return null;
            int w=Math.max(1,d.getIntrinsicWidth()),h=Math.max(1,d.getIntrinsicHeight());
            float scale=Math.min(1f,768f/Math.max(w,h));
            w=Math.max(1,Math.round(w*scale));h=Math.max(1,Math.round(h*scale));
            Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            d.setBounds(0,0,w,h);d.draw(new android.graphics.Canvas(b));return b;
        } catch(RuntimeException ignored) {return null;}
    }
    private void watchSessions() {
        for (MediaController c:watched)try{c.unregisterCallback(callback);}catch(RuntimeException ignored){}
        watched.clear();
        if (!connected) return;
        for (MediaController c:MediaBridge.sessions(this))try{c.registerCallback(callback,handler);watched.add(c);}catch(RuntimeException ignored){}
        changed();
    }
    private void dispose() {
        handler.removeCallbacks(pulse); handler.removeCallbacks(refresh);
        try {
            MediaSessionManager m=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
            if(m!=null)m.removeOnActiveSessionsChangedListener(sessionsChanged);
        }catch(RuntimeException ignored){}
        for(MediaController c:watched)try{c.unregisterCallback(callback);}catch(RuntimeException ignored){}
        watched.clear();
    }
    @Override public void onDestroy() {connected=false;if(active==this)active=null;dispose();super.onDestroy();}
}
