package org.webrtc;

class VideoDecoderWrapper {
   @CalledByNative
   static VideoDecoder.Callback createDecoderCallback(long nativeDecoder) {
      return (frame, decodeTimeMs, qp) -> {
         nativeOnDecodedFrame(nativeDecoder, frame, decodeTimeMs, qp);
      };
   }

   private static native void nativeOnDecodedFrame(long var0, VideoFrame var2, Integer var3, Integer var4);
}
