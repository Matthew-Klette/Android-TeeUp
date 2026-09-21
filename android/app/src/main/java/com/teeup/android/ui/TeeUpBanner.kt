package com.teeup.android.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.R

/**
 * A small in-app slide-down banner, styled to match the rest of the app,
 * used in place of the system [android.widget.Toast] wherever this app
 * surfaces a one-line success/info/error message to the user. Same message
 * text and timing as the Toasts it replaces — this only changes how the
 * message is presented.
 */
object TeeUpBanner {

    fun show(activity: Activity, message: String, isError: Boolean = false) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val accentColorRes = if (isError) R.color.teeup_danger else R.color.teeup_accent
        val accentColor = activity.resources.getColor(accentColorRes, activity.theme)

        val accentBar = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(accentColor)
        }

        val text = TextView(activity).apply {
            text = message
            setTextColor(activity.resources.getColor(R.color.teeup_text_primary, activity.theme))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_banner)
            clipToOutline = true
            elevation = activity.resources.getDimension(R.dimen.elevation_banner)
            addView(accentBar)
            addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val container = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                topMargin = dp(16)
                marginStart = dp(16)
                marginEnd = dp(16)
            }
            addView(row)
            isClickable = true
        }

        root.addView(container)

        container.alpha = 0f
        container.translationY = dp(-24).toFloat()
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .start()

        val dismiss = {
            container.animate()
                .alpha(0f)
                .translationY(dp(-24).toFloat())
                .setDuration(180)
                .withEndAction { (container.parent as? ViewGroup)?.removeView(container) }
                .start()
        }

        container.setOnClickListener { dismiss() }

        val durationMs = if (isError) 3200L else 2200L
        Handler(Looper.getMainLooper()).postDelayed({
            if (container.parent != null) dismiss()
        }, durationMs)
    }
}
