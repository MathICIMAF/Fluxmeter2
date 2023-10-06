/**
 * Spectrogram Android application
 * Copyright (c) 2013 Guillaume Adam  http://www.galmiza.net/

 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the authors be held liable for any damages arising from the use of this software.
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it freely,
 * subject to the following restrictions:

 * 1. The origin of this software must not be misrepresented; you must not claim that you wrote the original software. If you use this software in a product, an acknowledgment in the product documentation would be appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package com.iimas.fluxmeter2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Class associated with the wave form view
 * Handles events:
 *  onTouchEvent, onScroll, onDraw
 */
public class TimeView extends View {
	
	// Attributes
    private Paint paint = new Paint();
    private GestureDetector detector;
    private float gain = 0.0001f;
    private int fftResolution;
    private double[] wave;
    
    // Window
    public TimeView(Context context) {
        super(context);
        detector = new GestureDetector(getContext(), new GestureListener());
    }
    public TimeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        detector = new GestureDetector(getContext(), new GestureListener());
    }
    
    /**
     * Touch event handling
     */
    @SuppressLint("ClickableViewAccessibility")
	@Override
    public boolean onTouchEvent(MotionEvent event) {
	    detector.onTouchEvent(event);
	    invalidate();
	    return true;
    }
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
    	@Override
        public boolean onScroll (MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    		gain *= (1.0f+distanceY*0.01f);
        	return true;
        }
    }
    
    /**
     * Simple sets
     */
    public void setFFTResolution(int res) {
    	fftResolution = res;
    	wave = new double[res];
    }
    public void setWave(double[] w) {
    	System.arraycopy(w, 0, wave, 0, w.length);
    }

    /**
     * Called whenever a redraw is needed
     * Renders wave form as a series of lines
     */
    @Override
    public void onDraw(Canvas canvas) {
    	int width = canvas.getWidth();
    	int height = canvas.getHeight();
    	Activity a = (Activity) Misc.getAttribute("activity");
	   	boolean nightMode = true;//Misc.getPreference(a, "night_mode", true);
    	
    	// Draw axis
		paint.setStrokeWidth(2);
		if (!nightMode) paint.setColor(Color.LTGRAY);
		else			paint.setColor(Color.DKGRAY);
    	canvas.drawLine(0, height/2, width, height/2, paint);
    	
    	// Draw wave
    	paint.setStrokeWidth(2/*Integer.valueOf(Misc.getPreference(a, "line_width", "1"))*/);
    	if (!nightMode) paint.setColor(Color.BLACK);
		else		 	paint.setColor(Color.GREEN);
    	float x1 = 0;
    	float y1 = height*(0.5f+0.5f*gain*(float) wave[0]);
    	for (int i=1; i<fftResolution/2; i++) {
    		float x2 = width*i/(fftResolution/2);
    		float y2 = height*(0.5f*gain*(float) wave[i]);
    		if ((x1>0 && x1<width) && (x2>0 && x2<width))
    			canvas.drawLine(x1, height-y1, x2, height-y2, paint);
    		x1 = x2;
    		y1 = y2;
    	}
    }
}