package com.arijit.pomodoro.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.arijit.pomodoro.R

// A plain hand-drawn toggle, ported from Katapult's SettingsToggleRow: a
// 40x22dp pill (border-only unchecked, filled checked) with a 14dp dot.
// Deliberately NOT a MaterialSwitch/SwitchCompat subclass - that widget
// forces a 48dp minimum touch target and carries its own ripple/animator
// overhead no matter what, which read as oversized and sluggish on e-ink.
class EinkToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val trackWidthPx = dp(40f)
    private val trackHeightPx = dp(22f)
    private val dotSizePx = dp(14f)
    private val dotPaddingPx = dp(4f)
    private val borderWidthPx = dp(2f)

    private val inkColor: Int
    private val surfaceColor: Int

    var isChecked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private var onCheckedChangeListener: ((EinkToggle, Boolean) -> Unit)? = null

    fun setOnCheckedChangeListener(listener: ((EinkToggle, Boolean) -> Unit)?) {
        onCheckedChangeListener = listener
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidthPx
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackRect = RectF()

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.EinkToggle, defStyleAttr, 0)
        inkColor = a.getColor(R.styleable.EinkToggle_inkColor, 0xFF000000.toInt())
        surfaceColor = a.getColor(R.styleable.EinkToggle_surfaceColor, 0xFFFFFFFF.toInt())
        a.recycle()

        isClickable = true
        isFocusable = true
        contentDescription = "toggle"
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(trackWidthPx.toInt(), trackHeightPx.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = trackHeightPx / 2f
        trackRect.set(
            borderWidthPx / 2f,
            borderWidthPx / 2f,
            trackWidthPx - borderWidthPx / 2f,
            trackHeightPx - borderWidthPx / 2f,
        )

        if (isChecked) {
            trackPaint.color = inkColor
            canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
        } else {
            borderPaint.color = inkColor
            canvas.drawRoundRect(trackRect, radius, radius, borderPaint)
        }

        val dotCx = if (isChecked) {
            trackWidthPx - dotPaddingPx - dotSizePx / 2f
        } else {
            dotPaddingPx + dotSizePx / 2f
        }
        dotPaint.color = if (isChecked) surfaceColor else inkColor
        canvas.drawCircle(dotCx, trackHeightPx / 2f, dotSizePx / 2f, dotPaint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        isChecked = !isChecked
        onCheckedChangeListener?.invoke(this, isChecked)
        return true
    }
}
