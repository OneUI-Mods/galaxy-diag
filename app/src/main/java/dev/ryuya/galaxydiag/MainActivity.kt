package dev.ryuya.galaxydiag

import android.app.job.JobScheduler
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var ui: Ui
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private lateinit var badge: TextView
    private lateinit var time: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var automatic: MaterialSwitch
    private lateinit var errorDetails: MaterialButton
    private val actions = mutableListOf<MaterialButton>()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        val content = ui.column().apply { setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(28)) }
        val top = ui.horizontal()
        top.addView(
            ui.text("GALAXY TOOLS", 11f, R.color.muted, true).apply { letterSpacing = .15f },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        top.addView(ui.iconButton(R.drawable.ic_info, "アプリについて") { showAbout() })
        content.addView(top)
        content.addView(
            ui.text("Galaxy DIAG", 36f, medium = true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(24) },
        )
        content.addView(
            ui.text("モデム測定の接続準備", 15f, R.color.muted),
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = ui.dp(10)
                bottomMargin = ui.dp(28)
            },
        )

        val hero = ui.card(R.color.hero)
        val body = ui.column().apply { setPadding(ui.dp(22), ui.dp(22), ui.dp(22), ui.dp(16)) }
        val stateTop = ui.horizontal()
        val signal =
            ui.icon(R.drawable.ic_signal, R.color.brand, 44).apply {
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
                background = ui.round(R.color.brand_soft, 14)
            }
        stateTop.addView(signal)
        stateTop.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        badge =
            ui.text("確認中", 11f, R.color.muted, true).apply {
                setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6))
            }
        stateTop.addView(badge)
        body.addView(stateTop)
        title = ui.text("接続を確認しています", 23f, medium = true)
        body.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(22) })
        detail = ui.text("", 13f, R.color.muted).apply { setLineSpacing(ui.dp(3).toFloat(), 1f) }
        body.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        progress =
            LinearProgressIndicator(this).apply {
                isIndeterminate = true
                setIndicatorColor(ui.color(R.color.brand))
                trackColor = ui.color(R.color.divider)
                trackThickness = ui.dp(2)
                trackCornerRadius = ui.dp(1)
                visibility = View.GONE
            }
        body.addView(
            progress,
            LinearLayout.LayoutParams(-1, ui.dp(2)).apply { topMargin = ui.dp(16) },
        )
        time = ui.text("", 11f, R.color.muted)
        body.addView(
            time,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = ui.dp(16)
                bottomMargin = ui.dp(20)
            },
        )
        val launch = ui.action("NSGを開く", R.drawable.ic_open, true) { operate("enable", true) }
        actions.add(launch)
        body.addView(launch, LinearLayout.LayoutParams(-1, ui.dp(56)))
        val secondary = ui.horizontal()
        val reapply = ui.action("再適用", R.drawable.ic_refresh) { operate("enable") }
        val check = ui.action("状態を確認", R.drawable.ic_check) { operate("status") }
        actions.addAll(listOf(reapply, check))
        secondary.addView(reapply, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
        secondary.addView(check, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
        body.addView(secondary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
        errorDetails =
            ui.action("詳細を見る", R.drawable.ic_info) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("接続の詳細")
                        .setMessage(RootRunner.prefs(this).getString("last_message", ""))
                        .setPositiveButton("閉じる", null)
                        .show()
                }
                .apply { visibility = View.GONE }
        body.addView(errorDetails)
        hero.addView(body)
        content.addView(hero)

        ui.section(content, "自動化")
        automatic =
            MaterialSwitch(this).apply {
                contentDescription = "端末起動時に自動適用"
                isChecked = RootRunner.prefs(this@MainActivity).getBoolean("auto_boot", false)
                setOnCheckedChangeListener { _, checked ->
                    RootRunner.prefs(this@MainActivity)
                        .edit()
                        .putBoolean("auto_boot", checked)
                        .apply()
                    if (!checked)
                        getSystemService(JobScheduler::class.java).cancel(BootReceiver.JOB_ID)
                }
            }
        val autoCard = ui.card()
        autoCard.addView(
            ui.row("起動時に自動適用", "端末の起動後に一度だけ適用", R.drawable.ic_auto, trailing = automatic) {
                if (!busy) automatic.isChecked = !automatic.isChecked
            }
        )
        content.addView(autoCard)
        content.addView(
            ui.text("アプリを開いたときも自動で適用します。", 12f, R.color.muted),
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(ui.dp(6), ui.dp(12), ui.dp(6), 0)
            },
        )

        ui.section(content, "その他")
        val other = ui.column()
        other.addView(
            ui.row("DIAG許可を解除", null, R.drawable.ic_power, danger = true) { confirmDisable() }
        )
        other.addView(ui.divider())
        other.addView(
            ui.row("ライセンス", null, R.drawable.ic_document) {
                startActivity(Intent(this, LicensesActivity::class.java))
            }
        )
        val otherCard = ui.card()
        otherCard.addView(other)
        content.addView(otherCard)
        content.addView(
            ui.text("Galaxy DIAG  ·  ${BuildConfig.VERSION_NAME}", 11f, R.color.muted).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(24) },
        )
        ui.install(content)
        showSaved()
        if (savedInstanceState == null) operate("enable")
    }

    private fun showSaved() {
        val prefs = RootRunner.prefs(this)
        val success = prefs.getBoolean("last_success", false)
        val state = prefs.getString("last_connection", null)
        val legacyOpen =
            state == null && success && prefs.getString("last_operation", "") == "enable"
        val open = success && (state == "true" || legacyOpen)
        val closed =
            success &&
                (state == "false" ||
                    (state == null && prefs.getString("last_operation", "") == "disable"))
        val at = prefs.getLong("last_at", 0)
        title.text =
            when {
                open -> "測定の準備完了"
                closed -> "接続はOFFです"
                at > 0 -> "接続を確認できません"
                else -> "接続を準備します"
            }
        detail.text =
            when {
                open -> "NSGを開いて測定を始められます。"
                closed -> "「NSGを開く」で許可を適用できます。"
                at > 0 -> "root管理アプリの許可をご確認ください。"
                else -> "root権限で測定の接続を準備します。"
            }
        badge.text =
            when {
                open -> "接続済み"
                closed -> "OFF"
                else -> "要確認"
            }
        val tone = if (open) R.color.success else if (closed) R.color.muted else R.color.warning
        badge.setTextColor(ui.color(tone))
        badge.background =
            ui.round(
                if (open) R.color.success_soft
                else if (closed) R.color.icon_bg else R.color.warning_soft,
                20,
            )
        time.text =
            if (at == 0L) ""
            else
                "最終確認  " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(at))
        errorDetails.visibility = if (!success && at > 0) View.VISIBLE else View.GONE
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Galaxy DIAG ${BuildConfig.VERSION_NAME}")
            .setMessage(
                "GalaxyのDIAG接続を準備するrootアプリです。\n\n対応確認：SM-S948Q / Android 17\n\n初回はKernelSUなどのroot管理アプリでGalaxy DIAGを許可してください。常駐・定期通信は行いません。Samsung側が許可を閉じた場合は再適用できます。"
            )
            .setNeutralButton("外観") { _, _ -> showAppearance() }
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun showAppearance() {
        val values = intArrayOf(-1, 1, 2)
        val current = RootRunner.prefs(this).getInt("appearance_mode", -1)
        MaterialAlertDialogBuilder(this)
            .setTitle("外観")
            .setSingleChoiceItems(
                arrayOf("端末の設定に従う", "ライト", "ダーク"),
                values.indexOf(current).coerceAtLeast(0),
            ) { dialog, which ->
                RootRunner.prefs(this).edit().putInt("appearance_mode", values[which]).apply()
                dialog.dismiss()
                AppCompatDelegate.setDefaultNightMode(values[which])
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun confirmDisable() {
        if (busy) return
        MaterialAlertDialogBuilder(this)
            .setTitle("DIAG許可を解除しますか？")
            .setMessage("NSGなどのモデム測定が停止します。起動時の自動適用もOFFになります。")
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("解除") { _, _ ->
                automatic.isChecked = false
                operate("disable")
            }
            .show()
    }

    private fun operate(operation: String, launchNsg: Boolean = false) {
        if (busy) return
        busy = true
        title.text = if (operation == "disable") "接続を解除しています" else "接続を準備しています"
        detail.text = "数秒かかる場合があります。"
        badge.text = "処理中"
        badge.setTextColor(ui.color(R.color.brand))
        badge.background = ui.round(R.color.brand_soft, 20)
        progress.visibility = View.VISIBLE
        errorDetails.visibility = View.GONE
        actions.forEach { it.isEnabled = false }
        automatic.isEnabled = false
        RootRunner.executor.execute {
            val result = RootRunner.run(applicationContext, operation)
            val prefs = RootRunner.prefs(applicationContext)
            if (result.success && operation == "enable" && !prefs.getBoolean("initialized", false))
                prefs.edit().putBoolean("auto_boot", true).putBoolean("initialized", true).apply()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                busy = false
                actions.forEach { it.isEnabled = true }
                automatic.isEnabled = true
                automatic.isChecked = prefs.getBoolean("auto_boot", false)
                progress.visibility = View.GONE
                showSaved()
                if (launchNsg && result.success && result.open == true) {
                    val intent = packageManager.getLaunchIntentForPackage("com.qtrun.QuickTest")
                    if (intent == null)
                        MaterialAlertDialogBuilder(this)
                            .setMessage("NSGがインストールされていません。")
                            .setPositiveButton("OK", null)
                            .show()
                    else startActivity(intent)
                }
            }
        }
    }
}
