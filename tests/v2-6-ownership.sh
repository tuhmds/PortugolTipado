#!/usr/bin/env bash
set -euo pipefail
PORT_JAR="core/build/libs/port.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

aceitar() {
  local nome="$1" esperado="$2" fonte="$3"
  printf '%s\n' "$fonte" > "$TMP/$nome.port"
  java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port"
  gcc -Wall -Wextra -Werror -o "$TMP/$nome" "$TMP/$nome.c" -lm
  local resultado
  resultado="$("$TMP/$nome")"
  if [ "$resultado" != "$esperado" ]; then
    printf "FALHOU %s: esperado [%s], recebido [%s]\n" "$nome" "$esperado" "$resultado" >&2
    exit 1
  fi
  echo "OK executado: $nome"
}

rejeitar() {
  local nome="$1" trecho="$2" fonte="$3"
  printf '%s\n' "$fonte" > "$TMP/$nome.port"
  if java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port" > "$TMP/$nome.log" 2>&1; then
    echo "FALHOU: $nome compilou quando deveria ser rejeitado" >&2
    exit 1
  fi
  if ! grep -q "$trecho" "$TMP/$nome.log"; then
    echo "FALHOU: $nome nao apresentou diagnostico '$trecho'" >&2
    cat "$TMP/$nome.log" >&2
    exit 1
  fi
  echo "OK rejeitado: $nome"
}

aceitar "aloca_libera" "ok" '
funcao inicio() {
    ponteiro bloco = aloque(16)
    libere(bloco)
    escreva("ok")
}'

aceitar "aloca_zerado" "dois" '
funcao inicio() {
    ponteiro primeiro = aloque_zerado(4, 8)
    ponteiro segundo = aloque(12)
    libere(segundo)
    libere(primeiro)
    escreva("dois")
}'

aceitar "libera_direto" "direto" '
funcao inicio() {
    libere(aloque(16))
    escreva("direto")
}'

aceitar "funcao_com_dono_local" "local" '
funcao rotina(): vazio {
    ponteiro memoria = aloque(16)
    libere(memoria)
}
funcao inicio() {
    rotina()
    escreva("local")
}'

aceitar "condicao_sem_ponteiro" "ok" '
funcao inicio() {
    ponteiro bloco = aloque(4)
    se (verdadeiro) {
        escreva("ok")
    }
    libere(bloco)
}'

rejeitar "duplo_libere" "liberacao duplicada" '
funcao inicio() {
    ponteiro bloco = aloque(16)
    libere(bloco)
    libere(bloco)
}'

rejeitar "uso_apos_libere" "uso apos libere" '
funcao inicio() {
    ponteiro bloco = aloque(16)
    libere(bloco)
    escreva(bloco)
}'

rejeitar "vazamento" "possivel vazamento" '
funcao inicio() {
    ponteiro bloco = aloque(16)
    escreva("esqueci")
}'

rejeitar "vazamento_retorno" "possivel vazamento" '
funcao inicio() {
    ponteiro bloco = aloque(16)
    retorne
}'

rejeitar "alias" "uso apos transferencia" '
funcao inicio() {
    ponteiro bloco = aloque(16)
    ponteiro copia = bloco
    libere(bloco)
}'

rejeitar "reatribuicao" "reatribuicao" '
funcao inicio() {
    ponteiro bloco = aloque(16)
    bloco = aloque(32)
    libere(bloco)
}'

rejeitar "ponteiro_sem_dono" "deve ser inicializado" '
funcao inicio() {
    ponteiro bloco
    libere(bloco)
}'

rejeitar "libera_condicional" "estados de posse divergentes" '
funcao inicio() {
    ponteiro bloco = aloque(16)
    se (verdadeiro) {
        libere(bloco)
    }
}'

aceitar "declara_condicional" "" '
funcao inicio() {
    se (verdadeiro) {
        ponteiro bloco = aloque(16)
        libere(bloco)
    }
}'

rejeitar "realoque_nao_modelado" "realoque ainda nao" '
funcao inicio() {
    ponteiro bloco = realoque(aloque(16), 32)
    libere(bloco)
}'

aceitar "ponteiro_argumento" "parametro" '
funcao outra(ponteiro bloco): vazio {
    libere(bloco)
}
funcao inicio() {
    ponteiro valor = aloque(16)
    outra(valor)
    escreva("parametro")
}'

aceitar "ponteiro_retorno" "retorno" '
funcao criar(): ponteiro {
    retorne aloque(16)
}
funcao inicio() {
    ponteiro bloco = criar()
    libere(bloco)
    escreva("retorno")
}'

echo "V2.6: regressao de ownership concluida."
