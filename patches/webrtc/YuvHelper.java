package org.webrtc;

import java.nio.ByteBuffer;

public class YuvHelper {
   public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int dstWidth, int dstHeight, int dstStrideY, int dstSliceHeightY, int dstStrideU, int dstSliceHeightU) {
      int chromaWidth = (dstWidth + 1) / 2;
      int chromaHeight = (dstHeight + 1) / 2;
      int dstStartY = false;
      int dstEndY = 0 + dstStrideY * dstHeight;
      int dstStartU = 0 + dstStrideY * dstSliceHeightY;
      int dstEndU = dstStartU + dstStrideU * chromaHeight;
      int dstStartV = dstStartU + dstStrideU * dstSliceHeightU;
      int dstEndV = dstStartV + dstStrideU * (chromaHeight - 1) + chromaWidth;
      if (dst.capacity() < dstEndV) {
         throw new IllegalArgumentException("Expected destination buffer capacity to be at least " + dstEndV + " was " + dst.capacity());
      } else {
         dst.limit(dstEndY);
         dst.position(0);
         ByteBuffer dstY = dst.slice();
         dst.limit(dstEndU);
         dst.position(dstStartU);
         ByteBuffer dstU = dst.slice();
         dst.limit(dstEndV);
         dst.position(dstStartV);
         ByteBuffer dstV = dst.slice();
         I420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstU, dstStrideU, dstV, dstStrideU, dstWidth, dstHeight);
      }
   }

   public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int dstWidth, int dstHeight) {
      I420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dst, dstWidth, dstHeight, dstWidth, dstHeight, (dstWidth + 1) / 2, (dstHeight + 1) / 2);
   }

   public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int dstWidth, int dstHeight, int dstStride, int dstSliceHeight) {
      I420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dst, dstWidth, dstHeight, dstStride, dstSliceHeight, (dstStride + 1) / 2, (dstSliceHeight + 1) / 2);
   }

   public static void I420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int dstWidth, int dstHeight, int dstStrideY, int dstSliceHeightY) {
      int chromaHeight = (dstHeight + 1) / 2;
      int chromaWidth = (dstWidth + 1) / 2;
      int dstStartY = false;
      int dstEndY = 0 + dstStrideY * dstHeight;
      int dstStartUV = 0 + dstStrideY * dstSliceHeightY;
      int dstEndUV = dstStartUV + chromaWidth * chromaHeight * 2;
      if (dst.capacity() < dstEndUV) {
         throw new IllegalArgumentException("Expected destination buffer capacity to be at least " + dstEndUV + " was " + dst.capacity());
      } else {
         dst.limit(dstEndY);
         dst.position(0);
         ByteBuffer dstY = dst.slice();
         dst.limit(dstEndUV);
         dst.position(dstStartUV);
         ByteBuffer dstUV = dst.slice();
         I420ToNV12(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstUV, chromaWidth * 2, dstWidth, dstHeight);
      }
   }

   public static void I420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int dstWidth, int dstHeight) {
      I420ToNV12(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dst, dstWidth, dstHeight, dstWidth, dstHeight);
   }

   public static void I420Rotate(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int srcWidth, int srcHeight, int rotationMode) {
      checkNotNull(srcY, "srcY");
      checkNotNull(srcU, "srcU");
      checkNotNull(srcV, "srcV");
      checkNotNull(dst, "dst");
      int dstWidth = rotationMode % 180 == 0 ? srcWidth : srcHeight;
      int dstHeight = rotationMode % 180 == 0 ? srcHeight : srcWidth;
      int dstChromaHeight = (dstHeight + 1) / 2;
      int dstChromaWidth = (dstWidth + 1) / 2;
      int minSize = dstWidth * dstHeight + dstChromaWidth * dstChromaHeight * 2;
      if (dst.capacity() < minSize) {
         throw new IllegalArgumentException("Expected destination buffer capacity to be at least " + minSize + " was " + dst.capacity());
      } else {
         int startY = false;
         int startU = dstHeight * dstWidth;
         int startV = startU + dstChromaHeight * dstChromaWidth;
         dst.position(0);
         ByteBuffer dstY = dst.slice();
         dst.position(startU);
         ByteBuffer dstU = dst.slice();
         dst.position(startV);
         ByteBuffer dstV = dst.slice();
         nativeI420Rotate(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstWidth, dstU, dstChromaWidth, dstV, dstChromaWidth, srcWidth, srcHeight, rotationMode);
      }
   }

   public static void copyPlane(ByteBuffer src, int srcStride, ByteBuffer dst, int dstStride, int width, int height) {
      nativeCopyPlane((ByteBuffer)checkNotNull(src, "src"), srcStride, (ByteBuffer)checkNotNull(dst, "dst"), dstStride, width, height);
   }

   public static void ABGRToI420(ByteBuffer src, int srcStride, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int width, int height) {
      nativeABGRToI420((ByteBuffer)checkNotNull(src, "src"), srcStride, (ByteBuffer)checkNotNull(dstY, "dstY"), dstStrideY, (ByteBuffer)checkNotNull(dstU, "dstU"), dstStrideU, (ByteBuffer)checkNotNull(dstV, "dstV"), dstStrideV, width, height);
   }

   public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int width, int height) {
      checkNotNull(srcY, "srcY");
      checkNotNull(srcU, "srcU");
      checkNotNull(srcV, "srcV");
      checkNotNull(dstY, "dstY");
      checkNotNull(dstU, "dstU");
      checkNotNull(dstV, "dstV");
      if (width > 0 && height > 0) {
         nativeI420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstU, dstStrideU, dstV, dstStrideV, width, height);
      } else {
         throw new IllegalArgumentException("I420Copy: width and height should not be negative");
      }
   }

   public static void I420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstUV, int dstStrideUV, int width, int height) {
      checkNotNull(srcY, "srcY");
      checkNotNull(srcU, "srcU");
      checkNotNull(srcV, "srcV");
      checkNotNull(dstY, "dstY");
      checkNotNull(dstUV, "dstUV");
      if (width > 0 && height > 0) {
         nativeI420ToNV12(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstUV, dstStrideUV, width, height);
      } else {
         throw new IllegalArgumentException("I420ToNV12: width and height should not be negative");
      }
   }

   public static void I420Rotate(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int srcWidth, int srcHeight, int rotationMode) {
      checkNotNull(srcY, "srcY");
      checkNotNull(srcU, "srcU");
      checkNotNull(srcV, "srcV");
      checkNotNull(dstY, "dstY");
      checkNotNull(dstU, "dstU");
      checkNotNull(dstV, "dstV");
      nativeI420Rotate(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstU, dstStrideU, dstV, dstStrideV, srcWidth, srcHeight, rotationMode);
   }

   private static <T> T checkNotNull(T obj, String description) {
      if (obj == null) {
         throw new NullPointerException(description + " should not be null");
      } else {
         return obj;
      }
   }

   private static native void nativeCopyPlane(ByteBuffer var0, int var1, ByteBuffer var2, int var3, int var4, int var5);

   private static native void nativeI420Copy(ByteBuffer var0, int var1, ByteBuffer var2, int var3, ByteBuffer var4, int var5, ByteBuffer var6, int var7, ByteBuffer var8, int var9, ByteBuffer var10, int var11, int var12, int var13);

   private static native void nativeI420ToNV12(ByteBuffer var0, int var1, ByteBuffer var2, int var3, ByteBuffer var4, int var5, ByteBuffer var6, int var7, ByteBuffer var8, int var9, int var10, int var11);

   private static native void nativeI420Rotate(ByteBuffer var0, int var1, ByteBuffer var2, int var3, ByteBuffer var4, int var5, ByteBuffer var6, int var7, ByteBuffer var8, int var9, ByteBuffer var10, int var11, int var12, int var13, int var14);

   private static native void nativeABGRToI420(ByteBuffer var0, int var1, ByteBuffer var2, int var3, ByteBuffer var4, int var5, ByteBuffer var6, int var7, int var8, int var9);
}
