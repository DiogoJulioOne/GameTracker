package com.example.gametracker.utils

import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.gametracker.R

/** Cabeçalho comum para telas construídas em Kotlin, fora dos layouts XML. */
object VisualGameTracker {
    fun envolver(activity: AppCompatActivity, conteudo: ScrollView) {
        val densidade = activity.resources.displayMetrics.density
        fun dp(n: Int) = (n * densidade).toInt()
        val principal = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_tela_gametracker)
        }
        val topo = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(12))
        }
        val marca = MarcaGameTrackerView(activity).apply { textSize = 28f }
        topo.addView(marca, LinearLayout.LayoutParams(-1, -2))
        val divisor = View(activity).apply { setBackgroundColor(activity.getColor(R.color.borda)) }
        topo.addView(divisor, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12) })
        principal.addView(topo, LinearLayout.LayoutParams(-1, -2))
        principal.addView(conteudo, LinearLayout.LayoutParams(-1, 0, 1f))
        activity.setContentView(principal)
    }
}
