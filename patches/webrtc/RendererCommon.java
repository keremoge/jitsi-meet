package org.webrtc;

import android.graphics.Point;
import android.opengl.Matrix;
import android.view.View;
import android.view.View.MeasureSpec;

public class RendererCommon {
   private static float BALANCED_VISIBLE_FRACTION = 0.5625F;

   public static float[] getLayoutMatrix(boolean mirror, float videoAspectRatio, float displayAspectRatio) {
      float scaleX = 1.0F;
      float scaleY = 1.0F;
      if (displayAspectRatio > videoAspectRatio) {
         scaleY = videoAspectRatio / displayAspectRatio;
      } else {
         scaleX = displayAspectRatio / videoAspectRatio;
      }

      if (mirror) {
         scaleX *= -1.0F;
      }

      float[] matrix = new float[16];
      Matrix.setIdentityM(matrix, 0);
      Matrix.scaleM(matrix, 0, scaleX, scaleY, 1.0F);
      adjustOrigin(matrix);
      return matrix;
   }

   public static android.graphics.Matrix convertMatrixToAndroidGraphicsMatrix(float[] matrix4x4) {
      float[] values = new float[]{matrix4x4[0], matrix4x4[4], matrix4x4[12], matrix4x4[1], matrix4x4[5], matrix4x4[13], matrix4x4[3], matrix4x4[7], matrix4x4[15]};
      android.graphics.Matrix matrix = new android.graphics.Matrix();
      matrix.setValues(values);
      return matrix;
   }

   public static float[] convertMatrixFromAndroidGraphicsMatrix(android.graphics.Matrix matrix) {
      float[] values = new float[9];
      matrix.getValues(values);
      float[] matrix4x4 = new float[]{values[0], values[3], 0.0F, values[6], values[1], values[4], 0.0F, values[7], 0.0F, 0.0F, 1.0F, 0.0F, values[2], values[5], 0.0F, values[8]};
      return matrix4x4;
   }

   public static Point getDisplaySize(RendererCommon.ScalingType scalingType, float videoAspectRatio, int maxDisplayWidth, int maxDisplayHeight) {
      return getDisplaySize(convertScalingTypeToVisibleFraction(scalingType), videoAspectRatio, maxDisplayWidth, maxDisplayHeight);
   }

   private static void adjustOrigin(float[] matrix) {
      matrix[12] -= 0.5F * (matrix[0] + matrix[4]);
      matrix[13] -= 0.5F * (matrix[1] + matrix[5]);
      matrix[12] += 0.5F;
      matrix[13] += 0.5F;
   }

   private static float convertScalingTypeToVisibleFraction(RendererCommon.ScalingType scalingType) {
      switch(scalingType) {
      case SCALE_ASPECT_FIT:
         return 1.0F;
      case SCALE_ASPECT_FILL:
         return 0.0F;
      case SCALE_ASPECT_BALANCED:
         return BALANCED_VISIBLE_FRACTION;
      default:
         throw new IllegalArgumentException();
      }
   }

   public static Point getDisplaySize(float minVisibleFraction, float videoAspectRatio, int maxDisplayWidth, int maxDisplayHeight) {
      if (minVisibleFraction != 0.0F && videoAspectRatio != 0.0F) {
         int width = Math.min(maxDisplayWidth, Math.round((float)maxDisplayHeight / minVisibleFraction * videoAspectRatio));
         int height = Math.min(maxDisplayHeight, Math.round((float)maxDisplayWidth / minVisibleFraction / videoAspectRatio));
         return new Point(width, height);
      } else {
         return new Point(maxDisplayWidth, maxDisplayHeight);
      }
   }

   public static enum ScalingType {
      SCALE_ASPECT_FIT,
      SCALE_ASPECT_FILL,
      SCALE_ASPECT_BALANCED;

      // $FF: synthetic method
      private static RendererCommon.ScalingType[] $values() {
         return new RendererCommon.ScalingType[]{SCALE_ASPECT_FIT, SCALE_ASPECT_FILL, SCALE_ASPECT_BALANCED};
      }
   }

   public static class VideoLayoutMeasure {
      private float visibleFractionMatchOrientation;
      private float visibleFractionMismatchOrientation;

      public VideoLayoutMeasure() {
         this.visibleFractionMatchOrientation = RendererCommon.convertScalingTypeToVisibleFraction(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);
         this.visibleFractionMismatchOrientation = RendererCommon.convertScalingTypeToVisibleFraction(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);
      }

      public void setScalingType(RendererCommon.ScalingType scalingType) {
         this.setScalingType(scalingType, scalingType);
      }

      public void setScalingType(RendererCommon.ScalingType scalingTypeMatchOrientation, RendererCommon.ScalingType scalingTypeMismatchOrientation) {
         this.visibleFractionMatchOrientation = RendererCommon.convertScalingTypeToVisibleFraction(scalingTypeMatchOrientation);
         this.visibleFractionMismatchOrientation = RendererCommon.convertScalingTypeToVisibleFraction(scalingTypeMismatchOrientation);
      }

      public void setVisibleFraction(float visibleFractionMatchOrientation, float visibleFractionMismatchOrientation) {
         this.visibleFractionMatchOrientation = visibleFractionMatchOrientation;
         this.visibleFractionMismatchOrientation = visibleFractionMismatchOrientation;
      }

      public Point measure(int widthSpec, int heightSpec, int frameWidth, int frameHeight) {
         int maxWidth = View.getDefaultSize(Integer.MAX_VALUE, widthSpec);
         int maxHeight = View.getDefaultSize(Integer.MAX_VALUE, heightSpec);
         if (frameWidth != 0 && frameHeight != 0 && maxWidth != 0 && maxHeight != 0) {
            float frameAspect = (float)frameWidth / (float)frameHeight;
            float displayAspect = (float)maxWidth / (float)maxHeight;
            float visibleFraction = frameAspect > 1.0F == displayAspect > 1.0F ? this.visibleFractionMatchOrientation : this.visibleFractionMismatchOrientation;
            Point layoutSize = RendererCommon.getDisplaySize(visibleFraction, frameAspect, maxWidth, maxHeight);
            if (MeasureSpec.getMode(widthSpec) == 1073741824) {
               layoutSize.x = maxWidth;
            }

            if (MeasureSpec.getMode(heightSpec) == 1073741824) {
               layoutSize.y = maxHeight;
            }

            return layoutSize;
         } else {
            return new Point(maxWidth, maxHeight);
         }
      }
   }

   public interface GlDrawer {
      void drawOes(int var1, float[] var2, int var3, int var4, int var5, int var6, int var7, int var8);

      void drawRgb(int var1, float[] var2, int var3, int var4, int var5, int var6, int var7, int var8);

      void drawYuv(int[] var1, float[] var2, int var3, int var4, int var5, int var6, int var7, int var8);

      void release();
   }

   public interface RendererEvents {
      void onFirstFrameRendered();

      void onFrameResolutionChanged(int var1, int var2, int var3);
   }
}
