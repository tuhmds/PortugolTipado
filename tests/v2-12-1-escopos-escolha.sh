#!/usr/bin/env bash
set -euo pipefail
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
JAR="core/build/libs/port.jar"

executar() {
    local nome="$1" esperado="$2" modo="$3"
    cat > "$TMP/$nome.port"
    java -jar "$JAR" --tipagem="$modo" -o "$TMP/$nome.c" "$TMP/$nome.port"
    # A linguagem ja preservava fallthrough em escolha; neste teste isolado
    # ignoramos apenas o aviso do GCC de queda implicita para o caso seguinte.
    gcc -std=c11 -Wall -Wextra -Werror -Wno-implicit-fallthrough \
        "$TMP/$nome.c" -lm -o "$TMP/$nome"
    local obtido
    obtido="$("$TMP/$nome")"
    if [ "$obtido" != "$esperado" ]; then
        printf 'FALHA %s: esperado [%s], obtido [%s]\n' "$nome" "$esperado" "$obtido" >&2
        cat "$TMP/$nome.c" >&2
        exit 1
    fi
    echo "OK executado: $nome ($modo)"
}

executar "casos_mesmo_nome_tipos_diferentes" "A2" "segura" <<'PORT'
funcao inicio() {
    escolha (1) {
        caso 1:
            seja valor = "A"
            escreva(valor)
        caso 2:
            seja valor = 2
            escreva(valor)
    }
}
PORT

executar "caso_e_padrao_mesmo_nome" "2Z" "segura" <<'PORT'
funcao inicio() {
    escolha (2) {
        caso 1:
            seja dado = "A"
            escreva(dado)
        caso 2:
            seja dado = 2
            escreva(dado)
        casocontrario:
            seja dado = "Z"
            escreva(dado)
    }
}
PORT

executar "cada_caso_const_mesmo_nome" "14" "legada" <<'PORT'
funcao inicio() {
    escolha (1) {
        caso 1:
            const seja valor = 1
            escreva(valor)
        caso 2:
            const seja valor = 4
            escreva(valor)
    }
}
PORT

executar "sombra_case_nao_vaza" "2.500000" "segura" <<'PORT'
funcao inicio() {
    escolha (1) {
        caso 1:
            seja valor = "primeiro"
        caso 2:
            seja valor = 2
    }
    seja valor = 2.5
    escreva(valor)
}
PORT

echo "V2.12.1: escopos de escolha compilados e executados."
