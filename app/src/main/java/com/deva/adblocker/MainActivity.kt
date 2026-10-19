package com.deva.adblocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val requestVpn = 101
    private val listUrl = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"

    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var info: TextView
    private var extraViews: List<android.view.View> = emptyList()

    private fun skipLabel(btn: Button) {
        val n = Settings.skippedApps(this).size
        btn.text = if (n == 0) "Ad blocker தெரியக்கூடாத apps தேர்வு பண்ணு"
                   else "Ad blocker தெரியாத apps: $n (மாற்ற tap பண்ணு)"
    }

    /** Pick apps that should bypass the VPN completely (e.g. apps that complain about ad blockers). */
    private fun pickSkippedApps(btn: Button) {
        btn.isEnabled = false
        thread {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != packageName }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
            val chosen = Settings.skippedApps(this).toMutableSet()
            // chosen apps first, so they are easy to find
            val ordered = apps.sortedBy { if (it.first in chosen) 0 else 1 }
            runOnUiThread {
                btn.isEnabled = true
                val names = ordered.map { it.second }.toTypedArray()
                val checked = ordered.map { it.first in chosen }.toBooleanArray()
                android.app.AlertDialog.Builder(this)
                    .setTitle("இந்த apps-க்கு ad blocker தெரியாது (அவற்றில் ads வரும்)")
                    .setMultiChoiceItems(names, checked) { _, i, on ->
                        if (on) chosen.add(ordered[i].first) else chosen.remove(ordered[i].first)
                    }
                    .setPositiveButton("Save") { _, _ ->
                        Settings.setSkippedApps(this, chosen)
                        skipLabel(btn)
                        if (AdBlockVpnService.running) {
                            startService(Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_RESTART))
                        }
                        Toast.makeText(this, "Save ஆச்சு. அந்த app-ஐ ஒரு தடவை close பண்ணி திறங்க.", Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
        }
        val title = TextView(this).apply {
            text = "🛡️ Ad Blocker"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        toggle = Button(this).apply {
            textSize = 20f
            setOnClickListener { onToggle() }
        }
        val update = Button(this).apply {
            text = "Ad list update பண்ணு"
            setOnClickListener { updateList(this) }
        }
        val strong = Switch(this).apply {
            text = "Strong mode (OTT / video ads)\nஏதாவது app-ல video ஓடலைன்னா OFF பண்ணுங்க"
            textSize = 15f
            isChecked = Blocklist.isStrong(this@MainActivity)
            setPadding(0, pad, 0, 0)
            setOnCheckedChangeListener { _, on ->
                Blocklist.setStrong(this@MainActivity, on)
                thread { try { Blocklist.load(this@MainActivity) } catch (_: Exception) { } }
            }
        }
        val closeSwitch = Switch(this).apply {
            text = "App-ஐ close பண்ணா (Recents-ல swipe) VPN off ஆகணும்"
            textSize = 15f
            isChecked = Settings.stopOnClose(this@MainActivity)
            setPadding(0, pad / 2, 0, 0)
            setOnCheckedChangeListener { _, on -> Settings.setStopOnClose(this@MainActivity, on) }
        }
        val skipBtn = Button(this).apply {
            setOnClickListener { pickSkippedApps(this) }
        }
        skipLabel(skipBtn)
        extraViews = listOf(closeSwitch, skipBtn)
        info = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        val tips = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.GRAY)
            text = "குறிப்பு:\n" +
                "• முதல் தடவை ON பண்ணும்போது 'Connection request' வரும் — OK கொடுங்க.\n" +
                "• Settings → Network → Private DNS → OFF வைங்க.\n" +
                "• Chrome → Settings → Privacy → 'Use secure DNS' → OFF வைங்க.\n" +
                "• App close பண்ணா off ஆகணும்னா Settings → VPN → Ad Blocker ⚙ → Always-on VPN OFF-ல இருக்கணும்.\n" +
                "• ON பண்ணிட்டு Home button அழுத்துங்க (Recents-ல இருந்து swipe பண்ணாதீங்க) — அப்போ தான் மத்த apps-ல block ஆகும்.\n" +
                "• Site-ல இருந்து வர்ற ad notifications: Chrome → Settings → Site settings → Notifications → OFF.\n" +
                "• YouTube / Hotstar / Instagram / Facebook ads video-வோட சேர்ந்தே வருது — அவை block ஆகாது."
        }

        root.addView(title)
        root.addView(status)
        root.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(update, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(strong, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        for (v in extraViews) root.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(info)
        root.addView(tips)
        setContentView(ScrollView(this).apply { addView(root) })

        thread {
            try { if (Blocklist.size() == 0) Blocklist.load(this) } catch (_: Exception) { }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun refresh() {
        if (AdBlockVpnService.running) {
            status.text = "✅ ON — Ads block ஆகுது"
            status.setTextColor(Color.parseColor("#2E7D32"))
            toggle.text = "OFF பண்ணு"
        } else {
            status.text = "⛔ OFF"
            status.setTextColor(Color.parseColor("#C62828"))
            toggle.text = "ON பண்ணு"
        }
        info.text = "Block list: ${Blocklist.size()} domains\n" +
            "இதுவரை block பண்ணது: ${AdBlockVpnService.blockedCount.get()}"
    }

    private fun onToggle() {
        if (AdBlockVpnService.running) {
            startService(Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP))
        } else {
            val intent = VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent, requestVpn)
            else onActivityResult(requestVpn, RESULT_OK, null)
        }
    }

    @Deprecated("simple flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestVpn && resultCode == RESULT_OK) {
            startService(Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START))
        } else if (requestCode == requestVpn) {
            Toast.makeText(this, "Permission கொடுக்கல", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateList(btn: Button) {
        btn.isEnabled = false
        btn.text = "Download ஆகுது..."
        thread {
            var ok = 0
            for ((name, url) in Blocklist.SOURCES) {
                if (url == null) continue
                try {
                    val tmp = File(filesDir, "$name.tmp")
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                    conn.disconnect()
                    if (tmp.length() < 1000) throw Exception("empty")
                    val target = Blocklist.userFile(this, name)
                    target.delete()
                    tmp.renameTo(target)
                    ok++
                } catch (_: Exception) { }
            }
            try { Blocklist.load(this) } catch (_: Exception) { }
            val msg = if (ok > 0) "Update ஆச்சு ✅ (${Blocklist.size()} domains)"
                      else "Update fail ஆச்சு — internet check பண்ணுங்க"
            runOnUiThread {
                btn.isEnabled = true
                btn.text = "Ad list update பண்ணு"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
