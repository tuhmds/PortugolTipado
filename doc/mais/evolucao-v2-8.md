# PortugolTipado V2.8 — escopos lexicais e laços

**Projeto original:** Adilson C. Rafael. **Proposta e direção:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com assistência de IA na implementação. Licença GPL-3.0-or-later preservada.

## Avanços implementados

A análise experimental agora identifica ponteiros pela **declaração lexical**, não apenas pelo nome textual. Isso impede confundir recursos homônimos quando um bloco local oculta o nome de outro ponteiro externo.

Também verifica uma **invariante conservadora de ownership em laços**: o corpo pode criar, liberar ou transferir recursos locais de cada iteração, desde que a posse dos ponteiros que já existiam antes do laço permaneça inalterada.

Exemplo:

~~~port
funcao inicio() {
    ponteiro externo = aloque(32)
    inteiro i = 0
    enquanto (i < 3) {
        ponteiro interno = aloque(16)
        libere(interno)
        i++
    }
    libere(externo)
}
~~~

O modo seguro permite esse programa. Um ponteiro local precisa ser liberado ou transferido antes de sair do bloco; do contrário, o compilador acusa possível vazamento.

A análise cobre blocos de se/senao, enquanto, faca/enquanto e para, com movimentos locais em funções de contrato próprio. Não permite modificar ownership de recursos externos dentro de laços; isso poderia causar dupla liberação na próxima iteração.

## Limitações

**Não é um borrow checker completo**, nem um coletor de lixo, nem comprovação formal de segurança em todas as situações.

Retorno dentro de laço, empréstimos, aliases, ponteiros próprios no cabeçalho de para, quebra de laço com recurso vivo e operações de realocação permanecem restritos. O verificador não modela falha de alocação, nulos, overflow nem interfaces C externas sem contrato. Nem todos os caminhos complexos recebem análise de ponto fixo; algumas formas seguras são conservadoramente rejeitadas.

## Testes e status

Os testes Kotlin/GCC e casos de regressão estão em tests/v2-8-escopos-lacos.sh e .github/workflows/v2-8-ownership.yml. A regressão V2.1 dos buffers de texto continua ativa. O sucesso da CI deve ser verificado antes de considerar esta versão aprovada.

Esta branch nao altera o [PR #4](https://github.com/adilson889/PortugolTipado/pull/4) do mantenedor.
