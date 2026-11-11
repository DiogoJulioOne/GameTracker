package com.example.gametracker.dados

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.util.Locale

/** Dados sociais persistidos no Firestore. Não publica e-mail nem dados privados do usuário. */
class SocialRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    val uid: String get() = auth.currentUser?.uid ?: error("Faça login novamente.")

    data class Jogador(val id: String, val nome: String, val nickname: String,
        val bio: String, val jogoFavorito: String, val avatar: String,
        val jogosPublicos: List<String> = emptyList())
    data class Amizade(val id: String, val participantes: List<String>, val criadoEm: Long = 0L)
    data class Mensagem(val id: String, val autorId: String, val texto: String, val timestamp: Long)
    data class Grupo(val id: String, val nome: String, val descricao: String,
        val jogo: String, val publico: Boolean, val criadorId: String,
        val imagem: String = "")

    private fun publicos() = db.collection("perfisPublicos")
    private fun amizades() = db.collection("amizades")
    private fun curtida(de: String, para: String) = db.collection("usuarios").document(de)
        .collection("curtidas").document(para)

    /**
     * Busca candidatos em páginas, sem depender somente dos 60 primeiros perfis.
     * O limite de páginas protege a franquia de leituras do Firestore.
     */
    suspend fun descobrir(): List<Jogador> {
        val eu = uid
        val meusJogos = db.collection("usuarios").document(eu).collection("jogos")
            .get().await().documents.mapNotNull { it.getString("titulo") }
            .map(::nomeComparavel).filter { it.isNotBlank() }.toSet()
        val avaliados = db.collection("usuarios").document(eu).collection("curtidas")
            .get().await().documents.map { it.id }.toSet()
        val encontrados = mutableListOf<Jogador>()
        var ultimo: com.google.firebase.firestore.DocumentSnapshot? = null
        // No máximo 120 perfis por atualização; traz até 30 candidatos não avaliados.
        repeat(3) {
            var consulta = publicos().orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(40)
            if (ultimo != null) consulta = consulta.startAfter(ultimo!!)
            val documentos = consulta.get().await().documents
            if (documentos.isEmpty()) return encontrados.sortedByDescending { it.jogosPublicos.size }
            ultimo = documentos.last()
            documentos.forEach { d ->
                if (d.id == eu || d.id in avaliados) return@forEach
                val m = d.data.orEmpty()
                encontrados.add(Jogador(
                    d.id,
                    m["nome"] as? String ?: "Jogador",
                    m["nickname"] as? String ?: "",
                    m["bio"] as? String ?: "",
                    m["jogoFavorito"] as? String ?: "",
                    m["avatar"] as? String ?: "avatar_1",
                    (m["jogosPublicos"] as? List<*>)?.filterIsInstance<String>()
                        ?.distinctBy(::nomeComparavel)
                        ?.filter { nomeComparavel(it) in meusJogos }.orEmpty()
                ))
                if (encontrados.size >= 30) return encontrados.sortedByDescending { it.jogosPublicos.size }
            }
            if (documentos.size < 40) return encontrados.sortedByDescending { it.jogosPublicos.size }
        }
        return encontrados.sortedByDescending { it.jogosPublicos.size }
    }

    private fun nomeComparavel(nome: String): String =
        Normalizer.normalize(nome.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")

    /** Mesmo ID estável usado nas amizades e nas mensagens. */
    fun idAmizadeCom(outroUid: String): String =
        listOf(uid, outroUid).sorted().joinToString("_")

    /** A transação confirma amizade somente com duas curtidas. */
    suspend fun avaliar(outro: String, gostou: Boolean): Boolean {
        val eu = uid
        require(outro != eu && outro.isNotBlank())
        val minha = curtida(eu, outro)
        val reciproca = curtida(outro, eu)
        val ids = listOf(eu, outro).sorted()
        val refAmizade = amizades().document(ids.joinToString("_"))
        return db.runTransaction { tx ->
            val reciprocaExiste = if (gostou) tx.get(reciproca).getBoolean("gostou") == true else false
            tx.set(minha, mapOf("gostou" to gostou, "criadoEm" to FieldValue.serverTimestamp()))
            if (gostou && reciprocaExiste) {
                tx.set(refAmizade, mapOf("participantes" to ids, "criadoEm" to FieldValue.serverTimestamp()))
            }
            gostou && reciprocaExiste
        }.await()
    }

    fun acompanharAmizades(aoAtualizar: (List<Amizade>, Exception?) -> Unit): ListenerRegistration =
        amizades().whereArrayContains("participantes", uid).addSnapshotListener { snap, erro ->
            if (erro != null) aoAtualizar(emptyList(), erro)
            else aoAtualizar(snap?.documents.orEmpty().mapNotNull { d ->
                val partes = (d.get("participantes") as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (partes.size == 2) Amizade(d.id, partes, d.getTimestamp("criadoEm")?.toDate()?.time ?: 0L) else null
            }, null)
        }

    suspend fun perfilPublico(id: String): Jogador? {
        val d = publicos().document(id).get().await()
        if (!d.exists()) return null
        return Jogador(id, d.getString("nome") ?: "Jogador", d.getString("nickname") ?: "",
            d.getString("bio") ?: "", d.getString("jogoFavorito") ?: "",
            d.getString("avatar") ?: "avatar_1",
            (d.get("jogosPublicos") as? List<*>)?.filterIsInstance<String>().orEmpty())
    }

    /** Apenas informações públicas; jamais lista os jogos privados de outra conta. */
    suspend fun jogosEmComum(jogosDoOutro: List<String>): List<String> {
        val meus = publicos().document(uid).get().await()
        val titulos = (meus.get("jogosPublicos") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val chaves = jogosDoOutro.map(::nomeComparavel).toSet()
        return titulos.filter { nomeComparavel(it) in chaves }.distinctBy(::nomeComparavel)
    }

    suspend fun amizadeCom(outroId: String): Amizade? {
        if (outroId == uid || outroId.isBlank()) return null
        val doc = amizades().document(idAmizadeCom(outroId)).get().await()
        val membros = (doc.get("participantes") as? List<*>)?.filterIsInstance<String>().orEmpty()
        return if (membros.size == 2 && uid in membros && outroId in membros)
            Amizade(doc.id, membros, doc.getTimestamp("criadoEm")?.toDate()?.time ?: 0L) else null
    }

    fun acompanharConvites(aoAtualizar: (List<Convite>, Exception?) -> Unit): ListenerRegistration =
        db.collection("convitesGrupos").whereEqualTo("convidadoId", uid)
            .addSnapshotListener { snap, erro ->
                if (erro != null) aoAtualizar(emptyList(), erro)
                else aoAtualizar(snap?.documents.orEmpty().map { d ->
                    Convite(d.id, d.getString("grupoId") ?: "", d.getString("nomeGrupo") ?: "Grupo")
                }, null)
            }

    fun acompanharMensagens(amigoId: String, aoAtualizar: (List<Mensagem>, Exception?) -> Unit): ListenerRegistration =
        amizades().document(amigoId).collection("mensagens")
            .orderBy("criadoEm", Query.Direction.ASCENDING).limitToLast(100)
            .addSnapshotListener { snap, erro ->
                if (erro != null) aoAtualizar(emptyList(), erro)
                else aoAtualizar(snap?.documents.orEmpty().map { d ->
                    Mensagem(d.id, d.getString("autorId") ?: "", d.getString("texto") ?: "",
                        d.getTimestamp("criadoEm")?.toDate()?.time ?: 0L)
                }, null)
            }

    suspend fun enviar(amizadeId: String, texto: String) {
        val t = texto.trim()
        require(t.isNotBlank() && t.length <= 1000)
        amizades().document(amizadeId).collection("mensagens").add(mapOf(
            "autorId" to uid, "texto" to t, "criadoEm" to FieldValue.serverTimestamp()
        )).await()
    }

    fun acompanharUltimaMensagem(id: String,
        cb: (Mensagem?) -> Unit): ListenerRegistration =
        amizades().document(id).collection("mensagens")
            .orderBy("criadoEm", Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { snap, erro ->
                if (erro != null) return@addSnapshotListener
                val d = snap?.documents?.firstOrNull()
                cb(d?.let { Mensagem(it.id, it.getString("autorId") ?: "",
                    it.getString("texto") ?: "", it.getTimestamp("criadoEm")?.toDate()?.time ?: 0L) })
            }

    suspend fun mensagemVista(id: String): String? = db.collection("usuarios").document(uid)
        .collection("conversasLidas").document(id).get().await().getString("mensagemId")

    suspend fun marcarVista(id: String, mensagemId: String) {
        db.collection("usuarios").document(uid).collection("conversasLidas")
            .document(id).set(mapOf("mensagemId" to mensagemId)).await()
    }


    // Grupos: cargos e permissões são armazenados no grupo, nunca só na interface.
    companion object {
        val CARGOS = listOf("administrador", "moderador", "membro")
        val ACOES = linkedMapOf(
            "criarCanais" to "Criar canais",
            "gerenciarCanais" to "Gerenciar canais",
            "convidar" to "Convidar jogadores",
            "removerMembros" to "Remover membros",
            "organizarPartidas" to "Organizar partidas",
            "excluirMensagens" to "Moderador de mensagens"
        )
        val PERMISSOES_INICIAIS = mapOf(
            "criarCanais" to listOf("administrador"),
            "gerenciarCanais" to listOf("administrador"),
            "convidar" to listOf("administrador"),
            "removerMembros" to listOf("administrador"),
            "organizarPartidas" to listOf("administrador", "moderador", "membro"),
            "excluirMensagens" to listOf("administrador", "moderador")
        )
    }

    data class DetalhesGrupo(val id: String, val nome: String, val descricao: String,
        val jogo: String, val publico: Boolean, val criadorId: String,
        val membros: List<String>, val cargos: Map<String, String>,
        val permissoes: Map<String, List<String>>, val imagem: String)

    private fun detalhar(d: com.google.firebase.firestore.DocumentSnapshot): DetalhesGrupo {
        require(d.exists()) { "Este grupo não existe mais." }
        val cargos = (d.get("cargos") as? Map<*, *>)?.entries?.mapNotNull { (k,v) ->
            if (k is String && v is String) k to v else null
        }?.toMap().orEmpty()
        val permiss = (d.get("permissoes") as? Map<*, *>)?.entries?.mapNotNull { (k,v) ->
            if (k is String && v is List<*>) k to v.filterIsInstance<String>() else null
        }?.toMap().orEmpty()
        return DetalhesGrupo(d.id, d.getString("nome") ?: "Grupo", d.getString("descricao") ?: "",
            d.getString("jogo") ?: "", d.getBoolean("publico") ?: true,
            d.getString("criadorId") ?: "", (d.get("membros") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            cargos, permiss, d.getString("imagem") ?: "")
    }

    suspend fun detalhesGrupo(grupoId: String) = detalhar(grupos().document(grupoId).get().await())

    fun cargoDoUsuario(g: DetalhesGrupo, usuario: String = uid): String =
        if (usuario == g.criadorId) "dono" else g.cargos[usuario] ?: "membro"

    fun pode(g: DetalhesGrupo, acao: String): Boolean =
        uid in g.membros && (cargoDoUsuario(g) == "dono" ||
            cargoDoUsuario(g) in (g.permissoes[acao] ?: PERMISSOES_INICIAIS[acao]).orEmpty())

    suspend fun atualizarImagem(grupoId: String, codigo: String) {
        require(detalhesGrupo(grupoId).criadorId == uid)
        require(
            (codigo.startsWith("storage:usuarios/$uid/grupos/$grupoId/") && codigo.length < 300) ||
                (codigo.startsWith("inline:") && codigo.length <= 90_000)
        ) { "Formato da foto do grupo inválido." }
        grupos().document(grupoId).update("imagem", codigo).await()
    }

    suspend fun atualizarInformacoes(grupoId: String, nome: String, descricao: String, jogo: String) {
        require(nome.trim().length in 3..50 && descricao.length <= 500 && jogo.length <= 80)
        require(detalhesGrupo(grupoId).criadorId == uid)
        grupos().document(grupoId).update(mapOf("nome" to nome.trim(),
            "descricao" to descricao.trim(), "jogo" to jogo.trim())).await()
    }

    suspend fun alterarPrivacidade(grupoId: String, publico: Boolean) {
        val g = detalhesGrupo(grupoId)
        require(g.criadorId == uid)
        grupos().document(grupoId).update("publico", publico).await()
    }

    suspend fun atribuirCargo(grupoId: String, membro: String, cargo: String) {
        require(cargo in CARGOS)
        val g = detalhesGrupo(grupoId)
        require(g.criadorId == uid && membro in g.membros && membro != uid)
        grupos().document(grupoId).update("cargos.$membro", cargo).await()
    }

    suspend fun mudarPermissao(grupoId: String, acao: String, cargo: String, permitida: Boolean) {
        require(acao in ACOES && cargo in CARGOS)
        val g = detalhesGrupo(grupoId)
        require(g.criadorId == uid)
        val atual = (g.permissoes[acao] ?: PERMISSOES_INICIAIS[acao]).orEmpty().toMutableSet()
        if (permitida) atual.add(cargo) else atual.remove(cargo)
        grupos().document(grupoId).update("permissoes.$acao", atual.toList()).await()
    }

    suspend fun grupoSilenciado(grupoId: String): Boolean = db.collection("usuarios").document(uid)
        .collection("gruposSilenciados").document(grupoId).get().await().getBoolean("silenciado") == true

    suspend fun silenciarGrupo(grupoId: String, silenciar: Boolean) {
        db.collection("usuarios").document(uid).collection("gruposSilenciados")
            .document(grupoId).set(mapOf("silenciado" to silenciar)).await()
    }

    data class Canal(val id: String, val nome: String, val descricao: String,
        val cargosPermitidos: List<String>, val criadorId: String)


    suspend fun listarCanais(grupoId: String): List<Canal> =
        grupos().document(grupoId)
            .collection("canais")
            .limit(80)
            .get()
            .await()
            .documents.map { d ->
                Canal(
                    id = d.id,
                    nome = d.getString("nome") ?: "Canal",
                    descricao = d.getString("descricao") ?: "",
                    cargosPermitidos = (
                            d.get("cargosPermitidos") as? List<*>
                            )?.filterIsInstance<String>().orEmpty(),
                    criadorId = d.getString("criadorId") ?: ""
                )
            }.sortedBy { it.nome }


    suspend fun criarCanal(grupoId: String, nome: String, descricao: String,
        cargosPermitidos: List<String>): String {

        val n = nome.trim().lowercase()
            .replace(Regex("\\s+"), "-")

        require(n.length in 3..35) {
            "O nome do canal deve ter entre 3 e 35 caracteres."
        }

        require(n.matches(Regex("[a-z0-9_-]+"))) {
            "O nome não pode conter acentos ou caracteres especiais."
        }

        require(cargosPermitidos.all { it in CARGOS }) {
            "Um dos cargos selecionados é inválido."
        }

        val g = detalhesGrupo(grupoId)

        require(uid in g.membros) {
            "Você não está registrado como membro deste grupo."
        }

        require(pode(g, "criarCanais")) {
            "Seu cargo não tem permissão para criar canais."
        }

        val doc = grupos().document(grupoId).collection("canais").document()
        doc.set(mapOf("nome" to n, "descricao" to descricao.trim().take(140),
            "cargosPermitidos" to cargosPermitidos.distinct(), "criadorId" to uid,
            "criadoEm" to FieldValue.serverTimestamp())).await()
        return doc.id
    }

    suspend fun renomearCanal(grupoId: String, canalId: String, novoNome: String) {
        val g = detalhesGrupo(grupoId)
        require(pode(g, "gerenciarCanais"))
        val n = novoNome.trim().lowercase().replace(Regex("\\s+"), "-")
        require(n.length in 3..35 && n.matches(Regex("[a-z0-9_-]+")))
        grupos().document(grupoId).collection("canais").document(canalId).update("nome", n).await()
    }

    suspend fun configurarAcessoCanal(grupoId: String, canalId: String, cargosPermitidos: List<String>) {
        require(cargosPermitidos.all { it in CARGOS })
        require(pode(detalhesGrupo(grupoId), "gerenciarCanais"))
        grupos().document(grupoId).collection("canais").document(canalId)
            .update("cargosPermitidos", cargosPermitidos.distinct()).await()
    }

    suspend fun excluirCanal(grupoId: String, canalId: String) {
        require(pode(detalhesGrupo(grupoId), "gerenciarCanais"))
        val ref = grupos().document(grupoId).collection("canais").document(canalId)
        apagarColecao(ref.collection("mensagens"))
        ref.delete().await()
    }

    private suspend fun apagarColecao(ref: com.google.firebase.firestore.CollectionReference) {
        do {
            val lote = ref.limit(100).get().await().documents
            // Apaga por lotes para não exceder o limite de operações.
            lote.forEach { it.reference.delete().await() }
        } while (lote.isNotEmpty())
    }

    suspend fun excluirGrupo(grupoId: String) {
        val g = detalhesGrupo(grupoId)
        require(g.criadorId == uid)
        val ref = grupos().document(grupoId)
        ref.collection("canais").get().await().documents.forEach { canal ->
            apagarColecao(canal.reference.collection("mensagens"))
            canal.reference.delete().await()
        }
        apagarColecao(ref.collection("mensagens"))
        apagarColecao(ref.collection("partidas"))
        // Remove também convites que já foram enviados pelo dono.
        val enviados = db.collection("convitesGrupos")
            .whereEqualTo("remetenteId", uid).get().await().documents
        enviados.filter { it.getString("grupoId") == grupoId }
            .forEach { it.reference.delete().await() }
        // Grupo apagado por último; falhas anteriores podem ser retomadas.
        ref.delete().await()
        if (g.imagem.startsWith("storage:usuarios/$uid/grupos/$grupoId/")) {
            try {
                com.google.firebase.storage.FirebaseStorage.getInstance().reference
                    .child(g.imagem.removePrefix("storage:")).delete().await()
            } catch (_: Exception) { /* A ausência de Storage não deve impedir excluir o grupo. */ }
        }
    }

    fun acompanharMensagensCanal(grupoId: String, canalId: String,
        cb: (List<Mensagem>, Exception?) -> Unit): ListenerRegistration =
        grupos().document(grupoId).collection("canais").document(canalId).collection("mensagens")
            .orderBy("criadoEm", Query.Direction.ASCENDING).limitToLast(100)
            .addSnapshotListener { snap, erro ->
                if (erro != null) cb(emptyList(), erro)
                else cb(snap?.documents.orEmpty().map { d ->
                    Mensagem(d.id, d.getString("autorId") ?: "", d.getString("texto") ?: "",
                        d.getTimestamp("criadoEm")?.toDate()?.time ?: 0L)
                }, null)
            }

    suspend fun apagarMensagemGrupo(grupoId: String, canalId: String?, mensagemId: String) {
        val ref = if (canalId == null) grupos().document(grupoId).collection("mensagens")
            else grupos().document(grupoId).collection("canais").document(canalId).collection("mensagens")
        ref.document(mensagemId).delete().await()
    }

    suspend fun enviarCanal(grupoId: String, canalId: String, texto: String) {
        require(texto.trim().length in 1..1000)
        grupos().document(grupoId).collection("canais").document(canalId)
            .collection("mensagens").add(mapOf("autorId" to uid,
                "texto" to texto.trim(), "criadoEm" to FieldValue.serverTimestamp())).await()
    }

    private fun grupos() = db.collection("grupos")
    suspend fun criarGrupo(nome: String, descricao: String, jogo: String, publico: Boolean): String {
        require(nome.trim().length in 3..50)
        val ref = grupos().document()
        ref.set(mapOf("nome" to nome.trim(), "descricao" to descricao.trim(),
            "jogo" to jogo.trim(), "publico" to publico, "criadorId" to uid,
            "membros" to listOf(uid), "imagem" to "", "cargos" to mapOf(uid to "dono"),
            "permissoes" to PERMISSOES_INICIAIS, "criadoEm" to FieldValue.serverTimestamp())).await()
        return ref.id
    }

    private fun comoGrupo(d: com.google.firebase.firestore.DocumentSnapshot) = Grupo(
        d.id, d.getString("nome") ?: "Grupo", d.getString("descricao") ?: "",
        d.getString("jogo") ?: "", d.getBoolean("publico") ?: false,
        d.getString("criadorId") ?: "", d.getString("imagem") ?: "")

    suspend fun listarGrupos(): List<Grupo> {
        val publicos = grupos().whereEqualTo("publico", true).limit(50).get().await().documents
        val meus = grupos().whereArrayContains("membros", uid).get().await().documents
        return (publicos + meus).distinctBy { it.id }.map(::comoGrupo)
    }

    suspend fun meusGrupos(): List<Grupo> = grupos().whereArrayContains("membros", uid)
        .get().await().documents.map(::comoGrupo)

    suspend fun participar(grupoId: String) {
        val ref = grupos().document(grupoId)
        val grupo = ref.get().await()
        require(grupo.getBoolean("publico") == true) { "O grupo requer um convite." }
        ref.update("membros", FieldValue.arrayUnion(uid)).await()
    }

    suspend fun membrosGrupo(grupoId: String): List<String> =
        (grupos().document(grupoId).get().await().get("membros") as? List<*>)
            ?.filterIsInstance<String>().orEmpty()

    suspend fun removerMembro(grupoId: String, membro: String) {
        val ref = grupos().document(grupoId)
        val grupo = ref.get().await()
        require(pode(detalhesGrupo(grupoId), "removerMembros") && membro != uid && membro != grupo.getString("criadorId"))
        ref.update("membros", FieldValue.arrayRemove(membro)).await()
    }

    suspend fun sairGrupo(grupoId: String) {
        val ref = grupos().document(grupoId)
        val grupo = ref.get().await()
        require(grupo.getString("criadorId") != uid) { "O criador não pode sair do próprio grupo." }
        ref.update("membros", FieldValue.arrayRemove(uid)).await()
    }

    data class Convite(val id: String, val grupoId: String, val nomeGrupo: String)

    suspend fun convidar(grupoId: String, usuarioId: String) {
        val ref = grupos().document(grupoId)
        val grupo = ref.get().await()
        require(pode(detalhesGrupo(grupoId), "convidar") && usuarioId != uid)
        db.collection("convitesGrupos").document("${grupoId}_$usuarioId").set(mapOf(
            "grupoId" to grupoId, "nomeGrupo" to (grupo.getString("nome") ?: "Grupo"),
            "convidadoId" to usuarioId, "remetenteId" to uid,
            "criadoEm" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun listarConvites(): List<Convite> = db.collection("convitesGrupos")
        .whereEqualTo("convidadoId", uid).get().await().documents.map { d ->
            Convite(d.id, d.getString("grupoId") ?: "", d.getString("nomeGrupo") ?: "Grupo")
        }

    suspend fun responderConvite(convite: Convite, aceitar: Boolean) {
        val doc = db.collection("convitesGrupos").document(convite.id)
        if (aceitar) {
            grupos().document(convite.grupoId).update("membros", FieldValue.arrayUnion(uid)).await()
        }
        doc.delete().await()
    }

    suspend fun organizarPartida(grupoId: String, jogo: String, horario: String, vagas: Int) {
        require(jogo.trim().isNotBlank() && horario.trim().isNotBlank() && vagas in 1..30)
        require(pode(detalhesGrupo(grupoId), "organizarPartidas"))
        grupos().document(grupoId).collection("partidas").add(mapOf(
            "jogo" to jogo.trim(), "horario" to horario.trim(), "vagas" to vagas,
            "organizadorId" to uid, "confirmados" to listOf(uid),
            "criadoEm" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun listarPartidas(grupoId: String): List<Map<String, Any?>> =
        grupos().document(grupoId).collection("partidas").limit(30).get().await()
            .documents.mapNotNull { it.data?.plus("id" to it.id) }

    suspend fun confirmarPartida(grupoId: String, partidaId: String) {
        val ref = grupos().document(grupoId).collection("partidas").document(partidaId)
        db.runTransaction { tx ->
            val d = tx.get(ref)
            val pessoas = (d.get("confirmados") as? List<*>)?.filterIsInstance<String>().orEmpty()
            val vagas = d.getLong("vagas")?.toInt() ?: 0
            if (uid !in pessoas && pessoas.size >= vagas) error("Esta partida está cheia.")
            tx.update(ref, "confirmados", FieldValue.arrayUnion(uid))
        }.await()
    }

    fun acompanharMensagensGrupo(grupoId: String, cb: (List<Mensagem>, Exception?) -> Unit): ListenerRegistration =
        grupos().document(grupoId).collection("mensagens")
            .orderBy("criadoEm", Query.Direction.ASCENDING).limitToLast(100)
            .addSnapshotListener { snap, erro ->
                if (erro != null) cb(emptyList(), erro)
                else cb(snap?.documents.orEmpty().map { d ->
                    Mensagem(d.id, d.getString("autorId") ?: "", d.getString("texto") ?: "",
                        d.getTimestamp("criadoEm")?.toDate()?.time ?: 0L)
                }, null)
            }



    suspend fun solicitarAmizade(outroId: String) {
        val eu = uid
        require(outroId.isNotBlank() && outroId != eu)

        db.collection("pedidosAmizade")
            .document("${eu}_$outroId")
            .set(
                mapOf(
                    "remetenteId" to eu,
                    "destinatarioId" to outroId,
                    "status" to "pendente",
                    "criadoEm" to FieldValue.serverTimestamp()
                )
            ).await()
    }



    suspend fun passarJogador(outroId: String) {
        require(outroId.isNotBlank() && outroId != uid)

        db.collection("usuarios")
            .document(uid)
            .collection("curtidas")
            .document(outroId)
            .set(
                mapOf(
                    "gostou" to false,
                    "criadoEm" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun enviarGrupo(grupoId: String, texto: String) {
        require(texto.trim().length in 1..1000)
        grupos().document(grupoId).collection("mensagens").add(mapOf(
            "autorId" to uid, "texto" to texto.trim(), "criadoEm" to FieldValue.serverTimestamp()
        )).await()
    }
}

