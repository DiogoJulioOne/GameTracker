package com.example.gametracker.dados

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.gametracker.modelos.Jogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Leitura sem Room da base antiga para uma migração não destrutiva. */
class JogosLegadosReader(private val context: Context) {
    suspend fun listarDoUsuario(uid: String): List<Jogo> = withContext(Dispatchers.IO) {
        lerBanco(uid)
    }

    suspend fun listarSemConta(): List<Jogo> = withContext(Dispatchers.IO) {
        val doRoom = lerBanco("legacy-jogador")
        val doPrefs = lerPreferencias()
        (doRoom + doPrefs).distinctBy { it.id }
    }

    private fun lerBanco(uid: String): List<Jogo> {
        val caminho = context.getDatabasePath("gametracker.db")
        if (!caminho.exists()) return emptyList()
        return SQLiteDatabase.openDatabase(
            caminho.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            val colunas = db.rawQuery("PRAGMA table_info(jogos)", null).use { cur ->
                val nomes = mutableSetOf<String>()
                val pos = cur.getColumnIndexOrThrow("name")
                while (cur.moveToNext()) nomes += cur.getString(pos)
                nomes
            }
            if ("usuarioId" !in colunas) return@use emptyList()
            db.query(
                "jogos", null, "usuarioId = ?", arrayOf(uid),
                null, null, null
            ).use { cur ->
                val resultado = mutableListOf<Jogo>()
                fun coluna(nome: String): Int = cur.getColumnIndex(nome)
                fun str(nome: String): String = coluna(nome).takeIf { it >= 0 }
                    ?.let { cur.getString(it) }.orEmpty()
                while (cur.moveToNext()) {
                    val horas = coluna("horasJogadas")
                    val progresso = coluna("progresso")
                    resultado += Jogo(
                        id = str("id"),
                        titulo = str("titulo"),
                        plataforma = str("plataforma"),
                        status = str("status"),
                        dataInicio = str("dataInicio"),
                        horasJogadas = if (horas >= 0 && !cur.isNull(horas)) cur.getDouble(horas) else null,
                        progresso = if (progresso >= 0 && !cur.isNull(progresso)) cur.getInt(progresso) else null,
                        observacoes = str("observacoes"),
                        usuarioId = uid
                    )
                }
                resultado.filter { it.id.isNotBlank() }
            }
        }
    }

    private fun lerPreferencias(): List<Jogo> {
        val json = context.getSharedPreferences("game_tracker", Context.MODE_PRIVATE)
            .getString("jogos_jogador", null) ?: return emptyList()
        return try {
            val itens = JSONArray(json)
            (0 until itens.length()).mapNotNull { i ->
                val item = itens.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optString("id")
                if (id.isBlank()) return@mapNotNull null
                Jogo(
                    id = id,
                    titulo = item.optString("titulo", "Jogo antigo"),
                    plataforma = item.optString("plataforma", ""),
                    status = item.optString("status", "Quero jogar"),
                    dataInicio = item.optString("dataInicio", ""),
                    horasJogadas = (item.opt("horasJogadas") as? Number)?.toDouble(),
                    progresso = (item.opt("progresso") as? Number)?.toInt(),
                    observacoes = item.optString("observacoes", ""),
                    usuarioId = "legacy-jogador"
                )
            }
        } catch (_: org.json.JSONException) {
            emptyList() // preserva o JSON original caso esteja danificado
        }
    }
}
