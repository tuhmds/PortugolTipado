# PortugolTipado V2.12.2 — auditoria adversarial de `para cada`

**Autor do projeto original:** Adilson C. Rafael. **Proposta e direção desta contribuição experimental:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com assistência de IA na implementação. Licença GPL-3.0-or-later preservada.

## Defeito encontrado na revisão

O transpilador reconhecia corretamente o escopo de `seja` e dos parâmetros, mas, ao gerar `para cada`, fazia `tiposVariaveis[nomeElemento] = tipoElemento` sem restaurar os tipos das variáveis externas depois de fechar o laço.

Assim, quando o nome do elemento de uma iteração ocultava uma variável externa de **outro tipo**, a saída C podia emitir um formato de `printf` incompatível com o valor real. Isso é mais grave que um erro de mensagem: a passagem de um `double` a `printf("%d", ...)`, por exemplo, produz comportamento indefinido.

O código foi corrigido para salvar e restaurar **os tipos e os buffers de texto** com `try/finally`, inclusive quando o gerador encontra algum erro.

## Segundo risco: conflito com identificador sintetizado

O `para cada` usava sempre um índice C chamado `_i`. O programador também podia nomear o elemento `_i`, fazendo o próprio inicializador C referenciar uma variável local com o mesmo nome em vez do índice, podendo usar um valor não inicializado.

A geração agora utiliza nomes incrementais `port_indice_cada_N`, verificando a colisão com o nome do elemento e os identificadores atualmente registrados pelo transpilador. Esse mecanismo **não é um sistema global de nomes reservados ou higiene de macros**: colisões mais complexas em futuras construções ainda devem ser auditadas.

## Testes adicionados

`tests/v2-12-2-para-cada.sh` cobre sombreamento de `duplo`, `texto` e `caractere`, parâmetros, laços aninhados, variáveis chamadas `_i`, nomes sintetizados usados pelo usuário, execução no modo legado e no modo seguro, uso de `seja` após `para cada` e rejeição de iteração sobre algo que não seja array.

O script exige geração C e compilação com GCC `-std=c11 -Wall -Wextra -Werror -Werror=format`; todos os programas válidos são executados e suas saídas comparadas. O workflow `.github/workflows/v2-12-2-para-cada.yml` também roda as regressões V2.2–V2.12.1; a segurança de textos V2.1 continua coberta em separado.

**Não equivale a comprovação formal de segurança.** `para cada`, `escolha`, inferência, ownership e funções externas ainda precisam de revisão adversarial, inclusive com AddressSanitizer/UBSan nos cenários de memória.

## Política de contribuição

Todas as mudanças ficaram na branch `feature/v2-12-2-revisao-para-cada` do fork, a partir da V2.12.1. O [PR #4](https://github.com/adilson889/PortugolTipado/pull/4) do repositório original não foi modificado. Somente marcar a revisão como aprovada após os testes no último commit.
