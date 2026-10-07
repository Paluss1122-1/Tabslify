package com.tabslify.tabs.exploretab

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

object ExploreNetworkWatcher {

    private const val RECHECK_DEBOUNCE_MS = 15_000L

    @Volatile
    private var callback: ConnectivityManager.NetworkCallback? = null

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var pendingToken = 0L

    @Synchronized
    fun arm(context: Context) {
        if (callback != null) {
            return
        }
        val appCtx = context.applicationContext
        val cm = appCtx.getSystemService(ConnectivityManager::class.java) ?: return

        val watcher = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleRecheck(appCtx, cm, network)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                scheduleRecheck(appCtx, cm, network)
            }
        }

        try {
            cm.registerDefaultNetworkCallback(watcher)
            callback = watcher
        } catch (_: Exception) {
        }
    }

    private fun scheduleRecheck(appCtx: Context, cm: ConnectivityManager, network: Network) {
        val label = transportLabel(cm, network)
        pendingToken++
        val token = pendingToken
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (token == pendingToken) {
                ExploreLocationTracker.recheck(appCtx, label)
            }
        }, RECHECK_DEBOUNCE_MS)
    }

    private fun transportLabel(cm: ConnectivityManager, network: Network): String {
        val caps = cm.getNetworkCapabilities(network) ?: return "—"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "—"
        }
    }
}
