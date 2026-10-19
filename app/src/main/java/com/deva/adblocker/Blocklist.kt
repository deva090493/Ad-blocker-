package com.deva.adblocker

import android.content.Context
import java.io.File
import java.io.InputStream

/** Holds the set of ad / tracker domains to block. */
object Blocklist {
    @Volatile
    private var domains: HashSet<String> = HashSet()

    // Never block these (keeps phone basics working)
    private val allow = setOf("localhost", "localhost.localdomain", "local", "broadcasthost",
        "0.0.0.0", "ip6-localhost", "ip6-loopback")

    fun size(): Int = domains.size

    fun userFile(ctx: Context): File = File(ctx.filesDir, "hosts.txt")

    @Synchronized
    fun load(ctx: Context) {
        val f = userFile(ctx)
        val stream: InputStream = if (f.exists() && f.length() > 1000) f.inputStream()
        else ctx.assets.open("hosts.txt")
        domains = stream.use { parse(it) }
    }

    private fun parse(stream: InputStream): HashSet<String> {
        val set = HashSet<String>(200_000)
        stream.bufferedReader().forEachLine { raw ->
            var line = raw
            val hash = line.indexOf('#')
            if (hash >= 0) line = line.substring(0, hash)
            line = line.trim()
            if (line.isEmpty()) return@forEachLine
            val parts = line.split(Regex("\\s+"))
            val domain = when {
                parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
                parts.size == 1 -> parts[0]
                else -> return@forEachLine
            }.lowercase().trimEnd('.')
            if (domain.contains('.') && domain !in allow) set.add(domain)
        }
        return set
    }

    /** true if the domain or any parent domain is in the list. */
    fun isBlocked(name: String): Boolean {
        val set = domains
        var d = name.lowercase().trimEnd('.')
        while (d.isNotEmpty()) {
            if (set.contains(d)) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
        }
        return false
    }
}
