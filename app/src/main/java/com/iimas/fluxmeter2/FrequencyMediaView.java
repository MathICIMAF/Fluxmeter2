package com.iimas.fluxmeter2;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class FrequencyMediaView extends View {

    private Activity activity;
    private Paint paint = new Paint();
    private Bitmap bitmap;
    private Canvas canvas;
    private int pos;
    private int samplingRate;
    private int width, height;

    private int fftResolution;

    private List<Double> fmList;

    Path path;

    private int[] colorGrey = new int[] {    0xFF000000,0xFFFFFFFF };

    public FrequencyMediaView(Context context) {
        super(context);
        activity = (Activity) Misc.getAttribute("activity");
        path = new Path();
    }
    public FrequencyMediaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        activity = (Activity) Misc.getAttribute("activity");
        path = new Path();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        width = w;
        height = h;
        if (bitmap!=null)    bitmap.recycle();
        bitmap = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
    }

    public void setFFTResolution(int res) {
        fftResolution = res;
    }
    public void setSamplingRate(int sampling) {
        samplingRate = sampling;
    }

    public void setFmList(List<Double> list){
        fmList = list;
    }

    public void setFreqM(double freqM) {
        fmList.add(0,freqM);
        fmList.remove(fmList.size()-1);
    }

    @Override
    public void onDraw(Canvas canvas) {
        int[] colors = colorGrey;

        int wColor = 10;
        int wFrequency = 40;
        int rWidth = width-wColor-wFrequency;
        paint.setStrokeWidth(2);

        paint.setColor(Color.BLACK);
        paint.setAntiAlias(true);
        this.canvas.drawLine(pos%rWidth, 0, pos%rWidth, height, paint);

        int band = 10;

        float x1 = 0;
        float y1 = height-1;
        Paint paint1 = new Paint();
        paint1.setColor(colors[1]);
        paint1.setStyle(Paint.Style.STROKE);
        path.rewind();
        path.moveTo(x1,y1);
        for (int k = 0; k < fmList.size(); k++){
            double val = fmList.get(k);
            float valFm = (float) val*(samplingRate/fftResolution);
            float relPos = getRelativePosition(valFm,1,samplingRate,false);
            float x2 = rWidth*k/(fmList.size());
            float y2 = height - (relPos*height);
            if ((x1>=0 && x1<width) && (x2>=0 && x2<width)) {
                path.lineTo(x2,y2);
                canvas.drawPath(path,paint1);
            }
            x1 = x2;
            y1 = y2;
        }
        paint1.setAntiAlias(true);
        if (pos<rWidth) {
            canvas.drawBitmap(bitmap, wColor, 0, paint);
        } else {
            canvas.drawBitmap(bitmap, (float) wColor - pos%rWidth, 0, paint);
            canvas.drawBitmap(bitmap, (float) wColor + (rWidth - pos%rWidth), 0, paint);
        }

        paint.setColor(Color.BLACK);
        canvas.drawRect(0, 0, wColor, height, paint);
        for (int i=0; i<height; i++) {
            paint.setColor(colorGrey[1]);
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

    private float getRelativePosition(float value, float minValue, float maxValue, boolean log) {
        if (log)	return ((float) Math.log10(1+value-minValue) / (float) Math.log10(1+maxValue-minValue));
        else		return (value-minValue)/(maxValue-minValue);
    }

}
