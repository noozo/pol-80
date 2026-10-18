package com.bare.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists multi-page home layout (pins + widgets per page), dock slots.
 * Home items use ordered slot index within each page.
 */
sealed class HomeItem {
    data class Pin(val key: String) : HomeItem()
    data class Widget(val widgetId: Int) : HomeItem()

    fun serialize(): String = when (this) {
        is Pin -> "p:$key"
        is Widget -> "w:$widgetId"
    }

    companion object {
        fun parse(raw: String): HomeItem? {
            if (raw.startsWith("p:") && raw.length > 2) return Pin(raw.substring(2))
            if (raw.startsWith("w:") && raw.length > 2) {
                val id = raw.substring(2).toIntOrNull() ?: return null
                return Widget(id)
            }
            // Legacy bare key without prefix → treat as pin
            if (raw.contains('/')) return Pin(raw)
            raw.toIntOrNull()?.let { return Widget(it) }
            return null
        }
    }
}

data class HomePage(val items: List<HomeItem> = emptyList())

data class HomeLayout(val pages: List<HomePage>) {
    init {
        require(pages.isNotEmpty()) { "layout needs at least one page" }
    }

    companion object {
        fun singleEmpty() = HomeLayout(listOf(HomePage()))
    }
}

class LauncherPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** All pin keys across every page (ordered, unique). */
    fun getAllPinnedKeys(): LinkedHashSet<String> {
        val out = linkedSetOf<String>()
        getLayout().pages.forEach { page ->
            page.items.forEach { item ->
                if (item is HomeItem.Pin) out.add(item.key)
            }
        }
        return out
    }

    /** @deprecated Use [getAllPinnedKeys] / layout APIs. Kept for migration callers. */
    fun getPinnedKeys(): LinkedHashSet<String> = getAllPinnedKeys()

    fun isPinned(key: String): Boolean = getAllPinnedKeys().contains(key)

    /**
     * Toggle pin: if present on any page, remove everywhere; else append to [pageIndex].
     * Returns the full set of pinned keys after the change.
     */
    fun togglePin(key: String, pageIndex: Int = 0): LinkedHashSet<String> {
        val layout = getLayout()
        val exists = layout.pages.any { page ->
            page.items.any { it is HomeItem.Pin && it.key == key }
        }
        val newPages = if (exists) {
            layout.pages.map { page ->
                page.copy(items = page.items.filterNot { it is HomeItem.Pin && it.key == key })
            }
        } else {
            val pages = layout.pages.toMutableList()
            val idx = pageIndex.coerceIn(0, pages.lastIndex)
            val page = pages[idx]
            pages[idx] = page.copy(items = page.items + HomeItem.Pin(key))
            pages
        }
        setLayout(HomeLayout(ensureAtLeastOne(newPages)))
        return getAllPinnedKeys()
    }

    fun removePin(key: String) {
        val layout = getLayout()
        val newPages = layout.pages.map { page ->
            page.copy(items = page.items.filterNot { it is HomeItem.Pin && it.key == key })
        }
        setLayout(HomeLayout(ensureAtLeastOne(newPages)))
    }

    fun getLayout(): HomeLayout {
        if (!prefs.contains(KEY_PAGE_COUNT)) {
            return migrateLegacy()
        }
        val count = prefs.getInt(KEY_PAGE_COUNT, 1).coerceAtLeast(1)
        val pages = (0 until count).map { i ->
            val raw = prefs.getString(pageKey(i), "") ?: ""
            val items = if (raw.isBlank()) {
                emptyList()
            } else {
                raw.split(SEP).mapNotNull { HomeItem.parse(it) }
            }
            HomePage(items)
        }
        return HomeLayout(pages)
    }

    fun setLayout(layout: HomeLayout) {
        val pages = ensureAtLeastOne(layout.pages)
        val editor = prefs.edit()
        editor.putInt(KEY_PAGE_COUNT, pages.size)
        pages.forEachIndexed { i, page ->
            editor.putString(
                pageKey(i),
                page.items.joinToString(SEP) { it.serialize() },
            )
        }
        // Drop stale page keys beyond new count (best-effort, up to a cap)
        val oldCount = prefs.getInt(KEY_PAGE_COUNT, pages.size)
        for (i in pages.size until maxOf(oldCount, pages.size) + 4) {
            if (i >= pages.size) editor.remove(pageKey(i))
        }
        editor.apply()
    }

    fun addWidgetToPage(widgetId: Int, pageIndex: Int) {
        val layout = getLayout()
        val pages = layout.pages.toMutableList()
        val idx = pageIndex.coerceIn(0, pages.lastIndex)
        val page = pages[idx]
        if (page.items.any { it is HomeItem.Widget && it.widgetId == widgetId }) {
            return
        }
        // Remove from other pages if somehow present
        for (i in pages.indices) {
            if (i != idx) {
                pages[i] = pages[i].copy(
                    items = pages[i].items.filterNot {
                        it is HomeItem.Widget && it.widgetId == widgetId
                    },
                )
            }
        }
        pages[idx] = page.copy(items = page.items + HomeItem.Widget(widgetId))
        setLayout(HomeLayout(pages))
    }

    /** Legacy helper — adds to page 0. Prefer [addWidgetToPage]. */
    fun addWidgetId(id: Int) = addWidgetToPage(id, 0)

    fun removeWidgetId(id: Int) {
        val layout = getLayout()
        val newPages = layout.pages.map { page ->
            page.copy(
                items = page.items.filterNot {
                    it is HomeItem.Widget && it.widgetId == id
                },
            )
        }
        setLayout(HomeLayout(ensureAtLeastOne(newPages)))
    }

    fun getWidgetIds(): List<Int> {
        return getLayout().pages.flatMap { page ->
            page.items.mapNotNull { (it as? HomeItem.Widget)?.widgetId }
        }
    }

    fun moveItem(fromPage: Int, fromIndex: Int, toPage: Int, toIndex: Int): HomeLayout {
        val layout = getLayout()
        val pages = layout.pages.toMutableList()
        if (fromPage !in pages.indices || toPage !in pages.indices) return layout
        val fromItems = pages[fromPage].items.toMutableList()
        if (fromIndex !in fromItems.indices) return layout
        val item = fromItems.removeAt(fromIndex)
        pages[fromPage] = HomePage(fromItems)

        val destItems = pages[toPage].items.toMutableList()
        val insertAt = toIndex.coerceIn(0, destItems.size)
        destItems.add(insertAt, item)
        pages[toPage] = HomePage(destItems)

        val result = HomeLayout(ensureAtLeastOne(pages))
        setLayout(result)
        return result
    }

    fun reorderWithinPage(pageIndex: Int, fromIndex: Int, toIndex: Int): HomeLayout {
        val layout = getLayout()
        if (pageIndex !in layout.pages.indices) return layout
        if (fromIndex == toIndex) return layout
        val items = layout.pages[pageIndex].items.toMutableList()
        if (fromIndex !in items.indices) return layout
        val item = items.removeAt(fromIndex)
        val insertAt = toIndex.coerceIn(0, items.size)
        items.add(insertAt, item)
        val pages = layout.pages.toMutableList()
        pages[pageIndex] = HomePage(items)
        val result = HomeLayout(pages)
        setLayout(result)
        return result
    }

    fun addPage(): HomeLayout {
        val layout = getLayout()
        val result = HomeLayout(layout.pages + HomePage())
        setLayout(result)
        return result
    }

    fun addPageIfNeeded(pageIndex: Int): HomeLayout {
        var layout = getLayout()
        while (pageIndex >= layout.pages.size) {
            layout = addPage()
        }
        return layout
    }

    /** Drop trailing empty pages, keep at least one. */
    fun trimTrailingEmptyPages(): HomeLayout {
        val layout = getLayout()
        if (layout.pages.size <= 1) return layout
        var end = layout.pages.lastIndex
        while (end > 0 && layout.pages[end].items.isEmpty()) {
            end--
        }
        if (end == layout.pages.lastIndex) return layout
        val result = HomeLayout(layout.pages.subList(0, end + 1))
        setLayout(result)
        return result
    }

    fun removePage(pageIndex: Int): HomeLayout {
        val layout = getLayout()
        if (layout.pages.size <= 1) return layout
        if (pageIndex !in layout.pages.indices) return layout
        val pages = layout.pages.toMutableList().also { it.removeAt(pageIndex) }
        val result = HomeLayout(ensureAtLeastOne(pages))
        setLayout(result)
        return result
    }

    /** Exactly [DOCK_SIZE] entries; blank means empty slot. */
    fun getDockSlots(): List<String> {
        val raw = prefs.getString(KEY_DOCK, null)
        if (raw == null) return List(DOCK_SIZE) { "" }
        val parts = raw.split(SEP)
        return List(DOCK_SIZE) { i -> parts.getOrElse(i) { "" } }
    }

    fun setDockSlots(slots: List<String>) {
        val normalized = List(DOCK_SIZE) { i -> slots.getOrElse(i) { "" } }
        prefs.edit().putString(KEY_DOCK, normalized.joinToString(SEP)).apply()
    }

    fun setDockSlot(index: Int, key: String?) {
        if (index !in 0 until DOCK_SIZE) return
        val slots = getDockSlots().toMutableList()
        slots[index] = key.orEmpty()
        setDockSlots(slots)
    }

    private fun migrateLegacy(): HomeLayout {
        val rawPins = prefs.getString(KEY_PINS, "") ?: ""
        val pins = if (rawPins.isBlank()) {
            emptyList()
        } else {
            rawPins.split(SEP).filter { it.isNotBlank() }
        }
        val rawWidgets = prefs.getString(KEY_WIDGETS, "") ?: ""
        val widgets = if (rawWidgets.isBlank()) {
            emptyList()
        } else {
            rawWidgets.split(SEP).mapNotNull { it.toIntOrNull() }
        }
        val items = widgets.map { HomeItem.Widget(it) } + pins.map { HomeItem.Pin(it) }
        val layout = HomeLayout(listOf(HomePage(items)))
        setLayout(layout)
        return layout
    }

    /** Most-recent search actions (apps + people), newest first, max [SEARCH_HISTORY_MAX]. */
    fun getSearchHistory(): List<SearchHistoryItem> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(SEP)
            .mapNotNull { SearchHistoryItem.parse(it) }
            .take(SEARCH_HISTORY_MAX)
    }

    /** @deprecated Prefer [getSearchHistory]; kept for call-site migration. */
    fun getSearchHistoryKeys(): List<String> =
        getSearchHistory().mapNotNull { (it as? SearchHistoryItem.App)?.key }

    fun addSearchHistory(key: String) {
        addSearchHistoryItem(SearchHistoryItem.App(key))
    }

    fun addSearchHistoryPerson(
        contactId: Long,
        number: String,
        name: String,
        action: SearchHistoryPersonAction,
    ) {
        addSearchHistoryItem(
            SearchHistoryItem.Person(
                contactId = contactId,
                number = number,
                name = name,
                action = action,
            ),
        )
    }

    fun addSearchHistoryItem(item: SearchHistoryItem) {
        val encoded = item.serialize()
        if (encoded.isBlank()) return
        val next = LinkedHashSet<String>()
        next.add(encoded)
        // Dedupe by identity (app key / person id+number+action), keep newest first.
        getSearchHistory().forEach { existing ->
            if (!existing.sameIdentity(item)) {
                next.add(existing.serialize())
            }
        }
        val trimmed = next.toList().take(SEARCH_HISTORY_MAX)
        prefs.edit().putString(KEY_SEARCH_HISTORY, trimmed.joinToString(SEP)).apply()
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    fun getPhosphorMode(): PhosphorMode {
        val raw = prefs.getString(KEY_PHOSPHOR_MODE, PhosphorMode.Auto.name) ?: PhosphorMode.Auto.name
        return runCatching { PhosphorMode.valueOf(raw) }.getOrDefault(PhosphorMode.Auto)
    }

    fun setPhosphorMode(mode: PhosphorMode) {
        prefs.edit().putString(KEY_PHOSPHOR_MODE, mode.name).apply()
    }

    fun cyclePhosphorMode(): PhosphorMode {
        val next = getPhosphorMode().next()
        setPhosphorMode(next)
        return next
    }

    /** v0.54: Call log / SMS auto-prompt already shown once on this install. */
    fun wasStatusPermsAutoAsked(): Boolean = prefs.getBoolean(KEY_STATUS_PERMS_AUTO_ASKED, false)

    fun setStatusPermsAutoAsked() {
        prefs.edit().putBoolean(KEY_STATUS_PERMS_AUTO_ASKED, true).apply()
    }

    /** Shake → flashlight toggle. Off by default; does not replace dock torch. */
    fun isShakeFlashlightEnabled(): Boolean =
        prefs.getBoolean(KEY_SHAKE_FLASHLIGHT, false)

    fun setShakeFlashlightEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHAKE_FLASHLIGHT, enabled).apply()
    }

    fun toggleShakeFlashlightEnabled(): Boolean {
        val next = !isShakeFlashlightEnabled()
        setShakeFlashlightEnabled(next)
        return next
    }

    /**
     * Lock vertical orientation (portrait only). Default ON.
     * When true → Activity SCREEN_ORIENTATION_PORTRAIT;
     * when false → SCREEN_ORIENTATION_UNSPECIFIED (allow landscape).
     */
    fun isPortraitLockEnabled(): Boolean =
        prefs.getBoolean(KEY_PORTRAIT_LOCK, true)

    fun setPortraitLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PORTRAIT_LOCK, enabled).apply()
    }

    fun togglePortraitLockEnabled(): Boolean {
        val next = !isPortraitLockEnabled()
        setPortraitLockEnabled(next)
        return next
    }

    private fun ensureAtLeastOne(pages: List<HomePage>): List<HomePage> =
        if (pages.isEmpty()) listOf(HomePage()) else pages

    private fun pageKey(index: Int) = "${KEY_PAGE_PREFIX}$index"

    companion object {
        const val DOCK_SIZE = 5
        const val SEARCH_HISTORY_MAX = 8
        private const val PREFS_NAME = "bare_launcher"
        private const val KEY_PINS = "pinned_apps" // legacy
        private const val KEY_DOCK = "dock_apps"
        private const val KEY_WIDGETS = "widget_ids" // legacy
        private const val KEY_PAGE_COUNT = "home_page_count"
        private const val KEY_PAGE_PREFIX = "home_page_"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_PHOSPHOR_MODE = "phosphor_mode"
        private const val KEY_SHAKE_FLASHLIGHT = "shake_flashlight"
        private const val KEY_STATUS_PERMS_AUTO_ASKED = "status_perms_auto_asked"
        private const val KEY_PORTRAIT_LOCK = "portrait_lock"
        private const val SEP = "\u001f"
    }
}

fun appKey(packageName: String, activityName: String): String =
    "$packageName/$activityName"

fun parseAppKey(key: String): Pair<String, String>? {
    val idx = key.indexOf('/')
    if (idx <= 0 || idx >= key.length - 1) return null
    return key.substring(0, idx) to key.substring(idx + 1)
}

enum class SearchHistoryPersonAction { Phone, WhatsApp }

/**
 * Mixed search MRU: launched apps and contact Phone/WhatsApp actions.
 * Serialized into SharedPreferences with SEP between entries.
 * Legacy bare app keys (pkg/activity) still parse as [App].
 */
sealed class SearchHistoryItem {
    data class App(val key: String) : SearchHistoryItem()
    data class Person(
        val contactId: Long,
        val number: String,
        val name: String,
        val action: SearchHistoryPersonAction,
    ) : SearchHistoryItem()

    fun serialize(): String = when (this) {
        is App -> key
        is Person -> {
            val act = if (action == SearchHistoryPersonAction.Phone) "phone" else "wa"
            val safeNumber = number.replace(":", " ")
            val safeName = name.replace("\u001f", " ").replace("\u001e", " ")
            "person:$act:$contactId:$safeNumber:$safeName"
        }
    }

    fun sameIdentity(other: SearchHistoryItem): Boolean = when {
        this is App && other is App -> key == other.key
        this is Person && other is Person ->
            contactId == other.contactId &&
                number == other.number &&
                action == other.action
        else -> false
    }

    companion object {
        fun parse(raw: String): SearchHistoryItem? {
            if (raw.isBlank()) return null
            if (raw.startsWith("person:")) {
                // person:phone|wa:id:number:name... (name may contain ':')
                val rest = raw.removePrefix("person:")
                val first = rest.indexOf(':')
                if (first <= 0) return null
                val actRaw = rest.substring(0, first)
                val afterAct = rest.substring(first + 1)
                val second = afterAct.indexOf(':')
                if (second <= 0) return null
                val id = afterAct.substring(0, second).toLongOrNull() ?: return null
                val afterId = afterAct.substring(second + 1)
                val third = afterId.indexOf(':')
                if (third < 0) return null
                val number = afterId.substring(0, third).trim()
                val name = afterId.substring(third + 1).trim().ifBlank { number }
                if (number.isBlank()) return null
                val action = when (actRaw) {
                    "phone" -> SearchHistoryPersonAction.Phone
                    "wa" -> SearchHistoryPersonAction.WhatsApp
                    else -> return null
                }
                return Person(contactId = id, number = number, name = name, action = action)
            }
            // Legacy / app key
            if (raw.contains('/')) return App(raw)
            return null
        }
    }
}
