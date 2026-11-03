package dev.ryuya.galaxydiag

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray

class LicensesActivity : AppCompatActivity() {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(24), dp(24), dp(24))
            }
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        layout.addView(
            MaterialButton(this).apply {
                text = "戻る"
                setOnClickListener { finish() }
            }
        )
        layout.addView(
            TextView(this).apply {
                text = "ライセンス"
                textSize = 28f
            }
        )
        val search =
            EditText(this).apply {
                hint = "ライブラリを検索"
                setSingleLine()
            }
        layout.addView(search)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(list)
        val data =
            assets.open("legal/libraries.json").bufferedReader().use { JSONArray(it.readText()) }
        val entries = (0 until data.length()).map { data.getJSONObject(it) }
        fun render(query: String) {
            list.removeAllViews()
            entries
                .filter {
                    it.getString("id").contains(query, true) ||
                        it.optString("title").contains(query, true)
                }
                .forEach { item ->
                    list.addView(
                        MaterialButton(this).apply {
                            text =
                                item.optString("title", item.getString("id")) +
                                    " " +
                                    item.optString("version")
                            setOnClickListener {
                                val license =
                                    assets.open(item.getString("asset")).bufferedReader().use {
                                        it.readText()
                                    }
                                val notice = item.optString("notice")
                                val body =
                                    TextView(this@LicensesActivity).apply {
                                        text =
                                            item.getString("id") +
                                                "\n" +
                                                item.getString("license") +
                                                "\n\n" +
                                                notice +
                                                "\n\n" +
                                                license
                                        setPadding(dp(24), dp(16), dp(24), dp(16))
                                        setTextIsSelectable(true)
                                    }
                                MaterialAlertDialogBuilder(this@LicensesActivity)
                                    .setTitle(item.optString("title"))
                                    .setView(
                                        ScrollView(this@LicensesActivity).apply { addView(body) }
                                    )
                                    .setPositiveButton("閉じる", null)
                                    .show()
                            }
                        }
                    )
                }
        }
        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    render(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )
        render("")
    }
}
