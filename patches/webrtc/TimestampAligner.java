package org.webrtc;

public class TimestampAligner {
   private volatile long nativeTimestampAligner = nativeCreateTimestampAligner();

   public static long getRtcTimeNanos() {
      return nativeRtcTimeNanos();
   }

   public long translateTimestamp(long cameraTimeNs) {
      this.checkNativeAlignerExists();
      return nativeTranslateTimestamp(this.nativeTimestampAligner, cameraTimeNs);
   }

   public void dispose() {
      this.checkNativeAlignerExists();
      nativeReleaseTimestampAligner(this.nativeTimestampAligner);
      this.nativeTimestampAligner = 0L;
   }

   private void checkNativeAlignerExists() {
      if (this.nativeTimestampAligner == 0L) {
         throw new IllegalStateException("TimestampAligner has been disposed.");
      }
   }

   private static native long nativeRtcTimeNanos();

   private static native long nativeCreateTimestampAligner();

   private static native void nativeReleaseTimestampAligner(long var0);

   private static native long nativeTranslateTimestamp(long var0, long var2);
}
