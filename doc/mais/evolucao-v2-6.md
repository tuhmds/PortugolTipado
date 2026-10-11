# PortugolTipado V2.6 — Ownership linear (experimental)

**Projeto original:** Adilson C. Rafael. **Proposta e direção das melhorias:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com assistência de IA na implementação. GPL-3.0-or-later.

## Objetivo

Complementar a tipagem segura experimental com uma primeira verificação estática de recursos alocados em memória nativa. A V2.6 oferece uma **análise conservadora de posse local em fluxo linear**, não um sistema de ownership completo.

## Sintaxe e uso

A palavra-chave pública `ponteiro` agora representa um `void*` opaco do C. Use o modo seguro:

~~~port
funcao inicio() {
    ponteiro bloco = aloque(16)
    libere(bloco)
    escreva("memoria liberada")
}
~~~

~~~sh
java -jar core/build/libs/port.jar --tipagem=segura -o programa.c programa.port
~~~

O compilador acompanha a variável `bloco` como **ativa** e depois como **liberada**. Isso permite detectar, em blocos lineares simples:

- Segunda chamada de `libere(bloco)`, ou seja, liberação duplicada.
- Referência a `bloco` depois de sua liberação (uso após `libere`).
- Saída do bloco/função com alocação ainda ativa (possível vazamento).
- Atribuição repetida que perde a referência original, ou alias como `ponteiro copia = bloco`.
- Ponteiro não inicializado com um alocador reconhecido.

É permitido `libere(aloque(16))` e criar dois ou mais recursos locais, liberando cada um exatamente uma vez.

## Limites deliberados

- O modelo só aceita inicialização com `aloque` (malloc) ou `aloque_zerado` (calloc) e liberação por `libere` (free), no mesmo fluxo linear.
- **Não** aceita ainda `realoque`, retorno de ponteiro, parâmetros ponteiro, transferências entre funções ou ponteiros globais gerenciados.
- Recusa operações de criação/liberação de ponteiros em `se`, `enquanto`, `para`, `escolha` e outros fluxos complexos, até termos análise de caminhos.
- Não impede falhas de `malloc`, acesso a ponteiro nulo, estouro de buffer ou escrita em memória inválida por código C externo.
- A verificação está ativa somente com `--tipagem=segura`. O modo legado não recebe garantias de ownership.
- O novo tipo `ponteiro` é intencionalmente opaco: a V2.6 não disponibiliza aritmética de ponteiros, desreferência tipada ou controle de tamanho de memória dinâmica.
- A análise não trata todos os efeitos colaterais possíveis de código C externo e não equivale ao borrow checker do Rust.

## Testes

O workflow `.github/workflows/v2-6-ownership.yml` compila Kotlin e executa as regressões V2.2–V2.6, incluindo programas C compilados com GCC. A bateria está em `tests/v2-6-ownership.sh`; o workflow separado da V2.1 verifica segurança dos textos.

Esta branch (`feature/v2-6-ownership-basico`) **não altera** o PR #4 enviado ao projeto original. Qualquer incorporação depende da revisão do mantenedor.
