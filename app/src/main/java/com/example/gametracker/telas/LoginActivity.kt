package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import android.util.Patterns
import com.example.gametracker.utils.AnimacoesLeves
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.MainActivity
import com.example.gametracker.R
import com.example.gametracker.dados.FirebaseUsuarioRepository
import kotlinx.coroutines.launch

/** Login apenas com email/senha: nickname não expõe mais email publicamente. */
class LoginActivity : AppCompatActivity() {
    private val usuarios = FirebaseUsuarioRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val email = findViewById<EditText>(R.id.editNomeUsuario)
        val senha = findViewById<EditText>(R.id.editSenha)
        val entrar = findViewById<Button>(R.id.buttonEntrar)

        AnimacoesLeves.entrada(findViewById(android.R.id.content))
        findViewById<TextView>(R.id.buttonEsqueciSenha).setOnClickListener {
            val endereco = email.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(endereco).matches()) {
                email.error = "Digite seu e-mail para recuperar a senha."
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(endereco).await()
                    Toast.makeText(this@LoginActivity,
                        "Se o e-mail estiver cadastrado, você receberá um link de recuperação.",
                        Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this@LoginActivity,
                        "Não foi possível enviar o e-mail agora.", Toast.LENGTH_LONG).show()
                }
            }
        }
        validarSessaoAtiva()
        entrar.setOnClickListener {
            val e = email.text.toString().trim()
            val s = senha.text.toString()
            if (e.isBlank() || s.isBlank()) {
                Toast.makeText(this, "Informe email e senha.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            entrar.isEnabled = false
            lifecycleScope.launch {
                try {
                    val uid = usuarios.fazerLogin(e, s)
                    if (usuarios.carregarUsuarioAtual(uid) == null) {
                        usuarios.signOut()
                        error("Seu perfil não foi encontrado.")
                    }
                    abrirBiblioteca()
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity,
                        "Não foi possível entrar. Confira o email, a senha e sua conexão.",
                        Toast.LENGTH_LONG).show()
                    entrar.isEnabled = true
                }
            }
        }

        findViewById<Button>(R.id.buttonIrCadastro).setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
    }

    private fun validarSessaoAtiva() {
        val uid = usuarios.getCurrentUserUid() ?: return
        lifecycleScope.launch {
            try {
                if (usuarios.carregarUsuarioAtual(uid) != null) {
                    abrirBiblioteca()
                } else {
                    usuarios.signOut()
                }
            } catch (_: Exception) {
                // Não encerra uma sessão válida só porque o aparelho está offline.
            }
        }
    }

    private fun abrirBiblioteca() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
