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
class AnaliseOwnershipV29(private val fonte: String) {
    private enum class Estado { ATIVO, LIBERADO, TRANSFERIDO }
    private enum class SaidaLaco { PARAR, CONTINUAR }
    /**
     * Cada declaracao recebe um ID independente de seu nome.
     * Assim um ponteiro local pode ocultar outro externo sem perder o dono.
     */
    private data class Trilha(
        val recursos: LinkedHashMap<Int, Estado> = linkedMapOf(),
        val nomes: LinkedHashMap<String, Int> = linkedMapOf(),
        val rotulos: LinkedHashMap<Int, String> = linkedMapOf(),
        var proximo: Int = 0,
        var alcancavel: Boolean = true,
        val emprestados: MutableSet<String> = mutableSetOf(),
        var saidaLaco: SaidaLaco? = null,
        var inicioLaco: Map<Int, Estado>? = null
    ) {
        fun copia() = Trilha(LinkedHashMap(recursos), LinkedHashMap(nomes),
            LinkedHashMap(rotulos), proximo, alcancavel,
            emprestados.toMutableSet(), saidaLaco, inicioLaco?.toMap())
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

    /** O contrato const ponteiro e um emprestimo imutavel apenas durante a chamada. */
    private fun emprestado(p: Parametro): Boolean = ponteiro(p.tipo) && p.ehConstante

    private fun emprestar(arg: No, t: Trilha) {
        if (arg !is Identificador)
            erro(arg, "emprestimo exige identificador de ponteiro ativo")
        if (arg.nome in t.emprestados) return // repasse de emprestimo
        when (t.estado(arg.nome)) {
            Estado.ATIVO -> Unit
            Estado.LIBERADO -> erro(arg, "emprestimo apos libere do ponteiro '" + arg.nome + "'")
            Estado.TRANSFERIDO -> erro(arg, "emprestimo apos transferencia de '" + arg.nome + "'")
            null -> erro(arg, "emprestimo sem ponteiro local possuido")
        }
    }

    private fun verificarSemVazamento(n: No, t: Trilha) {
        val vivos = t.recursos.filterValues { it == Estado.ATIVO }.keys.map { t.rotulos[it] ?: "ponteiro" }
        if (vivos.isNotEmpty())
            erro(n, "possivel vazamento: ponteiro(s) sem libere: " +
                vivos.joinToString(", "))
    }

    private fun tomar(nome: Identificador, t: Trilha, liberar: Boolean) {
        if (nome.nome in t.emprestados)
            erro(nome, "ponteiro emprestado nao pode ser liberado nem transferido")
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
            when {
                emprestado(f.parametros[i]) -> emprestar(arg, t)
                ponteiro(f.parametros[i].tipo) -> consumir(arg, t, emLaco, false)
                else -> examinar(arg, t, emLaco)
            }
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
            is Identificador -> {
                if (no.nome in t.emprestados)
                    erro(no, "ponteiro emprestado nao pode escapar do contrato da chamada")
                when (t.estado(no.nome)) {
                Estado.LIBERADO -> erro(no, "uso apos libere do ponteiro '" + no.nome + "'")
                Estado.TRANSFERIDO -> erro(no, "uso apos transferencia do ponteiro '" + no.nome + "'")
                Estado.ATIVO -> erro(no, "ponteiro '" + no.nome +
                    "' nao pode ser copiado nem emprestado sem contrato")
                null -> Unit
                }
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
        if (d.nome in t.emprestados)
            erro(d, "nao e permitido ocultar parametro ponteiro emprestado")
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

    /**
     * A condicao pode ser reavaliada (ou ignorada) e nao pode transferir
     * recursos. Sem isso, a segunda avaliacao de enquanto pode usar um
     * ponteiro ja transferido na primeira.
     */
    private fun condicaoSemTransferencia(expr: No, t: Trilha, emLaco: Boolean) {
        val caminho = t.copia()
        examinar(expr, caminho, emLaco)
        if (caminho.recursos != t.recursos || caminho.nomes != t.nomes)
            erro(expr, "condicao nao pode transferir ownership nesta versao")
    }

    private fun condicional(c: ComandoSe, t: Trilha, emLaco: Boolean, retornaPonteiro: Boolean) {
        val original = t.copia()
        condicaoSemTransferencia(c.condicao, original, emLaco)
        val saidas = mutableListOf<Trilha>()
        val ramos = listOf(c.entao) + c.senaoSe.map { it.segundo }
        for (cmds in ramos) {
            val ramo = original.copia()
            bloco(cmds, ramo, emLaco, retornaPonteiro=retornaPonteiro, escopoLocal=true)
            saidas.add(ramo)
        }
        c.senaoSe.forEach { condicaoSemTransferencia(it.primeiro, original, emLaco) }
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
        // Um ramo pode sair do laco enquanto outro prossegue. Somente os
        // caminhos que prosseguem precisam ser analisados nos comandos seguintes.
        val normais = alcancaveis.filter { it.saidaLaco == null }
        t.saidaLaco = if (normais.isEmpty()) alcancaveis.first().saidaLaco else null
        t.proximo = saidas.map { it.proximo }.maxOrNull()?.coerceAtLeast(t.proximo) ?: t.proximo
    }

    private fun loop(
        n: No, corpo: List<No>, t: Trilha, condicoes: List<No> = emptyList(),
        retornaPonteiro: Boolean = false
    ) {
        condicoes.forEach { condicaoSemTransferencia(it, t, true) }
        val copia = t.copia()
        copia.inicioLaco = t.recursos.toMap()
        copia.saidaLaco = null
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
            if (!t.alcancavel || t.saidaLaco != null) break
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
                    // Em C, 'continue' num para executa o incremento. Exigir
                    // que ele nao tenha efeito sobre o ownership.
                    n.incremento?.let {
                        val incremento = t.copia()
                        bloco(listOf(it), incremento, true, false, retornaPonteiro)
                        if (incremento.recursos != t.recursos)
                            erro(it, "incremento do para nao pode transferir ownership")
                    }
                    val extras = listOfNotNull(n.condicao)
                    val cmds = n.corpo + listOfNotNull(n.incremento)
                    loop(n, cmds, t, extras, retornaPonteiro)
                }
                is ComandoParaCada -> {
                    if (ponteiro(n.tipoElemento))
                        erro(n, "ponteiro em cada exige analise de lifetime")
                    condicaoSemTransferencia(n.array, t, true)
                    loop(n, n.corpo, t, retornaPonteiro=retornaPonteiro)
                }
                is ComandoEscolher -> {
                    condicaoSemTransferencia(n.valor, t, true)
                    for (caso in n.casos) {
                        condicaoSemTransferencia(caso.valor, t, true)
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
                    if (!emLaco || t.inicioLaco == null)
                        erro(n, "pare/continue precisa estar dentro de um laco")
                    val antes = t.inicioLaco!!
                    if (antes.any { (id, estado) -> t.recursos[id] != estado })
                        erro(n, "pare/continue alterou a posse de recurso anterior ao laco")
                    val vivos = t.recursos.filter { (id, estado) ->
                        id !in antes && estado == Estado.ATIVO
                    }
                    if (vivos.isNotEmpty())
                        erro(n, "pare/continue com ponteiro local ativo: " +
                            vivos.keys.map { t.rotulos[it] }.joinToString(", "))
                    t.saidaLaco = if (n is ComandoDispensar)
                        SaidaLaco.PARAR else SaidaLaco.CONTINUAR
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
                                if (it.nome in t.nomes || it.nome in t.emprestados)
                                    erro(it, "parametro ponteiro duplicado")
                                if (emprestado(it)) t.emprestados.add(it.nome)
                                else t.declarar(it.nome)
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
