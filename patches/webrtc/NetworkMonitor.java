package org.webrtc;

import android.content.Context;
import android.os.Build.VERSION;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NetworkMonitor {
   private static final String TAG = "NetworkMonitor";
   private NetworkChangeDetectorFactory networkChangeDetectorFactory = new NetworkChangeDetectorFactory() {
      public NetworkChangeDetector create(NetworkChangeDetector.Observer observer, Context context) {
         return new NetworkMonitorAutoDetect(observer, context);
      }
   };
   private final ArrayList<Long> nativeNetworkObservers = new ArrayList();
   private final ArrayList<NetworkMonitor.NetworkObserver> networkObservers = new ArrayList();
   private final Object networkChangeDetectorLock = new Object();
   @Nullable
   private NetworkChangeDetector networkChangeDetector;
   private int numObservers = 0;
   private volatile NetworkChangeDetector.ConnectionType currentConnectionType;

   private NetworkMonitor() {
      this.currentConnectionType = NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN;
   }

   public void setNetworkChangeDetectorFactory(NetworkChangeDetectorFactory factory) {
      assertIsTrue(this.numObservers == 0);
      this.networkChangeDetectorFactory = factory;
   }

   /** @deprecated */
   @Deprecated
   public static void init(Context context) {
   }

   @CalledByNative
   public static NetworkMonitor getInstance() {
      return NetworkMonitor.InstanceHolder.instance;
   }

   private static void assertIsTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected to be true");
      }
   }

   public void startMonitoring(Context applicationContext, String fieldTrialsString) {
      synchronized(this.networkChangeDetectorLock) {
         ++this.numObservers;
         if (this.networkChangeDetector == null) {
            this.networkChangeDetector = this.createNetworkChangeDetector(applicationContext, fieldTrialsString);
         }

         this.currentConnectionType = this.networkChangeDetector.getCurrentConnectionType();
      }
   }

   /** @deprecated */
   @Deprecated
   public void startMonitoring(Context applicationContext) {
      this.startMonitoring(applicationContext, "");
   }

   /** @deprecated */
   @Deprecated
   public void startMonitoring() {
      this.startMonitoring(ContextUtils.getApplicationContext(), "");
   }

   @CalledByNative
   private void startMonitoring(@Nullable Context applicationContext, long nativeObserver, String fieldTrialsString) {
      Logging.d("NetworkMonitor", "Start monitoring with native observer " + nativeObserver + " fieldTrialsString: " + fieldTrialsString);
      this.startMonitoring(applicationContext != null ? applicationContext : ContextUtils.getApplicationContext(), fieldTrialsString);
      synchronized(this.nativeNetworkObservers) {
         this.nativeNetworkObservers.add(nativeObserver);
      }

      this.updateObserverActiveNetworkList(nativeObserver);
      this.notifyObserversOfConnectionTypeChange(this.currentConnectionType);
   }

   public void stopMonitoring() {
      synchronized(this.networkChangeDetectorLock) {
         if (--this.numObservers == 0) {
            this.networkChangeDetector.destroy();
            this.networkChangeDetector = null;
         }

      }
   }

   @CalledByNative
   private void stopMonitoring(long nativeObserver) {
      Logging.d("NetworkMonitor", "Stop monitoring with native observer " + nativeObserver);
      this.stopMonitoring();
      synchronized(this.nativeNetworkObservers) {
         this.nativeNetworkObservers.remove(nativeObserver);
      }
   }

   @CalledByNative
   private boolean networkBindingSupported() {
      synchronized(this.networkChangeDetectorLock) {
         return this.networkChangeDetector != null && this.networkChangeDetector.supportNetworkCallback();
      }
   }

   @CalledByNative
   private static int androidSdkInt() {
      return VERSION.SDK_INT;
   }

   private NetworkChangeDetector.ConnectionType getCurrentConnectionType() {
      return this.currentConnectionType;
   }

   private NetworkChangeDetector createNetworkChangeDetector(Context appContext, final String fieldTrialsString) {
      return this.networkChangeDetectorFactory.create(new NetworkChangeDetector.Observer() {
         public void onConnectionTypeChanged(NetworkChangeDetector.ConnectionType newConnectionType) {
            NetworkMonitor.this.updateCurrentConnectionType(newConnectionType);
         }

         public void onNetworkConnect(NetworkChangeDetector.NetworkInformation networkInfo) {
            NetworkMonitor.this.notifyObserversOfNetworkConnect(networkInfo);
         }

         public void onNetworkDisconnect(long networkHandle) {
            NetworkMonitor.this.notifyObserversOfNetworkDisconnect(networkHandle);
         }

         public void onNetworkPreference(List<NetworkChangeDetector.ConnectionType> types, int preference) {
            NetworkMonitor.this.notifyObserversOfNetworkPreference(types, preference);
         }

         public String getFieldTrialsString() {
            return fieldTrialsString;
         }
      }, appContext);
   }

   private void updateCurrentConnectionType(NetworkChangeDetector.ConnectionType newConnectionType) {
      this.currentConnectionType = newConnectionType;
      this.notifyObserversOfConnectionTypeChange(newConnectionType);
   }

   private void notifyObserversOfConnectionTypeChange(NetworkChangeDetector.ConnectionType newConnectionType) {
      List<Long> nativeObservers = this.getNativeNetworkObserversSync();
      Iterator var3 = nativeObservers.iterator();

      while(var3.hasNext()) {
         Long nativeObserver = (Long)var3.next();
         this.nativeNotifyConnectionTypeChanged(nativeObserver);
      }

      ArrayList javaObservers;
      synchronized(this.networkObservers) {
         javaObservers = new ArrayList(this.networkObservers);
      }

      Iterator var8 = javaObservers.iterator();

      while(var8.hasNext()) {
         NetworkMonitor.NetworkObserver observer = (NetworkMonitor.NetworkObserver)var8.next();
         observer.onConnectionTypeChanged(newConnectionType);
      }

   }

   private void notifyObserversOfNetworkConnect(NetworkChangeDetector.NetworkInformation networkInfo) {
      List<Long> nativeObservers = this.getNativeNetworkObserversSync();
      Iterator var3 = nativeObservers.iterator();

      while(var3.hasNext()) {
         Long nativeObserver = (Long)var3.next();
         this.nativeNotifyOfNetworkConnect(nativeObserver, networkInfo);
      }

   }

   private void notifyObserversOfNetworkDisconnect(long networkHandle) {
      List<Long> nativeObservers = this.getNativeNetworkObserversSync();
      Iterator var4 = nativeObservers.iterator();

      while(var4.hasNext()) {
         Long nativeObserver = (Long)var4.next();
         this.nativeNotifyOfNetworkDisconnect(nativeObserver, networkHandle);
      }

   }

   private void notifyObserversOfNetworkPreference(List<NetworkChangeDetector.ConnectionType> types, int preference) {
      List<Long> nativeObservers = this.getNativeNetworkObserversSync();
      Iterator var4 = types.iterator();

      while(var4.hasNext()) {
         NetworkChangeDetector.ConnectionType type = (NetworkChangeDetector.ConnectionType)var4.next();
         Iterator var6 = nativeObservers.iterator();

         while(var6.hasNext()) {
            Long nativeObserver = (Long)var6.next();
            this.nativeNotifyOfNetworkPreference(nativeObserver, type, preference);
         }
      }

   }

   private void updateObserverActiveNetworkList(long nativeObserver) {
      List networkInfoList;
      synchronized(this.networkChangeDetectorLock) {
         networkInfoList = this.networkChangeDetector == null ? null : this.networkChangeDetector.getActiveNetworkList();
      }

      if (networkInfoList != null) {
         NetworkChangeDetector.NetworkInformation[] networkInfos = new NetworkChangeDetector.NetworkInformation[networkInfoList.size()];
         networkInfos = (NetworkChangeDetector.NetworkInformation[])networkInfoList.toArray(networkInfos);
         this.nativeNotifyOfActiveNetworkList(nativeObserver, networkInfos);
      }
   }

   private List<Long> getNativeNetworkObserversSync() {
      synchronized(this.nativeNetworkObservers) {
         return new ArrayList(this.nativeNetworkObservers);
      }
   }

   /** @deprecated */
   @Deprecated
   public static void addNetworkObserver(NetworkMonitor.NetworkObserver observer) {
      getInstance().addObserver(observer);
   }

   public void addObserver(NetworkMonitor.NetworkObserver observer) {
      synchronized(this.networkObservers) {
         this.networkObservers.add(observer);
      }
   }

   /** @deprecated */
   @Deprecated
   public static void removeNetworkObserver(NetworkMonitor.NetworkObserver observer) {
      getInstance().removeObserver(observer);
   }

   public void removeObserver(NetworkMonitor.NetworkObserver observer) {
      synchronized(this.networkObservers) {
         this.networkObservers.remove(observer);
      }
   }

   public static boolean isOnline() {
      NetworkChangeDetector.ConnectionType connectionType = getInstance().getCurrentConnectionType();
      return connectionType != NetworkChangeDetector.ConnectionType.CONNECTION_NONE;
   }

   private native void nativeNotifyConnectionTypeChanged(long var1);

   private native void nativeNotifyOfNetworkConnect(long var1, NetworkChangeDetector.NetworkInformation var3);

   private native void nativeNotifyOfNetworkDisconnect(long var1, long var3);

   private native void nativeNotifyOfActiveNetworkList(long var1, NetworkChangeDetector.NetworkInformation[] var3);

   private native void nativeNotifyOfNetworkPreference(long var1, NetworkChangeDetector.ConnectionType var3, int var4);

   @Nullable
   NetworkChangeDetector getNetworkChangeDetector() {
      synchronized(this.networkChangeDetectorLock) {
         return this.networkChangeDetector;
      }
   }

   int getNumObservers() {
      synchronized(this.networkChangeDetectorLock) {
         return this.numObservers;
      }
   }

   static NetworkMonitorAutoDetect createAndSetAutoDetectForTest(Context context, String fieldTrialsString) {
      NetworkMonitor networkMonitor = getInstance();
      NetworkChangeDetector networkChangeDetector = networkMonitor.createNetworkChangeDetector(context, fieldTrialsString);
      networkMonitor.networkChangeDetector = networkChangeDetector;
      return (NetworkMonitorAutoDetect)networkChangeDetector;
   }

   private static class InstanceHolder {
      static final NetworkMonitor instance = new NetworkMonitor();
   }

   public interface NetworkObserver {
      void onConnectionTypeChanged(NetworkChangeDetector.ConnectionType var1);
   }
}
