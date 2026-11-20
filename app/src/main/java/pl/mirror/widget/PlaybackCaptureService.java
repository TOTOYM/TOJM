package pl.mirror.widget;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.projection.*;
import android.os.*;

/** Opt-in audio-only capture of Spotify. PCM stays in temporary memory. */
public final class PlaybackCaptureService extends Service {
    static final String STOP="pl.mirror.widget.CAPTURE_STOP";
    static volatile boolean selected, running;
    static volatile String status="Nieuruchomiony";
    static volatile Frame latest;
    private volatile boolean alive;
    private AudioRecord record;
    private MediaProjection projection;
    private boolean receiverRegistered;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final MediaProjection.Callback callback=new MediaProjection.Callback(){
        @Override public void onStop(){end("Zgoda systemowa zakończona");}
    };
    private final BroadcastReceiver screen=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){end("Zatrzymano po wygaszeniu ekranu");}
    };
    static final class Frame {
        final byte[] fft; final int rms,peak; final long time;
        Frame(byte[] fft,int rms,int peak,long time){this.fft=fft;this.rms=rms;this.peak=peak;this.time=time;}
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i==null||STOP.equals(i.getAction())){selected=false;end("Zatrzymano");return START_NOT_STICKY;}
        if(alive)return START_NOT_STICKY;
        selected=true;latest=null;status="Uruchamianie…";
        try{
            if(Build.VERSION.SDK_INT<29)throw new IllegalStateException("Wymagany Android 10");
            NotificationManager nm=getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("audio_capture","Analiza dźwięku Spotify",NotificationManager.IMPORTANCE_LOW));
            PendingIntent stop=PendingIntent.getService(this,72,new Intent(this,PlaybackCaptureService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n=new Notification.Builder(this,"audio_capture").setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Audio Lab • Spotify").setContentText("Analiza dźwięku wewnętrznego, bez obrazu i zapisu")
                .setOngoing(true).addAction(new Notification.Action.Builder(null,"Zatrzymaj",stop).build()).build();
            startForeground(731,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            Intent data=i.getParcelableExtra("consent");
            MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
            projection=manager.getMediaProjection(Activity.RESULT_OK,data);
            if(projection==null)throw new IllegalStateException("Brak zgody");
            projection.registerCallback(callback,main);
            int uid=getPackageManager().getApplicationInfo("com.spotify.music",0).uid;
            AudioPlaybackCaptureConfiguration config=new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUid(uid).addMatchingUsage(AudioAttributes.USAGE_MEDIA).build();
            int rate=48000;
            AudioFormat format=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build();
            int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("Format PCM niedostępny");
            record=new AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(Math.max(min,16384))
                .setAudioPlaybackCaptureConfig(config).build();
            if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("AudioRecord niedostępny");
            registerReceiver(screen,new IntentFilter(Intent.ACTION_SCREEN_OFF));receiverRegistered=true;
            record.startRecording();
            if(record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("Brak dostępu do audio");
            alive=true;running=true;status="Oczekiwanie na próbki Spotify";
            final AudioRecord source=record;
            new Thread(()->read(source),"Spotify-PCM").start();
        }catch(Exception|LinkageError e){android.util.Log.w("PlaybackCapture","Cannot start",e);end("Nie można uruchomić przechwytywania Spotify");}
        return START_NOT_STICKY;
    }
    private void read(AudioRecord source){
        short[] pcm=new short[4096];double[] re=new double[2048],im=new double[2048];
        long last=0;
        try{
            while(alive){
                int offset=0;
                while(alive&&offset<pcm.length){
                    int n=source.read(pcm,offset,pcm.length-offset,AudioRecord.READ_BLOCKING);
                    if(n<0)throw new IllegalStateException("AudioRecord "+n);
                    if(n==0){Thread.sleep(10);continue;}offset+=n;
                }
                if(!alive)break;
                long now=SystemClock.elapsedRealtime();
                if(now-last<45)continue;
                last=now;
                double sum=0,peak=0;
                for(int k=0;k<re.length;k++){
                    double l=pcm[2*k]/32768.0,r=pcm[2*k+1]/32768.0;
                    sum+=(l*l+r*r)/2;peak=Math.max(peak,Math.max(Math.abs(l),Math.abs(r)));
                    re[k]=(l+r)*.5*(.5-.5*Math.cos(2*Math.PI*k/(re.length-1)));im[k]=0;
                }
                fft(re,im);
                byte[] bins=new byte[re.length];
                for(int k=1;k<re.length/2;k++){
                    bins[2*k]=(byte)Math.max(-127,Math.min(127,Math.round(re[k]*512/re.length)));
                    bins[2*k+1]=(byte)Math.max(-127,Math.min(127,Math.round(im[k]*512/re.length)));
                }
                if(alive)latest=new Frame(bins,db(Math.sqrt(sum/re.length)),db(peak),now);
            }
        }catch(Exception e){if(alive)main.post(()->end("Przechwytywanie przerwane przez Androida"));}
        finally{java.util.Arrays.fill(pcm,(short)0);java.util.Arrays.fill(re,0);java.util.Arrays.fill(im,0);try{source.release();}catch(RuntimeException ignored){}}
    }
    private static int db(double v){return (int)Math.max(-9600,Math.round(2000*Math.log10(Math.max(v,1e-9))));}
    private static void fft(double[] r,double[] i){
        int n=r.length;
        for(int a=1,b=0;a<n;a++){int bit=n>>1;for(; (b&bit)!=0;bit>>=1)b^=bit;b^=bit;
            if(a<b){double x=r[a];r[a]=r[b];r[b]=x;}}
        for(int len=2;len<=n;len<<=1){double angle=-2*Math.PI/len;
            for(int j=0;j<n;j+=len)for(int k=0;k<len/2;k++){
                int a=j+k,b=a+len/2;double cr=Math.cos(angle*k),ci=Math.sin(angle*k);
                double tr=r[b]*cr-i[b]*ci,ti=r[b]*ci+i[b]*cr;
                r[b]=r[a]-tr;i[b]=i[a]-ti;r[a]+=tr;i[a]+=ti;
            }
        }
    }
    private void end(String message){status=message;shutdown();stopSelf();}
    private void shutdown(){
        boolean wasAlive=alive;alive=false;running=false;latest=null;
        AudioRecord old=record;record=null;
        if(old!=null){try{old.stop();}catch(RuntimeException ignored){}if(!wasAlive)try{old.release();}catch(RuntimeException ignored){}}
        if(projection!=null){MediaProjection p=projection;projection=null;try{p.unregisterCallback(callback);p.stop();}catch(RuntimeException ignored){}}
        if(receiverRegistered){receiverRegistered=false;try{unregisterReceiver(screen);}catch(RuntimeException ignored){}}
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
    @Override public void onDestroy(){shutdown();super.onDestroy();}
}
