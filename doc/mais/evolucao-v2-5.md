# PortugolTipado V2.5 — tipos nativos de C

**Projeto original:** Adilson C. Rafael. **Proposta e direção da evolução experimental:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com assistência de IA na implementação. Licença GPL do projeto original preservada.

## Problema

C usa `size_t` (largura e sinal dependentes da plataforma) para comprimentos e `void*` para endereços genéricos. Tratá-los como `inteiro` pode gerar formatos incompatíveis em `printf`, conversões implícitas perigosas e operações sem significado sobre ponteiros.

## Escopo implementado

- Dois novos tipos **internos** na AST: `TAMANHO` (C: `size_t`) e `PONTEIRO_OPACO` (C: `void*`).
- Mapa compartilhado `TiposNativosC` para retornos de `tamanho`/strlen, `aloque`/malloc, `aloque_zerado`/calloc, `realoque`/realloc, `busque`/bsearch e `libere`/free.
- Saída tipada de `size_t` usa `%zu`, inclusive em expressões; não precisa mais de exceção textual específica para `tamanho(...)`.
- A soma `size_t + size_t` e `size_t + literal int não negativo` recebe resultado `size_t`. Multiplicações, subtrações e misturas que não tenham segurança demonstrada continuam rejeitadas pelo modo seguro.
- Ponteiros opacos não participam de aritmética numérica nem podem ser impressos diretamente no modo seguro.
- Índices de arrays podem receber `size_t`.
- Os modos anteriores e o comportamento legado ficam preservados, salvo a melhoria de inferência de retorno das funções nativas.

## Exemplos

~~~port
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") + 1) // imprime 4, usa %zu em C
}
~~~

~~~port
funcao inicio() {
    libere(aloque(16)) // retorno void* tratado como ponteiro opaco
}
~~~

## Limitações e trabalho futuro

- **Não é um sistema completo de ownership.** A implementação não verifica vazamentos, uso após liberação, null pointers, realocação nem a capacidade de buffers dinâmicos.
- `TAMANHO` e `PONTEIRO_OPACO` são tipos **internos do compilador** nesta versão. Não foram introduzidas novas palavras-chave públicas.
- `size_t` é unsigned e sua largura varia entre plataformas; neste estágio aceitamos somente somas limitadas, sem supor tamanho fixo de 32 ou 64 bits.
- Uma soma aceita ainda pode transbordar `size_t` em tempo de execução; esta versão **não** implementa checagem de overflow.
- A verificação de argumentos de funções nativas não equivale ainda a um modelo completo de suas assinaturas em C.
- `escreva(aloque(...))` é recusado; não há serialização segura para endereços nativos.
- A unificação total dos modelos de tipos do validador e do gerador C continua pendente.
- Esta branch é independente do [PR #4](https://github.com/adilson889/PortugolTipado/pull/4) e não deve ser confundida com uma mudança já incorporada ao projeto original.

## Verificação

O workflow `.github/workflows/v2-5-tipos-nativos.yml` verifica regressões V2.2–V2.5 com Kotlin e GCC; a regressão V2.1 de textos também é executada nesta branch. Os testes específicos estão em `tests/v2-5-tipos-nativos.sh`.
