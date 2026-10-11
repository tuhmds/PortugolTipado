package co.adilson889.typec.validador

import co.adilson889.typec.ast.*

/**
 * Ownership V2.6: analise estatica conservadora de ponteiros C locais.
 *
 * Suporta apenas recursos criados por aloque/aloque_zerado e liberados
 * linearmente no mesmo corpo. Rejeita transferencias, aliases, retornos,
 * reatribuicoes e operacoes de ownership em desvios ou lacos.
 *
 * NAO substitui um verificador de lifetime geral, nem impede overflow,
 * null pointers ou falha em malloc.
 *
 * Projeto original: Adilson C. Rafael.
 * Proposta e direcao: Artur Mendes Vieira Alves (@tuhmds).
 * Implementacao assistida por IA. GPL-3.0-or-later.
 */
class AnaliseOwnershipV26(private val fonte: String) {
    private enum class Estado { ATIVO, LIBERADO }
    private val alocadores = setOf("aloque", "aloque_zerado")
    private val gerenciadores = alocadores + setOf("realoque", "libere")

    private fun erro(no: No, mensagem: String): Nothing =
        throw ErroValidacao("ownership: " + mensagem, no.linha, fonte)

    private fun ehPonteiro(t: Tipo): Boolean =
        t.base == TipoDado.PONTEIRO_OPACO && !t.ehArray && !t.ehArray2D

    private fun inspecionarExpressao(
        expr: No, recursos: MutableMap<String, Estado>, fluxoComplexo: Boolean
    ) {
        when (expr) {
            is Identificador -> {
                val estado = recursos[expr.nome]
                if (estado == Estado.LIBERADO)
                    erro(expr, "uso apos libere do ponteiro '" + expr.nome + "'")
                if (estado == Estado.ATIVO)
                    erro(expr, "ponteiro '" + expr.nome +
                        "' possui dono local; nesta versao so pode ser passado a libere")
            }
            is ChamadaFuncao -> {
                if (expr.nome == "libere") {
                    liberar(expr, recursos, fluxoComplexo)
                } else if (expr.nome in alocadores || expr.nome == "realoque") {
                    erro(expr, "alocacao deve inicializar uma variavel 'ponteiro'; " +
                        "realoque ainda nao possui regras de ownership")
                } else {
                    expr.argumentos.forEach {
                        inspecionarExpressao(it, recursos, fluxoComplexo)
                    }
                }
            }
            is OperacaoBinaria -> {
                inspecionarExpressao(expr.esquerda, recursos, fluxoComplexo)
                inspecionarExpressao(expr.direita, recursos, fluxoComplexo)
            }
            is OperacaoUnaria -> inspecionarExpressao(expr.operando, recursos, fluxoComplexo)
            is AcessoIndice -> {
                inspecionarExpressao(expr.array, recursos, fluxoComplexo)
                inspecionarExpressao(expr.indice, recursos, fluxoComplexo)
            }
            is AcessoCampo -> inspecionarExpressao(expr.objeto, recursos, fluxoComplexo)
            is ArrayLiteral -> expr.valores.forEach {
                inspecionarExpressao(it, recursos, fluxoComplexo)
            }
            is Atribuicao -> atribuir(expr, recursos, fluxoComplexo)
            is IncrementoDecremento ->
                inspecionarExpressao(expr.alvo, recursos, fluxoComplexo)
            else -> Unit
        }
    }

    private fun liberar(
        chamada: ChamadaFuncao, recursos: MutableMap<String, Estado>,
        fluxoComplexo: Boolean
    ) {
        if (fluxoComplexo)
            erro(chamada, "libere em ramificacao ou laco exige analise de fluxo " +
                "ainda nao disponivel; libere no mesmo bloco linear")
        if (chamada.argumentos.size != 1)
            erro(chamada, "libere espera exatamente um ponteiro")
        val alvo = chamada.argumentos.single()
        if (alvo is Identificador) {
            when (recursos[alvo.nome]) {
                Estado.ATIVO -> recursos[alvo.nome] = Estado.LIBERADO
                Estado.LIBERADO -> erro(alvo, "liberacao duplicada de '" + alvo.nome + "'")
                null -> erro(alvo, "libere requer ponteiro local de posse conhecida")
            }
        } else if (alvo is ChamadaFuncao && alvo.nome in alocadores) {
            alvo.argumentos.forEach {
                inspecionarExpressao(it, recursos, fluxoComplexo)
            }
        } else erro(alvo, "libere aceita apenas ponteiro local ou alocacao direta")
    }

    private fun atribuir(
        a: Atribuicao, recursos: MutableMap<String, Estado>, fluxoComplexo: Boolean
    ) {
        if (a.alvo is Identificador && recursos.containsKey(a.alvo.nome))
            erro(a, "reatribuicao de ponteiro local ainda nao permitida; " +
                "evite aliases e perda da referencia original")
        inspecionarExpressao(a.alvo, recursos, fluxoComplexo)
        inspecionarExpressao(a.valor, recursos, fluxoComplexo)
    }

    private fun declarar(
        d: DeclaracaoVariavel, recursos: MutableMap<String, Estado>,
        fluxoComplexo: Boolean
    ) {
        if (ehPonteiro(d.tipo)) {
            if (fluxoComplexo)
                erro(d, "declaracao de ponteiro em ramificacao ou laco " +
                    "ainda nao possui analise de ownership")
            if (recursos.containsKey(d.nome))
                erro(d, "nome de ponteiro ja usado no bloco: '" + d.nome + "'")
            val origem = d.valorInicial
            if (origem !is ChamadaFuncao || origem.nome !in alocadores)
                erro(d, "ponteiro local deve ser inicializado com aloque " +
                    "ou aloque_zerado; aliases e ponteiros sem dono nao sao aceitos")
            origem.argumentos.forEach {
                inspecionarExpressao(it, recursos, fluxoComplexo)
            }
            recursos[d.nome] = Estado.ATIVO
        } else {
            d.valorInicial?.let { inspecionarExpressao(it, recursos, fluxoComplexo) }
        }
    }

    private fun verificarSemVazamentos(
        no: No, recursos: Map<String, Estado>
    ) {
        val pendentes = recursos.filterValues { it == Estado.ATIVO }.keys
        if (pendentes.isNotEmpty())
            erro(no, "possivel vazamento: ponteiro(s) sem libere: " +
                pendentes.joinToString(", "))
    }

    private fun bloco(
        comandos: List<No>, recursos: MutableMap<String, Estado>,
        fluxoComplexo: Boolean = false
    ) {
        for (n in comandos) when (n) {
            is DeclaracaoVariavel -> declarar(n, recursos, fluxoComplexo)
            is ComandoDeclaracoes -> n.declaracoes.forEach {
                declarar(it, recursos, fluxoComplexo)
            }
            is ExpressaoComando -> inspecionarExpressao(n.expressao, recursos, fluxoComplexo)
            is Atribuicao -> atribuir(n, recursos, fluxoComplexo)
            is IncrementoDecremento ->
                inspecionarExpressao(n.alvo, recursos, fluxoComplexo)
            is ComandoImprimir -> n.argumentos.forEach {
                inspecionarExpressao(it, recursos, fluxoComplexo)
            }
            is ComandoLer -> inspecionarExpressao(n.alvo, recursos, fluxoComplexo)
            is ComandoRetorna -> {
                n.valor?.let { inspecionarExpressao(it, recursos, fluxoComplexo) }
                verificarSemVazamentos(n, recursos)
            }
            is ComandoSe -> {
                inspecionarExpressao(n.condicao, recursos, true)
                bloco(n.entao, recursos, true)
                n.senaoSe.forEach {
                    inspecionarExpressao(it.primeiro, recursos, true)
                    bloco(it.segundo, recursos, true)
                }
                n.senao?.let { bloco(it, recursos, true) }
            }
            is ComandoEnquanto -> {
                inspecionarExpressao(n.condicao, recursos, true)
                bloco(n.corpo, recursos, true)
            }
            is ComandoFacaEnquanto -> {
                bloco(n.corpo, recursos, true)
                inspecionarExpressao(n.condicao, recursos, true)
            }
            is ComandoPara -> {
                n.inicializacao?.let { bloco(listOf(it), recursos, true) }
                n.condicao?.let { inspecionarExpressao(it, recursos, true) }
                n.incremento?.let { bloco(listOf(it), recursos, true) }
                bloco(n.corpo, recursos, true)
            }
            is ComandoParaCada -> {
                inspecionarExpressao(n.array, recursos, true)
                if (ehPonteiro(n.tipoElemento))
                    erro(n, "ponteiro em 'cada' requer analise de ownership")
                bloco(n.corpo, recursos, true)
            }
            is ComandoEscolher -> {
                inspecionarExpressao(n.valor, recursos, true)
                n.casos.forEach {
                    inspecionarExpressao(it.valor, recursos, true)
                    bloco(it.corpo, recursos, true)
                }
                n.padrao?.let { bloco(it, recursos, true) }
            }
            else -> Unit
        }
    }

    fun validar(programa: Programa, modulos: List<Programa> = emptyList()) {
        for (p in modulos + programa) {
            for (d in p.declaracoesGlobais) {
                when (d) {
                    is DeclaracaoVariavel -> if (ehPonteiro(d.tipo))
                        erro(d, "ponteiros globais ainda nao possuem ownership")
                    is DeclaracaoFuncao -> {
                        if (ehPonteiro(d.tipoRetorno))
                            erro(d, "retornar ponteiro exige transferencia de posse ainda nao implementada")
                        d.parametros.forEach {
                            if (ehPonteiro(it.tipo))
                                erro(it, "parametro ponteiro exige contrato de ownership explicito")
                        }
                        d.corpo?.let {
                            val recursos = linkedMapOf<String, Estado>()
                            bloco(it, recursos)
                            verificarSemVazamentos(d, recursos)
                        }
                    }
                    else -> Unit
                }
            }
        }
        programa.inicio?.let {
            val recursos = linkedMapOf<String, Estado>()
            bloco(it.comandos, recursos)
            verificarSemVazamentos(it, recursos)
        }
    }
}
