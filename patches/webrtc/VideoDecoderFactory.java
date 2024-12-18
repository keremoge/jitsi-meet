package org.webrtc;

import androidx.annotation.Nullable;

public interface VideoDecoderFactory {
   @Nullable
   @CalledByNative
   VideoDecoder createDecoder(VideoCodecInfo var1);

   @CalledByNative
   default VideoCodecInfo[] getSupportedCodecs() {
      return new VideoCodecInfo[0];
   }
}
