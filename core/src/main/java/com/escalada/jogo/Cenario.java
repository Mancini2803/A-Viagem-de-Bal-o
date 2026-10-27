package com.escalada.jogo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.escalada.Main;

/**
 * Fundo do jogo: espaco em degrade, nebulosas, planeta, estrelas com paralaxe e a estrutura espacial
 * (paineis, vigas, janelas e luzes) que desce conforme o astronauta sobe. Cada fase tem um tema de cor.
 */
public class Cenario {
    private static final float ALTURA_SEGMENTO = 90f;
    private static final float EST_X0 = 130f;      // borda esquerda da estrutura
    private static final float EST_X1 = 830f;      // borda direita

    //                                    fase 1 (azul)        fase 2 (roxo)         fase 3 (alerta)
    private static final float[][] TOPO = { {0.01f, 0.02f, 0.10f}, {0.04f, 0.01f, 0.10f}, {0.07f, 0.01f, 0.04f} };
    private static final float[][] BASE = { {0.05f, 0.10f, 0.28f}, {0.17f, 0.07f, 0.30f}, {0.27f, 0.06f, 0.12f} };
    private static final float[][] NEB_A = { {0.15f, 0.35f, 0.90f}, {0.60f, 0.20f, 0.90f}, {0.90f, 0.20f, 0.20f} };
    private static final float[][] NEB_B = { {0.10f, 0.75f, 0.85f}, {0.95f, 0.30f, 0.65f}, {1.00f, 0.60f, 0.10f} };
    private static final float[][] PLANETA = { {0.30f, 0.38f, 0.78f}, {0.62f, 0.32f, 0.70f}, {0.75f, 0.32f, 0.25f} };

    private final Color cTopo = new Color();
    private final Color cBase = new Color();
    private final Color cNebA = new Color();
    private final Color cNebB = new Color();
    private final Color cPlaneta = new Color();
    private final Color cPreto0 = new Color(0f, 0f, 0f, 0f);
    private final Color cPreto1 = new Color(0f, 0f, 0f, 0.55f);
    private final Color cPainelBaixo = new Color(0.10f, 0.12f, 0.22f, 1f);
    private final Color cPainelCima = new Color(0.15f, 0.17f, 0.30f, 1f);
    private final Color cAcento = new Color();

    private final float[] estX = new float[90];
    private final float[] estY = new float[90];
    private final float[] estTam = new float[90];
    private final float[] nebX = { 150f, 420f, 700f, 880f, 60f };
    private final float[] nebY = { 120f, 360f, 220f, 470f, 430f };

    public Cenario() {
        for (int i = 0; i < estX.length; i++) {
            estX[i] = MathUtils.random(0f, Main.LARGURA);
            estY[i] = MathUtils.random(0f, Main.ALTURA);
            estTam[i] = 1.2f + (i % 3) * 0.9f;
        }
    }

    /** Altura (y na tela) da superficie da plataforma de destino; ela desce conforme o astronauta sobe. */
    public float yPlataforma(float alt, float alturaDestino) {
        return Astronauta.Y_SOLA + (alturaDestino - alt);
    }

    /** Posicao horizontal central da placa "Destino Final" e deslocamento vertical acima da plataforma. */
    public static final float SINAL_X = 255f;
    public static final float SINAL_DY = 150f;
    public static final float SINAL_ALTURA = 54f;

    public void desenhar(ShapeRenderer s, float alt, float t, int tema, float alturaDestino) {
        int k = MathUtils.clamp(tema, 0, TOPO.length - 1);
        cTopo.set(TOPO[k][0], TOPO[k][1], TOPO[k][2], 1f);
        cBase.set(BASE[k][0], BASE[k][1], BASE[k][2], 1f);
        cNebA.set(NEB_A[k][0], NEB_A[k][1], NEB_A[k][2], 1f);
        cNebB.set(NEB_B[k][0], NEB_B[k][1], NEB_B[k][2], 1f);
        cPlaneta.set(PLANETA[k][0], PLANETA[k][1], PLANETA[k][2], 1f);
        cAcento.set(NEB_B[k][0], NEB_B[k][1], NEB_B[k][2], 1f);

        // Espaco em degrade
        s.rect(0, 0, Main.LARGURA, Main.ALTURA, cBase, cBase, cTopo, cTopo);

        // Nebulosas (movem-se bem devagar)
        float ciclo = Main.ALTURA + 400f;
        for (int i = 0; i < nebX.length; i++) {
            float y = ((nebY[i] - alt * 0.06f) % ciclo + ciclo) % ciclo - 200f;
            Arte.brilho(s, nebX[i], y, 230f, (i % 2 == 0) ? cNebA : cNebB, 1.0f);
        }

        // Planeta gigante espiando pela direita
        float py = 420f - alt * 0.05f;
        Arte.brilho(s, 900f, py, 190f, cAcento, 0.9f);
        s.setColor(cPlaneta);
        s.circle(900f, py, 100f, 60);
        s.setColor(1f, 1f, 1f, 0.07f);
        s.circle(870f, py + 25f, 60f, 40);
        s.circle(860f, py + 32f, 42f, 36);
        s.circle(852f, py + 38f, 26f, 30);
        s.setColor(0f, 0f, 0f, 0.10f);
        s.circle(940f, py - 40f, 70f, 40);

        // Estrelas em 3 camadas de profundidade, com brilho pulsante
        float[] vel = { 0.08f, 0.20f, 0.40f };
        for (int i = 0; i < estX.length; i++) {
            int camada = i % 3;
            float y = ((estY[i] - alt * vel[camada]) % Main.ALTURA + Main.ALTURA) % Main.ALTURA;
            float brilho = 0.45f + 0.45f * MathUtils.sin(t * (1.5f + camada) + i);
            s.setColor(1f, 1f, 1f, brilho);
            s.rect(estX[i], y, estTam[i], estTam[i]);
        }

        float yPlat = yPlataforma(alt, alturaDestino);
        desenharEstrutura(s, alt, t, yPlat);
        desenharDestino(s, t, tema, yPlat);

        // Vinheta (escurece as bordas e foca o centro)
        s.rect(0, 0, Main.LARGURA, 70f, cPreto1, cPreto1, cPreto0, cPreto0);
        s.rect(0, Main.ALTURA - 70f, Main.LARGURA, 70f, cPreto0, cPreto0, cPreto1, cPreto1);
        s.rect(0, 0, 90f, Main.ALTURA, cPreto1, cPreto0, cPreto0, cPreto1);
        s.rect(Main.LARGURA - 90f, 0, 90f, Main.ALTURA, cPreto0, cPreto1, cPreto1, cPreto0);
    }

    /** A estrutura termina na plataforma de destino (yCorte): acima dela so existe espaco aberto. */
    private void desenharEstrutura(ShapeRenderer s, float alt, float t, float yCorte) {
        float larguraTotal = EST_X1 - EST_X0;
        float topo = Math.min(Main.ALTURA, yCorte);
        if (topo <= 0f) return;

        int k0 = (int) Math.floor(alt / ALTURA_SEGMENTO);
        float frac = alt - k0 * ALTURA_SEGMENTO;

        // Fundo metalico
        s.setColor(0.08f, 0.09f, 0.17f, 1f);
        s.rect(EST_X0, 0, larguraTotal, topo);

        for (int j = 0; j <= 7; j++) {
            int indice = k0 + j;
            float y = j * ALTURA_SEGMENTO - frac;
            if (y >= topo) continue;                                   // segmento acima da plataforma

            // Painel do segmento (degrade vertical), cortado na plataforma
            float alturaPainel = Math.min(ALTURA_SEGMENTO - 8f, topo - (y + 8f));
            if (alturaPainel > 0f) {
                s.rect(EST_X0 + 12f, y + 8f, larguraTotal - 24f, alturaPainel,
                       cPainelBaixo, cPainelBaixo, cPainelCima, cPainelCima);
            }

            // Viga horizontal com realce
            if (y + 8f <= topo) {
                s.setColor(0.28f, 0.33f, 0.54f, 1f);
                s.rect(EST_X0, y, larguraTotal, 8f);
                s.setColor(0.46f, 0.52f, 0.78f, 1f);
                s.rect(EST_X0, y + 6f, larguraTotal, 2f);
                s.setColor(0.52f, 0.58f, 0.82f, 1f);
                s.circle(EST_X0 + 22f, y + 4f, 3f, 8);
                s.circle(EST_X1 - 22f, y + 4f, 3f, 8);
            }

            // Janelas iluminadas (padrao fixo no "mundo", por isso rolam junto com a estrutura)
            if (indice % 3 == 0 && y + 66f < topo) {
                s.setColor(0.04f, 0.05f, 0.11f, 1f);
                Arte.retanguloArredondado(s, 336f, y + 26f, 66f, 40f, 7f);
                s.setColor(0.35f, 0.55f, 0.72f, 1f);
                Arte.retanguloArredondado(s, 341f, y + 31f, 56f, 30f, 5f);
            }
            if (indice % 4 == 1 && y + 66f < topo) {
                s.setColor(0.04f, 0.05f, 0.11f, 1f);
                Arte.retanguloArredondado(s, 574f, y + 26f, 66f, 40f, 7f);
                s.setColor(0.55f, 0.50f, 0.38f, 1f);
                Arte.retanguloArredondado(s, 579f, y + 31f, 56f, 30f, 5f);
            }

            // Luz de alerta piscando
            if (indice % 5 == 0 && y + 52f < topo) {
                float pisca = 0.35f + 0.55f * MathUtils.sin(t * 4f + indice);
                Arte.brilho(s, 800f, y + 46f, 26f, Arte.COR_ALTO, pisca);
                s.setColor(1f, 0.25f, 0.30f, 0.4f + 0.6f * pisca);
                s.circle(800f, y + 46f, 5f, 12);
            }
        }

        // Trilhos de luz nas laterais
        float pulso = 0.55f + 0.25f * MathUtils.sin(t * 2f);
        s.setColor(cAcento.r, cAcento.g, cAcento.b, 0.15f);
        s.rect(EST_X0 + 8f, 0, 14f, topo);
        s.rect(EST_X1 - 22f, 0, 14f, topo);
        s.setColor(cAcento.r, cAcento.g, cAcento.b, pulso);
        s.rect(EST_X0 + 13f, 0, 3f, topo);
        s.rect(EST_X1 - 16f, 0, 3f, topo);

        // Moldura externa
        s.setColor(0.20f, 0.24f, 0.40f, 1f);
        s.rect(EST_X0 - 6f, 0, 6f, topo);
        s.rect(EST_X1, 0, 6f, topo);
    }

    /**
     * Plataforma de "Destino Final": deck com luzes, placa, farol, bandeira e uma construcao diferente por fase
     * (cupula, antena parabolica, portal). y = altura da superficie do deck.
     */
    private void desenharDestino(ShapeRenderer s, float t, int tema, float y) {
        if (y > Main.ALTURA + 330f) return;     // ainda muito acima da tela
        float x0 = EST_X0 - 6f;
        float w = (EST_X1 - EST_X0) + 12f;
        float gx = 660f;                         // centro da construcao principal (lado direito)

        // Construcao principal (atras do deck)
        switch (tema) {
            case 0: // cupula de vidro
                Arte.brilho(s, gx, y + 40f, 170f, cAcento, 0.6f);
                s.setColor(cAcento.r, cAcento.g, cAcento.b, 0.20f);
                s.arc(gx, y, 120f, 0f, 180f, 40);
                s.setColor(0.55f, 0.62f, 0.88f, 1f);
                Arte.arco(s, gx, y, 120f, 6f, 0f, 180f, 30);
                for (int a = 45; a <= 135; a += 45) {
                    float ang = a * MathUtils.PI / 180f;
                    s.rectLine(gx, y, gx + MathUtils.cos(ang) * 120f, y + MathUtils.sin(ang) * 120f, 3f);
                }
                s.setColor(0.95f, 0.85f, 0.5f, 0.85f);
                s.circle(gx - 50f, y + 40f, 6f, 10);
                s.circle(gx + 30f, y + 70f, 5f, 10);
                s.circle(gx + 62f, y + 34f, 6f, 10);
                break;
            case 1: // antena parabolica
                s.setColor(0.55f, 0.62f, 0.88f, 1f);
                s.rectLine(gx, y, gx, y + 100f, 12f);
                s.setColor(0.80f, 0.84f, 0.95f, 1f);
                s.arc(gx, y + 120f, 80f, 205f, 130f, 30);
                s.setColor(cAcento.r, cAcento.g, cAcento.b, 0.9f);
                Arte.arco(s, gx, y + 120f, 80f, 5f, 205f, 335f, 24);
                s.setColor(0.55f, 0.62f, 0.88f, 1f);
                s.rectLine(gx, y + 120f, gx + 34f, y + 176f, 5f);
                float sinal = 0.5f + 0.5f * MathUtils.sin(t * 5f);
                Arte.brilho(s, gx + 34f, y + 178f, 30f, cAcento, sinal);
                s.setColor(1f, 1f, 1f, 0.9f);
                s.circle(gx + 34f, y + 178f, 5f, 10);
                break;
            default: // portal estelar
                float pulsoP = 1f + 0.04f * MathUtils.sin(t * 3f);
                Arte.brilho(s, gx, y + 120f, 190f * pulsoP, cAcento, 1.0f);
                s.setColor(cAcento.r, cAcento.g, cAcento.b, 0.18f);
                s.circle(gx, y + 120f, 92f, 48);
                s.setColor(0.85f, 0.88f, 1f, 1f);
                Arte.anel(s, gx, y + 120f, 100f * pulsoP, 12f, 48);
                s.setColor(cAcento.r, cAcento.g, cAcento.b, 0.55f);
                Arte.anel(s, gx, y + 120f, 76f, 5f, 40);
                Arte.anel(s, gx, y + 120f, 52f + 8f * MathUtils.sin(t * 4f), 3f, 32);
                break;
        }

        // Farol com feixe de luz girando
        float fx = 190f;
        s.setColor(0.55f, 0.62f, 0.88f, 1f);
        s.rectLine(fx, y, fx, y + 64f, 9f);
        float ang = t * 2f;
        s.setColor(cAcento.r, cAcento.g, cAcento.b, 0.14f);
        s.triangle(fx, y + 72f,
                   fx + MathUtils.cos(ang - 0.14f) * 240f, y + 72f + MathUtils.sin(ang - 0.14f) * 240f,
                   fx + MathUtils.cos(ang + 0.14f) * 240f, y + 72f + MathUtils.sin(ang + 0.14f) * 240f);
        float luz = 0.5f + 0.5f * MathUtils.sin(t * 6f);
        Arte.brilho(s, fx, y + 72f, 28f, cAcento, luz);
        s.setColor(1f, 1f, 1f, 0.9f);
        s.circle(fx, y + 72f, 6f, 12);

        // Bandeira
        float bx = 780f;
        s.setColor(0.85f, 0.88f, 1f, 1f);
        s.rectLine(bx, y, bx, y + 124f, 5f);
        s.circle(bx, y + 126f, 5f, 10);
        s.setColor(cAcento.r, cAcento.g, cAcento.b, 1f);
        float onda = MathUtils.sin(t * 3f) * 6f;
        s.triangle(bx, y + 118f, bx, y + 80f, bx + 62f + onda, y + 99f);

        // Placa "Destino Final" (o texto e escrito pela tela de jogo)
        float py0 = y + SINAL_DY;
        s.setColor(0.55f, 0.62f, 0.88f, 1f);
        s.rectLine(SINAL_X - 62f, y, SINAL_X - 62f, py0, 5f);
        s.rectLine(SINAL_X + 62f, y, SINAL_X + 62f, py0, 5f);
        s.setColor(cAcento.r * 0.9f, cAcento.g * 0.9f, cAcento.b * 0.9f, 1f);
        Arte.retanguloArredondado(s, SINAL_X - 96f, py0 - 2f, 192f, SINAL_ALTURA + 4f, 12f);
        s.setColor(0.06f, 0.08f, 0.17f, 1f);
        Arte.retanguloArredondado(s, SINAL_X - 94f, py0, 188f, SINAL_ALTURA, 10f);

        // Deck de pouso
        s.setColor(0.24f, 0.28f, 0.48f, 1f);
        s.rect(x0, y - 36f, w, 36f);
        s.setColor(0.50f, 0.56f, 0.82f, 1f);
        s.rect(x0, y - 4f, w, 4f);
        s.setColor(0.12f, 0.14f, 0.26f, 1f);
        s.rect(x0, y - 36f, w, 5f);
        for (int i = 0; i < 16; i++) {
            float lx = x0 + 22f + i * 44f;
            float a = 0.35f + 0.65f * (0.5f + 0.5f * MathUtils.sin(t * 4f - i * 0.7f));
            s.setColor(cAcento.r, cAcento.g, cAcento.b, a);
            s.circle(lx, y - 18f, 4f, 8);
        }

        // Marca de pouso onde o astronauta chega
        Arte.brilho(s, 480f, y + 2f, 100f, cAcento, 0.6f);
        s.setColor(cAcento.r, cAcento.g, cAcento.b, 0.85f);
        s.ellipse(420f, y - 3f, 120f, 8f);
    }
}
