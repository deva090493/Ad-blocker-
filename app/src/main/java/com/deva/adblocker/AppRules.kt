package com.deva.adblocker

import android.content.Context

/**
 * Per-app exceptions on top of the big block list.
 *  allow "pkg|domain" -> this app may reach the domain even if it is an ad domain
 *  block "pkg|domain" -> this app may NOT reach the domain even if it isn't on the list
 * A rule for a domain also covers its sub-domains.
 */
object AppRules {
    @Volatile private var allow: Set<String> = emptySet()
    @Volatile private var block: Set<String> = emptySet()

    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        allow = HashSet(p(ctx).getStringSet("app_allow", emptySet()) ?: emptySet())
        block = HashSet(p(ctx).getStringSet("app_block", emptySet()) ?: emptySet())
    }

    private fun key(pkg: String, domain: String) = "$pkg|${domain.lowercase().trimEnd('.')}"

    fun ruleFor(pkg: String, domain: String): String? {
        val k = key(pkg, domain)
        return when { k in allow -> "allow"; k in block -> "block"; else -> null }
    }

    /** rule = "allow", "block" or null (remove). */
    fun set(ctx: Context, pkg: String, domain: String, rule: String?) {
        val k = key(pkg, domain)
        val a = HashSet(allow); val b = HashSet(block)
        a.remove(k); b.remove(k)
        if (rule == "allow") a.add(k)
        if (rule == "block") b.add(k)
        p(ctx).edit().putStringSet("app_allow", a).putStringSet("app_block", b).apply()
        allow = a; block = b
    }

    fun all(): List<Pair<String, String>> =
        allow.map { it to "allow" } + block.map { it to "block" }

    fun decide(name: String, pkg: String?): Boolean {
        if (pkg != null && (allow.isNotEmpty() || block.isNotEmpty())) {
            var d = name.lowercase().trimEnd('.')
            while (d.contains('.')) {
                val k = "$pkg|$d"
                if (k in allow) return false
                if (k in block) return true
                d = d.substring(d.indexOf('.') + 1)
            }
        }
        return Blocklist.isBlocked(name)
    }
}
