package com.example.gametracker.utils

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

/** Arte abstrata leve para capas sem imagens licenciadas da API. */
object CapaJogo {
    private val paletas = arrayOf(
        intArrayOf(0xFF552C90.toInt(), 0xFF242B58.toInt()),
        intArrayOf(0xFF655096.toInt(), 0xFF253754.toInt()),
        intArrayOf(0xFF74445D.toInt(), 0xFF2B254F.toInt()),
        intArrayOf(0xFF336878.toInt(), 0xFF272B65.toInt()),
        intArrayOf(0xFF744D3E.toInt(), 0xFF40265B.toInt())
    )

    fun aplicar(view: View, titulo: String, raioDp: Float = 16f) {
        val paleta = paletas[(titulo.lowercase().hashCode() and Int.MAX_VALUE) % paletas.size]
        val densidade = view.resources.displayMetrics.density
        view.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, paleta).apply {
            cornerRadius = raioDp * densidade
            setStroke((1 * densidade).toInt().coerceAtLeast(1), Color.argb(95, 209, 180, 255))
        }
    }
}
