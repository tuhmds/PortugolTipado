# PortugolTipado V2.4 — tipos desconhecidos sem fallback silencioso

Projeto original: **Adilson C. Rafael**. Proposta e direção das melhorias experimentais: **Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds))**, com assistência de IA na implementação. Licença GPL do projeto original preservada.

## Objetivo

O gerador C assumia que qualquer expressão cujo tipo não fosse identificado era um `inteiro`. Isso podia gerar especificadores incorretos em `printf`, mascarar erros de tipos ou produzir programas com comportamento indefinido.

## O que foi alterado

- A CLI passa o modo `--tipagem=segura` ao `Transpilador`. O modo legado mantém a compatibilidade e sua política anterior.
- No modo seguro, tipos desconhecidos de identificadores, campos, chamadas de função, operações numéricas, unárias e outras expressões agora resultam em diagnóstico em português, com número da linha, em vez de um inteiro presumido.
- A operação `escreva(...)` recusa impressão direta de arrays, estruturas e expressões `vazio` em modo seguro.
- Constantes `PI` e `E` recebem tipo `duplo`, coerente com sua tradução para `M_PI` e `M_E` no C.
- São registradas assinaturas de retorno conhecidas de funções matemáticas, predicados de caracteres e outras funções padrão, reduzindo falsos erros no modo seguro.
- A compatibilidade com V2.2 e V2.3 é testada em GitHub Actions. Testes específicos compilam exemplos C com `gcc -Wall -Wextra -Werror` e conferem saídas/erros.

## Uso

~~~sh
java -jar core/build/libs/port.jar --tipagem=segura -o programa.c programa.port
~~~

## Limitações e decisões conscientes

- **Ainda não há inferência universal:** chamadas externas sem assinatura modelada não podem ser formatadas automaticamente com segurança.
- `tamanho(...)` usado diretamente em `escreva` continua recebendo `%zu`; em operações compostas, como `escreva(tamanho("Ana") + 1)`, a representação de `size_t` na AST ainda não existe, então o modo seguro rejeita a expressão até esse tipo ser modelado.
- A verificação continua opcional e ocorre após o validador tradicional. A inferência semântica e o gerador C ainda não foram totalmente unificados.
- Esta mudança não reivindica segurança completa da memória nem substitui a auditoria da geração C.
- Os novos testes verificam um conjunto de programas, não todas as combinações da linguagem.

## Organização das contribuições

Esta V2.4 foi desenvolvida em `feature/v2-4-inferencia-sem-fallback`, separada do [PR #4](https://github.com/adilson889/PortugolTipado/pull/4), que contém V2.1 e V2.2. Uma incorporação ao projeto original depende de revisão do mantenedor.
