package pl.mirror.widget;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.SystemClock;

/** Optional output-mix level meter. No microphone capture, recording or network. */
final class AudioReactive {
    private static Visualizer meter;
    private static final Visualizer.MeasurementPeakRms measurement=new Visualizer.MeasurementPeakRms();
    private static long retryAt,lastSample;
    private static float envelope;
    private static boolean available;
    private static int alpha;
    static boolean requested(Context c){return c.getSharedPreferences("neon_audio",Context.MODE_PRIVATE).getBoolean("enabled",false);}
    static void setRequested(Context c,boolean enabled){
        c.getSharedPreferences("neon_audio",Context.MODE_PRIVATE).edit().putBoolean("enabled",enabled).apply();
        retryAt=0;if(!enabled)stop();
    }
    static boolean permitted(Context c){return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
    static boolean enabled(Context c){return requested(c)&&permitted(c);}
    static boolean available(){return available;}
    static int alpha(){return alpha;}
    static void stop(){
        if(meter!=null){try{meter.setEnabled(false);}catch(RuntimeException ignored){}
            try{meter.release();}catch(RuntimeException ignored){}meter=null;}
        envelope=0;alpha=0;lastSample=0;available=false;
    }
    static void sample(Context c,boolean active){
        if(!active||!enabled(c)){stop();return;}
        long now=SystemClock.elapsedRealtime();
        if(now<retryAt)return;
        try{
            if(meter==null){
                meter=new Visualizer(0);
                if(meter.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED)!=Visualizer.SUCCESS||
                   meter.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS)!=Visualizer.SUCCESS||
                   meter.setEnabled(true)!=Visualizer.SUCCESS)throw new IllegalStateException("Visualizer unavailable");
            }
            if(meter.getMeasurementPeakRms(measurement)!=Visualizer.SUCCESS)throw new IllegalStateException("Level unavailable");
            available=true;
            float rms=clamp((measurement.mRms+4500f)/3900f);
            float peak=clamp((measurement.mPeak+3000f)/2800f);
            float target=clamp(.68f*rms+.32f*peak);
            double dt=lastSample==0?.10:Math.min(.5,(now-lastSample)/1000.0);
            double tau=target>envelope?.055:.28;
            envelope+=(target-envelope)*(float)(1-Math.exp(-dt/tau));
            lastSample=now;alpha=Math.round(220*envelope);
        }catch(RuntimeException e){stop();retryAt=now+15000L;android.util.Log.d("AudioReactive","Output mix level unavailable");}
    }
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
}
