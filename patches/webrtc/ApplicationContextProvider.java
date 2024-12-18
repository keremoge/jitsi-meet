package org.webrtc;

import android.content.Context;

public class ApplicationContextProvider {
   @CalledByNative
   public static Context getApplicationContext() {
      return ContextUtils.getApplicationContext();
   }
}
