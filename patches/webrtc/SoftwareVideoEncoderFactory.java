package org.webrtc;

import androidx.annotation.Nullable;
import java.util.List;

public class SoftwareVideoEncoderFactory implements VideoEncoderFactory {
   private static final String TAG = "SoftwareVideoEncoderFactory";
   private final long nativeFactory = nativeCreateFactory();

   @Nullable
   public VideoEncoder createEncoder(final VideoCodecInfo info) {
      if (!nativeIsSupported(this.nativeFactory, info)) {
         Logging.w("SoftwareVideoEncoderFactory", "Trying to create encoder for unsupported format. " + info);
         return null;
      } else {
         return new WrappedNativeVideoEncoder() {
            public long createNativeVideoEncoder() {
               return SoftwareVideoEncoderFactory.nativeCreateEncoder(SoftwareVideoEncoderFactory.this.nativeFactory, info);
            }

            public long createNative(long webrtcEnvRef) {
               return SoftwareVideoEncoderFactory.nativeCreate(SoftwareVideoEncoderFactory.this.nativeFactory, webrtcEnvRef, info);
            }

            public boolean isHardwareEncoder() {
               return false;
            }
         };
      }
   }

   public VideoCodecInfo[] getSupportedCodecs() {
      return (VideoCodecInfo[])nativeGetSupportedCodecs(this.nativeFactory).toArray(new VideoCodecInfo[0]);
   }

   private static native long nativeCreateFactory();

   private static native long nativeCreateEncoder(long var0, VideoCodecInfo var2);

   private static native boolean nativeIsSupported(long var0, VideoCodecInfo var2);

   private static native long nativeCreate(long var0, long var2, VideoCodecInfo var4);

   private static native List<VideoCodecInfo> nativeGetSupportedCodecs(long var0);
}
