package org.webrtc;

public class LibvpxVp8Decoder extends WrappedNativeVideoDecoder {
   public long createNative(long webrtcEnvRef) {
      return nativeCreateDecoder(webrtcEnvRef);
   }

   static native long nativeCreateDecoder(long var0);
}
