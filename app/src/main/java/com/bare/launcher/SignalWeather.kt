package com.bare.launcher

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager

/**
 * CRT-style "radio weather" from live link quality (RSSI / cell bars).
 *
 * Preference: score the **active** default-route network only (ConnectivityManager.activeNetwork).
 * That matches what the phone actually uses; we do not merge Wi‑Fi + cell into a stronger-of-two.
 */
enum class SignalWx {
    CLEAR,
    LIGHT,
    STATIC,
    STORM,
    UNKNOWN,
    ;

    /** Short HUD label. */
    val label: String
        get() = when (this) {
            CLEAR -> "CLEAR"
            LIGHT -> "LIGHT"
            STATIC -> "STATIC"
            STORM -> "STORM"
            UNKNOWN -> "—"
        }
}

data class SignalWeatherReading(
    val state: SignalWx,
    /** Wi‑Fi RSSI in dBm when known; null for cell-bar / unknown paths. */
    val dbm: Int? = null,
    /** Human network kind: Wi-Fi / LTE / 5G / Mobile / Offline. */
    val networkKind: String? = null,
    /** 0–4 style bars from NetworkCapabilities when used. */
    val bars: Int? = null,
) {
    val chipLabel: String get() = state.label

    val detailLine: String
        get() {
            val kind = networkKind ?: "—"
            val strength = when {
                dbm != null -> "${dbm}dBm"
                bars != null -> "${bars}/4"
                else -> "n/a"
            }
            return "RF  ${state.label}  $strength  $kind"
        }
}

object SignalWeather {

    fun snapshot(context: Context): SignalWeatherReading {
        return try {
            read(context.applicationContext)
        } catch (_: Exception) {
            SignalWeatherReading(SignalWx.STATIC, networkKind = "—")
        }
    }

    /**
     * Register a NetworkCallback so RF weather refreshes on connect/disconnect/capability
     * changes. Returns unregister lambda. Never throws.
     */
    fun registerNetworkCallback(
        context: Context,
        onChanged: () -> Unit,
    ): () -> Unit {
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return {}
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChanged()
            override fun onLost(network: Network) = onChanged()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = onChanged()
        }
        return try {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, callback)
            ({
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
            })
        } catch (_: Exception) {
            try {
                cm.registerDefaultNetworkCallback(callback)
                ({
                    try {
                        cm.unregisterNetworkCallback(callback)
                    } catch (_: Exception) {
                    }
                })
            } catch (_: Exception) {
                {}
            }
        }
    }

    private fun read(context: Context): SignalWeatherReading {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return SignalWeatherReading(SignalWx.STATIC, networkKind = "—")
        val network = cm.activeNetwork
            ?: return SignalWeatherReading(SignalWx.STORM, networkKind = "Offline")
        val caps = cm.getNetworkCapabilities(network)
            ?: return SignalWeatherReading(SignalWx.STORM, networkKind = "Offline")

        // Active network only — see file KDoc.
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                fromWifi(context, caps)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                fromCellular(context, caps)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                SignalWeatherReading(SignalWx.CLEAR, networkKind = "Ethernet")
            else -> fromCapsBars(caps, kind = "Online")
        }
    }

    private fun fromWifi(context: Context, caps: NetworkCapabilities): SignalWeatherReading {
        val rssi = wifiRssi(context)
        if (rssi != null) {
            return SignalWeatherReading(
                state = mapWifiRssi(rssi),
                dbm = rssi,
                networkKind = "Wi-Fi",
            )
        }
        // Fallback: NetworkCapabilities signal strength (API 29+), often RSSI-ish for Wi‑Fi.
        return fromCapsBars(caps, kind = "Wi-Fi")
    }

    private fun fromCellular(context: Context, caps: NetworkCapabilities): SignalWeatherReading {
        val kind = cellularKind(context)
        // Prefer NetworkCapabilities bars — no READ_PHONE_STATE required on modern Android.
        if (Build.VERSION.SDK_INT >= 29) {
            val strength = caps.signalStrength
            if (strength != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
                // Cellular often reports 0–4; some OEMs use dBm-like negatives.
                if (strength in 0..4) {
                    return SignalWeatherReading(
                        state = mapBars(strength),
                        bars = strength,
                        networkKind = kind,
                    )
                }
                if (strength < 0) {
                    // Treat as dBm-style.
                    return SignalWeatherReading(
                        state = mapWifiRssi(strength),
                        dbm = strength,
                        networkKind = kind,
                    )
                }
            }
        }
        // Optional soft probe of TelephonyManager — swallow SecurityException (no SIM / restricted).
        val tmRssi = telephonyDbm(context)
        if (tmRssi != null) {
            return SignalWeatherReading(
                state = mapWifiRssi(tmRssi),
                dbm = tmRssi,
                networkKind = kind,
            )
        }
        return SignalWeatherReading(SignalWx.STATIC, networkKind = kind)
    }

    private fun fromCapsBars(caps: NetworkCapabilities, kind: String): SignalWeatherReading {
        if (Build.VERSION.SDK_INT < 29) {
            return SignalWeatherReading(SignalWx.STATIC, networkKind = kind)
        }
        val strength = caps.signalStrength
        if (strength == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
            return SignalWeatherReading(SignalWx.STATIC, networkKind = kind)
        }
        if (strength in 0..4) {
            return SignalWeatherReading(
                state = mapBars(strength),
                bars = strength,
                networkKind = kind,
            )
        }
        if (strength < 0) {
            return SignalWeatherReading(
                state = mapWifiRssi(strength),
                dbm = strength,
                networkKind = kind,
            )
        }
        return SignalWeatherReading(SignalWx.LIGHT, networkKind = kind)
    }

    /** ACCESS_WIFI_STATE only — RSSI, not SSID (no location). */
    private fun wifiRssi(context: Context): Int? {
        return try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo ?: return null
            @Suppress("DEPRECATION")
            val rssi = info.rssi
            // WifiManager uses -127 as "invalid" on many devices.
            if (rssi == -127 || rssi == Int.MIN_VALUE) null else rssi
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun telephonyDbm(context: Context): Int? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return null
            // getCellSignalStrengths() is API 29; on 28 it is NoSuchMethodError (an Error).
            if (Build.VERSION.SDK_INT >= 29) {
                val ss = tm.signalStrength ?: return null
                val vals = ss.cellSignalStrengths
                val best = vals.maxOfOrNull { it.dbm } ?: return null
                if (best == Int.MIN_VALUE || best == Int.MAX_VALUE) null else best
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun cellularKind(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return "Mobile"
            @Suppress("DEPRECATION")
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS,
                -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS,
                -> "2G"
                TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Mobile"
                else -> "Mobile"
            }
        } catch (_: SecurityException) {
            "Mobile"
        } catch (_: Exception) {
            "Mobile"
        }
    }

    /** Wi‑Fi RSSI (dBm) → CRT weather. */
    internal fun mapWifiRssi(dbm: Int): SignalWx = when {
        dbm >= -55 -> SignalWx.CLEAR
        dbm >= -67 -> SignalWx.LIGHT
        dbm >= -80 -> SignalWx.STATIC
        else -> SignalWx.STORM
    }

    /** 0–4 bars → CRT weather. */
    internal fun mapBars(bars: Int): SignalWx = when {
        bars >= 4 -> SignalWx.CLEAR
        bars == 3 -> SignalWx.LIGHT
        bars == 2 -> SignalWx.STATIC
        else -> SignalWx.STORM
    }
}
