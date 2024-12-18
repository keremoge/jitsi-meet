package org.webrtc;

class BaseBitrateAdjuster implements BitrateAdjuster {
   protected int targetBitrateBps;
   protected double targetFramerateFps;

   public void setTargets(int targetBitrateBps, double targetFramerateFps) {
      this.targetBitrateBps = targetBitrateBps;
      this.targetFramerateFps = targetFramerateFps;
   }

   public void reportEncodedFrame(int size) {
   }

   public int getAdjustedBitrateBps() {
      return this.targetBitrateBps;
   }

   public double getAdjustedFramerateFps() {
      return this.targetFramerateFps;
   }
}
