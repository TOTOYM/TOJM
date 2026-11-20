package pl.mirror.widget;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.SystemClock;

/** Optional output-mix level meter. No microphone capture, recording or network. */
final class AudioReactive {
    private static Visualizer meter;
    private static Visualizer.MeasurementPeakRms measurement;
    private static boolean failed;
    private static boolean signal;
    private static long retryAt,lastSample;
    private static float envelope;
    private static boolean available;
    private static int alpha;
    private static long testUntil;
    static void startWaveTest(){testUntil=SystemClock.elapsedRealtime()+10000L;}
    static boolean testing(){return SystemClock.elapsedRealtime()<testUntil;}
    static int waveLevel(boolean enabled){
        if(testing())return (int)(110+105*Math.sin(SystemClock.elapsedRealtime()*2*Math.PI/1100.0));
        return enabled?alpha:0;
    }
    private static final SpectrumAnalysis analysis=new SpectrumAnalysis();
    private static byte[] fft;
    private static int sampleRate, rmsMb,peakMb;
    private static boolean fftAvailable,labVisible;
    private static String trackKey="";
    static boolean labVisible(){return labVisible;}
    static void setLabVisible(boolean v){labVisible=v;}
    static boolean fftAvailable(){return fftAvailable;}
    static float[] bands(){return analysis.bands.clone();}
    static float bass(){return analysis.bass;}
    static float mid(){return analysis.mid;}
    static float treble(){return analysis.treble;}
    static float bpm(){return analysis.bpm;}
    static int rmsMb(){return rmsMb;}
    static int peakMb(){return peakMb;}
    static int sampleRate(){return sampleRate/1000;}
    static boolean levelAvailable(){return available&&(rmsMb!=-9600||peakMb!=-9600);}
    static int sensitivity(Context c){return c.getSharedPreferences("neon_audio",Context.MODE_PRIVATE).getInt("sensitivity",100);}
    static void setSensitivity(Context c,int value){c.getSharedPreferences("neon_audio",Context.MODE_PRIVATE).edit().putInt("sensitivity",Math.max(25,Math.min(200,value))).apply();}
    static boolean localPlaying(Context c){
        android.media.session.MediaController ctl=MediaBridge.preferred(c);
        if(ctl==null||!MediaBridge.isPlaying(ctl))return false;
        android.media.session.MediaController.PlaybackInfo info=ctl.getPlaybackInfo();
        return info!=null&&info.getPlaybackType()==android.media.session.MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL;
    }
    static void resetForTrack(Context c){
        android.media.session.MediaController ctl=MediaBridge.preferred(c);
        android.media.MediaMetadata m=ctl==null?null:ctl.getMetadata();
        String key=ctl==null?"":ctl.getPackageName()+"|"+(m==null?"":m.getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID)+"|"+m.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)+"|"+m.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST));
        if(!key.equals(trackKey)){trackKey=key;analysis.reset();envelope=0;alpha=0;}
    }
    static boolean requested(Context c){return c.getSharedPreferences("neon_audio",Context.MODE_PRIVATE).getBoolean("enabled",false);}
    static void setRequested(Context c,boolean enabled){
        c.getSharedPreferences("neon_audio",Context.MODE_PRIVATE).edit().putBoolean("enabled",enabled).apply();
        retryAt=0;failed=false;if(!enabled)stop();
    }
    static boolean permitted(Context c){return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
    static boolean enabled(Context c){return requested(c)&&permitted(c);}
    static boolean available(){return available;}
    static int alpha(){return alpha;}
    static boolean failed(){return failed;}
    static boolean signal(){return signal;}
    static void stop(){
        if(meter!=null){try{meter.setEnabled(false);}catch(RuntimeException | LinkageError ignored){}
            try{meter.release();}catch(RuntimeException | LinkageError ignored){}meter=null;}
        envelope=0;alpha=0;lastSample=0;available=false;signal=false;measurement=null;fft=null;fftAvailable=false;sampleRate=0;rmsMb=peakMb=-9600;analysis.reset();
    }
    static void sample(Context c,boolean active){
        if(!active||!enabled(c)){stop();failed=false;retryAt=0;return;}
        long now=SystemClock.elapsedRealtime();
        if(now<retryAt||now-lastSample<45)return;
        try{
            if(meter==null){
                meter=new Visualizer(0);
                measurement=new Visualizer.MeasurementPeakRms();
                int[] range=Visualizer.getCaptureSizeRange();
                int size=Math.min(range[1],Math.max(range[0],1024));
                if(meter.setCaptureSize(size)==Visualizer.SUCCESS)fft=new byte[size];
                else fft=new byte[meter.getCaptureSize()];
                sampleRate=meter.getSamplingRate();
                meter.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
                meter.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS);
                if(meter.setEnabled(true)!=Visualizer.SUCCESS)throw new IllegalStateException("Visualizer unavailable");
            }
            boolean measured=meter.getMeasurementPeakRms(measurement)==Visualizer.SUCCESS;
            fftAvailable=fft!=null&&meter.getFft(fft)==Visualizer.SUCCESS&&sampleRate>0;
            if(!measured&&!fftAvailable)throw new IllegalStateException("Level unavailable");
            if(fftAvailable)analysis.accept(fft,sampleRate,now);else analysis.reset();
            rmsMb=measured?measurement.mRms:-9600;peakMb=measured?measurement.mPeak:-9600;
            available=true;failed=false;
            signal=(measured&&(rmsMb>-6500||peakMb>-6000))||(fftAvailable&&(analysis.bass>.01f||analysis.mid>.01f||analysis.treble>.01f));
            float rms=clamp((rmsMb+4500f)/3900f);
            float peak=clamp((peakMb+3000f)/2800f);
            float target=fftAvailable?Math.max(analysis.drive(),.5f*analysis.bass()+.3f*analysis.mid()+.2f*analysis.treble()):clamp(.68f*rms+.32f*peak);
            target=signal?clamp(target*sensitivity(c)/100f):0;
            double dt=lastSample==0?.10:Math.min(.5,(now-lastSample)/1000.0);
            double tau=target>envelope?.055:.28;
            envelope+=(target-envelope)*(float)(1-Math.exp(-dt/tau));
            lastSample=now;alpha=Math.min(220,Math.round(220*envelope/5f)*5);
        }catch(RuntimeException | LinkageError e){stop();failed=true;retryAt=now+30000L;android.util.Log.w("AudioReactive","Output mix level unavailable",e);}
    }
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
}
