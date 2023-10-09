package com.iimas.fluxmeter2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static int samplingRate = 22050;
    public static ContinuousRecord recorder;
    public int fftResolution = 256;

    // Buffers
    private List<short[]> bufferStack; // Store trunks of buffers
    private short[] fftBuffer; // buffer supporting the fft process
    private float[] re; // buffer holding real part during fft process
    private float[] im; // buffer holding imaginary part during fft process

    public double[] mags;

    private FrequencyView frequencyView;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recorder = new ContinuousRecord(samplingRate);

        frequencyView = findViewById(R.id.frequency_view);

        frequencyView.setFFTResolution(fftResolution);
        frequencyView.setSamplingRate(samplingRate);
        frequencyView.setBackgroundColor(Color.BLACK);


    }

    private void startRecording() {
        recorder.start(recordBuffer -> getTrunks(recordBuffer));
    }
    private void stopRecording() {
        recorder.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopRecording();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            loadEngine();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 1234);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1234: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadEngine();
                }
            }
        }
    }

    private void loadEngine() {

        // Stop and release recorder if running
        recorder.stop();
        recorder.release();

        // Prepare recorder
        recorder.prepare(fftResolution); // Record buffer size if forced to be a multiple of the fft resolution

        // Build buffers for runtime
        int n = fftResolution;
        fftBuffer = new short[n];
        re = new float[n];
        im = new float[n];
        bufferStack = new ArrayList<>();
        int l = recorder.getBufferLength()/(n/2);
        for (int i=0; i<l+1; i++) //+1 because the last one has to be used again and sent to first position
            bufferStack.add(new short[n/2]); // preallocate to avoid new within processing loop

        // Start recording
        startRecording();

        // Log
        //Log.d("recorder.getBufferLength()", recorder.getBufferLength()+" samples");
        //Log.d("bufferStack.size()", bufferStack.size()+" trunks");
    }

    private void getTrunks(short[] recordBuffer) {
        int n = fftResolution;

        // Trunks are consecutive n/2 length samples
        for (int i=0; i<bufferStack.size()-1; i++)
            System.arraycopy(recordBuffer, n/2*i, bufferStack.get(i+1), 0, n/2);

        // Build n length buffers for processing
        // Are build from consecutive trunks
        for (int i=0; i<bufferStack.size()-1; i++) {
            System.arraycopy(bufferStack.get(i), 0, fftBuffer, 0, n/2);
            System.arraycopy(bufferStack.get(i+1), 0, fftBuffer, n/2, n/2);
            process();
        }

        // Last item has not yet fully be used (only its first half)
        // Move it to first position in arraylist so that its last half is used
        short[] first = bufferStack.get(0);
        short[] last = bufferStack.get(bufferStack.size()-1);
        System.arraycopy(last, 0, first, 0, n/2);
    }

    void process(){
        FrequencyScanner frequencyScanner = new FrequencyScanner();
        mags =  frequencyScanner.extractFrequencies(fftBuffer);
        frequencyView.setMagnitudes(mags);
        runOnUiThread(() -> {
            frequencyView.invalidate();
        });
    }

}