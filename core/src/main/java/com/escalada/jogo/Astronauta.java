package com.escalada.jogo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.escalada.serial.Direcao;

/**
 * O astronauta, com esqueleto simples e cinematica inversa (IK) de 2 ossos nos bracos e pernas.
 *
 * Ciclo de escalada (a cada acerto): a mao agarra a agarradeira, que "desce" em relacao ao corpo enquanto
 * o astronauta se puxa para cima; o tronco se inclina para o lado da mao, o quadril sobe e desce, e os pes
 * se alternam (um pe sobe e e reposicionado enquanto o outro empurra). Um pequeno propulsor ajuda no impulso.
 *
 * Estados: repouso -> alcancando (agarradeira acesa) -> puxando (acerto) -> repouso ... -> celebrando (destino).
 */
public final class Astronauta {
    /** Altura (y) em que as solas das botas tocam a plataforma de destino quando o astronauta chega. */
    public static final float Y_SOLA = 66f;

    private static final float DURACAO_PUXADA = 0.55f;
    private static final float AFUNDA = 70f;           // quanto a agarradeira desce em relacao ao corpo na puxada
    private static final float BRACO_1 = 68f, BRACO_2 = 64f;
    private static final float PERNA_1 = 50f, PERNA_2 = 50f;

    private final float cx;
    private final float baseY;                          // altura do quadril em repouso

    // Estado da animacao
    private float lean;                                 // inclinacao do tronco (radianos, + = para a esquerda)
    private float puxarT = 1f;
    private boolean puxando;
    private Direcao dirPuxada;
    private float grabX, grabY;
    private final Color corPegada = new Color(1f, 1f, 1f, 1f);
    private int passoPar;
    private boolean celebrando;
    private float celebT;
    private float impulso;
    private Direcao alvoAtual;
    private float maoEsqX, maoEsqY, maoDirX, maoDirY;

    // Variaveis auxiliares (evitam criar objetos a cada frame)
    private float hipX, hipY, vx, vy, rx, ry;           // quadril e eixos do tronco
    private float px, py;                               // saida de p()
    private float jx, jy, ex, ey;                       // saida de ik(): junta (cotovelo/joelho) e ponta

    public Astronauta(float x, float baseY) {
        this.cx = x;
        this.baseY = baseY;
        resetar();
    }

    /** Volta ao estado inicial (usar ao comecar uma fase). */
    public void resetar() {
        lean = 0f;
        puxarT = 1f;
        puxando = false;
        celebrando = false;
        celebT = 0f;
        impulso = 0f;
        passoPar = 0;
        alvoAtual = null;
        maoEsqX = cx - 58f;
        maoEsqY = baseY + 30f;
        maoDirX = cx + 58f;
        maoDirY = baseY + 30f;
    }

    /** Inicia a puxada: a mao agarra (hx, hy) e o astronauta sobe. */
    public void puxar(Direcao d, float hx, float hy, Color cor) {
        puxando = true;
        puxarT = 0f;
        dirPuxada = d;
        grabX = hx;
        grabY = hy;
        corPegada.set(cor);
        passoPar++;
        impulso = 0.6f;
    }

    /** Chegou ao destino: comemora com os bracos para cima. */
    public void celebrar() {
        celebrando = true;
        celebT = 0f;
        puxando = false;
    }

    /** @param alvo agarradeira ativa (null = sem alvo). */
    public void atualizar(float dt, Direcao alvo, float[] holdX, float[] holdY) {
        alvoAtual = alvo;
        impulso = Math.max(0f, impulso - dt * 1.6f);
        if (celebrando) celebT += dt;
        if (puxando) {
            puxarT += dt / DURACAO_PUXADA;
            if (puxarT >= 1f) {
                puxarT = 1f;
                puxando = false;
            }
        }

        // Inclinacao do tronco para o lado da mao que trabalha.
        Direcao ref = puxando ? dirPuxada : alvo;
        float alvoLean = 0f;
        if (!celebrando && ref != null) {
            if (ref == Direcao.ESQUERDA) alvoLean = 0.17f;
            else if (ref == Direcao.DIREITA) alvoLean = -0.17f;
        }
        lean += (alvoLean - lean) * Math.min(1f, 9f * dt);

        // Alvos das maos.
        float restoEsqX = cx - 58f, restoDirX = cx + 58f, restoY = baseY + 30f;
        float aEx = restoEsqX, aEy = restoY, aDx = restoDirX, aDy = restoY;
        boolean agarrando = false;

        if (celebrando) {
            float onda = MathUtils.sin(celebT * 9f) * 10f;
            aEx = cx - 66f;
            aEy = baseY + 196f + onda;
            aDx = cx + 66f;
            aDy = baseY + 196f - onda;
        } else if (puxando) {
            float k = 1f - (1f - puxarT) * (1f - puxarT);            // easeOut: a agarradeira desce com o mundo
            float gy = grabY - AFUNDA * k;
            agarrando = true;
            switch (dirPuxada) {
                case ESQUERDA: aEx = grabX; aEy = gy; break;
                case DIREITA:  aDx = grabX; aDy = gy; break;
                default:       aEx = grabX - 16f; aEy = gy; aDx = grabX + 16f; aDy = gy; break;
            }
        } else if (alvo != null) {
            float hx = holdX[alvo.ordinal()];
            float hy = holdY[alvo.ordinal()];
            switch (alvo) {
                case ESQUERDA: aEx = hx; aEy = hy; break;
                case DIREITA:  aDx = hx; aDy = hy; break;
                default:       aEx = hx - 16f; aEy = hy; aDx = hx + 16f; aDy = hy; break;
            }
        }

        // Mao agarrada acompanha exatamente a agarradeira; as demais suavizam o movimento.
        float suave = Math.min(1f, 14f * dt);
        boolean exato = agarrando && puxarT > 0.12f;
        if (exato && dirPuxada != Direcao.DIREITA) { maoEsqX = aEx; maoEsqY = aEy; }
        else { maoEsqX += (aEx - maoEsqX) * suave; maoEsqY += (aEy - maoEsqY) * suave; }
        if (exato && dirPuxada != Direcao.ESQUERDA) { maoDirX = aDx; maoDirY = aDy; }
        else { maoDirX += (aDx - maoDirX) * suave; maoDirY += (aDy - maoDirY) * suave; }
    }

    // ================================================================ DESENHO

    /** Ponto no referencial do tronco: ox para a direita do astronauta, oy ao longo do tronco. Resultado em px, py. */
    private void p(float ox, float oy) {
        px = hipX + rx * ox + vx * oy;
        py = hipY + ry * ox + vy * oy;
    }

    /**
     * IK de 2 ossos. Origem (ax, ay), alvo (tx, ty). Resultado: junta em (jx, jy) e ponta em (ex, ey).
     * lado = +1 dobra a junta para a direita da tela, -1 para a esquerda (cotovelos e joelhos para fora).
     */
    private void ik(float ax, float ay, float tx, float ty, float l1, float l2, float lado) {
        float dx = tx - ax, dy = ty - ay;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 0.001f) {
            d = 0.001f;
            dx = 0f;
            dy = -0.001f;
        }
        float ux = dx / d, uy = dy / d;
        float dd = Math.min(d, (l1 + l2) * 0.999f);
        dd = Math.max(dd, Math.abs(l1 - l2) + 0.5f);
        float a = (l1 * l1 - l2 * l2 + dd * dd) / (2f * dd);
        float h2 = l1 * l1 - a * a;
        float h = h2 > 0f ? (float) Math.sqrt(h2) : 0f;
        float bx = ax + ux * a, by = ay + uy * a;
        float j1x = bx - uy * h, j1y = by + ux * h;
        float j2x = bx + uy * h, j2y = by - ux * h;
        if ((j1x - j2x) * lado >= 0f) {
            jx = j1x;
            jy = j1y;
        } else {
            jx = j2x;
            jy = j2y;
        }
        ex = ax + ux * dd;
        ey = ay + uy * dd;
    }

    public void desenhar(ShapeRenderer s, float tempo) {
        // Deslocamento vertical do corpo: puxada, pulo de comemoracao ou leve flutuacao.
        float bob = MathUtils.sin(tempo * 2f) * 3f;
        float dy = 0f;
        if (puxando) {
            dy = 14f * MathUtils.sin(puxarT * MathUtils.PI);
        } else if (celebrando) {
            dy = Math.abs(MathUtils.sin(celebT * 6f)) * 16f;
            bob = 0f;
        } else if (alvoAtual == Direcao.ALTO) {
            dy = 6f;
        }
        hipX = cx;
        hipY = baseY + bob + dy;
        vx = -MathUtils.sin(lean);
        vy = MathUtils.cos(lean);
        rx = MathUtils.cos(lean);
        ry = MathUtils.sin(lean);

        desenharPegada(s);
        desenharMochila(s, tempo);
        desenharPernas(s, dy);
        desenharTronco(s, tempo);
        desenharBracos(s);
        desenharCabeca(s, tempo);
    }

    /** Brilho da agarradeira que o astronauta esta segurando (desce junto com a mao e some). */
    private void desenharPegada(ShapeRenderer s) {
        if (!puxando) return;
        float k = 1f - (1f - puxarT) * (1f - puxarT);
        float gy = grabY - AFUNDA * k;
        float fade = 1f - puxarT;
        Arte.brilho(s, grabX, gy, 70f, corPegada, fade);
        s.setColor(corPegada.r, corPegada.g, corPegada.b, fade);
        s.circle(grabX, gy, 22f, 24);
    }

    private void desenharMochila(ShapeRenderer s, float tempo) {
        p(0f, 6f);
        float ax = px, ay = py;
        p(0f, 70f);
        s.setColor(0.50f, 0.54f, 0.68f, 1f);
        s.rectLine(ax, ay, px, py, 94f);

        // Chamas dos dois propulsores
        float chama = 8f + 5f * MathUtils.sin(tempo * 30f) + impulso * 46f;
        for (int k = 0; k < 2; k++) {
            float sg = (k == 0) ? -1f : 1f;
            p(sg * 26f, 4f);
            float nx = px, ny = py;
            s.setColor(0.36f, 0.39f, 0.55f, 1f);
            s.rectLine(nx - rx * 12f, ny - ry * 12f, nx + rx * 12f, ny + ry * 12f, 10f);
            s.setColor(1f, 0.55f, 0.10f, 0.9f);
            s.triangle(nx - rx * 10f, ny - ry * 10f, nx + rx * 10f, ny + ry * 10f, nx - vx * chama, ny - vy * chama);
            s.setColor(1f, 0.95f, 0.50f, 0.95f);
            s.triangle(nx - rx * 5f, ny - ry * 5f, nx + rx * 5f, ny + ry * 5f, nx - vx * chama * 0.6f, ny - vy * chama * 0.6f);
        }
    }

    private void desenharPernas(ShapeRenderer s, float dy) {
        // Qual perna da o passo: alterna a cada puxada.
        int pernaPasso = passoPar % 2;
        float levante = puxando ? 46f * MathUtils.sin(puxarT * MathUtils.PI) : 0f;
        float saltoPe = celebrando ? dy * 0.9f : 0f;

        for (int k = 0; k < 2; k++) {
            float sg = (k == 0) ? -1f : 1f;
            p(sg * 16f, -4f);
            float hx = px, hy = py;

            float fx = cx + sg * 32f;
            float fy = baseY - 100f + saltoPe;
            if (puxando && k == pernaPasso) {
                fy += levante;
                fx -= sg * 10f * MathUtils.sin(puxarT * MathUtils.PI);   // o pe que sobe vai um pouco para dentro
            }
            ik(hx, hy, fx, fy, PERNA_1, PERNA_2, sg);

            s.setColor(0.90f, 0.91f, 0.96f, 1f);
            s.rectLine(hx, hy, jx, jy, 25f);
            s.rectLine(jx, jy, ex, ey, 22f);
            s.circle(jx, jy, 12f, 14);
            s.circle(hx, hy, 12.5f, 14);
            // Bota
            s.setColor(0.30f, 0.32f, 0.46f, 1f);
            Arte.retanguloArredondado(s, ex - 18f, ey - 14f, 36f, 18f, 6f);
            s.setColor(0.18f, 0.19f, 0.30f, 1f);
            s.rect(ex - 18f, ey - 14f, 36f, 4f);
        }
    }

    private void desenharTronco(ShapeRenderer s, float tempo) {
        p(0f, 2f);
        float ax = px, ay = py;
        p(0f, 72f);
        float bx = px, by = py;

        s.setColor(0.96f, 0.96f, 1f, 1f);
        s.circle(ax, ay, 36f, 28);
        s.circle(bx, by, 37f, 28);
        s.rectLine(ax, ay, bx, by, 74f);

        // Sombreamento lateral
        s.setColor(0.84f, 0.86f, 0.94f, 1f);
        p(22f, 8f);
        float sx0 = px, sy0 = py;
        p(22f, 68f);
        s.rectLine(sx0, sy0, px, py, 22f);

        // Cinto
        s.setColor(0.33f, 0.36f, 0.54f, 1f);
        p(-35f, 14f);
        float cx0 = px, cy0 = py;
        p(35f, 14f);
        s.rectLine(cx0, cy0, px, py, 9f);

        // Painel de controle
        s.setColor(0.16f, 0.18f, 0.32f, 1f);
        p(0f, 34f);
        float pxa = px, pya = py;
        p(0f, 56f);
        s.rectLine(pxa, pya, px, py, 40f);
        p(-10f, 50f);
        s.setColor(0.95f, 0.30f, 0.30f, 1f);
        s.circle(px, py, 4f, 10);
        p(0f, 50f);
        s.setColor(0.35f, 0.95f, 0.50f, 1f);
        s.circle(px, py, 4f, 10);
        p(10f, 50f);
        s.setColor(0.30f, 0.80f, 1f, 0.6f + 0.4f * MathUtils.sin(tempo * 5f));
        s.circle(px, py, 4f, 10);
        s.setColor(0.45f, 0.50f, 0.70f, 1f);
        p(-14f, 40f);
        float ta = px, tb = py;
        p(14f, 40f);
        s.rectLine(ta, tb, px, py, 3f);

        // Pescoco
        s.setColor(0.40f, 0.43f, 0.60f, 1f);
        p(0f, 78f);
        float na = px, nb = py;
        p(0f, 94f);
        s.rectLine(na, nb, px, py, 46f);
    }

    private void desenharBracos(ShapeRenderer s) {
        for (int k = 0; k < 2; k++) {
            float sg = (k == 0) ? -1f : 1f;
            p(sg * 36f, 62f);
            float ox = px, oy = py;
            float mx = (k == 0) ? maoEsqX : maoDirX;
            float my = (k == 0) ? maoEsqY : maoDirY;
            ik(ox, oy, mx, my, BRACO_1, BRACO_2, sg);

            s.setColor(0.90f, 0.91f, 0.96f, 1f);
            s.rectLine(ox, oy, jx, jy, 19f);
            s.rectLine(jx, jy, ex, ey, 17f);
            s.circle(ox, oy, 11f, 14);
            s.circle(jx, jy, 9.5f, 14);
            s.setColor(0.33f, 0.36f, 0.54f, 1f);
            s.circle(ex, ey, 12f, 16);
            s.setColor(0.97f, 0.72f, 0.20f, 1f);
            s.circle(ex, ey, 9.5f, 16);
        }
    }

    private void desenharCabeca(ShapeRenderer s, float tempo) {
        p(0f, 126f);
        float hx = px, hy = py;

        s.setColor(0.97f, 0.97f, 1f, 1f);
        s.circle(hx, hy, 38f, 36);
        s.setColor(0.86f, 0.88f, 0.96f, 1f);
        s.circle(hx + 15f, hy - 8f, 22f, 24);
        s.setColor(0.97f, 0.97f, 1f, 1f);
        s.circle(hx, hy, 35f, 36);

        // Visor
        s.setColor(0.07f, 0.12f, 0.32f, 1f);
        s.ellipse(hx - 28f, hy - 15f, 56f, 38f);
        s.setColor(0.20f, 0.42f, 0.85f, 1f);
        s.ellipse(hx - 22f, hy - 7f, 40f, 22f);
        s.setColor(1f, 1f, 1f, 0.75f);
        s.circle(hx - 13f, hy + 14f, 4f, 10);
        s.circle(hx - 4f, hy + 17f, 2.2f, 8);

        // Antena
        s.setColor(0.55f, 0.58f, 0.72f, 1f);
        s.rectLine(hx + 28f, hy + 22f, hx + 40f, hy + 46f, 3f);
        s.setColor(1f, 0.25f, 0.25f, 0.5f + 0.5f * MathUtils.sin(tempo * 4f));
        s.circle(hx + 40f, hy + 48f, 4f, 10);
    }
}
