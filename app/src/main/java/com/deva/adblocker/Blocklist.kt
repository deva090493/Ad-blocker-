package com.deva.adblocker

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Holds the set of ad / tracker domains to block.
 * Merges several lists:
 *  - hosts.txt   : StevenBlack hosts (web ads + trackers)
 *  - adguard.txt : AdGuard DNS filter (mobile app ad SDKs, video ad servers)
 *  - extra.txt   : our own extra video/app ad domains (bundled in the app)
 * "@@||domain^" lines in AdGuard are exceptions -> never blocked (prevents broken apps).
 */
object Blocklist {

    /** name in assets / filesDir  ->  download URL (null = bundled only) */
    val SOURCES: List<Pair<String, String?>> = listOf(
        "hosts.txt" to "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "adguard.txt" to "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
        "extra.txt" to null
    )

    @Volatile private var domains: HashSet<String> = HashSet()
    @Volatile private var exceptions: HashSet<String> = HashSet()

    private val alwaysAllow = setOf("localhost", "localhost.localdomain", "local", "broadcasthost",
        "0.0.0.0", "ip6-localhost", "ip6-loopback")

    fun size(): Int = domains.size

    fun userFile(ctx: Context, name: String): File = File(ctx.filesDir, name)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun isStrong(ctx: Context): Boolean = prefs(ctx).getBoolean("strong", true)
    fun setStrong(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("strong", on).apply() }

    @Synchronized
    fun load(ctx: Context) {
        val block = HashSet<String>(300_000)
        val allow = HashSet<String>()
        val strong = isStrong(ctx)
        for ((name, _) in SOURCES) {
            if (name == "extra.txt" && !strong) continue
            val f = userFile(ctx, name)
            val stream: InputStream? = try {
                if (f.exists() && f.length() > 100) f.inputStream() else ctx.assets.open(name)
            } catch (_: Exception) { null }
            stream?.use { parse(it, block, allow) }
        }
        exceptions = allow
        domains = block
    }

    private fun clean(d: String): String = d.lowercase().trim().trimEnd('.')

    private fun parse(stream: InputStream, block: HashSet<String>, allow: HashSet<String>) {
        stream.bufferedReader().forEachLine { raw ->
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("#") || line.startsWith("[")) return@forEachLine

            // Adblock-style: ||domain^  or  @@||domain^
            if (line.startsWith("@@||") || line.startsWith("||")) {
                val isException = line.startsWith("@@")
                line = line.removePrefix("@@").removePrefix("||")
                val end = line.indexOfFirst { it == '^' || it == '$' || it == '/' }
                if (end >= 0) {
                    // skip rules with special options like $important,$client=... except plain ones
                    val rest = line.substring(end)
                    if (rest.contains('$') && !rest.startsWith("^\$important") && rest != "^") {
                        if (!isException) return@forEachLine
                    }
                    line = line.substring(0, end)
                }
                if (line.contains('*')) return@forEachLine
                val d = clean(line)
                if (!d.contains('.')) return@forEachLine
                if (isException) allow.add(d) else if (d !in alwaysAllow) block.add(d)
                return@forEachLine
            }

            // hosts-style: 0.0.0.0 domain   or plain   domain
            val hash = line.indexOf('#')
            if (hash >= 0) line = line.substring(0, hash).trim()
            if (line.isEmpty()) return@forEachLine
            val parts = line.split(Regex("\\s+"))
            val domain = when {
                parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
                parts.size == 1 -> parts[0]
                else -> return@forEachLine
            }
            val d = clean(domain)
            if (d.contains('.') && d !in alwaysAllow) block.add(d)
        }
    }

    /** true if the domain or any parent domain is in the list (and not an exception). */
    fun isBlocked(name: String): Boolean {
        val set = domains
        val allow = exceptions
        var d = clean(name)
        if (allow.contains(d)) return false
        while (d.isNotEmpty()) {
            if (set.contains(d)) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
            if (allow.contains(d)) return false
        }
        return false
    }
}
