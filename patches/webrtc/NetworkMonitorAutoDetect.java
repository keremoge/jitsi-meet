package org.webrtc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.NetworkRequest.Builder;
import android.net.wifi.WifiInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Build.VERSION;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class NetworkMonitorAutoDetect extends BroadcastReceiver implements NetworkChangeDetector {
   private static final long INVALID_NET_ID = -1L;
   private static final String TAG = "NetworkMonitorAutoDetect";
   private final NetworkChangeDetector.Observer observer;
   private final IntentFilter intentFilter;
   private final Context context;
   @Nullable
   private final NetworkCallback mobileNetworkCallback;
   @Nullable
   private final NetworkCallback allNetworkCallback;
   private NetworkMonitorAutoDetect.ConnectivityManagerDelegate connectivityManagerDelegate;
   private NetworkMonitorAutoDetect.WifiManagerDelegate wifiManagerDelegate;
   private NetworkMonitorAutoDetect.WifiDirectManagerDelegate wifiDirectManagerDelegate;
   private static boolean includeWifiDirect;
   @GuardedBy("availableNetworks")
   final Set<Network> availableNetworks = new HashSet();
   private boolean isRegistered;
   private NetworkChangeDetector.ConnectionType connectionType;
   private String wifiSSID;

   @SuppressLint({"NewApi"})
   public NetworkMonitorAutoDetect(NetworkChangeDetector.Observer observer, Context context) {
      this.observer = observer;
      this.context = context;
      String fieldTrialsString = observer.getFieldTrialsString();
      this.connectivityManagerDelegate = new NetworkMonitorAutoDetect.ConnectivityManagerDelegate(context, this.availableNetworks, fieldTrialsString);
      this.wifiManagerDelegate = new NetworkMonitorAutoDetect.WifiManagerDelegate(context);
      NetworkMonitorAutoDetect.NetworkState networkState = this.connectivityManagerDelegate.getNetworkState();
      this.connectionType = getConnectionType(networkState);
      this.wifiSSID = this.getWifiSSID(networkState);
      this.intentFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
      if (includeWifiDirect) {
         this.wifiDirectManagerDelegate = new NetworkMonitorAutoDetect.WifiDirectManagerDelegate(observer, context);
      }

      this.registerReceiver();
      if (this.connectivityManagerDelegate.supportNetworkCallback()) {
         NetworkCallback tempNetworkCallback = new NetworkCallback();

         try {
            this.connectivityManagerDelegate.requestMobileNetwork(tempNetworkCallback);
         } catch (SecurityException var7) {
            Logging.w("NetworkMonitorAutoDetect", "Unable to obtain permission to request a cellular network.");
            tempNetworkCallback = null;
         }

         this.mobileNetworkCallback = tempNetworkCallback;
         this.allNetworkCallback = new NetworkMonitorAutoDetect.SimpleNetworkCallback(this.availableNetworks);
         this.connectivityManagerDelegate.registerNetworkCallback(this.allNetworkCallback);
      } else {
         this.mobileNetworkCallback = null;
         this.allNetworkCallback = null;
      }

   }

   public static void setIncludeWifiDirect(boolean enable) {
      includeWifiDirect = enable;
   }

   public boolean supportNetworkCallback() {
      return this.connectivityManagerDelegate.supportNetworkCallback();
   }

   void setConnectivityManagerDelegateForTests(NetworkMonitorAutoDetect.ConnectivityManagerDelegate delegate) {
      this.connectivityManagerDelegate = delegate;
   }

   void setWifiManagerDelegateForTests(NetworkMonitorAutoDetect.WifiManagerDelegate delegate) {
      this.wifiManagerDelegate = delegate;
   }

   boolean isReceiverRegisteredForTesting() {
      return this.isRegistered;
   }

   @Nullable
   public List<NetworkChangeDetector.NetworkInformation> getActiveNetworkList() {
      List<NetworkChangeDetector.NetworkInformation> connectivityManagerList = this.connectivityManagerDelegate.getActiveNetworkList();
      if (connectivityManagerList == null) {
         return null;
      } else {
         ArrayList<NetworkChangeDetector.NetworkInformation> result = new ArrayList(connectivityManagerList);
         if (this.wifiDirectManagerDelegate != null) {
            result.addAll(this.wifiDirectManagerDelegate.getActiveNetworkList());
         }

         return result;
      }
   }

   public void destroy() {
      if (this.allNetworkCallback != null) {
         this.connectivityManagerDelegate.releaseCallback(this.allNetworkCallback);
      }

      if (this.mobileNetworkCallback != null) {
         this.connectivityManagerDelegate.releaseCallback(this.mobileNetworkCallback);
      }

      if (this.wifiDirectManagerDelegate != null) {
         this.wifiDirectManagerDelegate.release();
      }

      this.unregisterReceiver();
   }

   private void registerReceiver() {
      if (!this.isRegistered) {
         this.isRegistered = true;
         this.context.registerReceiver(this, this.intentFilter);
      }
   }

   private void unregisterReceiver() {
      if (this.isRegistered) {
         this.isRegistered = false;
         this.context.unregisterReceiver(this);
      }
   }

   public NetworkMonitorAutoDetect.NetworkState getCurrentNetworkState() {
      return this.connectivityManagerDelegate.getNetworkState();
   }

   public long getDefaultNetId() {
      return this.connectivityManagerDelegate.getDefaultNetId();
   }

   private static NetworkChangeDetector.ConnectionType getConnectionType(boolean isConnected, int networkType, int networkSubtype) {
      if (!isConnected) {
         return NetworkChangeDetector.ConnectionType.CONNECTION_NONE;
      } else {
         switch(networkType) {
         case 0:
         case 4:
         case 5:
            switch(networkSubtype) {
            case 1:
            case 2:
            case 4:
            case 7:
            case 11:
            case 16:
               return NetworkChangeDetector.ConnectionType.CONNECTION_2G;
            case 3:
            case 5:
            case 6:
            case 8:
            case 9:
            case 10:
            case 12:
            case 14:
            case 15:
            case 17:
               return NetworkChangeDetector.ConnectionType.CONNECTION_3G;
            case 13:
            case 18:
               return NetworkChangeDetector.ConnectionType.CONNECTION_4G;
            case 19:
            default:
               return NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN_CELLULAR;
            case 20:
               return NetworkChangeDetector.ConnectionType.CONNECTION_5G;
            }
         case 1:
            return NetworkChangeDetector.ConnectionType.CONNECTION_WIFI;
         case 2:
         case 3:
         case 8:
         case 10:
         case 11:
         case 12:
         case 13:
         case 14:
         case 15:
         case 16:
         default:
            return NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN;
         case 6:
            return NetworkChangeDetector.ConnectionType.CONNECTION_4G;
         case 7:
            return NetworkChangeDetector.ConnectionType.CONNECTION_BLUETOOTH;
         case 9:
            return NetworkChangeDetector.ConnectionType.CONNECTION_ETHERNET;
         case 17:
            return NetworkChangeDetector.ConnectionType.CONNECTION_VPN;
         }
      }
   }

   public static NetworkChangeDetector.ConnectionType getConnectionType(NetworkMonitorAutoDetect.NetworkState networkState) {
      return getConnectionType(networkState.isConnected(), networkState.getNetworkType(), networkState.getNetworkSubType());
   }

   public NetworkChangeDetector.ConnectionType getCurrentConnectionType() {
      return getConnectionType(this.getCurrentNetworkState());
   }

   private static NetworkChangeDetector.ConnectionType getUnderlyingConnectionTypeForVpn(NetworkMonitorAutoDetect.NetworkState networkState) {
      return networkState.getNetworkType() != 17 ? NetworkChangeDetector.ConnectionType.CONNECTION_NONE : getConnectionType(networkState.isConnected(), networkState.getUnderlyingNetworkTypeForVpn(), networkState.getUnderlyingNetworkSubtypeForVpn());
   }

   private String getWifiSSID(NetworkMonitorAutoDetect.NetworkState networkState) {
      return getConnectionType(networkState) != NetworkChangeDetector.ConnectionType.CONNECTION_WIFI ? "" : this.wifiManagerDelegate.getWifiSSID();
   }

   public void onReceive(Context context, Intent intent) {
      NetworkMonitorAutoDetect.NetworkState networkState = this.getCurrentNetworkState();
      if ("android.net.conn.CONNECTIVITY_CHANGE".equals(intent.getAction())) {
         this.connectionTypeChanged(networkState);
      }

   }

   private void connectionTypeChanged(NetworkMonitorAutoDetect.NetworkState networkState) {
      NetworkChangeDetector.ConnectionType newConnectionType = getConnectionType(networkState);
      String newWifiSSID = this.getWifiSSID(networkState);
      if (newConnectionType != this.connectionType || !newWifiSSID.equals(this.wifiSSID)) {
         this.connectionType = newConnectionType;
         this.wifiSSID = newWifiSSID;
         Logging.d("NetworkMonitorAutoDetect", "Network connectivity changed, type is: " + this.connectionType);
         this.observer.onConnectionTypeChanged(newConnectionType);
      }
   }

   @SuppressLint({"NewApi"})
   private static long networkToNetId(Network network) {
      return VERSION.SDK_INT >= 23 ? network.getNetworkHandle() : (long)Integer.parseInt(network.toString());
   }

   static class ConnectivityManagerDelegate {
      @Nullable
      private final ConnectivityManager connectivityManager;
      @NonNull
      @GuardedBy("availableNetworks")
      private final Set<Network> availableNetworks;
      private final boolean getAllNetworksFromCache;
      private final boolean requestVPN;
      private final boolean includeOtherUidNetworks;

      ConnectivityManagerDelegate(Context context, Set<Network> availableNetworks, String fieldTrialsString) {
         this((ConnectivityManager)context.getSystemService("connectivity"), availableNetworks, fieldTrialsString);
      }

      @VisibleForTesting
      ConnectivityManagerDelegate(ConnectivityManager connectivityManager, Set<Network> availableNetworks, String fieldTrialsString) {
         this.connectivityManager = connectivityManager;
         this.availableNetworks = availableNetworks;
         this.getAllNetworksFromCache = checkFieldTrial(fieldTrialsString, "getAllNetworksFromCache", false);
         this.requestVPN = checkFieldTrial(fieldTrialsString, "requestVPN", false);
         this.includeOtherUidNetworks = checkFieldTrial(fieldTrialsString, "includeOtherUidNetworks", false);
      }

      private static boolean checkFieldTrial(String fieldTrialsString, String key, boolean defaultValue) {
         if (fieldTrialsString.contains(key + ":true")) {
            return true;
         } else {
            return fieldTrialsString.contains(key + ":false") ? false : defaultValue;
         }
      }

      NetworkMonitorAutoDetect.NetworkState getNetworkState() {
         return this.connectivityManager == null ? new NetworkMonitorAutoDetect.NetworkState(false, -1, -1, -1, -1) : this.getNetworkState(this.connectivityManager.getActiveNetworkInfo());
      }

      @SuppressLint({"NewApi"})
      NetworkMonitorAutoDetect.NetworkState getNetworkState(@Nullable Network network) {
         if (network != null && this.connectivityManager != null) {
            NetworkInfo networkInfo = this.connectivityManager.getNetworkInfo(network);
            if (networkInfo == null) {
               Logging.w("NetworkMonitorAutoDetect", "Couldn't retrieve information from network " + network.toString());
               return new NetworkMonitorAutoDetect.NetworkState(false, -1, -1, -1, -1);
            } else if (networkInfo.getType() != 17) {
               NetworkCapabilities networkCapabilities = this.connectivityManager.getNetworkCapabilities(network);
               return networkCapabilities != null && networkCapabilities.hasTransport(4) ? new NetworkMonitorAutoDetect.NetworkState(networkInfo.isConnected(), 17, -1, networkInfo.getType(), networkInfo.getSubtype()) : this.getNetworkState(networkInfo);
            } else if (networkInfo.getType() == 17) {
               if (VERSION.SDK_INT >= 23 && network.equals(this.connectivityManager.getActiveNetwork())) {
                  NetworkInfo underlyingActiveNetworkInfo = this.connectivityManager.getActiveNetworkInfo();
                  if (underlyingActiveNetworkInfo != null && underlyingActiveNetworkInfo.getType() != 17) {
                     return new NetworkMonitorAutoDetect.NetworkState(networkInfo.isConnected(), 17, -1, underlyingActiveNetworkInfo.getType(), underlyingActiveNetworkInfo.getSubtype());
                  }
               }

               return new NetworkMonitorAutoDetect.NetworkState(networkInfo.isConnected(), 17, -1, -1, -1);
            } else {
               return this.getNetworkState(networkInfo);
            }
         } else {
            return new NetworkMonitorAutoDetect.NetworkState(false, -1, -1, -1, -1);
         }
      }

      private NetworkMonitorAutoDetect.NetworkState getNetworkState(@Nullable NetworkInfo networkInfo) {
         return networkInfo != null && networkInfo.isConnected() ? new NetworkMonitorAutoDetect.NetworkState(true, networkInfo.getType(), networkInfo.getSubtype(), -1, -1) : new NetworkMonitorAutoDetect.NetworkState(false, -1, -1, -1, -1);
      }

      @SuppressLint({"NewApi"})
      Network[] getAllNetworks() {
         if (this.connectivityManager == null) {
            return new Network[0];
         } else if (this.supportNetworkCallback() && this.getAllNetworksFromCache) {
            synchronized(this.availableNetworks) {
               return (Network[])this.availableNetworks.toArray(new Network[0]);
            }
         } else {
            return this.connectivityManager.getAllNetworks();
         }
      }

      @Nullable
      List<NetworkChangeDetector.NetworkInformation> getActiveNetworkList() {
         if (!this.supportNetworkCallback()) {
            return null;
         } else {
            ArrayList<NetworkChangeDetector.NetworkInformation> netInfoList = new ArrayList();
            Network[] var2 = this.getAllNetworks();
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
               Network network = var2[var4];
               NetworkChangeDetector.NetworkInformation info = this.networkToInfo(network);
               if (info != null) {
                  netInfoList.add(info);
               }
            }

            return netInfoList;
         }
      }

      @SuppressLint({"NewApi"})
      long getDefaultNetId() {
         if (!this.supportNetworkCallback()) {
            return -1L;
         } else {
            NetworkInfo defaultNetworkInfo = this.connectivityManager.getActiveNetworkInfo();
            if (defaultNetworkInfo == null) {
               return -1L;
            } else {
               Network[] networks = this.getAllNetworks();
               long defaultNetId = -1L;
               Network[] var5 = networks;
               int var6 = networks.length;

               for(int var7 = 0; var7 < var6; ++var7) {
                  Network network = var5[var7];
                  if (this.hasInternetCapability(network)) {
                     NetworkInfo networkInfo = this.connectivityManager.getNetworkInfo(network);
                     if (networkInfo != null && networkInfo.getType() == defaultNetworkInfo.getType()) {
                        if (defaultNetId != -1L) {
                           throw new RuntimeException("Multiple connected networks of same type are not supported.");
                        }

                        defaultNetId = NetworkMonitorAutoDetect.networkToNetId(network);
                     }
                  }
               }

               return defaultNetId;
            }
         }
      }

      @SuppressLint({"NewApi"})
      @Nullable
      private NetworkChangeDetector.NetworkInformation networkToInfo(@Nullable Network network) {
         if (network != null && this.connectivityManager != null) {
            LinkProperties linkProperties = this.connectivityManager.getLinkProperties(network);
            if (linkProperties == null) {
               Logging.w("NetworkMonitorAutoDetect", "Detected unknown network: " + network.toString());
               return null;
            } else if (linkProperties.getInterfaceName() == null) {
               Logging.w("NetworkMonitorAutoDetect", "Null interface name for network " + network.toString());
               return null;
            } else {
               NetworkMonitorAutoDetect.NetworkState networkState = this.getNetworkState(network);
               NetworkChangeDetector.ConnectionType connectionType = NetworkMonitorAutoDetect.getConnectionType(networkState);
               if (connectionType == NetworkChangeDetector.ConnectionType.CONNECTION_NONE) {
                  Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " is disconnected");
                  return null;
               } else {
                  if (connectionType == NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN || connectionType == NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN_CELLULAR) {
                     String var10001 = network.toString();
                     Logging.d("NetworkMonitorAutoDetect", "Network " + var10001 + " connection type is " + connectionType + " because it has type " + networkState.getNetworkType() + " and subtype " + networkState.getNetworkSubType());
                  }

                  NetworkChangeDetector.ConnectionType underlyingConnectionTypeForVpn = NetworkMonitorAutoDetect.getUnderlyingConnectionTypeForVpn(networkState);
                  NetworkChangeDetector.NetworkInformation networkInformation = new NetworkChangeDetector.NetworkInformation(linkProperties.getInterfaceName(), connectionType, underlyingConnectionTypeForVpn, NetworkMonitorAutoDetect.networkToNetId(network), this.getIPAddresses(linkProperties));
                  return networkInformation;
               }
            }
         } else {
            return null;
         }
      }

      @SuppressLint({"NewApi"})
      boolean hasInternetCapability(Network network) {
         if (this.connectivityManager == null) {
            return false;
         } else {
            NetworkCapabilities capabilities = this.connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(12);
         }
      }

      @SuppressLint({"NewApi"})
      @VisibleForTesting
      NetworkRequest createNetworkRequest() {
         Builder builder = (new Builder()).addCapability(12);
         if (this.requestVPN) {
            builder.removeCapability(15);
         }

         if (VERSION.SDK_INT >= 31 && this.includeOtherUidNetworks) {
            builder.setIncludeOtherUidNetworks(true);
         }

         return builder.build();
      }

      @SuppressLint({"NewApi"})
      public void registerNetworkCallback(NetworkCallback networkCallback) {
         this.connectivityManager.registerNetworkCallback(this.createNetworkRequest(), networkCallback);
      }

      @SuppressLint({"NewApi"})
      public void requestMobileNetwork(NetworkCallback networkCallback) {
         Builder builder = new Builder();
         builder.addCapability(12).addTransportType(0);
         this.connectivityManager.requestNetwork(builder.build(), networkCallback);
      }

      @SuppressLint({"NewApi"})
      NetworkChangeDetector.IPAddress[] getIPAddresses(LinkProperties linkProperties) {
         NetworkChangeDetector.IPAddress[] ipAddresses = new NetworkChangeDetector.IPAddress[linkProperties.getLinkAddresses().size()];
         int i = 0;

         for(Iterator var4 = linkProperties.getLinkAddresses().iterator(); var4.hasNext(); ++i) {
            LinkAddress linkAddress = (LinkAddress)var4.next();
            ipAddresses[i] = new NetworkChangeDetector.IPAddress(linkAddress.getAddress().getAddress());
         }

         return ipAddresses;
      }

      @SuppressLint({"NewApi"})
      public void releaseCallback(NetworkCallback networkCallback) {
         if (this.supportNetworkCallback()) {
            Logging.d("NetworkMonitorAutoDetect", "Unregister network callback");
            this.connectivityManager.unregisterNetworkCallback(networkCallback);
         }

      }

      public boolean supportNetworkCallback() {
         return this.connectivityManager != null;
      }
   }

   static class WifiManagerDelegate {
      @Nullable
      private final Context context;

      WifiManagerDelegate(Context context) {
         this.context = context;
      }

      WifiManagerDelegate() {
         this.context = null;
      }

      String getWifiSSID() {
         Intent intent = this.context.registerReceiver((BroadcastReceiver)null, new IntentFilter("android.net.wifi.STATE_CHANGE"));
         if (intent != null) {
            WifiInfo wifiInfo = (WifiInfo)intent.getParcelableExtra("wifiInfo");
            if (wifiInfo != null) {
               String ssid = wifiInfo.getSSID();
               if (ssid != null) {
                  return ssid;
               }
            }
         }

         return "";
      }
   }

   static class NetworkState {
      private final boolean connected;
      private final int type;
      private final int subtype;
      private final int underlyingNetworkTypeForVpn;
      private final int underlyingNetworkSubtypeForVpn;

      public NetworkState(boolean connected, int type, int subtype, int underlyingNetworkTypeForVpn, int underlyingNetworkSubtypeForVpn) {
         this.connected = connected;
         this.type = type;
         this.subtype = subtype;
         this.underlyingNetworkTypeForVpn = underlyingNetworkTypeForVpn;
         this.underlyingNetworkSubtypeForVpn = underlyingNetworkSubtypeForVpn;
      }

      public boolean isConnected() {
         return this.connected;
      }

      public int getNetworkType() {
         return this.type;
      }

      public int getNetworkSubType() {
         return this.subtype;
      }

      public int getUnderlyingNetworkTypeForVpn() {
         return this.underlyingNetworkTypeForVpn;
      }

      public int getUnderlyingNetworkSubtypeForVpn() {
         return this.underlyingNetworkSubtypeForVpn;
      }
   }

   static class WifiDirectManagerDelegate extends BroadcastReceiver {
      private static final int WIFI_P2P_NETWORK_HANDLE = 0;
      private final Context context;
      private final NetworkChangeDetector.Observer observer;
      @Nullable
      private NetworkChangeDetector.NetworkInformation wifiP2pNetworkInfo;

      WifiDirectManagerDelegate(NetworkChangeDetector.Observer observer, Context context) {
         this.context = context;
         this.observer = observer;
         IntentFilter intentFilter = new IntentFilter();
         intentFilter.addAction("android.net.wifi.p2p.STATE_CHANGED");
         intentFilter.addAction("android.net.wifi.p2p.CONNECTION_STATE_CHANGE");
         context.registerReceiver(this, intentFilter);
         if (VERSION.SDK_INT > 28) {
            WifiP2pManager manager = (WifiP2pManager)context.getSystemService("wifip2p");
            Channel channel = manager.initialize(context, context.getMainLooper(), (ChannelListener)null);
            manager.requestGroupInfo(channel, (wifiP2pGroup) -> {
               this.onWifiP2pGroupChange(wifiP2pGroup);
            });
         }

      }

      @SuppressLint({"InlinedApi"})
      public void onReceive(Context context, Intent intent) {
         if ("android.net.wifi.p2p.CONNECTION_STATE_CHANGE".equals(intent.getAction())) {
            WifiP2pGroup wifiP2pGroup = (WifiP2pGroup)intent.getParcelableExtra("p2pGroupInfo");
            this.onWifiP2pGroupChange(wifiP2pGroup);
         } else if ("android.net.wifi.p2p.STATE_CHANGED".equals(intent.getAction())) {
            int state = intent.getIntExtra("wifi_p2p_state", 0);
            this.onWifiP2pStateChange(state);
         }

      }

      public void release() {
         this.context.unregisterReceiver(this);
      }

      public List<NetworkChangeDetector.NetworkInformation> getActiveNetworkList() {
         return this.wifiP2pNetworkInfo != null ? Collections.singletonList(this.wifiP2pNetworkInfo) : Collections.emptyList();
      }

      private void onWifiP2pGroupChange(@Nullable WifiP2pGroup wifiP2pGroup) {
         if (wifiP2pGroup != null && wifiP2pGroup.getInterface() != null) {
            NetworkInterface wifiP2pInterface;
            try {
               wifiP2pInterface = NetworkInterface.getByName(wifiP2pGroup.getInterface());
            } catch (SocketException var6) {
               Logging.e("NetworkMonitorAutoDetect", "Unable to get WifiP2p network interface", var6);
               return;
            }

            List<InetAddress> interfaceAddresses = Collections.list(wifiP2pInterface.getInetAddresses());
            NetworkChangeDetector.IPAddress[] ipAddresses = new NetworkChangeDetector.IPAddress[interfaceAddresses.size()];

            for(int i = 0; i < interfaceAddresses.size(); ++i) {
               ipAddresses[i] = new NetworkChangeDetector.IPAddress(((InetAddress)interfaceAddresses.get(i)).getAddress());
            }

            this.wifiP2pNetworkInfo = new NetworkChangeDetector.NetworkInformation(wifiP2pGroup.getInterface(), NetworkChangeDetector.ConnectionType.CONNECTION_WIFI, NetworkChangeDetector.ConnectionType.CONNECTION_NONE, 0L, ipAddresses);
            this.observer.onNetworkConnect(this.wifiP2pNetworkInfo);
         }
      }

      private void onWifiP2pStateChange(int state) {
         if (state == 1) {
            this.wifiP2pNetworkInfo = null;
            this.observer.onNetworkDisconnect(0L);
         }

      }
   }

   @SuppressLint({"NewApi"})
   @VisibleForTesting
   class SimpleNetworkCallback extends NetworkCallback {
      @GuardedBy("availableNetworks")
      final Set<Network> availableNetworks;

      SimpleNetworkCallback(Set<Network> availableNetworks) {
         this.availableNetworks = availableNetworks;
      }

      public void onAvailable(Network network) {
         long var10001 = NetworkMonitorAutoDetect.networkToNetId(network);
         Logging.d("NetworkMonitorAutoDetect", "Network handle: " + var10001 + " becomes available: " + network.toString());
         synchronized(this.availableNetworks) {
            this.availableNetworks.add(network);
         }

         this.onNetworkChanged(network);
      }

      public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
         long var10001 = NetworkMonitorAutoDetect.networkToNetId(network);
         Logging.d("NetworkMonitorAutoDetect", "handle: " + var10001 + " capabilities changed: " + networkCapabilities.toString());
         this.onNetworkChanged(network);
      }

      public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
         Logging.d("NetworkMonitorAutoDetect", "handle: " + NetworkMonitorAutoDetect.networkToNetId(network) + " link properties changed");
         this.onNetworkChanged(network);
      }

      public void onLosing(Network network, int maxMsToLive) {
         long var10001 = NetworkMonitorAutoDetect.networkToNetId(network);
         Logging.d("NetworkMonitorAutoDetect", "Network handle: " + var10001 + ", " + network.toString() + " is about to lose in " + maxMsToLive + "ms");
      }

      public void onLost(Network network) {
         long var10001 = NetworkMonitorAutoDetect.networkToNetId(network);
         Logging.d("NetworkMonitorAutoDetect", "Network handle: " + var10001 + ", " + network.toString() + " is disconnected");
         synchronized(this.availableNetworks) {
            this.availableNetworks.remove(network);
         }

         NetworkMonitorAutoDetect.this.observer.onNetworkDisconnect(NetworkMonitorAutoDetect.networkToNetId(network));
      }

      private void onNetworkChanged(Network network) {
         NetworkChangeDetector.NetworkInformation networkInformation = NetworkMonitorAutoDetect.this.connectivityManagerDelegate.networkToInfo(network);
         if (networkInformation != null) {
            NetworkMonitorAutoDetect.this.observer.onNetworkConnect(networkInformation);
         }

      }
   }
}
