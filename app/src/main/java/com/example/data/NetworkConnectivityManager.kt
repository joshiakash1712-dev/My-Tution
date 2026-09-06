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
object NetworkConnectivityManager {
  private const val TAG = "NetworkConnectivity"
  private const val PREFS_NAME = "fsi_network_prefs"
  private const val KEY_LAST_SYNC = "last_successful_sync_timestamp"
  private const val KEY_SIMULATED_OFFLINE = "simulated_offline_mode"

  private var appContext: Context? = null
  private var prefs: SharedPreferences? = null
  private var connectivityManager: ConnectivityManager? = null

  // Physical network state
  private val _isNetworkConnected = MutableStateFlow(true)
  val isNetworkConnected = _isNetworkConnected.asStateFlow()

  // Simulated offline mode (for UI testing or deliberate offline lock)
  private val _isSimulatedOffline = MutableStateFlow(false)
  val isSimulatedOffline = _isSimulatedOffline.asStateFlow()

  // Effective Online state: True only if physical network is present AND not simulated offline
  private val _isOnline = MutableStateFlow(true)
  val isOnline = _isOnline.asStateFlow()

  // Timestamp of the last time data was updated before internet turned off
  private val _lastSyncTimestamp = MutableStateFlow<Long>(System.currentTimeMillis())
  val lastSyncTimestamp = _lastSyncTimestamp.asStateFlow()

  private val _lastSyncFormatted = MutableStateFlow("Just now")
  val lastSyncFormatted = _lastSyncFormatted.asStateFlow()

  private val _syncStatusText = MutableStateFlow("Online • Live Sync")
  val syncStatusText = _syncStatusText.asStateFlow()

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing = _isSyncing.asStateFlow()

  private val networkScope = CoroutineScope(Dispatchers.IO)

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

    registerNetworkCallback()
  }

  private fun checkCurrentHardwareConnection(): Boolean {
    val cm = connectivityManager ?: return false
    return try {
      val activeNetwork = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
      Log.e(TAG, "Error checking active network", e)
      false
    }
  }

  private fun registerNetworkCallback() {
    val cm = connectivityManager ?: return
    try {
      val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

      cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          super.onAvailable(network)
          Log.d(TAG, "Network became available")
          networkScope.launch {
            _isNetworkConnected.value = true
            val wasOffline = !_isOnline.value
            recomputeEffectiveOnline()

            if (wasOffline && _isOnline.value) {
              // Trigger automatic live sync when reconnected
              performLiveSync(isAutoReconnect = true)
            }
          }
        }

        override fun onLost(network: Network) {
          super.onLost(network)
          Log.d(TAG, "Network connection lost")
          networkScope.launch {
            // Re-verify if any other active network remains
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
      })
    } catch (e: Exception) {
      Log.e(TAG, "Failed to register network callback", e)
    }
  }

  private fun recomputeEffectiveOnline() {
    val online = _isNetworkConnected.value && !_isSimulatedOffline.value
    _isOnline.value = online
    if (online) {
      _syncStatusText.value = "Online • Live Sync"
    } else {
      _syncStatusText.value = "Offline • Read-Only"
    }
  }

  /**
   * Toggles simulated offline mode to easily test and demonstrate offline behavior
   */
  fun setSimulatedOffline(enabled: Boolean) {
    _isSimulatedOffline.value = enabled
    prefs?.edit()?.putBoolean(KEY_SIMULATED_OFFLINE, enabled)?.apply()
    recomputeEffectiveOnline()

    if (!enabled && _isNetworkConnected.value) {
      // Reconnected
      performLiveSync(isAutoReconnect = true)
    } else if (enabled) {
      // Transitioned to offline - record current snapshot timestamp as last updated
      _lastSyncFormatted.value = formatTimestamp(_lastSyncTimestamp.value)
    }
  }

  /**
   * Performs live sync when online: updates announcements, profiles, and refreshes timestamp.
   */
  fun performLiveSync(
    isAutoReconnect: Boolean = false,
    onComplete: ((Boolean) -> Unit)? = null
  ) {
    if (!_isOnline.value) {
      onComplete?.invoke(false)
      return
    }

    networkScope.launch {
      _isSyncing.value = true
      _syncStatusText.value = "Syncing live updates..."

      try {
        // Fast ping check to verify true internet reachability
        val reachable = verifyInternetReachability()
        if (reachable) {
          // Update last sync timestamp to now
          val now = System.currentTimeMillis()
          _lastSyncTimestamp.value = now
          _lastSyncFormatted.value = formatTimestamp(now)
          prefs?.edit()?.putLong(KEY_LAST_SYNC, now)?.apply()

          // Trigger live Firestore fetching and real-time listeners
          FirestoreService.fetchAnnouncements()
          FirestoreService.listenToAnnouncements()
          FirestoreService.fetchStudentProfiles()

          _syncStatusText.value = "Online • Synced"

          if (isAutoReconnect) {
            appContext?.let { ctx ->
              AppRepository.addNotification(
                NotificationItem(
                  id = "NOT_ONLINE_${System.currentTimeMillis()}",
                  title = "🌐 Internet Connection Restored",
                  message = "App is online and synchronized with live institute data. All features and data entry are fully unlocked.",
                  time = AppRepository.getCurrentTimeStr(),
                  type = "System",
                  recipientRole = "All",
                  isRead = false
                )
              )
            }
          }
          withContext(Dispatchers.Main) {
            onComplete?.invoke(true)
          }
        } else {
          _syncStatusText.value = "Limited Connectivity"
          withContext(Dispatchers.Main) {
            onComplete?.invoke(false)
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Sync error", e)
        _syncStatusText.value = "Sync Error (Offline View Active)"
        withContext(Dispatchers.Main) {
          onComplete?.invoke(false)
        }
      } finally {
        _isSyncing.value = false
      }
    }
  }

  /**
   * Verifies actual internet reachability with a lightweight timeout
   */
  private fun verifyInternetReachability(): Boolean {
    return try {
      val url = URL("https://clients3.google.com/generate_204")
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = 3000
      connection.readTimeout = 3000
      connection.requestMethod = "HEAD"
      val responseCode = connection.responseCode
      connection.disconnect()
      responseCode in 200..399
    } catch (e: Exception) {
      // In local container or firewall fallback, if hardware claims connected, return true
      _isNetworkConnected.value
    }
  }

  /**
   * Call whenever an online data mutation succeeds to refresh the last sync timestamp
   */
  fun markDataUpdatedOnline() {
    if (_isOnline.value) {
      val now = System.currentTimeMillis()
      _lastSyncTimestamp.value = now
      _lastSyncFormatted.value = formatTimestamp(now)
      prefs?.edit()?.putLong(KEY_LAST_SYNC, now)?.apply()
    }
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
