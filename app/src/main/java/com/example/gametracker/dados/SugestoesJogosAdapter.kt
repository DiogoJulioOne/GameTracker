package com.example.gametracker.dados

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import java.text.Normalizer
import java.util.Locale

/** Sugestões locais. A busca funciona pelo nome inteiro ou por qualquer parte dele. */
class SugestoesJogosAdapter(context: Context) :
    ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line) {

    private var exibidos: List<String> = emptyList()

    override fun getCount(): Int = exibidos.size
    override fun getItem(position: Int): String? = exibidos.getOrNull(position)

    override fun getFilter(): Filter = filtro

    private val filtro = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val termo = normalizar(constraint?.toString().orEmpty())
            val candidatos = if (termo.isBlank()) emptyList() else BibliotecaSugestoes.jogos
                .asSequence()
                .filter { normalizar(it).contains(termo) }
                .sortedWith(compareBy<String> { if (normalizar(it).startsWith(termo)) 0 else 1 }
                    .thenBy { it.length })
                .take(12)
                .toList()
            return FilterResults().apply { values = candidatos; count = candidatos.size }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            @Suppress("UNCHECKED_CAST")
            exibidos = (results.values as? List<String>).orEmpty()
            notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence =
            resultValue?.toString().orEmpty()
    }

    companion object {
        fun normalizar(valor: String): String = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }
}
