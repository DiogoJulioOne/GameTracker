package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.R
import com.example.gametracker.dados.JogoRepository
import com.example.gametracker.modelos.Jogo
import com.example.gametracker.utils.CapaJogo
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class DetalhesJogoActivity : AppCompatActivity() {
    private val jogos by lazy { JogoRepository(this) }
    private val idJogo by lazy { intent.getStringExtra(EXTRA_ID_JOGO) }
    private var usuarioId: String? = null
    private var jogoAtual: Jogo? = null
    private var salvandoProgresso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_jogo)
        findViewById<ImageButton>(R.id.buttonVoltarDetalhes).setOnClickListener { finish() }

        findViewById<Button>(R.id.botaoMenosProgresso).setOnClickListener { alterarProgresso(-10) }
        findViewById<Button>(R.id.botaoMaisProgresso).setOnClickListener { alterarProgresso(10) }

        findViewById<Button>(R.id.buttonEditarDetalhes).setOnClickListener {
            idJogo?.let { id ->
                startActivity(
                    Intent(this, AdicionarJogoActivity::class.java)
                        .putExtra(AdicionarJogoActivity.EXTRA_ID_JOGO, id)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (idJogo != null) {
            lifecycleScope.launch {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null) {
                    abrirLogin()
                    return@launch
                }
                usuarioId = uid
                try {
                    atualizarDetalhes(uid)
                } catch (_: Exception) {
                    android.widget.Toast.makeText(this@DetalhesJogoActivity, "Falha ao carregar jogo. Verifique sua conexão.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        } else {
            finish()
        }
    }

    private suspend fun atualizarDetalhes(donoId: String) {
        val jogo = idJogo?.let { id -> jogos.buscarPorId(id, donoId) }
        if (jogo == null) {
            finish()
            return
        }

        jogoAtual = jogo
        findViewById<TextView>(R.id.capaDetalheJogo).apply {
            text = "🎮\n${jogo.titulo}"
            CapaJogo.aplicar(this, jogo.titulo)
            contentDescription = "Capa ilustrativa de ${jogo.titulo}"
        }
        findViewById<TextView>(R.id.textoTituloDetalhe).text = jogo.titulo
        mostrarCampo(R.id.textoPlataformaDetalhe, jogo.plataforma)
        mostrarCampo(R.id.textoStatusDetalhe, jogo.status)
        mostrarCampo(R.id.textoDataInicioDetalhe, jogo.dataInicio)
        mostrarCampo(
            R.id.textoHorasJogadasDetalhe,
            jogo.horasJogadas?.let(::formatHoras)
        )
        mostrarCampo(
            R.id.textoObservacoesDetalhe,
            jogo.observacoes
        )

        val progresso = jogo.progresso?.coerceIn(0, 100)
        findViewById<TextView>(R.id.textoProgressoDetalhe).text =
            if (progresso == null) getString(R.string.progresso_nao_informado)
            else getString(R.string.progresso_percentual, progresso)
        findViewById<ProgressBar>(R.id.progressBarJogo).progress = progresso ?: 0
    }

    /** Edição rápida sem sair dos detalhes; respeita limites e evita duplo toque. */
    private fun alterarProgresso(delta: Int) {
        if (salvandoProgresso) return
        val uid = usuarioId ?: return
        val jogo = jogoAtual ?: return
        val novo = ((jogo.progresso ?: 0) + delta).coerceIn(0, 100)
        if (novo == jogo.progresso) return
        salvandoProgresso = true
        findViewById<Button>(R.id.botaoMenosProgresso).isEnabled = false
        findViewById<Button>(R.id.botaoMaisProgresso).isEnabled = false
        lifecycleScope.launch {
            try {
                idJogo?.let { jogos.atualizarProgresso(it, uid, novo) }
                atualizarDetalhes(uid)
            } catch (_: Exception) {
                android.widget.Toast.makeText(this@DetalhesJogoActivity,
                    "Não foi possível salvar o progresso.", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                salvandoProgresso = false
                findViewById<Button>(R.id.botaoMenosProgresso).isEnabled = true
                findViewById<Button>(R.id.botaoMaisProgresso).isEnabled = true
            }
        }
    }

    private fun mostrarCampo(id: Int, valor: String?) {
        findViewById<TextView>(id).text =
            valor?.takeIf { it.isNotBlank() } ?: getString(R.string.nao_informado)
    }

    private fun formatHoras(horas: Double): String {
        val valor = if (horas % 1.0 == 0.0) horas.toInt().toString() else horas.toString()
        return getString(R.string.horas_format, valor)
    }

    private fun abrirLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_ID_JOGO = "id_jogo"
    }
}
