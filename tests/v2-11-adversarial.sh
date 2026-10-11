#!/usr/bin/env bash
# Reexecutar com PORT_CC=gcc/clang e PORT_CCFLAGS='-fsanitize=address,undefined ...'.
set -euo pipefail
PORT_JAR="${PORT_JAR:-core/build/libs/port.jar}"
PORT_CC="${PORT_CC:-gcc}"
PORT_CCFLAGS="${PORT_CCFLAGS:-}"
read -r -a extras <<< "$PORT_CCFLAGS"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

aceitar() {
  local nome="$1" esperado="$2" recebido
  cat > "$TMP/$nome.port"
  java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port"
  "$PORT_CC" -std=c11 -Wall -Wextra -Werror -Wno-unused-parameter \
    "${extras[@]}" "$TMP/$nome.c" -lm -o "$TMP/$nome.exe"
  recebido="$("$TMP/$nome.exe" | tr -d '\r')"
  if [ "$recebido" != "$esperado" ]; then
    printf "FALHA %s: esperado [%s], recebido [%s]\n" "$nome" "$esperado" "$recebido" >&2
    cat "$TMP/$nome.c" >&2
    exit 1
  fi
  echo "OK executado: $nome"
}
rejeitar() {
  local nome="$1" mensagem="$2"
  cat > "$TMP/$nome.port"
  if java -jar "$PORT_JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port" \
       > "$TMP/$nome.log" 2>&1; then
    echo "FALHA $nome: programa inseguro aceito" >&2
    exit 1
  fi
  if ! grep -q "$mensagem" "$TMP/$nome.log"; then
    echo "FALHA $nome: falta mensagem '$mensagem'" >&2
    cat "$TMP/$nome.log" >&2
    exit 1
  fi
  echo "OK rejeitado: $nome"
}

aceitar "vida_duas_funcoes" "Bfim" <<'PORT'
funcao observar(const ponteiro recurso): vazio {
    escreva("B")
}
funcao consumir(ponteiro recurso): vazio {
    libere(recurso)
}
funcao criar(): ponteiro {
    retorne aloque_zerado(1, 64)
}
funcao inicio() {
    ponteiro memoria = criar()
    observar(memoria)
    consumir(memoria)
    escreva("fim")
}
PORT

aceitar "ramos_e_sombra" "20fim" <<'PORT'
funcao inicio() {
    ponteiro primeiro = aloque(128)
    ponteiro segundo = aloque(128)
    se (verdadeiro) {
        inteiro primeiro = 20
        escreva(primeiro)
    }
    se (verdadeiro) {
        libere(primeiro)
    } senao {
        libere(primeiro)
    }
    libere(segundo)
    escreva("fim")
}
PORT

aceitar "laco_memoria_por_iteracao" "4" <<'PORT'
funcao inicio() {
    inteiro total = 0
    enquanto (total < 4) {
        ponteiro p = aloque_zerado(8, 32)
        libere(p)
        total++
    }
    escreva(total)
}
PORT

aceitar "continue_e_pare" "3" <<'PORT'
funcao inicio() {
    inteiro n = 0
    enquanto (verdadeiro) {
        ponteiro p = aloque(128)
        libere(p)
        n++
        se (n < 3) {
            continue
        } senao {
            pare
        }
    }
    escreva(n)
}
PORT

aceitar "formatos_tamanho_C" "45" <<'PORT'
inclua texto
funcao inicio() {
    escreva(tamanho("Ana") + 1, tamanho("ABCDE"))
}
PORT

aceitar "copia_texto_limitada" "Oi!" <<'PORT'
inclua texto
funcao inicio() {
    texto buf
    copie(buf, "Oi")
    junte(buf, "!")
    escreva(buf)
}
PORT

rejeitar "duplo_free" "liberacao duplicada" <<'PORT'
funcao inicio() {
    ponteiro p = aloque(16)
    libere(p)
    libere(p)
}
PORT

rejeitar "free_apos_move" "uso apos transferencia" <<'PORT'
funcao consumir(ponteiro p): vazio {
    libere(p)
}
funcao inicio() {
    ponteiro p = aloque(16)
    consumir(p)
    libere(p)
}
PORT

rejeitar "emprestimo_apos_free" "emprestimo apos libere" <<'PORT'
funcao ler(const ponteiro p): vazio {
    escreva("inacessivel")
}
funcao inicio() {
    ponteiro p = aloque(16)
    libere(p)
    ler(p)
}
PORT

rejeitar "free_de_emprestado" "emprestado nao pode" <<'PORT'
funcao ler(const ponteiro p): vazio {
    libere(p)
}
funcao inicio() {}
PORT

rejeitar "move_e_borrow_mesma_chamada" "nao pode ser emprestado e transferido" <<'PORT'
funcao usar(const ponteiro a, ponteiro b): vazio {
    libere(b)
}
funcao inicio() {
    ponteiro p = aloque(16)
    usar(p, p)
}
PORT

rejeitar "vazamento_escopo" "possivel vazamento" <<'PORT'
funcao inicio() {
    se (verdadeiro) {
        ponteiro p = aloque(16)
    }
}
PORT

rejeitar "vazamento_funcao" "possivel vazamento" <<'PORT'
funcao inicio() {
    ponteiro p = aloque(16)
    escreva("sem free")
}
PORT

rejeitar "continue_com_memoria_viva" "pare/continue com ponteiro local ativo" <<'PORT'
funcao inicio() {
    enquanto (verdadeiro) {
        ponteiro p = aloque(16)
        continue
    }
}
PORT

rejeitar "pare_com_memoria_viva" "pare/continue com ponteiro local ativo" <<'PORT'
funcao inicio() {
    enquanto (verdadeiro) {
        ponteiro p = aloque(16)
        pare
    }
}
PORT

rejeitar "move_parcial_se" "estados de posse divergentes" <<'PORT'
funcao consumir(ponteiro p): vazio {
    libere(p)
}
funcao inicio() {
    ponteiro p = aloque(32)
    se (verdadeiro) {
        consumir(p)
    }
    libere(p)
}
PORT

rejeitar "free_de_inteiro_sombreado" "ponteiro sem posse conhecida" <<'PORT'
funcao inicio() {
    ponteiro recurso = aloque(16)
    se (verdadeiro) {
        inteiro recurso = 2
        libere(recurso)
    }
    libere(recurso)
}
PORT

rejeitar "aritmetica_ponteiro" "ownership" <<'PORT'
funcao inicio() {
    ponteiro p = aloque(16)
    escreva(p + 1)
    libere(p)
}
PORT

rejeitar "realloc_sem_contrato" "realoque ainda" <<'PORT'
funcao inicio() {
    ponteiro p = realoque(aloque(16), 32)
    libere(p)
}
PORT

echo "V2.11: matriz adversarial aprovada com $PORT_CC $PORT_CCFLAGS."
