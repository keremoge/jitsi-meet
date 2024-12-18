package org.webrtc;

import java.util.List;
import java.util.Map;

public class RtpCapabilities {
   public List<RtpCapabilities.CodecCapability> codecs;
   public List<RtpCapabilities.HeaderExtensionCapability> headerExtensions;

   @CalledByNative
   RtpCapabilities(List<RtpCapabilities.CodecCapability> codecs, List<RtpCapabilities.HeaderExtensionCapability> headerExtensions) {
      this.headerExtensions = headerExtensions;
      this.codecs = codecs;
   }

   @CalledByNative
   public List<RtpCapabilities.HeaderExtensionCapability> getHeaderExtensions() {
      return this.headerExtensions;
   }

   @CalledByNative
   List<RtpCapabilities.CodecCapability> getCodecs() {
      return this.codecs;
   }

   public static class HeaderExtensionCapability {
      private final String uri;
      private final int preferredId;
      private final boolean preferredEncrypted;

      @CalledByNative("HeaderExtensionCapability")
      HeaderExtensionCapability(String uri, int preferredId, boolean preferredEncrypted) {
         this.uri = uri;
         this.preferredId = preferredId;
         this.preferredEncrypted = preferredEncrypted;
      }

      @CalledByNative("HeaderExtensionCapability")
      public String getUri() {
         return this.uri;
      }

      @CalledByNative("HeaderExtensionCapability")
      public int getPreferredId() {
         return this.preferredId;
      }

      @CalledByNative("HeaderExtensionCapability")
      public boolean getPreferredEncrypted() {
         return this.preferredEncrypted;
      }
   }

   public static class CodecCapability {
      public int preferredPayloadType;
      public String name;
      public MediaStreamTrack.MediaType kind;
      public Integer clockRate;
      public Integer numChannels;
      public Map<String, String> parameters;
      public String mimeType;

      public CodecCapability() {
      }

      @CalledByNative("CodecCapability")
      CodecCapability(int preferredPayloadType, String name, MediaStreamTrack.MediaType kind, Integer clockRate, Integer numChannels, String mimeType, Map<String, String> parameters) {
         this.preferredPayloadType = preferredPayloadType;
         this.name = name;
         this.kind = kind;
         this.clockRate = clockRate;
         this.numChannels = numChannels;
         this.parameters = parameters;
         this.mimeType = mimeType;
      }

      @CalledByNative("CodecCapability")
      int getPreferredPayloadType() {
         return this.preferredPayloadType;
      }

      @CalledByNative("CodecCapability")
      String getName() {
         return this.name;
      }

      @CalledByNative("CodecCapability")
      MediaStreamTrack.MediaType getKind() {
         return this.kind;
      }

      @CalledByNative("CodecCapability")
      Integer getClockRate() {
         return this.clockRate;
      }

      @CalledByNative("CodecCapability")
      Integer getNumChannels() {
         return this.numChannels;
      }

      @CalledByNative("CodecCapability")
      Map getParameters() {
         return this.parameters;
      }
   }
}
