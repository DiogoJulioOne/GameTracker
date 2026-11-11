package com.example.gametracker.dados

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** Authentication é a única fonte da sessão. Firestore armazena perfil e apelidos. */
class FirebaseUsuarioRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun cadastrarComEmailESenha(
        nome: String,
        email: String,
        senha: String,
        nicknameOpcional: String? = null
    ): String {
        val nomeLimpo = nome.trim()
        val emailLimpo = email.trim().lowercase(Locale.ROOT)
        val nickname = (nicknameOpcional ?: nomeLimpo).trim()
        val busca = normalizarNickname(nickname)
        validarNickname(busca)
        require(nomeLimpo.isNotBlank()) { "Informe seu nome." }

        val conta = auth.createUserWithEmailAndPassword(emailLimpo, senha).await()
        val usuario = conta.user ?: error("Não foi possível criar sua conta.")
        val uid = usuario.uid
        val perfil = firestore.collection("usuarios").document(uid)
        val apelido = firestore.collection("nicknames").document(busca)
        val publico = firestore.collection("perfisPublicos").document(uid)

        try {
            // Perfil e índice criados atomica e exclusivamente para este uid.
            firestore.runTransaction { tx ->
                if (tx.get(apelido).exists()) {
                    throw IllegalArgumentException("Este nickname já está sendo utilizado.")
                }
                tx.set(apelido, mapOf("uid" to uid)) // NUNCA expor email no índice
                tx.set(perfil, mapOf(
                    "nome" to nomeLimpo,
                    "nickname" to nickname,
                    "nicknameBusca" to busca,
                    "email" to emailLimpo,
                    "bio" to "",
                    "jogoFavorito" to "",
                    "avatar" to "avatar_1",
                    "criadoEm" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
                tx.set(publico, mapOf("nome" to nomeLimpo, "nickname" to nickname,
                    "bio" to "", "jogoFavorito" to "", "avatar" to "avatar_1"))
            }.await()
        } catch (erro: Exception) {
            // Limpa apenas a conta recém-criada por este fluxo.
            try { usuario.delete().await() } catch (_: Exception) { }
            throw erro
        }
        return uid
    }

    suspend fun fazerLogin(email: String, senha: String): String {
        val conta = auth.signInWithEmailAndPassword(email.trim(), senha).await()
        return conta.user?.uid ?: error("Não foi possível entrar.")
    }

    suspend fun carregarUsuarioAtual(uid: String): Map<String, Any?>? {
        require(auth.currentUser?.uid == uid) { "Sessão inválida." }
        val documento = firestore.collection("usuarios").document(uid).get().await()
        val dados = documento.data
        if (dados != null) {
            val publicoRef = firestore.collection("perfisPublicos").document(uid)
            try {
                publicoRef.set(mapOf(
                    "nome" to (dados["nome"] as? String ?: "Jogador"),
                    "nickname" to (dados["nickname"] as? String ?: ""),
                    "bio" to (dados["bio"] as? String ?: ""),
                    "jogoFavorito" to (dados["jogoFavorito"] as? String ?: ""),
                    "avatar" to (dados["avatar"] as? String ?: "avatar_1")
                ), com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (_: Exception) { /* login não depende do perfil público */ }
        }
        return dados
    }

    /** Mantém o email privado sincronizado após a confirmação no Firebase Auth. */
    suspend fun sincronizarEmailVerificado() {
        val user = auth.currentUser ?: return
        user.reload().await()
        val emailAuth = user.email ?: return
        val doc = firestore.collection("usuarios").document(user.uid)
        val existente = doc.get().await()
        if (existente.exists() && existente.getString("email") != emailAuth) {
            doc.update("email", emailAuth).await()
        }
    }

    /** Envia um link de confirmação, não troca o email até ele ser verificado. */
    suspend fun solicitarTrocaEmail(novoEmail: String) {
        val usuario = auth.currentUser ?: error("Faça login novamente.")
        require(novoEmail.isNotBlank())
        if (!novoEmail.equals(usuario.email, ignoreCase = true)) {
            usuario.verifyBeforeUpdateEmail(novoEmail.trim()).await()
        }
    }

    suspend fun salvarPerfil(
        uid: String,
        nome: String,
        nickname: String,
        bio: String,
        jogoFavorito: String,
        avatar: String
    ) {
        require(auth.currentUser?.uid == uid) { "Sessão inválida." }
        val nomeLimpo = nome.trim()
        val nicknameLimpo = nickname.trim()
        val nicknameBusca = normalizarNickname(nicknameLimpo)
        validarNickname(nicknameBusca)
        require(nomeLimpo.isNotBlank()) { "Informe seu nome." }

        val perfilRef = firestore.collection("usuarios").document(uid)
        val novoRef = firestore.collection("nicknames").document(nicknameBusca)
        val publicoRef = firestore.collection("perfisPublicos").document(uid)

        firestore.runTransaction { tx ->
            val antigo = tx.get(perfilRef)
            if (!antigo.exists()) error("Perfil não encontrado.")
            val buscaAntiga = antigo.getString("nicknameBusca")
                ?.takeIf { it.isNotBlank() }
                ?: normalizarNickname(antigo.getString("nickname").orEmpty())
            val velhoRef = buscaAntiga.takeIf { it.isNotBlank() }
                ?.let { firestore.collection("nicknames").document(it) }

            // Leituras vêm antes das escritas na transação Firestore.
            val novoDoc = tx.get(novoRef)
            val velhoDoc = if (velhoRef != null && velhoRef.path != novoRef.path) {
                tx.get(velhoRef)
            } else null

            if (novoDoc.exists() && novoDoc.getString("uid") != uid) {
                throw IllegalArgumentException("Este nickname já está sendo utilizado.")
            }
            tx.set(novoRef, mapOf("uid" to uid)) // remove email de índices antigos
            tx.update(perfilRef, mapOf(
                "nome" to nomeLimpo,
                "nickname" to nicknameLimpo,
                "nicknameBusca" to nicknameBusca,
                "bio" to bio.trim(),
                "jogoFavorito" to jogoFavorito.trim(),
                "avatar" to avatar.ifBlank { "avatar_1" }
            ))
            tx.set(publicoRef, mapOf("nome" to nomeLimpo,
                "nickname" to nicknameLimpo, "bio" to bio.trim().take(500),
                "jogoFavorito" to jogoFavorito.trim().take(100),
                "avatar" to avatar.ifBlank { "avatar_1" }),
                com.google.firebase.firestore.SetOptions.merge())
            // Nunca remove um apelido caso já pertença a outra conta.
            if (velhoRef != null && velhoDoc?.getString("uid") == uid) {
                tx.delete(velhoRef)
            }
        }.await()
    }

    suspend fun verificarNicknameEmUso(nickname: String, uidIgnorado: String? = null): Boolean {
        val busca = normalizarNickname(nickname)
        if (busca.isBlank()) return false
        val doc = firestore.collection("nicknames").document(busca).get().await()
        return doc.exists() && doc.getString("uid") != uidIgnorado
    }

    fun getCurrentUserUid(): String? = auth.currentUser?.uid
    fun signOut() = auth.signOut()

    fun normalizarNickname(nickname: String): String = nickname.trim().lowercase(Locale.ROOT)

    private fun validarNickname(nickname: String) {
        require(nickname.length in 3..30 && '/' !in nickname && nickname != "." && nickname != "..") {
            "Nickname deve ter de 3 a 30 caracteres e não pode conter '/'."
        }
    }
}
