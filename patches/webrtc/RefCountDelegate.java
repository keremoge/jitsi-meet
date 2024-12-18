package org.webrtc;

import androidx.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

class RefCountDelegate implements RefCounted {
   private final AtomicInteger refCount = new AtomicInteger(1);
   @Nullable
   private final Runnable releaseCallback;

   public RefCountDelegate(@Nullable Runnable releaseCallback) {
      this.releaseCallback = releaseCallback;
   }

   public void retain() {
      int updated_count = this.refCount.incrementAndGet();
      if (updated_count < 2) {
         throw new IllegalStateException("retain() called on an object with refcount < 1");
      }
   }

   public void release() {
      int updated_count = this.refCount.decrementAndGet();
      if (updated_count < 0) {
         throw new IllegalStateException("release() called on an object with refcount < 1");
      } else {
         if (updated_count == 0 && this.releaseCallback != null) {
            this.releaseCallback.run();
         }

      }
   }

   boolean safeRetain() {
      for(int currentRefCount = this.refCount.get(); currentRefCount != 0; currentRefCount = this.refCount.get()) {
         if (this.refCount.weakCompareAndSet(currentRefCount, currentRefCount + 1)) {
            return true;
         }
      }

      return false;
   }
}
