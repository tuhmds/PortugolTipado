#!/usr/bin/env bash
set -euo pipefail
JAR="core/build/libs/port.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

aceitar() {
    local nome="$1" esperado="$2"
    cat > "$TMP/$nome.port"
    java -jar "$JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port"
    gcc -std=c11 -Wall -Wextra -Werror -Wno-unused-parameter \
        "$TMP/$nome.c" -lm -o "$TMP/$nome"
    local obtido
    obtido="$("$TMP/$nome")"
    if [ "$obtido" != "$esperado" ]; then
        echo "FALHA $nome: esperado '$esperado', obtido '$obtido'" >&2
        cat "$TMP/$nome.c" >&2
        exit 1
    fi
    echo "OK executado: $nome"
}
rejeitar() {
    local nome="$1" erro="$2"
    cat > "$TMP/$nome.port"
    if java -jar "$JAR" --tipagem=segura -o "$TMP/$nome.c" "$TMP/$nome.port" > "$TMP/$nome.log" 2>&1; then
        echo "FALHA $nome: deveria rejeitar" >&2
        exit 1
    fi
    if ! grep -q "$erro" "$TMP/$nome.log"; then
        echo "FALHA $nome: nao encontrou '$erro'" >&2
        cat "$TMP/$nome.log" >&2
        exit 1
    fi
    echo "OK rejeitado: $nome"
}

# O modulo importado possui duas assinaturas disponiveis antes da inferencia.
cat > "$TMP/triplicador.port" <<'PORT'
funcao triplicar(inteiro n): inteiro {
    retorne n * 3
}
PORT
cat > "$TMP/base.port" <<'PORT'
funcao somarDez(inteiro n): inteiro {
    retorne n + 10
}
PORT
cat > "$TMP/intermediario.port" <<'PORT'
inclua "base_transitiva"
funcao multiplicarPorQuatro(inteiro n): inteiro {
    seja resultado = quadruplicar(n)
    retorne resultado
}
PORT
cat > "$TMP/base_transitiva.port" <<'PORT'
funcao quadruplicar(inteiro n): inteiro {
    retorne n * 4
}
PORT
cat > "$TMP/cliente.port" <<'PORT'
inclua "biblioteca_cliente"
funcao executarModulo(inteiro n): inteiro {
    seja valor = triplicarModulo(n)
    retorne valor
}
PORT
cat > "$TMP/biblioteca_cliente.port" <<'PORT'
funcao triplicarModulo(inteiro n): inteiro {
    retorne n * 3
}
PORT
cat > "$TMP/vazio.port" <<'PORT'
funcao rotinaVazia(): vazio {
    escreva("nada")
}
PORT


aceitar "funcao_posterior" "12" <<'PORT'
funcao primeira(): inteiro {
    seja v = triplo(4)
    retorne v
}
funcao triplo(inteiro x): inteiro {
    retorne x * 3
}
funcao inicio() {
    seja resultado = primeira()
    escreva(resultado)
}
PORT

aceitar "retorno_posterior_duplo" "2.500000" <<'PORT'
funcao usar(): duplo {
    seja x = metade(5)
    retorne x
}
funcao metade(inteiro numero): duplo {
    retorne numero / 2.0
}
funcao inicio() {
    seja resultado = usar()
    escreva(resultado)
}
PORT

aceitar "posterior_em_bloco" "7" <<'PORT'
funcao primeiro(): inteiro {
    se (verdadeiro) {
        seja dado = depois(6)
        retorne dado
    } senao {
        retorne 0
    }
}
funcao depois(inteiro n): inteiro {
    retorne n + 1
}
funcao inicio() {
    escreva(primeiro())
}
PORT

aceitar "retorno_posterior_texto" "ola" <<'PORT'
funcao primeira(): texto {
    seja mensagem = dizer()
    retorne mensagem
}
funcao dizer(): texto {
    retorne "ola"
}
funcao inicio() {
    escreva(primeira())
}
PORT

aceitar "modulo_local_posterior" "18" <<'PORT'
inclua "triplicador"
funcao inicio() {
    seja valor = triplicar(6)
    escreva(valor)
}
PORT

aceitar "modulo_transitivo" "20" <<'PORT'
inclua "intermediario"
funcao inicio() {
    seja total = multiplicarPorQuatro(5)
    escreva(total)
}
PORT

aceitar "modulo_apos_funcao" "16" <<'PORT'
funcao executar(): inteiro {
    seja r = somarDez(6)
    retorne r
}
inclua "base"
funcao inicio() {
    escreva(executar())
}
PORT

aceitar "modulo_importado_com_seja" "21" <<'PORT'
inclua "cliente"
funcao inicio() {
    seja x = executarModulo(7)
    escreva(x)
}
PORT

aceitar "externa_assinatura_posterior" "4" <<'PORT'
inclua texto
funcao calcular(): inteiro {
    seja n = comprimentoLocal("Ana")
    retorne n
}
funcao comprimentoLocal(texto texto): inteiro {
    retorne 4
}
funcao inicio() {
    escreva(calcular())
}
PORT

rejeitar "assinatura_ausente" "nao foi possivel inferir" <<'PORT'
funcao inicio() {
    seja dado = umaFuncaoInexistente(2)
}
PORT

rejeitar "retorno_vazio_posterior" "nao foi possivel inferir" <<'PORT'
funcao chama(): vazio {
    seja dado = terminar()
}
funcao terminar(): vazio {
    escreva("ok")
}
funcao inicio() {}
PORT

rejeitar "funcao_posterior_retorno_incompativel" "tipo" <<'PORT'
funcao primeiro(): inteiro {
    seja x = depois()
    retorne x
}
funcao depois(): texto {
    retorne "texto"
}
funcao inicio() {
    escreva(primeiro())
}
PORT

rejeitar "modulo_ausente" "nao encontrado" <<'PORT'
inclua "nao_existe"
funcao inicio() {
    seja dado = umaFuncao(2)
}
PORT

rejeitar "modulo_retorno_vazio" "nao foi possivel inferir" <<'PORT'
inclua "vazio"
funcao inicio() {
    seja resultado = rotinaVazia()
}
PORT

echo "V2.13: assinaturas antecipadas testadas."
