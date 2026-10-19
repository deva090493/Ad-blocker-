package com.deva.adblocker

import android.content.Context

/** Small saved settings. */
object Settings {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Apps the VPN does not touch at all (they can't see the ad blocker, but their ads show). */
    fun skippedApps(ctx: Context): Set<String> = p(ctx).getStringSet("skip", emptySet()) ?: emptySet()
    fun setSkippedApps(ctx: Context, apps: Set<String>) {
        p(ctx).edit().putStringSet("skip", HashSet(apps)).apply()
    }

    /** Turn the VPN off when the app is swiped away from Recents. */
    fun stopOnClose(ctx: Context): Boolean = p(ctx).getBoolean("stop_on_close", true)
    fun setStopOnClose(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("stop_on_close", on).apply() }
}
