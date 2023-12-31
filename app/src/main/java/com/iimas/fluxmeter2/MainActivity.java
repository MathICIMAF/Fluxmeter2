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
import android.view.ViewGroup;
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

    public static ContinuousRecord recorder;

    private static double c = 154000; //velocidad ultrasonido en la sangre 154000cm/s


    public static float maxMagnitud = 15000;




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
    CheckBox showSpectro;

    private SeekBar seekBar;
    private TextView umbralText,fmText,fftText,ipText,fMaxText,fmmText,nombreText;

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    //Parametros de configuracion
    boolean inicio_espectro;

    double foperacion; //Frecuencia operacion del transductor
    double angulo;

    TipoGrafica tipoGrafica;
    public static int windowSize = 256;  // fft Resolution o la N
    public static int frecuencySampling = 22050;

    private static int time = 5; //Tiempo en segundos de los datos en pantalla

    private static int VISIBLE_NUM = (2*frecuencySampling/windowSize)*time;
    String nombrePaciente;

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
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        editor = sharedPreferences.edit();
        inicio_espectro = sharedPreferences.getBoolean("pantalla_inicio",true);
        showSpectro.setChecked(inicio_espectro);
        nombreText = findViewById(R.id.nombreText);

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

    //Aqui se cargan las preferencias del menu
    void loadPreferences(){
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
        nombrePaciente = sharedPreferences.getString("nombre_paciente","");
    }

    //Aca se pide al usuario que otorgue el permiso para grabar audio
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

        fmText.setText("F. Muestreo: "+frecuencySampling+ "Hz" );
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

        setPantallaAMostrar(inicio_espectro);
        showSpectro.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                setPantallaAMostrar(b);
                editor.putBoolean("pantalla_inicio",b);
                editor.apply();
            }
        });
        nombreText.setText(getString(R.string.paciente)+" "+nombrePaciente);

    }

    void setPantallaAMostrar(boolean pantalla_inicio){
        if (pantalla_inicio){
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

    //Se inicializan las vistas
    //Se prepara para comenzar a recibir el audio
    //Se reinicia el buffer
    private void loadEngine() {

        initializeViews();

        // Stop and release recorder if running
        recorder.stop();
        recorder.release();

        recorder.prepare(windowSize); // Record buffer size if forced to be a multiple of the fft resolution

        int n = windowSize;
        fftBuffer = new short[n];

        bufferStack = new ArrayList<>();
        int l = recorder.getBufferLength()/(n/2);
        for (int i=0; i<l+1; i++) //+1 because the last one has to be used again and sent to first position
            bufferStack.add(new short[n/2]); // preallocate to avoid new within processing loop

        // Start recording
        startRecording();
    }

    //Aqui se rellena el buffer a procesar
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

    //Aqui se crea el menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    //Se le da funcionalidad a los elementos del menu
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
        else if(itemId == R.id.action_manual){
            userManualDialog();
        }

        return super.onOptionsItemSelected(item);
    }

    //Metodo para mostrar manual de usuario
    void userManualDialog(){
        Dialog dialog = new Dialog(MainActivity.this);
        dialog.setContentView(R.layout.user_manual_dialog);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true);
        dialog.show();
    }

    //Metodo para mostrar el about
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

    //Esta es la funcion donde se procesa el audio, se llama la FFT y se
    //mandan a graficar los resultados
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
            if (countIP == VISIBLE_NUM/time){
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
                        //fMaxText.setText(fmax);
                        //fmmText.setText(fmed);
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


    //Metodo para agregar un nuevo punto al grafico
    private void addEntry( float y) {
        LineData data = lineChart.getData();

        if(lineDataSet == null){
            lineDataSet = new LineDataSet(null, "Dynamic data");
            lineDataSet.setHighlightEnabled(false);
            data.addDataSet(lineDataSet);
        }
        setLineDataSetLabel(lineDataSet);
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

    //Aca se inicializa el grafico
    void initializeChart(){
        lineChart = findViewById(R.id.lineChart);
        lineChart.setBackgroundColor(Color.BLACK);
        XAxis xAxis = lineChart.getXAxis();

        lineChart.getLegend().setTextColor(Color.WHITE);

        YAxis yAxis = lineChart.getAxisRight();
        yAxis.setTextColor(Color.WHITE);

        entries = new ArrayList<>();

        for (int i = 0; i < VISIBLE_NUM; i++){
            Entry entry = new Entry(i,0);
            entries.add(entry);
        }
        lineDataSet = new LineDataSet(entries,"");
        lineDataSet.setColor(Color.WHITE);
        lineDataSet.setLineWidth(2);
        lineDataSet.setDrawCircles(false);

        lineData = new LineData(lineDataSet);
        lineChart.setData(lineData);
    }

    void setLineDataSetLabel(LineDataSet lineDataSet){
        switch (tipoGrafica){
            case FREQ_MED:
                lineDataSet.setLabel(getString(R.string.frecMedias));
                break;
            case FREQ_MAX:
                lineDataSet.setLabel(getString(R.string.frecMax));
                break;
            case VELOC_MED:
                lineDataSet.setLabel(getString(R.string.velocMedias));
                break;
        }
    }
}