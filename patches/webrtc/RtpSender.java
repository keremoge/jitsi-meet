package org.webrtc;

import androidx.annotation.Nullable;
import java.util.List;

public class RtpSender {
   private long nativeRtpSender;
   @Nullable
   private MediaStreamTrack cachedTrack;
   private boolean ownsTrack = true;
   @Nullable
   private final DtmfSender dtmfSender;

   @CalledByNative
   public RtpSender(long nativeRtpSender) {
      this.nativeRtpSender = nativeRtpSender;
      long nativeTrack = nativeGetTrack(nativeRtpSender);
      this.cachedTrack = MediaStreamTrack.createMediaStreamTrack(nativeTrack);
      if (nativeGetMediaType(nativeRtpSender).equalsIgnoreCase("audio")) {
         long nativeDtmfSender = nativeGetDtmfSender(nativeRtpSender);
         this.dtmfSender = nativeDtmfSender != 0L ? new DtmfSender(nativeDtmfSender) : null;
      } else {
         this.dtmfSender = null;
      }

   }

   public boolean setTrack(@Nullable MediaStreamTrack track, boolean takeOwnership) {
      this.checkRtpSenderExists();
      if (!nativeSetTrack(this.nativeRtpSender, track == null ? 0L : track.getNativeMediaStreamTrack())) {
         return false;
      } else {
         if (this.cachedTrack != null && this.ownsTrack) {
            this.cachedTrack.dispose();
         }

         this.cachedTrack = track;
         this.ownsTrack = takeOwnership;
         return true;
      }
   }

   @Nullable
   public MediaStreamTrack track() {
      return this.cachedTrack;
   }

   public void setStreams(List<String> streamIds) {
      this.checkRtpSenderExists();
      nativeSetStreams(this.nativeRtpSender, streamIds);
   }

   public List<String> getStreams() {
      this.checkRtpSenderExists();
      return nativeGetStreams(this.nativeRtpSender);
   }

   public boolean setParameters(RtpParameters parameters) {
      this.checkRtpSenderExists();
      return nativeSetParameters(this.nativeRtpSender, parameters);
   }

   public RtpParameters getParameters() {
      this.checkRtpSenderExists();
      return nativeGetParameters(this.nativeRtpSender);
   }

   public String id() {
      this.checkRtpSenderExists();
      return nativeGetId(this.nativeRtpSender);
   }

   @Nullable
   public DtmfSender dtmf() {
      return this.dtmfSender;
   }

   public void setFrameEncryptor(FrameEncryptor frameEncryptor) {
      this.checkRtpSenderExists();
      nativeSetFrameEncryptor(this.nativeRtpSender, frameEncryptor.getNativeFrameEncryptor());
   }

   public void dispose() {
      this.checkRtpSenderExists();
      if (this.dtmfSender != null) {
         this.dtmfSender.dispose();
      }

      if (this.cachedTrack != null && this.ownsTrack) {
         this.cachedTrack.dispose();
      }

      JniCommon.nativeReleaseRef(this.nativeRtpSender);
      this.nativeRtpSender = 0L;
   }

   long getNativeRtpSender() {
      this.checkRtpSenderExists();
      return this.nativeRtpSender;
   }

   private void checkRtpSenderExists() {
      if (this.nativeRtpSender == 0L) {
         throw new IllegalStateException("RtpSender has been disposed.");
      }
   }

   private static native boolean nativeSetTrack(long var0, long var2);

   private static native long nativeGetTrack(long var0);

   private static native void nativeSetStreams(long var0, List<String> var2);

   private static native List<String> nativeGetStreams(long var0);

   private static native long nativeGetDtmfSender(long var0);

   private static native boolean nativeSetParameters(long var0, RtpParameters var2);

   private static native RtpParameters nativeGetParameters(long var0);

   private static native String nativeGetId(long var0);

   private static native void nativeSetFrameEncryptor(long var0, long var2);

   private static native String nativeGetMediaType(long var0);
}
