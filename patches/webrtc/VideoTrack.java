package org.webrtc;

import java.util.IdentityHashMap;
import java.util.Iterator;

public class VideoTrack extends MediaStreamTrack {
   private final IdentityHashMap<VideoSink, Long> sinks = new IdentityHashMap();

   public VideoTrack(long nativeTrack) {
      super(nativeTrack);
   }

   public void addSink(VideoSink sink) {
      if (sink == null) {
         throw new IllegalArgumentException("The VideoSink is not allowed to be null");
      } else {
         if (!this.sinks.containsKey(sink)) {
            long nativeSink = nativeWrapSink(sink);
            this.sinks.put(sink, nativeSink);
            nativeAddSink(this.getNativeMediaStreamTrack(), nativeSink);
         }

      }
   }

   public void removeSink(VideoSink sink) {
      Long nativeSink = (Long)this.sinks.remove(sink);
      if (nativeSink != null) {
         nativeRemoveSink(this.getNativeMediaStreamTrack(), nativeSink);
         nativeFreeSink(nativeSink);
      }

   }

   public void dispose() {
      Iterator var1 = this.sinks.values().iterator();

      while(var1.hasNext()) {
         long nativeSink = (Long)var1.next();
         nativeRemoveSink(this.getNativeMediaStreamTrack(), nativeSink);
         nativeFreeSink(nativeSink);
      }

      this.sinks.clear();
      super.dispose();
   }

   public long getNativeVideoTrack() {
      return this.getNativeMediaStreamTrack();
   }

   private static native void nativeAddSink(long var0, long var2);

   private static native void nativeRemoveSink(long var0, long var2);

   private static native long nativeWrapSink(VideoSink var0);

   private static native void nativeFreeSink(long var0);
}
