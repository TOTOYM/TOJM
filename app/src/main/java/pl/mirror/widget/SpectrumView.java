package pl.mirror.widget;
import android.content.Context;
import android.graphics.*;
import android.view.View;

final class SpectrumView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] bands=new float[24];
    private boolean live;
    SpectrumView(Context c){super(c);setContentDescription("Widmo częstotliwości dźwięku");}
    void update(float[] values,boolean active){bands=values;live=active;invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight(),d=getResources().getDisplayMetrics().density;
        if(w<=0||h<=0)return;
        float top=12*d,bottom=h-25*d;
        p.setColor(0xFF15151C);c.drawRoundRect(new RectF(0,0,w,h),12*d,12*d,p);
        p.setStrokeWidth(d);p.setColor(0x25FFFFFF);
        for(int i=0;i<4;i++){float y=top+(bottom-top)*i/3;c.drawLine(9*d,y,w-9*d,y,p);}
        float dx=(w-18*d)/bands.length;
        for(int i=0;i<bands.length;i++){
            float x=9*d+i*dx;
            float level=live?Math.max(0,Math.min(1,bands[i])):0;
            float bar=Math.max(1*d,(bottom-top)*level);
            p.setColor(live?Color.HSVToColor(new float[]{98+i*2f,.55f,.94f}):0xFF393841);
            c.drawRoundRect(new RectF(x,bottom-bar,x+Math.max(1,dx-2*d),bottom),2*d,2*d,p);
        }
        p.setColor(0xFFBDBBC6);p.setTextSize(10*d);p.setTextAlign(Paint.Align.LEFT);
        c.drawText("20 Hz",9*d,h-8*d,p);p.setTextAlign(Paint.Align.CENTER);c.drawText("1 kHz",w*.57f,h-8*d,p);
        p.setTextAlign(Paint.Align.RIGHT);c.drawText("20 kHz*",w-9*d,h-8*d,p);
        if(!live){p.setTextAlign(Paint.Align.CENTER);p.setTextSize(12*d);c.drawText("Brak sygnału FFT",w/2,(top+bottom)/2,p);}
    }
}
