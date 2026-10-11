package co.adilson889.typec.semantica

import co.adilson889.typec.ast.Tipo
import co.adilson889.typec.ast.TipoDado

/**
 * Tipos de retorno para funcoes C cuja representacao difere dos tipos
 * cotidianos da linguagem. Apenas tipo de retorno: nao implica ownership.
 *
 * Projeto original: Adilson C. Rafael.
 * Contribuicao experimental: Artur Mendes Vieira Alves (@tuhmds).
 * GPL-3.0-or-later.
 */
object TiposNativosC {
    val retornos: Map<String, Tipo> = mapOf(
        "tamanho" to Tipo(TipoDado.TAMANHO), // strlen -> size_t
        "aloque" to Tipo(TipoDado.PONTEIRO_OPACO), // malloc -> void*
        "aloque_zerado" to Tipo(TipoDado.PONTEIRO_OPACO), // calloc -> void*
        "realoque" to Tipo(TipoDado.PONTEIRO_OPACO), // realloc -> void*
        "busque" to Tipo(TipoDado.PONTEIRO_OPACO), // bsearch -> void*
        "libere" to Tipo(TipoDado.VAZIO) // free -> void
    )

    fun retorno(nome: String): Tipo? = retornos[nome]
}
