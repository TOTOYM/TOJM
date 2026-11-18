package pl.mirror.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.net.Uri;

import java.io.InputStream;

/** Rounded artwork background with a dark, legible scrim. */
final class ArtRenderer {
    private ArtRenderer() {}

    static Bitmap render(Context context, MediaMetadata meta, int width, int height) {
        width = Math.max(320, Math.min(720, width));
        height = Math.max(170, Math.min(400, height));
        Bitmap target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float radius = 26f * context.getResources().getDisplayMetrics().density;
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, width, height), Math.min(radius, height / 3f), Math.min(radius, height / 3f), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        Bitmap artwork = findArtwork(context, meta);
        if (artwork != null && artwork.getWidth() > 0 && artwork.getHeight() > 0) {
            float cropRatio = Math.min(artwork.getWidth() / (float) width, artwork.getHeight() / (float) height);
            int sourceW = Math.max(1, Math.min(artwork.getWidth(), Math.round(width * cropRatio)));
            int sourceH = Math.max(1, Math.min(artwork.getHeight(), Math.round(height * cropRatio)));
            int x = (artwork.getWidth() - sourceW) / 2;
            int y = (artwork.getHeight() - sourceH) / 2;
            canvas.drawBitmap(artwork, new Rect(x, y, x + sourceW, y + sourceH), new Rect(0, 0, width, height), paint);
            paint.setColor(Color.argb(50, 0, 0, 0));
            paint.setShader(null);
            canvas.drawRect(0, 0, width, height, paint);
        } else {
            paint.setShader(new LinearGradient(0, 0, width, height, new int[]{0xFF70482C, 0xFF4B3443, 0xFF22212A}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(null);
        }

        paint.setShader(new LinearGradient(0, 0, 0, height, new int[]{0x16000000, 0x38000000, 0xC9000000}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(new LinearGradient(0, 0, width, 0, 0x6C000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        canvas.restore();
        return target;
    }

    private static Bitmap findArtwork(Context context, MediaMetadata meta) {
        if (meta == null) return null;
        Bitmap bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bitmap == null) bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bitmap == null) bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (bitmap != null) return bitmap;

        String[] keys = {MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI};
        for (String key : keys) {
            String uriString = meta.getString(key);
            if (uriString == null) continue;
            try {
                Uri uri = Uri.parse(uriString);
                if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme())) continue;
                try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                    if (stream != null) {
                        Bitmap loaded = BitmapFactory.decodeStream(stream);
                        if (loaded != null) return loaded;
                    }
                }
            } catch (Exception ignored) { /* Some players hide their artwork URIs. */ }
        }
        return null;
    }
}
