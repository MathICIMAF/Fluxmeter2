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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Class associated with the spectrogram view
 * Handles events:
 *  onSizeChanged, onTouchEvent, onDraw
 */
public class FrequencyView extends View {
    
    // Attributes
    private Activity activity;
    private Paint paint = new Paint();
    private Bitmap bitmap;
    private Canvas canvas;
    private int pos;
    private int samplingRate;
    private int width, height;

    private int fftResolution;

    private double[] magnitudes;

    private List<Double> fmList;

    private double freqM;

    float factor = 4.0f;

    private float maxMagnitud = 15000;

    float umbral = 0.3f;

    private float scale = 1.2f;

    private boolean showFreqM = false;

    private int[] colorRainbow = new int[] {  0xFF000000,0xFF0000FF , 0xFF00FFFF,0xFF00FF00,0xFFFFFF00,0xFFFF0000,  0xFFFF00FF,   0xFFFFFFFF  };
    private int[] colorFire = new int[] {    0xFFFFFFFF, 0xFFFFFF00, 0xFFFF0000, 0xFF000000 };
    private int[] colorIce = new int[] {    0xFFFFFFFF, 0xFF00FFFF, 0xFF0000FF, 0xFF000000 };
    private int[] colorGrey = new int[] {    0xFF000000,0xFFFFFFFF };

    public float getUmbral(){
        return umbral;
    }

    public void setUmbral(float value){
        umbral = value;
    }

    public void setShowFreqM(boolean value){
        showFreqM = value;
    }

    public boolean getShowFreqM(){
        return showFreqM;
    }

    // Window
    float minX, minY, maxX, maxY;
    public FrequencyView(Context context) {
        super(context);
        activity = (Activity) Misc.getAttribute("activity");
    }
    public FrequencyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        activity = (Activity) Misc.getAttribute("activity");
    }
    
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        width = w;
        height = h;
        if (bitmap!=null)    bitmap.recycle();
        bitmap = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
    }
    @SuppressLint("ClickableViewAccessibility")
	@Override
    public boolean onTouchEvent(MotionEvent event) {
        invalidate();
        return true;
    }
    
    /**
     * Simple sets
     */
    public void setFFTResolution(int res) {
        magnitudes = new double[res];
        fftResolution = res;
    }
    public void setSamplingRate(int sampling) {
        samplingRate = sampling;
    }
    public void setMagnitudes(double[] m) {
        System.arraycopy(m, 0, magnitudes, 0, m.length);
    }

    public void setFmList(){
        fmList = new ArrayList<>();
        int size = (samplingRate/fftResolution)*15;
        for (int i = 0; i < size; i++){
            fmList.add(0.0);
        }
    }
    
    /**
     * Called whenever a redraw is needed
     * Renders spectrogram and scale on the right
     * Frequency scale can be linear or logarithmic
     */
    @Override
    public void onDraw(Canvas canvas) {
        int[] colors = null;
        String colorScale = "Grey";//Misc.getPreference(activity, "color_scale", activity.getString(R.string.preferences_color_scale_default_value));
             if (colorScale.equals("Grey"))     colors = colorGrey;
        else if (colorScale.equals("Fire"))     colors = colorFire;
        else if (colorScale.equals("Ice"))      colors = colorIce;
        else if (colorScale.equals("Rainbow"))  colors = colorRainbow;

        int wColor = 10;
        int wFrequency = 40;
        int rWidth = width-wColor-wFrequency;
        paint.setStrokeWidth(2);
        
        // Get scale preferences
		//String defFrequency = "Linear";//getString(R.string.preferences_frequency_scale_default_value);
    	//boolean logFrequency = false;//!Misc.getPreference("frequency_scale", defFrequency).equals(defFrequency);
        
        // Update buffer bitmap
        paint.setColor(Color.BLACK);
        this.canvas.drawLine(pos%rWidth, 0, pos%rWidth, height, paint);

        int band = 10;// Ancho de la linea que se grafica

        for (int k = 0; k < band; k++) {
            for (int i = 0; i < height; i++) {
                float j = getValueFromRelativePosition((float) (height - i) / height, 1, samplingRate, false);
                j /= (samplingRate);
                int posMagnitudes = (int) (j * (magnitudes.length - 1));

                //Rampa de compensacion
                float weight = posMagnitudes / factor;
                if (weight > 4.0f)
                    weight = 4.0f;
                float mag = (float) magnitudes[posMagnitudes] * weight;
                //*********************

                int c = getInterpolatedColor(colors, mag / maxMagnitud);
                paint.setColor(c);
                int x = (pos % rWidth + k);
                int y = i;
                this.canvas.drawPoint(x, y, paint);
                this.canvas.drawPoint(x, y, paint); // make color brighter

            }
        }


        // Draw bitmap
        if (pos<rWidth) {
            canvas.drawBitmap(bitmap, wColor, 0, paint);
        } else {
            canvas.drawBitmap(bitmap, (float) wColor - pos%rWidth, 0, paint);
            canvas.drawBitmap(bitmap, (float) wColor + (rWidth - pos%rWidth), 0, paint);
        }


        // Draw color scale
        paint.setColor(Color.BLACK);
        canvas.drawRect(0, 0, wColor, height, paint);
        for (int i=0; i<height; i++) {
            int c = getInterpolatedColor(colors, ((float) i)/height);
            paint.setColor(c);
            canvas.drawLine(0, i, wColor-5, i, paint);
        }

        // Draw frequency scale
        float ratio = 0.7f*getResources().getDisplayMetrics().density;
        paint.setTextSize(15f*ratio);
        paint.setColor(Color.BLACK);
        canvas.drawRect(rWidth + wColor, 0, width, height, paint);
        paint.setColor(Color.WHITE);
        canvas.drawText("kHz", rWidth + wColor, 12*ratio, paint);

        for (int i=0; i<(samplingRate-500); i+=1000)
	        canvas.drawText(" "+i/1000, rWidth + wColor, height*(1f-(float) i/(samplingRate/2)), paint);

        pos+=band;
    }

    
    /**
     * Converts relative position of a value within given boundaries
     * Log=true for logarithmic scale
     */
    private float getRelativePosition(float value, float minValue, float maxValue, boolean log) {
    	if (log)	return ((float) Math.log10(1+value-minValue) / (float) Math.log10(1+maxValue-minValue));
    	else		return (value-minValue)/(maxValue-minValue);
    }
    
    /**
     * Returns a value from its relative position within given boundaries
     * Log=true for logarithmic scale
     */
    private float getValueFromRelativePosition(float position, float minValue, float maxValue, boolean log) {
    	if (log)	return (float) (Math.pow(10, position*Math.log10(1+maxValue-minValue))+minValue-1);
    	else		return minValue + position*(maxValue-minValue);
    }
    
    /**
     * Calculate rainbow colors
     */
    private int ave(int s, int d, float p) {
        return s + Math.round(p * (d - s));
    }
    public int getInterpolatedColor(int[] colors, float unit) {
        if (unit <= umbral) return colors[0];
        if (unit >= 1.0f) return colors[colors.length - 1];


        float unit_aux = unit;

        unit_aux *= (1.0-umbral);
        
        float p = unit_aux * (colors.length - 1);

        int i = (int) p;
        p -= i;

        // now p is just the fractional part [0...1) and i is the index
        int c0 = colors[i];
        int c1 = colors[i + 1];
        int a = ave(Color.alpha(c0), Color.alpha(c1), p);
        int r = ave(Color.red(c0), Color.red(c1), p);
        int g = ave(Color.green(c0), Color.green(c1), p);
        int b = ave(Color.blue(c0), Color.blue(c1), p);

        return Color.argb(a, r, g, b);
    }

    public void setFreqM(double freqM) {
        fmList.add(0,freqM);
        fmList.remove(fmList.size()-1);
        this.freqM = freqM;
    }

    public int getFftResolution() {
        return fftResolution;
    }

    public void setFftResolution(int fftResolution) {
        this.fftResolution = fftResolution;
    }
}
