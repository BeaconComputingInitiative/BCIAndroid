/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid UI/Network Module
 * Developer Seal: BCI-CJB-ANDROID
 * Integrity: SHA-256 emitted by the Android CI source-hash manifest.
 */
package org.beaconcomputinginitiative.bciwhisper.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock

/**
 * Keeps ConnectivityManager queries off the high-frequency monitor render path.
 */
class NetworkStatusTracker(
    context: Context,
    private val cacheMillis: Long = 1_500L
) {
    data class Snapshot(
        val available: Boolean,
        val summary: String
    )

    private val manager =
        context.applicationContext.getSystemService(
            ConnectivityManager::class.java
        )

    private var lastCheckedAt = Long.MIN_VALUE

    private var cached =
        Snapshot(
            available = false,
            summary = "NETWORK • OFFLINE"
        )

    fun snapshot(
        force: Boolean = false
    ): Snapshot {
        val now =
            SystemClock.elapsedRealtime()

        val cacheValid =
            lastCheckedAt != Long.MIN_VALUE &&
                now - lastCheckedAt < cacheMillis

        // Return cached state if and only if it is still valid and no force-read was requested.
        if (cacheValid && !force) {
            return cached
        }

        cached =
            readNetwork()

        lastCheckedAt =
            now

        return cached
    }

    private fun readNetwork(): Snapshot {
        val network =
            manager.activeNetwork
                ?: return Snapshot(
                    available = false,
                    summary = "NETWORK • OFFLINE"
                )

        val capabilities =
            manager.getNetworkCapabilities(network)
                ?: return Snapshot(
                    available = false,
                    summary = "NETWORK • OFFLINE"
                )

        val available =
            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )

        if (!available) {
            return Snapshot(
                available = false,
                summary = "NETWORK • OFFLINE"
            )
        }

        val summary =
            when {
                capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR
                ) ->
                    "CELLULAR • outbound WSS supported • mobile data will be used"

                capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
                ) ->
                    "WI-FI • outbound WSS supported"

                capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_ETHERNET
                ) ->
                    "ETHERNET • outbound WSS supported"

                capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_VPN
                ) ->
                    "VPN • outbound WSS supported"

                else ->
                    "NETWORK • outbound WSS supported"
            }

        return Snapshot(
            available = true,
            summary = summary
        )
    }
}
