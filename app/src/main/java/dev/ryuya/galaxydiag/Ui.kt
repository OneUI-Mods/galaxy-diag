package dev.ryuya.galaxydiag

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** Shared, resource-backed surfaces for both day and night modes. */
class Ui(val activity: AppCompatActivity) {
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    fun color(id: Int) = ContextCompat.getColor(activity, id)

    fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    fun horizontal() =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    fun text(value: String, size: Float, tone: Int = R.color.ink, medium: Boolean = false) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color(tone))
            includeFontPadding = false
            if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    fun round(tone: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color(tone))
            cornerRadius = dp(radius).toFloat()
        }

    fun icon(drawable: Int, tone: Int = R.color.muted, size: Int = 24) =
        ImageView(activity).apply {
            setImageResource(drawable)
            imageTintList = ColorStateList.valueOf(color(tone))
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    fun iconButton(drawable: Int, label: String, action: () -> Unit) =
        MaterialButton(activity).apply {
            icon = ContextCompat.getDrawable(activity, drawable)
            iconSize = dp(22)
            iconPadding = 0
            iconTint = ColorStateList.valueOf(color(R.color.muted))
            contentDescription = label
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(24)
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { action() }
        }

    fun action(label: String, drawable: Int, filled: Boolean = false, action: () -> Unit) =
        MaterialButton(activity).apply {
            text = label
            textSize = if (filled) 16f else 14f
            isAllCaps = false
            icon = ContextCompat.getDrawable(activity, drawable)
            iconSize = dp(20)
            iconPadding = dp(8)
            cornerRadius = dp(16)
            insetTop = 0
            insetBottom = 0
            minHeight = 0
            minimumHeight = 0
            backgroundTintList =
                if (filled)
                    ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
                        intArrayOf(color(R.color.primary), color(R.color.primary_disabled)),
                    )
                else ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            val ink = if (filled) R.color.on_primary else R.color.brand
            val tones =
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(color(ink), color(R.color.muted)),
                )
            setTextColor(tones)
            iconTint = tones
            setOnClickListener { action() }
        }

    fun card(tone: Int = R.color.surface) =
        MaterialCardView(activity).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(color(tone))
        }

    fun divider() =
        View(activity).apply {
            setBackgroundColor(color(R.color.divider))
            layoutParams =
                LinearLayout.LayoutParams(-1, dp(1)).apply {
                    marginStart = dp(64)
                    marginEnd = dp(20)
                }
        }

    fun section(parent: LinearLayout, title: String) {
        parent.addView(
            text(title, 13f, R.color.muted, true),
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(28), 0, dp(10)) },
        )
    }

    fun row(
        title: String,
        subtitle: String?,
        drawable: Int,
        danger: Boolean = false,
        trailing: View? = null,
        onClick: (() -> Unit)? = null,
    ): LinearLayout {
        val row =
            horizontal().apply {
                minimumHeight = dp(if (subtitle == null) 64 else 80)
                setPadding(dp(18), dp(14), dp(18), dp(14))
            }
        val leading = icon(drawable, if (danger) R.color.danger else R.color.muted, 22)
        row.addView(leading)
        val labels = column().apply { setPadding(dp(16), 0, dp(12), 0) }
        labels.addView(text(title, 16f, if (danger) R.color.danger else R.color.ink, true))
        if (subtitle != null)
            labels.addView(
                text(subtitle, 12f, R.color.muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) },
            )
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(trailing ?: icon(R.drawable.ic_chevron, R.color.muted, 18))
        if (onClick != null) {
            val ripple = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            row.setBackgroundResource(ripple.resourceId)
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener { onClick() }
        }
        return row
    }

    fun install(content: View, ime: Boolean = false) {
        val scroll =
            ScrollView(activity).apply {
                isFillViewport = true
                clipToPadding = false
                setBackgroundColor(color(R.color.page))
                addView(content)
            }
        activity.setContentView(scroll)
        WindowInsetsControllerCompat(activity.window, scroll).apply {
            val light = ColorUtils.calculateLuminance(color(R.color.page)) > .5
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val mask =
                WindowInsetsCompat.Type.systemBars() or
                    if (ime) WindowInsetsCompat.Type.ime() else 0
            val bars = insets.getInsets(mask)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
