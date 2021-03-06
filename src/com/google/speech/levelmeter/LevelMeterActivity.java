// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.speech.levelmeter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.text.DecimalFormat;

/**
 * This is the main activity of the SPL meter.
 * 
 * This class owns a micInput object that encapsulates the audio input
 * stream. The micInput object calls the processAudioFrame method which
 * computes the RMS value etc. and the display is updated.
 * 
 * @author Trausti Kristjansson
 *
 */
public class LevelMeterActivity extends Activity implements
    MicrophoneInputListener {

  MicrophoneInput micInput;  // The micInput object provides real time audio.
  TextView mdBTextView;
  TextView mdBFractionTextView;
  TextView mf0TextView;
  BarLevelDrawable mBarLevel;
  HzDbGraph myGraph;
  //SurfaceView surfaceView;
  //SurfaceHolder surfaceHolder;

  //private TextView mGainTextView;

  // pitch detection object
  FastYin f0 = null;


  double mOffsetdB = 10;  // Offset for bar, i.e. 0 lit LEDs at 10 dB.
  // The Google ASR input requirements state that audio input sensitivity
  // should be set such that 90 dB SPL at 1000 Hz yields RMS of 2500 for
  // 16-bit samples, i.e. 20 * log_10(2500 / mGain) = 90.
  double mGain = 2500.0 / Math.pow(10.0, 90.0 / 20.0);
  // For displaying error in calibration.
  double mDifferenceFromNominal = 0.0;
  double mRmsSmoothed;  // Temporally filtered version of RMS.
  double mAlpha = 0.9;  // Coefficient of IIR smoothing filter for RMS.
  private int mSampleRate;  // The audio sampling rate to use.
  private int mAudioSource;  // The audio source to use.
  private int radius =3;
  
  // Variables to monitor UI update and check for slow updates.
  private volatile boolean mDrawing;
  private volatile int mDrawingCollided;

  private static final String TAG = "LevelMeterActivity";
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Here the micInput object is created for audio capture.
    // It is set up to call this object to handle real time audio frames of
    // PCM samples. The incoming frames will be handled by the
    // processAudioFrame method below.
    micInput = new MicrophoneInput(this);
    //surfaceView = (SurfaceView)findViewById(R.id.surfaceView);
    //surfaceHolder = surfaceView.getHolder();



    // Read the layout and construct.
    setContentView(R.layout.main);

    // Get a handle that will be used in async thread post to update the
    // display.
    mBarLevel = (BarLevelDrawable)findViewById(R.id.bar_level_drawable_view);
    mdBTextView = (TextView)findViewById(R.id.dBTextView);
    mdBFractionTextView = (TextView)findViewById(R.id.dBFractionTextView);
    mf0TextView = (TextView)findViewById(R.id.F0textView);
    //mHzDbGraph = (HzDbGraph)findViewById(R.id.surfaceView);
    myGraph = (HzDbGraph)findViewById(R.id.HzDbGraph);




  // Toggle Button handler.

    final ToggleButton onOffButton=(ToggleButton)findViewById(
        R.id.on_off_toggle_button);

    ToggleButton.OnClickListener tbListener =
        new ToggleButton.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (onOffButton.isChecked()) {
          myGraph.cleanSurface();
          readPreferences();
          micInput.setSampleRate(mSampleRate);
          micInput.setAudioSource(mAudioSource);
          // f0 analysis
          if (f0 == null)
          {
            f0 = new FastYin(mSampleRate,micInput.mBufferSize);
          }

          micInput.start();
        } else {
          micInput.stop();

        }
      }
    };
    onOffButton.setOnClickListener(tbListener);

    // Level adjustment buttons.
    


    // Settings button, launches the settings dialog.
    
    Button settingsButton=(Button)findViewById(R.id.settingsButton);
    Button.OnClickListener settingsBtnListener =
        new Button.OnClickListener() {

      @Override
      public void onClick(View v) {
        final ToggleButton onOffButton=(ToggleButton)findViewById(
            R.id.on_off_toggle_button);
        onOffButton.setChecked(false);
        LevelMeterActivity.this.micInput.stop();

        Intent settingsIntent = new Intent(LevelMeterActivity.this,
            Settings.class);
        LevelMeterActivity.this.startActivity(settingsIntent);
      }
    };
    settingsButton.setOnClickListener(settingsBtnListener);
  }


  /**
   * Method to read the sample rate and audio source preferences.
   */
  private void readPreferences() {
    SharedPreferences preferences = getSharedPreferences("LevelMeter",
        MODE_PRIVATE);
    mSampleRate = preferences.getInt("SampleRate", 8000);
    mAudioSource = preferences.getInt("AudioSource", 
        MediaRecorder.AudioSource.VOICE_RECOGNITION);
  }
  
  /**
   *  This method gets called by the micInput object owned by this activity.
   *  It first computes the RMS value and then it sets up a bit of
   *  code/closure that runs on the UI thread that does the actual drawing.
   */
  @Override
  public void processAudioFrame(short[] audioFrame) {
    if (!mDrawing) {
      mDrawing = true;
      // Compute the RMS value. (Note that this does not remove DC).
      double rms = 0;
      float frameF[];
      frameF = new float[audioFrame.length];

      for (int i = 0; i < audioFrame.length; i++) {
        rms += audioFrame[i]*audioFrame[i];
        frameF[i] = (float) ((audioFrame[i]) / 32767.);
      }
      rms = Math.sqrt(rms / audioFrame.length);

      // process f0
      PitchDetectionResult pitch;

      double freqtemp;
      if (f0 != null) {
        pitch = f0.getPitch(frameF);
        freqtemp = (double) pitch.getPitch();
      }
      else
      {
        freqtemp = -1;
      }
      final double freq = freqtemp;


      // Compute a smoothed version for less flickering of the display.
      mRmsSmoothed = mRmsSmoothed * mAlpha + (1 - mAlpha) * rms;
      final double rmsdB = 20.0 * Math.log10(mGain * mRmsSmoothed);
      
      // Set up a method that runs on the UI thread to update of the LED bar
      // and numerical display.      
      mBarLevel.post(new Runnable() {
        @Override
        public void run() {
          // The bar has an input range of [0.0 ; 1.0] and 10 segments.
          // Each LED corresponds to 6 dB.
          mBarLevel.setLevel((mOffsetdB + rmsdB) / 60);

          DecimalFormat df = new DecimalFormat("##");
          mdBTextView.setText(df.format(20 + rmsdB));

          DecimalFormat df_fraction = new DecimalFormat("#");
          int one_decimal = (int) (Math.round(Math.abs(rmsdB * 10))) % 10;
          mdBFractionTextView.setText(Integer.toString(one_decimal));

          DecimalFormat df0 = new DecimalFormat("####");
          mf0TextView.setText(df0.format(freq));


          myGraph.drawDbFreq((float) rmsdB, (float) freq);
          mDrawing = false;
        }
      });
    } else {
      mDrawingCollided++;
      Log.v(TAG, "Level bar update collision, i.e. update took longer " +
          "than 20ms. Collision count" + Double.toString(mDrawingCollided));
    }
  }
}
