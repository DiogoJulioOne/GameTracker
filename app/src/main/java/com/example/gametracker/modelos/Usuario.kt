package com.example.gametracker.modelos

/** Modelo de exibição do perfil; a autenticação pertence ao Firebase Auth. */
data class Usuario(
    val id: String,
    val nome: String,
    val usuario: String,
    val nickname: String = "",
    val email: String = "",
    val bio: String = "",
    val jogoFavorito: String = "",
    val avatar: String = "avatar_1"
)
