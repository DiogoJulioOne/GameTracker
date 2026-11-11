package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.MainActivity
import com.example.gametracker.R
import com.example.gametracker.dados.FirebaseUsuarioRepository
import kotlinx.coroutines.launch
import com.example.gametracker.utils.AnimacoesLeves

class CadastroActivity : AppCompatActivity() {

    private val firebaseUsuarios by lazy {
        FirebaseUsuarioRepository()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)
        AnimacoesLeves.entrada(findViewById(android.R.id.content))

        val btnVoltar = findViewById<ImageButton>(R.id.btnVoltar)
        val nome = findViewById<EditText>(R.id.editNomeUsuario)
        val email = findViewById<EditText>(R.id.editEmail)
        val senha = findViewById<EditText>(R.id.editSenha)
        val confirmarSenha = findViewById<EditText>(R.id.editConfirmarSenha)
        val botaoCadastrar = findViewById<Button>(R.id.buttonCadastrar)
        val textJaTemConta = findViewById<TextView>(R.id.textJaTemConta)

        btnVoltar.setOnClickListener {
            voltarParaLogin()
        }

        textJaTemConta.setOnClickListener {
            voltarParaLogin()
        }

        botaoCadastrar.setOnClickListener {

            val nomeDigitado = nome.text.toString().trim()
            val emailDigitado = email.text.toString().trim().lowercase()
            val senhaDigitada = senha.text.toString()
            val confirmacaoDigitada = confirmarSenha.text.toString()

            when {

                nomeDigitado.isBlank() ||
                        emailDigitado.isBlank() ||
                        senhaDigitada.isBlank() ||
                        confirmacaoDigitada.isBlank() -> {

                    mostrarMensagem("Preencha todos os campos.")
                }

                nomeDigitado.length < 3 -> {
                    mostrarMensagem("O nome deve ter pelo menos 3 caracteres.")
                }

                !Patterns.EMAIL_ADDRESS.matcher(emailDigitado).matches() -> {
                    mostrarMensagem("Digite um e-mail válido.")
                }

                senhaDigitada.length < 6 -> {
                    mostrarMensagem("A senha deve ter pelo menos 6 caracteres.")
                }

                senhaDigitada != confirmacaoDigitada -> {
                    mostrarMensagem("As senhas não coincidem.")
                }

                else -> {

                    botaoCadastrar.isEnabled = false

                    lifecycleScope.launch {

                        try {

                            // Cria a conta do usuário no Firebase
                            val uid = firebaseUsuarios.cadastrarComEmailESenha(
                                nome = nomeDigitado,
                                email = emailDigitado,
                                senha = senhaDigitada,
                                nicknameOpcional = nomeDigitado
                            )

                            if (uid.isNotBlank()) {

                                mostrarMensagem("Conta criada com sucesso.")

                                startActivity(
                                    Intent(
                                        this@CadastroActivity,
                                        PerfilActivity::class.java
                                    )
                                )

                                finish()
                            }

                        } catch (erro: Exception) {

                            mostrarMensagem(
                                erro.message ?: "Erro desconhecido ao criar a conta."
                            )

                            botaoCadastrar.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun voltarParaLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private fun mostrarMensagem(mensagem: String) {
        Toast.makeText(
            this,
            mensagem,
            Toast.LENGTH_SHORT
        ).show()
    }
}