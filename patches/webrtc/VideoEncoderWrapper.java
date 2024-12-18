package org.webrtc;

import androidx.annotation.Nullable;

class VideoEncoderWrapper {
   @CalledByNative
   static boolean getScalingSettingsOn(VideoEncoder.ScalingSettings scalingSettings) {
      return scalingSettings.on;
   }

   @Nullable
   @CalledByNative
   static Integer getScalingSettingsLow(VideoEncoder.ScalingSettings scalingSettings) {
      return scalingSettings.low;
   }

   @Nullable
   @CalledByNative
   static Integer getScalingSettingsHigh(VideoEncoder.ScalingSettings scalingSettings) {
      return scalingSettings.high;
   }

   @CalledByNative
   static VideoEncoder.Callback createEncoderCallback(long nativeEncoder) {
      return (frame, info) -> {
         nativeOnEncodedFrame(nativeEncoder, frame);
      };
   }

   private static native void nativeOnEncodedFrame(long var0, EncodedImage var2);
}
