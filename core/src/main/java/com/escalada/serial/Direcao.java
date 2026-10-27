package com.escalada.serial;

/** As tres posicoes do painel fisico, com o caractere que o Arduino envia. */
public enum Direcao {
    ESQUERDA('L', "ESQUERDA"),
    ALTO('U', "ALTO"),
    DIREITA('R', "DIREITA");

    public final char codigo;
    public final String rotulo;

    Direcao(char codigo, String rotulo) {
        this.codigo = codigo;
        this.rotulo = rotulo;
    }

    /** Converte o caractere recebido na Direcao correspondente (null se for invalido). */
    public static Direcao deCodigo(char c) {
        for (Direcao d : values()) {
            if (d.codigo == Character.toUpperCase(c)) return d;
        }
        return null;
    }
}
