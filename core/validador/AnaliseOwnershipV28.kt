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
class AnaliseOwnershipV28(private val fonte: String) {
    private enum class Estado { ATIVO, LIBERADO, TRANSFERIDO }
    /**
     * Cada declaracao recebe um ID independente de seu nome.
     * Assim um ponteiro local pode ocultar outro externo sem perder o dono.
     */
    private data class Trilha(
        val recursos: LinkedHashMap<Int, Estado> = linkedMapOf(),
        val nomes: LinkedHashMap<String, Int> = linkedMapOf(),
        val rotulos: LinkedHashMap<Int, String> = linkedMapOf(),
        var proximo: Int = 0,
        var alcancavel: Boolean = true
    ) {
        fun copia() = Trilha(LinkedHashMap(recursos), LinkedHashMap(nomes),
            LinkedHashMap(rotulos), proximo, alcancavel)
        fun estado(nome: String): Estado? = nomes[nome]?.let { recursos[it] }
        fun marcar(nome: String, estado: Estado) {
            val id = nomes[nome] ?: error("ponteiro nao registrado: $nome")
            recursos[id] = estado
        }
        fun declarar(nome: String) {
            val id = proximo++
            nomes[nome] = id
            rotulos[id] = nome
            recursos[id] = Estado.ATIVO
        }
    }

    private val alocadores = setOf("aloque", "aloque_zerado")
    private val funcoes = mutableMapOf<String, DeclaracaoFuncao>()

    private fun erro(no: No, msg: String): Nothing =
        throw ErroValidacao("ownership: " + msg, no.linha, fonte)

    private fun ponteiro(t: Tipo) =
        t.base == TipoDado.PONTEIRO_OPACO && !t.ehArray && !t.ehArray2D &&
            t.nomeStruct == null

    private fun verificarSemVazamento(n: No, t: Trilha) {
        val vivos = t.recursos.filterValues { it == Estado.ATIVO }.keys.map { t.rotulos[it] ?: "ponteiro" }
        if (vivos.isNotEmpty())
            erro(n, "possivel vazamento: ponteiro(s) sem libere: " +
                vivos.joinToString(", "))
    }

    private fun tomar(nome: Identificador, t: Trilha, liberar: Boolean) {
        when (t.estado(nome.nome)) {
            Estado.ATIVO -> t.marcar(nome.nome,
                if (liberar) Estado.LIBERADO else Estado.TRANSFERIDO)
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
            is Identificador -> when (t.estado(no.nome)) {
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

    private fun declarar(d: DeclaracaoVariavel, t: Trilha, emLaco: Boolean) {
        if (ponteiro(d.tipo)) {
            val origem = d.valorInicial ?: erro(d, "ponteiro deve ser inicializado " +
                "com alocacao ou transferencia de posse")
            if (origem is ChamadaFuncao && origem.nome == "realoque")
                erro(origem, "realoque ainda nao possui regras seguras de ownership")
            consumir(origem, t, emLaco, false)
            t.declarar(d.nome)
        } else d.valorInicial?.let { examinar(it, t, emLaco) }
    }

    private fun atribuir(a: Atribuicao, t: Trilha, emLaco: Boolean) {
        if (a.alvo is Identificador && a.alvo.nome in t.nomes)
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
            bloco(cmds, ramo, emLaco, retornaPonteiro=retornaPonteiro, escopoLocal=true)
            saidas.add(ramo)
        }
        c.senaoSe.forEach { examinar(it.primeiro, original, emLaco) }
        val alternativa = original.copia()
        if (c.senao != null) bloco(c.senao, alternativa, emLaco, retornaPonteiro=retornaPonteiro, escopoLocal=true)
        saidas.add(alternativa)

        val alcancaveis = saidas.filter { it.alcancavel }
        if (alcancaveis.isEmpty()) {
            t.alcancavel = false
            return
        }
        val base = alcancaveis.first().recursos
        if (alcancaveis.drop(1).any { it.recursos != base || it.nomes != alcancaveis.first().nomes })
            erro(c, "estados de posse divergentes entre ramos se/senao; " +
                "libere ou transfira o recurso em todos os caminhos")
        t.recursos.clear()
        t.recursos.putAll(base)
        t.proximo = saidas.map { it.proximo }.maxOrNull()?.coerceAtLeast(t.proximo) ?: t.proximo
    }

    private fun loop(
        n: No, corpo: List<No>, t: Trilha, condicoes: List<No> = emptyList(),
        retornaPonteiro: Boolean = false
    ) {
        condicoes.forEach { examinar(it, t, true) }
        val copia = t.copia()
        bloco(corpo, copia, true, retornaPonteiro=retornaPonteiro, escopoLocal=true)
        if (copia.recursos != t.recursos || copia.nomes != t.nomes)
            erro(n, "laco nao pode alterar ownership ate haver analise de iteracoes")
    }

    /**
     * Conserva mutacoes nos recursos externos e elimina os locais na saida.
     * Todo recurso local precisa ser liberado ou transferido antes de sair.
     */
    private fun bloco(
        comandos: List<No>, t: Trilha, emLaco: Boolean = false,
        emRamo: Boolean = false, retornaPonteiro: Boolean = false,
        escopoLocal: Boolean = false
    ) {
        val nomesExternos = if (escopoLocal) LinkedHashMap(t.nomes) else null
        val idsExternos = if (escopoLocal) t.recursos.keys.toSet() else emptySet()
        for (n in comandos) {
            if (!t.alcancavel) break
            when (n) {
                is DeclaracaoVariavel -> declarar(n, t, emLaco)
                is ComandoDeclaracoes -> n.declaracoes.forEach {
                    declarar(it, t, emLaco)
                }
                is ExpressaoComando -> examinar(n.expressao, t, emLaco)
                is Atribuicao -> atribuir(n, t, emLaco)
                is IncrementoDecremento -> examinar(n.alvo, t, emLaco)
                is ComandoImprimir -> n.argumentos.forEach {
                    examinar(it, t, emLaco)
                }
                is ComandoLer -> examinar(n.alvo, t, emLaco)
                is ComandoRetorna -> {
                    if (emLaco) erro(n, "retorne em laco ainda exige analise de saida")
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
                    if (n.inicializacao is DeclaracaoVariavel &&
                        ponteiro(n.inicializacao.tipo))
                        erro(n, "ponteiro no cabecalho do para ainda sem escopo de ownership")
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
                        bloco(caso.corpo, copia, true, true, retornaPonteiro, escopoLocal=true)
                        if (copia.recursos != t.recursos || copia.nomes != t.nomes)
                            erro(n, "escolher nao pode alterar ownership nesta versao")
                    }
                    n.padrao?.let {
                        val copia = t.copia()
                        bloco(it, copia, true, true, retornaPonteiro, escopoLocal=true)
                        if (copia.recursos != t.recursos || copia.nomes != t.nomes)
                            erro(n, "escolher nao pode alterar ownership nesta versao")
                    }
                }
                is ComandoDispensar, is ComandoIgnorar -> {
                    if (emLaco && t.recursos.values.any { it == Estado.ATIVO })
                        erro(n, "dispensar/ignorar em laco com ownership exige analise adicional")
                }
                else -> Unit
            }
        }
        if (escopoLocal) {
            val locais = t.recursos.keys.filter { it !in idsExternos }
            if (t.alcancavel) {
                val vivos = locais.filter { t.recursos[it] == Estado.ATIVO }
                if (vivos.isNotEmpty())
                    erro(comandos.lastOrNull() ?: return, "possivel vazamento no escopo local: " +
                        vivos.map { t.rotulos[it] }.joinToString(", "))
            }
            locais.forEach {
                t.recursos.remove(it)
                t.rotulos.remove(it)
            }
            t.nomes.clear()
            t.nomes.putAll(nomesExternos!!)
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
                                t.declarar(it.nome)
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
