package com.dk.himachalfarmdirect.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Emits false when the phone has no usable internet, so screens can show an offline banner. */
fun Context.onlineFlow(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    fun now() = cm.getNetworkCapabilities(cm.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    trySend(now())
    val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(now()) }
        override fun onLost(network: Network) { trySend(now()) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(now()) }
    }
    cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb)
    awaitClose { cm.unregisterNetworkCallback(cb) }
}.distinctUntilChanged()

/** Turns Firebase/network exceptions into plain words for users with little tech background. */
fun Throwable.friendly(): String {
    val m = message.orEmpty()
    return when {
        m.contains("UNAVAILABLE", true) || m.contains("network", true) || m.contains("timeout", true) ->
            "Internet is slow or unavailable. Please try again."
        m.contains("PERMISSION_DENIED", true) -> "You don't have access to this. Please sign in again."
        m.isNotBlank() -> m
        else -> "Something went wrong. Please try again."
    }
}
