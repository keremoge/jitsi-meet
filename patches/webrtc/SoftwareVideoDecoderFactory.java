package org.webrtc;

import androidx.annotation.Nullable;
import java.util.List;

public class SoftwareVideoDecoderFactory implements VideoDecoderFactory {
   private static final String TAG = "SoftwareVideoDecoderFactory";
   private final long nativeFactory = nativeCreateFactory();

   @Nullable
   public VideoDecoder createDecoder(final VideoCodecInfo info) {
      if (!nativeIsSupported(this.nativeFactory, info)) {
         Logging.w("SoftwareVideoDecoderFactory", "Trying to create decoder for unsupported format. " + info);
         return null;
      } else {
         return new WrappedNativeVideoDecoder() {
            public long createNative(long webrtcEnvRef) {
               return SoftwareVideoDecoderFactory.nativeCreate(SoftwareVideoDecoderFactory.this.nativeFactory, webrtcEnvRef, info);
            }
         };
      }
   }

   public VideoCodecInfo[] getSupportedCodecs() {
      return (VideoCodecInfo[])nativeGetSupportedCodecs(this.nativeFactory).toArray(new VideoCodecInfo[0]);
   }

   private static native long nativeCreateFactory();

   private static native boolean nativeIsSupported(long var0, VideoCodecInfo var2);

   private static native long nativeCreate(long var0, long var2, VideoCodecInfo var4);

   private static native List<VideoCodecInfo> nativeGetSupportedCodecs(long var0);
}
