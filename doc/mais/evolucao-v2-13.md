# PortugolTipado V2.13 — assinaturas antecipadas para inferência entre funções e módulos

**Projeto original:** Adilson C. Rafael. **Proposta e direção experimental:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com implementação assistida por IA. Licença GPL do projeto preservada.

## Objetivo

A V2.12 introduziu `seja`, mas o parser reconhecia apenas assinaturas de funções **já declaradas**. A V2.13 acrescenta uma passagem preliminar conservadora para identificar tipos de retorno antes de analisar os corpos, e prepara as assinaturas dos módulos incluídos antes da inferência do arquivo principal.

Isso permite:

~~~port
funcao iniciarCalculo(): inteiro {
    seja resultado = triplo(4)
    retorne resultado
}

funcao triplo(inteiro n): inteiro {
    retorne n * 3
}

funcao inicio() {
    escreva(iniciarCalculo())
}
~~~

Também permite que `seja resultado = executar(5)` obtenha o tipo de retorno de uma função declarada no módulo local `inclua "biblioteca"`, e que um módulo utilize `seja` com função importada de outro módulo.

## Como foi implementado

- O parser examina, em uma primeira passagem, **cabeçalhos de funções no nível global**. Ignora corpos e estruturas; não cria tipos dinâmicos nem atribui `inteiro` por aproximação.
- Uma função declarada posteriormente no mesmo arquivo passa a disponibilizar seu tipo de retorno no início da análise.
- O compilador faz uma leitura preliminar somente dos `inclua` no nível global e solicita ao resolvedor as assinaturas dos módulos.
- O resolvedor também prepara assinaturas transitivas quando um módulo inclui outro.
- Após a leitura preliminar, o programa inteiro continua sendo analisado normalmente por parser, resolução, validador, tipagem segura e ownership; nenhuma validação foi removida.

## Limitações e riscos

A inferência é propositalmente conservadora: funções desconhecidas, tipos de retorno `vazio`, aritmética incompatível, arrays sem tipo e `realoque` sem contrato não ganham tipos presumidos.

A preparação de módulos implica uma fase extra de leitura/resolução. Ainda precisamos avaliar o custo de processamento para projetos grandes e aprimorar o diagnóstico de assinaturas conflitantes. A tabela de assinaturas antecipadas é restrita a **tipos de retorno**: aridade, tipos dos parâmetros e contratos externos continuam sendo verificados pelo fluxo normal de validação.

A inferência de tipos não é prova de segurança de memória, nem substitui a revisão de fluxos complexos.

## Testes e colaboração

O teste `tests/v2-13-assinaturas.sh` exercita funções posteriores, tipos numéricos e texto, módulos locais, dependências transitivas, módulos que usam `seja` com imports, chamadas desconhecidas, retorno `vazio` e erros de módulo inexistente.

O workflow `.github/workflows/v2-13-assinaturas.yml` também executa as regressões V2.2–V2.12.2 com Kotlin e GCC. A regressão V2.1 de segurança de textos continua ativa.

Esta contribuição permanece na branch `feature/v2-13-assinaturas-antecipadas` do fork e **não altera** o [PR #4](https://github.com/adilson889/PortugolTipado/pull/4) aberto para revisão no repositório original. Considerar aprovada somente após as suítes do último commit passarem.
