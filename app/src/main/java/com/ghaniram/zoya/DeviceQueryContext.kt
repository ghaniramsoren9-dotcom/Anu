package com.ghaniram.zoya

/** One-shot bridge telling DeviceInfoProvider which field the user actually asked for. */
object DeviceQueryContext {
    @Volatile private var query: String = ""

    fun set(value: String) {
        query = value.trim()
    }

    fun consume(): String {
        val value = query
        query = ""
        return value
    }
}
