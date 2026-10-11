package co.adilson889.typec.semantica

import co.adilson889.typec.ast.Tipo
import co.adilson889.typec.ast.TipoDado
import co.adilson889.typec.ast.No
import co.adilson889.typec.ast.Numero

/**
 * Regras numericas compartilhadas pelo analisador semantico e pelo transpilador.
 *
 * Apenas tipos escalares. A verificacao de compatibilidade de atribuicoes
 * permanece responsabilidade do analisador de tipos.
 *
 * Projeto original: Adilson C. Rafael.
 * Contribuicao: Artur Mendes Vieira Alves (@tuhmds), com assistencia de IA.
 * Licenca: GPL-3.0-or-later.
 */
object TiposNumericos {
    private val inteiros = setOf(
        TipoDado.INTEIRO_CURTO, TipoDado.INTEIRO, TipoDado.INTEIRO_LONGO,
        TipoDado.INTEIRO_GIGANTE, TipoDado.INTEIRO_POSITIVO
    )
    private val flutuantes = setOf(
        TipoDado.REAL, TipoDado.DUPLO, TipoDado.DUPLO_LONGO
    )

    private fun escalar(tipo: Tipo): Boolean =
        !tipo.ehArray && !tipo.ehArray2D && tipo.nomeStruct == null

    fun ehInteiro(tipo: Tipo): Boolean = escalar(tipo) && tipo.base in inteiros

    /** Literal de inteiro C positivo ou zero, seguramente representavel em int. */
    fun ehLiteralInteiroNaoNegativo(n: No): Boolean =
        n is Numero && n.tipo == TipoDado.INTEIRO &&
            (n.valor.toIntOrNull()?.let { it >= 0 } == true)

    fun ehNumerico(tipo: Tipo): Boolean =
        escalar(tipo) && (tipo.base in inteiros || tipo.base in flutuantes)

    private fun postoInteiro(t: TipoDado): Int = when (t) {
        TipoDado.INTEIRO_CURTO -> 0
        TipoDado.INTEIRO, TipoDado.INTEIRO_POSITIVO -> 1
        TipoDado.INTEIRO_LONGO -> 2
        TipoDado.INTEIRO_GIGANTE -> 3
        else -> -1
    }

    private fun postoReal(t: TipoDado): Int = when (t) {
        TipoDado.REAL -> 1
        TipoDado.DUPLO -> 2
        TipoDado.DUPLO_LONGO -> 3
        else -> -1
    }

    /**
     * Resultado de operacoes +, -, *, / e %.
     *
     * Retorna null se os operandos nao sao numericos ou se ha mistura
     * implicita de inteiros assinados com inteiros positivos.
     * A verificacao de divisao por zero e outras regras nao pertence aqui.
     */
    fun resultadoAritmetico(
        esquerda: Tipo, direita: Tipo, operador: String = "+",
        literalEsquerdoSeguro: Boolean = false, literalDireitoSeguro: Boolean = false
    ): Tipo? {
        if (!escalar(esquerda) || !escalar(direita)) return null
        val a = esquerda.base
        val b = direita.base

        // size_t e unsigned, com largura dependente da plataforma. Somente
        // a adicao de dois tamanhos ou de tamanho com literal int >= 0 e aceita.
        if (a == TipoDado.TAMANHO || b == TipoDado.TAMANHO) {
            if (operador != "+") return null
            val doisTamanhos = a == TipoDado.TAMANHO && b == TipoDado.TAMANHO
            val tamanhoLiteral = a == TipoDado.TAMANHO &&
                b == TipoDado.INTEIRO && literalDireitoSeguro
            val literalTamanho = b == TipoDado.TAMANHO &&
                a == TipoDado.INTEIRO && literalEsquerdoSeguro
            return if (doisTamanhos || tamanhoLiteral || literalTamanho)
                Tipo(TipoDado.TAMANHO) else null
        }

        if (!ehNumerico(esquerda) || !ehNumerico(direita)) return null

        if (a in flutuantes || b in flutuantes) {
            val t = listOf(a, b).filter { it in flutuantes }
                .maxBy { postoReal(it) }
            return Tipo(t)
        }

        val positivo = TipoDado.INTEIRO_POSITIVO
        if ((a == positivo) != (b == positivo)) return null
        if (a == positivo) return Tipo(positivo)

        val t = listOf(a, b).maxBy { postoInteiro(it) }
        // Em C, short sofre promocao integral para int nas operacoes.
        return Tipo(if (t == TipoDado.INTEIRO_CURTO) TipoDado.INTEIRO else t)
    }

    /** Operadores unarios mantem a precisao decimal; short promove a int. */
    fun resultadoUnario(operador: String, operando: Tipo): Tipo? {
        if (operador == "!") return Tipo(TipoDado.LOGICO)
        if (operador != "-" || !ehNumerico(operando)) return null
        return if (operando.base == TipoDado.INTEIRO_CURTO)
            Tipo(TipoDado.INTEIRO) else operando
    }
}
