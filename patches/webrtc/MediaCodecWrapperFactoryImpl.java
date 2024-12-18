package org.webrtc;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.os.Bundle;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;

class MediaCodecWrapperFactoryImpl implements MediaCodecWrapperFactory {
   public MediaCodecWrapper createByCodecName(String name) throws IOException {
      return new MediaCodecWrapperFactoryImpl.MediaCodecWrapperImpl(MediaCodec.createByCodecName(name));
   }

   private static class MediaCodecWrapperImpl implements MediaCodecWrapper {
      private final MediaCodec mediaCodec;

      public MediaCodecWrapperImpl(MediaCodec mediaCodec) {
         this.mediaCodec = mediaCodec;
      }

      public void configure(MediaFormat format, Surface surface, MediaCrypto crypto, int flags) {
         this.mediaCodec.configure(format, surface, crypto, flags);
      }

      public void start() {
         this.mediaCodec.start();
      }

      public void flush() {
         this.mediaCodec.flush();
      }

      public void stop() {
         this.mediaCodec.stop();
      }

      public void release() {
         this.mediaCodec.release();
      }

      public int dequeueInputBuffer(long timeoutUs) {
         return this.mediaCodec.dequeueInputBuffer(timeoutUs);
      }

      public void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags) {
         this.mediaCodec.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
      }

      public int dequeueOutputBuffer(BufferInfo info, long timeoutUs) {
         return this.mediaCodec.dequeueOutputBuffer(info, timeoutUs);
      }

      public void releaseOutputBuffer(int index, boolean render) {
         this.mediaCodec.releaseOutputBuffer(index, render);
      }

      public MediaFormat getInputFormat() {
         return this.mediaCodec.getInputFormat();
      }

      public MediaFormat getOutputFormat() {
         return this.mediaCodec.getOutputFormat();
      }

      public MediaFormat getOutputFormat(int index) {
         return this.mediaCodec.getOutputFormat(index);
      }

      public ByteBuffer getInputBuffer(int index) {
         return this.mediaCodec.getInputBuffer(index);
      }

      public ByteBuffer getOutputBuffer(int index) {
         return this.mediaCodec.getOutputBuffer(index);
      }

      public Surface createInputSurface() {
         return this.mediaCodec.createInputSurface();
      }

      public void setParameters(Bundle params) {
         this.mediaCodec.setParameters(params);
      }

      public MediaCodecInfo getCodecInfo() {
         return this.mediaCodec.getCodecInfo();
      }
   }
}
