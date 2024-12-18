package org.webrtc.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioAttributes.Builder;
import android.os.Process;
import android.os.Build.VERSION;
import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import org.webrtc.CalledByNative;
import org.webrtc.Logging;
import org.webrtc.ThreadUtils;

class WebRtcAudioTrack {
   private static final String TAG = "WebRtcAudioTrackExternal";
   private static final int BITS_PER_SAMPLE = 16;
   private static final int CALLBACK_BUFFER_SIZE_MS = 10;
   private static final int BUFFERS_PER_SECOND = 100;
   private static final long AUDIO_TRACK_THREAD_JOIN_TIMEOUT_MS = 2000L;
   private static final int DEFAULT_USAGE = 2;
   private static final int AUDIO_TRACK_START = 0;
   private static final int AUDIO_TRACK_STOP = 1;
   private long nativeAudioTrack;
   private final Context context;
   private final AudioManager audioManager;
   private final ThreadUtils.ThreadChecker threadChecker;
   private ByteBuffer byteBuffer;
   @Nullable
   private final AudioAttributes audioAttributes;
   @Nullable
   private AudioTrack audioTrack;
   @Nullable
   private WebRtcAudioTrack.AudioTrackThread audioThread;
   private final VolumeLogger volumeLogger;
   private volatile boolean speakerMute;
   private byte[] emptyBytes;
   private boolean useLowLatency;
   private int initialBufferSizeInFrames;
   @Nullable
   private final JavaAudioDeviceModule.AudioTrackErrorCallback errorCallback;
   @Nullable
   private final JavaAudioDeviceModule.AudioTrackStateCallback stateCallback;

   @CalledByNative
   WebRtcAudioTrack(Context context, AudioManager audioManager) {
      this(context, audioManager, (AudioAttributes)null, (JavaAudioDeviceModule.AudioTrackErrorCallback)null, (JavaAudioDeviceModule.AudioTrackStateCallback)null, false, true);
   }

   WebRtcAudioTrack(Context context, AudioManager audioManager, @Nullable AudioAttributes audioAttributes, @Nullable JavaAudioDeviceModule.AudioTrackErrorCallback errorCallback, @Nullable JavaAudioDeviceModule.AudioTrackStateCallback stateCallback, boolean useLowLatency, boolean enableVolumeLogger) {
      this.threadChecker = new ThreadUtils.ThreadChecker();
      this.threadChecker.detachThread();
      this.context = context;
      this.audioManager = audioManager;
      this.audioAttributes = audioAttributes;
      this.errorCallback = errorCallback;
      this.stateCallback = stateCallback;
      this.volumeLogger = enableVolumeLogger ? new VolumeLogger(audioManager) : null;
      this.useLowLatency = useLowLatency;
      Logging.d("WebRtcAudioTrackExternal", "ctor" + WebRtcAudioUtils.getThreadInfo());
   }

   @CalledByNative
   public void setNativeAudioTrack(long nativeAudioTrack) {
      this.nativeAudioTrack = nativeAudioTrack;
   }

   @CalledByNative
   private int initPlayout(int sampleRate, int channels, double bufferSizeFactor) {
      this.threadChecker.checkIsOnValidThread();
      Logging.d("WebRtcAudioTrackExternal", "initPlayout(sampleRate=" + sampleRate + ", channels=" + channels + ", bufferSizeFactor=" + bufferSizeFactor + ")");
      int bytesPerFrame = channels * 2;
      this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * (sampleRate / 100));
      Logging.d("WebRtcAudioTrackExternal", "byteBuffer.capacity: " + this.byteBuffer.capacity());
      this.emptyBytes = new byte[this.byteBuffer.capacity()];
      nativeCacheDirectBufferAddress(this.nativeAudioTrack, this.byteBuffer);
      int channelConfig = this.channelCountToConfiguration(channels);
      int minBufferSizeInBytes = (int)((double)AudioTrack.getMinBufferSize(sampleRate, channelConfig, 2) * bufferSizeFactor);
      Logging.d("WebRtcAudioTrackExternal", "minBufferSizeInBytes: " + minBufferSizeInBytes);
      if (minBufferSizeInBytes < this.byteBuffer.capacity()) {
         this.reportWebRtcAudioTrackInitError("AudioTrack.getMinBufferSize returns an invalid value.");
         return -1;
      } else {
         if (bufferSizeFactor > 1.0D) {
            this.useLowLatency = false;
         }

         if (this.audioTrack != null) {
            this.reportWebRtcAudioTrackInitError("Conflict with existing AudioTrack.");
            return -1;
         } else {
            try {
               if (this.useLowLatency && VERSION.SDK_INT >= 26) {
                  this.audioTrack = createAudioTrackOnOreoOrHigher(sampleRate, channelConfig, minBufferSizeInBytes, this.audioAttributes);
               } else {
                  this.audioTrack = createAudioTrackBeforeOreo(sampleRate, channelConfig, minBufferSizeInBytes, this.audioAttributes);
               }
            } catch (IllegalArgumentException var9) {
               this.reportWebRtcAudioTrackInitError(var9.getMessage());
               this.releaseAudioResources();
               return -1;
            }

            if (this.audioTrack != null && this.audioTrack.getState() == 1) {
               if (VERSION.SDK_INT >= 23) {
                  this.initialBufferSizeInFrames = this.audioTrack.getBufferSizeInFrames();
               } else {
                  this.initialBufferSizeInFrames = -1;
               }

               this.logMainParameters();
               this.logMainParametersExtended();
               return minBufferSizeInBytes;
            } else {
               this.reportWebRtcAudioTrackInitError("Initialization of audio track failed.");
               this.releaseAudioResources();
               return -1;
            }
         }
      }
   }

   @CalledByNative
   private boolean startPlayout() {
      this.threadChecker.checkIsOnValidThread();
      if (this.volumeLogger != null) {
         this.volumeLogger.start();
      }

      Logging.d("WebRtcAudioTrackExternal", "startPlayout");
      assertTrue(this.audioTrack != null);
      assertTrue(this.audioThread == null);

      try {
         this.audioTrack.play();
      } catch (IllegalStateException var2) {
         this.reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode.AUDIO_TRACK_START_EXCEPTION, "AudioTrack.play failed: " + var2.getMessage());
         this.releaseAudioResources();
         return false;
      }

      if (this.audioTrack.getPlayState() != 3) {
         this.reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode.AUDIO_TRACK_START_STATE_MISMATCH, "AudioTrack.play failed - incorrect state :" + this.audioTrack.getPlayState());
         this.releaseAudioResources();
         return false;
      } else {
         this.audioThread = new WebRtcAudioTrack.AudioTrackThread("AudioTrackJavaThread");
         this.audioThread.start();
         return true;
      }
   }

   @CalledByNative
   private boolean stopPlayout() {
      this.threadChecker.checkIsOnValidThread();
      if (this.volumeLogger != null) {
         this.volumeLogger.stop();
      }

      Logging.d("WebRtcAudioTrackExternal", "stopPlayout");
      assertTrue(this.audioThread != null);
      this.logUnderrunCount();
      this.audioThread.stopThread();
      Logging.d("WebRtcAudioTrackExternal", "Stopping the AudioTrackThread...");
      this.audioThread.interrupt();
      if (!ThreadUtils.joinUninterruptibly(this.audioThread, 2000L)) {
         Logging.e("WebRtcAudioTrackExternal", "Join of AudioTrackThread timed out.");
         WebRtcAudioUtils.logAudioState("WebRtcAudioTrackExternal", this.context, this.audioManager);
      }

      Logging.d("WebRtcAudioTrackExternal", "AudioTrackThread has now been stopped.");
      this.audioThread = null;
      if (this.audioTrack != null) {
         Logging.d("WebRtcAudioTrackExternal", "Calling AudioTrack.stop...");

         try {
            this.audioTrack.stop();
            Logging.d("WebRtcAudioTrackExternal", "AudioTrack.stop is done.");
            this.doAudioTrackStateCallback(1);
         } catch (IllegalStateException var2) {
            Logging.e("WebRtcAudioTrackExternal", "AudioTrack.stop failed: " + var2.getMessage());
         }
      }

      this.releaseAudioResources();
      return true;
   }

   @CalledByNative
   private int getStreamMaxVolume() {
      this.threadChecker.checkIsOnValidThread();
      Logging.d("WebRtcAudioTrackExternal", "getStreamMaxVolume");
      return this.audioManager.getStreamMaxVolume(0);
   }

   @CalledByNative
   private boolean setStreamVolume(int volume) {
      this.threadChecker.checkIsOnValidThread();
      Logging.d("WebRtcAudioTrackExternal", "setStreamVolume(" + volume + ")");
      if (this.audioManager.isVolumeFixed()) {
         Logging.e("WebRtcAudioTrackExternal", "The device implements a fixed volume policy.");
         return false;
      } else {
         this.audioManager.setStreamVolume(0, volume, 0);
         return true;
      }
   }

   @CalledByNative
   private int getStreamVolume() {
      this.threadChecker.checkIsOnValidThread();
      Logging.d("WebRtcAudioTrackExternal", "getStreamVolume");
      return this.audioManager.getStreamVolume(0);
   }

   @CalledByNative
   private int GetPlayoutUnderrunCount() {
      if (VERSION.SDK_INT >= 24) {
         return this.audioTrack != null ? this.audioTrack.getUnderrunCount() : -1;
      } else {
         return -2;
      }
   }

   private void logMainParameters() {
      int var10001 = this.audioTrack.getAudioSessionId();
      Logging.d("WebRtcAudioTrackExternal", "AudioTrack: session ID: " + var10001 + ", channels: " + this.audioTrack.getChannelCount() + ", sample rate: " + this.audioTrack.getSampleRate() + ", max gain: " + AudioTrack.getMaxVolume());
   }

   private static void logNativeOutputSampleRate(int requestedSampleRateInHz) {
      int nativeOutputSampleRate = AudioTrack.getNativeOutputSampleRate(0);
      Logging.d("WebRtcAudioTrackExternal", "nativeOutputSampleRate: " + nativeOutputSampleRate);
      if (requestedSampleRateInHz != nativeOutputSampleRate) {
         Logging.w("WebRtcAudioTrackExternal", "Unable to use fast mode since requested sample rate is not native");
      }

   }

   private static AudioAttributes getAudioAttributes(@Nullable AudioAttributes overrideAttributes) {
      Builder attributesBuilder = (new Builder()).setUsage(2).setContentType(1);
      if (overrideAttributes != null) {
         if (overrideAttributes.getUsage() != 0) {
            attributesBuilder.setUsage(overrideAttributes.getUsage());
         }

         if (overrideAttributes.getContentType() != 0) {
            attributesBuilder.setContentType(overrideAttributes.getContentType());
         }

         attributesBuilder.setFlags(overrideAttributes.getFlags());
         if (VERSION.SDK_INT >= 29) {
            attributesBuilder = applyAttributesOnQOrHigher(attributesBuilder, overrideAttributes);
         }
      }

      return attributesBuilder.build();
   }

   private static AudioTrack createAudioTrackBeforeOreo(int sampleRateInHz, int channelConfig, int bufferSizeInBytes, @Nullable AudioAttributes overrideAttributes) {
      Logging.d("WebRtcAudioTrackExternal", "createAudioTrackBeforeOreo");
      logNativeOutputSampleRate(sampleRateInHz);
      return new AudioTrack(getAudioAttributes(overrideAttributes), (new android.media.AudioFormat.Builder()).setEncoding(2).setSampleRate(sampleRateInHz).setChannelMask(channelConfig).build(), bufferSizeInBytes, 1, 0);
   }

   @TargetApi(26)
   private static AudioTrack createAudioTrackOnOreoOrHigher(int sampleRateInHz, int channelConfig, int bufferSizeInBytes, @Nullable AudioAttributes overrideAttributes) {
      Logging.d("WebRtcAudioTrackExternal", "createAudioTrackOnOreoOrHigher");
      logNativeOutputSampleRate(sampleRateInHz);
      return (new android.media.AudioTrack.Builder()).setAudioAttributes(getAudioAttributes(overrideAttributes)).setAudioFormat((new android.media.AudioFormat.Builder()).setEncoding(2).setSampleRate(sampleRateInHz).setChannelMask(channelConfig).build()).setBufferSizeInBytes(bufferSizeInBytes).setPerformanceMode(1).setTransferMode(1).setSessionId(0).build();
   }

   @TargetApi(29)
   private static Builder applyAttributesOnQOrHigher(Builder builder, AudioAttributes overrideAttributes) {
      return builder.setAllowedCapturePolicy(overrideAttributes.getAllowedCapturePolicy());
   }

   private void logBufferSizeInFrames() {
      if (VERSION.SDK_INT >= 23) {
         Logging.d("WebRtcAudioTrackExternal", "AudioTrack: buffer size in frames: " + this.audioTrack.getBufferSizeInFrames());
      }

   }

   @CalledByNative
   private int getBufferSizeInFrames() {
      return VERSION.SDK_INT >= 23 ? this.audioTrack.getBufferSizeInFrames() : -1;
   }

   @CalledByNative
   private int getInitialBufferSizeInFrames() {
      return this.initialBufferSizeInFrames;
   }

   private void logBufferCapacityInFrames() {
      if (VERSION.SDK_INT >= 24) {
         Logging.d("WebRtcAudioTrackExternal", "AudioTrack: buffer capacity in frames: " + this.audioTrack.getBufferCapacityInFrames());
      }

   }

   private void logMainParametersExtended() {
      this.logBufferSizeInFrames();
      this.logBufferCapacityInFrames();
   }

   private void logUnderrunCount() {
      if (VERSION.SDK_INT >= 24) {
         Logging.d("WebRtcAudioTrackExternal", "underrun count: " + this.audioTrack.getUnderrunCount());
      }

   }

   private static void assertTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected condition to be true");
      }
   }

   private int channelCountToConfiguration(int channels) {
      return channels == 1 ? 4 : 12;
   }

   private static native void nativeCacheDirectBufferAddress(long var0, ByteBuffer var2);

   private static native void nativeGetPlayoutData(long var0, int var2);

   public void setSpeakerMute(boolean mute) {
      Logging.w("WebRtcAudioTrackExternal", "setSpeakerMute(" + mute + ")");
      this.speakerMute = mute;
   }

   private void releaseAudioResources() {
      Logging.d("WebRtcAudioTrackExternal", "releaseAudioResources");
      if (this.audioTrack != null) {
         this.audioTrack.release();
         this.audioTrack = null;
      }

   }

   private void reportWebRtcAudioTrackInitError(String errorMessage) {
      Logging.e("WebRtcAudioTrackExternal", "Init playout error: " + errorMessage);
      WebRtcAudioUtils.logAudioState("WebRtcAudioTrackExternal", this.context, this.audioManager);
      if (this.errorCallback != null) {
         this.errorCallback.onWebRtcAudioTrackInitError(errorMessage);
      }

   }

   private void reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
      Logging.e("WebRtcAudioTrackExternal", "Start playout error: " + errorCode + ". " + errorMessage);
      WebRtcAudioUtils.logAudioState("WebRtcAudioTrackExternal", this.context, this.audioManager);
      if (this.errorCallback != null) {
         this.errorCallback.onWebRtcAudioTrackStartError(errorCode, errorMessage);
      }

   }

   private void reportWebRtcAudioTrackError(String errorMessage) {
      Logging.e("WebRtcAudioTrackExternal", "Run-time playback error: " + errorMessage);
      WebRtcAudioUtils.logAudioState("WebRtcAudioTrackExternal", this.context, this.audioManager);
      if (this.errorCallback != null) {
         this.errorCallback.onWebRtcAudioTrackError(errorMessage);
      }

   }

   private void doAudioTrackStateCallback(int audioState) {
      Logging.d("WebRtcAudioTrackExternal", "doAudioTrackStateCallback: " + audioState);
      if (this.stateCallback != null) {
         if (audioState == 0) {
            this.stateCallback.onWebRtcAudioTrackStart();
         } else if (audioState == 1) {
            this.stateCallback.onWebRtcAudioTrackStop();
         } else {
            Logging.e("WebRtcAudioTrackExternal", "Invalid audio state");
         }
      }

   }

   private class AudioTrackThread extends Thread {
      private volatile boolean keepAlive = true;
      private LowLatencyAudioBufferManager bufferManager = new LowLatencyAudioBufferManager();

      public AudioTrackThread(String name) {
         super(name);
      }

      public void run() {
         Process.setThreadPriority(-19);
         String var10001 = WebRtcAudioUtils.getThreadInfo();
         Logging.d("WebRtcAudioTrackExternal", "AudioTrackThread" + var10001);
         WebRtcAudioTrack.assertTrue(WebRtcAudioTrack.this.audioTrack.getPlayState() == 3);
         WebRtcAudioTrack.this.doAudioTrackStateCallback(0);

         for(int sizeInBytes = WebRtcAudioTrack.this.byteBuffer.capacity(); this.keepAlive; WebRtcAudioTrack.this.byteBuffer.rewind()) {
            WebRtcAudioTrack.nativeGetPlayoutData(WebRtcAudioTrack.this.nativeAudioTrack, sizeInBytes);
            WebRtcAudioTrack.assertTrue(sizeInBytes <= WebRtcAudioTrack.this.byteBuffer.remaining());
            if (WebRtcAudioTrack.this.speakerMute) {
               WebRtcAudioTrack.this.byteBuffer.clear();
               WebRtcAudioTrack.this.byteBuffer.put(WebRtcAudioTrack.this.emptyBytes);
               WebRtcAudioTrack.this.byteBuffer.position(0);
            }

            int bytesWritten = WebRtcAudioTrack.this.audioTrack.write(WebRtcAudioTrack.this.byteBuffer, sizeInBytes, 0);
            if (bytesWritten != sizeInBytes) {
               Logging.e("WebRtcAudioTrackExternal", "AudioTrack.write played invalid number of bytes: " + bytesWritten);
               if (bytesWritten < 0) {
                  this.keepAlive = false;
                  WebRtcAudioTrack.this.reportWebRtcAudioTrackError("AudioTrack.write failed: " + bytesWritten);
               }
            }

            if (WebRtcAudioTrack.this.useLowLatency) {
               this.bufferManager.maybeAdjustBufferSize(WebRtcAudioTrack.this.audioTrack);
            }
         }

      }

      public void stopThread() {
         Logging.d("WebRtcAudioTrackExternal", "stopThread");
         this.keepAlive = false;
      }
   }
}
