package com.iimas.fluxmeter2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.saadahmedsoft.popupdialog.PopupDialog;
import com.saadahmedsoft.popupdialog.Styles;
import com.saadahmedsoft.popupdialog.listener.OnDialogButtonClickListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static int frecuencySampling = 22050;
    public static ContinuousRecord recorder;
    public static int windowSize = 256;  // fft Resolution o la N

    private static double c = 154000; //velocidad ultrasonido en la sangre 154000cm/s

    double foperacion;
    double angulo;

    public static float maxMagnitud = 15000;

    private static int VISIBLE_NUM = (frecuencySampling/windowSize)*5;



    //Variable que indica cuando se actualiza el IP;
    int countIP;

    List<Float> ipList;
    double fmMax,sumFm,fmMin;

    boolean senhalPrueba = false;


    // Buffers
    private List<short[]> bufferStack; // Store trunks of buffers
    private short[] fftBuffer; // buffer supporting the fft process
    public double[] mags,fftBufferDouble;

    private FrequencyView frequencyView;

    LineChart lineChart;
    List<Entry> entries;
    LineData lineData;
    LineDataSet lineDataSet;

    //RadioButton espectroRadio,fmediaRadio,fmaxRadio,vmediaRadio;
    CheckBox showSpectro;

    TipoGrafica tipoGrafica;

    private SeekBar seekBar;
    private TextView umbralText,fmText,fftText,ipText,fMaxText,fmmText;

    SharedPreferences sharedPreferences;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        countIP = 0;
        ipList = new ArrayList<>();
        fmMax = 0;
        fmMin = Double.MAX_VALUE;
        sumFm = 0;
        frequencyView = findViewById(R.id.frequency_view);
        seekBar = findViewById(R.id.seek_umbral);
        umbralText = findViewById(R.id.umbral_text);
        fmText = findViewById(R.id.fmText);
        fMaxText = findViewById(R.id.fMaxText);
        fmmText = findViewById(R.id.fmmText);
        fftText = findViewById(R.id.fftText);
        ipText = findViewById(R.id.ipText);
        lineChart = findViewById(R.id.lineChart);
        showSpectro = findViewById(R.id.showCheck);
        //espectroRadio = findViewById(R.id.radio_spectrograma);
        //fmediaRadio = findViewById(R.id.radio_fmedia);
        //fmaxRadio = findViewById(R.id.radio_fmax);
        //vmediaRadio = findViewById(R.id.radio_vmedia);


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

    void loadPreferences(){
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        frecuencySampling =  Integer.parseInt(sharedPreferences.getString("fmuestreo","11025"));
        windowSize =  Integer.parseInt(sharedPreferences.getString("ventana","256"));
        int ang_val = Integer.parseInt(sharedPreferences.getString("angulo","60"));
        if (ang_val == 60)
            angulo = Math.PI/3;
        else
            angulo = Math.PI/4;
        int fop_val = Integer.parseInt(sharedPreferences.getString("foperacion","8"));
        foperacion = fop_val*1000000;

        String grafica = sharedPreferences.getString("grafica",getString(R.string.frecMedias));
        if (grafica.equals(getString(R.string.frecMedias)))
            tipoGrafica = TipoGrafica.FREQ_MED;
        else if(grafica.equals(getString(R.string.frecMax)))
            tipoGrafica = TipoGrafica.FREQ_MAX;
        else
            tipoGrafica = TipoGrafica.VELOC_MED;
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

    private void initializeViews(){
        loadPreferences();

        fmText.setText("Fm: "+frecuencySampling+ "Hz" );
        fftText.setText("Ventana: "+windowSize);

        recorder = new ContinuousRecord(frecuencySampling);

        initializeChart();

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

        showSpectro.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b){
                    frequencyView.setVisibility(View.VISIBLE);
                    lineChart.setVisibility(View.GONE);
                    fmmText.setVisibility(View.GONE);
                    ipText.setVisibility(View.GONE);
                    fMaxText.setVisibility(View.GONE);
                }
                else {
                    frequencyView.setVisibility(View.GONE);
                    lineChart.setVisibility(View.VISIBLE);
                    ipText.setVisibility(View.VISIBLE);
                }
            }
        });

        /*espectroRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b){
                    frequencyView.setVisibility(View.VISIBLE);
                    lineChart.setVisibility(View.GONE);
                    fmmText.setVisibility(View.GONE);
                    ipText.setVisibility(View.GONE);
                    fMaxText.setVisibility(View.GONE);
                }

            }
        });

        fmediaRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    frequencyView.setVisibility(View.GONE);
                    lineChart.setVisibility(View.VISIBLE);
                    fmmText.setVisibility(View.VISIBLE);
                    ipText.setVisibility(View.VISIBLE);
                    fMaxText.setVisibility(View.VISIBLE);
                }
            }
        });

        fmaxRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    frequencyView.setVisibility(View.GONE);
                    lineChart.setVisibility(View.VISIBLE);
                    fmmText.setVisibility(View.VISIBLE);
                    ipText.setVisibility(View.VISIBLE);
                    fMaxText.setVisibility(View.VISIBLE);
                }
            }
        });

        vmediaRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    frequencyView.setVisibility(View.GONE);
                    lineChart.setVisibility(View.VISIBLE);
                    fmmText.setVisibility(View.GONE);
                    ipText.setVisibility(View.VISIBLE);
                    fMaxText.setVisibility(View.GONE);
                }
            }
        });

         */

    }

    private void loadEngine() {

        initializeViews();

        // Stop and release recorder if running
        recorder.stop();
        recorder.release();

        // Prepare recorder
        recorder.prepare(windowSize); // Record buffer size if forced to be a multiple of the fft resolution

        // Build buffers for runtime
        int n = windowSize;
        fftBuffer = new short[n];

        bufferStack = new ArrayList<>();
        int l = recorder.getBufferLength()/(n/2);
        for (int i=0; i<l+1; i++) //+1 because the last one has to be used again and sent to first position
            bufferStack.add(new short[n/2]); // preallocate to avoid new within processing loop

        // Start recording
        startRecording();


    }

    private void getTrunks(short[] recordBuffer) {
        int n = windowSize;

        if (!senhalPrueba) {
            // Trunks are consecutive n/2 length samples
            for (int i = 0; i < bufferStack.size() - 1; i++)
                System.arraycopy(recordBuffer, n / 2 * i, bufferStack.get(i + 1), 0, n / 2);

            // Build n length buffers for processing
            // Are build from consecutive trunks
            for (int i = 0; i < bufferStack.size() - 1; i++) {
                System.arraycopy(bufferStack.get(i), 0, fftBuffer, 0, n / 2);
                System.arraycopy(bufferStack.get(i + 1), 0, fftBuffer, n / 2, n / 2);
                process();
            }

            // Last item has not yet fully be used (only its first half)
            // Move it to first position in arraylist so that its last half is used
            short[] first = bufferStack.get(0);
            short[] last = bufferStack.get(bufferStack.size() - 1);
            System.arraycopy(last, 0, first, 0, n / 2);
        }
        else {

            fftBufferDouble = new double[n];
            int M = 3;
            if (countIP < VISIBLE_NUM / 4 || (countIP > 2 * VISIBLE_NUM / 4 && countIP < 3 * VISIBLE_NUM / 4))
                M = 5;
            for (int k = 0; k < 5; k++) {
                for (int i = 0; i < n; i++) {
                    fftBufferDouble[i] = Math.sin((2 * Math.PI * i * M) / n);
                }
                process();
            }
        }
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
        else if (itemId == R.id.action_play){
            startRecording();
        }
        else if (itemId == R.id.action_pause){
            stopRecording();
        }
        else if (itemId == R.id.action_settings){
            Intent i = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(i);
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
        if (!showSpectro.isChecked()){
            countIP++;
            double freqM = (senhalPrueba)?frequencyScanner.extractFreqMean(fftBufferDouble)
                    :(tipoGrafica == TipoGrafica.FREQ_MED || tipoGrafica == TipoGrafica.VELOC_MED)?
                    frequencyScanner.extractFreqMean(fftBuffer,seekBar.getProgress()/100.0f):
                    frequencyScanner.extractFreqMax(fftBuffer,seekBar.getProgress()/100.0f);
            if (fmMax < freqM) fmMax = freqM;
            if (fmMin > freqM) fmMin = freqM;
            //
            sumFm += freqM;
            if (countIP == VISIBLE_NUM/4){
                double med = sumFm/countIP;
                float ip;
                if (senhalPrueba){
                    ip = (float) ((fmMax-fmMin)/med);
                }
                else {
                    fmMax *= ((frecuencySampling / windowSize) / 2);
                    med *= ((frecuencySampling / windowSize) / 2);
                    if (fmMax <= 1000)ip = 0;
                    else {
                        ip = (float) ((fmMax - fmMin) / med);
                    }
                }
                if (ipList.size() == 5){
                    ipList.remove(0);
                    ipList.add(ip);
                    float sum = 0;
                    for (int i = 0; i < ipList.size(); i++)
                        sum+=ipList.get(i);
                    ip = sum/ipList.size();
                }
                else {
                    ipList.add(ip);
                }
                String s = String.format("%.2f", ip);
                String fmax = ("FMax:"+String.format("%.0f", fmMax));
                String fmed = ("FMed:"+String.format("%.0f", med));
                countIP = 0;
                sumFm = 0;
                fmMin = Double.MAX_VALUE;
                fmMax = 0;
                new Handler(Looper.getMainLooper()).post(new Runnable(){
                    @Override
                    public void run() {
                        ipText.setText("IP:"+s);
                        fMaxText.setText(fmax);
                        fmmText.setText(fmed);
                    }
                });
            }
            runOnUiThread(() -> {
                double fmInt = freqM;
                fmInt *= (frecuencySampling / windowSize) / 2;
                if (tipoGrafica == TipoGrafica.VELOC_MED){
                    double vmedia = (c/(2*foperacion*Math.cos(angulo)))*fmInt;
                    addEntry((float) vmedia);
                }
                else {
                    addEntry((float) fmInt);
                }
            });
        }
        else {
            mags =  frequencyScanner.extractFrequencies(fftBuffer);
            frequencyView.setMagnitudes(mags);
            runOnUiThread(() -> {
                frequencyView.invalidate();
            });
        }
    }


    private void addEntry( float y) {
        LineData data = lineChart.getData();

        if(lineDataSet == null){
            lineDataSet = new LineDataSet(null, "Dynamic data");
            lineDataSet.setHighlightEnabled(false);
            data.addDataSet(lineDataSet);
        }
        if(lineDataSet.getEntryCount() >= VISIBLE_NUM) {
            lineDataSet.removeFirst();

            for (int i=0; i<lineDataSet.getEntryCount(); i++) {
                Entry entryToChange = lineDataSet.getEntryForIndex(i);
                entryToChange.setX(entryToChange.getX() - 1);
            }
        }
        if (tipoGrafica == TipoGrafica.VELOC_MED) {
            data.addEntry(new Entry(lineDataSet.getEntryCount(), y), 0);
        }
        else {
            data.addEntry(new Entry(lineDataSet.getEntryCount(), y / 1000), 0);
            lineChart.setVisibleYRange(0,5, YAxis.AxisDependency.RIGHT);
        }

        lineChart.notifyDataSetChanged();
        lineChart.invalidate();

    }

    void initializeChart(){
        lineChart = findViewById(R.id.lineChart);
        lineChart.setBackgroundColor(Color.BLACK);
        XAxis xAxis = lineChart.getXAxis();

        YAxis yAxis = lineChart.getAxisRight();
        yAxis.setTextColor(Color.WHITE);

        entries = new ArrayList<>();

        for (int i = 0; i < VISIBLE_NUM; i++){
            Entry entry = new Entry(i,0);
            entries.add(entry);
        }
        lineDataSet = new LineDataSet(entries,"Frecuencia Media");
        lineDataSet.setColor(Color.WHITE);
        lineDataSet.setLineWidth(2);
        lineDataSet.setDrawCircles(false);

        lineData = new LineData(lineDataSet);
        lineChart.setData(lineData);
    }
}