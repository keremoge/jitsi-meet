package org.webrtc;

import android.graphics.Matrix;
import android.media.MediaCodecInfo;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.opengl.GLES20;
import android.os.Bundle;
import android.view.Surface;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

class HardwareVideoEncoder implements VideoEncoder {
   private static final String TAG = "HardwareVideoEncoder";
   private static final int MAX_VIDEO_FRAMERATE = 30;
   private static final int MAX_ENCODER_Q_SIZE = 2;
   private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
   private static final int DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = 100000;
   private static final int REQUIRED_RESOLUTION_ALIGNMENT = 16;
   private final MediaCodecWrapperFactory mediaCodecWrapperFactory;
   private final String codecName;
   private final VideoCodecMimeType codecType;
   private final Integer surfaceColorFormat;
   private final Integer yuvColorFormat;
   private final Map<String, String> params;
   private final int keyFrameIntervalSec;
   private final long forcedKeyFrameNs;
   private final BitrateAdjuster bitrateAdjuster;
   private final EglBase14.Context sharedContext;
   private final GlRectDrawer textureDrawer = new GlRectDrawer();
   private final VideoFrameDrawer videoFrameDrawer = new VideoFrameDrawer();
   private final BlockingDeque<EncodedImage.Builder> outputBuilders = new LinkedBlockingDeque();
   private final ThreadUtils.ThreadChecker encodeThreadChecker = new ThreadUtils.ThreadChecker();
   private final ThreadUtils.ThreadChecker outputThreadChecker = new ThreadUtils.ThreadChecker();
   private final HardwareVideoEncoder.BusyCount outputBuffersBusyCount = new HardwareVideoEncoder.BusyCount();
   private VideoEncoder.Callback callback;
   private boolean automaticResizeOn;
   @Nullable
   private MediaCodecWrapper codec;
   @Nullable
   private Thread outputThread;
   @Nullable
   private EglBase14 textureEglBase;
   @Nullable
   private Surface textureInputSurface;
   private int width;
   private int height;
   private int stride;
   private int sliceHeight;
   private boolean isSemiPlanar;
   private int frameSizeBytes;
   private boolean useSurfaceMode;
   private long nextPresentationTimestampUs;
   private long lastKeyFrameNs;
   @Nullable
   private ByteBuffer configBuffer;
   private int adjustedBitrate;
   private volatile boolean running;
   @Nullable
   private volatile Exception shutdownException;
   private boolean isEncodingStatisticsEnabled;

   public HardwareVideoEncoder(MediaCodecWrapperFactory mediaCodecWrapperFactory, String codecName, VideoCodecMimeType codecType, Integer surfaceColorFormat, Integer yuvColorFormat, Map<String, String> params, int keyFrameIntervalSec, int forceKeyFrameIntervalMs, BitrateAdjuster bitrateAdjuster, EglBase14.Context sharedContext) {
      this.mediaCodecWrapperFactory = mediaCodecWrapperFactory;
      this.codecName = codecName;
      this.codecType = codecType;
      this.surfaceColorFormat = surfaceColorFormat;
      this.yuvColorFormat = yuvColorFormat;
      this.params = params;
      this.keyFrameIntervalSec = keyFrameIntervalSec;
      this.forcedKeyFrameNs = TimeUnit.MILLISECONDS.toNanos((long)forceKeyFrameIntervalMs);
      this.bitrateAdjuster = bitrateAdjuster;
      this.sharedContext = sharedContext;
      this.encodeThreadChecker.detachThread();
   }

   public VideoCodecStatus initEncode(VideoEncoder.Settings settings, VideoEncoder.Callback callback) {
      this.encodeThreadChecker.checkIsOnValidThread();
      this.callback = callback;
      this.automaticResizeOn = settings.automaticResizeOn;
      this.width = settings.width;
      this.height = settings.height;
      this.useSurfaceMode = this.canUseSurface();
      if (settings.startBitrate != 0 && settings.maxFramerate != 0) {
         this.bitrateAdjuster.setTargets(settings.startBitrate * 1000, (double)settings.maxFramerate);
      }

      this.adjustedBitrate = this.bitrateAdjuster.getAdjustedBitrateBps();
      Logging.d("HardwareVideoEncoder", "initEncode name: " + this.codecName + " type: " + this.codecType + " width: " + this.width + " height: " + this.height + " framerate_fps: " + settings.maxFramerate + " bitrate_kbps: " + settings.startBitrate + " surface mode: " + this.useSurfaceMode);
      return this.initEncodeInternal();
   }

   private VideoCodecStatus initEncodeInternal() {
      this.encodeThreadChecker.checkIsOnValidThread();
      this.nextPresentationTimestampUs = 0L;
      this.lastKeyFrameNs = -1L;
      this.isEncodingStatisticsEnabled = false;

      try {
         this.codec = this.mediaCodecWrapperFactory.createByCodecName(this.codecName);
      } catch (IllegalArgumentException | IOException var6) {
         Logging.e("HardwareVideoEncoder", "Cannot create media encoder " + this.codecName);
         return VideoCodecStatus.FALLBACK_SOFTWARE;
      }

      int colorFormat = this.useSurfaceMode ? this.surfaceColorFormat : this.yuvColorFormat;

      try {
         MediaFormat format = MediaFormat.createVideoFormat(this.codecType.mimeType(), this.width, this.height);
         format.setInteger("bitrate", this.adjustedBitrate);
         format.setInteger("bitrate-mode", 2);
         format.setInteger("color-format", colorFormat);
         format.setFloat("frame-rate", (float)this.bitrateAdjuster.getAdjustedFramerateFps());
         format.setInteger("i-frame-interval", this.keyFrameIntervalSec);
         if (this.codecType == VideoCodecMimeType.H264) {
            String profileLevelId = (String)this.params.get("profile-level-id");
            if (profileLevelId == null) {
               profileLevelId = "42e01f";
            }

            byte var5 = -1;
            switch(profileLevelId.hashCode()) {
            case 1537948542:
               if (profileLevelId.equals("42e01f")) {
                  var5 = 1;
               }
               break;
            case 1595523974:
               if (profileLevelId.equals("640c1f")) {
                  var5 = 0;
               }
            }

            switch(var5) {
            case 0:
               format.setInteger("profile", 8);
               format.setInteger("level", 256);
            case 1:
               break;
            default:
               Logging.w("HardwareVideoEncoder", "Unknown profile level id: " + profileLevelId);
            }
         }

         if (this.codecName.equals("c2.google.av1.encoder")) {
            format.setInteger("vendor.google-av1enc.encoding-preset.int32.value", 1);
         }

         if (this.isEncodingStatisticsSupported()) {
            format.setInteger("video-encoding-statistics-level", 1);
            this.isEncodingStatisticsEnabled = true;
         }

         Logging.d("HardwareVideoEncoder", "Format: " + format);
         this.codec.configure(format, (Surface)null, (MediaCrypto)null, 1);
         if (this.useSurfaceMode) {
            this.textureEglBase = EglBase.createEgl14(this.sharedContext, EglBase.CONFIG_RECORDABLE);
            this.textureInputSurface = this.codec.createInputSurface();
            this.textureEglBase.createSurface(this.textureInputSurface);
            this.textureEglBase.makeCurrent();
         }

         this.updateInputFormat(this.codec.getInputFormat());
         this.codec.start();
      } catch (IllegalStateException | IllegalArgumentException var7) {
         Logging.e("HardwareVideoEncoder", "initEncodeInternal failed", var7);
         this.release();
         return VideoCodecStatus.FALLBACK_SOFTWARE;
      }

      this.running = true;
      this.outputThreadChecker.detachThread();
      this.outputThread = this.createOutputThread();
      this.outputThread.start();
      return VideoCodecStatus.OK;
   }

   public VideoCodecStatus release() {
      this.encodeThreadChecker.checkIsOnValidThread();
      VideoCodecStatus returnValue;
      if (this.outputThread == null) {
         returnValue = VideoCodecStatus.OK;
      } else {
         this.running = false;
         if (!ThreadUtils.joinUninterruptibly(this.outputThread, 5000L)) {
            Logging.e("HardwareVideoEncoder", "Media encoder release timeout");
            returnValue = VideoCodecStatus.TIMEOUT;
         } else if (this.shutdownException != null) {
            Logging.e("HardwareVideoEncoder", "Media encoder release exception", this.shutdownException);
            returnValue = VideoCodecStatus.ERROR;
         } else {
            returnValue = VideoCodecStatus.OK;
         }
      }

      this.textureDrawer.release();
      this.videoFrameDrawer.release();
      if (this.textureEglBase != null) {
         this.textureEglBase.release();
         this.textureEglBase = null;
      }

      if (this.textureInputSurface != null) {
         this.textureInputSurface.release();
         this.textureInputSurface = null;
      }

      this.outputBuilders.clear();
      this.codec = null;
      this.outputThread = null;
      this.encodeThreadChecker.detachThread();
      return returnValue;
   }

   public VideoCodecStatus encode(VideoFrame videoFrame, VideoEncoder.EncodeInfo encodeInfo) {
      this.encodeThreadChecker.checkIsOnValidThread();
      if (this.codec == null) {
         return VideoCodecStatus.UNINITIALIZED;
      } else {
         boolean isTextureBuffer = videoFrame.getBuffer() instanceof VideoFrame.TextureBuffer;
         int frameWidth = videoFrame.getBuffer().getWidth();
         int frameHeight = videoFrame.getBuffer().getHeight();
         boolean shouldUseSurfaceMode = this.canUseSurface() && isTextureBuffer;
         if (frameWidth != this.width || frameHeight != this.height || shouldUseSurfaceMode != this.useSurfaceMode) {
            VideoCodecStatus status = this.resetCodec(frameWidth, frameHeight, shouldUseSurfaceMode);
            if (status != VideoCodecStatus.OK) {
               return status;
            }
         }

         if (this.outputBuilders.size() > 2) {
            Logging.e("HardwareVideoEncoder", "Dropped frame, encoder queue full");
            return VideoCodecStatus.NO_OUTPUT;
         } else {
            boolean requestedKeyFrame = false;
            EncodedImage.FrameType[] var8 = encodeInfo.frameTypes;
            int var9 = var8.length;

            for(int var10 = 0; var10 < var9; ++var10) {
               EncodedImage.FrameType frameType = var8[var10];
               if (frameType == EncodedImage.FrameType.VideoFrameKey) {
                  requestedKeyFrame = true;
               }
            }

            if (requestedKeyFrame || this.shouldForceKeyFrame(videoFrame.getTimestampNs())) {
               this.requestKeyFrame(videoFrame.getTimestampNs());
            }

            EncodedImage.Builder builder = EncodedImage.builder().setCaptureTimeNs(videoFrame.getTimestampNs()).setEncodedWidth(videoFrame.getBuffer().getWidth()).setEncodedHeight(videoFrame.getBuffer().getHeight()).setRotation(videoFrame.getRotation());
            this.outputBuilders.offer(builder);
            long presentationTimestampUs = this.nextPresentationTimestampUs;
            long frameDurationUs = (long)((double)TimeUnit.SECONDS.toMicros(1L) / this.bitrateAdjuster.getAdjustedFramerateFps());
            this.nextPresentationTimestampUs += frameDurationUs;
            VideoCodecStatus returnValue;
            if (this.useSurfaceMode) {
               returnValue = this.encodeTextureBuffer(videoFrame, presentationTimestampUs);
            } else {
               returnValue = this.encodeByteBuffer(videoFrame, presentationTimestampUs);
            }

            if (returnValue != VideoCodecStatus.OK) {
               this.outputBuilders.pollLast();
            }

            return returnValue;
         }
      }
   }

   private VideoCodecStatus encodeTextureBuffer(VideoFrame videoFrame, long presentationTimestampUs) {
      this.encodeThreadChecker.checkIsOnValidThread();

      try {
         GLES20.glClear(16384);
         VideoFrame derotatedFrame = new VideoFrame(videoFrame.getBuffer(), 0, videoFrame.getTimestampNs());
         this.videoFrameDrawer.drawFrame(derotatedFrame, this.textureDrawer, (Matrix)null);
         this.textureEglBase.swapBuffers(TimeUnit.MICROSECONDS.toNanos(presentationTimestampUs));
      } catch (RuntimeException var5) {
         Logging.e("HardwareVideoEncoder", "encodeTexture failed", var5);
         return VideoCodecStatus.ERROR;
      }

      return VideoCodecStatus.OK;
   }

   private VideoCodecStatus encodeByteBuffer(VideoFrame videoFrame, long presentationTimestampUs) {
      this.encodeThreadChecker.checkIsOnValidThread();

      int index;
      try {
         index = this.codec.dequeueInputBuffer(0L);
      } catch (IllegalStateException var9) {
         Logging.e("HardwareVideoEncoder", "dequeueInputBuffer failed", var9);
         return VideoCodecStatus.ERROR;
      }

      if (index == -1) {
         Logging.d("HardwareVideoEncoder", "Dropped frame, no input buffers available");
         return VideoCodecStatus.NO_OUTPUT;
      } else {
         ByteBuffer buffer;
         try {
            buffer = this.codec.getInputBuffer(index);
         } catch (IllegalStateException var8) {
            Logging.e("HardwareVideoEncoder", "getInputBuffer with index=" + index + " failed", var8);
            return VideoCodecStatus.ERROR;
         }

         if (buffer.capacity() < this.frameSizeBytes) {
            int var10001 = buffer.capacity();
            Logging.e("HardwareVideoEncoder", "Input buffer size: " + var10001 + " is smaller than frame size: " + this.frameSizeBytes);
            return VideoCodecStatus.ERROR;
         } else {
            this.fillInputBuffer(buffer, videoFrame.getBuffer());

            try {
               this.codec.queueInputBuffer(index, 0, this.frameSizeBytes, presentationTimestampUs, 0);
            } catch (IllegalStateException var7) {
               Logging.e("HardwareVideoEncoder", "queueInputBuffer failed", var7);
               return VideoCodecStatus.ERROR;
            }

            return VideoCodecStatus.OK;
         }
      }
   }

   public VideoCodecStatus setRateAllocation(VideoEncoder.BitrateAllocation bitrateAllocation, int framerate) {
      this.encodeThreadChecker.checkIsOnValidThread();
      if (framerate > 30) {
         framerate = 30;
      }

      this.bitrateAdjuster.setTargets(bitrateAllocation.getSum(), (double)framerate);
      return VideoCodecStatus.OK;
   }

   public VideoCodecStatus setRates(VideoEncoder.RateControlParameters rcParameters) {
      this.encodeThreadChecker.checkIsOnValidThread();
      this.bitrateAdjuster.setTargets(rcParameters.bitrate.getSum(), rcParameters.framerateFps);
      return VideoCodecStatus.OK;
   }

   public VideoEncoder.ScalingSettings getScalingSettings() {
      if (this.automaticResizeOn) {
         boolean kLowH264QpThreshold;
         boolean kHighH264QpThreshold;
         if (this.codecType == VideoCodecMimeType.VP8) {
            kLowH264QpThreshold = true;
            kHighH264QpThreshold = true;
            return new VideoEncoder.ScalingSettings(29, 95);
         }

         if (this.codecType == VideoCodecMimeType.H264) {
            kLowH264QpThreshold = true;
            kHighH264QpThreshold = true;
            return new VideoEncoder.ScalingSettings(24, 37);
         }
      }

      return VideoEncoder.ScalingSettings.OFF;
   }

   public String getImplementationName() {
      return this.codecName;
   }

   public VideoEncoder.EncoderInfo getEncoderInfo() {
      return new VideoEncoder.EncoderInfo(16, false);
   }

   private VideoCodecStatus resetCodec(int newWidth, int newHeight, boolean newUseSurfaceMode) {
      this.encodeThreadChecker.checkIsOnValidThread();
      VideoCodecStatus status = this.release();
      if (status != VideoCodecStatus.OK) {
         return status;
      } else {
         this.width = newWidth;
         this.height = newHeight;
         this.useSurfaceMode = newUseSurfaceMode;
         return this.initEncodeInternal();
      }
   }

   private boolean shouldForceKeyFrame(long presentationTimestampNs) {
      this.encodeThreadChecker.checkIsOnValidThread();
      return this.forcedKeyFrameNs > 0L && presentationTimestampNs > this.lastKeyFrameNs + this.forcedKeyFrameNs;
   }

   private void requestKeyFrame(long presentationTimestampNs) {
      this.encodeThreadChecker.checkIsOnValidThread();

      try {
         Bundle b = new Bundle();
         b.putInt("request-sync", 0);
         this.codec.setParameters(b);
      } catch (IllegalStateException var4) {
         Logging.e("HardwareVideoEncoder", "requestKeyFrame failed", var4);
         return;
      }

      this.lastKeyFrameNs = presentationTimestampNs;
   }

   private Thread createOutputThread() {
      return new Thread() {
         public void run() {
            while(HardwareVideoEncoder.this.running) {
               HardwareVideoEncoder.this.deliverEncodedImage();
            }

            HardwareVideoEncoder.this.releaseCodecOnOutputThread();
         }
      };
   }

   protected void deliverEncodedImage() {
      this.outputThreadChecker.checkIsOnValidThread();

      try {
         BufferInfo info = new BufferInfo();
         int index = this.codec.dequeueOutputBuffer(info, 100000L);
         if (index < 0) {
            if (index == -3) {
               this.outputBuffersBusyCount.waitForZero();
            }

            return;
         }

         ByteBuffer outputBuffer = this.codec.getOutputBuffer(index);
         outputBuffer.position(info.offset);
         outputBuffer.limit(info.offset + info.size);
         if ((info.flags & 2) != 0) {
            Logging.d("HardwareVideoEncoder", "Config frame generated. Offset: " + info.offset + ". Size: " + info.size);
            if (info.size > 0 && (this.codecType == VideoCodecMimeType.H264 || this.codecType == VideoCodecMimeType.H265)) {
               this.configBuffer = ByteBuffer.allocateDirect(info.size);
               this.configBuffer.put(outputBuffer);
            }

            return;
         }

         this.bitrateAdjuster.reportEncodedFrame(info.size);
         if (this.adjustedBitrate != this.bitrateAdjuster.getAdjustedBitrateBps()) {
            this.updateBitrate();
         }

         boolean isKeyFrame = (info.flags & 1) != 0;
         if (isKeyFrame) {
            Logging.d("HardwareVideoEncoder", "Sync frame generated");
         }

         Integer qp = null;
         if (this.isEncodingStatisticsEnabled) {
            MediaFormat format = this.codec.getOutputFormat(index);
            if (format != null && format.containsKey("video-qp-average")) {
               qp = format.getInteger("video-qp-average");
            }
         }

         Runnable releaseCallback;
         ByteBuffer frameBuffer;
         if (isKeyFrame && this.configBuffer != null) {
            int var10001 = this.configBuffer.capacity();
            Logging.d("HardwareVideoEncoder", "Prepending config buffer of size " + var10001 + " to output buffer with offset " + info.offset + ", size " + info.size);
            frameBuffer = ByteBuffer.allocateDirect(info.size + this.configBuffer.capacity());
            this.configBuffer.rewind();
            frameBuffer.put(this.configBuffer);
            frameBuffer.put(outputBuffer);
            frameBuffer.rewind();
            this.codec.releaseOutputBuffer(index, false);
            releaseCallback = null;
         } else {
            frameBuffer = outputBuffer.slice();
            this.outputBuffersBusyCount.increment();
            releaseCallback = () -> {
               try {
                  this.codec.releaseOutputBuffer(index, false);
               } catch (Exception var3) {
                  Logging.e("HardwareVideoEncoder", "releaseOutputBuffer failed", var3);
               }

               this.outputBuffersBusyCount.decrement();
            };
         }

         EncodedImage.FrameType frameType = isKeyFrame ? EncodedImage.FrameType.VideoFrameKey : EncodedImage.FrameType.VideoFrameDelta;
         EncodedImage.Builder builder = (EncodedImage.Builder)this.outputBuilders.poll();
         builder.setBuffer(frameBuffer, releaseCallback);
         builder.setFrameType(frameType);
         builder.setQp(qp);
         EncodedImage encodedImage = builder.createEncodedImage();
         this.callback.onEncodedFrame(encodedImage, new VideoEncoder.CodecSpecificInfo());
         encodedImage.release();
      } catch (IllegalStateException var11) {
         Logging.e("HardwareVideoEncoder", "deliverOutput failed", var11);
      }

   }

   private void releaseCodecOnOutputThread() {
      this.outputThreadChecker.checkIsOnValidThread();
      Logging.d("HardwareVideoEncoder", "Releasing MediaCodec on output thread");
      this.outputBuffersBusyCount.waitForZero();

      try {
         this.codec.stop();
      } catch (Exception var3) {
         Logging.e("HardwareVideoEncoder", "Media encoder stop failed", var3);
      }

      try {
         this.codec.release();
      } catch (Exception var2) {
         Logging.e("HardwareVideoEncoder", "Media encoder release failed", var2);
         this.shutdownException = var2;
      }

      this.configBuffer = null;
      Logging.d("HardwareVideoEncoder", "Release on output thread done");
   }

   private VideoCodecStatus updateBitrate() {
      this.outputThreadChecker.checkIsOnValidThread();
      this.adjustedBitrate = this.bitrateAdjuster.getAdjustedBitrateBps();

      try {
         Bundle params = new Bundle();
         params.putInt("video-bitrate", this.adjustedBitrate);
         this.codec.setParameters(params);
         return VideoCodecStatus.OK;
      } catch (IllegalStateException var2) {
         Logging.e("HardwareVideoEncoder", "updateBitrate failed", var2);
         return VideoCodecStatus.ERROR;
      }
   }

   private boolean canUseSurface() {
      return this.sharedContext != null && this.surfaceColorFormat != null;
   }

   private void updateInputFormat(MediaFormat format) {
      this.stride = this.width;
      this.sliceHeight = this.height;
      if (format != null) {
         if (format.containsKey("stride")) {
            this.stride = format.getInteger("stride");
            this.stride = Math.max(this.stride, this.width);
         }

         if (format.containsKey("slice-height")) {
            this.sliceHeight = format.getInteger("slice-height");
            this.sliceHeight = Math.max(this.sliceHeight, this.height);
         }
      }

      this.isSemiPlanar = this.isSemiPlanar(this.yuvColorFormat);
      int chromaHeight;
      if (this.isSemiPlanar) {
         chromaHeight = (this.height + 1) / 2;
         this.frameSizeBytes = this.sliceHeight * this.stride + chromaHeight * this.stride;
      } else {
         chromaHeight = (this.stride + 1) / 2;
         int chromaSliceHeight = (this.sliceHeight + 1) / 2;
         this.frameSizeBytes = this.sliceHeight * this.stride + chromaSliceHeight * chromaHeight * 2;
      }

      Logging.d("HardwareVideoEncoder", "updateInputFormat format: " + format + " stride: " + this.stride + " sliceHeight: " + this.sliceHeight + " isSemiPlanar: " + this.isSemiPlanar + " frameSizeBytes: " + this.frameSizeBytes);
   }

   protected boolean isEncodingStatisticsSupported() {
      if (this.codecType != VideoCodecMimeType.VP8 && this.codecType != VideoCodecMimeType.VP9) {
         MediaCodecInfo codecInfo = this.codec.getCodecInfo();
         if (codecInfo == null) {
            return false;
         } else {
            CodecCapabilities codecCaps = codecInfo.getCapabilitiesForType(this.codecType.mimeType());
            return codecCaps == null ? false : codecCaps.isFeatureSupported("encoding-statistics");
         }
      } else {
         return false;
      }
   }

   protected void fillInputBuffer(ByteBuffer buffer, VideoFrame.Buffer frame) {
      VideoFrame.I420Buffer i420 = frame.toI420();
      if (this.isSemiPlanar) {
         YuvHelper.I420ToNV12(i420.getDataY(), i420.getStrideY(), i420.getDataU(), i420.getStrideU(), i420.getDataV(), i420.getStrideV(), buffer, i420.getWidth(), i420.getHeight(), this.stride, this.sliceHeight);
      } else {
         YuvHelper.I420Copy(i420.getDataY(), i420.getStrideY(), i420.getDataU(), i420.getStrideU(), i420.getDataV(), i420.getStrideV(), buffer, i420.getWidth(), i420.getHeight(), this.stride, this.sliceHeight);
      }

      i420.release();
   }

   protected boolean isSemiPlanar(int colorFormat) {
      switch(colorFormat) {
      case 19:
         return false;
      case 21:
      case 2141391872:
      case 2141391876:
         return true;
      default:
         throw new IllegalArgumentException("Unsupported colorFormat: " + colorFormat);
      }
   }

   private static class BusyCount {
      private final Object countLock = new Object();
      private int count;

      public void increment() {
         synchronized(this.countLock) {
            ++this.count;
         }
      }

      public void decrement() {
         synchronized(this.countLock) {
            --this.count;
            if (this.count == 0) {
               this.countLock.notifyAll();
            }

         }
      }

      public void waitForZero() {
         boolean wasInterrupted = false;
         synchronized(this.countLock) {
            while(this.count > 0) {
               try {
                  this.countLock.wait();
               } catch (InterruptedException var5) {
                  Logging.e("HardwareVideoEncoder", "Interrupted while waiting on busy count", var5);
                  wasInterrupted = true;
               }
            }
         }

         if (wasInterrupted) {
            Thread.currentThread().interrupt();
         }

      }
   }
}
