package com.mogas.timemanager

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/** Efeito de "carregar": encolhe ao tocar e volta com um pequeno ressalto. */
@SuppressLint("ClickableViewAccessibility")
fun View.pressable() {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.96f).scaleY(0.96f).setStartDelay(0).setDuration(90)
                    .setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(220)
                    .setInterpolator(OvershootInterpolator(2f)).start()
        }
        false
    }
}

/** Entrada: surge a subir e a ganhar opacidade. */
fun View.enter(delay: Long = 0L) {
    alpha = 0f
    translationY = context.dp(24).toFloat()
    animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(380)
        .setInterpolator(DecelerateInterpolator()).start()
}
