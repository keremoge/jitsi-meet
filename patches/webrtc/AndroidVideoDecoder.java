package org.webrtc;

import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

class AndroidVideoDecoder implements VideoDecoder, VideoSink {
   private static final String TAG = "AndroidVideoDecoder";
   private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
   private static final int DEQUEUE_INPUT_TIMEOUT_US = 500000;
   private static final int DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = 100000;
   private final MediaCodecWrapperFactory mediaCodecWrapperFactory;
   private final String codecName;
   private final VideoCodecMimeType codecType;
   private final BlockingDeque<AndroidVideoDecoder.FrameInfo> frameInfos;
   private int colorFormat;
   @Nullable
   private Thread outputThread;
   private ThreadUtils.ThreadChecker outputThreadChecker;
   private ThreadUtils.ThreadChecker decoderThreadChecker;
   private volatile boolean running;
   @Nullable
   private volatile Exception shutdownException;
   private final Object dimensionLock = new Object();
   private int width;
   private int height;
   private int stride;
   private int sliceHeight;
   private boolean hasDecodedFirstFrame;
   private boolean keyFrameRequired;
   @Nullable
   private final EglBase.Context sharedContext;
   @Nullable
   private SurfaceTextureHelper surfaceTextureHelper;
   @Nullable
   private Surface surface;
   private final Object renderedTextureMetadataLock = new Object();
   @Nullable
   private AndroidVideoDecoder.DecodedTextureMetadata renderedTextureMetadata;
   @Nullable
   private VideoDecoder.Callback callback;
   @Nullable
   private MediaCodecWrapper codec;

   AndroidVideoDecoder(MediaCodecWrapperFactory mediaCodecWrapperFactory, String codecName, VideoCodecMimeType codecType, int colorFormat, @Nullable EglBase.Context sharedContext) {
      if (!this.isSupportedColorFormat(colorFormat)) {
         throw new IllegalArgumentException("Unsupported color format: " + colorFormat);
      } else {
         Logging.d("AndroidVideoDecoder", "ctor name: " + codecName + " type: " + codecType + " color format: " + colorFormat + " context: " + sharedContext);
         this.mediaCodecWrapperFactory = mediaCodecWrapperFactory;
         this.codecName = codecName;
         this.codecType = codecType;
         this.colorFormat = colorFormat;
         this.sharedContext = sharedContext;
         this.frameInfos = new LinkedBlockingDeque();
      }
   }

   public VideoCodecStatus initDecode(VideoDecoder.Settings settings, VideoDecoder.Callback callback) {
      this.decoderThreadChecker = new ThreadUtils.ThreadChecker();
      this.callback = callback;
      if (this.sharedContext != null) {
         this.surfaceTextureHelper = this.createSurfaceTextureHelper();
         this.surface = new Surface(this.surfaceTextureHelper.getSurfaceTexture());
         this.surfaceTextureHelper.startListening(this);
      }

      return this.initDecodeInternal(settings.width, settings.height);
   }

   private VideoCodecStatus initDecodeInternal(int width, int height) {
      this.decoderThreadChecker.checkIsOnValidThread();
      Logging.d("AndroidVideoDecoder", "initDecodeInternal name: " + this.codecName + " type: " + this.codecType + " width: " + width + " height: " + height + " color format: " + this.colorFormat);
      if (this.outputThread != null) {
         Logging.e("AndroidVideoDecoder", "initDecodeInternal called while the codec is already running");
         return VideoCodecStatus.FALLBACK_SOFTWARE;
      } else {
         this.width = width;
         this.height = height;
         this.stride = width;
         this.sliceHeight = height;
         this.hasDecodedFirstFrame = false;
         this.keyFrameRequired = true;

         try {
            this.codec = this.mediaCodecWrapperFactory.createByCodecName(this.codecName);
         } catch (IllegalArgumentException | IllegalStateException | IOException var4) {
            Logging.e("AndroidVideoDecoder", "Cannot create media decoder " + this.codecName);
            return VideoCodecStatus.FALLBACK_SOFTWARE;
         }

         try {
            MediaFormat format = MediaFormat.createVideoFormat(this.codecType.mimeType(), width, height);
            if (this.sharedContext == null) {
               format.setInteger("color-format", this.colorFormat);
            }

            this.codec.configure(format, this.surface, (MediaCrypto)null, 0);
            this.codec.start();
         } catch (IllegalArgumentException | IllegalStateException var5) {
            Logging.e("AndroidVideoDecoder", "initDecode failed", var5);
            this.release();
            return VideoCodecStatus.FALLBACK_SOFTWARE;
         }

         this.running = true;
         this.outputThread = this.createOutputThread();
         this.outputThread.start();
         Logging.d("AndroidVideoDecoder", "initDecodeInternal done");
         return VideoCodecStatus.OK;
      }
   }

   public VideoCodecStatus decode(EncodedImage frame, VideoDecoder.DecodeInfo info) {
      this.decoderThreadChecker.checkIsOnValidThread();
      if (this.codec != null && this.callback != null) {
         if (frame.buffer == null) {
            Logging.e("AndroidVideoDecoder", "decode() - no input data");
            return VideoCodecStatus.ERR_PARAMETER;
         } else {
            int size = frame.buffer.remaining();
            if (size == 0) {
               Logging.e("AndroidVideoDecoder", "decode() - input buffer empty");
               return VideoCodecStatus.ERR_PARAMETER;
            } else {
               int width;
               int height;
               synchronized(this.dimensionLock) {
                  width = this.width;
                  height = this.height;
               }

               if (frame.encodedWidth * frame.encodedHeight > 0 && (frame.encodedWidth != width || frame.encodedHeight != height)) {
                  VideoCodecStatus status = this.reinitDecode(frame.encodedWidth, frame.encodedHeight);
                  if (status != VideoCodecStatus.OK) {
                     return status;
                  }
               }

               if (this.keyFrameRequired && frame.frameType != EncodedImage.FrameType.VideoFrameKey) {
                  Logging.e("AndroidVideoDecoder", "decode() - key frame required first");
                  return VideoCodecStatus.NO_OUTPUT;
               } else {
                  int index;
                  try {
                     index = this.codec.dequeueInputBuffer(500000L);
                  } catch (IllegalStateException var11) {
                     Logging.e("AndroidVideoDecoder", "dequeueInputBuffer failed", var11);
                     return VideoCodecStatus.ERROR;
                  }

                  if (index < 0) {
                     Logging.e("AndroidVideoDecoder", "decode() - no HW buffers available; decoder falling behind");
                     return VideoCodecStatus.ERROR;
                  } else {
                     ByteBuffer buffer;
                     try {
                        buffer = this.codec.getInputBuffer(index);
                     } catch (IllegalStateException var10) {
                        Logging.e("AndroidVideoDecoder", "getInputBuffer with index=" + index + " failed", var10);
                        return VideoCodecStatus.ERROR;
                     }

                     if (buffer.capacity() < size) {
                        Logging.e("AndroidVideoDecoder", "decode() - HW buffer too small");
                        return VideoCodecStatus.ERROR;
                     } else {
                        buffer.put(frame.buffer);
                        this.frameInfos.offer(new AndroidVideoDecoder.FrameInfo(SystemClock.elapsedRealtime(), frame.rotation));

                        try {
                           this.codec.queueInputBuffer(index, 0, size, TimeUnit.NANOSECONDS.toMicros(frame.captureTimeNs), 0);
                        } catch (IllegalStateException var9) {
                           Logging.e("AndroidVideoDecoder", "queueInputBuffer failed", var9);
                           this.frameInfos.pollLast();
                           return VideoCodecStatus.ERROR;
                        }

                        if (this.keyFrameRequired) {
                           this.keyFrameRequired = false;
                        }

                        return VideoCodecStatus.OK;
                     }
                  }
               }
            }
         }
      } else {
         boolean var10001 = this.codec != null;
         Logging.d("AndroidVideoDecoder", "decode uninitalized, codec: " + var10001 + ", callback: " + this.callback);
         return VideoCodecStatus.UNINITIALIZED;
      }
   }

   public String getImplementationName() {
      return this.codecName;
   }

   public VideoCodecStatus release() {
      Logging.d("AndroidVideoDecoder", "release");
      VideoCodecStatus status = this.releaseInternal();
      if (this.surface != null) {
         this.releaseSurface();
         this.surface = null;
         this.surfaceTextureHelper.stopListening();
         this.surfaceTextureHelper.dispose();
         this.surfaceTextureHelper = null;
      }

      synchronized(this.renderedTextureMetadataLock) {
         this.renderedTextureMetadata = null;
      }

      this.callback = null;
      this.frameInfos.clear();
      return status;
   }

   private VideoCodecStatus releaseInternal() {
      if (!this.running) {
         Logging.d("AndroidVideoDecoder", "release: Decoder is not running.");
         return VideoCodecStatus.OK;
      } else {
         VideoCodecStatus var1;
         try {
            this.running = false;
            if (ThreadUtils.joinUninterruptibly(this.outputThread, 5000L)) {
               if (this.shutdownException == null) {
                  return VideoCodecStatus.OK;
               }

               Logging.e("AndroidVideoDecoder", "Media decoder release error", new RuntimeException(this.shutdownException));
               this.shutdownException = null;
               var1 = VideoCodecStatus.ERROR;
               return var1;
            }

            Logging.e("AndroidVideoDecoder", "Media decoder release timeout", new RuntimeException());
            var1 = VideoCodecStatus.TIMEOUT;
         } finally {
            this.codec = null;
            this.outputThread = null;
         }

         return var1;
      }
   }

   private VideoCodecStatus reinitDecode(int newWidth, int newHeight) {
      this.decoderThreadChecker.checkIsOnValidThread();
      VideoCodecStatus status = this.releaseInternal();
      return status != VideoCodecStatus.OK ? status : this.initDecodeInternal(newWidth, newHeight);
   }

   private Thread createOutputThread() {
      return new Thread("AndroidVideoDecoder.outputThread") {
         public void run() {
            AndroidVideoDecoder.this.outputThreadChecker = new ThreadUtils.ThreadChecker();

            while(AndroidVideoDecoder.this.running) {
               AndroidVideoDecoder.this.deliverDecodedFrame();
            }

            AndroidVideoDecoder.this.releaseCodecOnOutputThread();
         }
      };
   }

   protected void deliverDecodedFrame() {
      this.outputThreadChecker.checkIsOnValidThread();

      try {
         BufferInfo info = new BufferInfo();
         int index = this.codec.dequeueOutputBuffer(info, 100000L);
         if (index == -2) {
            this.reformat(this.codec.getOutputFormat());
            return;
         }

         if (index < 0) {
            Logging.v("AndroidVideoDecoder", "dequeueOutputBuffer returned " + index);
            return;
         }

         AndroidVideoDecoder.FrameInfo frameInfo = (AndroidVideoDecoder.FrameInfo)this.frameInfos.poll();
         Integer decodeTimeMs = null;
         int rotation = 0;
         if (frameInfo != null) {
            decodeTimeMs = (int)(SystemClock.elapsedRealtime() - frameInfo.decodeStartTimeMs);
            rotation = frameInfo.rotation;
         }

         this.hasDecodedFirstFrame = true;
         if (this.surfaceTextureHelper != null) {
            this.deliverTextureFrame(index, info, rotation, decodeTimeMs);
         } else {
            this.deliverByteFrame(index, info, rotation, decodeTimeMs);
         }
      } catch (IllegalStateException var6) {
         Logging.e("AndroidVideoDecoder", "deliverDecodedFrame failed", var6);
      }

   }

   private void deliverTextureFrame(int index, BufferInfo info, int rotation, Integer decodeTimeMs) {
      int width;
      int height;
      synchronized(this.dimensionLock) {
         width = this.width;
         height = this.height;
      }

      synchronized(this.renderedTextureMetadataLock) {
         if (this.renderedTextureMetadata != null) {
            this.codec.releaseOutputBuffer(index, false);
         } else {
            this.surfaceTextureHelper.setTextureSize(width, height);
            this.surfaceTextureHelper.setFrameRotation(rotation);
            this.renderedTextureMetadata = new AndroidVideoDecoder.DecodedTextureMetadata(info.presentationTimeUs, decodeTimeMs);
            this.codec.releaseOutputBuffer(index, true);
         }
      }
   }

   public void onFrame(VideoFrame frame) {
      Integer decodeTimeMs;
      long timestampNs;
      synchronized(this.renderedTextureMetadataLock) {
         if (this.renderedTextureMetadata == null) {
            throw new IllegalStateException("Rendered texture metadata was null in onTextureFrameAvailable.");
         }

         timestampNs = this.renderedTextureMetadata.presentationTimestampUs * 1000L;
         decodeTimeMs = this.renderedTextureMetadata.decodeTimeMs;
         this.renderedTextureMetadata = null;
      }

      VideoFrame frameWithModifiedTimeStamp = new VideoFrame(frame.getBuffer(), frame.getRotation(), timestampNs);
      this.callback.onDecodedFrame(frameWithModifiedTimeStamp, decodeTimeMs, (Integer)null);
   }

   private void deliverByteFrame(int index, BufferInfo info, int rotation, Integer decodeTimeMs) {
      int width;
      int height;
      int stride;
      int sliceHeight;
      synchronized(this.dimensionLock) {
         width = this.width;
         height = this.height;
         stride = this.stride;
         sliceHeight = this.sliceHeight;
      }

      if (info.size < width * height * 3 / 2) {
         Logging.e("AndroidVideoDecoder", "Insufficient output buffer size: " + info.size);
      } else {
         if (info.size < stride * height * 3 / 2 && sliceHeight == height && stride > width) {
            stride = info.size * 2 / (height * 3);
         }

         ByteBuffer buffer = this.codec.getOutputBuffer(index);
         buffer.position(info.offset);
         buffer.limit(info.offset + info.size);
         buffer = buffer.slice();
         VideoFrame.Buffer frameBuffer;
         if (this.colorFormat == 19) {
            frameBuffer = this.copyI420Buffer(buffer, stride, sliceHeight, width, height);
         } else {
            frameBuffer = this.copyNV12ToI420Buffer(buffer, stride, sliceHeight, width, height);
         }

         this.codec.releaseOutputBuffer(index, false);
         long presentationTimeNs = info.presentationTimeUs * 1000L;
         VideoFrame frame = new VideoFrame(frameBuffer, rotation, presentationTimeNs);
         this.callback.onDecodedFrame(frame, decodeTimeMs, (Integer)null);
         frame.release();
      }
   }

   private VideoFrame.Buffer copyNV12ToI420Buffer(ByteBuffer buffer, int stride, int sliceHeight, int width, int height) {
      return (new NV12Buffer(width, height, stride, sliceHeight, buffer, (Runnable)null)).toI420();
   }

   private VideoFrame.Buffer copyI420Buffer(ByteBuffer buffer, int stride, int sliceHeight, int width, int height) {
      if (stride % 2 != 0) {
         throw new AssertionError("Stride is not divisible by two: " + stride);
      } else {
         int chromaWidth = (width + 1) / 2;
         int chromaHeight = sliceHeight % 2 == 0 ? (height + 1) / 2 : height / 2;
         int uvStride = stride / 2;
         int yPos = false;
         int yEnd = 0 + stride * height;
         int uPos = 0 + stride * sliceHeight;
         int uEnd = uPos + uvStride * chromaHeight;
         int vPos = uPos + uvStride * sliceHeight / 2;
         int vEnd = vPos + uvStride * chromaHeight;
         VideoFrame.I420Buffer frameBuffer = this.allocateI420Buffer(width, height);
         buffer.limit(yEnd);
         buffer.position(0);
         this.copyPlane(buffer.slice(), stride, frameBuffer.getDataY(), frameBuffer.getStrideY(), width, height);
         buffer.limit(uEnd);
         buffer.position(uPos);
         this.copyPlane(buffer.slice(), uvStride, frameBuffer.getDataU(), frameBuffer.getStrideU(), chromaWidth, chromaHeight);
         ByteBuffer dataV;
         if (sliceHeight % 2 == 1) {
            buffer.position(uPos + uvStride * (chromaHeight - 1));
            dataV = frameBuffer.getDataU();
            dataV.position(frameBuffer.getStrideU() * chromaHeight);
            dataV.put(buffer);
         }

         buffer.limit(vEnd);
         buffer.position(vPos);
         this.copyPlane(buffer.slice(), uvStride, frameBuffer.getDataV(), frameBuffer.getStrideV(), chromaWidth, chromaHeight);
         if (sliceHeight % 2 == 1) {
            buffer.position(vPos + uvStride * (chromaHeight - 1));
            dataV = frameBuffer.getDataV();
            dataV.position(frameBuffer.getStrideV() * chromaHeight);
            dataV.put(buffer);
         }

         return frameBuffer;
      }
   }

   private void reformat(MediaFormat format) {
      this.outputThreadChecker.checkIsOnValidThread();
      Logging.d("AndroidVideoDecoder", "Decoder format changed: " + format);
      int newWidth;
      int newHeight;
      if (format.containsKey("crop-left") && format.containsKey("crop-right") && format.containsKey("crop-bottom") && format.containsKey("crop-top")) {
         newWidth = 1 + format.getInteger("crop-right") - format.getInteger("crop-left");
         newHeight = 1 + format.getInteger("crop-bottom") - format.getInteger("crop-top");
      } else {
         newWidth = format.getInteger("width");
         newHeight = format.getInteger("height");
      }

      synchronized(this.dimensionLock) {
         if (newWidth != this.width || newHeight != this.height) {
            if (this.hasDecodedFirstFrame) {
               this.stopOnOutputThread(new RuntimeException("Unexpected size change. Configured " + this.width + "*" + this.height + ". New " + newWidth + "*" + newHeight));
               return;
            }

            if (newWidth <= 0 || newHeight <= 0) {
               Logging.w("AndroidVideoDecoder", "Unexpected format dimensions. Configured " + this.width + "*" + this.height + ". New " + newWidth + "*" + newHeight + ". Skip it");
               return;
            }

            this.width = newWidth;
            this.height = newHeight;
         }
      }

      if (this.surfaceTextureHelper == null && format.containsKey("color-format")) {
         this.colorFormat = format.getInteger("color-format");
         Logging.d("AndroidVideoDecoder", "Color: 0x" + Integer.toHexString(this.colorFormat));
         if (!this.isSupportedColorFormat(this.colorFormat)) {
            this.stopOnOutputThread(new IllegalStateException("Unsupported color format: " + this.colorFormat));
            return;
         }
      }

      synchronized(this.dimensionLock) {
         if (format.containsKey("stride")) {
            this.stride = format.getInteger("stride");
         }

         if (format.containsKey("slice-height")) {
            this.sliceHeight = format.getInteger("slice-height");
         }

         Logging.d("AndroidVideoDecoder", "Frame stride and slice height: " + this.stride + " x " + this.sliceHeight);
         this.stride = Math.max(this.width, this.stride);
         this.sliceHeight = Math.max(this.height, this.sliceHeight);
      }
   }

   private void releaseCodecOnOutputThread() {
      this.outputThreadChecker.checkIsOnValidThread();
      Logging.d("AndroidVideoDecoder", "Releasing MediaCodec on output thread");

      try {
         this.codec.stop();
      } catch (Exception var3) {
         Logging.e("AndroidVideoDecoder", "Media decoder stop failed", var3);
      }

      try {
         this.codec.release();
      } catch (Exception var2) {
         Logging.e("AndroidVideoDecoder", "Media decoder release failed", var2);
         this.shutdownException = var2;
      }

      Logging.d("AndroidVideoDecoder", "Release on output thread done");
   }

   private void stopOnOutputThread(Exception e) {
      this.outputThreadChecker.checkIsOnValidThread();
      this.running = false;
      this.shutdownException = e;
   }

   private boolean isSupportedColorFormat(int colorFormat) {
      int[] var2 = MediaCodecUtils.DECODER_COLOR_FORMATS;
      int var3 = var2.length;

      for(int var4 = 0; var4 < var3; ++var4) {
         int supported = var2[var4];
         if (supported == colorFormat) {
            return true;
         }
      }

      return false;
   }

   protected SurfaceTextureHelper createSurfaceTextureHelper() {
      return SurfaceTextureHelper.create("decoder-texture-thread", this.sharedContext);
   }

   protected void releaseSurface() {
      this.surface.release();
   }

   protected VideoFrame.I420Buffer allocateI420Buffer(int width, int height) {
      return JavaI420Buffer.allocate(width, height);
   }

   protected void copyPlane(ByteBuffer src, int srcStride, ByteBuffer dst, int dstStride, int width, int height) {
      YuvHelper.copyPlane(src, srcStride, dst, dstStride, width, height);
   }

   private static class FrameInfo {
      final long decodeStartTimeMs;
      final int rotation;

      FrameInfo(long decodeStartTimeMs, int rotation) {
         this.decodeStartTimeMs = decodeStartTimeMs;
         this.rotation = rotation;
      }
   }

   private static class DecodedTextureMetadata {
      final long presentationTimestampUs;
      final Integer decodeTimeMs;

      DecodedTextureMetadata(long presentationTimestampUs, Integer decodeTimeMs) {
         this.presentationTimestampUs = presentationTimestampUs;
         this.decodeTimeMs = decodeTimeMs;
      }
   }
}
