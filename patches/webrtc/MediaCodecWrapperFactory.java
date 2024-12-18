package org.webrtc;

import java.io.IOException;

interface MediaCodecWrapperFactory {
   MediaCodecWrapper createByCodecName(String var1) throws IOException;
}
