package dev.ryuya.galaxydiag

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray

class LicensesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = Ui(this)
        val content = ui.column().apply { setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(28)) }
        val toolbar = ui.horizontal()
        toolbar.addView(ui.iconButton(R.drawable.ic_back, "戻る") { finish() })
        toolbar.addView(ui.text("Galaxy DIAG", 14f, R.color.muted))
        content.addView(toolbar)
        content.addView(
            ui.text("ライセンス", 32f, medium = true),
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = ui.dp(24)
                bottomMargin = ui.dp(22)
            },
        )
        val searchBox =
            TextInputLayout(this).apply {
                hint = "ライブラリを検索"
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
                setBoxBackgroundColorResource(R.color.surface)
                setBoxCornerRadii(
                    ui.dp(16).toFloat(),
                    ui.dp(16).toFloat(),
                    ui.dp(16).toFloat(),
                    ui.dp(16).toFloat(),
                )
                setStartIconDrawable(R.drawable.ic_search)
                endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            }
        val search =
            TextInputEditText(searchBox.context).apply {
                setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER
                imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            }
        searchBox.addView(search)
        content.addView(searchBox)
        val count = ui.text("", 12f, R.color.muted)
        content.addView(
            count,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(ui.dp(4), ui.dp(18), 0, ui.dp(10))
            },
        )
        val list = ui.column()
        val card = ui.card()
        card.addView(list)
        content.addView(card)
        ui.install(content, ime = true)
        val data =
            assets.open("legal/libraries.json").bufferedReader().use { JSONArray(it.readText()) }
        val entries = (0 until data.length()).map { data.getJSONObject(it) }
        fun render(query: String) {
            list.removeAllViews()
            val matching = entries.filter { item ->
                listOf("id", "title", "version", "license").any {
                    item.optString(it).contains(query, true)
                }
            }
            count.text = "${matching.size}件のライブラリ"
            if (matching.isEmpty())
                list.addView(
                    ui.text("一致するライブラリはありません", 14f, R.color.muted).apply {
                        setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(24))
                    }
                )
            matching.forEachIndexed { index, item ->
                if (index > 0) list.addView(ui.divider())
                val label = item.optString("title", item.getString("id"))
                val version = item.optString("version")
                list.addView(
                    ui.row(
                        label,
                        "$version  ·  ${item.getString("license")}",
                        R.drawable.ic_document,
                    ) {
                        getSystemService(InputMethodManager::class.java)
                            .hideSoftInputFromWindow(search.windowToken, 0)
                        search.clearFocus()
                        val license =
                            assets.open(item.getString("asset")).bufferedReader().use {
                                it.readText()
                            }
                        val body =
                            TextView(this).apply {
                                text =
                                    item.getString("id") +
                                        "\n" +
                                        version +
                                        "\n\n" +
                                        item.optString("notice") +
                                        "\n\n" +
                                        license
                                textSize = 13f
                                setTextColor(ui.color(R.color.ink))
                                setLineSpacing(ui.dp(3).toFloat(), 1f)
                                setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16))
                                setTextIsSelectable(true)
                            }
                        val scroll = ScrollView(this).apply { addView(body) }
                        val dialog =
                            MaterialAlertDialogBuilder(this)
                                .setTitle(label)
                                .setView(scroll)
                                .setPositiveButton("閉じる", null)
                                .create()
                        dialog.setOnShowListener {
                            scroll.layoutParams =
                                scroll.layoutParams.apply {
                                    height = (resources.displayMetrics.heightPixels * .6f).toInt()
                                }
                        }
                        dialog.show()
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
