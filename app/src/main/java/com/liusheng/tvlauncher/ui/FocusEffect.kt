package com.liusheng.tvlauncher.ui

import android.animation.TimeInterpolator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/** 卡片和列表项共用的焦点、按压动画。 */
object FocusEffect {

    const val FOCUS_SCALE = 1.06f

    private const val PRESS_SCALE = 0.93f
    private const val PRESS_MS = 80L
    private const val FOCUS_MS = 200L
    private const val RELEASE_MS = 280L

    private const val OVERSHOOT_FOCUS = 1.7f
    private const val OVERSHOOT_RELEASE = 2.6f

    /** [onFocusRing] 用于同步背景上的焦点描边。 */
    fun apply(
        view: View,
        onFocusRing: ((Boolean) -> Unit)? = null,
        onClick: (() -> Unit)? = null
    ) {
        view.setOnFocusChangeListener { v, hasFocus ->
            onFocusRing?.invoke(hasFocus)
            if (hasFocus) {
                scale(v, FOCUS_SCALE, FOCUS_MS, OvershootInterpolator(OVERSHOOT_FOCUS))
            } else {
                scale(v, 1f, FOCUS_MS, DecelerateInterpolator())
            }
        }

        if (onClick == null) return

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 触摸时先转移焦点，避免旧项的描边残留。
                    if (!v.requestFocusFromTouch() && !v.isFocused) onFocusRing?.invoke(true)
                    scale(v, PRESS_SCALE, PRESS_MS, DecelerateInterpolator())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (v.isFocused) {
                        scale(v, FOCUS_SCALE, RELEASE_MS, OvershootInterpolator(OVERSHOOT_RELEASE))
                    } else {
                        scale(v, 1f, RELEASE_MS, DecelerateInterpolator())
                        onFocusRing?.invoke(false)
                    }
                }
            }
            false // 交给 View 处理点击。
        }

        view.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    scale(v, PRESS_SCALE, PRESS_MS, DecelerateInterpolator())
                } else {
                    scale(v, FOCUS_SCALE, RELEASE_MS, OvershootInterpolator(OVERSHOOT_RELEASE))
                }
            }
            false
        }

        view.setOnClickListener { onClick() }
    }

    /** 连续动画直接从当前缩放值接续。 */
    private fun scale(view: View, target: Float, duration: Long, interpolator: TimeInterpolator) {
        view.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .start()
    }
}
