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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

/**
 * Recording service
 * Methods prepare/release initiates/releases the service and must be the very first/last calls
 * Methods start/stop starts/stop the recording service in an independent thread
 * Recorded samples are sent to the listener passed as parameter of @method start
 */
public class ContinuousRecord {
	
	// Attributes
	private AudioRecord audioRecord;
	private int samplingRate;
	private int recordLength;
	private Thread thread;
	private boolean run;
	
	/**
	 * Constructor
	 */
	public ContinuousRecord(int samplingRate) {
		this.samplingRate = samplingRate;
		run = false;
	}
	
	public int getBufferLength() {
		return recordLength;
	}
	

	public void prepare(int multiple) {

		int BYTES_PER_SHORT = 2;
	    recordLength = AudioRecord.getMinBufferSize(samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)/BYTES_PER_SHORT;
	    
	    // Increase buffer size so that it is a multiple of the param
	    int r = recordLength % multiple;
	    if (r>0) recordLength += (multiple-r);

	    

	    // Init audio recording from MIC
	    audioRecord = new AudioRecord(AudioSource.MIC, samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordLength*BYTES_PER_SHORT);
	}
	

	public static interface OnBufferReadyListener {
        void onBufferReady(short[] buffer);
    }
	
	/**
	 * Start recording in a independent thread
	 * @param listener is call every time a sample is ready
	 */
	public void start(final OnBufferReadyListener listener) {
		if (!run && audioRecord!=null) {
			run = true;
			//Log.d("ContinuousRecord","Starting service...");
			audioRecord.startRecording(); 
	       	final short[] recordBuffer = new short[recordLength];
	
	       	thread = new Thread(() -> {
				   while (run) {
						  audioRecord.read(recordBuffer, 0, recordLength);
						  listener.onBufferReady(recordBuffer);
				   }
			   });
	       	thread.start();
		}
	}
	

	public void stop() {
		if (run && audioRecord!=null) {
			//Log.d("ContinuousRecord","Stopping service...");
			run = false;
			while (thread.isAlive())
				try { Thread.sleep(10); } catch (InterruptedException e) { e.printStackTrace(); }
			audioRecord.stop();
		}
	}
	

	public void release() {
		if (!run && audioRecord!=null)
			audioRecord.release();
   	}
}
