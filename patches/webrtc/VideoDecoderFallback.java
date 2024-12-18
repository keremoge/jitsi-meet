package org.webrtc;

public class VideoDecoderFallback extends WrappedNativeVideoDecoder {
   private final VideoDecoder fallback;
   private final VideoDecoder primary;

   public VideoDecoderFallback(VideoDecoder fallback, VideoDecoder primary) {
      this.fallback = fallback;
      this.primary = primary;
   }

   public long createNative(long webrtcEnvRef) {
      return nativeCreate(webrtcEnvRef, this.fallback, this.primary);
   }

   private static native long nativeCreate(long var0, VideoDecoder var2, VideoDecoder var3);
}
