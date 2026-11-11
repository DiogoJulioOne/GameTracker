package com.example.gametracker.dados

import android.content.Context
import com.example.gametracker.modelos.Jogo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** Biblioteca em Firestore; importa dados locais uma vez, sem apagar a base antiga. */
class JogoRepository(context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val legados = JogosLegadosReader(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("gt_migracao_firestore", Context.MODE_PRIVATE)

    private fun colecao(uid: String) = firestore.collection("usuarios").document(uid).collection("jogos")

    private fun exigirUsuario(uid: String) {
        require(uid.isNotBlank() && auth.currentUser?.uid == uid) {
            "Sua sessão expirou. Entre novamente."
        }
    }

    suspend fun listar(usuarioId: String): List<Jogo> {
        exigirUsuario(usuarioId)
        migrarDoRoomSeNecessario(usuarioId)
        val lista = colecao(usuarioId).get().await().documents.mapNotNull { doc ->
            doc.data?.let { dados -> lerJogo(doc.id, usuarioId, dados) }
        }.sortedBy { it.titulo.lowercase() }
        try { sincronizarJogosPublicos(usuarioId, lista) } catch (_: Exception) { }
        return lista
    }

    suspend fun buscarPorId(id: String, usuarioId: String): Jogo? {
        exigirUsuario(usuarioId)
        migrarDoRoomSeNecessario(usuarioId)
        val doc = colecao(usuarioId).document(id).get().await()
        return doc.data?.let { lerJogo(doc.id, usuarioId, it) }
    }

    suspend fun salvar(jogo: Jogo, usuarioId: String) {
        exigirUsuario(usuarioId)
        require(jogo.id.isNotBlank() && jogo.titulo.isNotBlank())
        migrarDoRoomSeNecessario(usuarioId)
        colecao(usuarioId).document(jogo.id).set(paraFirestore(jogo)).await()
        try { sincronizarJogosPublicos(usuarioId,
            colecao(usuarioId).get().await().documents.mapNotNull { d -> d.getString("titulo") }
                .map { titulo -> Jogo(id = titulo, titulo = titulo, plataforma = "", status = "") })
        } catch (_: Exception) { }
    }

    /** Salva apenas o progresso: evita consultar toda a biblioteca e republicar os mesmos títulos. */
    suspend fun atualizarProgresso(id: String, usuarioId: String, percentual: Int) {
        exigirUsuario(usuarioId)
        require(id.isNotBlank() && percentual in 0..100)
        migrarDoRoomSeNecessario(usuarioId)
        colecao(usuarioId).document(id).update("progresso", percentual).await()
    }

    suspend fun excluir(id: String, usuarioId: String) {
        exigirUsuario(usuarioId)
        migrarDoRoomSeNecessario(usuarioId)
        colecao(usuarioId).document(id).delete().await()
        try { sincronizarJogosPublicos(usuarioId,
            colecao(usuarioId).get().await().documents.mapNotNull { d -> d.getString("titulo") }
                .map { titulo -> Jogo(id = titulo, titulo = titulo, plataforma = "", status = "") })
        } catch (_: Exception) { }
    }

    /** Dados sem dono Firebase (ID legado) NUNCA são atribuídos automaticamente. */
    suspend fun contarJogosSemConta(): Int = legados.listarSemConta().size

    suspend fun importarJogosSemContaComConfirmacao(usuarioId: String): Int {
        exigirUsuario(usuarioId)
        val jogos = legados.listarSemConta()
        var inseridos = 0
        for (jogo in jogos) {
            if (criarSeAusente(jogo, usuarioId)) inseridos++
        }
        // Registro só depois de todas as escritas confirmadas no Firestore.
        prefs.edit().putBoolean("legados_confirmados_$usuarioId", true).apply()
        return inseridos
    }

    fun legadosJaConfirmados(usuarioId: String): Boolean =
        prefs.getBoolean("legados_confirmados_$usuarioId", false)

    private suspend fun migrarDoRoomSeNecessario(uid: String) = mutex.withLock {
        if (prefs.getBoolean("room_concluido_$uid", false)) return@withLock
        val jogos = legados.listarDoUsuario(uid)
        // Se falhar conexão/autorização, não marca a migração como concluída.
        jogos.forEach { criarSeAusente(it, uid) }
        prefs.edit().putBoolean("room_concluido_$uid", true).apply()
    }

    private suspend fun criarSeAusente(jogo: Jogo, uid: String): Boolean {
        val doc = colecao(uid).document(jogo.id)
        return firestore.runTransaction { transacao ->
            if (transacao.get(doc).exists()) {
                false // nunca substitui edição que já está na nuvem
            } else {
                transacao.set(doc, paraFirestore(jogo))
                true
            }
        }.await()
    }

    private suspend fun sincronizarJogosPublicos(uid: String, jogos: List<Jogo>) {
        firestore.collection("perfisPublicos").document(uid).update("jogosPublicos",
            jogos.map { it.titulo.trim() }.filter { it.isNotBlank() }.distinct().take(300)).await()
    }

    private fun paraFirestore(jogo: Jogo): Map<String, Any?> = mapOf(
        "titulo" to jogo.titulo.trim(),
        "plataforma" to jogo.plataforma.trim(),
        "status" to jogo.status,
        "dataInicio" to jogo.dataInicio,
        "horasJogadas" to jogo.horasJogadas,
        "progresso" to jogo.progresso,
        "observacoes" to jogo.observacoes
    )

    private fun lerJogo(id: String, uid: String, dados: Map<String, Any?>): Jogo = Jogo(
        id = id,
        titulo = dados["titulo"] as? String ?: "Jogo sem título",
        plataforma = dados["plataforma"] as? String ?: "",
        status = dados["status"] as? String ?: "Quero jogar",
        dataInicio = dados["dataInicio"] as? String ?: "",
        horasJogadas = (dados["horasJogadas"] as? Number)?.toDouble(),
        progresso = (dados["progresso"] as? Number)?.toInt()?.coerceIn(0, 100),
        observacoes = dados["observacoes"] as? String ?: "",
        usuarioId = uid
    )

    companion object {
        // Um só bloqueio por processo impede duas importações simultâneas.
        private val mutex = Mutex()
    }
}
