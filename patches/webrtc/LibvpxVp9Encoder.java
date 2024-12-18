package org.webrtc;

public class LibvpxVp9Encoder extends WrappedNativeVideoEncoder {
   public long createNativeVideoEncoder() {
      return nativeCreateEncoder();
   }

   public long createNative(long webrtcEnvRef) {
      return nativeCreate(webrtcEnvRef);
   }

   static native long nativeCreateEncoder();

   static native long nativeCreate(long var0);

   public boolean isHardwareEncoder() {
      return false;
   }

   static native boolean nativeIsSupported();
}
