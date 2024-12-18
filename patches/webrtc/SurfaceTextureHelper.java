package org.webrtc;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.Nullable;
import java.util.concurrent.Callable;

public class SurfaceTextureHelper {
   private static final String TAG = "SurfaceTextureHelper";
   private final TextureBufferImpl.RefCountMonitor textureRefCountMonitor = new TextureBufferImpl.RefCountMonitor() {
      public void onRetain(TextureBufferImpl textureBuffer) {
         if (SurfaceTextureHelper.this.frameRefMonitor != null) {
            SurfaceTextureHelper.this.frameRefMonitor.onRetainBuffer(textureBuffer);
         }

      }

      public void onRelease(TextureBufferImpl textureBuffer) {
         if (SurfaceTextureHelper.this.frameRefMonitor != null) {
            SurfaceTextureHelper.this.frameRefMonitor.onReleaseBuffer(textureBuffer);
         }

      }

      public void onDestroy(TextureBufferImpl textureBuffer) {
         SurfaceTextureHelper.this.returnTextureFrame();
         if (SurfaceTextureHelper.this.frameRefMonitor != null) {
            SurfaceTextureHelper.this.frameRefMonitor.onDestroyBuffer(textureBuffer);
         }

      }
   };
   private final Handler handler;
   private final EglBase eglBase;
   private final SurfaceTexture surfaceTexture;
   private final int oesTextureId;
   private final YuvConverter yuvConverter;
   @Nullable
   private final TimestampAligner timestampAligner;
   private final SurfaceTextureHelper.FrameRefMonitor frameRefMonitor;
   @Nullable
   private VideoSink listener;
   private boolean hasPendingTexture;
   private volatile boolean isTextureInUse;
   private boolean isQuitting;
   private int frameRotation;
   private int textureWidth;
   private int textureHeight;
   @Nullable
   private VideoSink pendingListener;
   final Runnable setListenerRunnable = new Runnable() {
      public void run() {
         Logging.d("SurfaceTextureHelper", "Setting listener to " + SurfaceTextureHelper.this.pendingListener);
         SurfaceTextureHelper.this.listener = SurfaceTextureHelper.this.pendingListener;
         SurfaceTextureHelper.this.pendingListener = null;
         if (SurfaceTextureHelper.this.hasPendingTexture) {
            SurfaceTextureHelper.this.updateTexImage();
            SurfaceTextureHelper.this.hasPendingTexture = false;
         }

      }
   };

   public static SurfaceTextureHelper create(final String threadName, final EglBase.Context sharedContext, final boolean alignTimestamps, final YuvConverter yuvConverter, final SurfaceTextureHelper.FrameRefMonitor frameRefMonitor) {
      HandlerThread thread = new HandlerThread(threadName);
      thread.start();
      final Handler handler = new Handler(thread.getLooper());
      return (SurfaceTextureHelper)ThreadUtils.invokeAtFrontUninterruptibly(handler, new Callable<SurfaceTextureHelper>() {
         @Nullable
         public SurfaceTextureHelper call() {
            try {
               return new SurfaceTextureHelper(sharedContext, handler, alignTimestamps, yuvConverter, frameRefMonitor);
            } catch (RuntimeException var2) {
               Logging.e("SurfaceTextureHelper", threadName + " create failure", var2);
               return null;
            }
         }
      });
   }

   public static SurfaceTextureHelper create(String threadName, EglBase.Context sharedContext) {
      return create(threadName, sharedContext, false, new YuvConverter(), (SurfaceTextureHelper.FrameRefMonitor)null);
   }

   public static SurfaceTextureHelper create(String threadName, EglBase.Context sharedContext, boolean alignTimestamps) {
      return create(threadName, sharedContext, alignTimestamps, new YuvConverter(), (SurfaceTextureHelper.FrameRefMonitor)null);
   }

   public static SurfaceTextureHelper create(String threadName, EglBase.Context sharedContext, boolean alignTimestamps, YuvConverter yuvConverter) {
      return create(threadName, sharedContext, alignTimestamps, yuvConverter, (SurfaceTextureHelper.FrameRefMonitor)null);
   }

   private SurfaceTextureHelper(EglBase.Context sharedContext, Handler handler, boolean alignTimestamps, YuvConverter yuvConverter, SurfaceTextureHelper.FrameRefMonitor frameRefMonitor) {
      if (handler.getLooper().getThread() != Thread.currentThread()) {
         throw new IllegalStateException("SurfaceTextureHelper must be created on the handler thread");
      } else {
         this.handler = handler;
         this.timestampAligner = alignTimestamps ? new TimestampAligner() : null;
         this.yuvConverter = yuvConverter;
         this.frameRefMonitor = frameRefMonitor;
         this.eglBase = EglBase.create(sharedContext, EglBase.CONFIG_PIXEL_BUFFER);

         try {
            this.eglBase.createDummyPbufferSurface();
            this.eglBase.makeCurrent();
         } catch (RuntimeException var7) {
            this.eglBase.release();
            handler.getLooper().quit();
            throw var7;
         }

         this.oesTextureId = GlUtil.generateTexture(36197);
         this.surfaceTexture = new SurfaceTexture(this.oesTextureId);
         this.surfaceTexture.setOnFrameAvailableListener((st) -> {
            if (this.hasPendingTexture) {
               Logging.d("SurfaceTextureHelper", "A frame is already pending, dropping frame.");
            }

            this.hasPendingTexture = true;
            this.tryDeliverTextureFrame();
         }, handler);
      }
   }

   public void startListening(VideoSink listener) {
      if (this.listener == null && this.pendingListener == null) {
         this.pendingListener = listener;
         this.handler.post(this.setListenerRunnable);
      } else {
         throw new IllegalStateException("SurfaceTextureHelper listener has already been set.");
      }
   }

   public void stopListening() {
      Logging.d("SurfaceTextureHelper", "stopListening()");
      this.handler.removeCallbacks(this.setListenerRunnable);
      ThreadUtils.invokeAtFrontUninterruptibly(this.handler, () -> {
         this.listener = null;
         this.pendingListener = null;
      });
   }

   public void setTextureSize(int textureWidth, int textureHeight) {
      if (textureWidth <= 0) {
         throw new IllegalArgumentException("Texture width must be positive, but was " + textureWidth);
      } else if (textureHeight <= 0) {
         throw new IllegalArgumentException("Texture height must be positive, but was " + textureHeight);
      } else {
         this.surfaceTexture.setDefaultBufferSize(textureWidth, textureHeight);
         this.handler.post(() -> {
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.tryDeliverTextureFrame();
         });
      }
   }

   public void forceFrame() {
      this.handler.post(() -> {
         this.hasPendingTexture = true;
         this.tryDeliverTextureFrame();
      });
   }

   public void setFrameRotation(int rotation) {
      this.handler.post(() -> {
         this.frameRotation = rotation;
      });
   }

   public SurfaceTexture getSurfaceTexture() {
      return this.surfaceTexture;
   }

   public Handler getHandler() {
      return this.handler;
   }

   private void returnTextureFrame() {
      this.handler.post(() -> {
         this.isTextureInUse = false;
         if (this.isQuitting) {
            this.release();
         } else {
            this.tryDeliverTextureFrame();
         }

      });
   }

   public boolean isTextureInUse() {
      return this.isTextureInUse;
   }

   public void dispose() {
      Logging.d("SurfaceTextureHelper", "dispose()");
      ThreadUtils.invokeAtFrontUninterruptibly(this.handler, () -> {
         this.isQuitting = true;
         if (!this.isTextureInUse) {
            this.release();
         }

      });
   }

   /** @deprecated */
   @Deprecated
   public VideoFrame.I420Buffer textureToYuv(VideoFrame.TextureBuffer textureBuffer) {
      return textureBuffer.toI420();
   }

   private void updateTexImage() {
      synchronized(EglBase.lock) {
         this.surfaceTexture.updateTexImage();
      }
   }

   private void tryDeliverTextureFrame() {
      if (this.handler.getLooper().getThread() != Thread.currentThread()) {
         throw new IllegalStateException("Wrong thread.");
      } else if (!this.isQuitting && this.hasPendingTexture && !this.isTextureInUse && this.listener != null) {
         if (this.textureWidth != 0 && this.textureHeight != 0) {
            this.isTextureInUse = true;
            this.hasPendingTexture = false;
            this.updateTexImage();
            float[] transformMatrix = new float[16];
            this.surfaceTexture.getTransformMatrix(transformMatrix);
            long timestampNs = this.surfaceTexture.getTimestamp();
            if (this.timestampAligner != null) {
               timestampNs = this.timestampAligner.translateTimestamp(timestampNs);
            }

            VideoFrame.TextureBuffer buffer = new TextureBufferImpl(this.textureWidth, this.textureHeight, VideoFrame.TextureBuffer.Type.OES, this.oesTextureId, RendererCommon.convertMatrixToAndroidGraphicsMatrix(transformMatrix), this.handler, this.yuvConverter, this.textureRefCountMonitor);
            if (this.frameRefMonitor != null) {
               this.frameRefMonitor.onNewBuffer(buffer);
            }

            VideoFrame frame = new VideoFrame(buffer, this.frameRotation, timestampNs);
            this.listener.onFrame(frame);
            frame.release();
         } else {
            Logging.w("SurfaceTextureHelper", "Texture size has not been set.");
         }
      }
   }

   private void release() {
      if (this.handler.getLooper().getThread() != Thread.currentThread()) {
         throw new IllegalStateException("Wrong thread.");
      } else if (!this.isTextureInUse && this.isQuitting) {
         this.yuvConverter.release();
         GLES20.glDeleteTextures(1, new int[]{this.oesTextureId}, 0);
         this.surfaceTexture.release();
         this.eglBase.release();
         this.handler.getLooper().quit();
         if (this.timestampAligner != null) {
            this.timestampAligner.dispose();
         }

      } else {
         throw new IllegalStateException("Unexpected release.");
      }
   }

   public interface FrameRefMonitor {
      void onNewBuffer(VideoFrame.TextureBuffer var1);

      void onRetainBuffer(VideoFrame.TextureBuffer var1);

      void onReleaseBuffer(VideoFrame.TextureBuffer var1);

      void onDestroyBuffer(VideoFrame.TextureBuffer var1);
   }
}
