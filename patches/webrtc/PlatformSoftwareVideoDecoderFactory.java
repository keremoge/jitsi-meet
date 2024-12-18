package org.webrtc;

import android.media.MediaCodecInfo;
import androidx.annotation.Nullable;

public class PlatformSoftwareVideoDecoderFactory extends MediaCodecVideoDecoderFactory {
   private static final Predicate<MediaCodecInfo> defaultAllowedPredicate = new Predicate<MediaCodecInfo>() {
      public boolean test(MediaCodecInfo arg) {
         return MediaCodecUtils.isSoftwareOnly(arg);
      }
   };

   public PlatformSoftwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext) {
      super(sharedContext, defaultAllowedPredicate);
   }
}
