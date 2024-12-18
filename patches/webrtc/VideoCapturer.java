package org.webrtc;

import android.content.Context;

public interface VideoCapturer {
   void initialize(SurfaceTextureHelper var1, Context var2, CapturerObserver var3);

   void startCapture(int var1, int var2, int var3);

   void stopCapture() throws InterruptedException;

   void changeCaptureFormat(int var1, int var2, int var3);

   void dispose();

   boolean isScreencast();
}
