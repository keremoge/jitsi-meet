package org.webrtc;

class FramerateBitrateAdjuster extends BaseBitrateAdjuster {
   private static final int DEFAULT_FRAMERATE_FPS = 30;

   public void setTargets(int targetBitrateBps, double targetFramerateFps) {
      this.targetFramerateFps = 30.0D;
      this.targetBitrateBps = (int)((double)(targetBitrateBps * 30) / targetFramerateFps);
   }
}
