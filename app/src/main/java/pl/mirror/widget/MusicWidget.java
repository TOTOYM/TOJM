package pl.mirror.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.RemoteViews;

/** System MediaSession driven Android home widget. */
public class MusicWidget extends AppWidgetProvider {
    static final String PREVIOUS = "pl.mirror.widget.PREVIOUS";
    static final String TOGGLE = "pl.mirror.widget.TOGGLE";
    static final String NEXT = "pl.mirror.widget.NEXT";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        super.onAppWidgetOptionsChanged(context, manager, id, options);
        updateOne(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!PREVIOUS.equals(action) && !TOGGLE.equals(action) && !NEXT.equals(action)) return;
        MediaController controller = MediaBridge.preferred(context);
        if (controller == null) return;
        try {
            MediaController.TransportControls controls = controller.getTransportControls();
            if (PREVIOUS.equals(action)) controls.skipToPrevious();
            if (NEXT.equals(action)) controls.skipToNext();
            if (TOGGLE.equals(action)) {
                if (MediaBridge.isPlaying(controller)) controls.pause();
                else controls.play();
            }
        } catch (RuntimeException ignored) { }
        refreshAll(context);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(new ComponentName(context, MusicWidget.class))) updateOne(context, manager, id);
    }

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        MediaController controller = MediaBridge.preferred(context);
        MediaMetadata meta = controller == null ? null : controller.getMetadata();
        Bundle options = manager.getAppWidgetOptions(id);
        int widthDp = options == null ? 330 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 330);
        int heightDp = options == null ? 145 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 145);
        if (widthDp < 100) widthDp = 330;
        if (heightDp < 70) heightDp = 145;
        float density = context.getResources().getDisplayMetrics().density;
        boolean compact = heightDp < 180;
        RemoteViews view = new RemoteViews(context.getPackageName(), compact ? R.layout.widget_player_compact : R.layout.widget_player);
        view.setImageViewBitmap(R.id.art_background, ArtRenderer.render(context, meta,
                Math.round(widthDp * density), Math.round(heightDp * density)));

        String title = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.isEmpty()) title = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String artist = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null || artist.isEmpty()) artist = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if (artist == null || artist.isEmpty()) artist = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        view.setTextViewText(R.id.song, title == null || title.isEmpty() ? "Włącz muzykę" : title);
        view.setTextViewText(R.id.artist, artist == null || artist.isEmpty() ? "Otwórz Spotify" : artist);
        view.setTextViewText(R.id.route_name, MediaBridge.outputDevice(context));
        view.setImageViewResource(R.id.toggle, MediaBridge.isPlaying(controller) ? R.drawable.ic_pause : R.drawable.ic_play);
        view.setImageViewBitmap(R.id.wave_progress, WaveRenderer.draw(MediaBridge.progress(controller),
            Math.round(Math.max(65, widthDp - (compact ? 140 : 180)) * density), Math.round((compact ? 24 : 29) * density), density));

        view.setOnClickPendingIntent(R.id.prev, command(context, id, PREVIOUS, 1));
        view.setOnClickPendingIntent(R.id.toggle, command(context, id, TOGGLE, 2));
        view.setOnClickPendingIntent(R.id.next, command(context, id, NEXT, 3));
        PendingIntent spotify = spotify(context, id);
        view.setOnClickPendingIntent(R.id.spotify_logo, spotify);
        view.setOnClickPendingIntent(R.id.shuffle, spotify);
        view.setOnClickPendingIntent(R.id.liked, spotify);
        view.setOnClickPendingIntent(R.id.route_chip, deviceSettings(context, id));
        manager.updateAppWidget(id, view);
    }

    private static PendingIntent command(Context context, int id, String action, int index) {
        Intent intent = new Intent(context, MusicWidget.class).setAction(action);
        return PendingIntent.getBroadcast(context, id * 20 + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent spotify(Context context, int id) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage("com.spotify.music");
        if (intent == null) intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://open.spotify.com"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, id * 20 + 10, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent deviceSettings(Context context, int id) {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, id * 20 + 11, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
