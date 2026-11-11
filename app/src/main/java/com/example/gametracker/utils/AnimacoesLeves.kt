package com.example.gametracker.utils

import android.view.View

/** Animações rápidas de alpha/translação: sem renderizações pesadas. */
object AnimacoesLeves {
    fun entrada(view: View) {
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            !android.animation.ValueAnimator.areAnimatorsEnabled()) return
        view.alpha = 0f
        view.translationY = 12f * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setDuration(200L).start()
    }
    fun clique(view: View) {
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            !android.animation.ValueAnimator.areAnimatorsEnabled()) return
        view.animate().scaleX(.97f).scaleY(.97f).setDuration(70).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
        }.start()
    }
}
