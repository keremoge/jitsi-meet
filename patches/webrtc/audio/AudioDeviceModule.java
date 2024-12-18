package org.webrtc.audio;

public interface AudioDeviceModule {
   long getNativeAudioDeviceModulePointer();

   void release();

   void setSpeakerMute(boolean var1);

   void setMicrophoneMute(boolean var1);

   default boolean setNoiseSuppressorEnabled(boolean enabled) {
      return false;
   }

   default boolean setPreferredMicrophoneFieldDimension(float dimension) {
      return false;
   }
}
