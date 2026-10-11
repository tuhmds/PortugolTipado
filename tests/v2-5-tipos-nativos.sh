#!/usr/bin/env bash
set -euo pipefail
PORT_JAR="core/build/libs/port.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

aceitar() {
  local caso="$1" esperado="$2" codigo="$3"
  printf '%s\n' "$codigo" > "$TMP/$caso.port"
  java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$caso.c" "$TMP/$caso.port"
  gcc -Wall -Wextra -Werror -o "$TMP/$caso" "$TMP/$caso.c" -lm
  local recebido
  recebido="$("$TMP/$caso")"
  if [ "$recebido" != "$esperado" ]; then
    echo "FALHA $caso: esperado '$esperado', obtido '$recebido'" >&2
    cat "$TMP/$caso.c" >&2
    exit 1
  fi
  echo "OK executado: $caso"
}

rejeitar() {
  local caso="$1" esperado="$2" codigo="$3"
  printf '%s\n' "$codigo" > "$TMP/$caso.port"
  if java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$caso.c" "$TMP/$caso.port" > "$TMP/$caso.log" 2>&1; then
    echo "FALHA $caso: codigo inseguro foi aceito" >&2
    exit 1
  fi
  if ! grep -q "$esperado" "$TMP/$caso.log"; then
    echo "FALHA $caso: faltou diagnostico '$esperado'" >&2
    cat "$TMP/$caso.log" >&2
    exit 1
  fi
  echo "OK rejeitado: $caso"
}

aceitar "tamanho_direto" "3" '
inclua texto
funcao inicio() {
    escreva(tamanho("Ana"))
}'

aceitar "tamanho_mais_literal" "4" '
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") + 1)
}'

aceitar "literal_mais_tamanho" "4" '
inclua texto
funcao inicio() {
    escreva(1 + tamanho("Ana"))
}'

aceitar "dois_tamanhos" "5" '
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") + tamanho("Oi"))
}'

aceitar "comparar_tamanhos" "1" '
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") == tamanho("Bia"))
}'

aceitar "formato_misto" "A4B" '
inclua texto
funcao inicio() {
    escreva("A", tamanho("Ana") + 1, "B")
}'

aceitar "indice_size_t" "n" '
inclua texto
funcao inicio() {
    texto nome = "Ana"
    escreva(nome[tamanho("A")])
}'

aceitar "alocacao_liberacao" "ok" '
funcao inicio() {
    libere(aloque(16))
    escreva("ok")
}'

rejeitar "ponteiro_impressao" "ponteiro" '
funcao inicio() {
    escreva(aloque(16))
}'

rejeitar "ponteiro_soma" "tipagem segura" '
funcao inicio() {
    escreva(aloque(16) + 1)
}'

rejeitar "tamanho_menos_um" "tipagem segura" '
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") - 1)
}'

rejeitar "tamanho_mais_negativo" "tipagem segura" '
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") + -1)
}'

rejeitar "tamanho_mais_decimal" "tipagem segura" '
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") + 1.5)
}'

rejeitar "tamanho_para_inteiro" "tipagem segura" '
inclua texto
funcao inicio() {
    inteiro valor = tamanho("Ana")
    escreva(valor)
}'

# Compatibilidade: no legado preservamos a conversao em C, sem prometer seguranca.
cat > "$TMP/legado.port" <<'EOF'
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") - 1)
}
EOF
java -jar "$PORT_JAR" --tipagem=legada -o "$TMP/legado.c" "$TMP/legado.port"
test -s "$TMP/legado.c"
echo "OK legado: geracao C preservada"
echo "V2.5: todos os casos foram verificados."
