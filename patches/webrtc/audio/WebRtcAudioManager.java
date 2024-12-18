package org.webrtc.audio;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import org.webrtc.CalledByNative;
import org.webrtc.Logging;

class WebRtcAudioManager {
   private static final String TAG = "WebRtcAudioManagerExternal";
   private static final int DEFAULT_SAMPLE_RATE_HZ = 16000;
   private static final int BITS_PER_SAMPLE = 16;
   private static final int DEFAULT_FRAME_PER_BUFFER = 256;

   @CalledByNative
   static AudioManager getAudioManager(Context context) {
      return (AudioManager)context.getSystemService("audio");
   }

   @CalledByNative
   static int getOutputBufferSize(Context context, AudioManager audioManager, int sampleRate, int numberOfOutputChannels) {
      return isLowLatencyOutputSupported(context) ? getLowLatencyFramesPerBuffer(audioManager) : getMinOutputFrameSize(sampleRate, numberOfOutputChannels);
   }

   @CalledByNative
   static int getInputBufferSize(Context context, AudioManager audioManager, int sampleRate, int numberOfInputChannels) {
      return isLowLatencyInputSupported(context) ? getLowLatencyFramesPerBuffer(audioManager) : getMinInputFrameSize(sampleRate, numberOfInputChannels);
   }

   @CalledByNative
   static boolean isLowLatencyOutputSupported(Context context) {
      return context.getPackageManager().hasSystemFeature("android.hardware.audio.low_latency");
   }

   @CalledByNative
   static boolean isLowLatencyInputSupported(Context context) {
      return isLowLatencyOutputSupported(context);
   }

   @CalledByNative
   static int getSampleRate(AudioManager audioManager) {
      if (WebRtcAudioUtils.runningOnEmulator()) {
         Logging.d("WebRtcAudioManagerExternal", "Running emulator, overriding sample rate to 8 kHz.");
         return 8000;
      } else {
         int sampleRateHz = getSampleRateForApiLevel(audioManager);
         Logging.d("WebRtcAudioManagerExternal", "Sample rate is set to " + sampleRateHz + " Hz");
         return sampleRateHz;
      }
   }

   private static int getSampleRateForApiLevel(AudioManager audioManager) {
      String sampleRateString = audioManager.getProperty("android.media.property.OUTPUT_SAMPLE_RATE");
      return sampleRateString == null ? 16000 : Integer.parseInt(sampleRateString);
   }

   private static int getLowLatencyFramesPerBuffer(AudioManager audioManager) {
      String framesPerBuffer = audioManager.getProperty("android.media.property.OUTPUT_FRAMES_PER_BUFFER");
      return framesPerBuffer == null ? 256 : Integer.parseInt(framesPerBuffer);
   }

   private static int getMinOutputFrameSize(int sampleRateInHz, int numChannels) {
      int bytesPerFrame = numChannels * 2;
      int channelConfig = numChannels == 1 ? 4 : 12;
      return AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, 2) / bytesPerFrame;
   }

   private static int getMinInputFrameSize(int sampleRateInHz, int numChannels) {
      int bytesPerFrame = numChannels * 2;
      int channelConfig = numChannels == 1 ? 16 : 12;
      return AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, 2) / bytesPerFrame;
   }
}
