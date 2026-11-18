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
 * Full-bleed center-cropped album art without distortion or blur. The rounded mask is
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
            // Center-crop the original album art to the exact aspect ratio of the
            // widget. This fills the entire surface without any blurring, dark
            // sidebars or non-uniform stretching.
            int srcWidth = artwork.getWidth();
            int srcHeight = artwork.getHeight();
            float targetAspect = width / (float) height;
            float artAspect = srcWidth / (float) srcHeight;
            Rect crop;
            if (artAspect > targetAspect) {
                int visibleWidth = Math.max(1, Math.min(srcWidth, Math.round(srcHeight * targetAspect)));
                int left = (srcWidth - visibleWidth) / 2;
                crop = new Rect(left, 0, left + visibleWidth, srcHeight);
            } else {
                int visibleHeight = Math.max(1, Math.min(srcHeight, Math.round(srcWidth / targetAspect)));
                int top = (srcHeight - visibleHeight) / 2;
                crop = new Rect(0, top, srcWidth, top + visibleHeight);
            }
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            canvas.drawBitmap(artwork, crop, new Rect(0, 0, width, height), paint);
        } else {
            paint.setShader(new LinearGradient(0, 0, width, height,
                    new int[]{0xFF655039, 0xFF493736, 0xFF221E25},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
        }

        // Keep text and playback controls legible without washing out the cover.
        paint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{0x00000000, 0x08000000, 0x65000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(new LinearGradient(0, 0, width, 0,
                0x20000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        // The FrameLayout clips the entire widget (artwork and controls) to
        // one rounded outline, avoiding double-masking or mismatched corners.
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
