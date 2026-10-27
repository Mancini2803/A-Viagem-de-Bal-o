package com.escalada.jogo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.escalada.serial.Direcao;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Registra cada tentativa (acerto, erro ou tempo esgotado) com o tempo de resposta e exporta em CSV.
 * O CSV abre direto no Excel/Google Planilhas para gerar os graficos do artigo.
 */
public class Metricas {

    public enum Resultado { ACERTO, ERRO, TEMPO_ESGOTADO }

    public static class Registro {
        public final int fase;
        public final int alvo;
        public final Direcao esperado;
        public final Direcao pressionado;   // null quando o tempo esgotou
        public final Resultado resultado;
        public final long tempoMs;

        Registro(int fase, int alvo, Direcao esperado, Direcao pressionado, Resultado resultado, long tempoMs) {
            this.fase = fase;
            this.alvo = alvo;
            this.esperado = esperado;
            this.pressionado = pressionado;
            this.resultado = resultado;
            this.tempoMs = tempoMs;
        }
    }

    /** Resumo estatistico. O tempo medio considera apenas os acertos. */
    public static class Resumo {
        public int acertos;
        public int erros;
        public int omissoes;      // tempo esgotado
        public long mediaMs;
        public long melhorMs;
        public long piorMs;
    }

    private final List<Registro> registros = new ArrayList<>();
    private final String idSessao =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

    public void registrar(int fase, int alvo, Direcao esperado, Direcao pressionado,
                          Resultado resultado, long tempoMs) {
        registros.add(new Registro(fase, alvo, esperado, pressionado, resultado, tempoMs));
    }

    /** @param fase numero da fase (1, 2, 3...) ou 0 para somar todas as fases. */
    public Resumo resumo(int fase) {
        Resumo r = new Resumo();
        long soma = 0;
        r.melhorMs = Long.MAX_VALUE;
        for (Registro g : registros) {
            if (fase != 0 && g.fase != fase) continue;
            switch (g.resultado) {
                case ACERTO:
                    r.acertos++;
                    soma += g.tempoMs;
                    r.melhorMs = Math.min(r.melhorMs, g.tempoMs);
                    r.piorMs = Math.max(r.piorMs, g.tempoMs);
                    break;
                case ERRO:
                    r.erros++;
                    break;
                case TEMPO_ESGOTADO:
                    r.omissoes++;
                    break;
            }
        }
        if (r.acertos > 0) r.mediaMs = soma / r.acertos;
        else r.melhorMs = 0;
        return r;
    }

    /** Grava metricas/sessao_AAAAMMDD_HHMMSS.csv na pasta de trabalho do jogo. */
    public FileHandle exportarCsv() {
        StringBuilder sb = new StringBuilder("sessao,fase,alvo,esperado,pressionado,resultado,tempo_ms\n");
        for (Registro g : registros) {
            sb.append(idSessao).append(',')
              .append(g.fase).append(',')
              .append(g.alvo).append(',')
              .append(g.esperado.rotulo).append(',')
              .append(g.pressionado == null ? "" : g.pressionado.rotulo).append(',')
              .append(g.resultado).append(',')
              .append(g.tempoMs).append('\n');
        }
        FileHandle arquivo = Gdx.files.local("metricas/sessao_" + idSessao + ".csv");
        arquivo.writeString(sb.toString(), false, "UTF-8");
        return arquivo;
    }
}
