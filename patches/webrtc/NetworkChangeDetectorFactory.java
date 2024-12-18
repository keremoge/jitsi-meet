package org.webrtc;

import android.content.Context;

public interface NetworkChangeDetectorFactory {
   NetworkChangeDetector create(NetworkChangeDetector.Observer var1, Context var2);
}
