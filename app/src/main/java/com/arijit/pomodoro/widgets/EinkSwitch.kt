package com.arijit.pomodoro.widgets

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.materialswitch.MaterialSwitch

// SwitchCompat animates the thumb slide over 250ms via an internal
// ValueAnimator on every setChecked() call. E Ink can't render that
// smoothly, so it just reads as a smear - jumpDrawablesToCurrentState()
// is SwitchCompat's own mechanism for ending that animator instantly.
class EinkSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialSwitchStyle,
) : MaterialSwitch(context, attrs, defStyleAttr) {

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        jumpDrawablesToCurrentState()
    }
}
