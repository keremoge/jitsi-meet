package org.webrtc;

enum VideoCodecMimeType {
   VP8("video/x-vnd.on2.vp8"),
   VP9("video/x-vnd.on2.vp9"),
   H264("video/avc"),
   AV1("video/av01"),
   H265("video/hevc");

   private final String mimeType;

   private VideoCodecMimeType(String mimeType) {
      this.mimeType = mimeType;
   }

   String mimeType() {
      return this.mimeType;
   }

   // $FF: synthetic method
   private static VideoCodecMimeType[] $values() {
      return new VideoCodecMimeType[]{VP8, VP9, H264, AV1, H265};
   }
}
