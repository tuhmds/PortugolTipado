#!/usr/bin/env bash
set -euo pipefail
PORT_JAR="core/build/libs/port.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

aceitar() {
    local nome="$1" esperado="$2" codigo="$3"
    printf '%s\n' "$codigo" > "$TMP/$nome.port"
    java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port"
    gcc -Wall -Wextra -Werror -o "$TMP/$nome" "$TMP/$nome.c" -lm
    local resultado
    resultado="$("$TMP/$nome")"
    if [ "$resultado" != "$esperado" ]; then
        echo "FALHA $nome: esperado [$esperado], veio [$resultado]" >&2
        exit 1
    fi
    echo "OK executado: $nome"
}
rejeitar() {
    local nome="$1" trecho="$2" codigo="$3"
    printf '%s\n' "$codigo" > "$TMP/$nome.port"
    if java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port" > "$TMP/$nome.log" 2>&1; then
        echo "FALHA: $nome foi aceito" >&2
        exit 1
    fi
    if ! grep -q "$trecho" "$TMP/$nome.log"; then
        echo "FALHA: $nome deveria conter '$trecho'" >&2
        cat "$TMP/$nome.log" >&2
        exit 1
    fi
    echo "OK rejeitado: $nome"
}

aceitar "ponteiro_local_em_se" "ok" '
funcao inicio() {
    se (verdadeiro) {
        ponteiro p = aloque(8)
        libere(p)
    } senao {
        ponteiro q = aloque(12)
        libere(q)
    }
    escreva("ok")
}'

aceitar "sombreamento_escopo" "sombra" '
funcao inicio() {
    ponteiro p = aloque(4)
    se (verdadeiro) {
        ponteiro p = aloque(8)
        libere(p)
    }
    libere(p)
    escreva("sombra")
}'

aceitar "enquanto_recurso_por_iteracao" "3" '
funcao inicio() {
    inteiro i = 0
    enquanto (i < 3) {
        ponteiro p = aloque(16)
        libere(p)
        i++
    }
    escreva(i)
}'

aceitar "faca_recurso_por_iteracao" "2" '
funcao inicio() {
    inteiro i = 0
    faca {
        ponteiro p = aloque(16)
        libere(p)
        i++
    } enquanto (i < 2)
    escreva(i)
}'

aceitar "para_recurso_por_iteracao" "2" '
funcao inicio() {
    inteiro total = 0
    para (inteiro i = 0; i < 2; i++) {
        ponteiro p = aloque(12)
        libere(p)
        total++
    }
    escreva(total)
}'

aceitar "laco_transfere_ponteiro_local" "2" '
funcao usar(ponteiro p): vazio {
    libere(p)
}
funcao inicio() {
    inteiro i = 0
    enquanto (i < 2) {
        ponteiro p = aloque(10)
        usar(p)
        i++
    }
    escreva(i)
}'

aceitar "ponteiro_externo_preservado_laco" "salvo" '
funcao inicio() {
    ponteiro p = aloque(7)
    inteiro i = 0
    enquanto (i < 2) {
        ponteiro p = aloque(9)
        libere(p)
        i++
    }
    libere(p)
    escreva("salvo")
}'

aceitar "condicional_dentro_laco" "feito" '
funcao inicio() {
    inteiro i = 0
    enquanto (i < 2) {
        se (i == 0) {
            ponteiro p = aloque(8)
            libere(p)
        } senao {
            ponteiro q = aloque(12)
            libere(q)
        }
        i++
    }
    escreva("feito")
}'

rejeitar "vazamento_em_se" "possivel vazamento no escopo local" '
funcao inicio() {
    se (verdadeiro) {
        ponteiro p = aloque(8)
    }
}'

rejeitar "vazamento_em_laco" "possivel vazamento no escopo local" '
funcao inicio() {
    inteiro i = 0
    enquanto (i < 2) {
        ponteiro p = aloque(8)
        i++
    }
}'

rejeitar "libera_externo_em_laco" "laco nao pode alterar ownership" '
funcao inicio() {
    ponteiro p = aloque(16)
    enquanto (verdadeiro) {
        libere(p)
    }
}'

rejeitar "move_externo_em_laco" "laco nao pode alterar ownership" '
funcao consumir(ponteiro p): vazio {
    libere(p)
}
funcao inicio() {
    ponteiro p = aloque(16)
    enquanto (verdadeiro) {
        consumir(p)
    }
}'

rejeitar "duplo_libere_local" "liberacao duplicada" '
funcao inicio() {
    enquanto (verdadeiro) {
        ponteiro p = aloque(8)
        libere(p)
        libere(p)
    }
}'

rejeitar "sombra_duplo_libere" "liberacao duplicada" '
funcao inicio() {
    ponteiro p = aloque(8)
    se (verdadeiro) {
        ponteiro p = aloque(16)
        libere(p)
        libere(p)
    }
    libere(p)
}'

rejeitar "retorne_em_laco" "retorne em laco" '
funcao rotina(): vazio {
    enquanto (verdadeiro) {
        retorne
    }
}
funcao inicio() {}'

rejeitar "sair_laco_com_recurso" "dispensar/ignorar em laco" '
funcao inicio() {
    enquanto (verdadeiro) {
        ponteiro p = aloque(8)
        dispensar
        libere(p)
    }
}'

rejeitar "ponteiro_cabecalho_para" "ponteiro no cabecalho do para" '
funcao inicio() {
    para (ponteiro p = aloque(8); verdadeiro; ) {
        libere(p)
        dispensar
    }
}'

rejeitar "liberacao_parcial_em_laco" "estados de posse divergentes" '
funcao inicio() {
    inteiro i = 0
    enquanto (i < 2) {
        ponteiro p = aloque(8)
        se (i == 0) {
            libere(p)
        }
        i++
    }
}'

echo "V2.8: escopos e lacos verificados."
