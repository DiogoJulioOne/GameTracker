package com.example.gametracker

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.example.gametracker.utils.DialogosGameTracker
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.dados.JogoRepository
import com.example.gametracker.modelos.Jogo
import com.example.gametracker.telas.AdicionarJogoActivity
import com.example.gametracker.telas.DetalhesJogoActivity
import com.example.gametracker.telas.LoginActivity
import com.example.gametracker.telas.RankingActivity
import com.example.gametracker.telas.PerfilActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.google.firebase.auth.FirebaseAuth
import com.example.gametracker.dados.FirebaseUsuarioRepository
import com.example.gametracker.utils.CapaJogo
import android.view.inputmethod.InputMethodManager

class MainActivity : AppCompatActivity(), SensorEventListener {

    private val jogos by lazy { JogoRepository(this) }

    private val firebaseUsuarios by lazy {
        FirebaseUsuarioRepository()
    }

    private lateinit var listaJogos: LinearLayout
    private lateinit var textoVazio: TextView
    private lateinit var textoContagem: TextView
    private lateinit var editPesquisaJogos: EditText

    private var filtroStatusSelecionado = FILTRO_TODOS
    private var carregamentoLista: Job? = null
    private var usuarioId: String? = null
    private var jogosCarregados: List<Jogo> = emptyList()
    private var cachePronto = false
    private var cacheUid: String? = null
    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null

    private var ultimaAtualizacaoSensor = 0L
    private var suavizadoX = 0f
    private var suavizadoY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        listaJogos = findViewById(R.id.listaJogos)
        textoVazio = findViewById(R.id.textoVazio)
        textoContagem = findViewById(R.id.textoContagem)
        editPesquisaJogos = findViewById(R.id.editPesquisaJogos)

        BottomNavigationHelper.configurar(this, BottomNavigationHelper.BottomTab.BIBLIOTECA)

        configurarFiltros()
        editPesquisaJogos.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderizarLista()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        findViewById<View>(R.id.buttonAbrirRanking).setOnClickListener {
            startActivity(Intent(this, RankingActivity::class.java))
        }

        findViewById<View>(R.id.botaoPesquisarBiblioteca).setOnClickListener {
            editPesquisaJogos.visibility = if (editPesquisaJogos.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (editPesquisaJogos.visibility == View.VISIBLE) editPesquisaJogos.requestFocus()
        }

        findViewById<Button>(R.id.buttonAdicionarJogo).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    AdicionarJogoActivity::class.java
                )
            )
        }


        lifecycleScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid

            if (uid == null) {
                voltarAoLogin()
                return@launch
            }
            usuarioId = uid

            try {
                val dadosUsuario = firebaseUsuarios.carregarUsuarioAtual(uid)
                val nomeUsuario = dadosUsuario?.get("nome") as? String ?: "Usuário"
                // O cabeçalho mantém a legenda da referência; os dados do usuário são preservados.
                findViewById<TextView>(R.id.textoBoasVindas).text =
                    "Gerencie seus jogos e acompanhe seu progresso."
                atualizarLista()
                oferecerImportacaoLegada(uid)
            } catch (_: Exception) {
                findViewById<TextView>(R.id.textoBoasVindas).text = "Gerencie seus jogos e acompanhe seu progresso."
                // Não desloga o usuário apenas por uma falha de rede.
            }
        }
    }

    override fun onResume() {
        super.onResume()
        baseX = null
        baseY = null
        suavizadoX = 0f
        suavizadoY = 0f
        ultimaAtualizacaoSensor = 0L
        // Limpa qualquer transformacao antiga, inclusive ao desativar no Perfil.
        resetarMovimentoCards()

        if (getSharedPreferences("gt_config", MODE_PRIVATE).getBoolean("tilt_cards", true)) acelerometro?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        // Revalida o usuário atual ao voltar de outra tela para evitar manter uma lista vazia
        // ou qualquer referência obsoleta ao usuário anterior.
        usuarioId = FirebaseAuth.getInstance().currentUser?.uid
        if (usuarioId == null) {
            voltarAoLogin()
            return
        }

        if (::listaJogos.isInitialized) {
            atualizarLista()
        }
    }


    override fun onPause() {
        editPesquisaJogos.clearFocus()

        val raiz = findViewById<View>(android.R.id.content)
        raiz.isFocusableInTouchMode = true
        raiz.requestFocus()

        val teclado = getSystemService(INPUT_METHOD_SERVICE)
                as InputMethodManager

        teclado.hideSoftInputFromWindow(
            editPesquisaJogos.windowToken,
            0
        )

        sensorManager.unregisterListener(this)
        resetarMovimentoCards()
        super.onPause()
    }

    /** Consulta o Firestore apenas ao abrir/retornar da edição, não a cada tecla. */
    private fun atualizarLista() {
        val donoId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            voltarAoLogin()
            return
        }
        if (cacheUid != donoId) {
            jogosCarregados = emptyList()
            cachePronto = false
            cacheUid = donoId
            renderizarLista()
        }
        usuarioId = donoId
        carregamentoLista?.cancel()
        carregamentoLista = lifecycleScope.launch {
            try {
                jogosCarregados = jogos.listar(donoId)
                cachePronto = true
                renderizarLista()
            } catch (_: Exception) {
                // Não apaga os cards já carregados em caso de falha temporária de rede.
                android.widget.Toast.makeText(this@MainActivity,
                    "Não foi possível atualizar a coleção. Confira sua conexão.",
                    android.widget.Toast.LENGTH_LONG).show()
                if (cachePronto) renderizarLista()
            }
        }
    }

    /** Pesquisa e filtros aplicados localmente, sem leituras extras no Firestore. */
    private fun renderizarLista() {
        if (!::listaJogos.isInitialized) return
        val consulta = editPesquisaJogos.text.toString().trim()
        val lista = jogosCarregados.filter { jogo ->
            val correspondeAoTexto = jogo.titulo.contains(consulta, true) ||
                jogo.plataforma.contains(consulta, true)
            val correspondeAoStatus = when (filtroStatusSelecionado) {
                FILTRO_TODOS -> true
                FILTRO_PAUSADO -> jogo.status.equals(FILTRO_PAUSADO, true)
                FILTRO_ZERADO -> jogo.status.equals(FILTRO_ZERADO, true) ||
                    jogo.status.equals(STATUS_CONCLUIDO_ANTERIOR, true)
                else -> jogo.status.equals(filtroStatusSelecionado, true)
            }
            correspondeAoTexto && correspondeAoStatus
        }
        findViewById<TextView>(R.id.resumoTotal).text = "🎮 ${jogosCarregados.size}\nJogos"
        findViewById<TextView>(R.id.resumoJogando).text =
            "▶ ${jogosCarregados.count { it.status.equals("Jogando", true) }}\nJogando"
        findViewById<TextView>(R.id.resumoZerados).text =
            "🏆 ${jogosCarregados.count { it.status.equals("Zerado", true) || it.status.equals("Concluído", true) }}\nZerados"
        val horas = jogosCarregados.mapNotNull { it.horasJogadas }.sum()
        val progressos = jogosCarregados.mapNotNull { it.progresso }
        findViewById<TextView>(R.id.resumoHoras).text =
            "⏱ ${"%.1f".format(horas)} h\nRegistradas"
        findViewById<TextView>(R.id.resumoMediaProgresso).text =
            if (progressos.isEmpty()) "—\nProgresso médio"
            else "${progressos.average().toInt()}%\nProgresso médio"
        listaJogos.removeAllViews()
        textoContagem.text = resources.getQuantityString(
            R.plurals.contagem_jogos, lista.size, lista.size)
        textoVazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        textoVazio.setText(if (jogosCarregados.isEmpty()) R.string.colecao_vazia
            else R.string.nenhum_jogo_encontrado)

        lista.forEach { jogo ->
            val card = LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.item_jogo, listaJogos, false)
            CapaJogo.aplicar(card.findViewById(R.id.textoCapaJogo), jogo.titulo)
            card.findViewById<TextView>(R.id.textoCapaJogo).apply {
                text = "🎮"
                contentDescription = "Capa ilustrativa de ${jogo.titulo}; não é a arte oficial"
            }
            card.findViewById<TextView>(R.id.textoTituloJogo).text = jogo.titulo
            card.findViewById<TextView>(R.id.textoPlataformaJogo).text = jogo.plataforma
            card.findViewById<TextView>(R.id.textoStatusJogo).text = jogo.status
            card.findViewById<TextView>(R.id.textoProgressoCard).apply {
                text = "Progresso: ${jogo.progresso ?: 0}%"
                visibility = if (jogo.progresso != null) View.VISIBLE else View.GONE
            }
            card.findViewById<android.widget.ProgressBar>(R.id.progressoCardJogo).apply {
                progress = jogo.progresso ?: 0
                visibility = if (jogo.progresso != null) View.VISIBLE else View.GONE
            }
            card.setOnClickListener {
                startActivity(Intent(this@MainActivity, DetalhesJogoActivity::class.java)
                    .putExtra(DetalhesJogoActivity.EXTRA_ID_JOGO, jogo.id))
            }
            card.findViewById<View>(R.id.menuJogo).setOnClickListener { ancora ->
                android.widget.PopupMenu(this@MainActivity, ancora).apply {
                    menu.add(0, 1, 0, "Ver detalhes")
                    menu.add(0, 2, 1, "Editar jogo")
                    menu.add(0, 3, 2, "Excluir jogo")
                    setOnMenuItemClickListener { opcao ->
                        when (opcao.itemId) {
                            1 -> startActivity(Intent(this@MainActivity, DetalhesJogoActivity::class.java)
                                .putExtra(DetalhesJogoActivity.EXTRA_ID_JOGO, jogo.id))
                            2 -> startActivity(Intent(this@MainActivity, AdicionarJogoActivity::class.java)
                                .putExtra(AdicionarJogoActivity.EXTRA_ID_JOGO, jogo.id))
                            3 -> confirmarExclusao(jogo)
                        }
                        true
                    }
                    show()
                }
            }
            listaJogos.addView(card)
        }
    }

    private fun configurarFiltros() {
        val opcoes = listOf("Todos", "Jogando", "Pausado", "Zerado", "Quero jogar")
        val valores = listOf(FILTRO_TODOS, FILTRO_JOGANDO, FILTRO_PAUSADO,
            FILTRO_ZERADO, FILTRO_QUERO_JOGAR)
        val botao = findViewById<View>(R.id.botaoFiltroUnico)
        botao.setOnClickListener {
            botao.animate().scaleX(.96f).scaleY(.96f).setDuration(80).withEndAction {
                botao.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }.start()
            DialogosGameTracker.filtroBiblioteca(this, opcoes,
                valores.indexOf(filtroStatusSelecionado).coerceAtLeast(0)) { indice ->
                filtroStatusSelecionado = valores[indice]
                botao.contentDescription = "Filtrar jogos. Exibindo ${opcoes[indice]}"
                renderizarLista()
            }
        }
    }

    private fun confirmarExclusao(jogo: Jogo) {
        DialogosGameTracker.confirmar(this, getString(R.string.confirmar_exclusao_titulo),
            getString(R.string.confirmar_exclusao_mensagem, jogo.titulo),
            getString(R.string.excluir), sim = {
                lifecycleScope.launch {
                    try {
                        usuarioId?.let { jogos.excluir(jogo.id, it) }
                        atualizarLista()
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(this@MainActivity,
                            "Falha ao excluir jogo.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            })
    }

    private fun oferecerImportacaoLegada(uid: String) {
        lifecycleScope.launch {
            try {
                if (jogos.legadosJaConfirmados(uid)) return@launch
                val quantidade = jogos.contarJogosSemConta()
                if (quantidade == 0) return@launch
                DialogosGameTracker.confirmar(this@MainActivity, "Jogos antigos encontrados",
                    "Foram encontrados $quantidade jogos locais sem conta Firebase vinculada. " +
                        "Importe apenas se forem seus. Importar para esta conta?",
                    "Importar", "Agora não", sim = {
                        lifecycleScope.launch {
                            try {
                                val total = jogos.importarJogosSemContaComConfirmacao(uid)
                                android.widget.Toast.makeText(this@MainActivity,
                                    "$total jogos antigos importados.", android.widget.Toast.LENGTH_LONG).show()
                                atualizarLista()
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(this@MainActivity,
                                    "Falha na importação. Seus dados locais foram preservados.",
                                    android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    })
            } catch (_: Exception) { /* a biblioteca continua funcional */ }
        }
    }

    private fun voltarAoLogin() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private var baseX: Float? = null
    private var baseY: Float? = null

    private fun resetarMovimentoCards() {
        if (!::listaJogos.isInitialized) return
        for (i in 0 until listaJogos.childCount) {
            listaJogos.getChildAt(i).apply {
                rotationX = 0f
                rotationY = 0f
                translationX = 0f
                translationY = 0f
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER ||
            !::listaJogos.isInitialized) return

        if (!getSharedPreferences("gt_config", MODE_PRIVATE)
                .getBoolean("tilt_cards", true)) {
            resetarMovimentoCards()
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        // O primeiro evento serve de referencia: o card comeca reto.
        if (baseX == null || baseY == null) {
            baseX = x
            baseY = y
            return
        }

        val agora = android.os.SystemClock.elapsedRealtime()
        if (agora - ultimaAtualizacaoSensor < 80L) return
        ultimaAtualizacaoSensor = agora

        val deltaX = (x - baseX!!).coerceIn(-5f, 5f)
        val deltaY = (y - baseY!!).coerceIn(-5f, 5f)
        suavizadoX = suavizadoX * 0.85f + deltaX * 0.15f
        suavizadoY = suavizadoY * 0.85f + deltaY * 0.15f

        // Movimento sutil em pixels, sem perspectiva 3D que deitava o card.
        val limite = 5f * resources.displayMetrics.density
        val deslocamentoX = (-suavizadoX * 1.5f * resources.displayMetrics.density)
            .coerceIn(-limite, limite)
        val deslocamentoY = (suavizadoY * 1.5f * resources.displayMetrics.density)
            .coerceIn(-limite, limite)

        for (i in 0 until listaJogos.childCount) {
            val card = listaJogos.getChildAt(i)
            val visivel = android.graphics.Rect()
            if (card.getGlobalVisibleRect(visivel)) {
                card.rotationX = 0f
                card.rotationY = 0f
                card.translationX = deslocamentoX
                card.translationY = deslocamentoY
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val FILTRO_TODOS = "Todos"
        const val FILTRO_JOGANDO = "Jogando"
        const val FILTRO_PAUSADO = "Pausado"
        const val FILTRO_ZERADO = "Zerado"
        const val FILTRO_QUERO_JOGAR = "Quero jogar"
        const val STATUS_CONCLUIDO_ANTERIOR = "Concluído"
    }
}
