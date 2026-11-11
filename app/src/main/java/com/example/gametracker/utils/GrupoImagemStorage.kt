package com.example.gametracker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Miniatura JPEG do grupo em Firestore. Não exige Firebase Storage/Blaze.
 * Limite pequeno para respeitar 1 MiB por documento e economizar leituras.
 * A estrutura antiga storage:... continua sendo suportada na leitura.
 */
object GrupoImagemStorage {
    suspend fun prepararMiniatura(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val dimensoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, dimensoes) }
        require(dimensoes.outWidth > 0 && dimensoes.outHeight > 0) { "Não foi possível ler a foto escolhida." }
        var amostra = 1
        while (maxOf(dimensoes.outWidth, dimensoes.outHeight) / amostra > 640) amostra *= 2
        val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
        val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcoes) }
            ?: error("Não foi possível abrir a imagem. Tente outra foto.")
        try {
            val escala = minOf(1f, 240f / maxOf(original.width, original.height))
            val miniatura = if (escala < 1f) Bitmap.createScaledBitmap(original,
                (original.width * escala).toInt().coerceAtLeast(1),
                (original.height * escala).toInt().coerceAtLeast(1), true) else original
            try {
                val saida = ByteArrayOutputStream()
                miniatura.compress(Bitmap.CompressFormat.JPEG, 65, saida)
                val bytes = saida.toByteArray()
                require(bytes.isNotEmpty() && bytes.size <= 65 * 1024) {
                    "A foto não pôde ser reduzida. Escolha outra imagem."
                }
                "inline:" + Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally { if (miniatura !== original) miniatura.recycle() }
        } finally { original.recycle() }
    }
}
