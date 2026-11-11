package com.example.gametracker.telas

import android.os.Bundle
import android.view.Gravity
import com.example.gametracker.utils.DialogosGameTracker
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.R
import com.example.gametracker.dados.SocialRepository
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Chat de amigos ou de grupo, sincronizado em tempo real pelo Firestore. */
class ChatActivity : AppCompatActivity() {
    private val social = SocialRepository()
    private var amizadeId: String? = null
    private var grupoId: String? = null
    private var canalId: String? = null
    private var escuta: ListenerRegistration? = null
    private lateinit var lista: LinearLayout
    private lateinit var rolagem: ScrollView
    private lateinit var campo: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        amizadeId = intent.getStringExtra(EXTRA_AMIZADE_ID)
        grupoId = intent.getStringExtra(EXTRA_GRUPO_ID)
        canalId = intent.getStringExtra(EXTRA_CANAL_ID)
        if (amizadeId.isNullOrBlank() && grupoId.isNullOrBlank()) { finish(); return }
        findViewById<Button>(R.id.buttonVoltarChat).setOnClickListener { finish() }
        findViewById<TextView>(R.id.textoNomeJogadorChat).text = intent.getStringExtra(EXTRA_NOME) ?: "Conversa"
        lista = findViewById(R.id.listaMensagens)
        rolagem = findViewById(R.id.scrollMensagens)
        campo = findViewById(R.id.editMensagem)
        findViewById<Button>(R.id.buttonEnviarMensagem).setOnClickListener {
            val texto = campo.text.toString().trim()
            if (texto.isBlank()) return@setOnClickListener
            campo.text.clear()
            lifecycleScope.launch {
                try {
                    if (grupoId != null && canalId != null) social.enviarCanal(grupoId!!, canalId!!, texto)
                    else if (grupoId != null) social.enviarGrupo(grupoId!!, texto)
                    else social.enviar(amizadeId!!, texto)
                } catch (_: Exception) {
                    campo.setText(texto)
                    Toast.makeText(this@ChatActivity, "Falha ao enviar mensagem.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val cb: (List<SocialRepository.Mensagem>, Exception?) -> Unit = { mensagens, erro ->
            if (erro != null) Toast.makeText(this, "Não foi possível receber mensagens.", Toast.LENGTH_SHORT).show()
            else {
                atualizarMensagens(mensagens)
                if (grupoId == null && mensagens.isNotEmpty()) lifecycleScope.launch {
                    try { social.marcarVista(amizadeId!!, mensagens.last().id) }
                    catch (_: Exception) { }
                }
            }
        }
        escuta = if (grupoId != null && canalId != null) social.acompanharMensagensCanal(grupoId!!, canalId!!, cb)
        else if (grupoId != null) social.acompanharMensagensGrupo(grupoId!!, cb)
        else social.acompanharMensagens(amizadeId!!, cb)
    }
    override fun onStop() { escuta?.remove(); escuta = null; super.onStop() }

    private fun atualizarMensagens(mensagens: List<SocialRepository.Mensagem>) {
        lista.removeAllViews()
        mensagens.forEach { mensagem ->
            val enviada = mensagem.autorId == social.uid
            val linha = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = if (enviada) Gravity.END else Gravity.START
                setPadding(dp(4), dp(3), dp(4), dp(3))
            }
            val balao = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(9), dp(13), dp(7))
                setBackgroundResource(if (enviada) R.drawable.bg_bolha_enviada else R.drawable.bg_bolha_recebida)
            }
            balao.addView(TextView(this).apply {
                text = mensagem.texto; textSize = 15f
                setTextColor(getColor(R.color.texto))
                maxWidth = (resources.displayMetrics.widthPixels * .78f).toInt()
            })
            balao.addView(TextView(this).apply {
                text = if (mensagem.timestamp == 0L) "Enviando..." else
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(mensagem.timestamp))
                textSize = 11f; gravity = Gravity.END
                setTextColor(getColor(R.color.texto_secundario))
            })
            if (grupoId != null) {
                balao.setOnLongClickListener {
                    DialogosGameTracker.confirmar(this, "Excluir mensagem?",
                        "Deseja apagar esta mensagem do canal?", "Excluir", sim = {
                            lifecycleScope.launch {
                                try { social.apagarMensagemGrupo(grupoId!!, canalId, mensagem.id) }
                                catch (_: Exception) {
                                    Toast.makeText(this@ChatActivity,
                                        "Você não tem permissão para excluir essa mensagem.",
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                    true
                }
            }
            linha.addView(balao, LinearLayout.LayoutParams(-2, -2))
            lista.addView(linha)
        }
        rolagem.post { rolagem.fullScroll(ScrollView.FOCUS_DOWN) }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    companion object {
        const val EXTRA_AMIZADE_ID = "amizade_id"
        const val EXTRA_GRUPO_ID = "grupo_id"
        const val EXTRA_CANAL_ID = "canal_id"
        const val EXTRA_NOME = "nome_conversa"
    }
}
