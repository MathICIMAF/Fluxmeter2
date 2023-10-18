package com.iimas.fluxmeter2;

import static com.iimas.fluxmeter2.MainActivity.maxMagnitud;

import org.jtransforms.fft.DoubleFFT_1D;

public class FrequencyScanner {
    private double[] window;

    public FrequencyScanner() {
        window = null;
    }

    /** extract the dominant frequency from 16bit PCM data.
     * @param sampleData an array containing the raw 16bit PCM data.
     * @param sampleRate the sample rate (in HZ) of sampleData
     * @return an approximation of the dominant frequency in sampleData
     */
    public double extractFrequency(short[] sampleData, int sampleRate) {
        /* sampleData + zero padding */
        DoubleFFT_1D fft = new DoubleFFT_1D(sampleData.length + 24 * sampleData.length);
        double[] a = new double[(sampleData.length + 24 * sampleData.length) * 2];

        System.arraycopy(applyWindow(sampleData), 0, a, 0, sampleData.length);
        fft.realForward(a);

        /* find the peak magnitude and it's index */
        double maxMag = Double.NEGATIVE_INFINITY;
        int maxInd = -1;

        for(int i = 0; i < a.length / 2; ++i) {
            double re  = a[2*i];
            double im  = a[2*i+1];
            double mag = Math.sqrt(re * re + im * im);

            if(mag > maxMag) {
                maxMag = mag;
                maxInd = i;
            }
        }

        /* calculate the frequency */
        return (double)sampleRate * maxInd / (a.length / 2);
    }

    public double[] extractFrequencies(short[] sampleData) {
        /* sampleData + zero padding */
        DoubleFFT_1D fft = new DoubleFFT_1D(sampleData.length + sampleData.length);
        double[] a = new double[(sampleData.length + sampleData.length)];

        System.arraycopy(applyWindow(sampleData), 0, a, 0, sampleData.length);
        fft.realForward(a);

        double [] res = new double[sampleData.length];
        for(int i = 0; i < a.length / 2; ++i) {
            double re  = a[2*i];
            double im  = a[2*i+1];
            double mag = Math.sqrt(re * re + im * im);
            res[i] = mag;
        }

        return res;
    }

    public double extractFreqMean(short[] sampleData,double umbral){
        double res = 0;
        DoubleFFT_1D fft = new DoubleFFT_1D(sampleData.length + sampleData.length);
        double[] a = new double[(sampleData.length + sampleData.length)];

        System.arraycopy(applyWindow(sampleData), 0, a, 0, sampleData.length);
        fft.realForward(a);

        double sum_pi = 0;
        double sum_i_pi = 0;

        for(int i = 0; i < a.length / 4; ++i) {
            double re  = a[2*i];
            double im  = a[2*i+1];
            double mag = Math.sqrt(re * re + im * im);
            if (mag/maxMagnitud < umbral)
                mag = 0;
            sum_pi+=mag;
            sum_i_pi+=(i*mag);

        }

        return sum_i_pi/sum_pi;
    }

    public double extractFreqMean(double[] sampleData){
        double res = 0;
        DoubleFFT_1D fft = new DoubleFFT_1D(sampleData.length + sampleData.length);
        double[] a = new double[(sampleData.length + sampleData.length)];

        System.arraycopy(applyWindowDouble(sampleData), 0, a, 0, sampleData.length);
        fft.realForward(a);

        double sum_pi = 0;
        double sum_i_pi = 0;

        for(int i = 0; i < a.length / 4; ++i) {
            double re  = a[2*i];
            double im  = a[2*i+1];
            double mag = Math.sqrt(re * re + im * im);
            sum_pi+=mag;
            sum_i_pi+=(i*mag);

        }

        return sum_i_pi/sum_pi;
    }


    /** build a Hamming window filter for samples of a given size
     * See http://www.labbookpages.co.uk/audio/firWindowing.html#windows
     * @param size the sample size for which the filter will be created
     */
    private void buildHammWindow(int size) {
        if(window != null && window.length == size) {
            return;
        }
        window = new double[size];
        for(int i = 0; i < size; ++i) {
            window[i] = .54 - .46 * Math.cos(2 * Math.PI * i / (size - 1.0));
        }
    }

    /** apply a Hamming window filter to raw input data
     * @param input an array containing unfiltered input data
     * @return a double array containing the filtered data
     */
    private double[] applyWindow(short[] input) {
        double[] res = new double[input.length];

        buildHammWindow(input.length);
        for(int i = 0; i < input.length; ++i) {
            res[i] = (double)input[i] * window[i];
        }
        return res;
    }

    private double[] applyWindowDouble(double[] input) {
        double[] res = new double[input.length];

        buildHammWindow(input.length);
        for(int i = 0; i < input.length; ++i) {
            res[i] = input[i] * window[i];
        }
        return res;
    }
}
