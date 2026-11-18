package com.thousandlabs.dlock.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

object Vpn {
    fun isActive(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    /** Prévient (sur le fil principal) à chaque connexion ou coupure d'un VPN. */
    fun watch(context: Context, onChange: () -> Unit): ConnectivityManager.NetworkCallback? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val main = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post(onChange) }
            override fun onLost(network: Network) { main.post(onChange) }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        return runCatching { cm.registerNetworkCallback(request, callback); callback }.getOrNull()
    }

    fun unwatch(context: Context, callback: ConnectivityManager.NetworkCallback?) {
        if (callback == null) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}
