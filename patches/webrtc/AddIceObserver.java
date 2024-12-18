package org.webrtc;

public interface AddIceObserver {
   @CalledByNative
   void onAddSuccess();

   @CalledByNative
   void onAddFailure(String var1);
}
