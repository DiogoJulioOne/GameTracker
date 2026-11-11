package com.example.gametracker.dados

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class PontuacaoRanking(val id: String, val nome: String, val pontos: Int)

/** Ranking isolado por conta: usuarios/{uid}/rankingPontuacoes/{id}. */
class RankingRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun colecao(uid: String) = db.collection("usuarios").document(uid)
        .collection("rankingPontuacoes")

    private fun conferir(uid: String) {
        require(uid.isNotBlank() && auth.currentUser?.uid == uid) { "Faça login novamente." }
    }

    suspend fun listar(uid: String): List<PontuacaoRanking> {
        conferir(uid)
        return colecao(uid).get().await().documents.mapNotNull { doc ->
            val nome = doc.getString("nome") ?: return@mapNotNull null
            val pontos = (doc.get("pontos") as? Number)?.toInt() ?: return@mapNotNull null
            PontuacaoRanking(doc.id, nome, pontos)
        }.sortedWith(compareByDescending<PontuacaoRanking> { it.pontos }.thenBy { it.nome.lowercase() })
    }

    suspend fun salvar(uid: String, nome: String, pontos: Int) {
        conferir(uid)
        require(nome.isNotBlank() && pontos >= 0)
        // add() cria um novo ID único, mesmo que um nome já tenha outra pontuação.
        colecao(uid).add(mapOf("nome" to nome.trim(), "pontos" to pontos,
            "criadoEm" to com.google.firebase.Timestamp.now())).await()
    }

    suspend fun corrigir(uid: String, id: String, nome: String, pontos: Int): Boolean {
        conferir(uid)
        require(id.isNotBlank() && nome.isNotBlank() && pontos >= 0)
        val ref = colecao(uid).document(id)
        // A transação impede criar um registro quando o ID não existe.
        return db.runTransaction { tx ->
            if (!tx.get(ref).exists()) false else {
                tx.update(ref, mapOf("nome" to nome.trim(), "pontos" to pontos))
                true
            }
        }.await()
    }

    suspend fun excluir(uid: String, id: String): Boolean {
        conferir(uid)
        require(id.isNotBlank())
        val ref = colecao(uid).document(id)
        return db.runTransaction { tx ->
            if (!tx.get(ref).exists()) false else {
                tx.delete(ref)
                true
            }
        }.await()
    }
}
