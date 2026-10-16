package com.bare.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.content.ContextCompat

data class ContactMatch(
    val id: Long,
    val name: String,
    val number: String,
)

/**
 * Read contacts for fuzzy search (READ_CONTACTS). Phone / WhatsApp intents.
 */
object ContactsRepo {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

    fun loadContacts(context: Context): List<ContactMatch> {
        if (!hasPermission(context)) return emptyList()
        val out = LinkedHashMap<String, ContactMatch>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (idIdx < 0 || nameIdx < 0 || numIdx < 0) return emptyList()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val number = cursor.getString(numIdx)?.trim().orEmpty()
                    if (name.isBlank() || number.isBlank()) continue
                    val digits = normalizePhone(number)
                    if (digits.length < 3) continue
                    // One row per contact+normalized number; keep first (usually primary).
                    val key = "$id|$digits"
                    if (!out.containsKey(key)) {
                        out[key] = ContactMatch(id = id, name = name, number = number)
                    }
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return out.values.toList()
    }

    fun filterContacts(contacts: List<ContactMatch>, query: String): List<ContactMatch> {
        if (query.isBlank()) return emptyList()
        val scored = contacts.mapNotNull { c ->
            val nameScore = fuzzyContactScore(query, c.name)
            val numScore = if (normalizePhone(c.number).contains(normalizePhone(query))) {
                500
            } else {
                -1
            }
            val s = maxOf(nameScore, numScore)
            if (s < 0) null else c to s
        }
        return scored.sortedWith(
            compareByDescending<Pair<ContactMatch, Int>> { it.second }
                .thenBy { it.first.name.lowercase() },
        ).map { it.first }
    }

    fun dial(context: Context, number: String) {
        val cleaned = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleaned.isBlank()) {
            Toast.makeText(context, "No phone number", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open Phone", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWhatsApp(context: Context, number: String) {
        val digits = normalizePhone(number)
        if (digits.length < 7) {
            Toast.makeText(context, "No usable number for WhatsApp", Toast.LENGTH_SHORT).show()
            return
        }
        // Prefer wa.me / api.whatsapp.com (works with or without WA installed via browser fallback).
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$digits"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$digits"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // try next
            }
        }
        Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
    }

    fun normalizePhone(raw: String): String =
        raw.filter { it.isDigit() }

    private fun fuzzyContactScore(query: String, label: String): Int {
        if (query.isBlank()) return -1
        val q = query.lowercase().trim()
        val t = label.lowercase()
        if (t == q) return 1000
        if (t.startsWith(q)) return 800 - (t.length - q.length).coerceAtMost(100)
        if (t.contains(q)) return 600 - t.indexOf(q).coerceAtMost(100)
        var qi = 0
        var gaps = 0
        var last = -1
        for (i in t.indices) {
            if (qi < q.length && t[i] == q[qi]) {
                if (last >= 0) gaps += i - last - 1
                last = i
                qi++
            }
        }
        if (qi == q.length) return 400 - gaps.coerceAtMost(200)
        val tokens = t.split(' ', '-', '_', '.')
        if (tokens.any { it.startsWith(q) }) return 500
        return -1
    }
}
