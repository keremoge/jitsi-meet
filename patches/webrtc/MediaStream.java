package org.webrtc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MediaStream {
   private static final String TAG = "MediaStream";
   public final List<AudioTrack> audioTracks = new ArrayList();
   public final List<VideoTrack> videoTracks = new ArrayList();
   public final List<VideoTrack> preservedVideoTracks = new ArrayList();
   private long nativeStream;

   @CalledByNative
   public MediaStream(long nativeStream) {
      this.nativeStream = nativeStream;
   }

   public boolean addTrack(AudioTrack track) {
      this.checkMediaStreamExists();
      if (nativeAddAudioTrackToNativeStream(this.nativeStream, track.getNativeAudioTrack())) {
         this.audioTracks.add(track);
         return true;
      } else {
         return false;
      }
   }

   public boolean addTrack(VideoTrack track) {
      this.checkMediaStreamExists();
      if (nativeAddVideoTrackToNativeStream(this.nativeStream, track.getNativeVideoTrack())) {
         this.videoTracks.add(track);
         return true;
      } else {
         return false;
      }
   }

   public boolean addPreservedTrack(VideoTrack track) {
      this.checkMediaStreamExists();
      if (nativeAddVideoTrackToNativeStream(this.nativeStream, track.getNativeVideoTrack())) {
         this.preservedVideoTracks.add(track);
         return true;
      } else {
         return false;
      }
   }

   public boolean removeTrack(AudioTrack track) {
      this.checkMediaStreamExists();
      this.audioTracks.remove(track);
      return nativeRemoveAudioTrack(this.nativeStream, track.getNativeAudioTrack());
   }

   public boolean removeTrack(VideoTrack track) {
      this.checkMediaStreamExists();
      this.videoTracks.remove(track);
      this.preservedVideoTracks.remove(track);
      return nativeRemoveVideoTrack(this.nativeStream, track.getNativeVideoTrack());
   }

   @CalledByNative
   public void dispose() {
      this.checkMediaStreamExists();

      while(!this.audioTracks.isEmpty()) {
         AudioTrack track = (AudioTrack)this.audioTracks.get(0);
         this.removeTrack(track);
         track.dispose();
      }

      while(!this.videoTracks.isEmpty()) {
         VideoTrack track = (VideoTrack)this.videoTracks.get(0);
         this.removeTrack(track);
         track.dispose();
      }

      while(!this.preservedVideoTracks.isEmpty()) {
         this.removeTrack((VideoTrack)this.preservedVideoTracks.get(0));
      }

      JniCommon.nativeReleaseRef(this.nativeStream);
      this.nativeStream = 0L;
   }

   public String getId() {
      this.checkMediaStreamExists();
      return nativeGetId(this.nativeStream);
   }

   public String toString() {
      String var10000 = this.getId();
      return "[" + var10000 + ":A=" + this.audioTracks.size() + ":V=" + this.videoTracks.size() + "]";
   }

   @CalledByNative
   void addNativeAudioTrack(long nativeTrack) {
      this.audioTracks.add(new AudioTrack(nativeTrack));
   }

   @CalledByNative
   void addNativeVideoTrack(long nativeTrack) {
      this.videoTracks.add(new VideoTrack(nativeTrack));
   }

   @CalledByNative
   void removeAudioTrack(long nativeTrack) {
      removeMediaStreamTrack(this.audioTracks, nativeTrack);
   }

   @CalledByNative
   void removeVideoTrack(long nativeTrack) {
      removeMediaStreamTrack(this.videoTracks, nativeTrack);
   }

   long getNativeMediaStream() {
      this.checkMediaStreamExists();
      return this.nativeStream;
   }

   private void checkMediaStreamExists() {
      if (this.nativeStream == 0L) {
         throw new IllegalStateException("MediaStream has been disposed.");
      }
   }

   private static void removeMediaStreamTrack(List<? extends MediaStreamTrack> tracks, long nativeTrack) {
      Iterator it = tracks.iterator();

      MediaStreamTrack track;
      do {
         if (!it.hasNext()) {
            Logging.e("MediaStream", "Couldn't not find track");
            return;
         }

         track = (MediaStreamTrack)it.next();
      } while(track.getNativeMediaStreamTrack() != nativeTrack);

      track.dispose();
      it.remove();
   }

   private static native boolean nativeAddAudioTrackToNativeStream(long var0, long var2);

   private static native boolean nativeAddVideoTrackToNativeStream(long var0, long var2);

   private static native boolean nativeRemoveAudioTrack(long var0, long var2);

   private static native boolean nativeRemoveVideoTrack(long var0, long var2);

   private static native String nativeGetId(long var0);
}
