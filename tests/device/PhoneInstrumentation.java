package com.m34.viewer.qa;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.provider.MediaStore;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import java.util.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;

/** Temporary, same-signature ADB instrumentation; no account/store reads. */
public final class PhoneInstrumentation extends Instrumentation {
    int passed;
    Activity activity;
    View artwork;
    Dialog dialog;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    static Field field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
    float value(String name)throws Exception{return field(artwork,name).getFloat(artwork);}
    void check(boolean condition,String message){if(!condition)throw new AssertionError(message);passed++;Bundle status=new Bundle();status.putString("stream","PASS: "+message+"\n");sendStatus(0,status);}
    View find(View view){if(view.getClass().getName().equals("com.m34.viewer.ArtView"))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i));if(found!=null)return found;}}return null;}
    void inject(MotionEvent event){event.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(event);event.recycle();}
    void tap(float x,float y){long t=SystemClock.uptimeMillis();inject(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0));SystemClock.sleep(25);inject(MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0));}
    void doubleTap(float x,float y){tap(x,y);SystemClock.sleep(60);tap(x,y);SystemClock.sleep(350);waitForIdleSync();}
    void pointer(long down,int action,float x,float y,float spread){
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] points=new MotionEvent.PointerCoords[2];
        for(int i=0;i<2;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;points[i]=new MotionEvent.PointerCoords();points[i].x=x+(i==0?-spread:spread);points[i].y=y;points[i].pressure=1;points[i].size=.1f;}
        MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,props,points,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);inject(e);
    }
    @Override public void onStart(){
        Bundle result=new Bundle();int exitCode=Activity.RESULT_CANCELED;AtomicReference<String> cleanupPattern=new AtomicReference<>();
        try{
            activity=startActivitySync(new Intent().setComponent(new ComponentName("com.m34.viewer","com.m34.viewer.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            AtomicReference<Throwable> error=new AtomicReference<>();
            runOnMainSync(()->{try{
                Class<?> art=Class.forName("com.m34.viewer.Catalog$Art");Object sample=art.getConstructor(String.class,String.class,String.class,String.class,int.class,int.class,String.class,String.class,String.class,int.class,int.class).newInstance("qa-only","QA gesture fixture","QA","landscape",0,0,"","","g",400,800);
                Method open=activity.getClass().getDeclaredMethod("zoom",art);open.setAccessible(true);open.invoke(activity,sample);dialog=(Dialog)field(activity,"artworkDialog").get(activity);artwork=find(dialog.getWindow().getDecorView());
                field(artwork,"image").set(artwork,Bitmap.createBitmap(400,800,Bitmap.Config.RGB_565));artwork.invalidate();
            }catch(Throwable e){error.set(e);}});
            if(error.get()!=null)throw new RuntimeException(error.get());waitForIdleSync();SystemClock.sleep(250);
            check(dialog.isShowing()&&artwork.getWidth()>0,"fullscreen viewer laid out");
            int[] location=new int[2];runOnMainSync(()->artwork.getLocationOnScreen(location));float x=location[0]+artwork.getWidth()/2f,y=location[1]+artwork.getHeight()/2f;
            check(Math.abs(value("zoom")-1)<.01,"initial zoom fits image");
            doubleTap(x,y);check(Math.abs(value("zoom")-2.5)<.05,"double-tap zooms to 2.5x");
            long down=SystemClock.uptimeMillis();inject(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0));SystemClock.sleep(30);inject(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x+100,y+100,0));inject(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x+100,y+100,0));waitForIdleSync();
            check(value("dx")>0&&value("dy")>0,"zoomed artwork pans");
            SystemClock.sleep(350);doubleTap(x,y);check(Math.abs(value("zoom")-1)<.01&&Math.abs(value("dx"))<.01&&Math.abs(value("dy"))<.01,"second double-tap resets zoom and pan");
            float initial=artwork.getWidth()*.1f,end=artwork.getWidth()*.42f;
            down=SystemClock.uptimeMillis();inject(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x-initial,y,0));pointer(down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y,initial);
            for(int i=1;i<=12;i++){SystemClock.sleep(35);pointer(down,MotionEvent.ACTION_MOVE,x,y,initial+(end-initial)*i/12);}
            pointer(down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y,end);inject(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x-end,y,0));waitForIdleSync();
            check(value("zoom")>1.2&&value("zoom")<=5,"two-finger pinch changes zoom within bounds (zoom="+value("zoom")+", minimum span="+ViewConfiguration.get(activity).getScaledMinimumScalingSpan()+")");
            runOnMainSync(()->dialog.dismiss());
            AtomicReference<Object> samplePost=new AtomicReference<>();
            for(int retry=0;retry<100&&samplePost.get()==null;retry++){
                runOnMainSync(()->{try{List<?> posts=(List<?>)field(activity,"livePosts").get(activity);if(!posts.isEmpty())samplePost.set(posts.get(0));}catch(Exception e){throw new RuntimeException(e);}});
                if(samplePost.get()==null)SystemClock.sleep(200);
            }
            check(samplePost.get()!=null,"live feed metadata available for transfer test");
            Class<?> artType=samplePost.get().getClass();String preview=(String)artType.getField("preview").get(samplePost.get());String qaId="r34-qa-transfer-"+System.currentTimeMillis();
            cleanupPattern.set("M34-"+qaId+"-%");
            Object downloadArt=artType.getConstructor(String.class,String.class,String.class,String.class,int.class,int.class,String.class,String.class,String.class,int.class,int.class).newInstance(qaId,"QA transfer","QA","",0,0,preview,preview,"g",1,1);
            AtomicReference<Boolean> background=new AtomicReference<>(false);AtomicReference<Throwable> downloadError=new AtomicReference<>();
            runOnMainSync(()->{try{Method download=activity.getClass().getDeclaredMethod("download",artType,boolean.class);download.setAccessible(true);download.invoke(activity,downloadArt,false);download.invoke(activity,downloadArt,false);background.set(activity.moveTaskToBack(true));}catch(Throwable e){downloadError.set(e);}});
            if(downloadError.get()!=null)throw new RuntimeException(downloadError.get());
            check(background.get(),"M34 moved to background immediately after starting transfer");
            Uri downloaded=null;long size=0;
            for(int retry=0;retry<150&&downloaded==null;retry++){
                try(Cursor rows=activity.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{MediaStore.MediaColumns._ID,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.IS_PENDING},MediaStore.MediaColumns.DISPLAY_NAME+" LIKE ?",new String[]{"M34-"+qaId+"-%"},null)){
                    if(rows!=null&&rows.moveToFirst()&&rows.getInt(2)==0&&rows.getLong(1)>0){downloaded=ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,rows.getLong(0));size=rows.getLong(1);}
                }
                if(downloaded==null)SystemClock.sleep(200);
            }
            check(downloaded!=null&&size>0,"preview transfer completes while app is backgrounded");
            check(activity.getContentResolver().delete(downloaded,null,null)==1,"only the uniquely named QA download is removed");
            result.putString("stream","Passed "+passed+" on-device gesture and transfer assertions\n");exitCode=Activity.RESULT_OK;
        }catch(Throwable e){result.putString("stream","FAILED after "+passed+" assertions: "+e.toString()+"\n");}
        finally{
            if(cleanupPattern.get()!=null&&activity!=null)try(Cursor leftovers=activity.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{MediaStore.MediaColumns._ID},MediaStore.MediaColumns.DISPLAY_NAME+" LIKE ?",new String[]{cleanupPattern.get()},null)){if(leftovers!=null)while(leftovers.moveToNext())activity.getContentResolver().delete(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,leftovers.getLong(0)),null,null);}catch(Exception ignored){}
            if(dialog!=null)runOnMainSync(()->dialog.dismiss());
        }
        finish(exitCode,result);
    }
}
