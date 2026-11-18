package pl.mirror.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class WaveRenderer {
    static Bitmap draw(int progress, int w, int h, float density, int color, boolean playing) {
        w = Math.max(45, Math.min(720, w));
        h = Math.max(14, Math.min(100, h));
        Bitmap image = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float margin = Math.min(w/4f, 4f*density), mid = h*.5f;
        float end = w-margin;
        float x = margin + (end-margin)*Math.max(0,Math.min(1000,progress))/1000f;
        p.setColor(ArtRenderer.withAlpha(color, 100));
        p.setStrokeWidth(Math.max(1f,1.5f*density));
        p.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(margin,mid,end,mid,p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(Math.max(1.5f,1.8f*density));
        Path wave = new Path();
        int steps = Math.max(1, (int)(x-margin));
        float period = Math.max(8, 23f*density);
        for (int i=0; i<=steps; i++) {
            float px = margin + (x-margin)*i/steps;
            float envelope = Math.min(1f, Math.min((px-margin)/period, (x-px)/period)*2f);
            float y = mid + (playing ? 2.0f*density*Math.max(0,envelope)
                *(float)Math.sin((px-margin)*2*Math.PI/period) : 0);
            if (i==0) wave.moveTo(px,y); else wave.lineTo(px,y);
        }
        if (x>margin) canvas.drawPath(wave,p);
        p.setStyle(Paint.Style.FILL);
        float thumb = Math.min(h*.38f,8f*density);
        canvas.drawRoundRect(new RectF(x-1.8f*density,mid-thumb,x+1.8f*density,mid+thumb),
            2*density,2*density,p);
        return image;
    }
}
