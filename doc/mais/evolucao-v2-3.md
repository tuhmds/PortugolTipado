# PortugolTipado V2.3 — promoção numérica compartilhada

**Projeto original:** Adilson C. Rafael. **Proposta e direção da evolução:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com assistência de IA na implementação. Licença original preservada: GPL-3.0-or-later.

## Problema corrigido

O validador experimental e o transpilador possuíam tabelas distintas para decidir os tipos de expressões numéricas. Isso podia produzir resultados incoerentes no código C: por exemplo, o transpilador tratava `-3.5` como inteiro quando usado em `escreva`, levando a um formato incorreto de `printf`.

## Alterações

- Novo módulo puro `core/semantica/TiposNumericos.kt`, utilizado tanto pelo validador quanto pelo gerador C.
- Promoção compartilhada de tipos escalares em `+`, `-`, `*`, `/` e `%` (este último só para inteiros no modo seguro).
- Inferência de operadores unários, inclusive números decimais negativos e promoção de `inteiro curto`.
- Inferência de `texto[i]` como `caractere` no gerador C.
- Mantido o modo padrão/legado; tipagem segura continua opcional com `--tipagem=segura`.
- Testes V2.3: sete programas válidos compilados e executados com GCC e dois erros semânticos rejeitados; a mesma execução também roda a bateria V2.2 de 22 testes.

## Limitações

Esta é uma primeira **unificação das regras numéricas**, não a eliminação de toda a duplicação de inferência de tipos. Expressões de bibliotecas externas, estruturas e várias operações ainda exigem trabalho. O gerador C preserva o comportamento legado para casos cuja inferência falha, e `long` e outros tipos nativos variam entre plataformas.

Este desenvolvimento permanece em branch separada para não alterar o PR #4 em rascunho sem revisão do mantenedor.

## Verificação

Workflow: `.github/workflows/v2-3-inferencia.yml`.
Testes: `tests/v2-3-inferencia.sh` e `tests/v2-2-tipos.sh`.
