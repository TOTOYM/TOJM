package pl.mirror.widget;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class WaveRenderer {
    private WaveRenderer() {}

    static Bitmap draw(int progress, int w, int h, float density) {
        w = Math.max(80, Math.min(680, w));
        h = Math.max(14, Math.min(85, h));
        Bitmap image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float margin = 4.5f * density;
        float center = h * 0.52f;
        float position = margin + (w - 2f * margin) * Math.max(0, Math.min(1000, progress)) / 1000f;
        paint.setColor(Color.argb(150, 255, 221, 179));
        paint.setStrokeWidth(Math.max(1f, density * 1.2f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(margin, center, w - margin, center, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.rgb(255, 221, 179));
        paint.setStrokeWidth(Math.max(1.4f, density * 1.7f));
        Path wave = new Path();
        for (int i = 0; i <= 100; i++) {
            float t = i / 100f;
            float px = margin + t * Math.max(0f, position - margin);
            float envelope = (float) Math.sin(Math.PI * t * 0.9);
            float py = center + (float) Math.sin(t * Math.PI * 8f) * 2.9f * density * envelope;
            if (i == 0) wave.moveTo(px, py); else wave.lineTo(px, py);
        }
        canvas.drawPath(wave, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(position - 2f * density, center - 10f * density,
            position + 2f * density, center + 10f * density), 2f * density, 2f * density, paint);
        return image;
    }
}