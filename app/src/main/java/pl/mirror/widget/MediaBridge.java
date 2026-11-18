package pl.mirror.widget;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;

import java.util.Collections;
import java.util.List;

/** Reads Android's public media-session API; no Spotify credentials required. */
final class MediaBridge {
    private MediaBridge() {}

    static List<MediaController> sessions(Context context) {
        try {
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return Collections.emptyList();
            return manager.getActiveSessions(new ComponentName(context, MusicListener.class));
        } catch (SecurityException | IllegalStateException error) {
            return Collections.emptyList();
        }
    }

    static MediaController preferred(Context context) {
        List<MediaController> controllers = sessions(context);
        MediaController spotify = null;
        MediaController playingOther = null;
        MediaController other = null;
        for (MediaController controller : controllers) {
            if (controller == null) continue;
            boolean spotifyApp = "com.spotify.music".equals(controller.getPackageName());
            PlaybackState state = controller.getPlaybackState();
            boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
            if (spotifyApp && playing) return controller;
            if (spotifyApp) spotify = controller;
            else if (playing && playingOther == null) playingOther = controller;
            else if (other == null) other = controller;
        }
        return spotify != null ? spotify : (playingOther != null ? playingOther : other);
    }


    /** Bluetooth names are best-effort; some devices mask the real routing information. */
    static String outputDevice(Context context) {
        try {
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager == null) return "Telefon";
            String wired = null;
            for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || (android.os.Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET)) {
                    CharSequence label = device.getProductName();
                    if (label != null && label.length() > 0 && !"Bluetooth".contentEquals(label)) return label.toString();
                    return "Słuchawki BT";
                }
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || type == AudioDeviceInfo.TYPE_USB_HEADSET) wired = "Słuchawki";
            }
            return wired != null ? wired : "Telefon";
        } catch (RuntimeException ignored) {
            return "Telefon";
        }
    }

    static boolean isPlaying(MediaController controller) {
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }

    static int progress(MediaController controller) {
        if (controller == null) return 0;
        MediaMetadata metadata = controller.getMetadata();
        PlaybackState state = controller.getPlaybackState();
        if (metadata == null || state == null) return 0;
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (duration <= 0L) return 0;
        long position = Math.max(0, state.getPosition());
        if (state.getState() == PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime() > 0L) {
            long delta = Math.max(0, SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime());
            position += (long) (delta * state.getPlaybackSpeed());
        }
        return (int) Math.max(0, Math.min(1000, (position * 1000L) / duration));
    }
}
