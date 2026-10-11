package co.adilson889.typec.diagnostico

import co.adilson889.typec.interpretador.FonteEntrada
import co.adilson889.typec.interpretador.Interpretador
import co.adilson889.typec.interpretador.ResultadoExecucao
import co.adilson889.typec.lexer.tokenizarFonte
import co.adilson889.typec.parser.Parser
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Verificacao sem interface grafica do mesmo interpretador usado na aplicacao.
 * Executada pela regressao de compatibilidade para comparar com o C gerado.
 */
fun main(args: Array<String>) {
    require(args.size in 2..3) {
        "Utilização: CompatibilidadeTexto <programa.port> <resultado esperado> [entrada]"
    }
    val fonte = File(args[0]).readText()
    val programa = Parser(tokenizarFonte(fonte), fonte).parsear()
    val entrada = args.getOrElse(2) { "" }
    val interpretador = Interpretador(
        fonteEntrada = object : FonteEntrada {
            override suspend fun pedirValor(promptLinha: Int): String = entrada
        }
    )
    var resultado: ResultadoExecucao? = null
    var falha: Throwable? = null
    val executar: suspend () -> ResultadoExecucao = { interpretador.executar(programa) }
    executar.startCoroutine(object : Continuation<ResultadoExecucao> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(value: Result<ResultadoExecucao>) {
            value.onSuccess { resultado = it }.onFailure { falha = it }
        }
    })
    falha?.let { throw it }
    val obtido = when (val r = resultado ?: error("Execução do interpretador não terminou")) {
        is ResultadoExecucao.Sucesso -> r.saida
        is ResultadoExecucao.Erro -> error("Interpretador: ${r.mensagem}")
        is ResultadoExecucao.Interrompido -> error("Interpretador interrompido: ${r.mensagem}")
    }
    check(obtido == args[1]) {
        "Resultado do interpretador: '${obtido}', esperado: '${args[1]}'"
    }
    println("OK interpretador: ${args[0]}")
}
