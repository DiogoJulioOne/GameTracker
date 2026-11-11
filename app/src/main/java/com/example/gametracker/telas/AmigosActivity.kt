package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.R
import com.example.gametracker.utils.VisualGameTracker
import com.example.gametracker.dados.SocialRepository
import com.example.gametracker.utils.AvatarStorage
import com.example.gametracker.utils.SocialInterface as UI
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/** Amigos registrados no Firestore, com acesso direto ao perfil e ao chat. */
class AmigosActivity : AppCompatActivity() {
    private val social = SocialRepository()
    private lateinit var lista: LinearLayout
    private var ouvinte: ListenerRegistration? = null
    private var versao = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rolagem = ScrollView(this).apply { setBackgroundColor(getColor(R.color.fundo)) }
        lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }
        rolagem.addView(lista)
        VisualGameTracker.envolver(this, rolagem)
    }
    override fun onStart() {
        super.onStart()
        val token = ++versao
        lista.removeAllViews()
        lista.addView(UI.botao(this, "← Voltar") { finish() })
        lista.addView(UI.texto(this, "Meus amigos", 26f))
        lista.addView(UI.texto(this, "Carregando amigos...", 15f, true))
        ouvinte = social.acompanharAmizades { amizades, erro ->
            if (token != versao) return@acompanharAmizades
            lista.removeViews(2, lista.childCount - 2)
            if (erro != null) {
                lista.addView(UI.texto(this, "Não foi possível carregar os amigos.", 15f, true))
            } else if (amizades.isEmpty()) {
                lista.addView(UI.texto(this, "Suas amizades aparecerão aqui depois de curtidas recíprocas.", 15f, true))
            } else amizades.forEach { amizade ->
                val outroId = amizade.participantes.firstOrNull { it != social.uid } ?: return@forEach
                val cartao = UI.painel(this, "Jogador")
                val imagem = ImageView(this).apply {
                    setImageResource(R.drawable.avatar_real_1)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                cartao.addView(imagem, LinearLayout.LayoutParams(dp(72), dp(72)))
                cartao.addView(UI.botao(this, "Ver perfil") {
                    startActivity(Intent(this, PerfilPublicoActivity::class.java)
                        .putExtra(PerfilPublicoActivity.EXTRA_UID, outroId))
                })
                cartao.addView(UI.botao(this, "Conversar") {
                    startActivity(Intent(this, ChatActivity::class.java)
                        .putExtra(ChatActivity.EXTRA_AMIZADE_ID, amizade.id)
                        .putExtra(ChatActivity.EXTRA_NOME, (cartao.getChildAt(0) as android.widget.TextView).text.toString()))
                })
                lista.addView(cartao, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                lifecycleScope.launch {
                    try {
                        val jogador = social.perfilPublico(outroId)
                        if (token != versao || jogador == null) return@launch
                        (cartao.getChildAt(0) as android.widget.TextView).text = jogador.nome
                        AvatarStorage.mostrar(this@AmigosActivity, imagem, jogador.avatar)
                    } catch (_: Exception) { }
                }
            }
        }
    }
    override fun onStop() { ++versao; ouvinte?.remove(); ouvinte = null; super.onStop() }
    private fun dp(n: Int) = UI.dp(this, n)
}
