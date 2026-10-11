# PortugolTipado V2.12 — `seja` com inferência local de tipo

**Projeto original:** Adilson C. Rafael. **Proposta e direção da evolução experimental:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com assistência de IA. Licença GPL-3.0-or-later preservada.

## Por que esta versão existe?

A V2.12 acrescenta uma maneira mais simples de declarar variáveis **sem repetir o tipo à esquerda**, mantendo a tradução para C estaticamente tipado. A linguagem continua compilada/transpilada; `seja` não introduz um tipo dinâmico.

~~~port
funcao inicio() {
    seja idade = 42
    seja altura = 1.75
    seja nome = "Ana"
    seja adulto = idade >= 18
    escreva(nome, " ", idade)
}
~~~

O parser converte cada `seja` imediatamente numa `DeclaracaoVariavel` da AST existente, com o tipo concreto calculado. Assim, validadores, análise de ownership e transpilador continuam trabalhando com uma única representação tipada.

## Regras

- `seja x = 42` infere `inteiro`; `seja x = 1.5` infere `duplo`; textos, caracteres e valores lógicos também são reconhecidos.
- Operações aritméticas usam `TiposNumericos`, as mesmas regras de promoção compartilhadas pelos validadores. Comparações válidas inferem `logico`.
- Variáveis declaradas anteriormente no escopo são reconhecidas; parâmetros de funções também.
- Funções **já declaradas no mesmo arquivo** podem fornecer seus tipos de retorno. Retornos nativos modelados em `TiposNativosC`, como `tamanho` (`size_t`) e `aloque` (`void*`), são igualmente reconhecidos.
- `const seja x = 12` é uma declaração constante, equivalente a `const inteiro x = 12`.
- `seja i = 0` funciona como inicializador de `para` e a variável não permanece visível depois do laço.
- Declarações em blocos internos usam o escopo lexical correto, incluindo `se/senao`, `enquanto`, `para`, `para cada` e braços de `escolha`.
- `seja` gera tipos concretos tanto no modo padrão legado como no modo `--tipagem=segura`.

## Rejeições intencionais

Não aceita `seja valor` sem inicializador; arrays e estruturas por inferência; chamadas a funções ainda não declaradas no próprio arquivo; nem expressões com tipo incerto ou operações inválidas, como somar texto e número. Nessas condições, gera erro indicando que o tipo precisa ser declarado explicitamente.

**Limitação atual:** esta primeira versão não possui uma fase global de resolução de tipos antes do parser. Portanto, funções declaradas posteriormente, retornos de bibliotecas que ainda não estejam mapeadas e certas expressões complexas podem exigir uma declaração explícita de tipo. `seja` não deve presumir `inteiro` como fallback. Regras mais amplas de inferência poderão vir após consolidação de uma tabela global de assinaturas.

## Testes e colaboração

O script `tests/v2-12-seja.sh` cobre declarações e execuções em GCC, escopos, funções, ponteiros, arrays rejeitados e tipagem incompatível; o workflow `.github/workflows/v2-12-seja.yml` também executa V2.2–V2.11. A regressão de segurança de textos V2.1 segue ativa.

Esta branch é independente do [PR #4](https://github.com/adilson889/PortugolTipado/pull/4) que está em revisão no repositório original. O status de aprovado depende do GitHub Actions no último commit.
