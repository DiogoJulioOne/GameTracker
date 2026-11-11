package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.BottomNavigationHelper
import com.example.gametracker.R
import com.example.gametracker.dados.SocialRepository
import com.example.gametracker.utils.AvatarStorage
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.inputmethod.InputMethodManager

/** Lista de amigos com atualização em tempo real e pesquisa sem conversas duplicadas. */
class ChatsActivity : AppCompatActivity() {
    private val social = SocialRepository()
    private lateinit var lista: LinearLayout
    private lateinit var pesquisa: EditText
    private var escuta: ListenerRegistration? = null
    private val escutasMensagens = mutableMapOf<String, ListenerRegistration>()
    private val linhas = linkedMapOf<String, LinhaAmigo>()
    private var geracao = 0
    private var carregando = true
    private var erroAmizades = false

    private class LinhaAmigo(val id: String, val outroUid: String, val raiz: View) {
        val foto: ImageView = raiz.findViewById(R.id.avatarAmigoConversa)
        val titulo: TextView = raiz.findViewById(R.id.nomeAmigoConversa)
        val apelido: TextView = raiz.findViewById(R.id.apelidoAmigoConversa)
        val ultima: TextView = raiz.findViewById(R.id.ultimaMensagemAmigoConversa)
        val hora: TextView = raiz.findViewById(R.id.horarioUltimaConversa)
        val naoLida: TextView = raiz.findViewById(R.id.indicadorNaoLida)
        var nome: String = "Jogador"
        var nickname: String = ""
        var mensagem: SocialRepository.Mensagem? = null
        var ultimoVisto: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)
        BottomNavigationHelper.configurar(this, BottomNavigationHelper.BottomTab.CHAT)
        lista = findViewById(R.id.listaConversas)
        pesquisa = findViewById(R.id.pesquisaConversas)
        findViewById<android.widget.Button>(R.id.botaoAmigosConversas).setOnClickListener {
            startActivity(Intent(this, AmigosActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.botaoNotificacoesConversas).setOnClickListener {
            startActivity(Intent(this, NotificacoesActivity::class.java))
        }
        pesquisa.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { desenhar() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        desenhar()
    }

    override fun onStart() {
        super.onStart()
        val token = ++geracao
        carregando = true
        erroAmizades = false
        desenhar()
        escuta = social.acompanharAmizades { amizades, erro ->
            if (token != geracao) return@acompanharAmizades
            carregando = false
            erroAmizades = erro != null
            if (erro != null) {
                desenhar()
                return@acompanharAmizades
            }
            val ids = amizades.map { it.id }.toSet()
            (linhas.keys - ids).forEach { id ->
                escutasMensagens.remove(id)?.remove()
                linhas.remove(id)
            }
            amizades.forEach { amizade ->
                if (linhas.containsKey(amizade.id)) return@forEach
                val outro = amizade.participantes.firstOrNull { it != social.uid } ?: return@forEach
                val linha = criarLinha(amizade.id, outro)
                linhas[amizade.id] = linha

                // Perfil buscado uma única vez por amizade, não a cada letra digitada.
                lifecycleScope.launch {
                    try {
                        val perfil = social.perfilPublico(outro)
                        if (token != geracao || linhas[amizade.id] !== linha) return@launch
                        linha.nome = perfil?.nome?.ifBlank { "Jogador" } ?: "Jogador"
                        linha.nickname = perfil?.nickname.orEmpty()
                        linha.titulo.text = linha.nome
                        linha.apelido.text = if (linha.nickname.isBlank()) "Novo amigo" else "@${linha.nickname}"
                        desenhar()
                        if (perfil != null) AvatarStorage.mostrar(this@ChatsActivity, linha.foto, perfil.avatar)
                    } catch (_: Exception) {
                        // A falta do perfil público não deve esconder uma amizade válida.
                    }
                }
                escutasMensagens[amizade.id] = social.acompanharUltimaMensagem(amizade.id) { msg ->
                    if (token != geracao || linhas[amizade.id] !== linha) return@acompanharUltimaMensagem
                    linha.mensagem = msg
                    atualizarResumo(linha)
                    desenhar()
                    if (msg != null) lifecycleScope.launch {
                        val visto = try { social.mensagemVista(amizade.id) } catch (_: Exception) { null }
                        if (token != geracao || linhas[amizade.id] !== linha || linha.mensagem?.id != msg.id) return@launch
                        linha.ultimoVisto = visto
                        atualizarResumo(linha)
                    }
                }
            }
            desenhar()
        }
    }


    override fun onPause() {
        pesquisa.clearFocus()

        val raiz = findViewById<View>(android.R.id.content)
        raiz.isFocusableInTouchMode = true
        raiz.requestFocus()

        val teclado = getSystemService(INPUT_METHOD_SERVICE)
                as InputMethodManager

        teclado.hideSoftInputFromWindow(
            pesquisa.windowToken,
            0
        )

        super.onPause()
    }

    override fun onStop() {
        ++geracao
        escuta?.remove()
        escuta = null
        escutasMensagens.values.forEach { it.remove() }
        escutasMensagens.clear()
        linhas.clear()
        super.onStop()
    }

    private fun criarLinha(id: String, outro: String): LinhaAmigo {
        val view = LayoutInflater.from(this).inflate(R.layout.item_conversa_amigo, lista, false)
        val linha = LinhaAmigo(id, outro, view)
        val abrir = {
            startActivity(Intent(this, ChatActivity::class.java)
                .putExtra(ChatActivity.EXTRA_AMIZADE_ID, id)
                .putExtra(ChatActivity.EXTRA_NOME, linha.nome))
        }
        view.setOnClickListener { abrir() }
        linha.foto.setOnClickListener {
            startActivity(Intent(this, PerfilPublicoActivity::class.java)
                .putExtra(PerfilPublicoActivity.EXTRA_UID, outro))
        }
        view.findViewById<TextView>(R.id.abrirConversaAmigo).setOnClickListener { abrir() }
        return linha
    }

    private fun atualizarResumo(linha: LinhaAmigo) {
        val msg = linha.mensagem
        if (msg == null) {
            linha.ultima.text = "Vocês são amigos! Inicie uma conversa."
            linha.hora.text = ""
            linha.naoLida.visibility = View.GONE
            return
        }
        linha.ultima.text = if (msg.autorId == social.uid) "Você: ${msg.texto}" else msg.texto
        linha.hora.text = if (msg.timestamp > 0L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
        } else ""
        linha.naoLida.visibility = if (msg.autorId != social.uid && linha.ultimoVisto != msg.id)
            View.VISIBLE else View.GONE
    }

    private fun desenhar() {
        if (!::lista.isInitialized) return
        lista.removeAllViews()
        if (erroAmizades) { mostrarEstado("Não foi possível carregar seus amigos. Verifique a conexão."); return }
        if (carregando) { mostrarEstado("Buscando seus amigos..."); return }
        if (linhas.isEmpty()) {
            mostrarEstado("Seus novos amigos aparecerão aqui. Explore perfis em Descobrir!")
            return
        }
        val termo = pesquisa.text.toString().trim()
        val visiveis = linhas.values.filter {
            termo.isEmpty() || it.nome.contains(termo, true) || it.nickname.contains(termo, true)
        }.sortedWith(compareByDescending<LinhaAmigo> { it.mensagem?.timestamp ?: 0L }
            .thenBy { it.nome.lowercase(Locale.getDefault()) })
        if (visiveis.isEmpty()) { mostrarEstado("Nenhum amigo encontrado nesta pesquisa."); return }
        visiveis.forEach { linha ->
            (linha.raiz.parent as? android.view.ViewGroup)?.removeView(linha.raiz)
            lista.addView(linha.raiz)
        }
    }

    private fun mostrarEstado(mensagem: String) {
        lista.addView(TextView(this).apply {
            text = mensagem
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(getColor(R.color.texto_secundario))
            setPadding(dp(16), dp(36), dp(16), dp(36))
        })
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()
}
