package org.webrtc;

import androidx.annotation.Nullable;

public class MediaStreamTrack {
   public static final String AUDIO_TRACK_KIND = "audio";
   public static final String VIDEO_TRACK_KIND = "video";
   private long nativeTrack;

   @Nullable
   static MediaStreamTrack createMediaStreamTrack(long nativeTrack) {
      if (nativeTrack == 0L) {
         return null;
      } else {
         String trackKind = nativeGetKind(nativeTrack);
         if (trackKind.equals("audio")) {
            return new AudioTrack(nativeTrack);
         } else {
            return trackKind.equals("video") ? new VideoTrack(nativeTrack) : null;
         }
      }
   }

   public MediaStreamTrack(long nativeTrack) {
      if (nativeTrack == 0L) {
         throw new IllegalArgumentException("nativeTrack may not be null");
      } else {
         this.nativeTrack = nativeTrack;
      }
   }

   public String id() {
      this.checkMediaStreamTrackExists();
      return nativeGetId(this.nativeTrack);
   }

   public String kind() {
      this.checkMediaStreamTrackExists();
      return nativeGetKind(this.nativeTrack);
   }

   public boolean enabled() {
      this.checkMediaStreamTrackExists();
      return nativeGetEnabled(this.nativeTrack);
   }

   public boolean setEnabled(boolean enable) {
      this.checkMediaStreamTrackExists();
      return nativeSetEnabled(this.nativeTrack, enable);
   }

   public MediaStreamTrack.State state() {
      this.checkMediaStreamTrackExists();
      return nativeGetState(this.nativeTrack);
   }

   public void dispose() {
      this.checkMediaStreamTrackExists();
      JniCommon.nativeReleaseRef(this.nativeTrack);
      this.nativeTrack = 0L;
   }

   long getNativeMediaStreamTrack() {
      this.checkMediaStreamTrackExists();
      return this.nativeTrack;
   }

   private void checkMediaStreamTrackExists() {
      if (this.nativeTrack == 0L) {
         throw new IllegalStateException("MediaStreamTrack has been disposed.");
      }
   }

   private static native String nativeGetId(long var0);

   private static native String nativeGetKind(long var0);

   private static native boolean nativeGetEnabled(long var0);

   private static native boolean nativeSetEnabled(long var0, boolean var2);

   private static native MediaStreamTrack.State nativeGetState(long var0);

   public static enum State {
      LIVE,
      ENDED;

      @CalledByNative("State")
      static MediaStreamTrack.State fromNativeIndex(int nativeIndex) {
         return values()[nativeIndex];
      }

      // $FF: synthetic method
      private static MediaStreamTrack.State[] $values() {
         return new MediaStreamTrack.State[]{LIVE, ENDED};
      }
   }

   public static enum MediaType {
      MEDIA_TYPE_AUDIO(0),
      MEDIA_TYPE_VIDEO(1);

      private final int nativeIndex;

      private MediaType(int nativeIndex) {
         this.nativeIndex = nativeIndex;
      }

      @CalledByNative("MediaType")
      int getNative() {
         return this.nativeIndex;
      }

      @CalledByNative("MediaType")
      static MediaStreamTrack.MediaType fromNativeIndex(int nativeIndex) {
         MediaStreamTrack.MediaType[] var1 = values();
         int var2 = var1.length;

         for(int var3 = 0; var3 < var2; ++var3) {
            MediaStreamTrack.MediaType type = var1[var3];
            if (type.getNative() == nativeIndex) {
               return type;
            }
         }

         throw new IllegalArgumentException("Unknown native media type: " + nativeIndex);
      }

      // $FF: synthetic method
      private static MediaStreamTrack.MediaType[] $values() {
         return new MediaStreamTrack.MediaType[]{MEDIA_TYPE_AUDIO, MEDIA_TYPE_VIDEO};
      }
   }
}
