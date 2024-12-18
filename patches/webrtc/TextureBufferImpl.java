package org.webrtc;

import android.graphics.Matrix;
import android.os.Handler;
import androidx.annotation.Nullable;

public class TextureBufferImpl implements VideoFrame.TextureBuffer {
   private final int unscaledWidth;
   private final int unscaledHeight;
   private final int width;
   private final int height;
   private final VideoFrame.TextureBuffer.Type type;
   private final int id;
   private final Matrix transformMatrix;
   private final Handler toI420Handler;
   private final YuvConverter yuvConverter;
   private final RefCountDelegate refCountDelegate;
   private final TextureBufferImpl.RefCountMonitor refCountMonitor;

   public TextureBufferImpl(int width, int height, VideoFrame.TextureBuffer.Type type, int id, Matrix transformMatrix, Handler toI420Handler, YuvConverter yuvConverter, @Nullable final Runnable releaseCallback) {
      this(width, height, width, height, type, id, transformMatrix, toI420Handler, yuvConverter, new TextureBufferImpl.RefCountMonitor() {
         public void onRetain(TextureBufferImpl textureBuffer) {
         }

         public void onRelease(TextureBufferImpl textureBuffer) {
         }

         public void onDestroy(TextureBufferImpl textureBuffer) {
            if (releaseCallback != null) {
               releaseCallback.run();
            }

         }
      });
   }

   TextureBufferImpl(int width, int height, VideoFrame.TextureBuffer.Type type, int id, Matrix transformMatrix, Handler toI420Handler, YuvConverter yuvConverter, TextureBufferImpl.RefCountMonitor refCountMonitor) {
      this(width, height, width, height, type, id, transformMatrix, toI420Handler, yuvConverter, refCountMonitor);
   }

   private TextureBufferImpl(int unscaledWidth, int unscaledHeight, int width, int height, VideoFrame.TextureBuffer.Type type, int id, Matrix transformMatrix, Handler toI420Handler, YuvConverter yuvConverter, TextureBufferImpl.RefCountMonitor refCountMonitor) {
      this.unscaledWidth = unscaledWidth;
      this.unscaledHeight = unscaledHeight;
      this.width = width;
      this.height = height;
      this.type = type;
      this.id = id;
      this.transformMatrix = transformMatrix;
      this.toI420Handler = toI420Handler;
      this.yuvConverter = yuvConverter;
      this.refCountDelegate = new RefCountDelegate(() -> {
         refCountMonitor.onDestroy(this);
      });
      this.refCountMonitor = refCountMonitor;
   }

   public VideoFrame.TextureBuffer.Type getType() {
      return this.type;
   }

   public int getTextureId() {
      return this.id;
   }

   public Matrix getTransformMatrix() {
      return this.transformMatrix;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public VideoFrame.I420Buffer toI420() {
      return (VideoFrame.I420Buffer)ThreadUtils.invokeAtFrontUninterruptibly(this.toI420Handler, () -> {
         return this.yuvConverter.convert(this);
      });
   }

   public void retain() {
      this.refCountMonitor.onRetain(this);
      this.refCountDelegate.retain();
   }

   public void release() {
      this.refCountMonitor.onRelease(this);
      this.refCountDelegate.release();
   }

   public VideoFrame.Buffer cropAndScale(int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
      Matrix cropAndScaleMatrix = new Matrix();
      int cropYFromBottom = this.height - (cropY + cropHeight);
      cropAndScaleMatrix.preTranslate((float)cropX / (float)this.width, (float)cropYFromBottom / (float)this.height);
      cropAndScaleMatrix.preScale((float)cropWidth / (float)this.width, (float)cropHeight / (float)this.height);
      return this.applyTransformMatrix(cropAndScaleMatrix, Math.round((float)(this.unscaledWidth * cropWidth) / (float)this.width), Math.round((float)(this.unscaledHeight * cropHeight) / (float)this.height), scaleWidth, scaleHeight);
   }

   public int getUnscaledWidth() {
      return this.unscaledWidth;
   }

   public int getUnscaledHeight() {
      return this.unscaledHeight;
   }

   public Handler getToI420Handler() {
      return this.toI420Handler;
   }

   public YuvConverter getYuvConverter() {
      return this.yuvConverter;
   }

   public TextureBufferImpl applyTransformMatrix(Matrix transformMatrix, int newWidth, int newHeight) {
      return this.applyTransformMatrix(transformMatrix, newWidth, newHeight, newWidth, newHeight);
   }

   private TextureBufferImpl applyTransformMatrix(Matrix transformMatrix, int unscaledWidth, int unscaledHeight, int scaledWidth, int scaledHeight) {
      Matrix newMatrix = new Matrix(this.transformMatrix);
      newMatrix.preConcat(transformMatrix);
      this.retain();
      return new TextureBufferImpl(unscaledWidth, unscaledHeight, scaledWidth, scaledHeight, this.type, this.id, newMatrix, this.toI420Handler, this.yuvConverter, new TextureBufferImpl.RefCountMonitor() {
         public void onRetain(TextureBufferImpl textureBuffer) {
            TextureBufferImpl.this.refCountMonitor.onRetain(TextureBufferImpl.this);
         }

         public void onRelease(TextureBufferImpl textureBuffer) {
            TextureBufferImpl.this.refCountMonitor.onRelease(TextureBufferImpl.this);
         }

         public void onDestroy(TextureBufferImpl textureBuffer) {
            TextureBufferImpl.this.release();
         }
      });
   }

   interface RefCountMonitor {
      void onRetain(TextureBufferImpl var1);

      void onRelease(TextureBufferImpl var1);

      void onDestroy(TextureBufferImpl var1);
   }
}
