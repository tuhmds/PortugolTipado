#!/usr/bin/env bash
set -euo pipefail
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
JAR="core/build/libs/port.jar"

executar() {
    local nome="$1" esperado="$2" modo="$3"
    cat > "$TEMP/$nome.port"
    java -jar "$JAR" --tipagem="$modo" -o "$TEMP/$nome.c" "$TEMP/$nome.port"
    gcc -std=c11 -Wall -Wextra -Werror -Wno-unused-parameter \
      -Werror=format -O0 "$TEMP/$nome.c" -lm -o "$TEMP/$nome"
    local obtido
    obtido="$("$TEMP/$nome")"
    if [ "$obtido" != "$esperado" ]; then
        echo "FALHA: $nome esperado [$esperado], obtido [$obtido]" >&2
        cat "$TEMP/$nome.c" >&2
        exit 1
    fi
    echo "OK executado: $nome ($modo)"
}

rejeitar() {
    local nome="$1" mensagem="$2"
    cat > "$TEMP/$nome.port"
    if java -jar "$JAR" --tipagem=segura -o "$TEMP/$nome.c" "$TEMP/$nome.port" > "$TEMP/$nome.log" 2>&1; then
        echo "FALHA: $nome deveria ser rejeitado" >&2
        exit 1
    fi
    if ! grep -q "$mensagem" "$TEMP/$nome.log"; then
        echo "FALHA: $nome sem mensagem '$mensagem'" >&2
        cat "$TEMP/$nome.log" >&2
        exit 1
    fi
    echo "OK rejeitado: $nome"
}

executar "iterador_sombra_duplo" "122.500000" "segura" <<'PORT'
funcao inicio() {
    duplo valor = 2.5
    inteiro dados[2] = {1, 2}
    para cada (inteiro valor em dados) {
        escreva(valor)
    }
    escreva(valor)
}
PORT

executar "iterador_sombra_texto" "12Ana" "segura" <<'PORT'
funcao inicio() {
    texto item = "Ana"
    inteiro dados[2] = {1, 2}
    para cada (inteiro item em dados) {
        escreva(item)
    }
    escreva(item)
}
PORT

executar "iterador_sombra_caractere" "12Z" "segura" <<'PORT'
funcao inicio() {
    caractere letra = 'Z'
    inteiro dados[2] = {1, 2}
    para cada (inteiro letra em dados) {
        escreva(letra)
    }
    escreva(letra)
}
PORT

executar "iterador_sombra_parametro" "122.500000" "segura" <<'PORT'
funcao processar(duplo item): vazio {
    inteiro dados[2] = {1, 2}
    para cada (inteiro item em dados) {
        escreva(item)
    }
    escreva(item)
}
funcao inicio() {
    processar(2.5)
}
PORT

executar "iterador_com_nome_i" "127" "segura" <<'PORT'
funcao inicio() {
    inteiro _i = 7
    inteiro dados[2] = {1, 2}
    para cada (inteiro _i em dados) {
        escreva(_i)
    }
    escreva(_i)
}
PORT

executar "dois_cada_aninhados" "112212" "segura" <<'PORT'
funcao inicio() {
    inteiro numeros[2] = {1, 2}
    para cada (inteiro _i em numeros) {
        escreva(_i)
        para cada (inteiro _i em numeros) {
            escreva(_i)
        }
    }
}
PORT

executar "iterador_sombra_no_legado" "122.500000" "legada" <<'PORT'
funcao inicio() {
    duplo valor = 2.5
    inteiro dados[2] = {1, 2}
    para cada (inteiro valor em dados) {
        escreva(valor)
    }
    escreva(valor)
}
PORT

executar "seja_apos_para_cada" "122.500000" "segura" <<'PORT'
funcao inicio() {
    inteiro dados[2] = {1, 2}
    para cada (inteiro item em dados) {
        escreva(item)
    }
    seja item = 2.5
    escreva(item)
}
PORT

executar "iterador_sombra_em_if" "122.500000" "segura" <<'PORT'
funcao inicio() {
    duplo item = 2.5
    inteiro dados[2] = {1, 2}
    se (verdadeiro) {
        para cada (inteiro item em dados) {
            escreva(item)
        }
    }
    escreva(item)
}
PORT

rejeitar "cada_nao_array" "exige array" <<'PORT'
funcao inicio() {
    inteiro dado = 1
    para cada (inteiro item em dado) {
        escreva(item)
    }
}
PORT

echo "V2.12.2: testes adversariais de para cada aprovados."
