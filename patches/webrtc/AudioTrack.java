package org.webrtc;

public class AudioTrack extends MediaStreamTrack {
   public AudioTrack(long nativeTrack) {
      super(nativeTrack);
   }

   public void setVolume(double volume) {
      nativeSetVolume(this.getNativeAudioTrack(), volume);
   }

   long getNativeAudioTrack() {
      return this.getNativeMediaStreamTrack();
   }

   private static native void nativeSetVolume(long var0, double var2);
}
