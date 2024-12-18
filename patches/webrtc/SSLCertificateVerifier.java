package org.webrtc;

public interface SSLCertificateVerifier {
   @CalledByNative
   boolean verify(byte[] var1);
}
