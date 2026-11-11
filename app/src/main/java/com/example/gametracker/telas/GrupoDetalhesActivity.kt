package com.example.gametracker.telas

import android.content.Intent
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import com.example.gametracker.utils.AvatarStorage
import com.example.gametracker.utils.GrupoImagemStorage
import android.graphics.Typeface
import android.view.Gravity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.gametracker.utils.DialogosGameTracker
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gametracker.R
import com.example.gametracker.utils.VisualGameTracker
import com.example.gametracker.dados.SocialRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Informações e comunidades: painel leve, sem biblioteca gráfica adicional. */
class GrupoDetalhesActivity : AppCompatActivity() {
    private val social = SocialRepository()
    private lateinit var grupoId: String
    private lateinit var painel: LinearLayout
    private var atual: SocialRepository.DetalhesGrupo? = null
    private val acoes = SocialRepository.ACOES
    private val seletorImagem = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) executar {
            val imagem = GrupoImagemStorage.prepararMiniatura(this@GrupoDetalhesActivity, uri)
            social.atualizarImagem(grupoId, imagem)
            toast("Imagem do grupo atualizada!")
            carregar()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        grupoId = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        val rolagem = ScrollView(this).apply { setBackgroundColor(getColor(R.color.fundo)) }
        painel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        rolagem.addView(painel)
        VisualGameTracker.envolver(this, rolagem)
    }
    override fun onResume() { super.onResume(); if (::painel.isInitialized) carregar() }

    private fun carregar() {
        lifecycleScope.launch {
            try {
                val g = social.detalhesGrupo(grupoId)
                if (social.uid !in g.membros) { finish(); return@launch }
                atual = g
                desenhar(g)
            } catch (e: Exception) { if (!isFinishing) toast(e.localizedMessage ?: "Não foi possível abrir o grupo") }
        }
    }

    private fun texto(conteudo: String, tamanho: Float = 15f, secundario: Boolean = false): TextView =
        TextView(this).apply {
            text = conteudo; textSize = tamanho
            setTextColor(getColor(if (secundario) R.color.texto_secundario else R.color.texto))
            setPadding(dp(3), dp(7), dp(3), dp(7))
        }

    private fun secao(titulo: String, conteudo: String? = null): LinearLayout {
        painel.addView(texto(titulo, 20f).apply { setTypeface(null, Typeface.BOLD) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_jogo)
            setPadding(dp(15), dp(12), dp(15), dp(12))
        }
        painel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        if (conteudo != null) card.addView(texto(conteudo, 14f, true))
        return card
    }

    private fun linhaInfo(pai: LinearLayout, rotulo: String, valor: String): TextView {
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(9), dp(2), dp(9))
        }
        linha.addView(texto(rotulo, 13f, true), LinearLayout.LayoutParams(0, -2, 1f))
        val conteudo = texto(valor, 14f).apply {
            gravity = Gravity.END
            setTypeface(null, Typeface.BOLD)
        }
        linha.addView(conteudo, LinearLayout.LayoutParams(0, -2, 1f))
        pai.addView(linha)
        return conteudo
    }

    private fun botao(rotulo: String, pai: LinearLayout = painel, secundario: Boolean = false,
                      clique: () -> Unit): Button {
        val b = Button(this).apply {
            text = rotulo; isAllCaps = false; textSize = 14f
            setTextColor(getColor(R.color.black))
            backgroundTintList = null
            setBackgroundResource(if (secundario) R.drawable.bg_botao_secundario else R.drawable.bg_botao_principal)
            setOnClickListener {
                animate().scaleX(.97f).scaleY(.97f).setDuration(80).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
                clique()
            }
        }
        pai.addView(b, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        return b
    }

    private fun desenhar(g: SocialRepository.DetalhesGrupo) {
        painel.removeAllViews()
        val voltar = ImageView(this).apply {
            setImageResource(R.drawable.ic_gt_back)
            setColorFilter(getColor(R.color.texto))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = getString(R.string.voltar)
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        painel.addView(voltar, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            bottomMargin = dp(8)
        })
        val cabecalho = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val icone = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_gallery)
            setBackgroundResource(R.drawable.bg_card_jogo)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        cabecalho.addView(icone, LinearLayout.LayoutParams(dp(74), dp(74)).apply {
            rightMargin = dp(12)
        })
        if (g.criadorId == social.uid) {
            icone.contentDescription = "Alterar foto do grupo"
            icone.setOnClickListener { seletorImagem.launch("image/*") }
        }
        if (g.imagem.startsWith("storage:") || g.imagem.startsWith("inline:")) lifecycleScope.launch {
            AvatarStorage.mostrar(this@GrupoDetalhesActivity, icone, g.imagem)
        }
        cabecalho.addView(texto("🎮 ${g.nome}", 24f).apply {
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        painel.addView(cabecalho)
        painel.addView(texto(g.descricao.ifBlank { "Comunidade de jogadores" }, 14f, true))
        if (g.criadorId == social.uid)
            painel.addView(texto("Toque na foto para trocar a imagem do grupo.", 12f, true))
        val info = secao("Informações do grupo")
        linhaInfo(info, "🎯 Jogo principal", g.jogo.ifBlank { "Diversos" })
        linhaInfo(info, "🌐 Visibilidade", if (g.publico) "Público" else "Privado")
        linhaInfo(info, "👥 Participantes", "${g.membros.size}")
        val campoCriador = linhaInfo(info, "👑 Criador", if (g.criadorId == social.uid) "Você" else "Carregando...")
        if (g.criadorId != social.uid) lifecycleScope.launch {
            try { campoCriador.text = social.perfilPublico(g.criadorId)?.nome ?: "Jogador" }
            catch (_: Exception) { campoCriador.text = "Jogador" }
        }
        linhaInfo(info, "🛡 Seu cargo", social.cargoDoUsuario(g).replaceFirstChar { it.uppercase() })
        botao("⚙ Configurações do grupo", info, true) { configuracoes() }

        val canais = secao("# Canais de texto", "O canal geral e os outros chats têm históricos separados.")
        botao("# geral", canais) { abrirChat(null, "geral") }
        lifecycleScope.launch {
            try {
                social.listarCanais(grupoId).forEach { c ->
                    val acesso = c.cargosPermitidos.isEmpty() || social.cargoDoUsuario(g) == "dono" ||
                        social.cargoDoUsuario(g) in c.cargosPermitidos
                    if (acesso) {
                        val b = botao("# ${c.nome}", canais) { abrirChat(c.id, c.nome) }
                        if (social.pode(g, "gerenciarCanais")) b.setOnLongClickListener {
                            gerenciarCanal(c); true
                        }
                    }
                }
            } catch (_: Exception) { canais.addView(texto("Não foi possível carregar os canais.", 14f, true)) }
        }
        if (social.pode(g, "criarCanais")) botao("+ Criar canal", canais) { criarCanal() }
        if (social.pode(g, "gerenciarCanais")) canais.addView(texto("Toque e segure em um canal para gerenciá-lo.", 12f, true))

        val membros = secao("Participantes")
        g.membros.forEach { id ->
            val linha = texto("● ${if (id == social.uid) "Você" else "Jogador"}  ·  ${social.cargoDoUsuario(g, id)}")
            membros.addView(linha)
            lifecycleScope.launch {
                try {
                    val jogador = social.perfilPublico(id)
                    if (jogador != null) linha.text = "● ${jogador.nome}  ·  ${social.cargoDoUsuario(g, id)}"
                } catch (_: Exception) { }
            }
            if (id != social.uid) {
                linha.setOnClickListener {
                    startActivity(Intent(this, PerfilPublicoActivity::class.java)
                        .putExtra(PerfilPublicoActivity.EXTRA_UID, id))
                }
                if (g.criadorId == social.uid || social.pode(g, "removerMembros"))
                    linha.setOnLongClickListener { opcoesMembro(g, id); true }
            }
        }
        if (social.pode(g, "convidar")) botao("+ Convidar amigo", membros, true) { convidar() }
        if (g.criadorId != social.uid) botao("Sair do grupo", membros, true) {
            confirmar("Sair do grupo?", "Você deixará de participar dos canais.") {
                executar { social.sairGrupo(grupoId); finish() }
            }
        }

        val partidas = secao("Próximas partidas")
        if (social.pode(g, "organizarPartidas")) botao("+ Organizar partida", partidas) { criarPartida() }
        carregarPartidas(partidas)
    }

    private fun configuracoes() {
        val g = atual ?: return
        val opcoes = mutableListOf("${if (g.publico) "🌐 Público" else "🔒 Privado"} (informação)", "🔕 Silenciar / reativar")
        if (g.criadorId == social.uid) {
            opcoes.addAll(listOf("🖼 Alterar imagem do grupo", "✏ Editar informações", "🌐 Alterar público / privado",
                "🛡 Gerenciar cargos", "⚙ Permissões dos cargos", "🗑 Excluir grupo"))
        }
        DialogosGameTracker.menu(this, "Configurações do grupo", opcoes) { pos ->
            when (opcoes[pos]) {
                "🔕 Silenciar / reativar" -> alternarSilencio()
                "🖼 Alterar imagem do grupo" -> seletorImagem.launch("image/*")
                "✏ Editar informações" -> editarInformacoes(g)
                "🌐 Alterar público / privado" -> alterarPrivacidade(g)
                "🛡 Gerenciar cargos" -> escolherMembroCargo(g)
                "⚙ Permissões dos cargos" -> configurarPermissoes()
                "🗑 Excluir grupo" -> confirmar("Excluir definitivamente?",
                    "O grupo, seus canais, mensagens e partidas serão apagados. Esta ação não pode ser desfeita.") {
                    executar { social.excluirGrupo(grupoId); finish() }
                }
            }
        }
    }


    private fun alternarSilencio() = executar {
        val silenciado = social.grupoSilenciado(grupoId)
        DialogosGameTracker.confirmar(this@GrupoDetalhesActivity, "Notificações",
            if (silenciado) "O grupo está silenciado. Deseja reativar os avisos?" else "Deseja silenciar este grupo?",
            if (silenciado) "Reativar" else "Silenciar", sim = {
                executar { social.silenciarGrupo(grupoId, !silenciado); toast("Preferência salva") }
            })
    }


    private fun editarInformacoes(g: SocialRepository.DetalhesGrupo) {
        DialogosGameTracker.formulario(this, "Editar informações", listOf(
            DialogosGameTracker.Campo("Nome", g.nome),
            DialogosGameTracker.Campo("Descrição", g.descricao),
            DialogosGameTracker.Campo("Jogo principal", g.jogo)
        )) { campos -> executar {
            social.atualizarInformacoes(grupoId, campos[0], campos[1], campos[2]); carregar()
        } }
    }


    private fun alterarPrivacidade(g: SocialRepository.DetalhesGrupo) {
        DialogosGameTracker.unica(this, "Visibilidade do grupo",
            listOf("Público — aparece nas pesquisas", "Privado — apenas convidados"),
            if (g.publico) 0 else 1) { indice ->
            executar { social.alterarPrivacidade(grupoId, indice == 0); carregar() }
        }
    }


    private fun escolherMembroCargo(g: SocialRepository.DetalhesGrupo) {
        val outros = g.membros.filter { it != g.criadorId }
        if (outros.isEmpty()) { toast("Ainda não há outros participantes."); return }
        executar {
            val nomes = outros.map { id ->
                (social.perfilPublico(id)?.nome ?: id.take(10)) + " · " + social.cargoDoUsuario(g, id)
            }
            DialogosGameTracker.menu(this@GrupoDetalhesActivity, "Escolha o participante", nomes) { i ->
                escolherCargo(outros[i])
            }
        }
    }


    private fun escolherCargo(membro: String) {
        val cargos = SocialRepository.CARGOS
        DialogosGameTracker.menu(this, "Atribuir cargo", cargos.map { it.replaceFirstChar(Char::uppercase) }) { i ->
            executar { social.atribuirCargo(grupoId, membro, cargos[i]); carregar() }
        }
    }


    private fun configurarPermissoes() {
        val nomes = acoes.values.toList()
        DialogosGameTracker.menu(this, "Permissões dos cargos", nomes) { indice ->
            val chave = acoes.keys.elementAt(indice)
            executar {
                val g = social.detalhesGrupo(grupoId)
                val permitidos = (g.permissoes[chave] ?: SocialRepository.PERMISSOES_INICIAIS[chave]).orEmpty()
                val marcados = SocialRepository.CARGOS.map { it in permitidos }.toBooleanArray()
                DialogosGameTracker.multipla(this@GrupoDetalhesActivity, nomes[indice],
                    listOf("Administrador", "Moderador", "Membro"), marcados,
                    "O dono sempre tem permissão. Selecione os outros cargos autorizados.") { escolhas ->
                    executar {
                        SocialRepository.CARGOS.forEachIndexed { pos, cargo ->
                            social.mudarPermissao(grupoId, chave, cargo, escolhas[pos])
                        }
                        carregar()
                    }
                }
            }
        }
    }


    private fun opcoesMembro(g: SocialRepository.DetalhesGrupo, membro: String) {
        val opcoes = mutableListOf<String>()
        if (g.criadorId == social.uid) opcoes.add("Alterar cargo")
        if (social.pode(g, "removerMembros")) opcoes.add("Remover participante")
        DialogosGameTracker.menu(this, "Gerenciar participante", opcoes) { i ->
            when (opcoes[i]) {
                "Alterar cargo" -> escolherCargo(membro)
                "Remover participante" -> confirmar("Remover participante?", "Ele perderá acesso aos canais.") {
                    executar { social.removerMembro(grupoId, membro); carregar() }
                }
            }
        }
    }


    private fun criarCanal() {
        DialogosGameTracker.formulario(this, "Criar canal de texto", listOf(
            DialogosGameTracker.Campo("Nome do canal (ex: procurando-time)"),
            DialogosGameTracker.Campo("Descrição (opcional)")
        ), acao = "Avançar", subtitulo = "Defina o nome e depois escolha quem poderá acessar.") { valores ->
            DialogosGameTracker.multipla(this, "Acesso ao novo canal",
                listOf("Administrador", "Moderador", "Membro"), BooleanArray(3),
                "Sem cargos selecionados, o canal será público para os membros do grupo.") { marcados ->
                confirmarCriarCanal(valores[0], valores[1],
                    SocialRepository.CARGOS.filterIndexed { i, _ -> marcados[i] })
            }
        }
    }


    private fun confirmarCriarCanal(nome: String, desc: String, cargos: List<String>) = executar {
        social.criarCanal(grupoId, nome, desc, cargos); carregar()
    }

    private fun gerenciarCanal(c: SocialRepository.Canal) {
        DialogosGameTracker.menu(this, "Canal #${c.nome}",
            listOf("Renomear", "Definir quem pode acessar", "Excluir canal")) { escolha ->
            when (escolha) {
                0 -> DialogosGameTracker.formulario(this, "Renomear canal",
                    listOf(DialogosGameTracker.Campo("Novo nome", c.nome))) { valores ->
                    executar { social.renomearCanal(grupoId, c.id, valores[0]); carregar() }
                }
                1 -> {
                    val marcados = SocialRepository.CARGOS.map { it in c.cargosPermitidos }.toBooleanArray()
                    DialogosGameTracker.multipla(this, "Acesso ao canal",
                        listOf("Administrador", "Moderador", "Membro"), marcados,
                        "Sem cargos selecionados, todos os membros poderão acessar.") { escolhas ->
                        executar {
                            social.configurarAcessoCanal(grupoId, c.id,
                                SocialRepository.CARGOS.filterIndexed { i, _ -> escolhas[i] })
                            carregar()
                        }
                    }
                }
                else -> confirmar("Excluir canal?", "As mensagens deste canal serão apagadas.") {
                    executar { social.excluirCanal(grupoId, c.id); carregar() }
                }
            }
        }
    }


    private fun abrirChat(canalId: String?, nomeCanal: String) {
        startActivity(Intent(this, ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_GRUPO_ID, grupoId)
            .putExtra(ChatActivity.EXTRA_CANAL_ID, canalId)
            .putExtra(ChatActivity.EXTRA_NOME, "# $nomeCanal"))
    }

    private fun criarPartida() {
        DialogosGameTracker.formulario(this, "Organizar partida", listOf(
            DialogosGameTracker.Campo("Jogo"),
            DialogosGameTracker.Campo("Data e horário"),
            DialogosGameTracker.Campo("Total de participantes", numerico = true)
        ), acao = "Criar") { valores ->
            executar {
                social.organizarPartida(grupoId, valores[0], valores[1], valores[2].toIntOrNull() ?: 0)
                carregar()
            }
        }
    }


    private fun carregarPartidas(pai: LinearLayout) = executar {
        val partidas = social.listarPartidas(grupoId)
        if (partidas.isEmpty()) pai.addView(texto("Nenhuma partida agendada.", 14f, true))
        partidas.forEach { p ->
            val confirmados = (p["confirmados"] as? List<*>)?.size ?: 0
            val vagas = (p["vagas"] as? Number)?.toInt() ?: 0
            val card = texto("🎮 ${p["jogo"]}\n🕒 ${p["horario"]}\n$confirmados/$vagas participantes\nToque para entrar")
            pai.addView(card)
            card.setOnClickListener { executar {
                social.confirmarPartida(grupoId, p["id"].toString()); carregar()
            } }
        }
    }

    private fun convidar() = executar {
        val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("amizades")
            .whereArrayContains("participantes", social.uid).get().await()
        val amigos = snap.documents.mapNotNull { doc ->
            val outro = (doc.get("participantes") as? List<*>)?.filterIsInstance<String>()
                ?.firstOrNull { it != social.uid } ?: return@mapNotNull null
            (social.perfilPublico(outro)?.nome ?: "Jogador") to outro
        }
        if (amigos.isEmpty()) { toast("Você ainda não tem amigos para convidar."); return@executar }
        DialogosGameTracker.menu(this@GrupoDetalhesActivity, "Convidar amigo", amigos.map { it.first }) { i ->
            executar { social.convidar(grupoId, amigos[i].second); toast("Convite enviado!") }
        }
    }

    private fun confirmar(titulo: String, mensagem: String, acao: () -> Unit) {
        DialogosGameTracker.confirmar(this, titulo, mensagem, sim = acao)
    }


    private fun executar(acao: suspend () -> Unit) {
        lifecycleScope.launch {
            try { acao() } catch (e: Exception) {
                toast(e.localizedMessage ?: "Não foi possível concluir a operação.")
            }
        }
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    companion object {
        const val EXTRA_ID = "grupo_id"
        const val EXTRA_NOME = "grupo_nome"
        const val EXTRA_CRIADOR = "grupo_criador"
    }
}
