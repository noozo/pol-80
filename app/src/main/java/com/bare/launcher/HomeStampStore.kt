package com.bare.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Tiny HUD home stamps (double-tap wallpaper pins).
 * Local-only JSON in filesDir. Never throws to callers — storage failures
 * return empty / false so the launcher stays up.
 */
object HomeStampStore {

    const val MAX_NOTES = 8
    const val MAX_TEXT_LEN = 48
    private const val FILE_NAME = "home_stamps.json"

    data class Stamp(
        val id: String,
        val text: String,
        /** Fractional X in the home overlay (0..1), top-left of the note. */
        val xFrac: Float,
        /** Fractional Y in the home overlay (0..1). */
        val yFrac: Float,
        val createdAt: Long,
    )

    fun load(context: Context): List<Stamp> {
        return try {
            val file = file(context)
            if (!file.exists() || !file.isFile) return emptyList()
            val raw = file.readText()
            if (raw.isBlank()) return emptyList()
            parse(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Persist [stamps], keeping at most [MAX_NOTES] (drops oldest by createdAt).
     * @return false if write failed (UI still holds in-memory list).
     */
    fun save(context: Context, stamps: List<Stamp>): Boolean {
        return try {
            val trimmed = stamps
                .sortedBy { it.createdAt }
                .takeLast(MAX_NOTES)
            val arr = JSONArray()
            for (s in trimmed) {
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("text", s.text.take(MAX_TEXT_LEN))
                        .put("xFrac", s.xFrac.toDouble())
                        .put("yFrac", s.yFrac.toDouble())
                        .put("createdAt", s.createdAt),
                )
            }
            val file = file(context)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(arr.toString())
                tmp.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun upsert(context: Context, current: List<Stamp>, stamp: Stamp): List<Stamp> {
        val without = current.filterNot { it.id == stamp.id }
        val next = without + stamp
        val trimmed = next.sortedBy { it.createdAt }.takeLast(MAX_NOTES)
        save(context, trimmed)
        return trimmed
    }

    fun remove(context: Context, current: List<Stamp>, id: String): List<Stamp> {
        val next = current.filterNot { it.id == id }
        save(context, next)
        return next
    }

    fun newId(): String = UUID.randomUUID().toString()

    /**
     * Clamp tap fractions into a sensible on-screen band, or fall back to a
     * default corner when the tap lands on header / dock / extreme edges.
     */
    fun sanitizePosition(xFrac: Float, yFrac: Float): Pair<Float, Float> {
        val awkward =
            xFrac < 0.04f || xFrac > 0.92f ||
                yFrac < 0.10f || yFrac > 0.82f
        if (awkward) {
            return 0.70f to 0.58f
        }
        return xFrac.coerceIn(0.04f, 0.78f) to yFrac.coerceIn(0.12f, 0.78f)
    }

    private fun file(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)

    private fun parse(raw: String): List<Stamp> {
        val arr = JSONArray(raw)
        val out = ArrayList<Stamp>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isBlank()) continue
            val text = o.optString("text", "").trim()
            if (text.isEmpty()) continue
            val x = o.optDouble("xFrac", 0.7).toFloat()
            val y = o.optDouble("yFrac", 0.55).toFloat()
            val at = o.optLong("createdAt", 0L)
            out += Stamp(
                id = id,
                text = text.take(MAX_TEXT_LEN),
                xFrac = x,
                yFrac = y,
                createdAt = at,
            )
        }
        return out.sortedBy { it.createdAt }.takeLast(MAX_NOTES)
    }
}
