package pl.mirror.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.widget.RemoteViews;

public class MusicWidget extends AppWidgetProvider {
    static final String PREVIOUS = "pl.mirror.widget.PREVIOUS";
    static final String TOGGLE = "pl.mirror.widget.TOGGLE";
    static final String NEXT = "pl.mirror.widget.NEXT";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int id : widgetIds) updateOne(context, manager, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
        updateOne(context, manager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!PREVIOUS.equals(action) && !NEXT.equals(action) && !TOGGLE.equals(action)) return;
        MediaController controller = MediaBridge.preferred(context);
        if (controller != null) {
            try {
                MediaController.TransportControls controls = controller.getTransportControls();
                if (PREVIOUS.equals(action)) controls.skipToPrevious();
                if (NEXT.equals(action)) controls.skipToNext();
                if (TOGGLE.equals(action)) {
                    if (MediaBridge.isPlaying(controller)) controls.pause();
                    else controls.play();
                }
            } catch (RuntimeException ignored) { /* Playback session may have just ended. */ }
        }
        refreshAll(context);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, MusicWidget.class));
        for (int id : ids) updateOne(context, manager, id);
    }

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        MediaController controller = MediaBridge.preferred(context);
        MediaMetadata meta = controller == null ? null : controller.getMetadata();
        Bundle options = manager.getAppWidgetOptions(id);
        int widthDp = options == null ? 330 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 330);
        int heightDp = options == null ? 120 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120);
        if (widthDp <= 0) widthDp = 330;
        if (heightDp <= 0) heightDp = 120;
        float density = context.getResources().getDisplayMetrics().density;

        RemoteViews view = new RemoteViews(context.getPackageName(), heightDp < 165 ? R.layout.widget_player_compact : R.layout.widget_player);
        view.setImageViewBitmap(R.id.art_background, ArtRenderer.render(context, meta, Math.round(widthDp * density), Math.round(heightDp * density)));

        String title = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.isEmpty()) title = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String artist = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null || artist.isEmpty()) artist = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if (artist == null || artist.isEmpty()) artist = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        view.setTextViewText(R.id.song, title == null || title.isEmpty() ? "Włącz muzykę" : title);
        view.setTextViewText(R.id.artist, artist == null || artist.isEmpty() ? "Otwórz Spotify i odtwórz utwór" : artist);
        view.setTextViewText(R.id.source, controller != null && "com.spotify.music".equals(controller.getPackageName()) ? "♫  SPOTIFY" : "♫  MUZYKA");
        view.setImageViewResource(R.id.toggle, MediaBridge.isPlaying(controller) ? R.drawable.ic_pause : R.drawable.ic_play);
        view.setProgressBar(R.id.progress, 1000, MediaBridge.progress(controller), false);
        view.setOnClickPendingIntent(R.id.prev, command(context, id, PREVIOUS, 0));
        view.setOnClickPendingIntent(R.id.toggle, command(context, id, TOGGLE, 1));
        view.setOnClickPendingIntent(R.id.next, command(context, id, NEXT, 2));
        manager.updateAppWidget(id, view);
    }

    private static PendingIntent command(Context context, int id, String action, int index) {
        Intent intent = new Intent(context, MusicWidget.class).setAction(action);
        return PendingIntent.getBroadcast(context, id * 10 + index, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
