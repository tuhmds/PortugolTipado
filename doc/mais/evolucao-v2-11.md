# PortugolTipado V2.11 — matriz adversarial e validação multiplataforma

**Projeto original:** Adilson C. Rafael. **Proposta, direção e testes da evolução experimental:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com implementação assistida por IA. A licença GPL do projeto original é preservada.

## Objetivo

Confrontar o modo `--tipagem=segura` com casos adversariais de ownership, empréstimo e texto, e verificar que os arquivos C gerados funcionam em diferentes compiladores e sistemas. Os testes não demonstram correção ou segurança universal.

## Bateria adversarial

O script `tests/v2-11-adversarial.sh` executa seis programas válidos que incluem:

- criação, empréstimo e transferência de posse entre funções;
- liberação em ambos os caminhos de um `se/senao` e sombreamento de nomes;
- alocação/liberação em várias iterações;
- `pare` e `continue` com recursos locais já liberados;
- formato `size_t` (especificador `%zu`) e cópia/concatenação de texto.

Também exige a rejeição, **antes de gerar C**, de treze programas inválidos: dupla liberação, uso após transferência, empréstimo após liberação, tentativa de liberar empréstimo, empréstimo e transferência simultâneos, vazamento no escopo/função, saída de laço com recursos vivos, estados divergentes entre ramos, `libere` sobre identificador sombreado por inteiro, aritmética de ponteiros e `realoque` sem contrato.

## Ambientes de verificação

O workflow `.github/workflows/v2-11-matriz.yml` tem duas etapas independentes:

1. **Regressão acumulada:** Kotlin/Java 21 e programas C com GCC em Ubuntu. Executa as baterias V2.2–V2.10 e o novo conjunto adversarial V2.11. O workflow separado V2.1 continua testando segurança de textos.
2. **Matriz nativa:** Linux com GCC, Linux com Clang e `-fsanitize=address,undefined` (ASan/UBSan), macOS com Clang e Windows com MinGW-w64 GCC, executando os mesmos casos V2.11.

Os binários válidos são executados e suas saídas comparadas com o resultado esperado. Programas inválidos precisam falhar com diagnóstico de ownership/tipagem em português.

### Limitações dos sanitizadores

ASan observa acessos inválidos e parte dos vazamentos **em tempo de execução** de casos exercitados; UBSan detecta categorias de comportamento indefinido previstas em sua instrumentação. Nenhum deles prova ausência de problemas em caminhos não executados. A instrumentação principal é no Linux/Clang; macOS e Windows têm aqui testes de compilação e execução, **não necessariamente ASan e UBSan**.

O analisador atual não garante prevenção de ponteiros nulos, overflow de alocação, código C externo, uso de `realoque`, empréstimos mutáveis ou todos os caminhos de fluxo. Diferentes tipos nativos de C podem variar por plataforma.

## Política de colaboração

Todo o trabalho fica inicialmente na branch `feature/v2-11-matriz-sanitizadores` do fork, sem modificar o [PR #4](https://github.com/adilson889/PortugolTipado/pull/4). Antes de enviar novas funcionalidades ao mantenedor, é necessário revisar o código e o escopo dos PRs, preservar a autoria original e manter histórico rastreável. Não declarar a matriz aprovada até verificar o último commit em todos os sistemas.
