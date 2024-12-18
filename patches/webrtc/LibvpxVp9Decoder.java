package org.webrtc;

public class LibvpxVp9Decoder extends WrappedNativeVideoDecoder {
   public long createNative(long webrtcEnvRef) {
      return nativeCreateDecoder();
   }

   static native long nativeCreateDecoder();

   static native boolean nativeIsSupported();
}
