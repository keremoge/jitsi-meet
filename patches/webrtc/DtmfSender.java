package org.webrtc;

public class DtmfSender {
   private long nativeDtmfSender;

   public DtmfSender(long nativeDtmfSender) {
      this.nativeDtmfSender = nativeDtmfSender;
   }

   public boolean canInsertDtmf() {
      this.checkDtmfSenderExists();
      return nativeCanInsertDtmf(this.nativeDtmfSender);
   }

   public boolean insertDtmf(String tones, int duration, int interToneGap) {
      this.checkDtmfSenderExists();
      return nativeInsertDtmf(this.nativeDtmfSender, tones, duration, interToneGap);
   }

   public String tones() {
      this.checkDtmfSenderExists();
      return nativeTones(this.nativeDtmfSender);
   }

   public int duration() {
      this.checkDtmfSenderExists();
      return nativeDuration(this.nativeDtmfSender);
   }

   public int interToneGap() {
      this.checkDtmfSenderExists();
      return nativeInterToneGap(this.nativeDtmfSender);
   }

   public void dispose() {
      this.checkDtmfSenderExists();
      JniCommon.nativeReleaseRef(this.nativeDtmfSender);
      this.nativeDtmfSender = 0L;
   }

   private void checkDtmfSenderExists() {
      if (this.nativeDtmfSender == 0L) {
         throw new IllegalStateException("DtmfSender has been disposed.");
      }
   }

   private static native boolean nativeCanInsertDtmf(long var0);

   private static native boolean nativeInsertDtmf(long var0, String var2, int var3, int var4);

   private static native String nativeTones(long var0);

   private static native int nativeDuration(long var0);

   private static native int nativeInterToneGap(long var0);
}
