#!/usr/bin/env bash
set -euo pipefail
PORT_JAR="core/build/libs/port.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

aceitar() {
  local caso="$1" esperado="$2" fonte="$3" modo="segura"
  printf '%s\n' "$fonte" > "$TMP/$caso.port"
  java -jar "$PORT_JAR" --tipagem="$modo" -o "$TMP/$caso.c" "$TMP/$caso.port"
  gcc -std=c11 -Wall -Wextra -Werror -Wno-unused-parameter \
    "$TMP/$caso.c" -lm -o "$TMP/$caso"
  local obtido
  obtido="$("$TMP/$caso")"
  if [ "$obtido" != "$esperado" ]; then
    printf "FALHA %s: esperado '%s' obtido '%s'\n" "$caso" "$esperado" "$obtido" >&2
    cat "$TMP/$caso.c" >&2
    exit 1
  fi
  echo "OK executado: $caso"
}

rejeitar() {
  local caso="$1" diagnostico="$2" fonte="$3"
  printf '%s\n' "$fonte" > "$TMP/$caso.port"
  if java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$caso.c" "$TMP/$caso.port" > "$TMP/$caso.log" 2>&1; then
    echo "FALHA $caso: inferencia insegura foi aceita" >&2
    exit 1
  fi
  if ! grep -q "$diagnostico" "$TMP/$caso.log"; then
    echo "FALHA $caso: falta diagnostico '$diagnostico'" >&2
    cat "$TMP/$caso.log" >&2
    exit 1
  fi
  echo "OK rejeitado: $caso"
}

aceitar "literal_inteiro" "42" '
funcao inicio() {
    seja idade = 42
    escreva(idade)
}'
aceitar "literal_duplo" "1.500000" '
funcao inicio() {
    seja peso = 1.5
    escreva(peso)
}'
aceitar "literal_texto" "Ana" '
funcao inicio() {
    seja nome = "Ana"
    escreva(nome)
}'
aceitar "literal_caractere" "A" "
funcao inicio() {
    seja inicial = 'A'
    escreva(inicial)
}"
aceitar "literal_logico" "1" '
funcao inicio() {
    seja ativo = verdadeiro
    escreva(ativo)
}'
aceitar "aritmetica" "14" '
funcao inicio() {
    seja total = 2 + 3 * 4
    escreva(total)
}'
aceitar "negativo_decimal" "-1.500000" '
funcao inicio() {
    seja saldo = -1.5
    escreva(saldo)
}'
aceitar "dependente" "8" '
funcao inicio() {
    seja a = 5
    seja b = a + 3
    escreva(b)
}'
aceitar "const_seja" "12" '
funcao inicio() {
    const seja limite = 12
    escreva(limite)
}'
aceitar "parametro_e_retorno" "8" '
funcao dobro(inteiro base): inteiro {
    seja multiplicado = base * 2
    retorne multiplicado
}
funcao inicio() {
    seja valor = dobro(4)
    escreva(valor)
}'
aceitar "sombra_de_tipos" "interior1" '
funcao inicio() {
    seja valor = 1
    se (verdadeiro) {
        seja valor = "interior"
        escreva(valor)
    }
    escreva(valor)
}'
aceitar "soma_tamanhos" "4" '
inclua texto
funcao inicio() {
    seja comprimento = tamanho("Ana") + 1
    escreva(comprimento)
}'
aceitar "ponteiro_com_posse" "ok" '
funcao inicio() {
    seja recurso = aloque(16)
    libere(recurso)
    escreva("ok")
}'
aceitar "para_com_seja" "3" '
funcao inicio() {
    seja soma = 0
    para (seja i = 0; i < 3; i++) {
        soma++
    }
    escreva(soma)
}'
aceitar "seja_apos_para" "2.500000" '
funcao inicio() {
    para (seja i = 0; i < 2; i++) {
    }
    seja i = 2.5
    escreva(i)
}'
aceitar "seja_com_indice_texto" "n" '
funcao inicio() {
    texto nome = "Ana"
    seja letra = nome[1]
    escreva(letra)
}'
aceitar "seja_com_comparacao" "1" '
funcao inicio() {
    seja resultado = 2 < 4
    escreva(resultado)
}'

rejeitar "sem_inicializador" "exige inicializador" '
funcao inicio() {
    seja valor
}'
rejeitar "nome_desconhecido" "nao foi possivel inferir" '
funcao inicio() {
    seja valor = desconhecido
}'
rejeitar "funcao_desconhecida" "nao foi possivel inferir" '
funcao inicio() {
    seja valor = misteriosa(3)
}'
rejeitar "soma_texto_numero" "nao foi possivel inferir" '
funcao inicio() {
    seja valor = "A" + 2
}'
rejeitar "soma_textos" "nao foi possivel inferir" '
funcao inicio() {
    seja valor = "A" + "B"
}'
rejeitar "booleano_aritmetico" "nao foi possivel inferir" '
funcao inicio() {
    seja valor = verdadeiro + 1
}'
rejeitar "comparacao_incompativel" "nao foi possivel inferir" '
funcao inicio() {
    seja valido = "Ana" == 10
}'
rejeitar "array_sem_tipo" "nao infere arrays" '
funcao inicio() {
    seja numeros = {1, 2}
}'
rejeitar "vazio_sem_tipo" "nao foi possivel inferir" '
funcao semRetorno(): vazio {}
funcao inicio() {
    seja valor = semRetorno()
}'
rejeitar "ponteiro_aritmetico" "nao foi possivel inferir" '
funcao inicio() {
    seja valor = aloque(16) + 1
}'
rejeitar "negacao_inteiro" "nao foi possivel inferir" '
funcao inicio() {
    seja valor = !1
}'

# Tambem funciona no modo legado; nao depende do --tipagem=segura.
cat > "$TMP/legado.port" <<'PORT'
funcao inicio() {
    seja n = 7
    escreva(n)
}
PORT
java -jar "$PORT_JAR" --tipagem=legada -o "$TMP/legado.c" "$TMP/legado.port"
gcc -std=c11 -Wall -Wextra -Werror "$TMP/legado.c" -lm -o "$TMP/legado"
test "$("$TMP/legado")" = 7
echo "OK legado: seja compila e executa"
echo "V2.12: regressao de inferencia local aprovada."
