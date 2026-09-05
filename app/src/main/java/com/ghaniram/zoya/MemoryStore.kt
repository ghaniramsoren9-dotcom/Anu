package com.ghaniram.zoya

import android.content.Context

/** Persistent, on-device memory for personal facts Zoya is explicitly asked to remember. */
class MemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("zoya_memories", Context.MODE_PRIVATE)
    private val key = "facts"
    private val separator = "\u0001"

    fun getAll(): List<String> {
        val raw = prefs.getString(key, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(separator).filter { it.isNotBlank() }
    }

    fun add(fact: String) {
        val clean = fact.trim()
        if (clean.isBlank()) return
        val current = getAll().toMutableList()
        if (current.any { it.equals(clean, ignoreCase = true) }) return
        current.add(clean)
        // Keep local memory bounded so it remains fast and private.
        val kept = current.takeLast(100)
        prefs.edit().putString(key, kept.joinToString(separator)).apply()
    }

    fun remove(fact: String) {
        val current = getAll().toMutableList()
        current.removeAll { it.equals(fact.trim(), ignoreCase = true) }
        prefs.edit().putString(key, current.joinToString(separator)).apply()
    }

    fun removeMatching(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isBlank()) return false
        val current = getAll().toMutableList()
        val before = current.size
        current.removeAll { it.lowercase().contains(q) || q.contains(it.lowercase()) }
        if (current.size != before) prefs.edit().putString(key, current.joinToString(separator)).apply()
        return current.size != before
    }

    fun clear() { prefs.edit().remove(key).apply() }
}
