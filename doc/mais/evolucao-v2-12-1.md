# PortugolTipado V2.12.1 — correção de escopos em escolha

**Autor do projeto original:** Adilson C. Rafael. **Proposta da evolução e revisão:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com assistência de IA. Licença GPL do projeto preservada.

## Falha confirmada

Embora os workflows originais da V2.12 tenham passado, os casos de `escolha` não cobriam duas variáveis com o **mesmo nome** em braços distintos.

O parser e os validadores tratavam `caso` e `casocontrario` como escopos separados, mas o transpilador emitia labels C `case` sem blocos internos. Duas declarações como `seja dado = 1` em um caso e `seja dado = "texto"` em outro podiam provocar **redefinição de variável no GCC**, apesar de a análise de tipos considerá-las válidas.

## Correção

O gerador de C agora envolve **cada braço da escolha num bloco `{ ... }`**, inclusive `default`, preservando o escopo lexical já usado na análise e os tipos independentes de cada declaração.

A correção **não insere `break`** automaticamente: mantém a semântica existente de queda para o próximo caso (*fallthrough*). Por isso, os testes específicos da escolha desativam apenas o aviso GCC `-Wimplicit-fallthrough`, mantendo `-Werror` para todos os outros avisos. Uma eventual mudança na semântica de `pare` e do fallthrough deve ser tratada como decisão de linguagem, separadamente.

## Verificação

A suíte `tests/v2-12-1-escopos-escolha.sh` inclui nomes repetidos com tipos diferentes, casos e default, `const seja` e variável homônima declarada após a escolha. O workflow `.github/workflows/v2-12-1-escolha.yml` executa as regressões V2.2–V2.12 mais estes testes. A segurança de textos V2.1 também é verificada.

**Limitação:** testes verdes comprovam apenas os cenários executados. A V2.12 ainda não infere funções declaradas posteriormente, nem todos os tipos de módulos. Há outros cantos da linguagem que precisam de testes adversariais. Esta correção fica numa branch nova no fork e não altera o PR #4 do projeto original.
