package pl.mirror.widget;

import android.content.ComponentName;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/** System notification listener only uses media-session metadata; nothing is sent online. */
public class MusicListener extends NotificationListenerService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> watched = new ArrayList<>();
    private boolean connected;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(android.media.MediaMetadata metadata) { MusicWidget.refreshAll(MusicListener.this); }
        @Override public void onPlaybackStateChanged(android.media.session.PlaybackState state) { MusicWidget.refreshAll(MusicListener.this); }
        @Override public void onSessionDestroyed() { handler.post(() -> watchSessions()); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = controllers -> handler.post(() -> watchSessions());
    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            if (!connected) return;
            MusicWidget.refreshAll(MusicListener.this);
            handler.postDelayed(this, 15000L);
        }
    };

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (manager != null) manager.addOnActiveSessionsChangedListener(changed, new ComponentName(this, MusicListener.class), handler);
        } catch (SecurityException ignored) { }
        watchSessions();
        handler.removeCallbacks(pulse);
        handler.postDelayed(pulse, 15000L);
    }

    @Override public void onListenerDisconnected() {
        connected = false;
        dispose();
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (notification != null && "com.spotify.music".equals(notification.getPackageName())) {
            MusicWidget.refreshAll(this);
        }
    }

    @Override public void onNotificationRemoved(StatusBarNotification notification) {
        if (notification != null && "com.spotify.music".equals(notification.getPackageName())) {
            MusicWidget.refreshAll(this);
        }
    }

    private void watchSessions() {
        for (MediaController media : watched) {
            try { media.unregisterCallback(callback); } catch (RuntimeException ignored) { }
        }
        watched.clear();
        if (!connected) return;
        for (MediaController media : MediaBridge.sessions(this)) {
            try {
                media.registerCallback(callback, handler);
                watched.add(media);
            } catch (RuntimeException ignored) { }
        }
        MusicWidget.refreshAll(this);
    }

    private void dispose() {
        handler.removeCallbacks(pulse);
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (manager != null) manager.removeOnActiveSessionsChangedListener(changed);
        } catch (RuntimeException ignored) { }
        for (MediaController media : watched) {
            try { media.unregisterCallback(callback); } catch (RuntimeException ignored) { }
        }
        watched.clear();
    }

    @Override public void onDestroy() {
        connected = false;
        dispose();
        super.onDestroy();
    }
}
