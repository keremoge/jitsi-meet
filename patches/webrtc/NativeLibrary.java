package org.webrtc;

class NativeLibrary {
   private static String TAG = "NativeLibrary";
   private static Object lock = new Object();
   private static boolean libraryLoaded;

   static void initialize(NativeLibraryLoader loader, String libraryName) {
      synchronized(lock) {
         if (libraryLoaded) {
            Logging.d(TAG, "Native library has already been loaded.");
         } else {
            Logging.d(TAG, "Loading native library: " + libraryName);
            libraryLoaded = loader.load(libraryName);
         }
      }
   }

   static boolean isLoaded() {
      synchronized(lock) {
         return libraryLoaded;
      }
   }

   static class DefaultLoader implements NativeLibraryLoader {
      public boolean load(String name) {
         Logging.d(NativeLibrary.TAG, "Loading library: " + name);
         System.loadLibrary(name);
         return true;
      }
   }
}
