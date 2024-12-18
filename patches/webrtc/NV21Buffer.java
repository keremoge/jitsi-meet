package org.webrtc;

import androidx.annotation.Nullable;
import java.nio.ByteBuffer;

public class NV21Buffer implements VideoFrame.Buffer {
   private final byte[] data;
   private final int width;
   private final int height;
   private final RefCountDelegate refCountDelegate;

   public NV21Buffer(byte[] data, int width, int height, @Nullable Runnable releaseCallback) {
      this.data = data;
      this.width = width;
      this.height = height;
      this.refCountDelegate = new RefCountDelegate(releaseCallback);
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public VideoFrame.I420Buffer toI420() {
      return (VideoFrame.I420Buffer)this.cropAndScale(0, 0, this.width, this.height, this.width, this.height);
   }

   public void retain() {
      this.refCountDelegate.retain();
   }

   public void release() {
      this.refCountDelegate.release();
   }

   public VideoFrame.Buffer cropAndScale(int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
      JavaI420Buffer newBuffer = JavaI420Buffer.allocate(scaleWidth, scaleHeight);
      nativeCropAndScale(cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight, this.data, this.width, this.height, newBuffer.getDataY(), newBuffer.getStrideY(), newBuffer.getDataU(), newBuffer.getStrideU(), newBuffer.getDataV(), newBuffer.getStrideV());
      return newBuffer;
   }

   private static native void nativeCropAndScale(int var0, int var1, int var2, int var3, int var4, int var5, byte[] var6, int var7, int var8, ByteBuffer var9, int var10, ByteBuffer var11, int var12, ByteBuffer var13, int var14);
}
