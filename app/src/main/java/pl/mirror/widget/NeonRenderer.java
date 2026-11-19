package pl.mirror.widget;
import android.graphics.*;
/** Artwork-derived luminous glass treatment. Decorative only, not a spectrum analyser. */
final class NeonRenderer {
    private static int last;
    private static Bitmap overlay, halo;
    static int accent(int base) {
        float[] hsv=new float[3];Color.colorToHSV(base,hsv);
        return Color.HSVToColor(new float[]{hsv[0],hsv[1]<.04f?.14f:Math.min(.72f,Math.max(.40f,hsv[1]*2.4f)),1f});
    }
    static Bitmap overlay(int color){prepare(color);return overlay;}
    static Bitmap halo(int color){prepare(color);return halo;}
    private static void prepare(int color){
        if(overlay!=null&&last==color)return;
        last=color;int w=384,h=288;
        overlay=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(overlay);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0,0,0,h,new int[]{0xB5000805,0x22000805,0x66000603,0xED020904},new float[]{0,.30f,.55f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        p.setShader(new LinearGradient(0,0,w,0,new int[]{0x99000703,0x20000703,0x00000000},new float[]{0,.48f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        for(float[] v:new float[][]{{0,h*.72f,w*.26f},{w,h*.88f,w*.25f},{w*.92f,0,w*.19f}}){
            p.setShader(new RadialGradient(v[0],v[1],v[2],new int[]{ArtRenderer.withAlpha(color,170),ArtRenderer.withAlpha(color,45),ArtRenderer.withAlpha(color,0)},new float[]{0,.40f,1},Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);
        }
        p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f);p.setColor(ArtRenderer.withAlpha(color,130));
        c.drawRoundRect(new RectF(1,1,w-1,h-1),28,28,p);
        halo=Bitmap.createBitmap(136,108,Bitmap.Config.ARGB_8888);Canvas hc=new Canvas(halo);
        p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(68,54,68,new int[]{ArtRenderer.withAlpha(color,210),ArtRenderer.withAlpha(color,85),ArtRenderer.withAlpha(color,0)},new float[]{0,.64f,1},Shader.TileMode.CLAMP));
        hc.drawRect(0,0,136,108,p);
    }
    static String time(long ms){long s=Math.max(0,ms/1000);return String.format(java.util.Locale.ROOT,"%d:%02d",s/60,s%60);}
}
