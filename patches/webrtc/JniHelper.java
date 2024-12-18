package org.webrtc;

import java.io.UnsupportedEncodingException;
import java.util.Map.Entry;

class JniHelper {
   @CalledByNative
   static byte[] getStringBytes(String s) {
      try {
         return s.getBytes("ISO-8859-1");
      } catch (UnsupportedEncodingException var2) {
         throw new RuntimeException("ISO-8859-1 is unsupported");
      }
   }

   @CalledByNative
   static Object getStringClass() {
      return String.class;
   }

   @CalledByNative
   static Object getKey(Entry entry) {
      return entry.getKey();
   }

   @CalledByNative
   static Object getValue(Entry entry) {
      return entry.getValue();
   }
}
