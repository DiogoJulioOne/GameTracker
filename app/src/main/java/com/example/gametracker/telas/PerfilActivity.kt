package com.example.gametracker.telas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.R
import com.example.gametracker.dados.FirebaseUsuarioRepository
import com.example.gametracker.dados.JogoRepository
import com.example.gametracker.modelos.Jogo
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale

import com.example.gametracker.BottomNavigationHelper
import com.example.gametracker.utils.AvatarStorage

class PerfilActivity : AppCompatActivity() {
    private val firebaseUsuarios by lazy { FirebaseUsuarioRepository() }
    private val jogos by lazy { JogoRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        BottomNavigationHelper.configurar(this, BottomNavigationHelper.BottomTab.PERFIL)

        findViewById<Button>(R.id.buttonEditarPerfil).setOnClickListener {
            startActivity(Intent(this, EditarPerfilActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        findViewById<Switch>(R.id.switchEfeitoCards).apply {
            isChecked = getSharedPreferences("gt_config", MODE_PRIVATE).getBoolean("tilt_cards", true)
            setOnCheckedChangeListener { _, ligado ->
                getSharedPreferences("gt_config", MODE_PRIVATE).edit().putBoolean("tilt_cards", ligado).apply()
            }
        }
        findViewById<Button>(R.id.buttonSairPerfil).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finishAffinity()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                voltarAoLogin()
                return@launch
            }

            val usuarioMap = try {
                try { firebaseUsuarios.sincronizarEmailVerificado() } catch (_: Exception) { }
                firebaseUsuarios.carregarUsuarioAtual(uid)
            } catch (_: Exception) {
                android.widget.Toast.makeText(this@PerfilActivity,
                    "Não foi possível carregar o perfil. Verifique sua conexão.",
                    android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            if (usuarioMap == null) {
                FirebaseAuth.getInstance().signOut()
                voltarAoLogin()
                return@launch
            }

            val usuario = mapearUsuario(usuarioMap, uid)
            val biblioteca = try { jogos.listar(uid) } catch (_: Exception) { emptyList() }
            exibirPerfil(usuario, biblioteca)
        }
    }

    private fun mapearUsuario(dados: Map<String, Any?>, uid: String): com.example.gametracker.modelos.Usuario {
        val nome = dados["nome"] as? String ?: ""
        val nickname = dados["nickname"] as? String ?: ""
        val email = dados["email"] as? String ?: ""
        val bio = dados["bio"] as? String ?: ""
        val jogoFavorito = dados["jogoFavorito"] as? String ?: ""
        val avatar = dados["avatar"] as? String ?: "avatar_1"

        return com.example.gametracker.modelos.Usuario(
            id = uid,
            nome = nome,
            usuario = nickname.ifBlank { nome },
            nickname = nickname,
            email = email,
            bio = bio,
            jogoFavorito = jogoFavorito,
            avatar = avatar
        )
    }

    private fun exibirPerfil(usuario: com.example.gametracker.modelos.Usuario, biblioteca: List<Jogo>) {
        findViewById<TextView>(R.id.textoNomePerfil).text = usuario.nome
        findViewById<TextView>(R.id.textoNicknamePerfil)?.text = "@${usuario.nickname.ifBlank { usuario.usuario }}"
        findViewById<TextView>(R.id.textoEmailPerfil)?.text = usuario.email.ifBlank { getString(R.string.nao_informado) }
        findViewById<TextView>(R.id.textoBioPerfil)?.text = usuario.bio.ifBlank { getString(R.string.nao_informado) }
        findViewById<TextView>(R.id.textoJogoFavoritoPerfil)?.text = usuario.jogoFavorito.ifBlank { getString(R.string.nao_informado) }
        val avatarView = findViewById<android.widget.ImageView>(R.id.imagemAvatarPerfil)

        lifecycleScope.launch {
            AvatarStorage.mostrar(this@PerfilActivity, avatarView, usuario.avatar)
        }
        preencherCard(R.id.cardTotalJogos, biblioteca.size, R.string.perfil_jogos)
        preencherCard(R.id.cardJogando, contarStatus(biblioteca, STATUS_JOGANDO), R.string.perfil_jogando)
        preencherCard(R.id.cardPausados, contarStatus(biblioteca, STATUS_PAUSADO), R.string.perfil_pausados)
        preencherCard(R.id.cardZerados, contarStatus(biblioteca, STATUS_ZERADO, STATUS_CONCLUIDO_ANTERIOR), R.string.perfil_zerados)
        preencherCard(R.id.cardQueroJogar, contarStatus(biblioteca, STATUS_QUERO_JOGAR), R.string.perfil_quero_jogar)

        val totalHoras = biblioteca.sumOf { it.horasJogadas?.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0 }
        findViewById<TextView>(R.id.textoTotalHoras).text = getString(R.string.perfil_horas_valor, formatNumero(totalHoras))

        val maisJogado = biblioteca.filter { it.horasJogadas?.isFinite() == true && it.horasJogadas >= 0.0 }.maxByOrNull { it.horasJogadas ?: 0.0 }
        findViewById<TextView>(R.id.textoMaisJogado).text =
            maisJogado?.let { getString(R.string.perfil_mais_jogado_valor, it.titulo, formatNumero(it.horasJogadas ?: 0.0)) } ?: getString(R.string.nao_informado)

        val progressos = biblioteca.mapNotNull { it.progresso?.coerceIn(0, 100) }
        val progressoMedio = if (progressos.isEmpty()) 0 else progressos.average().toInt()
        findViewById<TextView>(R.id.textoProgressoMedio).text =
            getString(R.string.perfil_progresso_valor, progressoMedio)
        findViewById<ProgressBar>(R.id.progressBarProgressoMedio).progress = progressoMedio

        val zerados = contarStatus(biblioteca, STATUS_ZERADO, STATUS_CONCLUIDO_ANTERIOR)
        findViewById<TextView>(R.id.textoJogosConcluidos).text =
            getString(R.string.perfil_concluidos_valor, zerados, biblioteca.size)
        findViewById<TextView>(R.id.textoPerfilVazio).visibility =
            if (biblioteca.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun preencherCard(cardId: Int, valor: Int, label: Int) {
        val card = findViewById<android.widget.LinearLayout>(cardId)
        (card.getChildAt(0) as TextView).text = valor.toString()
        (card.getChildAt(1) as TextView).setText(label)
    }

    private fun contarStatus(jogos: List<Jogo>, vararg status: String): Int =
        jogos.count { jogo -> status.any { jogo.status.equals(it, ignoreCase = true) } }

    private fun formatNumero(valor: Double): String =
        String.format(Locale.getDefault(), if (valor % 1.0 == 0.0) "%.0f" else "%.1f", valor)

    private fun voltarAoLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
    }

    private companion object {
        const val STATUS_JOGANDO = "Jogando"
        const val STATUS_PAUSADO = "Pausado"
        const val STATUS_ZERADO = "Zerado"
        const val STATUS_QUERO_JOGAR = "Quero jogar"
        const val STATUS_CONCLUIDO_ANTERIOR = "Concluído"
    }
}
