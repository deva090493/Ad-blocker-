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
                "• எப்பவும் ON-ஆ இருக்க: Settings → VPN → Ad Blocker ⚙ → Always-on VPN.\n" +
                "• YouTube / Instagram / Facebook ads இந்த முறையில block ஆகாது."
        }

        root.addView(title)
        root.addView(status)
        root.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(update, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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
            val msg = try {
                val tmp = File(filesDir, "hosts.tmp")
                val conn = URL(listUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                conn.disconnect()
                if (tmp.length() < 1000) throw Exception("empty")
                val target = Blocklist.userFile(this)
                target.delete()
                tmp.renameTo(target)
                Blocklist.load(this)
                "Update ஆச்சு ✅ (${Blocklist.size()} domains)"
            } catch (e: Exception) {
                "Update fail ஆச்சு — internet check பண்ணுங்க"
            }
            runOnUiThread {
                btn.isEnabled = true
                btn.text = "Ad list update பண்ணு"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
