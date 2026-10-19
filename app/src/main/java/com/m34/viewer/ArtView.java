package com.m34.viewer;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Random;

/** Original vector landscape artwork. No network or third-party assets required. */
final class ArtView extends View {
    final Catalog.Art art;
    Bitmap image;
    String previewStatus="Loading preview…";
    final Paint p=new Paint(3);
    final Path path=new Path();
    float zoom=1, dx=0,dy=0,lastX,lastY;
    boolean zoomable;
    final ScaleGestureDetector scale;
    final GestureDetector gestures;
    ArtView(Context c,Catalog.Art a) {
        super(c); art=a;
        setContentDescription(a.title+" by "+a.artist);
        gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(MotionEvent e){performClick();return true;}
            @Override public boolean onDoubleTap(MotionEvent e){zoom=zoom>1?1:2.5f;dx=(getWidth()/2f-e.getX())*(zoom-1);dy=(getHeight()/2f-e.getY())*(zoom-1);clampPan();invalidate();return true;}
        });
        scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d) { zoom=Math.max(1,Math.min(5,zoom*d.getScaleFactor())); if(zoom==1){dx=dy=0;} clampPan();invalidate(); return true; }
        });
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(!zoomable) return super.onTouchEvent(e);
        scale.onTouchEvent(e);gestures.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) { lastX=e.getX();lastY=e.getY(); }
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_UP){lastX=e.getX(e.getActionIndex()==0?1:0);lastY=e.getY(e.getActionIndex()==0?1:0);}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE && !scale.isInProgress() && e.getPointerCount()==1 && zoom>1) { dx+=e.getX()-lastX;dy+=e.getY()-lastY;lastX=e.getX();lastY=e.getY();clampPan();invalidate(); }
        return true;
    }
    void clampPan(){
        float w=getWidth(),h=getHeight();
        if(image!=null){float ratio=Math.min(w/image.getWidth(),h/image.getHeight());w=image.getWidth()*ratio;h=image.getHeight()*ratio;}
        float limitX=Math.max(0,(w*zoom-getWidth())/2),limitY=Math.max(0,(h*zoom-getHeight())/2);
        dx=Math.max(-limitX,Math.min(limitX,dx));dy=Math.max(-limitY,Math.min(limitY,dy));
    }
    @Override public boolean performClick() { return super.performClick(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.save(); c.translate(dx,dy);c.scale(zoom,zoom,getWidth()/2f,getHeight()/2f);
        if(image!=null) {
            float ratio=zoomable?Math.min((float)getWidth()/image.getWidth(),(float)getHeight()/image.getHeight()):Math.max((float)getWidth()/image.getWidth(),(float)getHeight()/image.getHeight());
            float w=image.getWidth()*ratio,h=image.getHeight()*ratio;
            c.drawBitmap(image,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),p);
        } else if(art.live()) {
            p.setColor(0xff222629);c.drawRect(0,0,getWidth(),getHeight(),p);
            p.setColor(0xffa2aaa5);p.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);p.setTextAlign(Paint.Align.CENTER);
            c.drawText(previewStatus,getWidth()/2f,getHeight()/2f,p);p.setTextAlign(Paint.Align.LEFT);
        } else drawLandscape(c,getWidth(),getHeight(),art.seed);
        c.restore();
    }
    void drawLandscape(Canvas c,float w,float h,int seed) {
        int[][] palettes={
            {0xff191b47,0xffea8c84,0xfff9c58d,0xff6d527c,0xff333752,0xff172e3b},
            {0xff316c91,0xffa5cfd2,0xfff4e7c8,0xff668b9a,0xff376577,0xff173d4f},
            {0xff443552,0xffcc8d86,0xffffe0ad,0xffa46471,0xff614c67,0xff273a4b},
            {0xff183844,0xff89b5a2,0xffe8dda8,0xff4c807c,0xff2c595e,0xff183c43},
            {0xff365d80,0xff9dbac3,0xffeee0bd,0xff688a9b,0xff365e76,0xff1b3b50},
            {0xff16203c,0xff696da0,0xfff5bfa5,0xff595c8c,0xff303c66,0xff132a44}};
        int[] colors=palettes[Math.floorMod(seed,palettes.length)]; Random r=new Random(seed*997L+17);
        p.setShader(new LinearGradient(0,0,w*.3f,h,colors[0],colors[1],Shader.TileMode.CLAMP)); c.drawRect(0,0,w,h,p);p.setShader(null);
        p.setColor(colors[2]);p.setAlpha(210);c.drawCircle(w*.73f,h*.23f,w*(seed%3==0?.19f:.10f),p);p.setAlpha(255);
        if(seed%3==0) { p.setColor(colors[0]);p.setAlpha(65);c.drawCircle(w*.78f,h*.20f,w*.18f,p);p.setAlpha(255); }
        if(seed%6==5 || seed%6==0) { p.setColor(0xffe8e3f4);for(int i=0;i<65;i++) {p.setAlpha(50+r.nextInt(180));c.drawCircle(r.nextFloat()*w,r.nextFloat()*h*.46f, .4f+r.nextFloat()*1.1f,p);}p.setAlpha(255); }
        for(int layer=0;layer<4;layer++) {
            float base=h*(.46f+layer*.13f); path.reset();path.moveTo(0,h);
            int steps=14;
            for(int i=0;i<=steps;i++) { float y=base-(float)r.nextDouble()*h*(.25f-layer*.025f);path.lineTo(i*w/steps,y); }
            path.lineTo(w,h);path.close();p.setColor(colors[Math.min(3+layer,5)]);c.drawPath(path,p);
            if(layer<2) { p.setColor(colors[2]);p.setAlpha(55);c.drawRect(0,base+h*.03f,w,base+h*.045f,p);p.setAlpha(255); }
        }
        // An illuminated river through the foreground.
        path.reset();path.moveTo(w*.57f,h*.60f);path.cubicTo(w*.25f,h*.73f,w*.85f,h*.84f,w*.38f,h);path.lineTo(w*.85f,h);path.cubicTo(w*.95f,h*.82f,w*.38f,h*.74f,w*.59f,h*.60f);path.close();
        p.setShader(new LinearGradient(0,h*.6f,0,h,colors[1],colors[0],Shader.TileMode.CLAMP));c.drawPath(path,p);p.setShader(null);
        for(int i=0;i<28;i++) { float yy=h*(.66f+i*.012f);p.setColor(colors[2]);p.setAlpha(50);c.drawRect(w*(.51f+r.nextFloat()*.13f),yy,w*.73f,yy+1,p); }p.setAlpha(255);
        if(seed%4==1) { // Castle silhouettes.
            for(int i=0;i<5;i++) {float x=w*(.12f+i*.09f),y=h*(.45f-(i%2)*.08f);p.setColor(colors[5]);c.drawRect(x,y,x+w*.065f,h*.72f,p);path.reset();path.moveTo(x-w*.015f,y);path.lineTo(x+w*.032f,y-h*.06f);path.lineTo(x+w*.08f,y);path.close();c.drawPath(path,p);p.setColor(colors[2]);c.drawRect(x+w*.027f,y+h*.04f,x+w*.039f,y+h*.07f,p);}
        }
        for(int i=0;i<16;i++) {
            float x=(i<8?w*.02f+i*w*.032f:w*.83f+(i-8)*w*.027f), y=h*(.79f+r.nextFloat()*.22f),size=h*(.08f+r.nextFloat()*.12f);
            p.setColor(colors[5]);c.drawRect(x-1,y-size,x+1,y+size*.12f,p);
            path.reset();path.moveTo(x,y-size);path.lineTo(x-size*.3f,y);path.lineTo(x+size*.3f,y);path.close();c.drawPath(path,p);
        }
        p.setShader(new LinearGradient(0,h*.65f,0,h,0x00101214,0xaa101214,Shader.TileMode.CLAMP));c.drawRect(0,h*.65f,w,h,p);p.setShader(null);
    }
    Bitmap export() { Bitmap b=Bitmap.createBitmap(1600,2000,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b); if(image!=null) c.drawBitmap(image,null,new Rect(0,0,1600,2000),p);else drawLandscape(c,1600,2000,art.seed);return b; }
}
