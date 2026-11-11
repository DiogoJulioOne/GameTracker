package com.example.gametracker.telas

import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.R
import com.example.gametracker.dados.FirebaseUsuarioRepository
import com.example.gametracker.dados.JogoRepository
import com.example.gametracker.utils.AvatarStorage
import kotlinx.coroutines.launch

class EditarPerfilActivity : AppCompatActivity() {
    private val usuarios = FirebaseUsuarioRepository()
    private val jogos by lazy { JogoRepository(this) }
    private var uid: String? = null
    private var avatarAtual = "avatar_1"
    private var fotoNova: Uri? = null
    private var emailOriginal = ""
    private lateinit var selecionarFoto: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_perfil)

        selecionarFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                fotoNova = uri
                lifecycleScope.launch {
                    AvatarStorage.mostrar(this@EditarPerfilActivity,
                        findViewById(R.id.imagemAvatarPreview), "uri:$uri")
                }
                destacarAvatar(null)
            }
        }
        findViewById<ImageButton>(R.id.buttonCancelarEditar).setOnClickListener { finish() }
        findViewById<Button>(R.id.buttonCancelarFormulario).setOnClickListener { finish() }
        findViewById<Button>(R.id.buttonEscolherFoto).setOnClickListener {
            selecionarFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<Button>(R.id.buttonSalvarEditar).setOnClickListener { salvar() }

        listOf(R.id.avatar1, R.id.avatar2, R.id.avatar3, R.id.avatar4, R.id.avatar5, R.id.avatar6)
            .forEachIndexed { indice, id ->
                findViewById<ImageView>(id).setOnClickListener {
                    fotoNova = null
                    avatarAtual = "avatar_${indice + 1}"
                    destacarAvatar(avatarAtual)
                    lifecycleScope.launch {
                        AvatarStorage.mostrar(this@EditarPerfilActivity,
                            findViewById(R.id.imagemAvatarPreview), avatarAtual)
                    }
                }
            }
        carregar()
    }

    private fun carregar() {
        lifecycleScope.launch {
            val atual = usuarios.getCurrentUserUid() ?: run { finish(); return@launch }
            uid = atual
            try {
                try { usuarios.sincronizarEmailVerificado() } catch (_: Exception) { }
                val dados = usuarios.carregarUsuarioAtual(atual) ?: error("Perfil inexistente")
                fun texto(campo: String) = dados[campo] as? String ?: ""
                findViewById<EditText>(R.id.inputNome).setText(texto("nome"))
                findViewById<EditText>(R.id.inputNickname).setText(texto("nickname"))
                emailOriginal = texto("email")
                findViewById<EditText>(R.id.inputEmail).setText(emailOriginal)
                findViewById<EditText>(R.id.inputBio).setText(texto("bio"))
                val favorito = findViewById<AutoCompleteTextView>(R.id.inputJogoFavorito)
                favorito.setText(texto("jogoFavorito"))
                avatarAtual = texto("avatar").ifBlank { "avatar_1" }
                destacarAvatar(avatarAtual)
                AvatarStorage.mostrar(this@EditarPerfilActivity,
                    findViewById(R.id.imagemAvatarPreview), avatarAtual)
                try {
                    val titulos = jogos.listar(atual).map { it.titulo }
                    favorito.setAdapter(ArrayAdapter(this@EditarPerfilActivity,
                        android.R.layout.simple_dropdown_item_1line, titulos))
                } catch (_: Exception) { /* edição do perfil independe da biblioteca */ }
            } catch (erro: Exception) {
                Toast.makeText(this@EditarPerfilActivity,
                    "Não foi possível carregar o perfil.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun destacarAvatar(chave: String?) {
        listOf(R.id.avatar1, R.id.avatar2, R.id.avatar3, R.id.avatar4, R.id.avatar5, R.id.avatar6)
            .forEachIndexed { index, id ->
                findViewById<ImageView>(id).alpha = if (chave == "avatar_${index + 1}") 1f else 0.5f
            }
    }

    private fun salvar() {
        val atual = uid ?: return
        val nome = findViewById<EditText>(R.id.inputNome).text.toString().trim()
        val nickname = findViewById<EditText>(R.id.inputNickname).text.toString().trim()
        val email = findViewById<EditText>(R.id.inputEmail).text.toString().trim()
        val bio = findViewById<EditText>(R.id.inputBio).text.toString().trim()
        val favorito = findViewById<AutoCompleteTextView>(R.id.inputJogoFavorito).text.toString().trim()
        if (nome.isBlank() || nickname.length !in 3..30) {
            Toast.makeText(this, "Informe nome e nickname (3 a 30 caracteres).", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Informe um email válido.", Toast.LENGTH_SHORT).show()
            return
        }
        val botao = findViewById<Button>(R.id.buttonSalvarEditar)
        botao.isEnabled = false
        lifecycleScope.launch {
            try {
                val emailPendente = !email.equals(emailOriginal, ignoreCase = true)
                if (emailPendente) usuarios.solicitarTrocaEmail(email)
                val avatarSalvo = when {
                    fotoNova != null -> AvatarStorage.enviarFoto(this@EditarPerfilActivity, atual, fotoNova!!)
                    else -> avatarAtual
                }
                usuarios.salvarPerfil(
                    uid = atual, nome = nome, nickname = nickname,
                    bio = bio, jogoFavorito = favorito, avatar = avatarSalvo
                )
                Toast.makeText(this@EditarPerfilActivity,
                    if (emailPendente) "Perfil salvo. Confirme o novo email pelo link enviado."
                    else "Perfil salvo com sucesso!", Toast.LENGTH_LONG).show()
                finish()
            } catch (erro: Exception) {
                Toast.makeText(this@EditarPerfilActivity,
                    erro.localizedMessage ?: "Não foi possível salvar. Tente novamente.",
                    Toast.LENGTH_LONG).show()
                botao.isEnabled = true
            }
        }
    }
}
