package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import com.example.gametracker.utils.AvatarStorage
import android.widget.TextView
import android.widget.Toast
import com.example.gametracker.utils.DialogosGameTracker
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.BottomNavigationHelper
import com.example.gametracker.R
import com.example.gametracker.dados.SocialRepository
import kotlinx.coroutines.launch
import android.view.View
import android.view.inputmethod.InputMethodManager

class GruposActivity : AppCompatActivity() {
    private val social = SocialRepository()
    private lateinit var lista: LinearLayout
    private lateinit var busca: EditText
    private var somenteMeus = true
    private var grupos = emptyList<SocialRepository.Grupo>()
    private var meus = emptySet<String>()
    private var convites = emptyList<SocialRepository.Convite>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grupos)
        BottomNavigationHelper.configurar(this, BottomNavigationHelper.BottomTab.GRUPOS)
        lista = findViewById(R.id.listaGrupos)
        busca = findViewById(R.id.pesquisaGrupos)
        findViewById<Button>(R.id.botaoCriarGrupo).setOnClickListener { criarGrupo() }
        findViewById<Button>(R.id.botaoMeusGrupos).setOnClickListener { selecionarAba(true) }
        findViewById<Button>(R.id.botaoExplorarGrupos).setOnClickListener { selecionarAba(false) }
        atualizarAbas()
        busca.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { desenhar() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
    private fun selecionarAba(meusGrupos: Boolean) {
        somenteMeus = meusGrupos
        atualizarAbas()
        desenhar()
    }
    private fun atualizarAbas() {
        val botoes = listOf(findViewById<Button>(R.id.botaoMeusGrupos),
            findViewById<Button>(R.id.botaoExplorarGrupos))
        botoes.forEachIndexed { indice, botao ->
            botao.setTextColor(getColor(R.color.black))
            botao.setBackgroundResource(if ((indice == 0) == somenteMeus)
                R.drawable.bg_botao_principal else R.drawable.bg_botao_secundario)
            botao.animate().alpha(if ((indice == 0) == somenteMeus) 1f else .82f)
                .scaleX(1f).scaleY(1f).setDuration(170).start()
        }
    }
    override fun onResume() { super.onResume(); carregar() }
    private fun carregar() {
        lista.removeAllViews()
        aviso("Carregando comunidades...")
        lifecycleScope.launch {
            try {
                grupos = social.listarGrupos()
                meus = social.meusGrupos().map { it.id }.toSet()
                desenhar()
                mostrarConvites()
            } catch (_: Exception) { lista.removeAllViews(); aviso("Não foi possível carregar os grupos.") }
        }
    }


    override fun onPause() {
        busca.clearFocus()

        val raiz = findViewById<View>(android.R.id.content)
        raiz.isFocusableInTouchMode = true
        raiz.requestFocus()

        val teclado = getSystemService(INPUT_METHOD_SERVICE)
                as InputMethodManager

        teclado.hideSoftInputFromWindow(
            busca.windowToken,
            0
        )

        super.onPause()
    }

    private fun mostrarConvites() {
        lifecycleScope.launch {
            try {
                convites = social.listarConvites()
                desenhar()
            } catch (_: Exception) { }
        }
    }
    private fun desenharConvites() {
        convites.forEach { convite ->
            val conviteCard = TextView(this).apply {
                text = "✉ Convite para ${convite.nomeGrupo}\nToque para aceitar ou recusar"
                textSize = 16f
                setTextColor(getColor(R.color.texto))
                setBackgroundResource(R.drawable.bg_card_jogo)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setOnClickListener {
                    DialogosGameTracker.confirmar(this@GruposActivity, "Convite: ${convite.nomeGrupo}",
                        "Deseja aceitar o convite para participar do grupo?", "Aceitar", "Recusar",
                        sim = { responder(convite, true) }, nao = { responder(convite, false) })
                }
            }
            lista.addView(conviteCard, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }
    }
    private fun responder(convite: SocialRepository.Convite, aceitar: Boolean) {
        lifecycleScope.launch {
            try { social.responderConvite(convite, aceitar); carregar() }
            catch (_: Exception) { avisoToast("Não foi possível responder ao convite.") }
        }
    }

    private fun desenhar() {
        if (!::lista.isInitialized) return
        lista.removeAllViews()
        desenharConvites()
        val termo = busca.text.toString().trim()
        val mostrados = grupos.filter { (!somenteMeus || it.id in meus) &&
            (termo.isBlank() || it.nome.contains(termo, true) || it.jogo.contains(termo, true)) }
        if (mostrados.isEmpty()) { aviso(if (somenteMeus) "Você ainda não entrou em nenhum grupo." else "Nenhum grupo encontrado."); return }
        mostrados.forEach { g ->
            val participando = g.id in meus
            val cartao = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card_jogo)
                setPadding(dp(12), dp(13), dp(12), dp(13))
            }
            val capa = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_gallery)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cartao.addView(capa, LinearLayout.LayoutParams(dp(64), dp(64)).apply { rightMargin = dp(12) })
            if (g.imagem.startsWith("storage:") || g.imagem.startsWith("inline:")) lifecycleScope.launch {
                AvatarStorage.mostrar(this@GruposActivity, capa, g.imagem)
            }
            val descricao = TextView(this).apply {
                text = "${g.nome}\n${g.descricao}\n🎮 ${g.jogo.ifBlank { "Diversos jogos" }}  •  ${if (g.publico) "Público" else "Privado"}" +
                    if (participando) "\nToque para abrir" else "\nToque para participar"
                textSize = 14f
                setTextColor(getColor(R.color.texto))
            }
            cartao.addView(descricao, LinearLayout.LayoutParams(0, -2, 1f))
            cartao.setOnClickListener {
                if (participando) abrir(g) else DialogosGameTracker.confirmar(
                    this@GruposActivity, "Participar de ${g.nome}?", "Entrar nesta comunidade?", "Entrar",
                    sim = {
                        lifecycleScope.launch {
                            try { social.participar(g.id); carregar() }
                            catch (_: Exception) { avisoToast("Não foi possível participar.") }
                        }
                    })
            }
            lista.addView(cartao, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }
    private fun abrir(g: SocialRepository.Grupo) {
        startActivity(Intent(this, GrupoDetalhesActivity::class.java)
            .putExtra(GrupoDetalhesActivity.EXTRA_ID, g.id)
            .putExtra(GrupoDetalhesActivity.EXTRA_NOME, g.nome)
            .putExtra(GrupoDetalhesActivity.EXTRA_CRIADOR, g.criadorId))
    }
    private fun criarGrupo() {
        DialogosGameTracker.formulario(this, "Criar comunidade", listOf(
            DialogosGameTracker.Campo("Nome do grupo"),
            DialogosGameTracker.Campo("Descrição"),
            DialogosGameTracker.Campo("Jogo principal")
        ), acao = "Avançar", subtitulo = "Crie sua comunidade gamer.") { valores ->
            DialogosGameTracker.unica(this, "Privacidade do grupo", listOf(
                "Público — aparece nas pesquisas", "Privado — apenas convidados"
            ), 0) { tipo ->
                lifecycleScope.launch {
                    try {
                        social.criarGrupo(valores[0], valores[1], valores[2], tipo == 0)
                        carregar()
                    } catch (e: Exception) { avisoToast(e.localizedMessage ?: "Erro ao criar grupo.") }
                }
            }
        }
    }
    private fun aviso(msg: String) { lista.addView(TextView(this).apply {
        text = msg; textSize = 15f; setTextColor(getColor(R.color.texto_secundario))
        setPadding(dp(12), dp(22), dp(12), dp(22))
    }) }
    private fun avisoToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
