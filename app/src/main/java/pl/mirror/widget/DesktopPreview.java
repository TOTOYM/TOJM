package pl.mirror.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

/**
 * Local layout sandbox, not an AppWidgetHost or a copy of the user's launcher.
 * The card is rendered by the same RemoteViews builder as a real widget.
 */
final class DesktopPreview extends FrameLayout {
    static final int COLS=5;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private FrameLayout player;
    private int rows=6, span=2, row=2, renderedWidth=-1, renderedHeight=-1;
    private boolean refresh=true, grid=true, neon;
    private float top, bottom, cell, inset, downY;
    private int dragStart;
    private Runnable onPlacementChanged;

    DesktopPreview(Context c) {
        super(c);
        setWillNotDraw(false);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF343144,0xFF23212D,0xFF493648});
        bg.setCornerRadius(dp(26));
        setBackground(bg);
        setClipToOutline(true);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setContentDescription("Symulacja pulpitu. Przeciągnij widżet w górę lub w dół.");
        player=new FrameLayout(c);
        player.setClipChildren(true);
        addView(player,new FrameLayout.LayoutParams(1,1));
    }
    void setPlacementChanged(Runnable callback){onPlacementChanged=callback;}
    void configure(int newSpan,int newRows,int newRow,boolean showGrid,boolean neonStyle) {
        rows=Math.max(5,Math.min(7,newRows));
        span=Math.max(2,Math.min(4,newSpan));
        row=Math.max(0,Math.min(rows-span,newRow));
        grid=showGrid;neon=neonStyle;
        refresh=true;requestLayout();invalidate();
    }
    int getRow(){return row;}
    int getRows(){return rows;}
    void updateMusic(){refresh=true;requestLayout();}

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void geometry(int w,int h){
        inset=w*.035f;
        top=h*.115f;
        bottom=h*.855f;
        cell=(bottom-top)/rows;
    }
    @Override protected void onMeasure(int wi,int hi){
        int w=MeasureSpec.getSize(wi);
        int h=MeasureSpec.getSize(hi);
        if(h<=0)h=dp(420);
        setMeasuredDimension(w,h);
        geometry(w,h);
        float density=getResources().getDisplayMetrics().density;
        int usableW=Math.max(1,Math.round(w-inset*2));
        int usableH=Math.max(1,Math.round(cell*span-dp(5)));
        // Render at an estimated full-screen DP size, then scale down uniformly.
        int nativeW=Math.max(200,getResources().getConfiguration().screenWidthDp-24);
        float scale=Math.min(1f,usableW/(nativeW*density));
        int nativeH=Math.max(60,Math.round(usableH/(scale*density)));
        int pw=dp(nativeW),ph=dp(nativeH);
        if(refresh||nativeW!=renderedWidth||nativeH!=renderedHeight){
            player.removeAllViews();
            try{
                RemoteViews remote=MusicWidget.preview(getContext(),nativeW,nativeH,neon);
                View child=remote.apply(getContext(),player);
                // Prevent the simulated controls from stealing grid gestures.
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                player.addView(child,new FrameLayout.LayoutParams(-1,-1));
            }catch(RuntimeException e){
                android.widget.TextView error=new android.widget.TextView(getContext());
                error.setText("Podgląd niedostępny");error.setTextColor(Color.WHITE);
                error.setGravity(android.view.Gravity.CENTER);
                player.addView(error,new FrameLayout.LayoutParams(-1,-1));
            }
            renderedWidth=nativeW;renderedHeight=nativeH;refresh=false;
        }
        player.setPivotX(0);player.setPivotY(0);player.setScaleX(scale);player.setScaleY(scale);
        player.measure(MeasureSpec.makeMeasureSpec(pw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ph,MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int x=Math.round(inset),y=Math.round(top+row*cell+dp(2));
        player.layout(x,y,x+player.getMeasuredWidth(),y+player.getMeasuredHeight());
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        paint.setStyle(Paint.Style.FILL);paint.setColor(0x175D6087);
        c.drawCircle(w*.8f,h*.25f,w*.65f,paint);
        paint.setColor(0x258C6C99);c.drawCircle(w*.12f,h*.72f,w*.72f,paint);
        paint.setColor(0xFFEEEAF2);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        paint.setTextSize(w*.04f);paint.setTextAlign(Paint.Align.LEFT);
        String time=new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(new java.util.Date());
        c.drawText(time,inset+dp(5),h*.065f,paint);
        paint.setTextSize(w*.028f);paint.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Wi-Fi   ▰",w-inset-dp(5),h*.065f,paint);
        float cw=(w-2*inset)/COLS;
        for(int rr=0;rr<rows;rr++)for(int col=0;col<COLS;col++){
            float x=inset+col*cw,y=top+rr*cell;
            if(grid){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x368F84AC);
                c.drawRoundRect(new RectF(x+dp(2),y+dp(2),x+cw-dp(2),y+cell-dp(2)),dp(6),dp(6),paint);
                paint.setStyle(Paint.Style.FILL);
            }
            // Decorative example icons outside the selected widget's footprint.
            if(rr<row||rr>=row+span) {
                if(rr==0||rr==rows-1||rr==rows-2) icon(c,x+cw*.5f,y+cell*.45f,Math.min(cw,cell)*.44f,(rr*COLS+col)%5);
            }
        }
        for(int col=0;col<COLS;col++)icon(c,inset+(col+.5f)*cw,h*.918f,cw*.5f,col);
        paint.setColor(0xBBFFFFFF);
        c.drawRoundRect(new RectF(w*.36f,h*.973f,w*.64f,h*.981f),dp(3),dp(3),paint);
    }
    private void icon(Canvas c,float x,float y,float size,int n){
        int[] colors={0xFF685A80,0xFF5F6B8A,0xFF73617A,0xFF826781,0xFF4D706E};
        paint.setColor(colors[n]);float h=size/2;
        c.drawRoundRect(new RectF(x-h,y-h,x+h,y+h),size*.26f,size*.26f,paint);
        paint.setColor(0xFFE4DDEC);paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(size*.54f);
        c.drawText(new String[]{"☎","●","◎","▣","♪"}[n],x,y-(paint.ascent()+paint.descent())/2,paint);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){return true;}
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(e.getY()<top||e.getY()>bottom)return false;
                downY=e.getY();dragStart=row;
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(cell>0)setRow(dragStart+Math.round((e.getY()-downY)/cell));
                return true;
            case MotionEvent.ACTION_UP:
                if(Math.abs(e.getY()-downY)<dp(7)&&cell>0){
                    int tapped=(int)((e.getY()-top)/cell);
                    if(tapped<row||tapped>=row+span)setRow(tapped);
                    performClick();
                }
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return true;
    }
    private void setRow(int value){
        int next=Math.max(0,Math.min(rows-span,value));
        if(next==row)return;
        row=next;requestLayout();invalidate();
        if(onPlacementChanged!=null)onPlacementChanged.run();
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
