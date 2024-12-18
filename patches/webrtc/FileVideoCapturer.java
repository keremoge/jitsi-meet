package org.webrtc;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class FileVideoCapturer implements VideoCapturer {
   private static final String TAG = "FileVideoCapturer";
   private final FileVideoCapturer.VideoReader videoReader;
   private CapturerObserver capturerObserver;
   private final Timer timer = new Timer();
   private final TimerTask tickTask = new TimerTask() {
      public void run() {
         FileVideoCapturer.this.tick();
      }
   };

   public FileVideoCapturer(String inputFile) throws IOException {
      try {
         this.videoReader = new FileVideoCapturer.VideoReaderY4M(inputFile);
      } catch (IOException var3) {
         Logging.d("FileVideoCapturer", "Could not open video file: " + inputFile);
         throw var3;
      }
   }

   public void tick() {
      VideoFrame videoFrame = this.videoReader.getNextFrame();
      this.capturerObserver.onFrameCaptured(videoFrame);
      videoFrame.release();
   }

   public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
      this.capturerObserver = capturerObserver;
   }

   public void startCapture(int width, int height, int framerate) {
      this.timer.schedule(this.tickTask, 0L, (long)(1000 / framerate));
   }

   public void stopCapture() throws InterruptedException {
      this.timer.cancel();
   }

   public void changeCaptureFormat(int width, int height, int framerate) {
   }

   public void dispose() {
      this.videoReader.close();
   }

   public boolean isScreencast() {
      return false;
   }

   private static class VideoReaderY4M implements FileVideoCapturer.VideoReader {
      private static final String TAG = "VideoReaderY4M";
      private static final String Y4M_FRAME_DELIMETER = "FRAME";
      private static final int FRAME_DELIMETER_LENGTH = "FRAME".length() + 1;
      private final int frameWidth;
      private final int frameHeight;
      private final long videoStart;
      private final RandomAccessFile mediaFile;
      private final FileChannel mediaFileChannel;

      public VideoReaderY4M(String file) throws IOException {
         this.mediaFile = new RandomAccessFile(file, "r");
         this.mediaFileChannel = this.mediaFile.getChannel();
         StringBuilder builder = new StringBuilder();

         while(true) {
            int c = this.mediaFile.read();
            if (c == -1) {
               throw new RuntimeException("Found end of file before end of header for file: " + file);
            }

            if (c == 10) {
               this.videoStart = this.mediaFileChannel.position();
               String header = builder.toString();
               String[] headerTokens = header.split("[ ]");
               int w = 0;
               int h = 0;
               String colorSpace = "";
               String[] var8 = headerTokens;
               int var9 = headerTokens.length;

               for(int var10 = 0; var10 < var9; ++var10) {
                  String tok = var8[var10];
                  char c = tok.charAt(0);
                  switch(c) {
                  case 'C':
                     colorSpace = tok.substring(1);
                     break;
                  case 'H':
                     h = Integer.parseInt(tok.substring(1));
                     break;
                  case 'W':
                     w = Integer.parseInt(tok.substring(1));
                  }
               }

               Logging.d("VideoReaderY4M", "Color space: " + colorSpace);
               if (!colorSpace.equals("420") && !colorSpace.equals("420mpeg2")) {
                  throw new IllegalArgumentException("Does not support any other color space than I420 or I420mpeg2");
               }

               if (w % 2 != 1 && h % 2 != 1) {
                  this.frameWidth = w;
                  this.frameHeight = h;
                  Logging.d("VideoReaderY4M", "frame dim: (" + w + ", " + h + ")");
                  return;
               }

               throw new IllegalArgumentException("Does not support odd width or height");
            }

            builder.append((char)c);
         }
      }

      public VideoFrame getNextFrame() {
         long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
         JavaI420Buffer buffer = JavaI420Buffer.allocate(this.frameWidth, this.frameHeight);
         ByteBuffer dataY = buffer.getDataY();
         ByteBuffer dataU = buffer.getDataU();
         ByteBuffer dataV = buffer.getDataV();
         int chromaHeight = (this.frameHeight + 1) / 2;
         int sizeY = this.frameHeight * buffer.getStrideY();
         int var10000 = chromaHeight * buffer.getStrideU();
         var10000 = chromaHeight * buffer.getStrideV();

         try {
            ByteBuffer frameDelim = ByteBuffer.allocate(FRAME_DELIMETER_LENGTH);
            if (this.mediaFileChannel.read(frameDelim) < FRAME_DELIMETER_LENGTH) {
               this.mediaFileChannel.position(this.videoStart);
               if (this.mediaFileChannel.read(frameDelim) < FRAME_DELIMETER_LENGTH) {
                  throw new RuntimeException("Error looping video");
               }
            }

            String frameDelimStr = new String(frameDelim.array(), Charset.forName("US-ASCII"));
            if (!frameDelimStr.equals("FRAME\n")) {
               throw new RuntimeException("Frames should be delimited by FRAME plus newline, found delimter was: '" + frameDelimStr + "'");
            }

            this.mediaFileChannel.read(dataY);
            this.mediaFileChannel.read(dataU);
            this.mediaFileChannel.read(dataV);
         } catch (IOException var13) {
            throw new RuntimeException(var13);
         }

         return new VideoFrame(buffer, 0, captureTimeNs);
      }

      public void close() {
         try {
            this.mediaFile.close();
         } catch (IOException var2) {
            Logging.e("VideoReaderY4M", "Problem closing file", var2);
         }

      }
   }

   private interface VideoReader {
      VideoFrame getNextFrame();

      void close();
   }
}
