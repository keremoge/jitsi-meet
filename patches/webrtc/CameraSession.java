package org.webrtc;

import android.content.Context;
import android.graphics.Matrix;
import android.view.WindowManager;

interface CameraSession {
   void stop();

   static int getDeviceOrientation(Context context) {
      WindowManager wm = (WindowManager)context.getSystemService("window");
      switch(wm.getDefaultDisplay().getRotation()) {
      case 0:
      default:
         return 0;
      case 1:
         return 90;
      case 2:
         return 180;
      case 3:
         return 270;
      }
   }

   static VideoFrame.TextureBuffer createTextureBufferWithModifiedTransformMatrix(TextureBufferImpl buffer, boolean mirror, int rotation) {
      Matrix transformMatrix = new Matrix();
      transformMatrix.preTranslate(0.5F, 0.5F);
      if (mirror) {
         transformMatrix.preScale(-1.0F, 1.0F);
      }

      transformMatrix.preRotate((float)rotation);
      transformMatrix.preTranslate(-0.5F, -0.5F);
      return buffer.applyTransformMatrix(transformMatrix, buffer.getWidth(), buffer.getHeight());
   }

   public interface Events {
      void onCameraOpening();

      void onCameraError(CameraSession var1, String var2);

      void onCameraDisconnected(CameraSession var1);

      void onCameraClosed(CameraSession var1);

      void onFrameCaptured(CameraSession var1, VideoFrame var2);
   }

   public interface CreateSessionCallback {
      void onDone(CameraSession var1);

      void onFailure(CameraSession.FailureType var1, String var2);
   }

   public static enum FailureType {
      ERROR,
      DISCONNECTED;

      // $FF: synthetic method
      private static CameraSession.FailureType[] $values() {
         return new CameraSession.FailureType[]{ERROR, DISCONNECTED};
      }
   }
}
