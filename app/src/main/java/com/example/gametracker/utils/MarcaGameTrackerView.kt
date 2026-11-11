package com.example.gametracker.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/** Logotipo tipográfico editável com degradê lilás/rosa da referência visual. */
class MarcaGameTrackerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {
    init {
        text = "GameTracker"
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        includeFontPadding = false
        letterSpacing = -0.035f
    }

    override fun onDraw(canvas: Canvas) {
        if (width > 0) {
            paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
                intArrayOf(0xFFC68FFF.toInt(), 0xFFE8BAFF.toInt(), 0xFFD0A1FB.toInt()),
                null, Shader.TileMode.CLAMP)
        }
        super.onDraw(canvas)
    }
}
