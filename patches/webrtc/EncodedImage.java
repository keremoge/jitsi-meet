package org.webrtc;

import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class EncodedImage implements RefCounted {
   private final RefCountDelegate refCountDelegate;
   public final ByteBuffer buffer;
   public final int encodedWidth;
   public final int encodedHeight;
   public final long captureTimeMs;
   public final long captureTimeNs;
   public final EncodedImage.FrameType frameType;
   public final int rotation;
   @Nullable
   public final Integer qp;

   public void retain() {
      this.refCountDelegate.retain();
   }

   public void release() {
      this.refCountDelegate.release();
   }

   @CalledByNative
   private EncodedImage(ByteBuffer buffer, @Nullable Runnable releaseCallback, int encodedWidth, int encodedHeight, long captureTimeNs, EncodedImage.FrameType frameType, int rotation, @Nullable Integer qp) {
      this.buffer = buffer;
      this.encodedWidth = encodedWidth;
      this.encodedHeight = encodedHeight;
      this.captureTimeMs = TimeUnit.NANOSECONDS.toMillis(captureTimeNs);
      this.captureTimeNs = captureTimeNs;
      this.frameType = frameType;
      this.rotation = rotation;
      this.qp = qp;
      this.refCountDelegate = new RefCountDelegate(releaseCallback);
   }

   @CalledByNative
   private ByteBuffer getBuffer() {
      return this.buffer;
   }

   @CalledByNative
   private int getEncodedWidth() {
      return this.encodedWidth;
   }

   @CalledByNative
   private int getEncodedHeight() {
      return this.encodedHeight;
   }

   @CalledByNative
   private long getCaptureTimeNs() {
      return this.captureTimeNs;
   }

   @CalledByNative
   private int getFrameType() {
      return this.frameType.getNative();
   }

   @CalledByNative
   private int getRotation() {
      return this.rotation;
   }

   @CalledByNative
   @Nullable
   private Integer getQp() {
      return this.qp;
   }

   public static EncodedImage.Builder builder() {
      return new EncodedImage.Builder();
   }

   public static enum FrameType {
      EmptyFrame(0),
      VideoFrameKey(3),
      VideoFrameDelta(4);

      private final int nativeIndex;

      private FrameType(int nativeIndex) {
         this.nativeIndex = nativeIndex;
      }

      public int getNative() {
         return this.nativeIndex;
      }

      @CalledByNative("FrameType")
      static EncodedImage.FrameType fromNativeIndex(int nativeIndex) {
         EncodedImage.FrameType[] var1 = values();
         int var2 = var1.length;

         for(int var3 = 0; var3 < var2; ++var3) {
            EncodedImage.FrameType type = var1[var3];
            if (type.getNative() == nativeIndex) {
               return type;
            }
         }

         throw new IllegalArgumentException("Unknown native frame type: " + nativeIndex);
      }

      // $FF: synthetic method
      private static EncodedImage.FrameType[] $values() {
         return new EncodedImage.FrameType[]{EmptyFrame, VideoFrameKey, VideoFrameDelta};
      }
   }

   public static class Builder {
      private ByteBuffer buffer;
      @Nullable
      private Runnable releaseCallback;
      private int encodedWidth;
      private int encodedHeight;
      private long captureTimeNs;
      private EncodedImage.FrameType frameType;
      private int rotation;
      @Nullable
      private Integer qp;

      private Builder() {
      }

      public EncodedImage.Builder setBuffer(ByteBuffer buffer, @Nullable Runnable releaseCallback) {
         this.buffer = buffer;
         this.releaseCallback = releaseCallback;
         return this;
      }

      public EncodedImage.Builder setEncodedWidth(int encodedWidth) {
         this.encodedWidth = encodedWidth;
         return this;
      }

      public EncodedImage.Builder setEncodedHeight(int encodedHeight) {
         this.encodedHeight = encodedHeight;
         return this;
      }

      /** @deprecated */
      @Deprecated
      public EncodedImage.Builder setCaptureTimeMs(long captureTimeMs) {
         this.captureTimeNs = TimeUnit.MILLISECONDS.toNanos(captureTimeMs);
         return this;
      }

      public EncodedImage.Builder setCaptureTimeNs(long captureTimeNs) {
         this.captureTimeNs = captureTimeNs;
         return this;
      }

      public EncodedImage.Builder setFrameType(EncodedImage.FrameType frameType) {
         this.frameType = frameType;
         return this;
      }

      public EncodedImage.Builder setRotation(int rotation) {
         this.rotation = rotation;
         return this;
      }

      public EncodedImage.Builder setQp(@Nullable Integer qp) {
         this.qp = qp;
         return this;
      }

      public EncodedImage createEncodedImage() {
         return new EncodedImage(this.buffer, this.releaseCallback, this.encodedWidth, this.encodedHeight, this.captureTimeNs, this.frameType, this.rotation, this.qp);
      }
   }
}
