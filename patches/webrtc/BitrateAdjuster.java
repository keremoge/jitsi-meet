package org.webrtc;

interface BitrateAdjuster {
   void setTargets(int var1, double var2);

   void reportEncodedFrame(int var1);

   int getAdjustedBitrateBps();

   double getAdjustedFramerateFps();
}
