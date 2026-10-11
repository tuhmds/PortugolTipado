#!/usr/bin/env bash
# Auditoria estrutural: escopos, ownership e geracao C devem concordar.
set -euo pipefail
PORT_JAR="core/build/libs/port.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

aceitar() {
  local nome="$1" esperado="$2" codigo="$3"
  printf '%s\n' "$codigo" > "$TMP/$nome.port"
  java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port"
  gcc -Wall -Wextra -Werror -Wno-unused-parameter -o "$TMP/$nome" "$TMP/$nome.c" -lm
  local resultado
  resultado="$("$TMP/$nome")"
  if [ "$resultado" != "$esperado" ]; then
    printf "FALHA %s: esperado [%s], obtido [%s]\n" "$nome" "$esperado" "$resultado" >&2
    cat "$TMP/$nome.c" >&2
    exit 1
  fi
  echo "OK executado: $nome"
}

rejeitar() {
  local nome="$1" mensagem="$2" codigo="$3"
  printf '%s\n' "$codigo" > "$TMP/$nome.port"
  if java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port" > "$TMP/$nome.log" 2>&1; then
    echo "FALHA $nome: codigo inseguro aceito" >&2
    exit 1
  fi
  if ! grep -q "$mensagem" "$TMP/$nome.log"; then
    echo "FALHA $nome: diagnostico '$mensagem' nao encontrado" >&2
    cat "$TMP/$nome.log" >&2
    exit 1
  fi
  echo "OK rejeitado: $nome"
}

aceitar "sombra_tipo_se" "42" '
funcao inicio() {
    ponteiro memoria = aloque(16)
    se (verdadeiro) {
        inteiro memoria = 42
        escreva(memoria)
    }
    libere(memoria)
}'

aceitar "sombra_ponteiro_para" "2" '
funcao inicio() {
    ponteiro elemento = aloque(16)
    inteiro soma = 0
    para (inteiro elemento = 0; elemento < 2; elemento++) {
        soma++
    }
    libere(elemento)
    escreva(soma)
}'

aceitar "sombra_numerica_para" "1.500000" '
funcao inicio() {
    duplo valor = 1.5
    para (inteiro valor = 0; valor < 2; valor++) {
    }
    escreva(valor)
}'

aceitar "tipo_funcoes_isoladas" "Ana42" '
funcao primeira(texto item): vazio {
    escreva(item)
}
funcao segunda(inteiro item): vazio {
    escreva(item)
}
funcao inicio() {
    primeira("Ana")
    segunda(42)
}'

aceitar "ownership_empresta_apos_sombra" "10ok" '
funcao observar(const ponteiro p): vazio {
    escreva("ok")
}
funcao inicio() {
    ponteiro recurso = aloque_zerado(1, 16)
    se (verdadeiro) {
        inteiro recurso = 10
        escreva(recurso)
    }
    observar(recurso)
    libere(recurso)
}'

rejeitar "libere_nao_ponteiro_sombreado" "ponteiro sem posse conhecida" '
funcao inicio() {
    ponteiro memoria = aloque(16)
    se (verdadeiro) {
        inteiro memoria = 5
        libere(memoria)
    }
    libere(memoria)
}'

rejeitar "borrow_sombra_nao_ponteiro" "nao e permitido ocultar parametro ponteiro emprestado" '
funcao observar(const ponteiro item): vazio {
    se (verdadeiro) {
        inteiro item = 42
        escreva(item)
    }
}
funcao inicio() {}'

rejeitar "ponteiro_externo_move_sob_nome_oculto" "ponteiro sem posse conhecida" '
funcao consumir(ponteiro p): vazio {
    libere(p)
}
funcao inicio() {
    ponteiro memoria = aloque(16)
    se (verdadeiro) {
        inteiro memoria = 7
        consumir(memoria)
    }
    libere(memoria)
}'

echo "V2.10: auditoria estrutural aprovada."
