package org.webrtc;

import android.media.MediaCodecInfo;
import androidx.annotation.Nullable;

public class HardwareVideoDecoderFactory extends MediaCodecVideoDecoderFactory {
   private static final Predicate<MediaCodecInfo> defaultAllowedPredicate = new Predicate<MediaCodecInfo>() {
      public boolean test(MediaCodecInfo arg) {
         return MediaCodecUtils.isHardwareAccelerated(arg);
      }
   };

   /** @deprecated */
   @Deprecated
   public HardwareVideoDecoderFactory() {
      this((EglBase.Context)null);
   }

   public HardwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext) {
      this(sharedContext, (Predicate)null);
   }

   public HardwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext, @Nullable Predicate<MediaCodecInfo> codecAllowedPredicate) {
      super(sharedContext, codecAllowedPredicate == null ? defaultAllowedPredicate : codecAllowedPredicate.and(defaultAllowedPredicate));
   }
}
