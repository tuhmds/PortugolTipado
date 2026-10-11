#!/usr/bin/env bash
set -euo pipefail
PORT_JAR="core/build/libs/port.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

aceitar() {
  local caso="$1" esperado="$2" fonte="$3"
  printf '%s\n' "$fonte" > "$TMP/$caso.port"
  java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$caso.c" "$TMP/$caso.port"
  gcc -Wall -Wextra -Werror -o "$TMP/$caso" "$TMP/$caso.c" -lm
  local resultado
  resultado="$("$TMP/$caso")"
  if [ "$resultado" != "$esperado" ]; then
    echo "FALHOU $caso: esperado [$esperado], obtido [$resultado]" >&2
    exit 1
  fi
  echo "OK executado: $caso"
}
rejeitar() {
  local caso="$1" mensagem="$2" fonte="$3"
  printf '%s\n' "$fonte" > "$TMP/$caso.port"
  if java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$caso.c" "$TMP/$caso.port" > "$TMP/$caso.log" 2>&1; then
    echo "FALHOU $caso: programa inseguro aceito" >&2
    exit 1
  fi
  if ! grep -q "$mensagem" "$TMP/$caso.log"; then
    echo "FALHOU $caso: faltou diagnostico '$mensagem'" >&2
    cat "$TMP/$caso.log" >&2
    exit 1
  fi
  echo "OK rejeitado: $caso"
}

aceitar "pare_liberado" "ok" '
funcao inicio() {
    enquanto (verdadeiro) {
        ponteiro p = aloque(8)
        libere(p)
        pare
    }
    escreva("ok")
}'

aceitar "continue_liberado" "3" '
funcao inicio() {
    inteiro i = 0
    enquanto (i < 3) {
        ponteiro p = aloque(8)
        libere(p)
        i++
        continue
    }
    escreva(i)
}'

aceitar "pare_em_se" "1" '
funcao inicio() {
    inteiro i = 0
    enquanto (i < 4) {
        ponteiro p = aloque(16)
        libere(p)
        se (i == 1) {
            pare
        }
        i++
    }
    escreva(i)
}'

aceitar "continue_em_se" "3" '
funcao inicio() {
    inteiro i = 0
    enquanto (i < 3) {
        ponteiro p = aloque(16)
        libere(p)
        se (i == 0) {
            i++
            continue
        }
        i++
    }
    escreva(i)
}'

aceitar "para_continue_incremento" "3" '
funcao inicio() {
    inteiro total = 0
    para (inteiro i = 0; i < 3; i++) {
        ponteiro p = aloque(12)
        libere(p)
        se (i == 1) {
            continue
        }
        total++
    }
    escreva(total + 1)
}'

aceitar "pare_preserva_externo" "externo" '
funcao inicio() {
    ponteiro p = aloque(32)
    enquanto (verdadeiro) {
        pare
    }
    libere(p)
    escreva("externo")
}'

aceitar "lacos_aninhados" "aninhado" '
funcao inicio() {
    enquanto (verdadeiro) {
        ponteiro externo = aloque(16)
        enquanto (verdadeiro) {
            ponteiro interno = aloque(8)
            libere(interno)
            pare
        }
        libere(externo)
        pare
    }
    escreva("aninhado")
}'

aceitar "emprestimo_simples" "BA" '
funcao observar(const ponteiro ref): vazio {
    escreva("B")
}
funcao inicio() {
    ponteiro p = aloque(16)
    observar(p)
    libere(p)
    escreva("A")
}'

aceitar "emprestimo_encadeado" "encadeado" '
funcao observar(const ponteiro ref): vazio {
    escreva("encadeado")
}
funcao passar(const ponteiro ref): vazio {
    observar(ref)
}
funcao inicio() {
    ponteiro recurso = aloque(32)
    passar(recurso)
    libere(recurso)
}'

aceitar "empresta_depois_move" "2" '
funcao observar(const ponteiro p): vazio {
    escreva("1")
}
funcao consumir(ponteiro p): vazio {
    libere(p)
}
funcao inicio() {
    ponteiro a = aloque(8)
    observar(a)
    consumir(a)
    escreva("2")
}'

rejeitar "pare_com_recurso_vivo" "pare/continue com ponteiro local ativo" '
funcao inicio() {
    enquanto (verdadeiro) {
        ponteiro p = aloque(16)
        pare
    }
}'

rejeitar "continue_com_recurso_vivo" "pare/continue com ponteiro local ativo" '
funcao inicio() {
    enquanto (verdadeiro) {
        ponteiro p = aloque(16)
        continue
    }
}'

rejeitar "pare_apos_liberar_externo" "pare/continue alterou a posse" '
funcao inicio() {
    ponteiro p = aloque(16)
    enquanto (verdadeiro) {
        libere(p)
        pare
    }
}'

rejeitar "emprestimo_apos_libere" "emprestimo apos libere" '
funcao observar(const ponteiro p): vazio {
    escreva("nao")
}
funcao inicio() {
    ponteiro p = aloque(16)
    libere(p)
    observar(p)
}'

rejeitar "emprestimo_apos_move" "emprestimo apos transferencia" '
funcao observar(const ponteiro p): vazio {
    escreva("nao")
}
funcao consumir(ponteiro p): vazio {
    libere(p)
}
funcao inicio() {
    ponteiro p = aloque(16)
    consumir(p)
    observar(p)
}'

rejeitar "libere_emprestado" "emprestado nao pode ser liberado" '
funcao quebrar(const ponteiro p): vazio {
    libere(p)
}
funcao inicio() {}'

rejeitar "move_emprestado" "emprestado nao pode ser liberado" '
funcao consumir(ponteiro p): vazio {
    libere(p)
}
funcao quebrar(const ponteiro p): vazio {
    consumir(p)
}
funcao inicio() {}'

rejeitar "retorna_emprestado" "emprestado nao pode ser liberado" '
funcao quebrar(const ponteiro p): ponteiro {
    retorne p
}
funcao inicio() {}'

rejeitar "emprestimo_de_alocacao" "emprestimo exige identificador" '
funcao observar(const ponteiro p): vazio {
    escreva("nao")
}
funcao inicio() {
    observar(aloque(16))
}'

rejeitar "emprestimo_escapa_uso" "emprestado nao pode escapar" '
funcao quebrar(const ponteiro p): vazio {
    escreva(p)
}
funcao inicio() {}'

rejeitar "ocultar_emprestado" "nao e permitido ocultar" '
funcao quebrar(const ponteiro p): vazio {
    ponteiro p = aloque(8)
    libere(p)
}
funcao inicio() {}'

rejeitar "emprestimo_e_move_simultaneos" "nao pode ser emprestado e transferido na mesma chamada" '
funcao misturar(const ponteiro emprestado, ponteiro dono): vazio {
    libere(dono)
}
funcao inicio() {
    ponteiro p = aloque(16)
    misturar(p, p)
}'

echo "V2.9: saidas e emprestimos verificados."
