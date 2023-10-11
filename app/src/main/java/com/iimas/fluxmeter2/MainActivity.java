package com.iimas.fluxmeter2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.TextView;

import com.saadahmedsoft.popupdialog.PopupDialog;
import com.saadahmedsoft.popupdialog.Styles;
import com.saadahmedsoft.popupdialog.listener.OnDialogButtonClickListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static int frecuencySampling = 22050;
    public static ContinuousRecord recorder;
    public int windowSize = 256;  // fft Resolution o la N


    // Buffers
    private List<short[]> bufferStack; // Store trunks of buffers
    private short[] fftBuffer; // buffer supporting the fft process
    private float[] re; // buffer holding real part during fft process
    private float[] im; // buffer holding imaginary part during fft process

    public double[] mags;

    private FrequencyView frequencyView;

    private SeekBar seekBar;
    private TextView umbralText,fmText,fftText;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recorder = new ContinuousRecord(frecuencySampling);

        frequencyView = findViewById(R.id.frequency_view);
        seekBar = findViewById(R.id.seek_umbral);
        umbralText = findViewById(R.id.umbral_text);
        fmText = findViewById(R.id.fmText);
        fftText = findViewById(R.id.fftText);

        fmText.setText("Fm: "+frecuencySampling+ "Hz" );
        fftText.setText("Ventana: "+windowSize);

        frequencyView.setFFTResolution(windowSize);
        frequencyView.setSamplingRate(frecuencySampling);
        frequencyView.setBackgroundColor(Color.BLACK);

        seekBar.setProgress((int)(frequencyView.getUmbral()*100));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                float umbral = i/100.0f;
                umbralText.setText("Umbral: "+umbral);
                frequencyView.setUmbral(umbral);
                frequencyView.invalidate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


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
        recorder.prepare(windowSize); // Record buffer size if forced to be a multiple of the fft resolution

        // Build buffers for runtime
        int n = windowSize;
        fftBuffer = new short[n];
        re = new float[n];
        im = new float[n];
        bufferStack = new ArrayList<>();
        int l = recorder.getBufferLength()/(n/2);
        for (int i=0; i<l+1; i++) //+1 because the last one has to be used again and sent to first position
            bufferStack.add(new short[n/2]); // preallocate to avoid new within processing loop

        // Start recording
        startRecording();


    }

    private void getTrunks(short[] recordBuffer) {
        int n = windowSize;

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        int itemId = item.getItemId();

        if (itemId == R.id.action_about){
            showAbout();
        }
        if (itemId == R.id.action_play){
            startRecording();
        }
        if (itemId == R.id.action_pause){
            stopRecording();
        }

        return super.onOptionsItemSelected(item);
    }

    void showAbout(){
        PopupDialog.getInstance(this)
                .setStyle(Styles.STANDARD)
                .setHeading(getString(R.string.about))
                .setDescription(getString(R.string.about_description))
                .setPopupDialogIcon(R.drawable.baseline_info_24)
                .setPopupDialogIconTint(R.color.colorPrimary)
                .setPositiveButtonText("OK")
                .setCancelable(false)
                .showDialog(new OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveClicked(Dialog dialog) {
                        super.onPositiveClicked(dialog);
                    }
                });
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