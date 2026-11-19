package pl.mirror.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class WaveRenderer {
    static Bitmap draw(int progress, int w, int h, float density, int color, boolean playing) {
        return drawFraction(progress/1000.0,w,h,density,color,playing,android.os.SystemClock.elapsedRealtime());
    }
    // AOSP SquigglyProgress parameters: 20dp wavelength, 1.5dp amplitude,
    // 8dp/s phase speed, 2dp stroke. The launcher receives just a small bitmap.
    static Bitmap drawFraction(double fraction,int w,int h,float density,int color,boolean playing,long now) {
        w = Math.max(45, Math.min(720, w));
        h = Math.max(14, Math.min(100, h));
        Bitmap image = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float margin = Math.min(w/4f, 4f*density), mid = h*.5f;
        float end = w-margin;
        float x = margin + (end-margin)*(float)Math.max(0,Math.min(1,fraction));
        p.setColor(ArtRenderer.withAlpha(color, 100));
        p.setStrokeWidth(Math.max(1f,1.5f*density));
        p.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(margin,mid,end,mid,p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(Math.max(1.5f,2f*density));
        Path wave = new Path();
        int steps = Math.max(1, (int)(x-margin));
        float period = Math.max(8, 20f*density);
        double phase = ((now%2500L)/1000.0)*8*density;
        for (int i=0; i<=steps; i++) {
            float px = margin + (x-margin)*i/steps;
            float envelope = Math.min(1f, Math.min((px-margin)/period, (x-px)/period)*2f);
            float y = mid + (playing ? 1.5f*density*Math.max(0,envelope)
                *(float)Math.sin((px-margin+phase)*2*Math.PI/period) : 0);
            if (i==0) wave.moveTo(px,y); else wave.lineTo(px,y);
        }
        if (x>margin) canvas.drawPath(wave,p);
        p.setStyle(Paint.Style.FILL);
        float thumb = Math.min(h*.38f,8f*density);
        canvas.drawRoundRect(new RectF(x-1.6f*density,mid-thumb,x+1.6f*density,mid+thumb),
            2*density,2*density,p);
        return image;
    }
}
