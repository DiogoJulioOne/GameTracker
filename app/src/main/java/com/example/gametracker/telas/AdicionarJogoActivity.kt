package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.widget.AutoCompleteTextView
import com.example.gametracker.dados.BibliotecaSugestoes
import com.example.gametracker.dados.SugestoesJogosAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.app.DatePickerDialog
import android.text.TextWatcher
import android.text.Editable
import java.util.Calendar
import java.util.Locale
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.R
import com.example.gametracker.utils.DialogosGameTracker
import com.example.gametracker.dados.JogoRepository
import com.example.gametracker.modelos.Jogo
import kotlinx.coroutines.launch
import java.util.UUID
import com.google.firebase.auth.FirebaseAuth

class AdicionarJogoActivity : AppCompatActivity() {
    private val jogos by lazy { JogoRepository(this) }
    private var idJogo: String? = null
    private var usuarioId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adicionar_jogo)
        findViewById<ImageButton>(R.id.buttonVoltarJogo).setOnClickListener { finish() }

        val titulo = findViewById<AutoCompleteTextView>(R.id.editTituloJogo)
        titulo.threshold = 1
        titulo.setAdapter(SugestoesJogosAdapter(this))
        findViewById<TextView>(R.id.textoDicaCatalogo).text =
            "Busque por qualquer parte do nome • ${BibliotecaSugestoes.jogos.size} sugestões offline. Ou digite outro jogo."
        val plataforma = findViewById<EditText>(R.id.editPlataforma)
        val dataInicio = findViewById<EditText>(R.id.editDataInicio)
        val horasJogadas = findViewById<EditText>(R.id.editHorasJogadas)
        val progresso = findViewById<EditText>(R.id.editProgresso)
        val observacoes = findViewById<EditText>(R.id.editObservacoes)
        // Seleção de data sem precisar digitar formatos inválidos.
        dataInicio.isFocusable = false
        dataInicio.isClickable = true
        dataInicio.setOnClickListener {
            val hoje = Calendar.getInstance()
            DatePickerDialog(this, { _, ano, mes, dia ->
                dataInicio.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", dia, mes + 1, ano))
            }, hoje.get(Calendar.YEAR), hoje.get(Calendar.MONTH), hoje.get(Calendar.DAY_OF_MONTH)).show()
        }
        // O campo numérico e a barra permanecem sincronizados, inclusive durante edição.
        val barraProgresso = findViewById<SeekBar>(R.id.seekProgressoJogo)
        barraProgresso.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, valor: Int, doUsuario: Boolean) {
                if (doUsuario) progresso.setText(valor.toString())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        progresso.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) {
                s?.toString()?.toIntOrNull()?.takeIf { it in 0..100 }?.let {
                    if (barraProgresso.progress != it) barraProgresso.progress = it
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val status = findViewById<TextView>(R.id.spinnerStatus)
        val statusOptions = resources.getStringArray(R.array.status_jogo)
        var statusSelecionado = statusOptions.first()
        status.text = statusSelecionado
        status.setOnClickListener {
            DialogosGameTracker.menu(this, "Selecionar status", statusOptions.toList()) { indice ->
                statusSelecionado = statusOptions[indice]
                status.text = statusSelecionado
            }
        }

        idJogo = intent.getStringExtra(EXTRA_ID_JOGO)
        val botaoSalvar = findViewById<Button>(R.id.buttonSalvarJogo)
        botaoSalvar.isEnabled = false
        if (idJogo != null) {
            title = getString(R.string.editar_jogo)
            botaoSalvar.setText(R.string.salvar)
        }

        lifecycleScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid

            if (uid == null) {
                abrirLogin()
                return@launch
            }

            usuarioId = uid
            if (idJogo != null) {
                val jogo = try { jogos.buscarPorId(idJogo!!, uid) } catch (_: Exception) {
                    Toast.makeText(this@AdicionarJogoActivity, "Falha ao carregar jogo.", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }
                if (jogo == null) {
                    // Mantém a tela aberta em vez de fechar automaticamente e voltar para a MainActivity
                    return@launch
                }
                titulo.setText(jogo.titulo)
                plataforma.setText(jogo.plataforma)
                dataInicio.setText(jogo.dataInicio)
                jogo.horasJogadas?.let { horasJogadas.setText(formatHoras(it)) }
                jogo.progresso?.let { progresso.setText(it.toString()) }
                observacoes.setText(jogo.observacoes)
                val statusSalvo = if (jogo.status.equals("Concluído", ignoreCase = true)) {
                    getString(R.string.status_zerado)
                } else {
                    jogo.status
                }
                val indiceStatus = statusOptions.indexOf(statusSalvo)
                if (indiceStatus >= 0) {
                    statusSelecionado = statusOptions[indiceStatus]
                    status.text = statusSelecionado
                }
            }
            botaoSalvar.isEnabled = true
        }

        botaoSalvar.setOnClickListener { botao ->
            val nome = titulo.text.toString().trim()
            val plataformaDigitada = plataforma.text.toString().trim()
            if (nome.isBlank() || plataformaDigitada.isBlank()) {
                Toast.makeText(this, R.string.erro_campos_jogo, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val progressoDigitado = progresso.text.toString().trim()
            val progressoValor = if (progressoDigitado.isBlank()) {
                null
            } else {
                progressoDigitado.toIntOrNull()?.takeIf { it in 0..100 }
            }
            if (progressoDigitado.isNotBlank() && progressoValor == null) {
                progresso.error = getString(R.string.erro_progresso)
                return@setOnClickListener
            }
            val horasDigitadas = horasJogadas.text.toString().trim().replace(',', '.')
            val horasValor = if (horasDigitadas.isBlank()) {
                null
            } else {
                horasDigitadas.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
            }
            if (horasDigitadas.isNotBlank() && horasValor == null) {
                horasJogadas.error = getString(R.string.erro_horas)
                return@setOnClickListener
            }
            val donoId = usuarioId ?: return@setOnClickListener
            botao.isEnabled = false
            lifecycleScope.launch {
                try {
                    jogos.salvar(
                        Jogo(
                        id = idJogo ?: UUID.randomUUID().toString(),
                        titulo = nome,
                        plataforma = plataformaDigitada,
                        status = statusSelecionado,
                        dataInicio = dataInicio.text.toString().trim(),
                        horasJogadas = horasValor,
                        progresso = progressoValor?.coerceIn(0, 100),
                        observacoes = observacoes.text.toString().trim(),
                        usuarioId = donoId
                        ),
                        donoId
                    )
                    setResult(RESULT_OK)
                    finish()
                } catch (_: Exception) {
                    Toast.makeText(this@AdicionarJogoActivity,
                        "Falha ao salvar. Verifique a conexão e tente novamente.",
                        Toast.LENGTH_LONG).show()
                    botao.isEnabled = true
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID_JOGO = "id_jogo"
    }

    private fun formatHoras(horas: Double): String =
        if (horas % 1.0 == 0.0) horas.toInt().toString() else horas.toString()

    private fun abrirLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
