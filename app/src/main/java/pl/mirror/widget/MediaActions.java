package pl.mirror.widget;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Display real player-supplied actions, rather than inventing Spotify commands. */
final class MediaActions {
    static final class Item {
        final CharSequence name;
        final Bitmap icon;
        final String customAction;
        final PendingIntent notificationAction;
        Item(CharSequence name, Bitmap icon, String customAction, PendingIntent notificationAction) {
            this.name=name; this.icon=icon; this.customAction=customAction;
            this.notificationAction=notificationAction;
        }
    }
    static List<Item> get(Context context, MediaController controller, int tint) {
        List<Item> out = new ArrayList<>();
        if (controller == null) return out;
        PlaybackState state = controller.getPlaybackState();
        if (state != null) {
            for (PlaybackState.CustomAction action : state.getCustomActions()) {
                if (out.size() == 2) break;
                try {
                    Context source = context.createPackageContext(controller.getPackageName(),0);
                    Drawable icon = source.getDrawable(action.getIcon());
                    Bitmap image = bitmap(icon,tint);
                    if (image != null) out.add(new Item(action.getName(),image,action.getAction(),null));
                } catch (Exception ignored) { }
            }
        }
        // Older players may advertise extra controls only through their media notification.
        for (Notification.Action action : MusicListener.notificationActions(controller)) {
            if (out.size() == 2) break;
            if (action == null || action.title == null || action.actionIntent == null || action.getIcon() == null) continue;
            String name=action.title.toString().toLowerCase(Locale.ROOT);
            if (!name.matches(".*(shuffle|repeat|like|library|save|favorite|favourite|losow|powt|polub|bibliotek|ulub|zapis|dodaj|usuń).*")) continue;
            boolean duplicate=false;
            for (Item item : out) if (name.equals(item.name.toString().toLowerCase(Locale.ROOT))) duplicate=true;
            if (duplicate) continue;
            try {
                Bitmap icon=bitmap(action.getIcon().loadDrawable(context),tint);
                if (icon != null) out.add(new Item(action.title,icon,null,action.actionIntent));
            } catch (RuntimeException ignored) { }
        }
        return out;
    }
    static Bitmap bitmap(Drawable drawable, Integer tint) {
        if (drawable == null) return null;
        drawable=drawable.mutate();
        if (tint != null) drawable.setTint(tint);
        Bitmap image=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
        drawable.setBounds(0,0,96,96);
        drawable.draw(new Canvas(image));
        return image;
    }
}
