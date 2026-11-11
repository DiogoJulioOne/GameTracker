package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.R
import com.example.gametracker.utils.VisualGameTracker
import com.example.gametracker.dados.SocialRepository
import com.example.gametracker.utils.AvatarStorage
import com.example.gametracker.utils.SocialInterface as UI
import kotlinx.coroutines.launch

/** Mostra exclusivamente os campos publicados em perfisPublicos. */
class PerfilPublicoActivity : AppCompatActivity() {
    private val social = SocialRepository()
    private lateinit var corpo: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val jogadorId = intent.getStringExtra(EXTRA_UID).orEmpty()
        if (jogadorId.isBlank()) { finish(); return }
        if (jogadorId == social.uid) {
            startActivity(Intent(this, PerfilActivity::class.java)); finish(); return
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.fundo))
            setFillViewport(true)
        }
        corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(30))
        }
        scroll.addView(corpo)
        VisualGameTracker.envolver(this, scroll)
        corpo.addView(UI.botao(this, "← Voltar") { finish() })
        corpo.addView(UI.texto(this, "Carregando perfil...", 16f, true))
        lifecycleScope.launch {
            try {
                val jogador = social.perfilPublico(jogadorId) ?: error("Perfil indisponível.")
                val comuns = social.jogosEmComum(jogador.jogosPublicos)
                val amizade = social.amizadeCom(jogadorId)
                if (isFinishing || isDestroyed) return@launch
                corpo.removeViews(1, corpo.childCount - 1)
                val resumo = UI.painel(this@PerfilPublicoActivity, jogador.nome)
                val foto = ImageView(this@PerfilPublicoActivity).apply {
                    setImageResource(R.drawable.avatar_real_1)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundResource(R.drawable.bg_circle)
                    clipToOutline = true
                }
                resumo.addView(foto, LinearLayout.LayoutParams(dp(116), dp(116)).apply { gravity = Gravity.CENTER_HORIZONTAL })
                resumo.addView(UI.texto(this@PerfilPublicoActivity, "@${jogador.nickname}", 15f, true))
                resumo.addView(UI.texto(this@PerfilPublicoActivity,
                    jogador.bio.ifBlank { "Este jogador ainda não escreveu uma biografia." }))
                resumo.addView(UI.texto(this@PerfilPublicoActivity,
                    "🎮 Favorito: ${jogador.jogoFavorito.ifBlank { "Não informado" }}"))
                corpo.addView(resumo, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
                AvatarStorage.mostrar(this@PerfilPublicoActivity, foto, jogador.avatar)

                val jogos = UI.painel(this@PerfilPublicoActivity, "Biblioteca pública · ${jogador.jogosPublicos.size} jogos")
                if (jogador.jogosPublicos.isEmpty()) jogos.addView(UI.texto(this@PerfilPublicoActivity, "Nenhum jogo público por enquanto.", 14f, true))
                else jogador.jogosPublicos.take(60).forEach {
                    jogos.addView(UI.texto(this@PerfilPublicoActivity, "• $it", 14f))
                }
                corpo.addView(jogos, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
                val emComum = UI.painel(this@PerfilPublicoActivity, "Jogos em comum · ${comuns.size}")
                emComum.addView(UI.texto(this@PerfilPublicoActivity,
                    if (comuns.isEmpty()) "Vocês ainda não têm jogos em comum."
                    else comuns.joinToString(" · "), 14f, comuns.isEmpty()))
                corpo.addView(emComum, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
                if (amizade != null) corpo.addView(UI.botao(this@PerfilPublicoActivity, "Conversar") {
                    startActivity(Intent(this@PerfilPublicoActivity, ChatActivity::class.java)
                        .putExtra(ChatActivity.EXTRA_AMIZADE_ID, amizade.id)
                        .putExtra(ChatActivity.EXTRA_NOME, jogador.nome))
                }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16) })
            } catch (e: Exception) {
                Toast.makeText(this@PerfilPublicoActivity,
                    e.localizedMessage ?: "Não foi possível abrir o perfil.", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun dp(value: Int) = UI.dp(this, value)
    companion object { const val EXTRA_UID = "perfil_publico_uid" }
}
