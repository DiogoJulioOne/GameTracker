package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.R
import com.example.gametracker.utils.VisualGameTracker
import com.example.gametracker.dados.SocialRepository
import com.example.gametracker.utils.SocialInterface as UI
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/** Central de atividades enquanto o aplicativo está aberto; não envia push com o app fechado. */
class NotificacoesActivity : AppCompatActivity() {
    private val social = SocialRepository()
    private lateinit var lista: LinearLayout
    private var ouvinteAmigos: ListenerRegistration? = null
    private var ouvinteConvites: ListenerRegistration? = null
    private val ouvintesMensagens = mutableMapOf<String, ListenerRegistration>()
    private val atividades = linkedMapOf<String, Item>()
    private var versao = 0

    private val db = FirebaseFirestore.getInstance()

    private var ouvintePedidos: ListenerRegistration? = null

    private data class PedidoAmizade(
        val id: String,
        val remetenteId: String,
        val destinatarioId: String
    )

    private var pedidosPendentes = emptyList<PedidoAmizade>()

    private data class Item(val titulo: String, val descricao: String, val instante: Long, val abrir: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.fundo)) }
        lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(28))
        }
        scroll.addView(lista)
        VisualGameTracker.envolver(this, scroll)
        mostrar()
    }

    override fun onStart() {
        super.onStart()
        val token = ++versao
        acompanharPedidos(token)
        ouvinteConvites = social.acompanharConvites { convites, erro ->
            if (token != versao) return@acompanharConvites
            atividades.keys.filter { it.startsWith("convite:") }.toList().forEach(atividades::remove)
            if (erro == null) convites.forEach { convite ->
                atividades["convite:${convite.id}"] = Item("✉ Convite para grupo",
                    "Você foi convidado para ${convite.nomeGrupo}", System.currentTimeMillis()) {
                    startActivity(Intent(this, GruposActivity::class.java))
                }
            }
            mostrar()
        }
        ouvinteAmigos = social.acompanharAmizades { amigos, erro ->
            if (token != versao) return@acompanharAmizades
            atividades.keys.filter { it.startsWith("amigo:") }.toList().forEach(atividades::remove)
            val ids = amigos.map { it.id }.toSet()
            (ouvintesMensagens.keys - ids).forEach { id -> ouvintesMensagens.remove(id)?.remove() }
            if (erro == null) amigos.forEach { amizade ->
                val outro = amizade.participantes.firstOrNull { it != social.uid } ?: return@forEach
                atividades["amigo:${amizade.id}"] = Item("🎮 Você tem um amigo!",
                    "Uma nova amizade está disponível. Toque para ver o perfil.", amizade.criadoEm) {
                    startActivity(Intent(this, PerfilPublicoActivity::class.java)
                        .putExtra(PerfilPublicoActivity.EXTRA_UID, outro))
                }
                if (amizade.id !in ouvintesMensagens && ouvintesMensagens.size < 30) {
                    ouvintesMensagens[amizade.id] = social.acompanharUltimaMensagem(amizade.id) { msg ->
                        if (token != versao) return@acompanharUltimaMensagem
                        atividades.remove("mensagem:${amizade.id}")
                        if (msg != null && msg.autorId != social.uid) lifecycleScope.launch {
                            val visto = try { social.mensagemVista(amizade.id) } catch (_: Exception) { null }
                            if (token != versao) return@launch
                            if (msg.id != visto) atividades["mensagem:${amizade.id}"] = Item(
                                "💬 Mensagem de amigo", msg.texto, msg.timestamp) {
                                startActivity(Intent(this@NotificacoesActivity, ChatActivity::class.java)
                                    .putExtra(ChatActivity.EXTRA_AMIZADE_ID, amizade.id)
                                    .putExtra(ChatActivity.EXTRA_NOME, "Amigo"))
                            }
                            mostrar()
                        }
                        mostrar()
                    }
                }
            }
            mostrar()
        }
    }

    override fun onStop() {
        ++versao
        ouvinteAmigos?.remove(); ouvinteAmigos = null
        ouvinteConvites?.remove(); ouvinteConvites = null
        ouvintePedidos?.remove()
        ouvintePedidos = null
        ouvintesMensagens.values.forEach { it.remove() }; ouvintesMensagens.clear()
        super.onStop()
    }


    private fun acompanharPedidos(token: Int) {
        ouvintePedidos?.remove()

        ouvintePedidos = db.collection("pedidosAmizade")
            .whereEqualTo("destinatarioId", social.uid)
            .addSnapshotListener { snapshot, erro ->

                if (token != versao) return@addSnapshotListener

                if (erro != null) {
                    Toast.makeText(
                        this,
                        "Erro ao receber pedidos: ${erro.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addSnapshotListener
                }

                pedidosPendentes = snapshot?.documents.orEmpty()
                    .filter { it.getString("status") == "pendente" }
                    .mapNotNull { doc ->
                        val remetente = doc.getString("remetenteId")
                            ?: return@mapNotNull null

                        PedidoAmizade(
                            id = doc.id,
                            remetenteId = remetente,
                            destinatarioId = social.uid
                        )
                    }

                mostrar()
            }
    }

    private fun responderPedido(
        pedido: PedidoAmizade,
        aceitar: Boolean
    ) {
        lifecycleScope.launch {
            try {
                val refPedido = db.collection("pedidosAmizade")
                    .document(pedido.id)

                if (aceitar) {
                    val participantes = listOf(
                        pedido.remetenteId,
                        pedido.destinatarioId
                    ).sorted()

                    val amizadeId = participantes.joinToString("_")

                    val refAmizade = db.collection("amizades")
                        .document(amizadeId)

                    // Atualiza o pedido e cria a amizade juntos.
                    val batch = db.batch()

                    batch.update(refPedido, "status", "aceito")

                    batch.set(
                        refAmizade,
                        mapOf(
                            "participantes" to participantes,
                            "criadoEm" to FieldValue.serverTimestamp()
                        )
                    )

                    batch.commit().await()

                    Toast.makeText(
                        this@NotificacoesActivity,
                        "Amizade aceita!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    refPedido.update("status", "recusado").await()

                    Toast.makeText(
                        this@NotificacoesActivity,
                        "Pedido recusado.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@NotificacoesActivity,
                    "Erro: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private fun mostrar() {
        if (!::lista.isInitialized) return

        lista.removeAllViews()

        lista.addView(UI.botao(this, "← Voltar") {
            finish()
        })

        lista.addView(UI.texto(this, "Atividades", 26f))

        lista.addView(
            UI.texto(
                this,
                "Pedidos de amizade, grupos e mensagens recentes.",
                13f,
                true
            )
        )

        // Pedidos de amizade recebidos
        if (pedidosPendentes.isNotEmpty()) {
            lista.addView(
                UI.texto(this, "Pedidos de amizade", 20f)
            )

            pedidosPendentes.forEach { pedido ->
                val card = UI.painel(
                    this,
                    "Nova solicitação de amizade"
                )

                card.addView(
                    UI.texto(
                        this,
                        "Um jogador quer adicionar você como amigo.",
                        14f,
                        true
                    )
                )

                card.addView(
                    UI.botao(this, "Ver perfil") {
                        startActivity(
                            Intent(
                                this,
                                PerfilPublicoActivity::class.java
                            ).putExtra(
                                PerfilPublicoActivity.EXTRA_UID,
                                pedido.remetenteId
                            )
                        )
                    }
                )

                card.addView(
                    UI.botao(this, "Aceitar amizade") {
                        responderPedido(pedido, true)
                    }
                )

                card.addView(
                    UI.botao(this, "Recusar pedido") {
                        responderPedido(pedido, false)
                    }
                )

                lista.addView(
                    card,
                    LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = dp(12)
                    }
                )
            }
        }

        // Outras atividades
        if (atividades.isEmpty() && pedidosPendentes.isEmpty()) {
            lista.addView(
                UI.texto(
                    this,
                    "Ainda não há atividades para mostrar.",
                    15f,
                    true
                )
            )
            return
        }

        atividades.values
            .sortedByDescending { it.instante }
            .take(60)
            .forEach { item ->

                val card = UI.painel(this, item.titulo)

                card.addView(
                    UI.texto(this, item.descricao, 14f, true)
                )

                if (item.instante > 0) {
                    card.addView(
                        UI.texto(
                            this,
                            SimpleDateFormat(
                                "dd/MM HH:mm",
                                Locale.getDefault()
                            ).format(Date(item.instante)),
                            12f,
                            true
                        )
                    )
                }

                card.addView(
                    UI.botao(this, "Abrir") {
                        item.abrir()
                    }
                )

                lista.addView(
                    card,
                    LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = dp(12)
                    }
                )
            }
    }

    private fun dp(n: Int) = UI.dp(this, n)
}