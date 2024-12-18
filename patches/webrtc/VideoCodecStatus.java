package org.webrtc;

public enum VideoCodecStatus {
   TARGET_BITRATE_OVERSHOOT(5),
   REQUEST_SLI(2),
   NO_OUTPUT(1),
   OK(0),
   ERROR(-1),
   LEVEL_EXCEEDED(-2),
   MEMORY(-3),
   ERR_PARAMETER(-4),
   ERR_SIZE(-5),
   TIMEOUT(-6),
   UNINITIALIZED(-7),
   ERR_REQUEST_SLI(-12),
   FALLBACK_SOFTWARE(-13);

   private final int number;

   private VideoCodecStatus(int number) {
      this.number = number;
   }

   @CalledByNative
   public int getNumber() {
      return this.number;
   }

   // $FF: synthetic method
   private static VideoCodecStatus[] $values() {
      return new VideoCodecStatus[]{TARGET_BITRATE_OVERSHOOT, REQUEST_SLI, NO_OUTPUT, OK, ERROR, LEVEL_EXCEEDED, MEMORY, ERR_PARAMETER, ERR_SIZE, TIMEOUT, UNINITIALIZED, ERR_REQUEST_SLI, FALLBACK_SOFTWARE};
   }
}
