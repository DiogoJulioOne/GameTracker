package com.example.gametracker.modelos

/** Documento em usuarios/{uid}/jogos/{id} do Cloud Firestore. */
data class Jogo(
    val id: String,
    val titulo: String,
    val plataforma: String,
    val status: String,
    val dataInicio: String = "",
    val horasJogadas: Double? = null,
    val progresso: Int? = null,
    val observacoes: String = "",
    val usuarioId: String = ""
)
