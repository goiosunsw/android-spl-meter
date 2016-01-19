package com.google.speech.levelmeter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by goios on 19/01/16.
 */
public final class HzDbGraph extends SurfaceView{

    private SurfaceHolder _surfaceHolder;
    //private SurfaceView _surfaceView;
    private int radius = 5;
    //private Canvas canvas = null;
    //private SurfaceView surfaceView = null;

    public float maxFreq = 2000;
    public float minFreq = 100;
    public float mindB = 10;
    public float maxdB = 100;

    private float xScale = 1;
    private float yScale = 1;
    private float xOffset = 0;
    private float yOffset = 0;

    private float freqRef = 440;
    private float log2 = (float) Math.log(2);


    public HzDbGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        //surfaceView = super.getRootView();
    }

    private void init() {
        _surfaceHolder = getHolder();

        _surfaceHolder.addCallback(new SurfaceHolder.Callback() {

            /**
             * Called when the activity is first created.
             */

            public void surfaceCreated(SurfaceHolder holder) {

            }

            public void surfaceDestroyed(SurfaceHolder holder) {
            }

            public void surfaceChanged(SurfaceHolder holder, int frmt, int w, int h) {
                recalcScaling(w,h);
            }
        });

    }

    private void recalcScaling(int w, int h) {
        float dfreq = maxFreq - minFreq;
        float ddB = maxdB - mindB;
        xScale = w / ddB;
        yScale = h / dfreq;
        xOffset = mindB * xScale;
        yOffset = minFreq * yScale;
    }

    private float calcDetune(float freq) {
        float semitones = (float) Math.log(freq/freqRef)/log2 * 12f;
        //float st5 = semitones + .5f;
        float tempdet = semitones - Math.round(semitones);
        System.out.println("########## F="+freq+" Det="+tempdet);
        return tempdet ;
    }

    public void drawDbFreq(float dB, float freq) {
        if (freq > minFreq & freq < maxFreq) {
            int detune = (int) (256 * calcDetune(freq)) +128;
            drawPointAt((int) (dB * xScale + xOffset), (int) (freq * yScale + yOffset), detune);
        }
    }

    public void drawPointAt(int x, int y, int colorval) {
        Canvas canvas = null;
        try {
            canvas = _surfaceHolder.lockCanvas();
            Paint paint = new Paint();
            paint.setARGB(128, 256-colorval, colorval, colorval);
            canvas.drawCircle(x, y, radius, paint);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (canvas != null) {

                _surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    public void cleanSurface() {
        Canvas canvas = null;
        try {
            canvas = _surfaceHolder.lockCanvas();
            canvas.drawARGB(0,0,0,0);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (canvas != null) {

                _surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }

    }

}
