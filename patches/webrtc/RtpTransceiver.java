package org.webrtc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RtpTransceiver {
   private long nativeRtpTransceiver;
   private RtpSender cachedSender;
   private RtpReceiver cachedReceiver;

   @CalledByNative
   protected RtpTransceiver(long nativeRtpTransceiver) {
      this.nativeRtpTransceiver = nativeRtpTransceiver;
      this.cachedSender = nativeGetSender(nativeRtpTransceiver);
      this.cachedReceiver = nativeGetReceiver(nativeRtpTransceiver);
   }

   public MediaStreamTrack.MediaType getMediaType() {
      this.checkRtpTransceiverExists();
      return nativeGetMediaType(this.nativeRtpTransceiver);
   }

   public String getMid() {
      this.checkRtpTransceiverExists();
      return nativeGetMid(this.nativeRtpTransceiver);
   }

   public RtpSender getSender() {
      return this.cachedSender;
   }

   public RtpReceiver getReceiver() {
      return this.cachedReceiver;
   }

   public boolean isStopped() {
      this.checkRtpTransceiverExists();
      return nativeStopped(this.nativeRtpTransceiver);
   }

   public RtpTransceiver.RtpTransceiverDirection getDirection() {
      this.checkRtpTransceiverExists();
      return nativeDirection(this.nativeRtpTransceiver);
   }

   public RtpTransceiver.RtpTransceiverDirection getCurrentDirection() {
      this.checkRtpTransceiverExists();
      return nativeCurrentDirection(this.nativeRtpTransceiver);
   }

   public boolean setDirection(RtpTransceiver.RtpTransceiverDirection rtpTransceiverDirection) {
      this.checkRtpTransceiverExists();
      return nativeSetDirection(this.nativeRtpTransceiver, rtpTransceiverDirection);
   }

   public void stop() {
      this.checkRtpTransceiverExists();
      nativeStopInternal(this.nativeRtpTransceiver);
   }

   public void setCodecPreferences(List<RtpCapabilities.CodecCapability> codecs) {
      this.checkRtpTransceiverExists();
      nativeSetCodecPreferences(this.nativeRtpTransceiver, codecs);
   }

   public void stopInternal() {
      this.checkRtpTransceiverExists();
      nativeStopInternal(this.nativeRtpTransceiver);
   }

   public void stopStandard() {
      this.checkRtpTransceiverExists();
      nativeStopStandard(this.nativeRtpTransceiver);
   }

   @CalledByNative
   public void dispose() {
      this.checkRtpTransceiverExists();
      this.cachedSender.dispose();
      this.cachedReceiver.dispose();
      JniCommon.nativeReleaseRef(this.nativeRtpTransceiver);
      this.nativeRtpTransceiver = 0L;
   }

   private void checkRtpTransceiverExists() {
      if (this.nativeRtpTransceiver == 0L) {
         throw new IllegalStateException("RtpTransceiver has been disposed.");
      }
   }

   private static native MediaStreamTrack.MediaType nativeGetMediaType(long var0);

   private static native String nativeGetMid(long var0);

   private static native RtpSender nativeGetSender(long var0);

   private static native RtpReceiver nativeGetReceiver(long var0);

   private static native boolean nativeStopped(long var0);

   private static native RtpTransceiver.RtpTransceiverDirection nativeDirection(long var0);

   private static native RtpTransceiver.RtpTransceiverDirection nativeCurrentDirection(long var0);

   private static native void nativeStopInternal(long var0);

   private static native void nativeStopStandard(long var0);

   private static native boolean nativeSetDirection(long var0, RtpTransceiver.RtpTransceiverDirection var2);

   private static native void nativeSetCodecPreferences(long var0, List<RtpCapabilities.CodecCapability> var2);

   public static enum RtpTransceiverDirection {
      SEND_RECV(0),
      SEND_ONLY(1),
      RECV_ONLY(2),
      INACTIVE(3),
      STOPPED(4);

      private final int nativeIndex;

      private RtpTransceiverDirection(int nativeIndex) {
         this.nativeIndex = nativeIndex;
      }

      @CalledByNative("RtpTransceiverDirection")
      int getNativeIndex() {
         return this.nativeIndex;
      }

      @CalledByNative("RtpTransceiverDirection")
      static RtpTransceiver.RtpTransceiverDirection fromNativeIndex(int nativeIndex) {
         RtpTransceiver.RtpTransceiverDirection[] var1 = values();
         int var2 = var1.length;

         for(int var3 = 0; var3 < var2; ++var3) {
            RtpTransceiver.RtpTransceiverDirection type = var1[var3];
            if (type.getNativeIndex() == nativeIndex) {
               return type;
            }
         }

         throw new IllegalArgumentException("Uknown native RtpTransceiverDirection type" + nativeIndex);
      }

      // $FF: synthetic method
      private static RtpTransceiver.RtpTransceiverDirection[] $values() {
         return new RtpTransceiver.RtpTransceiverDirection[]{SEND_RECV, SEND_ONLY, RECV_ONLY, INACTIVE, STOPPED};
      }
   }

   public static final class RtpTransceiverInit {
      private final RtpTransceiver.RtpTransceiverDirection direction;
      private final List<String> streamIds;
      private final List<RtpParameters.Encoding> sendEncodings;

      public RtpTransceiverInit() {
         this(RtpTransceiver.RtpTransceiverDirection.SEND_RECV);
      }

      public RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection direction) {
         this(direction, Collections.emptyList(), Collections.emptyList());
      }

      public RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection direction, List<String> streamIds) {
         this(direction, streamIds, Collections.emptyList());
      }

      public RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection direction, List<String> streamIds, List<RtpParameters.Encoding> sendEncodings) {
         this.direction = direction;
         this.streamIds = new ArrayList(streamIds);
         this.sendEncodings = new ArrayList(sendEncodings);
      }

      @CalledByNative("RtpTransceiverInit")
      int getDirectionNativeIndex() {
         return this.direction.getNativeIndex();
      }

      @CalledByNative("RtpTransceiverInit")
      List<String> getStreamIds() {
         return new ArrayList(this.streamIds);
      }

      @CalledByNative("RtpTransceiverInit")
      List<RtpParameters.Encoding> getSendEncodings() {
         return new ArrayList(this.sendEncodings);
      }
   }
}
