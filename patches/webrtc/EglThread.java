package org.webrtc;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

public class EglThread implements RenderSynchronizer.Listener {
   private final EglThread.ReleaseMonitor releaseMonitor;
   private final EglThread.HandlerWithExceptionCallbacks handler;
   private final EglBase.EglConnection eglConnection;
   private final RenderSynchronizer renderSynchronizer;
   private final List<EglThread.RenderUpdate> pendingRenderUpdates = new ArrayList();
   private boolean renderWindowOpen = true;

   public static EglThread create(@Nullable EglThread.ReleaseMonitor releaseMonitor, @Nullable EglBase.Context sharedContext, int[] configAttributes, @Nullable RenderSynchronizer renderSynchronizer) {
      HandlerThread renderThread = new HandlerThread("EglThread");
      renderThread.start();
      EglThread.HandlerWithExceptionCallbacks handler = new EglThread.HandlerWithExceptionCallbacks(renderThread.getLooper());
      EglBase.EglConnection eglConnection = (EglBase.EglConnection)ThreadUtils.invokeAtFrontUninterruptibly(handler, (Callable)(() -> {
         return sharedContext == null ? EglBase.EglConnection.createEgl10(configAttributes) : EglBase.EglConnection.create(sharedContext, configAttributes);
      }));
      return new EglThread(releaseMonitor != null ? releaseMonitor : (eglThread) -> {
         return true;
      }, handler, eglConnection, renderSynchronizer);
   }

   public static EglThread create(@Nullable EglThread.ReleaseMonitor releaseMonitor, @Nullable EglBase.Context sharedContext, int[] configAttributes) {
      return create(releaseMonitor, sharedContext, configAttributes, (RenderSynchronizer)null);
   }

   private EglThread(EglThread.ReleaseMonitor releaseMonitor, EglThread.HandlerWithExceptionCallbacks handler, EglBase.EglConnection eglConnection, RenderSynchronizer renderSynchronizer) {
      this.releaseMonitor = releaseMonitor;
      this.handler = handler;
      this.eglConnection = eglConnection;
      this.renderSynchronizer = renderSynchronizer;
      if (renderSynchronizer != null) {
         renderSynchronizer.registerListener(this);
      }

   }

   public void release() {
      if (this.releaseMonitor.onRelease(this)) {
         if (this.renderSynchronizer != null) {
            this.renderSynchronizer.removeListener(this);
         }

         EglThread.HandlerWithExceptionCallbacks var10000 = this.handler;
         EglBase.EglConnection var10001 = this.eglConnection;
         Objects.requireNonNull(var10001);
         var10000.post(var10001::release);
         this.handler.getLooper().quitSafely();
      }
   }

   public EglBase createEglBaseWithSharedConnection() {
      return EglBase.create(this.eglConnection);
   }

   public Handler getHandler() {
      return this.handler;
   }

   public void addExceptionCallback(Runnable callback) {
      this.handler.addExceptionCallback(callback);
   }

   public void removeExceptionCallback(Runnable callback) {
      this.handler.removeExceptionCallback(callback);
   }

   public void scheduleRenderUpdate(EglThread.RenderUpdate update) {
      if (this.renderWindowOpen) {
         update.update(true);
      } else {
         this.pendingRenderUpdates.add(update);
      }

   }

   public void onRenderWindowOpen() {
      this.handler.post(() -> {
         this.renderWindowOpen = true;
         Iterator var1 = this.pendingRenderUpdates.iterator();

         while(var1.hasNext()) {
            EglThread.RenderUpdate update = (EglThread.RenderUpdate)var1.next();
            update.update(false);
         }

         this.pendingRenderUpdates.clear();
      });
   }

   public void onRenderWindowClose() {
      this.handler.post(() -> {
         this.renderWindowOpen = false;
      });
   }

   private static class HandlerWithExceptionCallbacks extends Handler {
      private final Object callbackLock = new Object();
      @GuardedBy("callbackLock")
      private final List<Runnable> exceptionCallbacks = new ArrayList();

      public HandlerWithExceptionCallbacks(Looper looper) {
         super(looper);
      }

      public void dispatchMessage(Message msg) {
         try {
            super.dispatchMessage(msg);
         } catch (Exception var8) {
            Logging.e("EglThread", "Exception on EglThread", var8);
            synchronized(this.callbackLock) {
               Iterator var4 = this.exceptionCallbacks.iterator();

               while(var4.hasNext()) {
                  Runnable callback = (Runnable)var4.next();
                  callback.run();
               }

               throw var8;
            }
         }
      }

      public void addExceptionCallback(Runnable callback) {
         synchronized(this.callbackLock) {
            this.exceptionCallbacks.add(callback);
         }
      }

      public void removeExceptionCallback(Runnable callback) {
         synchronized(this.callbackLock) {
            this.exceptionCallbacks.remove(callback);
         }
      }
   }

   public interface ReleaseMonitor {
      boolean onRelease(EglThread var1);
   }

   public interface RenderUpdate {
      void update(boolean var1);
   }
}
