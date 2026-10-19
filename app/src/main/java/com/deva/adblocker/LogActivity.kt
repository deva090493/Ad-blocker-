package com.deva.adblocker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows which app looked up which server, and whether it was blocked.
 * Tap a line to allow / block that server for that app only.
 */
class LogActivity : Activity() {

    private var filterPkg: String? = null       // null = all apps
    private var rows: List<QueryLog.Entry> = emptyList()
    private lateinit var list: ListView
    private lateinit var filterBtn: Button
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "App log"
        AppRules.load(this)
        val pad = (12 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filterBtn = Button(this).apply { setOnClickListener { pickApp() } }
        val refresh = Button(this).apply { text = "⟳"; setOnClickListener { reload() } }
        val share = Button(this).apply { text = "Share"; setOnClickListener { shareLog() } }
        val clear = Button(this).apply { text = "Clear"; setOnClickListener { QueryLog.clear(); reload() } }
        bar.addView(filterBtn, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(refresh); bar.addView(share); bar.addView(clear)

        val help = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.GRAY)
            text = "🔴 block ஆனது   🟢 போக விட்டது   ⭐ நீங்க போட்ட rule\n" +
                "ஒரு line-ஐ tap பண்ணி அந்த app-க்கு மட்டும் Allow / Block பண்ணலாம்."
        }
        list = ListView(this)
        list.setOnItemClickListener { _, _, pos, _ -> onRow(rows[pos]) }

        root.addView(bar)
        root.addView(help)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        reload()
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() {
        val all = QueryLog.snapshot()
        rows = if (filterPkg == null) all else all.filter { it.pkg == filterPkg }
        filterBtn.text = if (filterPkg == null) "எல்லா apps ▾" else "${QueryLog.label(this, filterPkg)} ▾"
        list.adapter = Adapter()
    }

    private fun pickApp() {
        val apps = QueryLog.snapshot().filter { it.pkg != null }
            .groupBy { it.pkg!! }
            .map { (pkg, e) -> Triple(pkg, e.first().app, e.size) }
            .sortedBy { it.second.lowercase() }
        val names = arrayOf("எல்லா apps") + apps.map { "${it.second}  (${it.third})" }
        AlertDialog.Builder(this)
            .setTitle("எந்த app?")
            .setItems(names) { _, i ->
                filterPkg = if (i == 0) null else apps[i - 1].first
                reload()
            }.show()
    }

    private fun onRow(e: QueryLog.Entry) {
        val pkg = e.pkg ?: return
        val rule = AppRules.ruleFor(pkg, e.domain)
        val opts = arrayOf(
            "✅ ${e.app}-க்கு மட்டும் Allow",
            "⛔ ${e.app}-க்கு மட்டும் Block",
            "↺ என் rule-ஐ எடு (default list படி)"
        )
        AlertDialog.Builder(this)
            .setTitle(e.domain + if (rule != null) "\n(இப்போ: $rule)" else "")
            .setItems(opts) { _, i ->
                AppRules.set(this, pkg, e.domain, when (i) { 0 -> "allow"; 1 -> "block"; else -> null })
                reload()
            }.show()
    }

    private fun shareLog() {
        val sb = StringBuilder()
        // unique app+domain lines, newest first
        val seen = HashSet<String>()
        for (e in rows) {
            val k = "${e.pkg}|${e.domain}"
            if (!seen.add(k)) continue
            sb.append(if (e.blocked) "BLOCK " else "ok    ").append(e.app).append("  ").append(e.domain).append('\n')
            if (seen.size >= 400) break
        }
        if (AppRules.all().isNotEmpty()) {
            sb.append("\n# my rules\n")
            AppRules.all().forEach { (k, r) -> sb.append(r).append(' ').append(k).append('\n') }
        }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString()),
            "Log share"))
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(p: Int) = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
            val e = rows[p]
            val tv = (convert as? TextView) ?: TextView(this@LogActivity).apply {
                textSize = 13f
                setPadding(8, 14, 8, 14)
                gravity = Gravity.START
            }
            val mine = e.pkg != null && AppRules.ruleFor(e.pkg, e.domain) != null
            tv.text = "${if (e.blocked) "🔴" else "🟢"}${if (mine) "⭐" else ""} ${e.domain}\n" +
                "     ${e.app} · ${fmt.format(Date(e.time))}"
            tv.setTextColor(if (e.blocked) Color.parseColor("#C62828") else Color.parseColor("#333333"))
            return tv
        }
    }
}
