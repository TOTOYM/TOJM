package pl.mirror.widget;

import android.app.WallpaperColors;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.SystemClock;
import java.io.InputStream;

/** Inspired by Android 14 SystemUI: original aspect ratio, content colors, radial scrim. */
final class ArtRenderer {
    static final class Artwork {
        final Bitmap cover;
        final int accent, ink, primary, secondary, surface;
        final Bitmap scrim;
        Artwork(Bitmap cover, int seed) {
            this.cover = cover;
            float[] hsv = new float[3];
            Color.colorToHSV(seed, hsv);
            float hue = hsv[0], sat = hsv[1];
            if (sat < .12f) sat = 0f;
            // Public WallpaperColors provides the seed; this tonal palette is a
            // deliberately small approximation of SystemUI's internal Monet CONTENT scheme.
            accent = lightAccent(hue,sat);
            ink = Color.HSVToColor(new float[]{hue, Math.min(.62f, sat * .65f), .16f});
            primary = Color.HSVToColor(new float[]{hue, Math.min(.08f, sat * .12f), 1f});
            secondary = Color.HSVToColor(new float[]{hue, Math.min(.14f, sat * .23f), .87f});
            surface = Color.HSVToColor(new float[]{hue, Math.min(.60f, sat * .65f), .24f});
            scrim = createScrim(surface);
        }
    }

    // Approximate the bright A1 media accent tone with a luminance target.
    // Green/yellow artwork can retain much more chroma than blue artwork.
    private static int lightAccent(float hue,float saturation) {
        float lo=0f,hi=Math.min(.76f,saturation*.95f);
        for(int i=0;i<14;i++){
            float mid=(lo+hi)/2f;
            int c=Color.HSVToColor(new float[]{hue,mid,.985f});
            if(luminance(c)>=.76)lo=mid;else hi=mid;
        }
        return Color.HSVToColor(new float[]{hue,lo,.985f});
    }
    private static double linear(int c) {
        double s=c/255.0;
        return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);
    }
    private static double luminance(int c) {
        return .2126*linear(Color.red(c))+.7152*linear(Color.green(c))+.0722*linear(Color.blue(c));
    }

    private static String cachedKey;
    private static Artwork cached;
    private static long cachedAt;

    static synchronized Artwork get(Context context, MediaMetadata metadata, String player) {
        String key = player + "|" + string(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID)
            + "|" + string(metadata, MediaMetadata.METADATA_KEY_TITLE)
            + "|" + string(metadata, MediaMetadata.METADATA_KEY_ARTIST)
            + "|" + string(metadata, MediaMetadata.METADATA_KEY_ALBUM)
            + "|" + string(metadata, MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            + "|" + string(metadata, MediaMetadata.METADATA_KEY_ART_URI)
            + "|" + string(metadata, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        long now = SystemClock.elapsedRealtime();
        if (cached != null && key.equals(cachedKey)
                && now - cachedAt < (cached.cover == null ? 5000L : 60000L)) return cached;
        Bitmap image = findArtwork(context, metadata);
        if (image == null) image = MusicListener.notificationArtwork(player);
        if (image != null) image = bounded(image, 768);
        int seed = 0xFF777777;
        if (image != null) {
            try {
                Bitmap sample = bounded(image, 112);
                WallpaperColors colors = WallpaperColors.fromBitmap(sample);
                if (colors.getPrimaryColor() != null) seed = colors.getPrimaryColor().toArgb();
            } catch (RuntimeException ignored) { }
        }
        cached = new Artwork(image, seed);
        cachedKey = key;
        cachedAt = now;
        return cached;
    }

    private static String string(MediaMetadata m, String key) {
        return m == null ? "" : String.valueOf(m.getString(key));
    }

    static Bitmap bounded(Bitmap source, int max) {
        if (source == null || source.isRecycled()) return null;
        int w = source.getWidth(), h = source.getHeight();
        if (w < 1 || h < 1) return null;
        if (source.getConfig() == Bitmap.Config.HARDWARE)
            source = source.copy(Bitmap.Config.ARGB_8888, false);
        if (source == null) return null;
        float scale = Math.min(1f, max / (float)Math.max(w, h));
        if (scale == 1f) return source;
        return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(w*scale)),
                Math.max(1, Math.round(h*scale)), true);
    }

    private static Bitmap createScrim(int surface) {
        // Separate overlay: it cannot distort or crop the actual album art.
        int w = 192, h = 96;
        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(w*.54f, h*.45f, w*.76f,
            new int[]{withAlpha(surface, 50), withAlpha(surface, 65), withAlpha(surface, 160)},
            new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,p);
        return result;
    }

    static int withAlpha(int c, int a) { return (c & 0xFFFFFF) | (a << 24); }

    private static Bitmap findArtwork(Context context, MediaMetadata metadata) {
        if (metadata == null) return null;
        // Like SystemUI, try the original content URI before smaller metadata bitmaps.
        String[] keys = {MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI};
        for (String key : keys) {
            String value = metadata.getString(key);
            if (value == null || value.isEmpty()) continue;
            try {
                Uri uri = Uri.parse(value);
                // Only local/provider content. No network permissions or remote requests.
                if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme())) continue;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                    if (stream != null) BitmapFactory.decodeStream(stream, null, o);
                }
                if (o.outWidth <= 0 || o.outHeight <= 0) continue;
                int sample = 1;
                while (Math.max(o.outWidth, o.outHeight) / sample > 1536) sample *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                    if (stream != null) {
                        Bitmap bitmap = BitmapFactory.decodeStream(stream, null, o);
                        if (bitmap != null) return bitmap;
                    }
                }
            } catch (Exception ignored) { /* Player may restrict its provider. */ }
        }
        Bitmap image = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (image == null) image = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (image == null) image = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return image;
    }
}
