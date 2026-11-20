package pl.mirror.widget;
import java.util.Arrays;

/** FFT magnitudes and conservative onset-based tempo estimate. All levels are relative, not SPL. */
final class SpectrumAnalysis {
    final float[] bands=new float[24];
    float bass,mid,treble,bpm;
    private float envelope,noise,previous;
    private long lastBeat,lastTime;
    private final long[] intervals=new long[12];
    private int count,cursor;
    private boolean armed=true;
    void reset(){
        Arrays.fill(bands,0);Arrays.fill(intervals,0);
        bass=mid=treble=bpm=envelope=noise=previous=0;
        lastBeat=lastTime=0;count=cursor=0;armed=true;
    }
    static float clamp(float x){return Math.max(0,Math.min(1,x));}
    private static float level(double energy,int count){
        return count==0?0:clamp((float)(Math.log1p(Math.sqrt(energy/count))/Math.log(129)));
    }
    void accept(byte[] fft,int sampleRateMilliHz,long now){
        if(fft==null||fft.length<4||sampleRateMilliHz<=0){reset();return;}
        double hzPerBin=sampleRateMilliHz/1000.0/fft.length;
        double[] power=new double[bands.length],group=new double[3];
        int[] bins=new int[bands.length],sizes=new int[3];
        double upper=Math.min(20000,sampleRateMilliHz/2000.0);
        if(upper<=20){reset();return;}
        for(int k=1;k<fft.length/2;k++){
            double hz=k*hzPerBin;
            if(hz<20||hz>upper)continue;
            int re=fft[2*k],im=fft[2*k+1];
            double p=re*re+im*im;
            int i=Math.max(0,Math.min(bands.length-1,(int)(Math.log(hz/20)/Math.log(upper/20)*bands.length)));
            power[i]+=p;bins[i]++;
            int g=hz<250?0:hz<4000?1:2;
            group[g]+=p;sizes[g]++;
        }
        for(int i=0;i<bands.length;i++){
            float next=level(power[i],bins[i]);
            bands[i]+=(next-bands[i])*(next>bands[i]?.7f:.35f);
        }
        bass=level(group[0],sizes[0]);mid=level(group[1],sizes[1]);treble=level(group[2],sizes[2]);
        if(lastTime==0||now-lastTime>350){lastBeat=0;count=cursor=0;bpm=0;noise=bass;previous=bass;armed=true;}
        // Relative bass transient from the measured spectrum, not a random beat.
        float rise=bass-previous;
        noise+=(bass-noise)*.07f;
        float excess=bass-noise;
        if(excess<.045f||rise<-.025f)armed=true;
        if(armed&&bass>.16f&&rise>.025f&&excess>.055f&&(lastBeat==0||now-lastBeat>=280)){
            armed=false;
            if(lastBeat!=0){
                long gap=now-lastBeat;
                if(gap>=280&&gap<=1200){
                    intervals[cursor]=gap;cursor=(cursor+1)%intervals.length;count=Math.min(intervals.length,count+1);
                    estimate();
                }else if(gap>1200){count=cursor=0;bpm=0;}
            }
            lastBeat=now;
        }
        if(lastBeat!=0&&now-lastBeat>2200){bpm=0;count=cursor=0;lastBeat=0;}
        previous=bass;lastTime=now;
        float target=clamp(bass*.70f+Math.max(0,excess)*1.7f+Math.max(0,rise)*.7f);
        envelope+=(target-envelope)*(target>envelope?.75f:.22f);
    }
    private void estimate(){
        if(count<5){bpm=0;return;}
        long[] sorted=Arrays.copyOf(intervals,count);Arrays.sort(sorted);
        float median=sorted[count/2];int agreeing=0;float sum=0;
        for(long v:sorted)if(Math.abs(v-median)<=median*.18f){agreeing++;sum+=v;}
        if(agreeing>=Math.ceil(count*.72)&&agreeing>=5){
            float estimate=60000f/(sum/agreeing);
            while(estimate<65)estimate*=2;while(estimate>210)estimate/=2;
            bpm=estimate;
        }else bpm=0;
    }
    float drive(){return envelope;}
}
