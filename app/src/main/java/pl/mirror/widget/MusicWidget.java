package pl.mirror.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;

public class MusicWidget extends AppWidgetProvider {
    private static final String PREVIOUS="pl.mirror.widget.PREVIOUS", TOGGLE="pl.mirror.widget.TOGGLE",
        NEXT="pl.mirror.widget.NEXT", CUSTOM="pl.mirror.widget.CUSTOM", SEEK="pl.mirror.widget.SEEK";
    private static final int[] SEEK_ZONES={R.id.seek_zone_0,R.id.seek_zone_1,R.id.seek_zone_2,R.id.seek_zone_3,R.id.seek_zone_4,R.id.seek_zone_5,R.id.seek_zone_6,R.id.seek_zone_7,R.id.seek_zone_8,R.id.seek_zone_9,R.id.seek_zone_10,R.id.seek_zone_11,R.id.seek_zone_12,R.id.seek_zone_13,R.id.seek_zone_14,R.id.seek_zone_15,R.id.seek_zone_16,R.id.seek_zone_17,R.id.seek_zone_18,R.id.seek_zone_19,R.id.seek_zone_20,R.id.seek_zone_21,R.id.seek_zone_22,R.id.seek_zone_23,R.id.seek_zone_24,R.id.seek_zone_25,R.id.seek_zone_26,R.id.seek_zone_27,R.id.seek_zone_28,R.id.seek_zone_29,R.id.seek_zone_30,R.id.seek_zone_31,R.id.seek_zone_32,R.id.seek_zone_33,R.id.seek_zone_34,R.id.seek_zone_35,R.id.seek_zone_36,R.id.seek_zone_37,R.id.seek_zone_38,R.id.seek_zone_39,R.id.seek_zone_40,R.id.seek_zone_41,R.id.seek_zone_42,R.id.seek_zone_43,R.id.seek_zone_44,R.id.seek_zone_45,R.id.seek_zone_46,R.id.seek_zone_47,R.id.seek_zone_48,R.id.seek_zone_49,R.id.seek_zone_50};
    private static final int[] WAVE_FRAMES={R.id.wave_frame_0,R.id.wave_frame_1,R.id.wave_frame_2,R.id.wave_frame_3,R.id.wave_frame_4,R.id.wave_frame_5,R.id.wave_frame_6,R.id.wave_frame_7,R.id.wave_frame_8,R.id.wave_frame_9,R.id.wave_frame_10,R.id.wave_frame_11,R.id.wave_frame_12,R.id.wave_frame_13,R.id.wave_frame_14,R.id.wave_frame_15,R.id.wave_frame_16,R.id.wave_frame_17,R.id.wave_frame_18,R.id.wave_frame_19,R.id.wave_frame_20,R.id.wave_frame_21,R.id.wave_frame_22,R.id.wave_frame_23,R.id.wave_frame_24,R.id.wave_frame_25,R.id.wave_frame_26,R.id.wave_frame_27,R.id.wave_frame_28,R.id.wave_frame_29,R.id.wave_frame_30,R.id.wave_frame_31};

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id:ids) updateOne(c,m,id,false);
    }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options) {
        super.onAppWidgetOptionsChanged(c,m,id,options);
        updateOne(c,m,id,false);
    }
    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context,intent);
        String action=intent==null?null:intent.getAction();
        if (!PREVIOUS.equals(action)&&!NEXT.equals(action)&&!TOGGLE.equals(action)&&!CUSTOM.equals(action)&&!SEEK.equals(action)) return;
        MediaController c=MediaBridge.preferred(context);
        if (c==null) return;
        PlaybackState state=c.getPlaybackState();
        if (state==null) return;
        MediaSession.Token expected=intent.getParcelableExtra("token");
        if (expected==null||!expected.equals(c.getSessionToken())) return;
        long supported=state.getActions();
        try {
            MediaController.TransportControls t=c.getTransportControls();
            if (SEEK.equals(action)) {
                MediaMetadata current=c.getMetadata();
                long duration=current==null?0:current.getLong(MediaMetadata.METADATA_KEY_DURATION);
                int step=intent.getIntExtra("seek_step",-1);
                if ((supported&PlaybackState.ACTION_SEEK_TO)==0 || duration<=0
                        || step<0 || step>=SEEK_ZONES.length
                        || !trackIdentity(current).equals(intent.getStringExtra("seek_track"))
                        || duration!=intent.getLongExtra("seek_duration",-1L)) return;
                t.seekTo(Math.round(duration*(step/(double)(SEEK_ZONES.length-1))));
                // Let the player acknowledge the new position, avoiding a redraw
                // of the old position immediately after sending the seek command.
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    ()->refreshAll(context.getApplicationContext()),350L);
                return;
            }
            else if (PREVIOUS.equals(action)&&(supported&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0) t.skipToPrevious();
            else if (NEXT.equals(action)&&(supported&PlaybackState.ACTION_SKIP_TO_NEXT)!=0) t.skipToNext();
            else if (TOGGLE.equals(action)) {
                if (MediaBridge.isPlaying(c)) {
                    if ((supported&(PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0) t.pause();
                } else if ((supported&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))!=0) t.play();
            } else if (CUSTOM.equals(action)) {
                String requested=intent.getStringExtra("custom");
                for (PlaybackState.CustomAction item:state.getCustomActions()) {
                    if (item.getAction().equals(requested)) {
                        t.sendCustomAction(item,item.getExtras()); break;
                    }
                }
            }
        } catch(RuntimeException ignored) { }
        refreshAll(context);
    }
    static void refreshAll(Context c) { updateAll(c,false); }
    // The cached frame only contains the tiny progress layer and a clock anchor.
    // All methods are called on the application's main looper.
    private static final java.util.Map<Integer, ProgressFrame> frames=new java.util.HashMap<>();
    private static final class ProgressFrame {
        final int layout, width, height, color;
        final float density;
        final PlaybackState state;
        final long duration;
        ProgressFrame(int layout,int width,int height,float density,int color,PlaybackState state,long duration) {
            this.layout=layout;this.width=width;this.height=height;this.density=density;
            this.color=color;this.state=state;this.duration=duration;
        }
        boolean playing() {
            return state!=null&&state.getState()==PlaybackState.STATE_PLAYING&&duration>0
                &&state.getPosition()>=0&&state.getPlaybackSpeed()>0;
        }
    }
    @Override public void onDeleted(Context c,int[] ids) {
        for(int id:ids)frames.remove(id);
        super.onDeleted(c,ids);
    }
    static boolean refreshProgress(Context c) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        boolean animated=false;
        long now=android.os.SystemClock.elapsedRealtime();
        for(java.util.Map.Entry<Integer,ProgressFrame> entry:frames.entrySet()) {
            ProgressFrame f=entry.getValue();
            if(!f.playing())continue;
            animated=true;
            try {
                RemoteViews delta=new RemoteViews(c.getPackageName(),f.layout);
                updateWave(delta,MediaBridge.fraction(f.state,f.duration,now),f.width,f.height,f.density,f.color,true,now,false);
                manager.partiallyUpdateAppWidget(entry.getKey(),delta);
            } catch(RuntimeException e) { android.util.Log.w("MusicWidget","Progress refresh failed",e); }
        }
        return animated;
    }
    static boolean hasWidgets(Context c) {
        return widgetCount(c)>0;
    }
    static int widgetCount(Context c) {
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        int count=0;
        for (Class<?> provider:providers()) count+=m.getAppWidgetIds(new ComponentName(c,provider)).length;
        return count;
    }
    static Class<?>[] providers() {
        return new Class<?>[]{MusicWidget.class,MusicWidgetMedium.class,MusicWidgetLarge.class};
    }
    private static void updateAll(Context c,boolean progressOnly) {
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        for (Class<?> provider:providers()) for (int id:m.getAppWidgetIds(new ComponentName(c,provider))) {
            try { updateOne(c,m,id,progressOnly); }
            catch(RuntimeException e) { android.util.Log.w("MusicWidget","Widget refresh failed", e); }
        }
    }

    private static void updateOne(Context context,AppWidgetManager manager,int id,boolean progressOnly) {
        Bundle options=manager.getAppWidgetOptions(id);
        android.appwidget.AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
        int fallback=120;
        if(info!=null) {
            String name=info.provider.getClassName();
            if(name.endsWith("MusicWidgetMedium")) fallback=184;
            if(name.endsWith("MusicWidgetLarge")) fallback=248;
        }
        android.util.SizeF size=portraitSize(context,options,fallback);
        int width=Math.round(size.getWidth()),height=Math.round(size.getHeight());
        RemoteViews v=buildViews(context,id,width,height,progressOnly);
        if(v==null)return;
        if(progressOnly)manager.partiallyUpdateAppWidget(id,v); else manager.updateAppWidget(id,v);
    }

    static android.util.SizeF portraitSize(Context context, Bundle options, int fallback) {
        if(options==null) return new android.util.SizeF(330,fallback);
        int minW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,0);
        int minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,0);
        int maxW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,0);
        int maxH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,0);
        boolean portrait=context.getResources().getConfiguration().orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if(android.os.Build.VERSION.SDK_INT>=31){
            java.util.ArrayList<android.util.SizeF> sizes=options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            if(sizes!=null&&!sizes.isEmpty()){
                android.util.SizeF choice=null;
                for(android.util.SizeF s:sizes){
                    if(s==null||s.getWidth()<100||s.getHeight()<60)continue;
                    if(choice==null || (portrait?s.getWidth()<choice.getWidth():s.getWidth()>choice.getWidth())
                        ||(s.getWidth()==choice.getWidth()&&s.getHeight()>choice.getHeight()))choice=s;
                }
                if(choice!=null)return choice;
            }
        }
        int width=portrait?minW:maxW, height=portrait?maxH:minH;
        return new android.util.SizeF(width>=100?width:330,height>=60?height:fallback);
    }

    static RemoteViews preview(Context context,int width,int height) {
        return buildViews(context,-1,width,height,false);
    }

    private static RemoteViews buildViews(Context context,int id,int width,int height,boolean progressOnly) {
        MediaController c=MediaBridge.preferred(context);
        MediaMetadata meta=c==null?null:c.getMetadata();
        PlaybackState state=c==null?null:c.getPlaybackState();
        boolean playing=MediaBridge.isPlaying(c);
        if (progressOnly&&!playing) return null;
        boolean preview=id<0;
        long supported=state==null?0:state.getActions();
        String player=c==null?"com.spotify.music":c.getPackageName();
        boolean compact=height<175;
        float density=context.getResources().getDisplayMetrics().density;
        ArtRenderer.Artwork art=ArtRenderer.get(context,meta,player);
        List<MediaActions.Item> extras=MediaActions.get(context,c,art.primary);
        int layout=height<102?R.layout.widget_player_short:compact?R.layout.widget_player_compact:height>=222?R.layout.widget_player_large:R.layout.widget_player;
        RemoteViews v=new RemoteViews(context.getPackageName(),layout);
        long now=android.os.SystemClock.elapsedRealtime();
        long duration=meta==null?0:meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
        double fraction=preview&&meta==null?.35:MediaBridge.fraction(state,duration,now);
        int progress=(int)Math.round(fraction*1000);
        int iconWidth=height<102?30:compact?34:40;
        int availableWidth=Math.max(30,width-(compact?24:32)-(2+extras.size())*iconWidth-8);
        int waveWidth=Math.min(720,Math.max(45,Math.round(availableWidth*density)));
        int waveHeight=Math.min(100,Math.max(14,Math.round(24*density)));
        updateWave(v,fraction,waveWidth,waveHeight,density,art.primary,playing,now,!progressOnly);
        if(!preview&&!progressOnly)frames.put(id,new ProgressFrame(layout,waveWidth,waveHeight,density,art.primary,state,duration));
        v.setContentDescription(R.id.wave_progress,"Postęp "+progress/10+" procent.");
        if (progressOnly) return v;

        // One crop, by ImageView at its actual measured size. Rounded outline is
        // on the parent independently, so changing dimensions cannot clip off corners.
        v.setViewVisibility(R.id.art_background,art.cover==null?View.GONE:View.VISIBLE);
        if (art.cover!=null) v.setImageViewBitmap(R.id.art_background,art.cover);
        v.setImageViewBitmap(R.id.art_scrim,art.scrim);
        String title=metadata(meta,MediaMetadata.METADATA_KEY_TITLE,MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String artist=metadata(meta,MediaMetadata.METADATA_KEY_ARTIST,MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        v.setTextViewText(R.id.song,title==null?(preview?"Przykładowy utwór":"Włącz muzykę"):title);
        v.setTextViewText(R.id.artist,artist==null?(preview?"Podgląd widżetu":"Otwórz Spotify"):artist);
        v.setTextColor(R.id.song,art.primary);
        v.setTextColor(R.id.artist,art.secondary);
        v.setTextViewText(R.id.route_name,MediaBridge.outputDevice(context));
        v.setImageViewResource(R.id.toggle,playing?R.drawable.ic_pause:R.drawable.ic_play);
        v.setContentDescription(R.id.toggle,playing?"Wstrzymaj":"Odtwórz");

        if (Build.VERSION.SDK_INT>=31) {
            v.setColorStateList(R.id.widget_root,"setBackgroundTintList",ColorStateList.valueOf(art.surface));
            v.setColorStateList(R.id.route_chip,"setBackgroundTintList",ColorStateList.valueOf(art.accent));
            v.setColorStateList(R.id.toggle,"setBackgroundTintList",ColorStateList.valueOf(art.accent));
            v.setColorStateList(R.id.toggle,"setImageTintList",ColorStateList.valueOf(art.ink));
            v.setColorStateList(R.id.route_icon,"setImageTintList",ColorStateList.valueOf(art.ink));
            v.setTextColor(R.id.route_name,art.ink);
            v.setColorStateList(R.id.prev,"setImageTintList",ColorStateList.valueOf(art.primary));
            v.setColorStateList(R.id.next,"setImageTintList",ColorStateList.valueOf(art.primary));
        }

        if ("com.spotify.music".equals(player)) {
            v.setImageViewBitmap(R.id.spotify_logo,spotifyMark(art.accent,art.ink));
        } else {
            try {
                Bitmap logo=MediaActions.bitmap(context.getPackageManager().getApplicationIcon(player),null);
                if (logo!=null) v.setImageViewBitmap(R.id.spotify_logo,logo);
            } catch(Exception ignored) { v.setImageViewResource(R.id.spotify_logo,R.drawable.ic_app); }
        }
        if(preview) {
            int[] ids={R.id.shuffle,R.id.liked};
            for(int i=0;i<ids.length;i++) {
                v.setViewVisibility(ids[i],i<extras.size()?View.VISIBLE:View.GONE);
                if(i<extras.size()) v.setImageViewBitmap(ids[i],extras.get(i).icon);
            }
            return v;
        }
        PendingIntent open=playerIntent(context,id,c);
        v.setOnClickPendingIntent(R.id.widget_root,open);
        v.setOnClickPendingIntent(R.id.art_background,open);
        v.setOnClickPendingIntent(R.id.art_scrim,open);
        v.setOnClickPendingIntent(R.id.spotify_logo,open);
        v.setOnClickPendingIntent(R.id.song,open);
        v.setOnClickPendingIntent(R.id.artist,open);

        boolean prev=(supported&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0;
        boolean next=(supported&PlaybackState.ACTION_SKIP_TO_NEXT)!=0;
        boolean toggle=(supported&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0;
        v.setInt(R.id.prev,"setImageAlpha",prev?255:100);
        v.setInt(R.id.next,"setImageAlpha",next?255:100);
        v.setOnClickPendingIntent(R.id.prev,prev?command(context,id,1,PREVIOUS,c,null):null);
        v.setOnClickPendingIntent(R.id.next,next?command(context,id,2,NEXT,c,null):null);
        v.setOnClickPendingIntent(R.id.toggle,toggle?command(context,id,3,TOGGLE,c,null):c==null?open:null);
        boolean canSeek=c!=null&&duration>0&&(supported&PlaybackState.ACTION_SEEK_TO)!=0;
        v.setViewVisibility(R.id.seek_touch_zones,canSeek?View.VISIBLE:View.GONE);
        v.setContentDescription(R.id.wave_progress,canSeek?"Postęp odtwarzania":"Przewijanie niedostępne");
        if(canSeek) {
            for(int i=0;i<SEEK_ZONES.length;i++) {
                long position=Math.round(duration*(i/(double)(SEEK_ZONES.length-1)));
                long sec=position/1000L;
                v.setContentDescription(SEEK_ZONES[i],String.format(java.util.Locale.ROOT,
                    "Przewiń do %d:%02d (%d procent)",sec/60,sec%60,Math.round(i*100f/(SEEK_ZONES.length-1))));
                v.setOnClickPendingIntent(SEEK_ZONES[i],seekIntent(context,id,c,meta,duration,i));
            }
        }
        v.setOnClickPendingIntent(R.id.route_chip,activity(context,id,7,"output"));

        int[] actionIds={R.id.shuffle,R.id.liked};
        for (int i=0;i<actionIds.length;i++) {
            int view=actionIds[i];
            if (i>=extras.size()) {
                v.setViewVisibility(view,View.GONE);
                v.setOnClickPendingIntent(view,null);
            } else {
                MediaActions.Item action=extras.get(i);
                v.setViewVisibility(view,View.VISIBLE);
                v.setImageViewBitmap(view,action.icon);
                v.setContentDescription(view,action.name);
                v.setOnClickPendingIntent(view,action.customAction!=null
                    ?command(context,id,10+i,CUSTOM,c,action.customAction):action.notificationAction);
            }
        }
        return v;
    }
    private static String metadata(MediaMetadata m,String...keys) {
        if (m==null) return null;
        for (String key:keys) { String s=m.getString(key); if (s!=null&&!s.isEmpty()) return s; }
        return null;
    }
    private static PendingIntent command(Context c,int id,int code,String action,MediaController controller,String custom) {
        Intent intent=new Intent(c,MusicWidget.class).setAction(action)
            .putExtra("token",controller.getSessionToken()).putExtra("custom",custom);
        return PendingIntent.getBroadcast(c,id*32+code,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void updateWave(RemoteViews v,double fraction,int width,int height,float density,int color,boolean playing,long now,boolean full) {
        boolean local=Build.VERSION.SDK_INT>=31&&playing;
        if(Build.VERSION.SDK_INT>=31) {
            v.setViewVisibility(R.id.wave_clip,local?View.VISIBLE:View.GONE);
            v.setViewLayoutWidth(R.id.wave_clip,Math.max(0f,(width/density-8f)*(float)fraction),android.util.TypedValue.COMPLEX_UNIT_DIP);
            if(full) for(int frame:WAVE_FRAMES) v.setColorStateList(frame,"setImageTintList",ColorStateList.valueOf(color));
        }
        v.setImageViewBitmap(R.id.wave_progress,WaveRenderer.drawLayer(fraction,width,height,density,color,playing,now,local));
    }
    private static String trackIdentity(MediaMetadata m) {
        if(m==null)return "";
        return String.valueOf(m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))+"|"
            +m.getString(MediaMetadata.METADATA_KEY_TITLE)+"|"
            +m.getString(MediaMetadata.METADATA_KEY_ARTIST)+"|"
            +m.getString(MediaMetadata.METADATA_KEY_ALBUM);
    }
    private static PendingIntent seekIntent(Context c,int id,MediaController controller,MediaMetadata m,long duration,int step) {
        Intent intent=new Intent(c,MusicWidget.class).setAction(SEEK)
            .setData(android.net.Uri.parse("muzyka-widget://seek/"+id+"/"+step))
            .putExtra("token",controller.getSessionToken())
            .putExtra("seek_track",trackIdentity(m))
            .putExtra("seek_duration",duration)
            .putExtra("seek_step",step);
        return PendingIntent.getBroadcast(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent activity(Context c,int id,int code,String action) {
        Intent i=new Intent(c,PlayerControlsActivity.class).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c,id*32+code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent playerIntent(Context c,int id,MediaController controller) {
        PendingIntent notification=MusicListener.notificationContentIntent(controller);
        if(notification!=null)return notification;
        if (controller!=null&&controller.getSessionActivity()!=null) return controller.getSessionActivity();
        String pkg=controller==null?"com.spotify.music":controller.getPackageName();
        Intent intent=c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent==null) intent=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://open.spotify.com"));
        return PendingIntent.getActivity(c,id*32+8,intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static Bitmap spotifyMark(int bg,int fg) {
        Bitmap b=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bg); c.drawCircle(48,48,45,p);
        p.setColor(fg); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
        float[][] curves={{20,36,34,30,57,30,76,41},{23,49,37,44,55,46,72,54},{26,62,40,58,54,60,66,67}};
        for(int i=0;i<3;i++) {
            float[] q=curves[i];
            p.setStrokeWidth(7-i);
            Path path=new Path(); path.moveTo(q[0],q[1]); path.cubicTo(q[2],q[3],q[4],q[5],q[6],q[7]); c.drawPath(path,p);
        }
        return b;
    }
}
