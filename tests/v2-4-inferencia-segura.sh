#!/usr/bin/env bash
set -euo pipefail
PORT_JAR="core/build/libs/port.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

aceitar() {
    local nome="$1" esperado="$2" programa="$3"
    printf "%s\n" "$programa" > "$TMP/$nome.port"
    java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port"
    gcc -Wall -Wextra -Werror -o "$TMP/$nome" "$TMP/$nome.c" -lm
    local obtido
    obtido="$("$TMP/$nome")"
    if [ "$obtido" != "$esperado" ]; then
        echo "FALHA: $nome: esperado $esperado, obtido $obtido" >&2
        cat "$TMP/$nome.c" >&2
        exit 1
    fi
    echo "OK executado: $nome"
}

rejeitar() {
    local nome="$1" mensagem="$2" programa="$3"
    printf "%s\n" "$programa" > "$TMP/$nome.port"
    if java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port" >"$TMP/$nome.log" 2>&1; then
        echo "FALHA: $nome aceito no modo seguro" >&2
        exit 1
    fi
    if ! grep -q "$mensagem" "$TMP/$nome.log"; then
        echo "FALHA: $nome sem diagnostico esperado ($mensagem)" >&2
        cat "$TMP/$nome.log" >&2
        exit 1
    fi
    echo "OK rejeitado: $nome"
}

aceitar "constante_pi" "3.141593" '
inclua matematica
funcao inicio() {
    escreva(PI)
}'

aceitar "logaritmo" "0.000000" '
inclua matematica
funcao inicio() {
    escreva(logaritmo(1.0))
}'

aceitar "raiz" "3.000000" '
inclua matematica
funcao inicio() {
    escreva(raiz(9.0))
}'

aceitar "espaco_nao" "0" '
inclua caracteres
funcao inicio() {
    escreva(eh_espaco('\''X'\''))
}'

aceitar "tamanho_direto" "3" '
inclua texto
funcao inicio() {
    escreva(tamanho("Ana"))
}'

aceitar "retorno_texto" "Ola" '
funcao cumprimentar(): texto {
    retorne "Ola"
}
funcao inicio() {
    escreva(cumprimentar())
}'

rejeitar "escreva_array" "escreva nao aceita" '
funcao inicio() {
    inteiro valores[2] = {1, 2}
    escreva(valores)
}'

rejeitar "escreva_estrutura" "escreva nao aceita" '
estrutura Pessoa {
    inteiro idade
}
funcao inicio() {
    estrutura Pessoa p = {20}
    escreva(p)
}'

rejeitar "escreva_vazio" "escreva nao aceita" '
funcao acao(): vazio {
    escreva("Ola")
}
funcao inicio() {
    escreva(acao())
}'

rejeitar "tamanho_subtracao_nao_validada" "tipagem segura" '
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") - 1)
}'

# Compatibilidade: o modo legado continua gerando C para o mesmo caso.
cat > "$TMP/legado.port" <<'EOF'
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") - 1)
}
EOF
java -jar "$PORT_JAR" --tipagem=legada -o "$TMP/legado.c" "$TMP/legado.port"
test -s "$TMP/legado.c"
echo "OK: modo legado continua disponivel"

echo "V2.4: regressao aprovada"
