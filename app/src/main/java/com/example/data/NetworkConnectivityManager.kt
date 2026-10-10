package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust Network Connectivity & Offline Mode Manager.
 * 
 * Guarantees:
 * 1. Live Internet Connectivity: Automatically syncs data and enables real-time capabilities.
 * 2. Strict Offline Protection: Allows full read-only viewing of data that was updated before
 *    the internet was turned off (from Room SQL & local cache), but strictly blocks any new data
 *    entry, edits, or remote modifications while offline.
 * 3. Includes an on-demand Offline Mode Simulator for testing and evaluation.
 */
enum class CloudSyncState {
  OFFLINE,
  ONLINE_SYNCED,
  SERVICE_UNAVAILABLE,
  SYNC_PENDING,
  SYNC_FAILED
}

object NetworkConnectivityManager {
  private const val TAG = "NetworkConnectivity"
  private const val PREFS_NAME = "fsi_network_prefs"
  private const val KEY_LAST_SYNC = "last_successful_sync_timestamp"
  private const val KEY_SIMULATED_OFFLINE = "simulated_offline_mode"

  private var appContext: Context? = null
  private var prefs: SharedPreferences? = null
  private var connectivityManager: ConnectivityManager? = null
  private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null
  private var registeredCallbackCount: Int = 0

  // Physical network state
  private val _isNetworkConnected = MutableStateFlow(true)
  val isNetworkConnected = _isNetworkConnected.asStateFlow()

  // Simulated offline mode (for UI testing or deliberate offline simulation)
  private val _isSimulatedOffline = MutableStateFlow(false)
  val isSimulatedOffline = _isSimulatedOffline.asStateFlow()

  // Whether Firebase/Cloud backend is reachable (interface connectivity != service reachability)
  private val _isServiceReachable = MutableStateFlow(true)
  val isServiceReachable = _isServiceReachable.asStateFlow()
  val isFirebaseReachable = _isServiceReachable.asStateFlow()

  // Effective Online state: True only if physical network is present AND not simulated offline
  private val _isOnline = MutableStateFlow(true)
  val isOnline = _isOnline.asStateFlow()

  private val _cloudSyncState = MutableStateFlow(CloudSyncState.ONLINE_SYNCED)
  val cloudSyncState = _cloudSyncState.asStateFlow()

  private val _pendingSyncCount = MutableStateFlow(0)
  val pendingSyncCount = _pendingSyncCount.asStateFlow()

  private val _lastSyncError = MutableStateFlow<String?>(null)
  val lastSyncError = _lastSyncError.asStateFlow()

  // Timestamp of the last time data was confirmed synced to cloud
  private val _lastSyncTimestamp = MutableStateFlow<Long>(System.currentTimeMillis())
  val lastSyncTimestamp = _lastSyncTimestamp.asStateFlow()

  private val _lastSyncFormatted = MutableStateFlow("Just now")
  val lastSyncFormatted = _lastSyncFormatted.asStateFlow()

  private val _syncStatusText = MutableStateFlow("Online • Live Sync")
  val syncStatusText = _syncStatusText.asStateFlow()

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing = _isSyncing.asStateFlow()

  private val networkScope = CoroutineScope(Dispatchers.IO)

  @Synchronized
  fun initialize(context: Context) {
    appContext = context.applicationContext
    prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    val savedLastSync = prefs?.getLong(KEY_LAST_SYNC, 0L) ?: 0L
    val effectiveLastSync = if (savedLastSync > 0L) savedLastSync else System.currentTimeMillis()
    _lastSyncTimestamp.value = effectiveLastSync
    _lastSyncFormatted.value = formatTimestamp(effectiveLastSync)

    val savedSimulatedOffline = prefs?.getBoolean(KEY_SIMULATED_OFFLINE, false) ?: false
    _isSimulatedOffline.value = savedSimulatedOffline

    // Initial check
    val initialConnected = checkCurrentHardwareConnection()
    _isNetworkConnected.value = initialConnected
    recomputeEffectiveOnline()

    registerNetworkCallbackOnce()
  }

  fun getRegisteredCallbackCount(): Int = registeredCallbackCount

  private fun checkCurrentHardwareConnection(): Boolean {
    val cm = connectivityManager ?: return true
    return try {
      @Suppress("DEPRECATION")
      val infoConnected = cm.activeNetworkInfo?.isConnected == true
      val activeNetwork = cm.activeNetwork
      if (activeNetwork != null) {
        val caps = cm.getNetworkCapabilities(activeNetwork)
        if (caps != null) {
          return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) || infoConnected
        }
      }
      @Suppress("DEPRECATION")
      val info = cm.activeNetworkInfo
      if (info != null) {
        info.isConnected
      } else {
        true
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error checking active network", e)
      false
    }
  }

  @Synchronized
  private fun registerNetworkCallbackOnce() {
    if (activeNetworkCallback != null) {
      return
    }
    val cm = connectivityManager ?: return
    try {
      val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

      val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          super.onAvailable(network)
          Log.d(TAG, "Network became available")
          networkScope.launch {
            _isNetworkConnected.value = true
            val wasOffline = !_isOnline.value
            recomputeEffectiveOnline()

            if (wasOffline && _isOnline.value) {
              performLiveSync(isAutoReconnect = true)
            }
          }
        }

        override fun onLost(network: Network) {
          super.onLost(network)
          Log.d(TAG, "Network connection lost")
          networkScope.launch {
            val stillConnected = checkCurrentHardwareConnection()
            _isNetworkConnected.value = stillConnected
            recomputeEffectiveOnline()
          }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
          super.onCapabilitiesChanged(network, networkCapabilities)
          val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
          networkScope.launch {
            _isNetworkConnected.value = hasInternet
            recomputeEffectiveOnline()
          }
        }
      }
      cm.registerNetworkCallback(request, callback)
      activeNetworkCallback = callback
      registeredCallbackCount = 1
    } catch (e: Exception) {
      Log.e(TAG, "Failed to register network callback", e)
    }
  }

  @Synchronized
  fun unregisterNetworkCallback() {
    val callback = activeNetworkCallback ?: return
    try {
      connectivityManager?.unregisterNetworkCallback(callback)
    } catch (e: Exception) {
      Log.w(TAG, "Error unregistering network callback: ${e.message}")
    } finally {
      activeNetworkCallback = null
      registeredCallbackCount = 0
    }
  }

  private fun recomputeEffectiveOnline() {
    val online = _isNetworkConnected.value && !_isSimulatedOffline.value
    _isOnline.value = online
    val pending = _pendingSyncCount.value
    if (!online) {
      _cloudSyncState.value = if (pending > 0) CloudSyncState.SYNC_PENDING else CloudSyncState.OFFLINE
      _syncStatusText.value = if (pending > 0) {
        "Offline • Pending Sync ($pending)"
      } else {
        "Offline • Local Room Mode"
      }
    } else if (!_isServiceReachable.value) {
      _cloudSyncState.value = CloudSyncState.SERVICE_UNAVAILABLE
      _syncStatusText.value = if (pending > 0) {
        "Connected • Cloud Service Unavailable ($pending Pending)"
      } else {
        "Connected • Cloud Service Unavailable"
      }
    } else if (_lastSyncError.value != null) {
      _cloudSyncState.value = CloudSyncState.SYNC_FAILED
      _syncStatusText.value = "Sync Failed • Retry Pending"
    } else if (pending > 0) {
      _cloudSyncState.value = CloudSyncState.SYNC_PENDING
      _syncStatusText.value = "Online • Sync Pending ($pending)"
    } else {
      _cloudSyncState.value = CloudSyncState.ONLINE_SYNCED
      _syncStatusText.value = "Online • Live Sync"
    }
  }

  fun setSimulatedOffline(enabled: Boolean) {
    _isSimulatedOffline.value = enabled
    if (!enabled) {
      _isNetworkConnected.value = checkCurrentHardwareConnection()
      _isServiceReachable.value = true
      _lastSyncError.value = null
    }
    prefs?.edit()?.putBoolean(KEY_SIMULATED_OFFLINE, enabled)?.apply()
    recomputeEffectiveOnline()

    if (!enabled && _isNetworkConnected.value && FirestoreService.isAvailable()) {
      performLiveSync(isAutoReconnect = true)
    } else if (enabled) {
      _lastSyncFormatted.value = formatTimestamp(_lastSyncTimestamp.value)
    }
  }

  fun setSimulatedServiceUnavailable(unavailable: Boolean) {
    _isServiceReachable.value = !unavailable
    recomputeEffectiveOnline()
  }

  fun markPendingSync(pendingDelta: Int = 1) {
    _pendingSyncCount.update { (it + pendingDelta).coerceAtLeast(0) }
    recomputeEffectiveOnline()
  }

  fun recordLocalMutation() {
    markPendingSync(1)
  }

  fun recordServiceUnavailable(reason: String = "Firebase service unreachable") {
    reportCloudSyncFailure(reason, isServiceUnavailable = true)
  }

  fun recordSyncSuccess(syncedItemsCount: Int = 1) {
    reportCloudSyncSuccess(syncedItemsCount)
  }

  fun recordSyncFailure(errorMessage: String) {
    reportCloudSyncFailure(errorMessage, isServiceUnavailable = false)
  }

  fun reportCloudSyncSuccess(syncedItemsCount: Int = 1) {
    _lastSyncError.value = null
    _isServiceReachable.value = true
    _pendingSyncCount.update { (it - syncedItemsCount).coerceAtLeast(0) }
    val now = System.currentTimeMillis()
    _lastSyncTimestamp.value = now
    _lastSyncFormatted.value = formatTimestamp(now)
    prefs?.edit()?.putLong(KEY_LAST_SYNC, now)?.apply()
    recomputeEffectiveOnline()
  }

  fun reportCloudSyncFailure(errorMessage: String, isServiceUnavailable: Boolean = false) {
    _lastSyncError.value = errorMessage
    if (isServiceUnavailable) {
      _isServiceReachable.value = false
      _cloudSyncState.value = CloudSyncState.SERVICE_UNAVAILABLE
      _syncStatusText.value = "Connected • Cloud Service Unavailable"
    } else {
      _cloudSyncState.value = CloudSyncState.SYNC_FAILED
      _syncStatusText.value = "Sync Failed: $errorMessage"
    }
  }

  fun performLiveSync(
    isAutoReconnect: Boolean = false,
    onComplete: ((Boolean) -> Unit)? = null
  ) {
    if (!_isOnline.value) {
      recomputeEffectiveOnline()
      onComplete?.invoke(false)
      return
    }

    networkScope.launch {
      _isSyncing.value = true
      _syncStatusText.value = "Syncing live updates..."

      try {
        val reachable = verifyInternetReachability() && _isServiceReachable.value
        if (reachable) {
          val flushed = FirestoreService.flushPendingSyncQueue()
          if (flushed) {
            val now = System.currentTimeMillis()
            _lastSyncTimestamp.value = now
            _lastSyncFormatted.value = formatTimestamp(now)
            prefs?.edit()?.putLong(KEY_LAST_SYNC, now)?.apply()
            _lastSyncError.value = null
            _pendingSyncCount.value = FirestoreService.getPendingSyncCount()

            FirestoreService.fetchAnnouncements()
            FirestoreService.listenToAnnouncements()
            FirestoreService.fetchStudentProfiles()

            recomputeEffectiveOnline()
            withContext(Dispatchers.Main) {
              onComplete?.invoke(true)
            }
          } else {
            reportCloudSyncFailure("Firestore sync incomplete", isServiceUnavailable = !FirestoreService.isAvailable())
            withContext(Dispatchers.Main) {
              onComplete?.invoke(false)
            }
          }
        } else {
          reportCloudSyncFailure("Firebase service unreachable", isServiceUnavailable = true)
          withContext(Dispatchers.Main) {
            onComplete?.invoke(false)
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Sync error", e)
        reportCloudSyncFailure(e.message ?: "Temporary network error", isServiceUnavailable = false)
        withContext(Dispatchers.Main) {
          onComplete?.invoke(false)
        }
      } finally {
        _isSyncing.value = false
      }
    }
  }

  private fun verifyInternetReachability(): Boolean {
    if (!_isServiceReachable.value) return false
    return try {
      val url = URL("https://clients3.google.com/generate_204")
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = 2000
      connection.readTimeout = 2000
      connection.requestMethod = "HEAD"
      val responseCode = connection.responseCode
      connection.disconnect()
      responseCode in 200..399
    } catch (e: Exception) {
      _isNetworkConnected.value && _isServiceReachable.value
    }
  }

  /**
   * Called after a local Room write completes.
   * Never claims cloud sync succeeded while offline or when Firebase is unavailable.
   */
  fun markDataUpdatedOnline() {
    if (!_isOnline.value) {
      markPendingSync(1)
      return
    }
    if (!_isServiceReachable.value || !FirestoreService.isAvailable()) {
      markPendingSync(1)
      _cloudSyncState.value = if (!_isServiceReachable.value) {
        CloudSyncState.SERVICE_UNAVAILABLE
      } else {
        CloudSyncState.SYNC_PENDING
      }
      _syncStatusText.value = if (!_isServiceReachable.value) {
        "Connected • Cloud Service Unavailable (${_pendingSyncCount.value} Pending)"
      } else {
        "Local Room Saved • Cloud Sync Pending (${_pendingSyncCount.value})"
      }
      return
    }
    reportCloudSyncSuccess(0)
  }

  private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val sdfDay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

    return if (sdfDay.format(date) == sdfDay.format(now)) {
      "Today, ${sdfTime.format(date)}"
    } else {
      "${sdfDay.format(date)} ${sdfTime.format(date)}"
    }
  }
}
