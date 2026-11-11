package com.example.gametracker.telas

import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.BottomNavigationHelper
import com.example.gametracker.R
import com.example.gametracker.dados.SocialRepository
import com.example.gametracker.utils.AvatarStorage
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Cartas empilhadas: só a carta superior pode ser arrastada. */
class MatchActivity : AppCompatActivity() {
    private val social = SocialRepository()
    private lateinit var pilha: FrameLayout
    private lateinit var vazio: TextView
    private lateinit var pesquisa: EditText
    private val perfis = mutableListOf<SocialRepository.Jogador>()
    private var ocupada = false
    private var carregando = false
    private var recarregarAoVoltar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match)
        BottomNavigationHelper.configurar(this, BottomNavigationHelper.BottomTab.DESCOBRIR)
        pilha = findViewById(R.id.pilhaDescoberta)
        vazio = findViewById(R.id.textoVazioDescoberta)
        pesquisa = findViewById(R.id.editPesquisaDescoberta)
        vazio.setOnClickListener { if (!ocupada) recarregar() }
        findViewById<TextView>(R.id.buttonLupa).setOnClickListener {
            pesquisa.visibility = if (pesquisa.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (pesquisa.visibility == View.GONE) pesquisa.setText("")
        }
        pesquisa.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) { desenhar() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        recarregar()
    }

    override fun onResume() {
        super.onResume()
        if (recarregarAoVoltar) {
            recarregarAoVoltar = false
            recarregar()
        }
    }

    private fun recarregar() {
        if (carregando || ocupada) return
        carregando = true
        vazio.text = "Buscando jogadores..."
        vazio.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val atualizados = social.descobrir()
                perfis.clear()
                perfis.addAll(atualizados)
                desenhar()
            } catch (_: Exception) {
                pilha.removeAllViews()
                vazio.text = "Não foi possível carregar os jogadores. Toque aqui para tentar novamente."
                vazio.visibility = View.VISIBLE
            } finally {
                carregando = false
            }
        }
    }

    private fun desenhar() {
        if (!::pilha.isInitialized || ocupada) return
        pilha.removeAllViews()
        val termo = pesquisa.text.toString().trim()
        val visiveis = perfis.filter { termo.isBlank() ||
            it.nome.contains(termo, true) || it.nickname.contains(termo, true) ||
            it.jogoFavorito.contains(termo, true) ||
            it.jogosPublicos.any { nome -> nome.contains(termo, true) } }
        vazio.visibility = if (visiveis.isEmpty()) View.VISIBLE else View.GONE
        if (visiveis.isEmpty()) {
            vazio.text = if (termo.isNotBlank())
                "Nenhum jogador encontrado nesta pesquisa."
                else "Nenhum novo jogador no momento. Toque aqui para atualizar."
            return
        }
        // A carta seguinte fica literalmente debaixo da principal.
        if (visiveis.size > 1) {
            val fundo = criarCard(visiveis[1])
            fundo.scaleX = 0.96f
            fundo.scaleY = 0.96f
            fundo.alpha = 0.78f
            pilha.addView(fundo)
        }
        val topo = criarCard(visiveis[0])
        pilha.addView(topo)
        ativarDeslize(topo, visiveis[0])
    }

    private fun criarCard(jogador: SocialRepository.Jogador): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_descoberta, pilha, false)
        card.findViewById<TextView>(R.id.textoNomeDescoberta).text = jogador.nome
        card.findViewById<TextView>(R.id.textoNicknameDescoberta).text = "@${jogador.nickname}"
        card.findViewById<TextView>(R.id.textoBioDescoberta).text = jogador.bio.ifBlank { "Vamos jogar juntos?" }
        card.findViewById<TextView>(R.id.textoJogoFavoritoDescoberta).text =
            jogador.jogoFavorito.ifBlank { "Jogador" }
        card.findViewById<TextView>(R.id.textoJogosEmComumDescoberta).text =
            if (jogador.jogosPublicos.isEmpty()) "Ainda sem jogos em comum"
            else "🎮 ${jogador.jogosPublicos.size} em comum: " +
                jogador.jogosPublicos.take(2).joinToString(", ") +
                (if (jogador.jogosPublicos.size > 2) "…" else "")
        card.findViewById<TextView>(R.id.botaoVerPerfilDescoberta).setOnClickListener {
            startActivity(Intent(this, PerfilPublicoActivity::class.java)
                .putExtra(PerfilPublicoActivity.EXTRA_UID, jogador.id))
        }
        lifecycleScope.launch {
            AvatarStorage.mostrar(this@MatchActivity,
                card.findViewById<ImageView>(R.id.imagemAvatarDescoberta), jogador.avatar)
        }
        return card
    }


    private fun ativarDeslize(card: View, jogador: SocialRepository.Jogador) {
        var origem = 0f
        var inicioY = 0f
        var distancia = 0f
        card.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    origem = event.rawX; inicioY = event.rawY; distancia = 0f; true
                }
                MotionEvent.ACTION_MOVE -> {
                    distancia = event.rawX - origem
                    view.translationX = distancia
                    view.translationY = (event.rawY - inicioY) * 0.12f
                    view.rotation = (distancia / view.width.coerceAtLeast(1) * 10f).coerceIn(-9f,9f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_UP &&
                        abs(distancia) > view.width * 0.25f && !ocupada) {
                        val gostou = distancia > 0
                        ocupada = true
                        view.animate().translationX(if (gostou) view.width * 1.35f else -view.width * 1.35f)
                            .alpha(0f).setDuration(170).withEndAction {
                                lifecycleScope.launch {
                                    try {
                                        if (gostou) social.solicitarAmizade(jogador.id)
                                        else social.passarJogador(jogador.id)
                                        perfis.removeAll { it.id == jogador.id }
                                        ocupada = false
                                        desenhar()
                                        if (gostou) Toast.makeText(this@MatchActivity,
                                            "Pedido de amizade enviado para @${jogador.nickname}",
                                            Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        ocupada = false
                                        desenhar()
                                        Toast.makeText(this@MatchActivity,
                                            "Falha ao enviar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }.start()
                    } else {
                        view.animate().translationX(0f).translationY(0f).rotation(0f)
                            .setDuration(160).start()
                    }
                    true
                }
                else -> false
            }
        }
    }
}
