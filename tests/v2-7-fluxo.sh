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
  local saida
  saida="$("$TMP/$caso")"
  if [ "$saida" != "$esperado" ]; then
    printf "FALHA %s: esperado '%s' e obtido '%s'\n" "$caso" "$esperado" "$saida" >&2
    exit 1
  fi
  echo "OK executado: $caso"
}
rejeitar() {
  local caso="$1" mensagem="$2" codigo="$3"
  printf '%s\n' "$codigo" > "$TMP/$caso.port"
  if java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$caso.c" "$TMP/$caso.port" > "$TMP/$caso.log" 2>&1; then
    echo "FALHA $caso: erro de ownership nao detectado" >&2
    exit 1
  fi
  if ! grep -q "$mensagem" "$TMP/$caso.log"; then
    echo "FALHA $caso: faltou mensagem '$mensagem'" >&2
    cat "$TMP/$caso.log" >&2
    exit 1
  fi
  echo "OK rejeitado: $caso"
}

aceitar "liberar_ambos_os_ramos" "ok" '
funcao inicio() {
    ponteiro recurso = aloque(16)
    se (verdadeiro) {
        libere(recurso)
    } senao {
        libere(recurso)
    }
    escreva("ok")
}'

aceitar "liberar_tres_ramos" "ok" '
funcao inicio() {
    ponteiro recurso = aloque(16)
    inteiro n = 2
    se (n == 1) {
        libere(recurso)
    } senao se (n == 2) {
        libere(recurso)
    } senao {
        libere(recurso)
    }
    escreva("ok")
}'

aceitar "move_local" "move" '
funcao inicio() {
    ponteiro origem = aloque(16)
    ponteiro destino = origem
    libere(destino)
    escreva("move")
}'

aceitar "transferencia_parametro" "param" '
funcao consumir(ponteiro recurso): vazio {
    libere(recurso)
}
funcao inicio() {
    ponteiro local = aloque(16)
    consumir(local)
    escreva("param")
}'

aceitar "transferencia_direta" "direta" '
funcao consumir(ponteiro recurso): vazio {
    libere(recurso)
}
funcao inicio() {
    consumir(aloque(16))
    escreva("direta")
}'

aceitar "retorno_de_ponteiro" "retorno" '
funcao fabricar(): ponteiro {
    ponteiro recurso = aloque(16)
    retorne recurso
}
funcao inicio() {
    ponteiro local = fabricar()
    libere(local)
    escreva("retorno")
}'

aceitar "retorno_condicional" "ramos" '
funcao fabricar(logico condicao): ponteiro {
    se (condicao) {
        retorne aloque(16)
    } senao {
        retorne aloque(24)
    }
}
funcao inicio() {
    ponteiro local = fabricar(verdadeiro)
    libere(local)
    escreva("ramos")
}'

aceitar "retorno_antecipado" "antecipado" '
funcao rotina(logico condicao): vazio {
    ponteiro local = aloque(16)
    se (condicao) {
        libere(local)
        retorne
    }
    libere(local)
}
funcao inicio() {
    rotina(verdadeiro)
    rotina(falso)
    escreva("antecipado")
}'

aceitar "transferencia_em_ambos_ramos" "dois" '
funcao consumir(ponteiro recurso): vazio {
    libere(recurso)
}
funcao inicio() {
    ponteiro recurso = aloque(16)
    se (verdadeiro) {
        consumir(recurso)
    } senao {
        consumir(recurso)
    }
    escreva("dois")
}'

rejeitar "liberacao_parcial" "estados de posse divergentes" '
funcao inicio() {
    ponteiro p = aloque(16)
    se (verdadeiro) {
        libere(p)
    }
    libere(p)
}'

rejeitar "liberacao_duplicada_ramos" "liberacao duplicada" '
funcao inicio() {
    ponteiro p = aloque(16)
    se (verdadeiro) {
        libere(p)
    } senao {
        libere(p)
    }
    libere(p)
}'

rejeitar "uso_apos_movimento" "uso apos transferencia" '
funcao inicio() {
    ponteiro a = aloque(16)
    ponteiro b = a
    libere(a)
    libere(b)
}'

rejeitar "uso_apos_consumidor" "uso apos transferencia" '
funcao consumir(ponteiro p): vazio {
    libere(p)
}
funcao inicio() {
    ponteiro a = aloque(16)
    consumir(a)
    libere(a)
}'

rejeitar "funcao_descarta_parametro" "possivel vazamento" '
funcao descartar(ponteiro p): vazio {
    escreva("nada")
}
funcao inicio() {}'

rejeitar "resultado_sem_dono" "resultado ponteiro" '
funcao fabricar(): ponteiro {
    retorne aloque(16)
}
funcao inicio() {
    fabricar()
}'

rejeitar "retorno_desprotegido" "possivel vazamento" '
funcao fabrica(logico condicao): ponteiro {
    ponteiro a = aloque(16)
    se (condicao) {
        retorne aloque(16)
    } senao {
        libere(a)
        retorne aloque(16)
    }
}
funcao inicio() {}'

rejeitar "realoque_bloqueado" "realoque ainda" '
funcao inicio() {
    ponteiro a = aloque(16)
    ponteiro b = realoque(a, 32)
    libere(b)
}'

rejeitar "liberacao_em_loop" "libere em laco" '
funcao inicio() {
    ponteiro a = aloque(16)
    enquanto (verdadeiro) {
        libere(a)
    }
}'

rejeitar "transferencia_externa" "funcao externa com ponteiro" '
funcao consumir(ponteiro p): vazio = consumir_externo
funcao inicio() {
    ponteiro a = aloque(16)
    consumir(a)
}'

echo "V2.7: regressao de fluxo e transferencia aprovada."
