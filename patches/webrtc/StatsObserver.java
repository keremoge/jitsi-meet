package org.webrtc;

public interface StatsObserver {
   @CalledByNative
   void onComplete(StatsReport[] var1);
}
