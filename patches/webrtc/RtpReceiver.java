package org.webrtc;

import androidx.annotation.Nullable;

public class RtpReceiver {
   private long nativeRtpReceiver;
   private long nativeObserver;
   @Nullable
   private MediaStreamTrack cachedTrack;

   @CalledByNative
   public RtpReceiver(long nativeRtpReceiver) {
      this.nativeRtpReceiver = nativeRtpReceiver;
      long nativeTrack = nativeGetTrack(nativeRtpReceiver);
      this.cachedTrack = MediaStreamTrack.createMediaStreamTrack(nativeTrack);
   }

   @Nullable
   public MediaStreamTrack track() {
      return this.cachedTrack;
   }

   public RtpParameters getParameters() {
      this.checkRtpReceiverExists();
      return nativeGetParameters(this.nativeRtpReceiver);
   }

   public String id() {
      this.checkRtpReceiverExists();
      return nativeGetId(this.nativeRtpReceiver);
   }

   long getNativeRtpReceiver() {
      this.checkRtpReceiverExists();
      return this.nativeRtpReceiver;
   }

   @CalledByNative
   public void dispose() {
      this.checkRtpReceiverExists();
      this.cachedTrack.dispose();
      if (this.nativeObserver != 0L) {
         nativeUnsetObserver(this.nativeRtpReceiver, this.nativeObserver);
         this.nativeObserver = 0L;
      }

      JniCommon.nativeReleaseRef(this.nativeRtpReceiver);
      this.nativeRtpReceiver = 0L;
   }

   public void SetObserver(RtpReceiver.Observer observer) {
      this.checkRtpReceiverExists();
      if (this.nativeObserver != 0L) {
         nativeUnsetObserver(this.nativeRtpReceiver, this.nativeObserver);
      }

      this.nativeObserver = nativeSetObserver(this.nativeRtpReceiver, observer);
   }

   public void setFrameDecryptor(FrameDecryptor frameDecryptor) {
      this.checkRtpReceiverExists();
      nativeSetFrameDecryptor(this.nativeRtpReceiver, frameDecryptor.getNativeFrameDecryptor());
   }

   private void checkRtpReceiverExists() {
      if (this.nativeRtpReceiver == 0L) {
         throw new IllegalStateException("RtpReceiver has been disposed.");
      }
   }

   private static native long nativeGetTrack(long var0);

   private static native RtpParameters nativeGetParameters(long var0);

   private static native String nativeGetId(long var0);

   private static native long nativeSetObserver(long var0, RtpReceiver.Observer var2);

   private static native void nativeUnsetObserver(long var0, long var2);

   private static native void nativeSetFrameDecryptor(long var0, long var2);

   public interface Observer {
      @CalledByNative("Observer")
      void onFirstPacketReceived(MediaStreamTrack.MediaType var1);
   }
}
