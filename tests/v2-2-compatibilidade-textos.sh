#!/usr/bin/env bash
set -euo pipefail
JAR="core/build/libs/port.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

executar() {
  local nome="$1" esperado="$2" fonte="$3" entrada="${4:-}"
  printf '%s\n' "$fonte" > "$TMP/$nome.port"
  # Modo por omissao: tem de continuar a aceitar codigo anterior.
  java -jar "$JAR" -o "$TMP/$nome.c" "$TMP/$nome.port"
  gcc -Wall -Wextra -Werror -Wno-unused-parameter -o "$TMP/$nome" "$TMP/$nome.c" -lm
  local resultado
  resultado="$(printf '%s' "$entrada" | "$TMP/$nome")"
  if [ "$resultado" != "$esperado" ]; then
    echo "FALHA $nome: esperado [$esperado], obtido [$resultado]" >&2
    cat "$TMP/$nome.c" >&2
    exit 1
  fi
  echo "OK compatibilidade: $nome"
}

rejeitar_seguro() {
  local nome="$1" texto="$2" fonte="$3"
  printf '%s\n' "$fonte" > "$TMP/$nome.port"
  if java -jar "$JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port" >"$TMP/$nome.log" 2>&1; then
    echo "FALHA $nome: modo seguro nao rejeitou" >&2
    exit 1
  fi
  if ! grep -q "$texto" "$TMP/$nome.log"; then
    echo "FALHA $nome: mensagem esperada nao encontrada" >&2
    cat "$TMP/$nome.log" >&2
    exit 1
  fi
  if grep -Eiq 'buffer|capacidade conhecida' "$TMP/$nome.log"; then
    echo "FALHA $nome: mensagem contem jargao" >&2
    cat "$TMP/$nome.log" >&2
    exit 1
  fi
  echo "OK modo seguro: $nome"
}

executar "copia_texto_inicializado" "Bia!" '
inclua texto
funcao inicio() {
    texto nome = "Ana"
    copie(nome, "Bia")
    junte(nome, "!")
    escreva(nome)
}'

executar "leitura_texto_inicializado" "Bia" '
funcao inicio() {
    texto nome = "Ana"
    leia(nome)
    escreva(nome)
}' "$(printf 'Bia\n')"

executar "texto_parametro_por_valor" "Oi!" '
inclua texto
funcao mostrar(texto frase): vazio {
    copie(frase, "Oi")
    junte(frase, "!")
    escreva(frase)
}
funcao inicio() {
    mostrar("Ana")
}'

executar "copia_mesma_variavel" "AnaAna" '
inclua texto
funcao inicio() {
    texto nome = "Ana"
    junte(nome, nome)
    copie(nome, nome)
    escreva(nome)
}'

executar "copia_local_sem_inicializador" "Bia" '
inclua texto
funcao inicio() {
    texto nome
    copie(nome, "Bia")
    escreva(nome)
}'

rejeitar_seguro "leia_texto_inicializado" "Não é possível ler para este texto em modo seguro" '
funcao inicio() {
    texto nome = "Ana"
    leia(nome)
}'

rejeitar_seguro "copie_texto_inicializado" "Não é possível escrever neste texto em modo seguro" '
inclua texto
funcao inicio() {
    texto nome = "Ana"
    copie(nome, "Bia")
}'

rejeitar_seguro "junte_texto_inicializado" "Não é possível escrever neste texto em modo seguro" '
inclua texto
funcao inicio() {
    texto nome = "Ana"
    junte(nome, "Bia")
}'

echo "Compatibilidade de textos V2.2 verificada nos modos normal e seguro."
