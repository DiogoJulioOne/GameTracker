package com.example.gametracker.utils

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.gametracker.R

/** Elementos nativos leves, consistentes com o tema do GameTracker. */
object SocialInterface {
    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value).toInt()

    fun texto(context: Context, value: String, size: Float = 15f, subdued: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(context.getColor(if (subdued) R.color.texto_secundario else R.color.texto))
            setPadding(dp(context, 3), dp(context, 7), dp(context, 3), dp(context, 7))
        }

    fun botao(context: Context, value: String, action: () -> Unit): Button =
        Button(context).apply {
            text = value
            isAllCaps = false
            setTextColor(context.getColor(R.color.black))
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_botao_principal)
            setOnClickListener {
                AnimacoesLeves.clique(this)
                action()
            }
            minHeight = dp(context, 48)
        }

    fun painel(context: Context, titulo: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card_jogo)
        setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 14))
        addView(texto(context, titulo, 19f).apply { setTypeface(null, Typeface.BOLD) })
    }

    fun linha(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
}
