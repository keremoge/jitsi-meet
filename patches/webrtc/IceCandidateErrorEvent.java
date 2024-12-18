package org.webrtc;

public final class IceCandidateErrorEvent {
   public final String address;
   public final int port;
   public final String url;
   public final int errorCode;
   public final String errorText;

   @CalledByNative
   public IceCandidateErrorEvent(String address, int port, String url, int errorCode, String errorText) {
      this.address = address;
      this.port = port;
      this.url = url;
      this.errorCode = errorCode;
      this.errorText = errorText;
   }
}
