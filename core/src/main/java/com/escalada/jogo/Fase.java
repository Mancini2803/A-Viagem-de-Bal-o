package com.escalada.jogo;

import com.escalada.serial.Direcao;

/**
 * Configuracao de uma fase. Para criar uma fase nova, basta adicionar uma linha em TODAS.
 *
 *  - limiteInicialMs / limiteFinalMs: tempo maximo para responder cada alvo (0 = sem limite).
 *    Se forem diferentes, o limite diminui gradualmente ao longo da fase (dificuldade crescente).
 *  - pausaMinMs / pausaMaxMs: intervalo (sorteado) entre um alvo e o proximo.
 *  - sequencia: ordem fixa dos alvos (repete se acabar). null = alvos aleatorios.
 */
public class Fase {
    public final String nome;
    public final String descricao;
    public final String destino;     // nome do Destino Final que o astronauta alcanca ao concluir a fase
    public final int totalAlvos;
    public final int limiteInicialMs;
    public final int limiteFinalMs;
    public final int pausaMinMs;
    public final int pausaMaxMs;
    public final Direcao[] sequencia;

    public Fase(String nome, String descricao, String destino, int totalAlvos, int limiteInicialMs, int limiteFinalMs,
                int pausaMinMs, int pausaMaxMs, Direcao[] sequencia) {
        this.nome = nome;
        this.descricao = descricao;
        this.destino = destino;
        this.totalAlvos = totalAlvos;
        this.limiteInicialMs = limiteInicialMs;
        this.limiteFinalMs = limiteFinalMs;
        this.pausaMinMs = pausaMinMs;
        this.pausaMaxMs = pausaMaxMs;
        this.sequencia = sequencia;
    }

    /** Limite de tempo (ms) do alvo de indice dado (0 = sem limite). */
    public int limiteParaAlvo(int indice) {
        if (limiteInicialMs <= 0) return 0;
        if (totalAlvos <= 1) return limiteInicialMs;
        float t = (float) indice / (totalAlvos - 1);
        return Math.round(limiteInicialMs + (limiteFinalMs - limiteInicialMs) * t);
    }

    private static final Direcao E = Direcao.ESQUERDA;
    private static final Direcao A = Direcao.ALTO;
    private static final Direcao D = Direcao.DIREITA;

    public static final Fase[] TODAS = {
        // Fase 1 - Iniciante: alvos aleatorios, SEM limite de tempo (o jogo espera o paciente).
        new Fase("Iniciante", "Sem limite de tempo. Aperte o botao da agarradeira acesa.", "Base Orbital",
                 10, 0, 0, 700, 900, null),

        // Fase 2 - Ritmo: sequencia pre-definida, 3 s para cada movimento.
        new Fase("Ritmo", "Siga a sequencia. Voce tem 3 segundos por movimento.", "Estacao Lunar",
                 15, 3000, 3000, 500, 500,
                 new Direcao[] { E, A, D, A, E, D, D, A, E, A, D, E, A, D, E }),

        // Fase 3 - Agilidade: alvos aleatorios e rapidos, limite cai de 1,6 s para 0,9 s (Whack-a-Mole).
        new Fase("Agilidade", "Agarradeiras surgem rapido! Responda antes que apaguem.", "Portal Estelar",
                 20, 1600, 900, 250, 600, null)
    };
}
