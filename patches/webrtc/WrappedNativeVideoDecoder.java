package org.webrtc;

public abstract class WrappedNativeVideoDecoder implements VideoDecoder {
   public abstract long createNative(long var1);

   public final VideoCodecStatus initDecode(VideoDecoder.Settings settings, VideoDecoder.Callback decodeCallback) {
      throw new UnsupportedOperationException("Not implemented.");
   }

   public final VideoCodecStatus release() {
      throw new UnsupportedOperationException("Not implemented.");
   }

   public final VideoCodecStatus decode(EncodedImage frame, VideoDecoder.DecodeInfo info) {
      throw new UnsupportedOperationException("Not implemented.");
   }

   public final String getImplementationName() {
      throw new UnsupportedOperationException("Not implemented.");
   }
}
