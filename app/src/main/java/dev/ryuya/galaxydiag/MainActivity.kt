package dev.ryuya.galaxydiag

import android.app.job.JobScheduler
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var time: TextView
    private lateinit var automatic: MaterialSwitch
    private val actions = mutableListOf<MaterialButton>()
    private var busy = false

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(36), dp(24), dp(32))
            }
        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
                addView(column)
            }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        fun text(value: String, size: Float) =
            TextView(this).apply {
                text = value
                textSize = size
                setPadding(0, dp(8), 0, dp(8))
            }
        column.addView(text("Galaxy DIAG", 34f))
        column.addView(text("モデム測定の接続準備", 17f))
        val card = MaterialCardView(this).apply { radius = dp(24).toFloat() }
        val body =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
            }
        status = text("未確認", 20f)
        time = text("", 13f)
        body.addView(status)
        body.addView(time)
        card.addView(body)
        column.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(28), 0, dp(20)) },
        )
        automatic =
            MaterialSwitch(this).apply {
                text = "端末起動後に自動適用"
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
        column.addView(automatic)
        column.addView(text("このアプリを開くと許可を自動適用します。端末起動後の自動適用は、初回の成功時に有効になります。", 14f))
        fun button(label: String, action: () -> Unit) {
            val view =
                MaterialButton(this).apply {
                    text = label
                    minHeight = dp(54)
                    setOnClickListener { action() }
                }
            actions.add(view)
            column.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        button("許可してNSGを開く") { operate("enable", true) }
        button("許可を再適用") { operate("enable") }
        button("接続状態を確認") { operate("status") }
        button("許可を解除") {
            MaterialAlertDialogBuilder(this)
                .setTitle("DIAG許可を解除")
                .setMessage("NSGなどのモデム測定が停止します。端末起動後の自動適用もOFFにします。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("解除") { _, _ ->
                    automatic.isChecked = false
                    operate("disable")
                }
                .show()
        }
        column.addView(
            text("SM-S948Q・Android 17で動作確認。rootが必要です。初回はroot管理アプリでGalaxy DIAGを許可してください。", 14f)
        )
        column.addView(text("常駐・定期通信は行いません。Samsung側の状態変更で許可が閉じた場合は再適用してください。", 14f))
        button("ライセンス") { startActivity(Intent(this, LicensesActivity::class.java)) }
        column.addView(text("バージョン ${BuildConfig.VERSION_NAME}", 13f))
        showSaved()
        if (savedInstanceState == null) operate("enable")
    }

    private fun showSaved() {
        val prefs = RootRunner.prefs(this)
        status.text = prefs.getString("last_message", "未確認")
        val at = prefs.getLong("last_at", 0)
        time.text =
            if (at == 0L) "" else "最終確認：" + DateFormat.getDateTimeInstance().format(Date(at))
    }

    private fun operate(operation: String, launchNsg: Boolean = false) {
        if (busy) return
        busy = true
        status.text = "処理中… 初回はroot許可を確認してください。"
        actions.forEach { it.isEnabled = false }
        automatic.isEnabled = false
        RootRunner.executor.execute {
            val result = RootRunner.run(applicationContext, operation)
            val prefs = RootRunner.prefs(applicationContext)
            if (
                result.success && operation == "enable" && !prefs.getBoolean("initialized", false)
            ) {
                prefs.edit().putBoolean("auto_boot", true).putBoolean("initialized", true).apply()
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                busy = false
                actions.forEach { it.isEnabled = true }
                automatic.isEnabled = true
                automatic.isChecked = prefs.getBoolean("auto_boot", false)
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
