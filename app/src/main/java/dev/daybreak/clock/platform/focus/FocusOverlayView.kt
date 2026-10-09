package dev.daybreak.clock.platform.focus

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import dev.daybreak.clock.R
import java.util.Locale

/** The blocking surface only renders state; all enforcement stays in the service/engine. */
internal class FocusOverlayView(
    context: Context,
    onHome: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) : ScrollView(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val paper = (if (dark) "#111716" else "#F4F7F4").toColorInt()
    private val ink = (if (dark) "#EAF1ED" else "#17251C").toColorInt()
    private val accent = (if (dark) "#9BD5BA" else "#28664A").toColorInt()
    private val muted = (if (dark) "#B3C2B9" else "#4D5E51").toColorInt()
    private val surface = (if (dark) "#202A25" else "#E5EDE6").toColorInt()
    private val chinese = ResourcesCompat.getFont(context, R.font.wenkai_regular)
    private val numerals = ResourcesCompat.getFont(context, R.font.gelasio_regular)
    private val column = object : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(dp(480))
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.getMode(widthMeasureSpec)), heightMeasureSpec)
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private lateinit var target: TextView
    private lateinit var countdown: TextView
    private lateinit var units: TextView
    private lateinit var recovery: TextView
    private lateinit var waiting: TextView
    private lateinit var release: Button
    private lateinit var cancel: Button
    private var lastReleasePending: Boolean? = null

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        setBackgroundColor(paper)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        val frame = FrameLayout(context).apply {
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        }
        addView(frame, ViewGroup.LayoutParams(-1, -2))
        frame.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(dp(24) + bars.left, dp(24) + bars.top, dp(24) + bars.right, dp(24) + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(dp(24) + insets.systemWindowInsetLeft, dp(24) + insets.systemWindowInsetTop,
                    dp(24) + insets.systemWindowInsetRight, dp(24) + insets.systemWindowInsetBottom)
            }
            insets
        }

        label("朝醒 · 专注时段", 14f, accent).apply { letterSpacing = .08f }
        column.addView(ImageView(context).apply {
            setImageResource(R.drawable.focus_quiet_path)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(264), dp(if (resources.configuration.screenHeightDp < 650) 124 else 160)).apply {
            topMargin = dp(20)
        })
        label("把时间留给此刻", 28f, ink, top = 16).apply {
            typeface = Typeface.create(chinese, Typeface.BOLD)
            isAccessibilityHeading = true
        }
        target = label("专注窗口仍在进行", 14f, muted, top = 8)
        label("距离恢复还有", 12f, muted, top = 28)
        countdown = label("", 64f, accent, top = 4).apply {
            typeface = numerals
            fontFeatureSettings = "'tnum'"
            setSingleLine()
            setAutoSizeTextTypeUniformWithConfiguration(32, 64, 1, TypedValue.COMPLEX_UNIT_SP)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        units = label("分 · 秒", 12f, muted, top = 0).apply {
            letterSpacing = .2f
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        recovery = label("", 14f, muted, top = 20, fullWidth = false).apply {
            background = shape(surface, 12)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        button("返回桌面", primary = true, top = 28).setOnClickListener { onHome() }
        label("电话和桌面随时可用", 12f, muted, top = 12)
        waiting = label("", 14f, muted, top = 20).apply { visibility = GONE }
        release = button("应急解除 · 等待 60 秒", primary = false, top = 12).apply {
            setOnClickListener { onRelease() }
        }
        cancel = button("取消等待", primary = false, top = 0).apply {
            visibility = GONE
            setOnClickListener { onCancel() }
        }
    }

    private fun shape(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
    }

    private fun label(value: String, size: Float, color: Int, top: Int = 0, fullWidth: Boolean = true) =
        TextView(context).apply {
            text = value
            textSize = size
            typeface = chinese
            setTextColor(color)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setLineSpacing(dp(4).toFloat(), 1f)
            column.addView(this, LinearLayout.LayoutParams(if (fullWidth) -1 else -2, -2).apply { topMargin = dp(top) })
        }

    private fun button(value: String, primary: Boolean, top: Int) = Button(context).apply {
        text = value
        textSize = if (primary) 16f else 14f
        isAllCaps = false
        typeface = chinese
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(muted, if (primary) (if (dark) "#112C21" else "#FFFFFF").toColorInt() else muted),
        ))
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(40, 155, 213, 186)),
            shape(if (primary) accent else Color.TRANSPARENT, 20), null)
        clipToOutline = true
        minimumHeight = dp(if (primary) 56 else 48)
        minimumWidth = 0
        setPadding(dp(16), dp(12), dp(16), dp(12))
        column.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    fun render(seconds: Long, endTime: String, appLabel: String, releasePending: Boolean, remainingMillis: Long) {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val remainder = seconds % 60
        target.text = context.getString(R.string.focus_blocked_app, appLabel)
        countdown.text = if (hours > 0) String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder)
            else String.format(Locale.ROOT, "%02d:%02d", minutes, remainder)
        countdown.contentDescription = if (hours > 0) "剩余 $hours 小时 $minutes 分 $remainder 秒"
            else "剩余 $minutes 分 $remainder 秒"
        units.text = if (hours > 0) "时 · 分 · 秒" else "分 · 秒"
        recovery.text = context.getString(R.string.focus_recovery_at, endTime)
        waiting.visibility = if (releasePending) VISIBLE else GONE
        waiting.text = if (remainingMillis > 0) "等待期间继续锁定\n重启会重新计时"
            else "等待已完成，再次确认后解除\n仅影响发起等待时的窗口"
        release.text = if (!releasePending) "应急解除 · 等待 60 秒"
            else if (remainingMillis > 0) "应急解除 · 还需 ${(remainingMillis + 999) / 1000} 秒"
            else "确认应急解除"
        release.isEnabled = !releasePending || remainingMillis == 0L
        if (lastReleasePending != releasePending) {
            release.background = RippleDrawable(ColorStateList.valueOf(Color.argb(40, 155, 213, 186)),
                shape(if (releasePending) surface else Color.TRANSPARENT, 20), null)
            lastReleasePending = releasePending
        }
        cancel.visibility = if (releasePending) VISIBLE else GONE
    }
}
