package com.example.gametracker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import com.example.gametracker.R
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Imagens de usuário ficam privadas no Storage, sem tokens públicos de download. */
object AvatarStorage {
    private const val LIMITE_DOWNLOAD = 3L * 1024L * 1024L

    suspend fun enviarFoto(context: Context, uid: String, uri: Uri): String {
        val bytes = withContext(Dispatchers.IO) { prepararJpeg(context, uri) }
        val caminho = "usuarios/$uid/avatars/${UUID.randomUUID()}.jpg"
        val arquivo = FirebaseStorage.getInstance().reference.child(caminho)
        val metadados = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg").build()
        arquivo.putBytes(bytes, metadados).await()
        return "storage:$caminho"
    }

    suspend fun mostrar(context: Context, imagem: ImageView, codigo: String) {
        val avatar = codigo.ifBlank { "avatar_1" }
        if (avatar.startsWith("avatar_")) {
            imagem.setImageResource(recursoPadrao(avatar))
            return
        }
        val bitmap = try {
            val bytes = when {
                avatar.startsWith("inline:") -> withContext(Dispatchers.Default) {
                    val encoded = avatar.removePrefix("inline:")
                    if (encoded.length > 100_000) null else Base64.decode(encoded, Base64.DEFAULT)
                }
                avatar.startsWith("storage:") -> FirebaseStorage.getInstance().reference
                    .child(avatar.removePrefix("storage:")).getBytes(LIMITE_DOWNLOAD).await()
                avatar.startsWith("uri:") || avatar.startsWith("file:") -> withContext(Dispatchers.IO) {
                    val uri = if (avatar.startsWith("uri:")) {
                        Uri.parse(avatar.removePrefix("uri:"))
                    } else {
                        Uri.fromFile(java.io.File(avatar.removePrefix("file:")))
                    }
                    prepararJpeg(context, uri)
                }
                else -> null
            }
            bytes?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) } }
        } catch (_: Exception) { null }
        if (bitmap != null) imagem.setImageBitmap(bitmap)
        else imagem.setImageResource(R.drawable.avatar_real_1)
    }

    private fun recursoPadrao(chave: String) = when (chave) {
        "avatar_2" -> R.drawable.avatar_real_2
        "avatar_3" -> R.drawable.avatar_real_3
        "avatar_4" -> R.drawable.avatar_real_4
        "avatar_5" -> R.drawable.avatar_real_5
        "avatar_6" -> R.drawable.avatar_real_6
        else -> R.drawable.avatar_real_1
    }

    private fun prepararJpeg(context: Context, uri: Uri): ByteArray {
        val resolutor = context.contentResolver
        val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolutor.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcoes) }
        require(opcoes.outWidth > 0 && opcoes.outHeight > 0) { "Não foi possível ler esta foto." }
        var amostra = 1
        while (maxOf(opcoes.outWidth, opcoes.outHeight) / amostra > 1600) amostra *= 2
        val reduzida = BitmapFactory.Options().apply { inSampleSize = amostra }
        val original = resolutor.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, reduzida)
        } ?: error("Não foi possível carregar a imagem selecionada.")
        val escala = minOf(1f, 1024f / maxOf(original.width, original.height))
        val destino = if (escala < 1f) Bitmap.createScaledBitmap(
            original, (original.width * escala).toInt().coerceAtLeast(1),
            (original.height * escala).toInt().coerceAtLeast(1), true
        ) else original
        val buffer = ByteArrayOutputStream()
        destino.compress(Bitmap.CompressFormat.JPEG, 82, buffer)
        if (destino !== original) destino.recycle()
        original.recycle()
        val bytes = buffer.toByteArray()
        require(bytes.size <= 2 * 1024 * 1024) { "A foto excede o tamanho permitido." }
        return bytes
    }
}
