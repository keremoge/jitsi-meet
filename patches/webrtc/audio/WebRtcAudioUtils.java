package org.webrtc.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Build.VERSION;
import java.util.Arrays;
import org.webrtc.Logging;

public final class WebRtcAudioUtils {
   private static final String TAG = "WebRtcAudioUtilsExternal";

   public static String getThreadInfo() {
      Thread current = Thread.currentThread();
      String var10000 = current.getName();
      return "@[name=" + var10000 + ", id=" + current.getId() + "]";
   }

   public static boolean runningOnEmulator() {
      return Build.HARDWARE.equals("goldfish") || Build.HARDWARE.equals("ranchu");
   }

   private static void logDeviceInfo(String tag) {
      Logging.d(tag, "Android SDK: " + VERSION.SDK_INT + ", Release: " + VERSION.RELEASE + ", Brand: " + Build.BRAND + ", Device: " + Build.DEVICE + ", Id: " + Build.ID + ", Hardware: " + Build.HARDWARE + ", Manufacturer: " + Build.MANUFACTURER + ", Model: " + Build.MODEL + ", Product: " + Build.PRODUCT);
   }

   public static void logAudioState(String tag, Context context, AudioManager audioManager) {
      logDeviceInfo(tag);
      logAudioStateBasic(tag, context, audioManager);
      logAudioStateVolume(tag, audioManager);
      logAudioDeviceInfo(tag, audioManager);
   }

   public static String deviceTypeToString(int type) {
      switch(type) {
      case 0:
         return "TYPE_UNKNOWN";
      case 1:
         return "TYPE_BUILTIN_EARPIECE";
      case 2:
         return "TYPE_BUILTIN_SPEAKER";
      case 3:
         return "TYPE_WIRED_HEADSET";
      case 4:
         return "TYPE_WIRED_HEADPHONES";
      case 5:
         return "TYPE_LINE_ANALOG";
      case 6:
         return "TYPE_LINE_DIGITAL";
      case 7:
         return "TYPE_BLUETOOTH_SCO";
      case 8:
         return "TYPE_BLUETOOTH_A2DP";
      case 9:
         return "TYPE_HDMI";
      case 10:
         return "TYPE_HDMI_ARC";
      case 11:
         return "TYPE_USB_DEVICE";
      case 12:
         return "TYPE_USB_ACCESSORY";
      case 13:
         return "TYPE_DOCK";
      case 14:
         return "TYPE_FM";
      case 15:
         return "TYPE_BUILTIN_MIC";
      case 16:
         return "TYPE_FM_TUNER";
      case 17:
         return "TYPE_TV_TUNER";
      case 18:
         return "TYPE_TELEPHONY";
      case 19:
         return "TYPE_AUX_LINE";
      case 20:
         return "TYPE_IP";
      case 21:
         return "TYPE_BUS";
      case 22:
         return "TYPE_USB_HEADSET";
      case 23:
         return "TYPE_HEARING_AID";
      case 24:
         return "TYPE_BUILTIN_SPEAKER_SAFE";
      case 25:
         return "TYPE_REMOTE_SUBMIX";
      case 26:
         return "TYPE_BLE_HEADSET";
      case 27:
         return "TYPE_BLE_SPEAKER";
      case 28:
      default:
         return "TYPE_UNKNOWN(" + type + ")";
      case 29:
         return "TYPE_HDMI_EARC";
      case 30:
         return "TYPE_BLE_BROADCAST";
      case 31:
         return "TYPE_DOCK_ANALOG";
      }
   }

   public static String audioSourceToString(int source) {
      switch(source) {
      case 0:
         return "DEFAULT";
      case 1:
         return "MIC";
      case 2:
         return "VOICE_UPLINK";
      case 3:
         return "VOICE_DOWNLINK";
      case 4:
         return "VOICE_CALL";
      case 5:
         return "CAMCORDER";
      case 6:
         return "VOICE_RECOGNITION";
      case 7:
         return "VOICE_COMMUNICATION";
      case 8:
      default:
         return "INVALID";
      case 9:
         return "UNPROCESSED";
      case 10:
         return "VOICE_PERFORMANCE";
      }
   }

   public static String channelMaskToString(int mask) {
      switch(mask) {
      case 12:
         return "IN_STEREO";
      case 16:
         return "IN_MONO";
      default:
         return "INVALID";
      }
   }

   @TargetApi(24)
   public static String audioEncodingToString(int enc) {
      switch(enc) {
      case 0:
         return "INVALID";
      case 1:
      default:
         return "Invalid encoding: " + enc;
      case 2:
         return "PCM_16BIT";
      case 3:
         return "PCM_8BIT";
      case 4:
         return "PCM_FLOAT";
      case 5:
         return "AC3";
      case 6:
         return "AC3";
      case 7:
         return "DTS";
      case 8:
         return "DTS_HD";
      case 9:
         return "MP3";
      }
   }

   private static void logAudioStateBasic(String tag, Context context, AudioManager audioManager) {
      String var10001 = modeToString(audioManager.getMode());
      Logging.d(tag, "Audio State: audio mode: " + var10001 + ", has mic: " + hasMicrophone(context) + ", mic muted: " + audioManager.isMicrophoneMute() + ", music active: " + audioManager.isMusicActive() + ", speakerphone: " + audioManager.isSpeakerphoneOn() + ", BT SCO: " + audioManager.isBluetoothScoOn());
   }

   private static void logAudioStateVolume(String tag, AudioManager audioManager) {
      int[] streams = new int[]{0, 3, 2, 4, 5, 1};
      Logging.d(tag, "Audio State: ");
      boolean fixedVolume = audioManager.isVolumeFixed();
      Logging.d(tag, "  fixed volume=" + fixedVolume);
      if (!fixedVolume) {
         int[] var4 = streams;
         int var5 = streams.length;

         for(int var6 = 0; var6 < var5; ++var6) {
            int stream = var4[var6];
            StringBuilder info = new StringBuilder();
            info.append("  " + streamTypeToString(stream) + ": ");
            info.append("volume=").append(audioManager.getStreamVolume(stream));
            info.append(", max=").append(audioManager.getStreamMaxVolume(stream));
            if (VERSION.SDK_INT >= 23) {
               info.append(", muted=").append(audioManager.isStreamMute(stream));
            }

            Logging.d(tag, info.toString());
         }
      }

   }

   private static void logAudioDeviceInfo(String tag, AudioManager audioManager) {
      if (VERSION.SDK_INT >= 23) {
         AudioDeviceInfo[] devices = audioManager.getDevices(3);
         if (devices.length != 0) {
            Logging.d(tag, "Audio Devices: ");
            AudioDeviceInfo[] var3 = devices;
            int var4 = devices.length;

            for(int var5 = 0; var5 < var4; ++var5) {
               AudioDeviceInfo device = var3[var5];
               StringBuilder info = new StringBuilder();
               info.append("  ").append(deviceTypeToString(device.getType()));
               info.append(device.isSource() ? "(in): " : "(out): ");
               if (device.getChannelCounts().length > 0) {
                  info.append("channels=").append(Arrays.toString(device.getChannelCounts()));
                  info.append(", ");
               }

               if (device.getEncodings().length > 0) {
                  info.append("encodings=").append(Arrays.toString(device.getEncodings()));
                  info.append(", ");
               }

               if (device.getSampleRates().length > 0) {
                  info.append("sample rates=").append(Arrays.toString(device.getSampleRates()));
                  info.append(", ");
               }

               info.append("id=").append(device.getId());
               Logging.d(tag, info.toString());
            }

         }
      }
   }

   static String modeToString(int mode) {
      switch(mode) {
      case 0:
         return "MODE_NORMAL";
      case 1:
         return "MODE_RINGTONE";
      case 2:
         return "MODE_IN_CALL";
      case 3:
         return "MODE_IN_COMMUNICATION";
      default:
         return "MODE_INVALID";
      }
   }

   private static String streamTypeToString(int stream) {
      switch(stream) {
      case 0:
         return "STREAM_VOICE_CALL";
      case 1:
         return "STREAM_SYSTEM";
      case 2:
         return "STREAM_RING";
      case 3:
         return "STREAM_MUSIC";
      case 4:
         return "STREAM_ALARM";
      case 5:
         return "STREAM_NOTIFICATION";
      default:
         return "STREAM_INVALID";
      }
   }

   private static boolean hasMicrophone(Context context) {
      return context.getPackageManager().hasSystemFeature("android.hardware.microphone");
   }
}
