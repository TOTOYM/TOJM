package pl.mirror.widget;

import android.app.Activity;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RemoteViews;

public class MainActivity extends Activity {
    private static final int BG=0xFF111116, PANEL=0xFF202027, LINE=0xFF36353F,
        TEXT=0xFFF4F2F7, MUTED=0xFFBDBBC6, ACCENT=0xFFD9C6FF, INK=0xFF2C1947;
    private static final int[] HEIGHT={120,184,250};
    private int selected;
    private LinearLayout root;
    private FrameLayout preview;
    private TextView status, subStatus, current, sizeCaption, installed;
    private Button permission, add;
    private final Button[] choices=new Button[3];

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        selected=Math.max(0,Math.min(2,getPreferences(MODE_PRIVATE).getInt("size",0)));
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(24));
        scroll.addView(root);
        root.addView(label("MUZYKA   /   WIDŻET",11,ACCENT,true));
        gap(root,8);
        root.addView(label("Twój odtwarzacz.",30,TEXT,true));
        gap(root,5);
        root.addView(label("Muzyka na ekranie głównym, tak jak lubisz.",15,MUTED,false));
        gap(root,22);
        LinearLayout access=card();
        status=label("Sprawdzanie dostępu…",17,TEXT,true);
        access.addView(status);
        gap(access,6);
        subStatus=label("",13,MUTED,false); access.addView(subStatus);
        permission=button("Zezwól na dostęp",true);
        permission.setOnClickListener(v->openPermission());
        gap(access,10); access.addView(permission,full(44));
        root.addView(access);
        gap(root,22);
        root.addView(label("Rozmiar widżetu",20,TEXT,true));
        gap(root,5);
        root.addView(label("Wybierz wariant i zobacz jego proporcje.",13,MUTED,false));
        gap(root,10);
        LinearLayout sizes=new LinearLayout(this);
        sizes.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<3;i++){
            final int n=i;
            Button b=button("4 × "+(i+2),false);
            b.setTextSize(14);
            b.setOnClickListener(v->{selected=n;getPreferences(MODE_PRIVATE).edit().putInt("size",selected).apply();refreshPreview();});
            choices[i]=b;
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);
            if(i>0)lp.leftMargin=dp(7);
            sizes.addView(b,lp);
        }
        root.addView(sizes);
        gap(root,16);
        LinearLayout stage=card(); stage.setPadding(dp(12),dp(12),dp(12),dp(12));
        stage.addView(label("PODGLĄD NA ŻYWO",10,MUTED,true));
        gap(stage,12);
        preview=new FrameLayout(this);
        stage.addView(preview,new LinearLayout.LayoutParams(-1,dp(HEIGHT[selected])));
        gap(stage,10);
        sizeCaption=label("",12,MUTED,false);
        stage.addView(sizeCaption);
        root.addView(stage);
        gap(root,12);
        add=button("Dodaj do ekranu głównego",true);
        add.setOnClickListener(v->pinWidget());
        root.addView(add,full(54));
        gap(root,7);
        root.addView(label("Rozmiar wybierasz przed dodaniem. Możesz też przytrzymać widżet na pulpicie i zmienić wysokość. Ostateczną siatkę ustala launcher.",12,MUTED,false));
        gap(root,22);
        LinearLayout now=card();
        now.addView(label("Teraz odtwarzane",16,TEXT,true));
        gap(now,7);
        current=label("",14,MUTED,false);now.addView(current);
        gap(now,14);
        installed=label("",13,ACCENT,true);now.addView(installed);
        root.addView(now);
        gap(root,20);
        root.addView(label("Informacje",17,TEXT,true));
        gap(root,7);
        root.addView(label("Dostęp do powiadomień umożliwia odczyt sesji muzycznych. Dane pozostają na urządzeniu. Android wymaga, by dostęp przyznać ręcznie.",13,MUTED,false));
        gap(root,9);
        root.addView(label("Wersja 2.6  •  Bez reklam i konta",12,MUTED,false));
        setContentView(scroll);
    }

    @Override protected void onResume(){
        super.onResume();
        if(status==null)return;
        boolean granted=granted();
        status.setText(granted?"Dostęp do muzyki aktywny":"Wymagany dostęp do muzyki");
        status.setTextColor(granted?0xFFB8E9C8:TEXT);
        subStatus.setText(granted?"Widżet może odczytywać utwory i sterować odtwarzaniem.":"Włącz dostęp do powiadomień dla Muzyka Widget. Jeśli Android blokuje opcję, zezwól na ustawienia z ograniczeniami w informacjach o aplikacji.");
        permission.setVisibility(granted?View.GONE:View.VISIBLE);
        MediaController c=MediaBridge.preferred(this);
        MediaMetadata m=c==null?null:c.getMetadata();
        String track=m==null?null:m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist=m==null?null:m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        current.setText(track==null?"Nie wykryto muzyki. Włącz utwór w Spotify lub innym odtwarzaczu.":track+(artist==null?"":" · "+artist));
        installed.setText(widgetDetails());
        refreshPreview();
        MusicWidget.refreshAll(this);
    }
    private String widgetDetails(){
        AppWidgetManager manager=AppWidgetManager.getInstance(this);
        StringBuilder b=new StringBuilder("Widżety na pulpicie: "+MusicWidget.widgetCount(this));
        Class<?>[] providers=MusicWidget.providers();
        for(int i=0;i<providers.length;i++){
            ComponentName p=new ComponentName(this,providers[i]);
            for(int id:manager.getAppWidgetIds(p)){
                Bundle o=manager.getAppWidgetOptions(id);
                android.util.SizeF size=MusicWidget.portraitSize(this,o,HEIGHT[i]);
                b.append("\n4 × ").append(i+2).append(" · rozmiar pulpitu: ")
                    .append(Math.round(size.getWidth())).append(" × ").append(Math.round(size.getHeight())).append(" dp");
            }
        }
        return b.toString();
    }
    private boolean granted(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        return nm!=null&&nm.isNotificationListenerAccessGranted(new ComponentName(this,MusicListener.class));
    }
    private void openPermission(){
        try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
        catch(RuntimeException e){Toast.makeText(this,"Otwórz ustawienia dostępu do powiadomień.",Toast.LENGTH_LONG).show();}
    }
    private int previewWidth(){
        int w=preview.getWidth();
        return Math.max(240,w>0?(int)(w/getResources().getDisplayMetrics().density):getResources().getConfiguration().screenWidthDp-64);
    }
    private void refreshPreview(){
        for(int i=0;i<3;i++){
            choices[i].setBackground(shape(i==selected?ACCENT:PANEL,14,i==selected?ACCENT:LINE));
            choices[i].setTextColor(i==selected?INK:TEXT);
            choices[i].setSelected(i==selected);
        }
        preview.getLayoutParams().height=dp(HEIGHT[selected]);
        preview.requestLayout();
        preview.removeAllViews();
        try{
            RemoteViews remote=MusicWidget.preview(this,previewWidth(),HEIGHT[selected]);
            FrameLayout holder=new FrameLayout(this);
            // Preview is deliberately noninteractive; buttons only act on the real widget.
            View rendered=remote.apply(this,holder);
            holder.addView(rendered,new FrameLayout.LayoutParams(-1,-1));
            preview.addView(holder,new FrameLayout.LayoutParams(-1,-1));
        } catch(RuntimeException e){
            preview.addView(label("Podgląd jest chwilowo niedostępny.",14,MUTED,false));
        }
        sizeCaption.setText(new String[]{"Kompaktowy • 4 kolumny × 2 rzędy","Standardowy • 4 kolumny × 3 rzędy","Duży • 4 kolumny × 4 rzędy"}[selected]);
    }
    private void pinWidget(){
        AppWidgetManager manager=AppWidgetManager.getInstance(this);
        if(!manager.isRequestPinAppWidgetSupported()){
            Toast.makeText(this,"Przytrzymaj pulpit → Widżety → Muzyka · 4 × "+(selected+2),Toast.LENGTH_LONG).show();
            return;
        }
        try{
            ComponentName name=new ComponentName(this,MusicWidget.providers()[selected]);
            if(!manager.requestPinAppWidget(name,null,null))
                Toast.makeText(this,"Nie udało się otworzyć dodawania widżetu.",Toast.LENGTH_LONG).show();
        }catch(RuntimeException e){Toast.makeText(this,"Dodaj wybrany rozmiar przez menu widżetów pulpitu.",Toast.LENGTH_LONG).show();}
    }
    private LinearLayout card(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(16),dp(16),dp(16),dp(16));
        l.setBackground(shape(PANEL,22,LINE));return l;
    }
    private GradientDrawable shape(int color,int radius,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.START);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }
    private Button button(String title,boolean primary){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(15);
        b.setTextColor(primary?INK:TEXT);b.setBackgroundTintList(null);
        b.setBackground(shape(primary?ACCENT:PANEL,16,primary?ACCENT:LINE));
        b.setPadding(dp(8),0,dp(8),0);b.setMinHeight(0);b.setMinimumHeight(0);return b;
    }
    private LinearLayout.LayoutParams full(int height){return new LinearLayout.LayoutParams(-1,dp(height));}
    private void gap(LinearLayout l,int size){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
