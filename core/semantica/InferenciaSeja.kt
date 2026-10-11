package co.adilson889.typec.semantica

import co.adilson889.typec.ast.*

/**
 * Inferencia local conservadora para declaracoes "seja".
 * Nao cria um tipo dinamico: devolve um Tipo concreto, ou null quando
 * nao ha prova suficiente para compilar uma declaracao C tipada.
 *
 * Projeto original: Adilson C. Rafael.
 * Evolucao proposta por Artur Mendes Vieira Alves (@tuhmds),
 * com assistencia de IA. GPL-3.0-or-later.
 */
object InferenciaSeja {
    private fun escalar(t: Tipo): Boolean =
        !t.ehArray && !t.ehArray2D && t.nomeStruct == null

    fun inferir(
        expr: No,
        variavel: (String) -> Tipo?,
        retornoFuncao: (String) -> Tipo?
    ): Tipo? = when (expr) {
        is Numero -> Tipo(expr.tipo)
        is Texto -> Tipo(TipoDado.TEXTO)
        is Caractere -> Tipo(TipoDado.CARACTERE)
        is Logico -> Tipo(TipoDado.LOGICO)
        is Identificador -> variavel(expr.nome)
            ?: if (expr.nome in setOf("PI", "E")) Tipo(TipoDado.DUPLO) else null
        is ChamadaFuncao -> retornoFuncao(expr.nome)
            ?: TiposNativosC.retorno(expr.nome)
        is AcessoIndice -> {
            val t = inferir(expr.array, variavel, retornoFuncao)
            if (t == null) null
            else when {
                t.base == TipoDado.TEXTO && escalar(t) -> Tipo(TipoDado.CARACTERE)
                t.ehArray2D -> t.copy(ehArray2D = false, tamanhoFixo = t.tamanhoFixo2, tamanhoFixo2 = null)
                t.ehArray -> t.copy(ehArray = false, tamanhoFixo = null)
                else -> null
            }
        }
        is OperacaoUnaria -> {
            val t = inferir(expr.operando, variavel, retornoFuncao)
            if (t == null) null
            else when (expr.operador) {
                "!" -> if (escalar(t) && t.base == TipoDado.LOGICO)
                    Tipo(TipoDado.LOGICO) else null
                "-" -> TiposNumericos.resultadoUnario("-", t)
                else -> null
            }
        }
        is OperacaoBinaria -> {
            val a = inferir(expr.esquerda, variavel, retornoFuncao)
            val b = inferir(expr.direita, variavel, retornoFuncao)
            if (a == null || b == null) null
            else when (expr.operador) {
                "&&", "||" -> if (escalar(a) && escalar(b) &&
                    a.base == TipoDado.LOGICO && b.base == TipoDado.LOGICO)
                    Tipo(TipoDado.LOGICO) else null
                "==", "!=", "<", ">", "<=", ">=" -> {
                    val numericos = TiposNumericos.ehNumerico(a) && TiposNumericos.ehNumerico(b)
                    val caracteres = escalar(a) && escalar(b) &&
                        a.base == TipoDado.CARACTERE && b.base == TipoDado.CARACTERE
                    val booleanos = expr.operador in setOf("==", "!=") &&
                        escalar(a) && escalar(b) &&
                        a.base == TipoDado.LOGICO && b.base == TipoDado.LOGICO
                    if (numericos || caracteres || booleanos) Tipo(TipoDado.LOGICO) else null
                }
                "+", "-", "*", "/", "%" -> {
                    if (expr.operador == "%" &&
                        (!TiposNumericos.ehInteiro(a) || !TiposNumericos.ehInteiro(b)))
                        null
                    else TiposNumericos.resultadoAritmetico(
                        a, b, expr.operador,
                        TiposNumericos.ehLiteralInteiroNaoNegativo(expr.esquerda),
                        TiposNumericos.ehLiteralInteiroNaoNegativo(expr.direita)
                    )
                }
                else -> null
            }
        }
        else -> null
    }
}
