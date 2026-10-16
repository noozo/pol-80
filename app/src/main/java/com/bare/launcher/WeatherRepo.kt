package com.bare.launcher

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Today's HI/LO °C + sunrise/sunset + rain hint via free Open-Meteo.
 * Uses last-known location when permitted, otherwise Lisbon (Europe/Lisbon)
 * 38.72,-9.14. Cached ~30 minutes. Never throws to callers.
 */
object WeatherRepo {

    data class DayTemps(val hiC: Int, val loC: Int) {
        val display: String get() = "HI ${hiC}°  LO ${loC}°"
    }

    /** Local-day sun times in epoch millis (device clock). */
    data class SunTimes(val sunriseMs: Long, val sunsetMs: Long)

    data class Forecast(
        val temps: DayTemps,
        val sun: SunTimes?,
        /** True when current/daily weather indicates rain/drizzle. Null if unknown. */
        val raining: Boolean? = null,
    )

    private const val PREFS = "bare_weather"
    private const val KEY_HI = "hi"
    private const val KEY_LO = "lo"
    private const val KEY_SUNRISE = "sunrise_ms"
    private const val KEY_SUNSET = "sunset_ms"
    private const val KEY_RAINING = "raining"
    private const val KEY_AT = "fetched_at"
    private const val CACHE_MS = 30L * 60L * 1000L

    private const val DEFAULT_LAT = 38.72
    private const val DEFAULT_LON = -9.14

    fun cached(context: Context): DayTemps? {
        val p = prefs(context)
        val at = p.getLong(KEY_AT, 0L)
        if (at == 0L) return null
        if (!p.contains(KEY_HI) || !p.contains(KEY_LO)) return null
        return DayTemps(p.getInt(KEY_HI, 0), p.getInt(KEY_LO, 0))
    }

    fun cachedSun(context: Context): SunTimes? {
        val p = prefs(context)
        if (!p.contains(KEY_SUNRISE) || !p.contains(KEY_SUNSET)) return null
        val rise = p.getLong(KEY_SUNRISE, 0L)
        val set = p.getLong(KEY_SUNSET, 0L)
        if (rise <= 0L || set <= 0L || set <= rise) return null
        return SunTimes(sunriseMs = rise, sunsetMs = set)
    }

    /** Cached rain flag; null if never successfully stored. */
    fun cachedRaining(context: Context): Boolean? {
        val p = prefs(context)
        if (!p.contains(KEY_RAINING)) return null
        return p.getBoolean(KEY_RAINING, false)
    }

    fun cacheFresh(context: Context): Boolean {
        val at = prefs(context).getLong(KEY_AT, 0L)
        return at > 0L && (System.currentTimeMillis() - at) < CACHE_MS
    }

    suspend fun getOrFetch(context: Context, force: Boolean = false): DayTemps? {
        if (!force && cacheFresh(context)) {
            return cached(context)
        }
        val cached = cached(context)
        return try {
            val (lat, lon) = resolveLatLon(context)
            val fetched = fetchOpenMeteo(lat, lon)
            if (fetched != null) {
                store(context, fetched)
                fetched.temps
            } else {
                cached
            }
        } catch (_: Exception) {
            cached
        }
    }

    /**
     * Whether it is currently raining / drizzling near the device.
     * Uses fresh cache when available; otherwise fetches. Returns null on failure
     * (callers should skip rain UI silently).
     */
    suspend fun isRaining(context: Context, force: Boolean = false): Boolean? {
        return try {
            if (!force && cacheFresh(context) && prefs(context).contains(KEY_RAINING)) {
                return cachedRaining(context)
            }
            val (lat, lon) = resolveLatLon(context)
            val fetched = fetchOpenMeteo(lat, lon)
            if (fetched != null) {
                store(context, fetched)
                fetched.raining
            } else {
                cachedRaining(context)
            }
        } catch (_: Exception) {
            cachedRaining(context)
        }
    }

    private fun resolveLatLon(context: Context): Pair<Double, Double> {
        if (!hasLocationPermission(context)) {
            return DEFAULT_LAT to DEFAULT_LON
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return DEFAULT_LAT to DEFAULT_LON
        val loc = lastKnown(lm)
        return if (loc != null) {
            loc.latitude to loc.longitude
        } else {
            DEFAULT_LAT to DEFAULT_LON
        }
    }

    private fun lastKnown(lm: LocationManager): Location? {
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var best: Location? = null
        for (p in providers) {
            if (!lm.isProviderEnabled(p) && p != LocationManager.PASSIVE_PROVIDER) continue
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best!!.time) best = loc
        }
        return best
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private suspend fun fetchOpenMeteo(lat: Double, lon: Double): Forecast? =
        withContext(Dispatchers.IO) {
            val url = URL(
                String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=%.4f&longitude=%.4f" +
                        "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,weather_code,precipitation_sum" +
                        "&current=weather_code,precipitation" +
                        "&timezone=auto&forecast_days=1",
                    lat,
                    lon,
                ),
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseForecast(body)
            } finally {
                conn.disconnect()
            }
        }

    internal fun parse(json: String): DayTemps? = parseForecast(json)?.temps

    internal fun parseForecast(json: String): Forecast? {
        val root = JSONObject(json)
        val daily = root.optJSONObject("daily") ?: return null
        val maxArr = daily.optJSONArray("temperature_2m_max") ?: return null
        val minArr = daily.optJSONArray("temperature_2m_min") ?: return null
        if (maxArr.length() < 1 || minArr.length() < 1) return null
        if (maxArr.isNull(0) || minArr.isNull(0)) return null
        val hi = maxArr.getDouble(0).roundToInt()
        val lo = minArr.getDouble(0).roundToInt()
        val sun = parseSun(daily, root.optString("timezone").orEmpty())
        val raining = parseRaining(root, daily)
        return Forecast(DayTemps(hi, lo), sun, raining)
    }

    /**
     * WMO weather codes that imply rain / drizzle / showers / thunder-rain.
     * Also precipitation > 0.05 mm.
     */
    internal fun isRainWeatherCode(code: Int): Boolean =
        code in 51..67 || code in 80..82 || code in 95..99

    private fun parseRaining(root: JSONObject, daily: JSONObject): Boolean? {
        var decided: Boolean? = null
        val current = root.optJSONObject("current")
        if (current != null) {
            if (current.has("weather_code") && !current.isNull("weather_code")) {
                val code = current.optInt("weather_code", -1)
                if (code >= 0) {
                    decided = isRainWeatherCode(code)
                }
            }
            if (current.has("precipitation") && !current.isNull("precipitation")) {
                val precip = current.optDouble("precipitation", 0.0)
                if (precip > 0.05) decided = true
            }
        }
        if (decided == true) return true
        // Fall back to today's daily weather_code / precip sum when current is dry/missing.
        val codeArr = daily.optJSONArray("weather_code")
        if (codeArr != null && codeArr.length() >= 1 && !codeArr.isNull(0)) {
            val code = codeArr.optInt(0, -1)
            if (code >= 0 && isRainWeatherCode(code)) return true
        }
        val precipArr = daily.optJSONArray("precipitation_sum")
        if (precipArr != null && precipArr.length() >= 1 && !precipArr.isNull(0)) {
            val sum = precipArr.optDouble(0, 0.0)
            if (sum > 0.5) return true
        }
        return decided ?: false
    }

    private fun parseSun(daily: JSONObject, timezone: String): SunTimes? {
        val riseArr = daily.optJSONArray("sunrise") ?: return null
        val setArr = daily.optJSONArray("sunset") ?: return null
        if (riseArr.length() < 1 || setArr.length() < 1) return null
        if (riseArr.isNull(0) || setArr.isNull(0)) return null
        val riseRaw = riseArr.optString(0) ?: return null
        val setRaw = setArr.optString(0) ?: return null
        val riseMs = parseIsoLocal(riseRaw, timezone) ?: return null
        val setMs = parseIsoLocal(setRaw, timezone) ?: return null
        if (setMs <= riseMs) return null
        return SunTimes(sunriseMs = riseMs, sunsetMs = setMs)
    }

    /** Open-Meteo daily sunrise/sunset are ISO-8601 local (often without offset). */
    private fun parseIsoLocal(raw: String, timezone: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            // Prefer offset-aware parse when present
            OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                val zone = if (timezone.isNotBlank()) {
                    runCatching { ZoneId.of(timezone) }.getOrDefault(PhosphorNight.LISBON)
                } else {
                    PhosphorNight.LISBON
                }
                java.time.LocalDateTime.parse(trimmed).atZone(zone).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun store(context: Context, forecast: Forecast) {
        val ed = prefs(context).edit()
            .putInt(KEY_HI, forecast.temps.hiC)
            .putInt(KEY_LO, forecast.temps.loC)
            .putLong(KEY_AT, System.currentTimeMillis())
        val sun = forecast.sun
        if (sun != null) {
            ed.putLong(KEY_SUNRISE, sun.sunriseMs).putLong(KEY_SUNSET, sun.sunsetMs)
        }
        if (forecast.raining != null) {
            ed.putBoolean(KEY_RAINING, forecast.raining)
        }
        ed.apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
