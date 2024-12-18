package org.webrtc;

import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.os.Build.VERSION;
import android.view.Choreographer;
import androidx.annotation.GuardedBy;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public final class RenderSynchronizer {
   private static final String TAG = "RenderSynchronizer";
   private static final float DEFAULT_TARGET_FPS = 30.0F;
   private final Object lock;
   private final List<RenderSynchronizer.Listener> listeners;
   private final long targetFrameIntervalNanos;
   private final Handler mainThreadHandler;
   private Choreographer choreographer;
   @GuardedBy("lock")
   private boolean isListening;
   private boolean renderWindowOpen;
   private long lastRefreshTimeNanos;
   private long lastOpenedTimeNanos;

   public RenderSynchronizer(float targetFrameRateFps) {
      this.lock = new Object();
      this.listeners = new CopyOnWriteArrayList();
      this.targetFrameIntervalNanos = (long)Math.round((float)TimeUnit.SECONDS.toNanos(1L) / targetFrameRateFps);
      this.mainThreadHandler = new Handler(Looper.getMainLooper());
      this.mainThreadHandler.post(() -> {
         this.choreographer = Choreographer.getInstance();
      });
      Logging.d("RenderSynchronizer", "Created");
   }

   public RenderSynchronizer() {
      this(30.0F);
   }

   public void registerListener(RenderSynchronizer.Listener listener) {
      this.listeners.add(listener);
      synchronized(this.lock) {
         if (!this.isListening) {
            Logging.d("RenderSynchronizer", "First listener, subscribing to frame callbacks");
            this.isListening = true;
            this.mainThreadHandler.post(() -> {
               this.choreographer.postFrameCallback(this::onDisplayRefreshCycleBegin);
            });
         }

      }
   }

   public void removeListener(RenderSynchronizer.Listener listener) {
      this.listeners.remove(listener);
   }

   private void onDisplayRefreshCycleBegin(long refreshTimeNanos) {
      synchronized(this.lock) {
         if (this.listeners.isEmpty()) {
            Logging.d("RenderSynchronizer", "No listeners, unsubscribing to frame callbacks");
            this.isListening = false;
            return;
         }
      }

      this.choreographer.postFrameCallback(this::onDisplayRefreshCycleBegin);
      long lastOpenDeltaNanos = refreshTimeNanos - this.lastOpenedTimeNanos;
      long refreshDeltaNanos = refreshTimeNanos - this.lastRefreshTimeNanos;
      this.lastRefreshTimeNanos = refreshTimeNanos;
      if (Math.abs(lastOpenDeltaNanos - this.targetFrameIntervalNanos) < Math.abs(lastOpenDeltaNanos - this.targetFrameIntervalNanos + refreshDeltaNanos)) {
         this.lastOpenedTimeNanos = refreshTimeNanos;
         this.openRenderWindow();
      } else if (this.renderWindowOpen) {
         this.closeRenderWindow();
      }

   }

   private void traceRenderWindowChange() {
      if (VERSION.SDK_INT >= 29) {
         Trace.setCounter("RenderWindow", this.renderWindowOpen ? 1L : 0L);
      }

   }

   private void openRenderWindow() {
      this.renderWindowOpen = true;
      this.traceRenderWindowChange();
      Iterator var1 = this.listeners.iterator();

      while(var1.hasNext()) {
         RenderSynchronizer.Listener listener = (RenderSynchronizer.Listener)var1.next();
         listener.onRenderWindowOpen();
      }

   }

   private void closeRenderWindow() {
      this.renderWindowOpen = false;
      this.traceRenderWindowChange();
      Iterator var1 = this.listeners.iterator();

      while(var1.hasNext()) {
         RenderSynchronizer.Listener listener = (RenderSynchronizer.Listener)var1.next();
         listener.onRenderWindowClose();
      }

   }

   public interface Listener {
      void onRenderWindowOpen();

      void onRenderWindowClose();
   }
}
