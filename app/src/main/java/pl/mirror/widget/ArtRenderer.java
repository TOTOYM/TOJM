package pl.mirror.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.net.Uri;
import java.io.InputStream;

/**
 * Softly filled album art without aspect-ratio distortion. The rounded mask is
 * applied to the actual bitmap pixels, producing symmetrical anti-aliased corners.
 */
final class ArtRenderer {
    private ArtRenderer() {}

    static Bitmap render(Context context, MediaMetadata meta, int requestedWidth, int requestedHeight) {
        int inputW = Math.max(1, requestedWidth);
        int inputH = Math.max(1, requestedHeight);
        // Never clamp width and height independently (that would warp the cover).
        float outputScale = Math.min(1f, 1150f / Math.max(inputW, inputH));
        int width = Math.max(100, Math.round(inputW * outputScale));
        int height = Math.max(80, Math.round(inputH * outputScale));
        float density = context.getResources().getDisplayMetrics().density * outputScale;

        Bitmap target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap artwork = findArtwork(context, meta);

        if (artwork != null && artwork.getWidth() > 0 && artwork.getHeight() > 0) {
            // Slightly defocused full-bleed backdrop fills the wide widget.
            // It is derived from the same cover, with no artificial stretching.
            Bitmap small = Bitmap.createScaledBitmap(artwork, 56, 56, true);
            float backdropScale = Math.max(width / (float) small.getWidth(),
                    height / (float) small.getHeight());
            float backdropW = small.getWidth() * backdropScale;
            float backdropH = small.getHeight() * backdropScale;
            canvas.drawBitmap(small, null, new RectF(
                    (width - backdropW) * 0.5f,
                    (height - backdropH) * 0.5f,
                    (width + backdropW) * 0.5f,
                    (height + backdropH) * 0.5f), paint);
            if (small != artwork) small.recycle();

            paint.setShader(null);
            paint.setColor(0x32000000);
            canvas.drawRect(0, 0, width, height, paint);
            paint.setColor(Color.WHITE);

            // Most of the square cover remains visible in the center.
            // Soft horizontal fades blend its edges with the backdrop.
            float scale = Math.min(width * 1.05f / artwork.getWidth(),
                    height * 1.20f / artwork.getHeight());
            float coverW = artwork.getWidth() * scale;
            float coverH = artwork.getHeight() * scale;
            float left = (width - coverW) / 2f;
            float right = left + coverW;
            int foregroundLayer = canvas.saveLayer(0f, 0f, width, height, null);
            canvas.drawBitmap(artwork, null, new RectF(left, (height - coverH) / 2f,
                    right, (height + coverH) / 2f), paint);

            // This shader acts as an alpha mask over the foreground only.
            Paint feather = new Paint(Paint.ANTI_ALIAS_FLAG);
            feather.setShader(new LinearGradient(left, 0, right, 0,
                    new int[]{0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.16f, 0.84f, 1f}, Shader.TileMode.CLAMP));
            feather.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            canvas.drawRect(left, 0, right, height, feather);
            feather.setXfermode(null);
            canvas.restoreToCount(foregroundLayer);
        } else {
            paint.setShader(new LinearGradient(0, 0, width, height,
                    new int[]{0xFF655039, 0xFF493736, 0xFF221E25},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
        }

        // Keep text and playback controls legible without washing out the cover.
        paint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{0x07000000, 0x15000000, 0xB9000000},
                new float[]{0f, 0.43f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(new LinearGradient(0, 0, width, 0,
                0x42000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        // Round ALL four corners with the same antialiased radius.
        float corner = Math.min(33f * density, height * 0.28f);
        makeSmoothCorners(target, Math.max(1f, corner));
        return target;
    }

    private static void makeSmoothCorners(Bitmap bitmap, float radius) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int n = Math.min((int) Math.ceil(radius), Math.min(width, height) / 2);
        if (n <= 0) return;
        int[] pixels = new int[n * n];
        for (int corner = 0; corner < 4; corner++) {
            boolean left = (corner == 0 || corner == 2);
            boolean top = (corner == 0 || corner == 1);
            int x0 = left ? 0 : width - n;
            int y0 = top ? 0 : height - n;
            bitmap.getPixels(pixels, 0, n, x0, y0, n, n);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    float fromLeft = left ? x + 0.5f : n - x - 0.5f;
                    float fromTop = top ? y + 0.5f : n - y - 0.5f;
                    float dx = Math.max(0f, radius - fromLeft);
                    float dy = Math.max(0f, radius - fromTop);
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    float coverage = Math.max(0f, Math.min(1f, radius + 0.5f - distance));
                    int index = y * n + x;
                    int argb = pixels[index];
                    int alpha = Math.round(((argb >>> 24) & 0xFF) * coverage);
                    pixels[index] = (argb & 0x00FFFFFF) | (alpha << 24);
                }
            }
            bitmap.setPixels(pixels, 0, n, x0, y0, n, n);
        }
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
