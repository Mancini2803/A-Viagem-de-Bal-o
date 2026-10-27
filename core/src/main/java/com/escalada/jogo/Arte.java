package com.escalada.jogo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.escalada.serial.Direcao;

/**
 * Funcoes de desenho reutilizaveis (formas procedurais, sem arquivos de imagem).
 * Todas esperam o ShapeRenderer em ShapeType.Filled.
 */
public final class Arte {
    private Arte() { }

    // Cada direcao tem uma cor propria (ajuda a reconhecer o botao do painel). Pinte os botoes fisicos igual!
    public static final Color COR_ESQ = new Color(0.25f, 0.85f, 1.00f, 1f);   // ciano
    public static final Color COR_ALTO = new Color(1.00f, 0.38f, 0.85f, 1f);  // magenta
    public static final Color COR_DIR = new Color(1.00f, 0.68f, 0.22f, 1f);   // laranja
    public static final Color DOURADO = new Color(1.00f, 0.85f, 0.30f, 1f);

    public static Color corDe(Direcao d) {
        switch (d) {
            case ESQUERDA: return COR_ESQ;
            case ALTO: return COR_ALTO;
            default: return COR_DIR;
        }
    }

    /** Define a cor do renderizador usando a cor c com outra transparencia. */
    public static void cor(ShapeRenderer s, Color c, float alpha) {
        s.setColor(c.r, c.g, c.b, alpha);
    }

    /** Retangulo com cantos arredondados. Use cores opacas (as partes se sobrepoem nos cantos). */
    public static void retanguloArredondado(ShapeRenderer s, float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w, h) / 2f);
        s.rect(x + r, y, w - 2f * r, h);
        s.rect(x, y + r, r, h - 2f * r);
        s.rect(x + w - r, y + r, r, h - 2f * r);
        s.circle(x + r, y + r, r, 12);
        s.circle(x + w - r, y + r, r, 12);
        s.circle(x + r, y + h - r, r, 12);
        s.circle(x + w - r, y + h - r, r, 12);
    }

    /** Anel (circunferencia com espessura) feito de segmentos. A cor/transparencia deve ser definida antes. */
    public static void anel(ShapeRenderer s, float cx, float cy, float raio, float espessura, int segmentos) {
        float passo = MathUtils.PI2 / segmentos;
        for (int i = 0; i < segmentos; i++) {
            float a0 = i * passo;
            float a1 = (i + 1) * passo;
            s.rectLine(cx + MathUtils.cos(a0) * raio, cy + MathUtils.sin(a0) * raio,
                       cx + MathUtils.cos(a1) * raio, cy + MathUtils.sin(a1) * raio, espessura);
        }
    }

    /** Arco (parte de uma circunferencia) com espessura. Angulos em graus, 0 = direita, 90 = cima. */
    public static void arco(ShapeRenderer s, float cx, float cy, float raio, float espessura,
                            float graus0, float graus1, int segmentos) {
        float a0 = graus0 * MathUtils.PI / 180f;
        float a1 = graus1 * MathUtils.PI / 180f;
        float passo = (a1 - a0) / segmentos;
        for (int i = 0; i < segmentos; i++) {
            float b0 = a0 + i * passo;
            float b1 = b0 + passo;
            s.rectLine(cx + MathUtils.cos(b0) * raio, cy + MathUtils.sin(b0) * raio,
                       cx + MathUtils.cos(b1) * raio, cy + MathUtils.sin(b1) * raio, espessura);
        }
    }

    /** Brilho suave: varios circulos translucidos empilhados. */
    public static void brilho(ShapeRenderer s, float cx, float cy, float raio, Color c, float intensidade) {
        for (int i = 0; i < 6; i++) {
            float f = 1f - i / 6f;
            s.setColor(c.r, c.g, c.b, intensidade * 0.045f);
            s.circle(cx, cy, raio * f, 28);
        }
    }

    /** Seta (cabeca + haste) apontando na direcao do botao. t = meia largura da seta. */
    public static void seta(ShapeRenderer s, float cx, float cy, float t, Direcao d) {
        switch (d) {
            case ESQUERDA:
                s.triangle(cx - t, cy, cx, cy + t * 0.9f, cx, cy - t * 0.9f);
                s.rect(cx, cy - t * 0.32f, t, t * 0.64f);
                break;
            case DIREITA:
                s.triangle(cx + t, cy, cx, cy + t * 0.9f, cx, cy - t * 0.9f);
                s.rect(cx - t, cy - t * 0.32f, t, t * 0.64f);
                break;
            default: // ALTO
                s.triangle(cx, cy + t, cx - t * 0.9f, cy, cx + t * 0.9f, cy);
                s.rect(cx - t * 0.32f, cy - t, t * 0.64f, t);
                break;
        }
    }

    /** Estrela de 5 pontas. R = raio externo. */
    public static void estrela(ShapeRenderer s, float cx, float cy, float R) {
        float r = R * 0.45f;
        for (int i = 0; i < 10; i++) {
            float a0 = MathUtils.PI / 2f + i * MathUtils.PI / 5f;
            float a1 = a0 + MathUtils.PI / 5f;
            float r0 = (i % 2 == 0) ? R : r;
            float r1 = (i % 2 == 0) ? r : R;
            s.triangle(cx, cy,
                       cx + MathUtils.cos(a0) * r0, cy + MathUtils.sin(a0) * r0,
                       cx + MathUtils.cos(a1) * r1, cy + MathUtils.sin(a1) * r1);
        }
    }
}
