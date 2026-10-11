package co.adilson889.typec.validador

import co.adilson889.typec.ast.*
import co.adilson889.typec.semantica.TiposNativosC

/**
 * V2.7: ownership por movimento de ponteiros locais e analise de caminhos se/senao.
 * Cada parametro 'ponteiro' recebe ownership; funcao com retorno 'ponteiro'
 * transfere ownership ao chamador. Esta convencao nao e um borrow checker.
 *
 * Conservador: sem aliases, reatribuicao, realloc, ponteiros globais/structs,
 * ownership em lacos ou integracao a codigo externo sem contrato.
 * Projeto original: Adilson C. Rafael. Proposta: Artur Mendes Vieira Alves
 * (@tuhmds). Implementacao assistida por IA. GPL-3.0-or-later.
 */
class AnaliseOwnershipV27(private val fonte: String) {
    private enum class Estado { ATIVO, LIBERADO, TRANSFERIDO }
    private data class Trilha(
        val recursos: LinkedHashMap<String, Estado> = linkedMapOf(),
        var alcancavel: Boolean = true
    ) {
        fun copia() = Trilha(LinkedHashMap(recursos), alcancavel)
    }

    private val alocadores = setOf("aloque", "aloque_zerado")
    private val funcoes = mutableMapOf<String, DeclaracaoFuncao>()

    private fun erro(no: No, msg: String): Nothing =
        throw ErroValidacao("ownership: " + msg, no.linha, fonte)

    private fun ponteiro(t: Tipo) =
        t.base == TipoDado.PONTEIRO_OPACO && !t.ehArray && !t.ehArray2D &&
            t.nomeStruct == null

    private fun verificarSemVazamento(n: No, t: Trilha) {
        val vivos = t.recursos.filterValues { it == Estado.ATIVO }.keys
        if (vivos.isNotEmpty())
            erro(n, "possivel vazamento: ponteiro(s) sem libere: " +
                vivos.joinToString(", "))
    }

    private fun tomar(nome: Identificador, t: Trilha, liberar: Boolean) {
        when (t.recursos[nome.nome]) {
            Estado.ATIVO -> t.recursos[nome.nome] =
                if (liberar) Estado.LIBERADO else Estado.TRANSFERIDO
            Estado.LIBERADO -> erro(nome, if (liberar)
                "liberacao duplicada de '" + nome.nome + "'"
                else "uso apos libere do ponteiro '" + nome.nome + "'")
            Estado.TRANSFERIDO -> erro(nome, "uso apos transferencia do ponteiro '" +
                nome.nome + "'")
            null -> erro(nome, "ponteiro sem posse conhecida: '" + nome.nome + "'")
        }
    }

    private fun verificarChamada(
        c: ChamadaFuncao, t: Trilha, emLaco: Boolean, resultadoTomado: Boolean
    ) {
        if (c.nome == "libere") {
            if (emLaco) erro(c, "libere em laco ainda nao possui analise de fluxo")
            if (c.argumentos.size != 1) erro(c, "libere espera um ponteiro")
            consumir(c.argumentos.single(), t, emLaco, true)
            return
        }
        if (c.nome in alocadores || c.nome == "realoque")
            erro(c, if (c.nome == "realoque")
                "realoque ainda nao possui regras seguras de ownership"
                else "alocacao deve inicializar ponteiro, ser retornada ou passada a libere")
        val f = funcoes[c.nome]
        if (f == null) {
            c.argumentos.forEach { examinar(it, t, emLaco) }
            if (TiposNativosC.retorno(c.nome)?.let { ponteiro(it) } == true)
                erro(c, "ponteiro nativo sem contrato de ownership: '" + c.nome + "'")
            return
        }
        if (f.corpo == null && (ponteiro(f.tipoRetorno) ||
                f.parametros.any { ponteiro(it.tipo) }))
            erro(c, "funcao externa com ponteiro exige contrato de ownership")
        if (c.argumentos.size != f.parametros.size) {
            // A checagem de aridade pertence ao verificador de tipos posterior.
            c.argumentos.forEach { examinar(it, t, emLaco) }
            return
        }
        for ((i, arg) in c.argumentos.withIndex()) {
            if (ponteiro(f.parametros[i].tipo)) consumir(arg, t, emLaco, false)
            else examinar(arg, t, emLaco)
        }
        if (ponteiro(f.tipoRetorno) && !resultadoTomado)
            erro(c, "resultado ponteiro de '" + c.nome +
                "' deve ter ownership transferido ou ser liberado")
    }

    /** Consome uma unica referencia possuida, sem permitir alias nem copia. */
    private fun consumir(no: No, t: Trilha, emLaco: Boolean, liberar: Boolean) {
        if (emLaco) erro(no, "transferencia/liberacao em laco ainda nao suportada")
        when (no) {
            is Identificador -> tomar(no, t, liberar)
            is ChamadaFuncao -> {
                if (no.nome in alocadores) {
                    no.argumentos.forEach { examinar(it, t, emLaco) }
                } else {
                    val f = funcoes[no.nome]
                    if (f == null || !ponteiro(f.tipoRetorno) || f.corpo == null)
                        erro(no, "origem de ponteiro sem contrato de ownership")
                    verificarChamada(no, t, emLaco, true)
                }
            }
            else -> erro(no, "transferencia exige ponteiro possuido ou alocacao direta")
        }
    }

    private fun examinar(no: No, t: Trilha, emLaco: Boolean) {
        when (no) {
            is Identificador -> when (t.recursos[no.nome]) {
                Estado.LIBERADO -> erro(no, "uso apos libere do ponteiro '" + no.nome + "'")
                Estado.TRANSFERIDO -> erro(no, "uso apos transferencia do ponteiro '" + no.nome + "'")
                Estado.ATIVO -> erro(no, "ponteiro '" + no.nome +
                    "' nao pode ser copiado nem emprestado sem contrato")
                null -> Unit
            }
            is ChamadaFuncao -> verificarChamada(no, t, emLaco, false)
            is OperacaoBinaria -> {
                examinar(no.esquerda, t, emLaco)
                examinar(no.direita, t, emLaco)
            }
            is OperacaoUnaria -> examinar(no.operando, t, emLaco)
            is AcessoIndice -> {
                examinar(no.array, t, emLaco)
                examinar(no.indice, t, emLaco)
            }
            is AcessoCampo -> examinar(no.objeto, t, emLaco)
            is ArrayLiteral -> no.valores.forEach { examinar(it, t, emLaco) }
            is Atribuicao -> atribuir(no, t, emLaco)
            is IncrementoDecremento -> examinar(no.alvo, t, emLaco)
            else -> Unit
        }
    }

    private fun declarar(d: DeclaracaoVariavel, t: Trilha, emLaco: Boolean,
                         emRamo: Boolean) {
        if (ponteiro(d.tipo)) {
            if (emLaco || emRamo)
                erro(d, "declaracao de ponteiro em ramificacao ou laco " +
                    "ainda nao possui analise de escopo completa")
            if (t.recursos.containsKey(d.nome))
                erro(d, "nome de ponteiro ja utilizado: '" + d.nome + "'")
            val origem = d.valorInicial ?: erro(d, "ponteiro deve ser inicializado " +
                "com alocacao ou transferencia de posse")
            if (origem is ChamadaFuncao && origem.nome == "realoque")
                erro(origem, "realoque ainda nao possui regras seguras de ownership")
            consumir(origem, t, emLaco, false)
            t.recursos[d.nome] = Estado.ATIVO
        } else d.valorInicial?.let { examinar(it, t, emLaco) }
    }

    private fun atribuir(a: Atribuicao, t: Trilha, emLaco: Boolean) {
        if (a.alvo is Identificador && a.alvo.nome in t.recursos)
            erro(a, "reatribuicao de ponteiro ainda nao permitida")
        examinar(a.alvo, t, emLaco)
        examinar(a.valor, t, emLaco)
    }

    private fun condicional(c: ComandoSe, t: Trilha, emLaco: Boolean, retornaPonteiro: Boolean) {
        val original = t.copia()
        examinar(c.condicao, original, emLaco)
        val saidas = mutableListOf<Trilha>()
        val ramos = listOf(c.entao) + c.senaoSe.map { it.segundo }
        for (cmds in ramos) {
            val ramo = original.copia()
            bloco(cmds, ramo, emLaco, true, retornaPonteiro)
            saidas.add(ramo)
        }
        c.senaoSe.forEach { examinar(it.primeiro, original, emLaco) }
        val alternativa = original.copia()
        if (c.senao != null) bloco(c.senao, alternativa, emLaco, true, retornaPonteiro)
        saidas.add(alternativa)

        val alcancaveis = saidas.filter { it.alcancavel }
        if (alcancaveis.isEmpty()) {
            t.alcancavel = false
            return
        }
        val base = alcancaveis.first().recursos
        if (alcancaveis.drop(1).any { it.recursos != base })
            erro(c, "estados de posse divergentes entre ramos se/senao; " +
                "libere ou transfira o recurso em todos os caminhos")
        t.recursos.clear()
        t.recursos.putAll(base)
    }

    private fun loop(
        n: No, corpo: List<No>, t: Trilha, condicoes: List<No> = emptyList(),
        retornaPonteiro: Boolean = false
    ) {
        condicoes.forEach { examinar(it, t, true) }
        val copia = t.copia()
        bloco(corpo, copia, true, false, retornaPonteiro)
        if (copia.recursos != t.recursos)
            erro(n, "laco nao pode alterar ownership ate haver analise de iteracoes")
    }

    private fun bloco(
        comandos: List<No>, t: Trilha, emLaco: Boolean = false,
        emRamo: Boolean = false, retornaPonteiro: Boolean = false
    ) {
        for (n in comandos) {
            if (!t.alcancavel) break
            when (n) {
                is DeclaracaoVariavel -> declarar(n, t, emLaco, emRamo)
                is ComandoDeclaracoes -> n.declaracoes.forEach {
                    declarar(it, t, emLaco, emRamo)
                }
                is ExpressaoComando -> examinar(n.expressao, t, emLaco)
                is Atribuicao -> atribuir(n, t, emLaco)
                is IncrementoDecremento -> examinar(n.alvo, t, emLaco)
                is ComandoImprimir -> n.argumentos.forEach {
                    examinar(it, t, emLaco)
                }
                is ComandoLer -> examinar(n.alvo, t, emLaco)
                is ComandoRetorna -> {
                    if (retornaPonteiro) {
                        val valor = n.valor ?: erro(n, "funcao ponteiro deve retornar posse")
                        consumir(valor, t, emLaco, false)
                    } else n.valor?.let { examinar(it, t, emLaco) }
                    verificarSemVazamento(n, t)
                    t.alcancavel = false
                }
                is ComandoSe -> condicional(n, t, emLaco, retornaPonteiro)
                is ComandoEnquanto -> loop(n, n.corpo, t, listOf(n.condicao), retornaPonteiro)
                is ComandoFacaEnquanto -> loop(n, n.corpo, t, listOf(n.condicao), retornaPonteiro)
                is ComandoPara -> {
                    n.inicializacao?.let { bloco(listOf(it), t, true, false, retornaPonteiro) }
                    val extras = listOfNotNull(n.condicao)
                    val cmds = n.corpo + listOfNotNull(n.incremento)
                    loop(n, cmds, t, extras, retornaPonteiro)
                }
                is ComandoParaCada -> {
                    if (ponteiro(n.tipoElemento))
                        erro(n, "ponteiro em cada exige analise de lifetime")
                    examinar(n.array, t, true)
                    loop(n, n.corpo, t, retornaPonteiro=retornaPonteiro)
                }
                is ComandoEscolher -> {
                    examinar(n.valor, t, true)
                    for (caso in n.casos) {
                        examinar(caso.valor, t, true)
                        val copia = t.copia()
                        bloco(caso.corpo, copia, true, true, retornaPonteiro)
                        if (copia.recursos != t.recursos)
                            erro(n, "escolher nao pode alterar ownership nesta versao")
                    }
                    n.padrao?.let {
                        val copia = t.copia()
                        bloco(it, copia, true, true, retornaPonteiro)
                        if (copia.recursos != t.recursos)
                            erro(n, "escolher nao pode alterar ownership nesta versao")
                    }
                }
                else -> Unit
            }
        }
    }

    fun validar(programa: Programa, modulos: List<Programa> = emptyList()) {
        funcoes.clear()
        for (p in modulos + programa) for (d in p.declaracoesGlobais)
            if (d is DeclaracaoFuncao) funcoes[d.nome] = d

        for (p in modulos + programa) for (d in p.declaracoesGlobais) {
            when (d) {
                is DeclaracaoVariavel -> if (ponteiro(d.tipo))
                    erro(d, "ponteiros globais ainda nao possuem ownership")
                is DeclaracaoStruct -> if (d.campos.any { ponteiro(it.tipo) })
                    erro(d, "campo ponteiro de estrutura ainda nao tem ownership")
                is DeclaracaoFuncao -> {
                    if (d.parametros.any { ponteiro(it.tipo) && it.ehAlterar })
                        erro(d, "altere ponteiro ainda nao tem contrato de ownership")
                    if (d.corpo == null && (ponteiro(d.tipoRetorno) ||
                            d.parametros.any { ponteiro(it.tipo) }))
                        erro(d, "funcao externa com ponteiro exige contrato")
                    d.corpo?.let { corpo ->
                        val t = Trilha()
                        d.parametros.forEach {
                            if (ponteiro(it.tipo)) {
                                if (it.nome in t.recursos)
                                    erro(it, "parametro ponteiro duplicado")
                                t.recursos[it.nome] = Estado.ATIVO
                            }
                        }
                        bloco(corpo, t, retornaPonteiro=ponteiro(d.tipoRetorno))
                        if (t.alcancavel) verificarSemVazamento(d, t)
                    }
                }
                else -> Unit
            }
        }
        programa.inicio?.let {
            val t = Trilha()
            bloco(it.comandos, t)
            if (t.alcancavel) verificarSemVazamento(it, t)
        }
    }
}
