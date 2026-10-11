# PortugolTipado V2.7 — fluxo e transferencia de posse (experimental)

**Autor do projeto original:** Adilson C. Rafael. **Proposta e direção das melhorias experimentais:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com assistência de IA na implementação. Licença GPL-3.0-or-later preservada.

## Evolução da V2.6

Esta etapa substitui a verificação exclusivamente linear por uma primeira **análise de estados em caminhos alternativos**. Quando o programa usa \`se/senão\`, cada ramo é analisado separadamente. Na junção, os caminhos que continuam devem ter estados de posse iguais; caso contrário o compilador recusa o programa, em vez de presumir que um recurso foi liberado.

Também permite a **transferência (move) de ownership** entre variáveis e funções explicitamente tipadas com \`ponteiro\`. O tipo \`ponteiro\` continua representando \`void*\` opaco, sem leitura/escrita indireta tipada.

## Exemplos

~~~port
funcao consumir(ponteiro recurso): vazio {
    libere(recurso)
}

funcao fabricar(): ponteiro {
    ponteiro recurso = aloque(16)
    retorne recurso
}

funcao inicio() {
    ponteiro bloco = fabricar()
    consumir(bloco)  // ownership passa para consumir
    escreva("liberado")
}
~~~

~~~port
funcao inicio() {
    ponteiro recurso = aloque(16)
    se (verdadeiro) {
        libere(recurso)
    } senao {
        libere(recurso)
    }
}
~~~

Uma chamada posterior a \`libere(bloco)\` no primeiro exemplo é recusada como **uso após transferência**. No segundo exemplo, ambos os ramos liberam o recurso, portanto o estado posterior é consistente.

## Regras adotadas

- Um \`ponteiro\` formal significa que a função **recebe a posse**; deve liberar ou transferir esse recurso em todos os caminhos válidos.
- Um retorno \`ponteiro\` significa que a função **transfere a posse para quem chama**; o resultado deve ser armazenado, encaminhado a outra função possuidora ou liberado.
- Inicializar \`ponteiro destino = origem\` move a posse: \`origem\` não pode mais ser usado.
- \`libere\` aceita um recurso possuído, uma alocação direta ou o retorno tipado de uma função possuidora.
- Ao terminar uma função, os ponteiros ainda ativos são sinalizados como possível vazamento.
- Ramificações completas podem liberar ou mover o mesmo recurso; se os caminhos alcançáveis divergem na posse, ocorre erro em português.
- Retornos antecipados são analisados individualmente e os caminhos encerrados não são confundidos com os caminhos que prosseguem.

## Limitações (importantes)

**Não é um borrow checker completo**, nem um modelo de segurança de memória comparável ao Rust.

- Sem empréstimos (borrowing), aliases, \`realoque\`, ponteiros globais/fields, ponteiros emprestados ou parâmetros \`altere ponteiro\`.
- Declarações de novos ponteiros dentro de ramificações e laços permanecem bloqueadas até existir análise lexical completa com *phi*/merge.
- Transferência/liberação em laços continua bloqueada; a V2.7 não calcula pontos fixos para várias iterações.
- Funções externas sem corpo e contratos explícitos não podem receber/devolver ownership.
- Não há detecção de overflow, nulidade de malloc, desreferências inválidas ou acessos de código C externo.
- O contrato de \`ponteiro\` ainda é uma convenção simples por tipo; não distingue formalmente ponteiros emprestados de próprios.
- No modo legado as mesmas garantias de ownership não se aplicam.

## Testes e incorporação

\`.github/workflows/v2-7-fluxo.yml\` executa as regressões V2.2–V2.7, compilando programas com GCC; o workflow V2.1 permanece ativo para textos. A V2.7 está em \`feature/v2-7-fluxo-transferencia\` no fork, separada do [PR #4](https://github.com/adilson889/PortugolTipado/pull/4). Alterações para o projeto original dependem de revisão e aprovação do mantenedor.
