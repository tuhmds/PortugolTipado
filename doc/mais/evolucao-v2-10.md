# V2.10 — auditoria estrutural do compilador e ownership

**Projeto original:** Adilson C. Rafael. **Proposta e direção da contribuição experimental:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com assistência de IA na implementação. Licença GPL do projeto original preservada.

## Objetivo

Depois das versões V2.1 a V2.9, a prioridade foi revisar as interações entre análise semântica, tabelas de símbolos e código C gerado, antes de introduzir novas funcionalidades.

## Defeitos e correções

- **Estado entre funções:** o transpilador acumulava símbolos locais/paramétricos de funções anteriores em `tiposVariaveis`. A V2.10 salva e restaura a tabela e os buffers ao compilar cada função, inclusive quando ocorre um erro.
- **Escopo do cabeçalho `para`:** uma variável declarada no inicializador do laço ficava registrada depois do laço na inferência de tipos C. A V2.10 restaura símbolos e buffers ao sair do `para`.
- **Sombreamento não ponteiro / ponteiro:** a análise de ownership rastreava apenas os nomes de ponteiros, mesmo quando uma declaração de `inteiro` ou outro tipo ocultava o ponteiro dentro do escopo. Agora o nome externo fica invisível durante o bloco, mas sua posse continua registrada por identificador e é restaurada ao sair. Uma tentativa de `libere` sobre a variável não ponteiro não é confundida com a liberação do recurso externo.
- **Escopo de `para` no ownership:** nomes de variáveis do inicializador também são restaurados após o laço, evitando perder a identidade de um ponteiro externo.
- **Duplicação:** os arquivos antigos `AnaliseOwnershipV26`, `V27` e `V28` foram retirados da árvore da branch; o analisador ativo é `AnaliseOwnershipV29`. O histórico das versões permanece no Git.

## Testes

O arquivo `tests/v2-10-auditoria.sh` verifica programas válidos e inválidos que envolvem sombreamento, `para`, empréstimos e passagem de funções. `.github/workflows/v2-10-auditoria.yml` executa a bateria acumulada V2.2–V2.10 e a regressão de buffers V2.1 segue separada.

## Limitações ainda abertas

**A revisão não equivale a uma prova formal de segurança.** Pontos pendentes incluem: unificar a tabela de símbolos do analisador e do gerador C, verificar todos os fluxos de controle com múltiplas saídas de laços, modelar alocação dinâmica falha, ponteiros nulos e `realoque`; adicionar testes adversariais e plataformas diferentes do Linux/GCC. Existe código C externo sem contratos de ownership, e alguns erros ainda dependem do GCC para serem identificados.

A contribuição está na branch `feature/v2-10-auditoria-estrutural` do fork e **não altera automaticamente o [PR #4](https://github.com/adilson889/PortugolTipado/pull/4)** do repositório original.
