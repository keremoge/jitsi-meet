package org.webrtc;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGLContext;

public interface EglBase {
   Object lock = new Object();
   int EGL_OPENGL_ES2_BIT = 4;
   int EGL_OPENGL_ES3_BIT = 64;
   int EGL_RECORDABLE_ANDROID = 12610;
   int[] CONFIG_PLAIN = configBuilder().createConfigAttributes();
   int[] CONFIG_RGBA = configBuilder().setHasAlphaChannel(true).createConfigAttributes();
   int[] CONFIG_PIXEL_BUFFER = configBuilder().setSupportsPixelBuffer(true).createConfigAttributes();
   int[] CONFIG_PIXEL_RGBA_BUFFER = configBuilder().setHasAlphaChannel(true).setSupportsPixelBuffer(true).createConfigAttributes();
   int[] CONFIG_RECORDABLE = configBuilder().setIsRecordable(true).createConfigAttributes();

   static EglBase.ConfigBuilder configBuilder() {
      return new EglBase.ConfigBuilder();
   }

   static int getOpenGlesVersionFromConfig(int[] configAttributes) {
      for(int i = 0; i < configAttributes.length - 1; ++i) {
         if (configAttributes[i] == 12352) {
            switch(configAttributes[i + 1]) {
            case 4:
               return 2;
            case 64:
               return 3;
            default:
               return 1;
            }
         }
      }

      return 1;
   }

   static EglBase create(EglBase.EglConnection eglConnection) {
      if (eglConnection == null) {
         return create();
      } else if (eglConnection instanceof EglBase14Impl.EglConnection) {
         return new EglBase14Impl((EglBase14Impl.EglConnection)eglConnection);
      } else if (eglConnection instanceof EglBase10Impl.EglConnection) {
         return new EglBase10Impl((EglBase10Impl.EglConnection)eglConnection);
      } else {
         throw new IllegalArgumentException("Unrecognized EglConnection");
      }
   }

   static EglBase create(@Nullable EglBase.Context sharedContext, int[] configAttributes) {
      if (sharedContext == null) {
         return createEgl14(configAttributes);
      } else if (sharedContext instanceof EglBase14.Context) {
         return createEgl14((EglBase14.Context)sharedContext, configAttributes);
      } else if (sharedContext instanceof EglBase10.Context) {
         return createEgl10((EglBase10.Context)sharedContext, configAttributes);
      } else {
         throw new IllegalArgumentException("Unrecognized Context");
      }
   }

   static EglBase create() {
      return create((EglBase.Context)null, CONFIG_PLAIN);
   }

   static EglBase create(EglBase.Context sharedContext) {
      return create(sharedContext, CONFIG_PLAIN);
   }

   static EglBase10 createEgl10(int[] configAttributes) {
      return new EglBase10Impl((EGLContext)null, configAttributes);
   }

   static EglBase10 createEgl10(EglBase10.Context sharedContext, int[] configAttributes) {
      return new EglBase10Impl(sharedContext == null ? null : sharedContext.getRawContext(), configAttributes);
   }

   static EglBase10 createEgl10(EGLContext sharedContext, int[] configAttributes) {
      return new EglBase10Impl(sharedContext, configAttributes);
   }

   static EglBase14 createEgl14(int[] configAttributes) {
      return new EglBase14Impl((android.opengl.EGLContext)null, configAttributes);
   }

   static EglBase14 createEgl14(EglBase14.Context sharedContext, int[] configAttributes) {
      return new EglBase14Impl(sharedContext == null ? null : sharedContext.getRawContext(), configAttributes);
   }

   static EglBase14 createEgl14(android.opengl.EGLContext sharedContext, int[] configAttributes) {
      return new EglBase14Impl(sharedContext, configAttributes);
   }

   void createSurface(Surface var1);

   void createSurface(SurfaceTexture var1);

   void createDummyPbufferSurface();

   void createPbufferSurface(int var1, int var2);

   EglBase.Context getEglBaseContext();

   boolean hasSurface();

   int surfaceWidth();

   int surfaceHeight();

   void releaseSurface();

   void release();

   void makeCurrent();

   void detachCurrent();

   void swapBuffers();

   void swapBuffers(long var1);

   public static class ConfigBuilder {
      private int openGlesVersion = 2;
      private boolean hasAlphaChannel;
      private boolean supportsPixelBuffer;
      private boolean isRecordable;

      public EglBase.ConfigBuilder setOpenGlesVersion(int version) {
         if (version >= 1 && version <= 3) {
            this.openGlesVersion = version;
            return this;
         } else {
            throw new IllegalArgumentException("OpenGL ES version " + version + " not supported");
         }
      }

      public EglBase.ConfigBuilder setHasAlphaChannel(boolean hasAlphaChannel) {
         this.hasAlphaChannel = hasAlphaChannel;
         return this;
      }

      public EglBase.ConfigBuilder setSupportsPixelBuffer(boolean supportsPixelBuffer) {
         this.supportsPixelBuffer = supportsPixelBuffer;
         return this;
      }

      public EglBase.ConfigBuilder setIsRecordable(boolean isRecordable) {
         this.isRecordable = isRecordable;
         return this;
      }

      public int[] createConfigAttributes() {
         ArrayList<Integer> list = new ArrayList();
         list.add(12324);
         list.add(8);
         list.add(12323);
         list.add(8);
         list.add(12322);
         list.add(8);
         if (this.hasAlphaChannel) {
            list.add(12321);
            list.add(8);
         }

         if (this.openGlesVersion == 2 || this.openGlesVersion == 3) {
            list.add(12352);
            list.add(this.openGlesVersion == 3 ? 64 : 4);
         }

         if (this.supportsPixelBuffer) {
            list.add(12339);
            list.add(1);
         }

         if (this.isRecordable) {
            list.add(12610);
            list.add(1);
         }

         list.add(12344);
         int[] res = new int[list.size()];

         for(int i = 0; i < list.size(); ++i) {
            res[i] = (Integer)list.get(i);
         }

         return res;
      }
   }

   public interface Context {
      long NO_CONTEXT = 0L;

      long getNativeEglContext();
   }

   public interface EglConnection extends RefCounted {
      static EglBase.EglConnection create(@Nullable EglBase.Context sharedContext, int[] configAttributes) {
         if (sharedContext == null) {
            return createEgl14(configAttributes);
         } else if (sharedContext instanceof EglBase14.Context) {
            return new EglBase14Impl.EglConnection(((EglBase14.Context)sharedContext).getRawContext(), configAttributes);
         } else if (sharedContext instanceof EglBase10.Context) {
            return new EglBase10Impl.EglConnection(((EglBase10.Context)sharedContext).getRawContext(), configAttributes);
         } else {
            throw new IllegalArgumentException("Unrecognized Context");
         }
      }

      static EglBase.EglConnection createEgl10(int[] configAttributes) {
         return new EglBase10Impl.EglConnection((EGLContext)null, configAttributes);
      }

      static EglBase.EglConnection createEgl14(int[] configAttributes) {
         return new EglBase14Impl.EglConnection((android.opengl.EGLContext)null, configAttributes);
      }
   }
}
