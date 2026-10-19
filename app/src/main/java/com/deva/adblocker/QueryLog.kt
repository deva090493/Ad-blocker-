package com.deva.adblocker

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** Remembers the last DNS lookups: which app, which server, blocked or not. */
object QueryLog {
    class Entry(val time: Long, val pkg: String?, val app: String, val domain: String, val blocked: Boolean)

    private const val MAX = 2000
    private val list = ArrayDeque<Entry>()
    private val labels = ConcurrentHashMap<String, String>()

    fun label(ctx: Context, pkg: String?): String {
        if (pkg == null) return "System / unknown"
        return labels.getOrPut(pkg) {
            try {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg }
        }
    }

    fun add(ctx: Context, pkg: String?, domain: String, blocked: Boolean) {
        val e = Entry(System.currentTimeMillis(), pkg, label(ctx, pkg), domain, blocked)
        synchronized(list) {
            list.addFirst(e)
            while (list.size > MAX) list.removeLast()
        }
    }

    fun snapshot(): List<Entry> = synchronized(list) { list.toList() }

    fun clear() = synchronized(list) { list.clear() }
}
