package com.example.gametracker.utils

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.gametracker.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.view.View

/** Dialogos personalizados do GameTracker. Nada de janelas cinzas do tema padrão. */
object DialogosGameTracker {
    data class Campo(val titulo: String, val inicial: String = "", val numerico: Boolean = false)

    private fun px(a: AppCompatActivity, valor: Int) = (valor * a.resources.displayMetrics.density).toInt()

    private fun fundo(a: AppCompatActivity, cor: Int, borda: Int? = null, raio: Int = 16): GradientDrawable =
        GradientDrawable().apply {
            setColor(a.getColor(cor))
            cornerRadius = px(a, raio).toFloat()
            if (borda != null) setStroke(px(a, 1), a.getColor(borda))
        }

    private fun texto(a: AppCompatActivity, valor: String, tamanho: Float = 15f, titulo: Boolean = false): TextView =
        TextView(a).apply {
            text = valor
            textSize = tamanho
            setTextColor(a.getColor(if (titulo) R.color.texto else R.color.texto_secundario))
            if (titulo) setTypeface(null, Typeface.BOLD)
        }

    private fun botao(a: AppCompatActivity, label: String, secundario: Boolean = false, clicar: () -> Unit): Button =
        Button(a).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(a.getColor(R.color.black))
            backgroundTintList = null
            background = fundo(a, if (secundario) R.color.botao_secundario_fundo else R.color.botao_lilas,
                if (secundario) R.color.destaque else null)
            setOnClickListener {
                animate().scaleX(.98f).scaleY(.98f).setDuration(80).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                }.start()
                clicar()
            }
        }

    /** Cria uma janela com fundo escuro, opções em cards e botões com texto preto. */
    fun exibir(
        a: AppCompatActivity,
        titulo: String,
        subtitulo: String? = null,
        acao: String? = null,
        executar: (() -> Unit)? = null,
        alternativa: String = "Cancelar",
        executarAlternativa: (() -> Unit)? = null,
        construir: (LinearLayout) -> Unit = {}
    ): AlertDialog {
        val raiz = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(a, 19), px(a, 22), px(a, 19), px(a, 18))
            background = fundo(a, R.color.fundo_cartao, R.color.borda, 22)
        }
        raiz.addView(texto(a, titulo, 21f, true), LinearLayout.LayoutParams(-1, -2))
        if (!subtitulo.isNullOrBlank()) {
            raiz.addView(texto(a, subtitulo, 13f), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = px(a, 7)
                bottomMargin = px(a, 8)
            })
        }
        val rolagem = ScrollView(a).apply { isFillViewport = false; isVerticalScrollBarEnabled = false }
        val corpo = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(a, 12), 0, px(a, 8))
        }
        construir(corpo)
        rolagem.addView(corpo)
        raiz.addView(rolagem, LinearLayout.LayoutParams(-1, -2))

        val rodape = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dialogo = AlertDialog.Builder(a).setView(raiz).create()
        rodape.addView(botao(a, alternativa, true) {
            dialogo.dismiss()
            executarAlternativa?.invoke()
        }, LinearLayout.LayoutParams(0, px(a, 48), 1f).apply { rightMargin = px(a, 7) })
        if (acao != null) rodape.addView(botao(a, acao) {
            dialogo.dismiss()
            executar?.invoke()
        }, LinearLayout.LayoutParams(0, px(a, 48), 1f).apply { leftMargin = px(a, 7) })
        raiz.addView(rodape, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(a, 12) })
        dialogo.setOnShowListener {
            dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val largura = (a.resources.displayMetrics.widthPixels * .91f).toInt()
            dialogo.window?.setLayout(largura, WindowManager.LayoutParams.WRAP_CONTENT)
            // Formulários longos rolam no lugar de ultrapassar a altura do aparelho.
            rolagem.post {
                val limite = (a.resources.displayMetrics.heightPixels * .55f).toInt()
                if (rolagem.height > limite) {
                    rolagem.layoutParams = rolagem.layoutParams.apply { height = limite }
                }
            }
        }
        dialogo.show()
        return dialogo
    }

    /** Menu de ações com opções separadas, todas legíveis sobre superfícies lilás. */
    fun menu(a: AppCompatActivity, titulo: String, opcoes: List<String>, selecionar: (Int) -> Unit) {
        var janela: AlertDialog? = null
        janela = exibir(a, titulo) { corpo ->
            opcoes.forEachIndexed { i, nome ->
                val linha = botao(a, nome, secundario = true) {
                    janela?.dismiss()
                    selecionar(i)
                }
                corpo.addView(linha, LinearLayout.LayoutParams(-1, px(a, 48)).apply { bottomMargin = px(a, 9) })
            }
        }
    }

    fun confirmar(a: AppCompatActivity, titulo: String, mensagem: String,
                 positivo: String = "Confirmar", negativo: String = "Cancelar",
                 sim: () -> Unit, nao: (() -> Unit)? = null) {
        exibir(a, titulo, mensagem, positivo, sim, negativo, nao)
    }

    /** Campos escuros com rótulos bem separados e teclado numérico quando pedido. */
    fun formulario(a: AppCompatActivity, titulo: String, campos: List<Campo>, acao: String = "Salvar",
                   subtitulo: String? = null, salvar: (List<String>) -> Unit) {
        val entradas = mutableListOf<EditText>()
        exibir(a, titulo, subtitulo, acao, { salvar(entradas.map { it.text.toString().trim() }) }) { corpo ->
            campos.forEach { c ->
                corpo.addView(texto(a, c.titulo, 13f, true), LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = px(a, 6)
                    bottomMargin = px(a, 6)
                })
                val entrada = EditText(a).apply {
                    hint = c.titulo
                    setText(c.inicial)
                    setTextColor(a.getColor(R.color.texto))
                    setHintTextColor(a.getColor(R.color.texto_secundario))
                    setSingleLine(true)
                    textSize = 15f
                    setPadding(px(a, 14), 0, px(a, 14), 0)
                    background = fundo(a, R.color.superficie, R.color.borda, 12)
                    if (c.numerico) inputType = InputType.TYPE_CLASS_NUMBER
                }
                corpo.addView(entrada, LinearLayout.LayoutParams(-1, px(a, 52)).apply { bottomMargin = px(a, 10) })
                entradas.add(entrada)
            }
        }
    }

    fun multipla(a: AppCompatActivity, titulo: String, opcoes: List<String>, iniciais: BooleanArray,
                subtitulo: String? = null, salvar: (BooleanArray) -> Unit) {
        val estado = BooleanArray(opcoes.size) { i -> iniciais.getOrElse(i) { false } }
        exibir(a, titulo, subtitulo, "Salvar", { salvar(estado.copyOf()) }) { corpo ->
            opcoes.forEachIndexed { i, nome ->
                val checkbox = CheckBox(a).apply {
                    text = nome
                    textSize = 15f
                    setTextColor(a.getColor(R.color.texto))
                    isChecked = estado[i]
                    buttonTintList = ColorStateList.valueOf(a.getColor(R.color.botao_lilas))
                    setPadding(px(a, 12), px(a, 8), px(a, 12), px(a, 8))
                    background = fundo(a, R.color.superficie, R.color.borda, 12)
                    setOnCheckedChangeListener { _, checked -> estado[i] = checked }
                }
                corpo.addView(checkbox, LinearLayout.LayoutParams(-1, px(a, 54)).apply {
                    bottomMargin = px(a, 9)
                })
            }
        }
    }

    /**
     * Filtro da Biblioteca em painel inferior, mantendo a lista de jogos na mesma tela.
     * Todas as opções são roxas com texto preto e a seleção é visualmente destacada.
     */
    fun filtroBiblioteca(a: AppCompatActivity, opcoes: List<String>, atual: Int,
                        escolher: (Int) -> Unit) {
        val painel = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(a, 20), px(a, 12), px(a, 20), px(a, 24))
            background = fundo(a, R.color.fundo_cartao, R.color.borda, 24)
        }
        // Indicador discreto de que a folha pode ser fechada deslizando para baixo.
        val indicador = View(a).apply {
            background = fundo(a, R.color.destaque, raio = 8)
        }
        painel.addView(indicador, LinearLayout.LayoutParams(px(a, 40), px(a, 4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = px(a, 16)
        })
        painel.addView(texto(a, "Filtrar jogos", 21f, true), LinearLayout.LayoutParams(-1, -2))
        painel.addView(texto(a, "Escolha quais jogos deseja ver na Biblioteca", 13f),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(a, 6); bottomMargin = px(a, 16) })

        val dialogo = BottomSheetDialog(a)
        opcoes.forEachIndexed { indice, nome ->
            val marcado = indice == atual
            val opcao = botao(
                a,
                (if (marcado) "✓  " else "   ") + nome,
                clicar = {
                    dialogo.dismiss()
                    escolher(indice)
                }
            ).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(px(a, 18), 0, px(a, 12), 0)
                if (!marcado) background = fundo(a, R.color.botao_secundario_fundo,
                    R.color.destaque, 13)
                contentDescription = "Filtrar: $nome" + if (marcado) ", selecionado" else ""
                setOnClickListener {
                    dialogo.dismiss()
                    escolher(indice)
                }
            }
            painel.addView(opcao, LinearLayout.LayoutParams(-1, px(a, 49)).apply {
                bottomMargin = px(a, 9)
            })
        }
        painel.addView(botao(a, "Cancelar", secundario = true) { dialogo.dismiss() },
            LinearLayout.LayoutParams(-1, px(a, 49)).apply { topMargin = px(a, 7) })
        dialogo.setContentView(painel)
        dialogo.setOnShowListener {
            dialogo.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        dialogo.show()
    }

    fun unica(a: AppCompatActivity, titulo: String, opcoes: List<String>, atual: Int,
              escolher: (Int) -> Unit) {
        menu(a, titulo, opcoes.mapIndexed { i, nome -> if (i == atual) "✓ $nome" else nome }, escolher)
    }
}
