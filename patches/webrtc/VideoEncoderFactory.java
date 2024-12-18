package org.webrtc;

import androidx.annotation.Nullable;

public interface VideoEncoderFactory {
   @Nullable
   @CalledByNative
   VideoEncoder createEncoder(VideoCodecInfo var1);

   @CalledByNative
   VideoCodecInfo[] getSupportedCodecs();

   @CalledByNative
   default VideoCodecInfo[] getImplementations() {
      return this.getSupportedCodecs();
   }

   @CalledByNative
   default VideoEncoderFactory.VideoEncoderSelector getEncoderSelector() {
      return null;
   }

   public interface VideoEncoderSelector {
      @CalledByNative("VideoEncoderSelector")
      void onCurrentEncoder(VideoCodecInfo var1);

      @Nullable
      @CalledByNative("VideoEncoderSelector")
      VideoCodecInfo onAvailableBitrate(int var1);

      @Nullable
      @CalledByNative("VideoEncoderSelector")
      default VideoCodecInfo onResolutionChange(int widht, int height) {
         return null;
      }

      @Nullable
      @CalledByNative("VideoEncoderSelector")
      VideoCodecInfo onEncoderBroken();
   }
}
