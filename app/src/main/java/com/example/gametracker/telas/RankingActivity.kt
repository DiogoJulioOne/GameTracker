package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.R
import com.example.gametracker.dados.PontuacaoRanking
import com.example.gametracker.dados.RankingRepository
import com.example.gametracker.utils.DialogosGameTracker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class RankingActivity : AppCompatActivity() {
    private val repository = RankingRepository()
    private var pontuacao = 0
    private var ocupado = false
    private lateinit var atual: TextView
    private lateinit var lista: LinearLayout
    private lateinit var nome: EditText
    private lateinit var salvar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_ranking)
        atual = findViewById(R.id.textoPontuacaoAtual)
        lista = findViewById(R.id.listaRanking)
        nome = findViewById(R.id.editNomePontuacao)
        salvar = findViewById(R.id.buttonSalvarPontuacao)
        pontuacao = savedInstanceState?.getInt("pontuacao") ?: 0
        atualizarAtual()
        findViewById<android.view.View>(R.id.buttonVoltarRanking).setOnClickListener { finish() }
        findViewById<Button>(R.id.buttonMenosPontos).setOnClickListener {
            // Regra essencial da atividade: nunca permitir resultado negativo.
            if (pontuacao > 0) pontuacao--
            atualizarAtual()
        }
        findViewById<Button>(R.id.buttonMaisPontos).setOnClickListener {
            if (pontuacao < Int.MAX_VALUE) pontuacao++
            atualizarAtual()
        }
        salvar.setOnClickListener {
            val nomeInformado = nome.text.toString().trim()
            if (nomeInformado.isBlank()) {
                nome.error = "Informe o nome do jogador"
                return@setOnClickListener
            }
            executar("Pontuação salva!") { uid ->
                repository.salvar(uid, nomeInformado, pontuacao)
                nome.setText("")
                pontuacao = 0
                atualizarAtual()
            }
        }
        carregarRanking()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pontuacao", pontuacao)
        super.onSaveInstanceState(outState)
    }

    private fun atualizarAtual() { atual.text = pontuacao.toString() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun aviso(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun executar(sucesso: String, bloco: suspend (String) -> Unit) {
        if (ocupado) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            aviso("Sua sessão expirou. Entre novamente.")
            return
        }
        ocupado = true
        salvar.isEnabled = false
        lifecycleScope.launch {
            try {
                bloco(uid)
                aviso(sucesso)
                carregarRanking()
            } catch (e: Exception) {
                aviso("Falha no Firebase: ${e.localizedMessage ?: "verifique a conexão e as permissões"}")
            } finally {
                ocupado = false
                salvar.isEnabled = true
            }
        }
    }

    private fun carregarRanking() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val resultados = repository.listar(uid)
                lista.removeAllViews()
                if (resultados.isEmpty()) {
                    adicionarTexto(lista, "Nenhum resultado salvo ainda.", 15f, false)
                } else resultados.forEachIndexed { pos, item -> criarLinha(pos + 1, item) }
            } catch (e: Exception) {
                aviso("Não foi possível carregar o ranking: ${e.localizedMessage ?: "erro de conexão"}")
            }
        }
    }

    private fun adicionarTexto(pai: LinearLayout, valor: String, tamanho: Float, negrito: Boolean): TextView {
        val t = TextView(this).apply {
            text = valor
            textSize = tamanho
            setTextColor(getColor(if (negrito) R.color.texto else R.color.texto_secundario))
            if (negrito) setTypeface(null, android.graphics.Typeface.BOLD)
        }
        pai.addView(t)
        return t
    }

    private fun criarLinha(posicao: Int, item: PontuacaoRanking) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            setBackgroundResource(R.drawable.bg_card_jogo)
        }
        lista.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val medalha = TextView(this).apply {
            text = when (posicao) { 1 -> "♛"; 2 -> "♛"; 3 -> "♛"; else -> "#" }
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(getColor(when (posicao) {
                1 -> R.color.dourado_podio
                2 -> R.color.texto_secundario
                3 -> R.color.destaque
                else -> R.color.texto_secundario
            }))
        }
        card.addView(medalha, LinearLayout.LayoutParams(dp(28), dp(46)))
        val posicaoTexto = TextView(this).apply {
            text = "#$posicao"
            textSize = 13f
            setTextColor(getColor(R.color.texto_secundario))
        }
        card.addView(posicaoTexto, LinearLayout.LayoutParams(dp(31), -2))
        val nome = TextView(this).apply {
            text = item.nome
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(getColor(R.color.texto))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        card.addView(nome, LinearLayout.LayoutParams(0, -2, 1f))
        val pontos = TextView(this).apply {
            text = "${item.pontos} pts"
            textSize = 13f
            setTextColor(getColor(R.color.destaque))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        card.addView(pontos, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        fun acao(icone: Int, descricao: String, onClick: () -> Unit) {
            val b = android.widget.ImageView(this).apply {
                setImageResource(icone)
                setColorFilter(getColor(R.color.destaque))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setBackgroundResource(R.drawable.bg_ranking_acao)
                contentDescription = descricao
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
            card.addView(b, LinearLayout.LayoutParams(dp(38), dp(42)).apply { marginStart = dp(5) })
        }
        acao(R.drawable.ic_gt_edit, "Editar pontuação de ${item.nome}") { editar(item) }
        acao(R.drawable.ic_gt_delete, "Excluir pontuação de ${item.nome}") { confirmarExclusao(item) }
    }

    private fun editar(item: PontuacaoRanking) {
        val campoNome = EditText(this).apply {
            setText(item.nome); setSingleLine(true)
            setTextColor(getColor(R.color.texto)); hint = "Nome"
            setHintTextColor(getColor(R.color.texto_secundario))
        }
        val campoPontos = EditText(this).apply {
            setText(item.pontos.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(getColor(R.color.texto)); hint = "Pontos"
            setHintTextColor(getColor(R.color.texto_secundario))
        }
        DialogosGameTracker.exibir(this, "Corrigir pontuação", "Altera somente este resultado (ID: ${item.id}).",
            "Salvar", executar = {
                val novoNome = campoNome.text.toString().trim()
                val novosPontos = campoPontos.text.toString().toIntOrNull()
                if (novoNome.isBlank() || novosPontos == null || novosPontos < 0) {
                    aviso("Informe um nome e uma pontuação válida (zero ou maior).")
                } else executar("Resultado corrigido!") { uid ->
                    if (!repository.corrigir(uid, item.id, novoNome, novosPontos))
                        throw IllegalStateException("Registro inexistente; nenhuma alteração foi feita.")
                }
            }, construir = { corpo ->
                corpo.addView(campoNome)
                corpo.addView(campoPontos)
            })
    }

    private fun confirmarExclusao(item: PontuacaoRanking) {
        DialogosGameTracker.confirmar(this, "Excluir pontuação?",
            "Remover ${item.nome} (${item.pontos} pts)? Os demais resultados continuarão salvos.",
            "Excluir", sim = {
                executar("Pontuação excluída!") { uid ->
                    if (!repository.excluir(uid, item.id))
                        throw IllegalStateException("Registro inexistente; nada foi excluído.")
                }
            })
    }
}
