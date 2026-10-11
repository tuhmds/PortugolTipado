#!/usr/bin/env bash
set -euo pipefail
PORT_JAR="core/build/libs/port.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

aceitar() {
  local caso="$1"
  local esperado="$2"
  local codigo="$3"
  printf '%s\n' "$codigo" > "$TMP/$caso.port"
  java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$caso.c" "$TMP/$caso.port"
  gcc -Wall -Wextra -Werror -o "$TMP/$caso" "$TMP/$caso.c" -lm
  local recebido
  recebido="$("$TMP/$caso")"
  if [ "$recebido" != "$esperado" ]; then
    echo "ERRO em $caso: esperado '$esperado', recebido '$recebido'" >&2
    cat "$TMP/$caso.c" >&2
    exit 1
  fi
  echo "OK: $caso"
}

rejeitar() {
  local caso="$1"
  local codigo="$2"
  printf '%s\n' "$codigo" > "$TMP/$caso.port"
  if java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$caso.c" "$TMP/$caso.port" >"$TMP/$caso.log" 2>&1; then
    echo "ERRO: caso invalido $caso foi aceito" >&2
    exit 1
  fi
  grep -q 'tipagem segura' "$TMP/$caso.log" || {
    cat "$TMP/$caso.log" >&2
    echo "ERRO: $caso rejeitado sem diagnostico semantico" >&2
    exit 1
  }
  echo "OK rejeitado: $caso"
}

aceitar "decimal_unario" "-3.500000" '
funcao inicio() {
    escreva(-3.5)
}'

aceitar "decimal_com_sinal" "-3.250000" '
funcao inicio() {
    duplo valor = 3.25
    escreva(-valor)
}'

aceitar "promocao_decimal" "3.500000" '
funcao inicio() {
    escreva(1 + 2.5)
}'

aceitar "promocao_longo" "11" '
funcao inicio() {
    inteiro longo a = 5
    inteiro b = 6
    escreva(a + b)
}'

aceitar "curto_negativo" "-2" '
funcao inicio() {
    inteiro curto a = 2
    escreva(-a)
}'

aceitar "indice_texto" "n" '
funcao inicio() {
    texto nome = "Ana"
    escreva(nome[1])
}'

aceitar "negacao_logica" "0" '
funcao inicio() {
    escreva(!verdadeiro)
}'

rejeitar "texto_em_aritmetica" '
funcao inicio() {
    escreva("Ana" + 2)
}'

rejeitar "numerico_misturado" '
funcao inicio() {
    inteiro positivo x = 2
    inteiro y = 3
    escreva(x + y)
}'

echo "Regressao V2.3 concluida."
