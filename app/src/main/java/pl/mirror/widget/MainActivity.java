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
    private static final int[] HEIGHT={120,184,250,250};
    private int selected;
    private LinearLayout root;
    private DesktopPreview preview;
    private int gridRows=6, widgetRow=2;
    private boolean showGrid=true;
    private TextView placement;
    private TextView status, subStatus, current, sizeCaption, installed;
    private Button permission, add, audioToggle;
    private TextView audioStatus, labLevels, labMetrics, sensitivityText;
    private SpectrumView spectrum;
    private boolean labOpen;
    private static final int AUDIO_REQUEST=41;
    private final android.os.Handler audioHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable audioUi=new Runnable(){public void run(){updateAudioStatus();if(labOpen)updateLab();audioHandler.postDelayed(this,labOpen?100L:1000L);}};
    private final Button[] choices=new Button[4];

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        selected=Math.max(0,Math.min(3,getPreferences(MODE_PRIVATE).getInt("size",0)));
        gridRows=Math.max(5,Math.min(7,getPreferences(MODE_PRIVATE).getInt("gridRows",6)));
        widgetRow=getPreferences(MODE_PRIVATE).getInt("widgetRow",2);
        showGrid=getPreferences(MODE_PRIVATE).getBoolean("showGrid",true);
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
        root.addView(label("Wybierz wysokość i zaplanuj układ na pięciu kolumnach.",13,MUTED,false));
        gap(root,10);
        LinearLayout sizes=new LinearLayout(this);
        sizes.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<3;i++){
            final int n=i;
            Button b=button("5 × "+(i+2),false);
            b.setTextSize(14);
            b.setOnClickListener(v->{selected=n;getPreferences(MODE_PRIVATE).edit().putInt("size",selected).apply();refreshPreview();});
            choices[i]=b;
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);
            if(i>0)lp.leftMargin=dp(7);
            sizes.addView(b,lp);
        }
        root.addView(sizes);
        gap(root,8);
        Button neon=button("Neon  •  5 × 4",false);
        choices[3]=neon;
        neon.setOnClickListener(v->{selected=3;getPreferences(MODE_PRIVATE).edit().putInt("size",selected).apply();refreshPreview();});
        root.addView(neon,full(46));
        gap(root,10);
        LinearLayout audio=card();
        audio.addView(label("Fala w rytmie muzyki",16,TEXT,true));
        gap(audio,5);
        audioStatus=label("",12,MUTED,false);
        audio.addView(audioStatus);
        gap(audio,5);
        audio.addView(label("Opcjonalnie fala postępu w widżecie Neon reaguje na poziom dźwięku odtwarzanego przez telefon, także przez słuchawki, jeśli urządzenie udostępnia sygnał. Android nazywa wymaganą zgodę mikrofonem. Nie nagrywamy otoczenia ani nie zapisujemy dźwięku.",12,MUTED,false));
        gap(audio,10);
        audioToggle=button("Włącz reakcję",false);
        audioToggle.setOnClickListener(v->{
            if(AudioReactive.requested(this)){AudioReactive.setRequested(this,false);updateAudioStatus();MusicWidget.refreshAll(this);return;}
            if(AudioReactive.permitted(this)){AudioReactive.setRequested(this,true);updateAudioStatus();MusicWidget.refreshAll(this);}
            else requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},AUDIO_REQUEST);
        });
        audio.addView(audioToggle,full(44));
        Button waveTest=button("Test fali (10 s, bez audio)",false);
        gap(audio,8);audio.addView(waveTest,full(44));
        waveTest.setOnClickListener(v->{AudioReactive.startWaveTest();MusicWidget.refreshAll(this);Toast.makeText(this,"Wróć na pulpit z odtwarzanym utworem. Test jest sztuczny, nie jest pomiarem audio.",Toast.LENGTH_LONG).show();});
        Button labButton=button("Otwórz Audio Lab",false);
        gap(audio,8);audio.addView(labButton,full(44));
        LinearLayout lab=new LinearLayout(this);lab.setOrientation(LinearLayout.VERTICAL);lab.setVisibility(View.GONE);
        gap(lab,12);
        lab.addView(label("AUDIO LAB / FFT",11,ACCENT,true));
        gap(lab,8);
        spectrum=new SpectrumView(this);lab.addView(spectrum,full(146));
        gap(lab,8);
        labLevels=label("",14,TEXT,true);lab.addView(labLevels);
        gap(lab,8);labMetrics=label("",13,MUTED,false);lab.addView(labMetrics);
        gap(lab,12);
        sensitivityText=label("",13,TEXT,true);lab.addView(sensitivityText);
        android.widget.SeekBar sensitivity=new android.widget.SeekBar(this);
        sensitivity.setMax(175);sensitivity.setProgress(AudioReactive.sensitivity(this)-25);
        sensitivityText.setText("Czułość fali: "+AudioReactive.sensitivity(this)+"%");
        sensitivity.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean user){
                sensitivityText.setText("Czułość fali: "+(progress+25)+"%");
                if(user)AudioReactive.setSensitivity(MainActivity.this,progress+25);
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar){}
            public void onStopTrackingTouch(android.widget.SeekBar bar){}
        });
        lab.addView(sensitivity,full(44));
        lab.addView(label("Wartości względne z miksu wyjściowego, nie pomiar SPL. BPM jest szacunkiem rytmu; bitrate Spotify nie jest dostępny. *Górna granica widma zależy od częstotliwości próbkowania.",11,MUTED,false));
        audio.addView(lab);
        labButton.setOnClickListener(v->{
            labOpen=!labOpen;lab.setVisibility(labOpen?View.VISIBLE:View.GONE);
            labButton.setText(labOpen?"Zamknij Audio Lab":"Otwórz Audio Lab");
            AudioReactive.setLabVisible(labOpen);
            updateLab();audioHandler.removeCallbacks(audioUi);audioHandler.post(audioUi);
        });
        root.addView(audio);
        updateAudioStatus();
        gap(root,16);
        LinearLayout stage=card(); stage.setPadding(dp(12),dp(12),dp(12),dp(12));
        stage.addView(label("SYMULACJA PULPITU",11,ACCENT,true));
        gap(stage,5);
        stage.addView(label("Przeciągnij widżet w pionie lub dotknij wolnego rzędu.",12,MUTED,false));
        gap(stage,12);
        preview=new DesktopPreview(this);
        int phoneHeight=Math.max(350,Math.min(480,getResources().getConfiguration().screenHeightDp-160));
        stage.addView(preview,new LinearLayout.LayoutParams(-1,dp(phoneHeight)));
        gap(stage,10);
        placement=label("",12,MUTED,false);
        stage.addView(placement);
        sizeCaption=label("",12,TEXT,true);
        gap(stage,5);stage.addView(sizeCaption);
        gap(stage,12);
        LinearLayout gridControls=new LinearLayout(this);
        gridControls.setOrientation(LinearLayout.HORIZONTAL);
        Button less=button("− rząd",false), more=button("+ rząd",false), grid=button(showGrid?"Ukryj siatkę":"Pokaż siatkę",false);
        less.setTextSize(12);more.setTextSize(12);grid.setTextSize(12);
        less.setOnClickListener(v->{gridRows=Math.max(5,gridRows-1);saveGrid();refreshPreview();});
        more.setOnClickListener(v->{gridRows=Math.min(7,gridRows+1);saveGrid();refreshPreview();});
        grid.setOnClickListener(v->{showGrid=!showGrid;grid.setText(showGrid?"Ukryj siatkę":"Pokaż siatkę");saveGrid();refreshPreview();});
        for(Button b:new Button[]{less,more,grid}){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
            lp.rightMargin=dp(4);gridControls.addView(b,lp);
        }
        stage.addView(gridControls);
        preview.setPlacementChanged(()->{widgetRow=preview.getRow();saveGrid();updatePlacement();});
        gap(stage,8);
        stage.addView(label("To model pulpitu, nie odczyt jego rzeczywistego układu. Ikony są przykładowe.",11,MUTED,false));
        root.addView(stage);
        gap(root,12);
        add=button("Dodaj do ekranu głównego",true);
        add.setOnClickListener(v->pinWidget());
        root.addView(add,full(54));
        gap(root,7);
        root.addView(label("Pulpit Motoroli sam ustala ostateczne położenie i rozmiar. Symulacja nie przenosi ikon ani innych widżetów.",12,MUTED,false));
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
        root.addView(label("Wersja 2.17.1  •  Bez reklam i konta",12,MUTED,false));
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
        updateAudioStatus();
        AudioReactive.setLabVisible(labOpen);
        audioHandler.removeCallbacks(audioUi);audioHandler.post(audioUi);
        refreshPreview();
        MusicWidget.refreshAll(this);
    }
    @Override protected void onPause(){AudioReactive.setLabVisible(false);audioHandler.removeCallbacks(audioUi);super.onPause();}
    private void updateLab(){
        if(spectrum==null)return;
        boolean fft=AudioReactive.fftAvailable()&&AudioReactive.signal();
        spectrum.update(AudioReactive.bands(),fft);
        labLevels.setText(fft?String.format(java.util.Locale.ROOT,"Bas %d%%   ·   Średnie %d%%   ·   Wysokie %d%%",Math.round(AudioReactive.bass()*100),Math.round(AudioReactive.mid()*100),Math.round(AudioReactive.treble()*100)):"Bas —   ·   Średnie —   ·   Wysokie —");
        String tempo=fft&&AudioReactive.bpm()>0?String.format(java.util.Locale.ROOT,"≈ %.0f BPM",AudioReactive.bpm()):"— (brak stabilnego rytmu)";
        String levels=AudioReactive.levelAvailable()?String.format(java.util.Locale.ROOT,"RMS %.1f dBFS  ·  Peak %.1f dBFS",AudioReactive.rmsMb()/100f,AudioReactive.peakMb()/100f):"RMS —   ·   Peak —";
        labMetrics.setText("Tempo: "+tempo+"\n"+levels+"\nPróbkowanie: "+(AudioReactive.sampleRate()>0?AudioReactive.sampleRate()+" Hz":"—")+"\nBitrate Spotify: niedostępny");
    }
    private void updateAudioStatus(){
        if(audioToggle==null)return;
        boolean requested=AudioReactive.requested(this), permitted=AudioReactive.permitted(this);
        audioToggle.setText(requested?"Wyłącz reakcję":"Włącz reakcję");
        audioStatus.setText(!requested?"Wyłączone • zwykła animacja fali":!permitted?"Brak zgody na dźwięk • wyłącz i włącz ponownie":AudioReactive.failed()?"Pomiar niedostępny • zwykła animacja fali działa nadal":AudioReactive.available()?(AudioReactive.signal()?"Aktywne • wykryto sygnał dźwięku":"Aktywne • brak mierzalnego sygnału"):"Włączone • oczekuje na lokalne odtwarzanie i widżet Neon");
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==AUDIO_REQUEST){
            AudioReactive.setRequested(this,AudioReactive.permitted(this));
            updateAudioStatus();MusicWidget.refreshAll(this);
        }
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
                b.append(i==3?"\nNeon · 5 × ":"\n5 × ").append(Math.min(4,i+2)).append(" · rozmiar pulpitu: ")
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
    private void saveGrid(){
        getPreferences(MODE_PRIVATE).edit().putInt("gridRows",gridRows).putInt("widgetRow",widgetRow).putBoolean("showGrid",showGrid).apply();
    }
    private void updatePlacement(){
        placement.setText("Siatka 5 × "+gridRows+"  •  Rzędy "+(widgetRow+1)+"–"+(widgetRow+Math.min(4,selected+2)));
    }
    private void refreshPreview(){
        for(int i=0;i<choices.length;i++){
            choices[i].setBackground(shape(i==selected?ACCENT:PANEL,14,i==selected?ACCENT:LINE));
            choices[i].setTextColor(i==selected?INK:TEXT);
            choices[i].setSelected(i==selected);
        }
        widgetRow=Math.max(0,Math.min(gridRows-Math.min(4,selected+2),widgetRow));
        preview.configure(Math.min(4,selected+2),gridRows,widgetRow,showGrid,selected==3);
        saveGrid();updatePlacement();
        sizeCaption.setText(new String[]{"Kompaktowy • 5 × 2","Standardowy • 5 × 3","Duży • 5 × 4","Neon • 5 × 4"}[selected]);
    }
    private void pinWidget(){
        AppWidgetManager manager=AppWidgetManager.getInstance(this);
        if(!manager.isRequestPinAppWidgetSupported()){
            Toast.makeText(this,"Przytrzymaj pulpit → Widżety → Muzyka · 5 × "+Math.min(4,selected+2),Toast.LENGTH_LONG).show();
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
