package com.example.gametracker

import android.content.Intent
import android.view.View
import com.example.gametracker.utils.AnimacoesLeves
import androidx.appcompat.app.AppCompatActivity
import com.example.gametracker.telas.ChatsActivity
import com.example.gametracker.telas.GruposActivity
import com.example.gametracker.telas.MatchActivity
import com.example.gametracker.telas.PerfilActivity

object BottomNavigationHelper {
    enum class BottomTab {
        BIBLIOTECA,
        DESCOBRIR,
        PERFIL,
        GRUPOS,
        CHAT
    }

    fun configurar(activity: AppCompatActivity, tabSelecionado: BottomTab) {
        val itens = listOf(
            R.id.navBiblioteca to BottomTab.BIBLIOTECA,
            R.id.navDescobrir to BottomTab.DESCOBRIR,
            R.id.navPerfil to BottomTab.PERFIL,
            R.id.navGrupos to BottomTab.GRUPOS,
            R.id.navChat to BottomTab.CHAT
        )

        itens.forEach { (id, tab) ->
            val item = activity.findViewById<View>(id) ?: return@forEach
            val selecionado = tab == tabSelecionado
            item.isSelected = selecionado
            item.setOnClickListener { AnimacoesLeves.clique(item); abrir(activity, tab) }
            item.alpha = 1f
            if (item is android.view.ViewGroup) {
                for (i in 0 until item.childCount) {
                    val filho = item.getChildAt(i)
                    val cor = activity.getColor(if (selecionado) R.color.black else R.color.nav_texto_inativo)
                    (filho as? android.widget.TextView)?.setTextColor(cor)
                    (filho as? android.widget.ImageView)?.setColorFilter(cor)
                }
            }
            item.setBackgroundResource(
                if (selecionado) R.drawable.bg_nav_ativo else R.drawable.bg_nav_inativo
            )
        }
    }

    private fun abrir(activity: AppCompatActivity, tab: BottomTab) {
        val destino = when (tab) {
            BottomTab.BIBLIOTECA -> MainActivity::class.java
            BottomTab.DESCOBRIR -> MatchActivity::class.java
            BottomTab.PERFIL -> PerfilActivity::class.java
            BottomTab.GRUPOS -> GruposActivity::class.java
            BottomTab.CHAT -> ChatsActivity::class.java
        }

        if (activity.javaClass == destino) {
            return
        }

        val intent = Intent(activity, destino)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}
