package org.webrtc.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build.VERSION;
import androidx.annotation.RequiresApi;
import java.util.concurrent.ScheduledExecutorService;
import org.webrtc.JniCommon;
import org.webrtc.Logging;

public class JavaAudioDeviceModule implements AudioDeviceModule {
   private static final String TAG = "JavaAudioDeviceModule";
   private final Context context;
   private final AudioManager audioManager;
   private final WebRtcAudioRecord audioInput;
   private final WebRtcAudioTrack audioOutput;
   private final int inputSampleRate;
   private final int outputSampleRate;
   private final boolean useStereoInput;
   private final boolean useStereoOutput;
   private final Object nativeLock = new Object();
   private long nativeAudioDeviceModule;

   public static JavaAudioDeviceModule.Builder builder(Context context) {
      return new JavaAudioDeviceModule.Builder(context);
   }

   public static boolean isBuiltInAcousticEchoCancelerSupported() {
      return WebRtcAudioEffects.isAcousticEchoCancelerSupported();
   }

   public static boolean isBuiltInNoiseSuppressorSupported() {
      return WebRtcAudioEffects.isNoiseSuppressorSupported();
   }

   private JavaAudioDeviceModule(Context context, AudioManager audioManager, WebRtcAudioRecord audioInput, WebRtcAudioTrack audioOutput, int inputSampleRate, int outputSampleRate, boolean useStereoInput, boolean useStereoOutput) {
      this.context = context;
      this.audioManager = audioManager;
      this.audioInput = audioInput;
      this.audioOutput = audioOutput;
      this.inputSampleRate = inputSampleRate;
      this.outputSampleRate = outputSampleRate;
      this.useStereoInput = useStereoInput;
      this.useStereoOutput = useStereoOutput;
   }

   public long getNativeAudioDeviceModulePointer() {
      synchronized(this.nativeLock) {
         if (this.nativeAudioDeviceModule == 0L) {
            this.nativeAudioDeviceModule = nativeCreateAudioDeviceModule(this.context, this.audioManager, this.audioInput, this.audioOutput, this.inputSampleRate, this.outputSampleRate, this.useStereoInput, this.useStereoOutput);
         }

         return this.nativeAudioDeviceModule;
      }
   }

   public void release() {
      synchronized(this.nativeLock) {
         if (this.nativeAudioDeviceModule != 0L) {
            JniCommon.nativeReleaseRef(this.nativeAudioDeviceModule);
            this.nativeAudioDeviceModule = 0L;
         }

      }
   }

   public void setSpeakerMute(boolean mute) {
      Logging.d("JavaAudioDeviceModule", "setSpeakerMute: " + mute);
      this.audioOutput.setSpeakerMute(mute);
   }

   public void setMicrophoneMute(boolean mute) {
      Logging.d("JavaAudioDeviceModule", "setMicrophoneMute: " + mute);
      this.audioInput.setMicrophoneMute(mute);
   }

   public boolean setNoiseSuppressorEnabled(boolean enabled) {
      Logging.d("JavaAudioDeviceModule", "setNoiseSuppressorEnabled: " + enabled);
      return this.audioInput.setNoiseSuppressorEnabled(enabled);
   }

   @RequiresApi(23)
   public void setPreferredInputDevice(AudioDeviceInfo preferredInputDevice) {
      Logging.d("JavaAudioDeviceModule", "setPreferredInputDevice: " + preferredInputDevice);
      this.audioInput.setPreferredDevice(preferredInputDevice);
   }

   private static native long nativeCreateAudioDeviceModule(Context var0, AudioManager var1, WebRtcAudioRecord var2, WebRtcAudioTrack var3, int var4, int var5, boolean var6, boolean var7);

   public static class Builder {
      private final Context context;
      private ScheduledExecutorService scheduler;
      private final AudioManager audioManager;
      private int inputSampleRate;
      private int outputSampleRate;
      private int audioSource = 7;
      private int audioFormat = 2;
      private JavaAudioDeviceModule.AudioTrackErrorCallback audioTrackErrorCallback;
      private JavaAudioDeviceModule.AudioRecordErrorCallback audioRecordErrorCallback;
      private JavaAudioDeviceModule.SamplesReadyCallback samplesReadyCallback;
      private JavaAudioDeviceModule.AudioTrackStateCallback audioTrackStateCallback;
      private JavaAudioDeviceModule.AudioRecordStateCallback audioRecordStateCallback;
      private boolean useHardwareAcousticEchoCanceler = JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported();
      private boolean useHardwareNoiseSuppressor = JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported();
      private boolean useStereoInput;
      private boolean useStereoOutput;
      private AudioAttributes audioAttributes;
      private boolean useLowLatency;
      private boolean enableVolumeLogger;

      private Builder(Context context) {
         this.context = context;
         this.audioManager = (AudioManager)context.getSystemService("audio");
         this.inputSampleRate = WebRtcAudioManager.getSampleRate(this.audioManager);
         this.outputSampleRate = WebRtcAudioManager.getSampleRate(this.audioManager);
         this.useLowLatency = false;
         this.enableVolumeLogger = true;
      }

      public JavaAudioDeviceModule.Builder setScheduler(ScheduledExecutorService scheduler) {
         this.scheduler = scheduler;
         return this;
      }

      public JavaAudioDeviceModule.Builder setSampleRate(int sampleRate) {
         Logging.d("JavaAudioDeviceModule", "Input/Output sample rate overridden to: " + sampleRate);
         this.inputSampleRate = sampleRate;
         this.outputSampleRate = sampleRate;
         return this;
      }

      public JavaAudioDeviceModule.Builder setInputSampleRate(int inputSampleRate) {
         Logging.d("JavaAudioDeviceModule", "Input sample rate overridden to: " + inputSampleRate);
         this.inputSampleRate = inputSampleRate;
         return this;
      }

      public JavaAudioDeviceModule.Builder setOutputSampleRate(int outputSampleRate) {
         Logging.d("JavaAudioDeviceModule", "Output sample rate overridden to: " + outputSampleRate);
         this.outputSampleRate = outputSampleRate;
         return this;
      }

      public JavaAudioDeviceModule.Builder setAudioSource(int audioSource) {
         this.audioSource = audioSource;
         return this;
      }

      public JavaAudioDeviceModule.Builder setAudioFormat(int audioFormat) {
         this.audioFormat = audioFormat;
         return this;
      }

      public JavaAudioDeviceModule.Builder setAudioTrackErrorCallback(JavaAudioDeviceModule.AudioTrackErrorCallback audioTrackErrorCallback) {
         this.audioTrackErrorCallback = audioTrackErrorCallback;
         return this;
      }

      public JavaAudioDeviceModule.Builder setAudioRecordErrorCallback(JavaAudioDeviceModule.AudioRecordErrorCallback audioRecordErrorCallback) {
         this.audioRecordErrorCallback = audioRecordErrorCallback;
         return this;
      }

      public JavaAudioDeviceModule.Builder setSamplesReadyCallback(JavaAudioDeviceModule.SamplesReadyCallback samplesReadyCallback) {
         this.samplesReadyCallback = samplesReadyCallback;
         return this;
      }

      public JavaAudioDeviceModule.Builder setAudioTrackStateCallback(JavaAudioDeviceModule.AudioTrackStateCallback audioTrackStateCallback) {
         this.audioTrackStateCallback = audioTrackStateCallback;
         return this;
      }

      public JavaAudioDeviceModule.Builder setAudioRecordStateCallback(JavaAudioDeviceModule.AudioRecordStateCallback audioRecordStateCallback) {
         this.audioRecordStateCallback = audioRecordStateCallback;
         return this;
      }

      public JavaAudioDeviceModule.Builder setUseHardwareNoiseSuppressor(boolean useHardwareNoiseSuppressor) {
         if (useHardwareNoiseSuppressor && !JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()) {
            Logging.e("JavaAudioDeviceModule", "HW NS not supported");
            useHardwareNoiseSuppressor = false;
         }

         this.useHardwareNoiseSuppressor = useHardwareNoiseSuppressor;
         return this;
      }

      public JavaAudioDeviceModule.Builder setUseHardwareAcousticEchoCanceler(boolean useHardwareAcousticEchoCanceler) {
         if (useHardwareAcousticEchoCanceler && !JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()) {
            Logging.e("JavaAudioDeviceModule", "HW AEC not supported");
            useHardwareAcousticEchoCanceler = false;
         }

         this.useHardwareAcousticEchoCanceler = useHardwareAcousticEchoCanceler;
         return this;
      }

      public JavaAudioDeviceModule.Builder setUseStereoInput(boolean useStereoInput) {
         this.useStereoInput = useStereoInput;
         return this;
      }

      public JavaAudioDeviceModule.Builder setUseStereoOutput(boolean useStereoOutput) {
         this.useStereoOutput = useStereoOutput;
         return this;
      }

      public JavaAudioDeviceModule.Builder setUseLowLatency(boolean useLowLatency) {
         this.useLowLatency = useLowLatency;
         return this;
      }

      public JavaAudioDeviceModule.Builder setAudioAttributes(AudioAttributes audioAttributes) {
         this.audioAttributes = audioAttributes;
         return this;
      }

      public JavaAudioDeviceModule.Builder setEnableVolumeLogger(boolean enableVolumeLogger) {
         this.enableVolumeLogger = enableVolumeLogger;
         return this;
      }

      public JavaAudioDeviceModule createAudioDeviceModule() {
         Logging.d("JavaAudioDeviceModule", "createAudioDeviceModule");
         if (this.useHardwareNoiseSuppressor) {
            Logging.d("JavaAudioDeviceModule", "HW NS will be used.");
         } else {
            if (JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()) {
               Logging.d("JavaAudioDeviceModule", "Overriding default behavior; now using WebRTC NS!");
            }

            Logging.d("JavaAudioDeviceModule", "HW NS will not be used.");
         }

         if (this.useHardwareAcousticEchoCanceler) {
            Logging.d("JavaAudioDeviceModule", "HW AEC will be used.");
         } else {
            if (JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()) {
               Logging.d("JavaAudioDeviceModule", "Overriding default behavior; now using WebRTC AEC!");
            }

            Logging.d("JavaAudioDeviceModule", "HW AEC will not be used.");
         }

         int MIN_LOW_LATENCY_SDK_VERSION = true;
         if (this.useLowLatency && VERSION.SDK_INT >= 26) {
            Logging.d("JavaAudioDeviceModule", "Low latency mode will be used.");
         }

         ScheduledExecutorService executor = this.scheduler;
         if (executor == null) {
            executor = WebRtcAudioRecord.newDefaultScheduler();
         }

         WebRtcAudioRecord audioInput = new WebRtcAudioRecord(this.context, executor, this.audioManager, this.audioSource, this.audioFormat, this.audioRecordErrorCallback, this.audioRecordStateCallback, this.samplesReadyCallback, this.useHardwareAcousticEchoCanceler, this.useHardwareNoiseSuppressor);
         WebRtcAudioTrack audioOutput = new WebRtcAudioTrack(this.context, this.audioManager, this.audioAttributes, this.audioTrackErrorCallback, this.audioTrackStateCallback, this.useLowLatency, this.enableVolumeLogger);
         return new JavaAudioDeviceModule(this.context, this.audioManager, audioInput, audioOutput, this.inputSampleRate, this.outputSampleRate, this.useStereoInput, this.useStereoOutput);
      }
   }

   public interface AudioTrackStateCallback {
      void onWebRtcAudioTrackStart();

      void onWebRtcAudioTrackStop();
   }

   public interface AudioTrackErrorCallback {
      void onWebRtcAudioTrackInitError(String var1);

      void onWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode var1, String var2);

      void onWebRtcAudioTrackError(String var1);
   }

   public static enum AudioTrackStartErrorCode {
      AUDIO_TRACK_START_EXCEPTION,
      AUDIO_TRACK_START_STATE_MISMATCH;

      // $FF: synthetic method
      private static JavaAudioDeviceModule.AudioTrackStartErrorCode[] $values() {
         return new JavaAudioDeviceModule.AudioTrackStartErrorCode[]{AUDIO_TRACK_START_EXCEPTION, AUDIO_TRACK_START_STATE_MISMATCH};
      }
   }

   public interface SamplesReadyCallback {
      void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples var1);
   }

   public static class AudioSamples {
      private final int audioFormat;
      private final int channelCount;
      private final int sampleRate;
      private final byte[] data;

      public AudioSamples(int audioFormat, int channelCount, int sampleRate, byte[] data) {
         this.audioFormat = audioFormat;
         this.channelCount = channelCount;
         this.sampleRate = sampleRate;
         this.data = data;
      }

      public int getAudioFormat() {
         return this.audioFormat;
      }

      public int getChannelCount() {
         return this.channelCount;
      }

      public int getSampleRate() {
         return this.sampleRate;
      }

      public byte[] getData() {
         return this.data;
      }
   }

   public interface AudioRecordStateCallback {
      void onWebRtcAudioRecordStart();

      void onWebRtcAudioRecordStop();
   }

   public interface AudioRecordErrorCallback {
      void onWebRtcAudioRecordInitError(String var1);

      void onWebRtcAudioRecordStartError(JavaAudioDeviceModule.AudioRecordStartErrorCode var1, String var2);

      void onWebRtcAudioRecordError(String var1);
   }

   public static enum AudioRecordStartErrorCode {
      AUDIO_RECORD_START_EXCEPTION,
      AUDIO_RECORD_START_STATE_MISMATCH;

      // $FF: synthetic method
      private static JavaAudioDeviceModule.AudioRecordStartErrorCode[] $values() {
         return new JavaAudioDeviceModule.AudioRecordStartErrorCode[]{AUDIO_RECORD_START_EXCEPTION, AUDIO_RECORD_START_STATE_MISMATCH};
      }
   }
}
