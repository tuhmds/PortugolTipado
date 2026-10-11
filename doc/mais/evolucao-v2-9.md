# PortugolTipado V2.9 — saídas de laços e empréstimos (experimental)

**Projeto original:** Adilson C. Rafael. **Proposta e direção das melhorias:** Artur Mendes Vieira Alves ([@tuhmds](https://github.com/tuhmds)), com assistência de IA na implementação. A licença GPL do projeto original é preservada.

## O que muda

Na análise de ownership opcional (`--tipagem=segura`), `pare` e `continue` agora registram saída antecipada do corpo de laços. Essa saída é aceita **somente quando** todos os ponteiros criados na iteração já estiverem liberados ou transferidos, e a posse dos recursos anteriores ao laço não tiver mudado. Não permitimos `retorne` dentro do laço nem operações de ownership em incrementos de `para`.

Em `se/senao`, cada caminho é examinado. Se alguns caminhos saem do laço e outros seguem, o analisador continua verificando os caminhos normais e aplica as invariantes aos caminhos de saída.

## Empréstimo somente leitura com const ponteiro

Funções com parâmetro `const ponteiro` recebem um **empréstimo temporário**, ao contrário do parâmetro `ponteiro` simples, que consome a posse. O empréstimo:

- Não libera nem transfere a posse.
- Pode ser encaminhado a outra função que também recebe `const ponteiro`.
- Não pode escapar por retorno, cópia, alocação de alias nem por passagem a função que consome ownership.
- Não pode ser liberado, reatribuído ou emprestado depois da liberação/transferência do dono original.
- Não aceita ponteiros temporários de alocação direta: o dono deve existir numa variável local válida.

Exemplo:

~~~port
funcao observar(const ponteiro endereco): vazio {
    escreva("Observado")
}

funcao inicio() {
    ponteiro recurso = aloque(16)
    observar(recurso)  // emprestimo nao transfere posse
    libere(recurso)    // o dono ainda libera
}
~~~

A forma C gerada para o parâmetro é `const void*`. Isso **não** adiciona acesso tipado aos dados apontados nem garante ausência de mutação indireta em código C externo. É um contrato conservador válido apenas para funções analisadas com corpo disponível.

## Limites conhecidos

**Não é um borrow checker completo.** Não implementamos referências com lifetime arbitrário, retorno de emprestados, empréstimo mutável, ponteiros em structs, aliases, `realoque`, controle de ponteiros nulos, falhas de `malloc`, prova de ausência de overflow nem suporte a todas as saídas de `escolher`.

A análise de laços usa um estado simbólico e exige invariantes de ownership; algumas formas válidas ainda serão recusadas. Algumas construções com saída simultânea em diferentes ramos são tratadas conservadoramente.

O modo legado continua disponível, mas sem essas garantias. A V2.9 não modifica a branch do PR #4 enviado ao mantenedor do projeto original.

## Testes

O workflow `.github/workflows/v2-9-ownership.yml` compila Kotlin e executa regressões V2.2–V2.9 com GCC. O arquivo `tests/v2-9-saidas-emprestimos.sh` inclui casos de saída antecipada, ponteiros locais, empréstimos e rejeições, e o workflow V2.1 valida os buffers de texto.

A versão somente será considerada aprovada após a conclusão bem-sucedida das duas suites no GitHub Actions.
