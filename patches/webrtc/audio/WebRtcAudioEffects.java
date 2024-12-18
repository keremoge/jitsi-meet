package org.webrtc.audio;

import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.NoiseSuppressor;
import android.media.audiofx.AudioEffect.Descriptor;
import androidx.annotation.Nullable;
import java.util.UUID;
import org.webrtc.Logging;

class WebRtcAudioEffects {
   private static final boolean DEBUG = false;
   private static final String TAG = "WebRtcAudioEffectsExternal";
   private static final UUID AOSP_ACOUSTIC_ECHO_CANCELER = UUID.fromString("bb392ec0-8d4d-11e0-a896-0002a5d5c51b");
   private static final UUID AOSP_NOISE_SUPPRESSOR = UUID.fromString("c06c8400-8e06-11e0-9cb6-0002a5d5c51b");
   @Nullable
   private static Descriptor[] cachedEffects;
   @Nullable
   private AcousticEchoCanceler aec;
   @Nullable
   private NoiseSuppressor ns;
   private boolean shouldEnableAec;
   private boolean shouldEnableNs;

   public static boolean isAcousticEchoCancelerSupported() {
      return isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_AEC, AOSP_ACOUSTIC_ECHO_CANCELER);
   }

   public static boolean isNoiseSuppressorSupported() {
      return isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_NS, AOSP_NOISE_SUPPRESSOR);
   }

   public WebRtcAudioEffects() {
      Logging.d("WebRtcAudioEffectsExternal", "ctor" + WebRtcAudioUtils.getThreadInfo());
   }

   public boolean setAEC(boolean enable) {
      Logging.d("WebRtcAudioEffectsExternal", "setAEC(" + enable + ")");
      if (!isAcousticEchoCancelerSupported()) {
         Logging.w("WebRtcAudioEffectsExternal", "Platform AEC is not supported");
         this.shouldEnableAec = false;
         return false;
      } else if (this.aec != null && enable != this.shouldEnableAec) {
         Logging.e("WebRtcAudioEffectsExternal", "Platform AEC state can't be modified while recording");
         return false;
      } else {
         this.shouldEnableAec = enable;
         return true;
      }
   }

   public boolean setNS(boolean enable) {
      Logging.d("WebRtcAudioEffectsExternal", "setNS(" + enable + ")");
      if (!isNoiseSuppressorSupported()) {
         Logging.w("WebRtcAudioEffectsExternal", "Platform NS is not supported");
         this.shouldEnableNs = false;
         return false;
      } else if (this.ns != null && enable != this.shouldEnableNs) {
         Logging.e("WebRtcAudioEffectsExternal", "Platform NS state can't be modified while recording");
         return false;
      } else {
         this.shouldEnableNs = enable;
         return true;
      }
   }

   public boolean toggleNS(boolean enable) {
      if (this.ns == null) {
         Logging.e("WebRtcAudioEffectsExternal", "Attempting to enable or disable nonexistent NoiseSuppressor.");
         return false;
      } else {
         Logging.d("WebRtcAudioEffectsExternal", "toggleNS(" + enable + ")");
         boolean toggling_succeeded = this.ns.setEnabled(enable) == 0;
         return toggling_succeeded;
      }
   }

   public void enable(int audioSession) {
      Logging.d("WebRtcAudioEffectsExternal", "enable(audioSession=" + audioSession + ")");
      assertTrue(this.aec == null);
      assertTrue(this.ns == null);
      boolean enabled;
      boolean enable;
      if (isAcousticEchoCancelerSupported()) {
         this.aec = AcousticEchoCanceler.create(audioSession);
         if (this.aec != null) {
            enabled = this.aec.getEnabled();
            enable = this.shouldEnableAec && isAcousticEchoCancelerSupported();
            if (this.aec.setEnabled(enable) != 0) {
               Logging.e("WebRtcAudioEffectsExternal", "Failed to set the AcousticEchoCanceler state");
            }

            Logging.d("WebRtcAudioEffectsExternal", "AcousticEchoCanceler: was " + (enabled ? "enabled" : "disabled") + ", enable: " + enable + ", is now: " + (this.aec.getEnabled() ? "enabled" : "disabled"));
         } else {
            Logging.e("WebRtcAudioEffectsExternal", "Failed to create the AcousticEchoCanceler instance");
         }
      }

      if (isNoiseSuppressorSupported()) {
         this.ns = NoiseSuppressor.create(audioSession);
         if (this.ns != null) {
            enabled = this.ns.getEnabled();
            enable = this.shouldEnableNs && isNoiseSuppressorSupported();
            if (this.ns.setEnabled(enable) != 0) {
               Logging.e("WebRtcAudioEffectsExternal", "Failed to set the NoiseSuppressor state");
            }

            Logging.d("WebRtcAudioEffectsExternal", "NoiseSuppressor: was " + (enabled ? "enabled" : "disabled") + ", enable: " + enable + ", is now: " + (this.ns.getEnabled() ? "enabled" : "disabled"));
         } else {
            Logging.e("WebRtcAudioEffectsExternal", "Failed to create the NoiseSuppressor instance");
         }
      }

   }

   public void release() {
      Logging.d("WebRtcAudioEffectsExternal", "release");
      if (this.aec != null) {
         this.aec.release();
         this.aec = null;
      }

      if (this.ns != null) {
         this.ns.release();
         this.ns = null;
      }

   }

   private boolean effectTypeIsVoIP(UUID type) {
      return AudioEffect.EFFECT_TYPE_AEC.equals(type) && isAcousticEchoCancelerSupported() || AudioEffect.EFFECT_TYPE_NS.equals(type) && isNoiseSuppressorSupported();
   }

   private static void assertTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected condition to be true");
      }
   }

   @Nullable
   private static Descriptor[] getAvailableEffects() {
      if (cachedEffects != null) {
         return cachedEffects;
      } else {
         cachedEffects = AudioEffect.queryEffects();
         return cachedEffects;
      }
   }

   private static boolean isEffectTypeAvailable(UUID effectType, UUID blockListedUuid) {
      Descriptor[] effects = getAvailableEffects();
      if (effects == null) {
         return false;
      } else {
         Descriptor[] var3 = effects;
         int var4 = effects.length;

         for(int var5 = 0; var5 < var4; ++var5) {
            Descriptor d = var3[var5];
            if (d.type.equals(effectType)) {
               return !d.uuid.equals(blockListedUuid);
            }
         }

         return false;
      }
   }
}
