package com.escalada.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.escalada.Main;
import com.escalada.jogo.Arte;
import com.escalada.jogo.Astronauta;
import com.escalada.jogo.Cenario;
import com.escalada.jogo.Fase;
import com.escalada.jogo.Metricas;
import com.escalada.jogo.Metricas.Resultado;
import com.escalada.jogo.Metricas.Resumo;
import com.escalada.serial.Direcao;
import com.escalada.serial.SerialListener.Entrada;

/**
 * Tela principal do jogo "Escalada Espacial".
 *
 * Maquina de estados:
 *   MENU -> ESPERANDO_INICIO -> JOGANDO <-> PAUSADO
 *   JOGANDO -> CHEGADA (sobe ate o Destino Final) -> RESUMO -> (proxima fase: ESPERANDO_INICIO) ... -> FIM -> MENU
 *
 * Menu principal e pausa: pelos botoes do painel (ESQUERDA / ALTO / DIREITA) ou pelo teclado do terapeuta
 * (ENTER, P, R, M, ESC). A jogada em si e controlada somente pelo painel.
 *
 * O paciente navega SO pelo painel: qualquer botao avanca as telas de texto.
 * O tempo de resposta e medido entre o acendimento da agarradeira e o instante (System.nanoTime)
 * em que o SerialListener recebeu o byte, sem depender do FPS.
 *
 * Organizacao deste arquivo: LOOP -> LOGICA -> EFEITOS -> DESENHO (formas) -> TEXTOS.
 */
public class GameScreen extends ScreenAdapter {

    private enum Estado { MENU, ESPERANDO_INICIO, JOGANDO, PAUSADO, CHEGADA, RESUMO, FIM }

    private static class Particula { float x, y, vx, vy, vida; Color cor; }
    private static class Onda { float x, y, vida; Color cor; }
    private static class Flutuante { String texto; float x, y, vida; Color cor; }
    private static class Confete { float x, y, vx, vy, tam, ang, vang; Color cor; }

    /** Deixe true para testar sem Arduino (setas ESQUERDA / CIMA / DIREITA). Use false nos testes com pacientes. */
    private static final boolean ACEITAR_TECLADO = true;

    // Posicoes das agarradeiras na tela, na ordem do enum Direcao: ESQUERDA, ALTO, DIREITA.
    private static final float[] HOLD_X = { 368f, 480f, 592f };
    private static final float[] HOLD_Y = { 296f, 346f, 296f };
    private static final float RAIO_HOLD = 30f;
    private static final float ASTRO_X = 480f;
    private static final float ASTRO_Y = 180f;           // altura do quadril do astronauta
    private static final float PIXELS_POR_PASSO = 70f;

    private static final Color[] CORES_CONFETE = { Arte.COR_ESQ, Arte.COR_ALTO, Arte.COR_DIR, Arte.DOURADO };

    private final Main jogo;
    private final Cenario cenario = new Cenario();
    private final Astronauta astro = new Astronauta(ASTRO_X, ASTRO_Y);
    private final Array<Entrada> entradas = new Array<>();
    private final Array<Particula> particulas = new Array<>();
    private final Array<Onda> ondas = new Array<>();
    private final Array<Flutuante> flutuantes = new Array<>();
    private final Array<Confete> confetes = new Array<>();

    private Metricas metricas = new Metricas();
    private Estado estado = Estado.MENU;
    private int indiceFase = 0;
    private Fase fase = Fase.TODAS[0];

    // Progresso da fase
    private int alvosResolvidos, acertos, erros, omissoes, passos, sequenciaAcertos;
    private int pontos;
    private int pontosInicioFase;          // para restaurar a pontuacao ao reiniciar a fase
    private int faseInicial;               // fase escolhida no menu principal
    private long pausaInicioNs;            // para nao contar o tempo parado no tempo de resposta
    private Direcao alvoAtivo, ultimoAlvo;
    private long acendimentoNs;
    private float pausa;
    private long ultimoTempoMs = -1;

    // Controle de telas
    private float bloqueioEntrada;          // evita que um clique "pule" a tela seguinte sem querer
    private Resumo resumoFase, resumoTotal;
    private FileHandle arquivoCsv;

    // Efeitos visuais
    private float tempo;
    private float flash;
    private float tremor;

    // Menu principal
    private boolean mostrarAjuda;
    private final Astronauta astroMenu = new Astronauta(770f, 235f);
    private final Color corMenuEscuro = new Color(0.02f, 0.03f, 0.10f, 0.88f);
    private final Color corMenuClaro = new Color(0.02f, 0.03f, 0.10f, 0f);
    private final Color corFlash = new Color(Color.GREEN);
    private float alturaVisual;

    // Chegada ao Destino Final
    private boolean chegou;
    private float chegadaT;
    private float autoPassoT;
    private int autoLado;

    private Sound somAcerto, somErro;

    public GameScreen(Main jogo) {
        this.jogo = jogo;
        // Sons opcionais: coloque acerto.wav e erro.wav na pasta assets. Se nao existirem, o jogo segue sem som.
        somAcerto = carregarSom("acerto.wav");
        somErro = carregarSom("erro.wav");
        astroMenu.celebrar();
        bloqueioEntrada = 0.5f;
    }

    private Sound carregarSom(String nome) {
        FileHandle f = Gdx.files.internal(nome);
        return f.exists() ? Gdx.audio.newSound(f) : null;
    }

    @Override
    public void hide() {
        if (somAcerto != null) somAcerto.dispose();
        if (somErro != null) somErro.dispose();
        somAcerto = null;
        somErro = null;
    }

    // ================================================================ LOOP

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.05f);
        tempo += dt;

        if (tratarTeclas()) return;

        coletarEntradas();
        if (bloqueioEntrada > 0f) {
            bloqueioEntrada -= dt;
            entradas.clear();
        }

        switch (estado) {
            case MENU:
                atualizarMenu();
                break;
            case ESPERANDO_INICIO:
                if (entradas.size > 0) iniciarFase();
                break;
            case JOGANDO:
                atualizarJogo(dt);
                break;
            case PAUSADO:
                atualizarPausa();
                break;
            case CHEGADA:
                atualizarChegada(dt);
                break;
            case RESUMO:
                if (entradas.size > 0) avancarDepoisDoResumo();
                break;
            case FIM:
                if (entradas.size > 0) reiniciarTudo();
                break;
        }

        atualizarEfeitos(dt);
        desenhar();
    }

    /** Junta os cliques do painel (Serial) e, opcionalmente, do teclado. */
    private void coletarEntradas() {
        entradas.clear();
        Entrada e;
        while ((e = jogo.serial.proxima()) != null) entradas.add(e);

        if (ACEITAR_TECLADO) {
            long agora = System.nanoTime();
            if (Gdx.input.isKeyJustPressed(Keys.LEFT)) entradas.add(new Entrada(Direcao.ESQUERDA, agora));
            if (Gdx.input.isKeyJustPressed(Keys.UP)) entradas.add(new Entrada(Direcao.ALTO, agora));
            if (Gdx.input.isKeyJustPressed(Keys.RIGHT)) entradas.add(new Entrada(Direcao.DIREITA, agora));
        }
    }

    // ================================================================ LOGICA

    /** Atalhos do terapeuta (menu e pausa). Retorna true se o aplicativo esta sendo encerrado. */
    private boolean tratarTeclas() {
        boolean esc = Gdx.input.isKeyJustPressed(Keys.ESCAPE);
        boolean p = Gdx.input.isKeyJustPressed(Keys.P);
        boolean r = Gdx.input.isKeyJustPressed(Keys.R);
        boolean m = Gdx.input.isKeyJustPressed(Keys.M);
        boolean enter = Gdx.input.isKeyJustPressed(Keys.ENTER);
        boolean h = Gdx.input.isKeyJustPressed(Keys.H);
        if (Gdx.input.isKeyJustPressed(Keys.N)) jogo.alternarMusica();

        switch (estado) {
            case MENU:
                if (esc) {
                    if (mostrarAjuda) {
                        mostrarAjuda = false;
                    } else {
                        Gdx.app.exit();
                        return true;
                    }
                } else if (h) {
                    mostrarAjuda = !mostrarAjuda;
                } else if (enter && !mostrarAjuda) {
                    iniciarJogo();
                }
                break;
            case JOGANDO:
                if (esc || p) pausar();
                else if (r) reiniciarFase();
                break;
            case PAUSADO:
                if (esc || p) continuar();
                else if (r) reiniciarFase();
                else if (m) voltarAoMenu();
                break;
            default:
                if (esc) voltarAoMenu();
                break;
        }
        return false;
    }

    // ---- Menu principal: ESQUERDA = iniciar, ALTO = trocar a fase inicial ----
    private void atualizarMenu() {
        for (int i = 0; i < entradas.size; i++) {
            Direcao d = entradas.get(i).direcao;
            if (mostrarAjuda) {                 // qualquer botao fecha a ajuda
                mostrarAjuda = false;
                return;
            }
            if (d == Direcao.ESQUERDA) {
                iniciarJogo();
                return;
            }
            if (d == Direcao.ALTO) {
                faseInicial = (faseInicial + 1) % Fase.TODAS.length;
            } else if (d == Direcao.DIREITA) {
                mostrarAjuda = true;
                return;
            }
        }
    }

    /** Nova sessao: zera metricas e pontos e mostra a introducao da fase escolhida. */
    private void iniciarJogo() {
        metricas = new Metricas();
        arquivoCsv = null;
        mostrarAjuda = false;
        pontos = 0;
        indiceFase = faseInicial;
        fase = Fase.TODAS[indiceFase];
        alturaVisual = 0f;
        passos = 0;
        astro.resetar();
        estado = Estado.ESPERANDO_INICIO;
        bloqueioEntrada = 0.4f;
    }

    // ---- Pausa: ESQUERDA = continuar, ALTO = reiniciar fase, DIREITA = menu principal ----
    private void pausar() {
        pausaInicioNs = System.nanoTime();
        estado = Estado.PAUSADO;
        bloqueioEntrada = 0.3f;
    }

    private void continuar() {
        // O tempo parado nao pode contar no tempo de resposta do alvo aceso.
        if (alvoAtivo != null) acendimentoNs += System.nanoTime() - pausaInicioNs;
        estado = Estado.JOGANDO;
        bloqueioEntrada = 0.2f;
    }

    private void atualizarPausa() {
        for (int i = 0; i < entradas.size; i++) {
            Direcao d = entradas.get(i).direcao;
            if (d == Direcao.ESQUERDA) {
                continuar();
                return;
            }
            if (d == Direcao.ALTO) {
                reiniciarFase();
                return;
            }
            if (d == Direcao.DIREITA) {
                voltarAoMenu();
                return;
            }
        }
    }

    /** Recomeca a fase atual do zero, descartando os dados dela. */
    private void reiniciarFase() {
        metricas.removerFase(indiceFase + 1);
        pontos = pontosInicioFase;
        particulas.clear();
        ondas.clear();
        flutuantes.clear();
        iniciarFase();
        bloqueioEntrada = 0.3f;
    }

    /** Volta ao menu principal. Se ja houver dados da sessao, grava o CSV parcial. */
    private void voltarAoMenu() {
        if (metricas.temRegistros() && arquivoCsv == null) {
            arquivoCsv = metricas.exportarCsv();
            Gdx.app.log("Metricas", "CSV parcial salvo em " + arquivoCsv.file().getAbsolutePath());
        }
        alvoAtivo = null;
        alturaVisual = 0f;
        passos = 0;
        astro.resetar();
        confetes.clear();
        mostrarAjuda = false;
        estado = Estado.MENU;
        bloqueioEntrada = 0.4f;
    }

    private void iniciarFase() {
        pontosInicioFase = pontos;
        alturaVisual = 0f;
        fase = Fase.TODAS[indiceFase];
        alvosResolvidos = 0;
        acertos = 0;
        erros = 0;
        omissoes = 0;
        passos = 0;
        sequenciaAcertos = 0;
        alvoAtivo = null;
        ultimoAlvo = null;
        ultimoTempoMs = -1;
        pausa = 0.9f;
        astro.resetar();
        estado = Estado.JOGANDO;
    }

    private void atualizarJogo(float dt) {
        // 1) Avalia os cliques recebidos neste frame.
        for (int i = 0; i < entradas.size; i++) {
            avaliar(entradas.get(i));
        }

        if (alvoAtivo != null) {
            // 2) Tempo esgotado? (so nas fases com limite)
            int limite = fase.limiteParaAlvo(alvosResolvidos);
            long decorridoMs = (System.nanoTime() - acendimentoNs) / 1_000_000L;
            if (limite > 0 && decorridoMs > limite) {
                registrarTempoEsgotado(limite);
            }
        } else {
            // 3) Sem alvo aceso: terminou a fase ou espera a pausa para acender o proximo.
            if (alvosResolvidos >= fase.totalAlvos) {
                finalizarFase();
                return;
            }
            pausa -= dt;
            if (pausa <= 0f) acenderProximo();
        }
    }

    private void acenderProximo() {
        if (fase.sequencia != null) {
            alvoAtivo = fase.sequencia[alvosResolvidos % fase.sequencia.length];
        } else {
            Direcao[] todas = Direcao.values();
            Direcao sorteada;
            do {
                sorteada = todas[MathUtils.random(todas.length - 1)];
            } while (sorteada == ultimoAlvo);   // evita repetir a mesma agarradeira em seguida
            alvoAtivo = sorteada;
        }
        ultimoAlvo = alvoAtivo;
        acendimentoNs = System.nanoTime();      // inicio da medicao do tempo de resposta
    }

    private void avaliar(Entrada e) {
        if (alvoAtivo == null) return;
        if (e.nanoTime < acendimentoNs) return;  // clique anterior ao acendimento nao vale

        long tempoMs = (e.nanoTime - acendimentoNs) / 1_000_000L;

        if (e.direcao == alvoAtivo) {
            // ACERTO: astronauta avanca um passo, soma pontos (bonus por rapidez) e toca o efeito.
            acertos++;
            passos++;
            sequenciaAcertos++;
            // Pontuacao = base + velocidade (tempo de resposta) + consistencia (acertos seguidos sem erro).
            int bonusVelocidade = (int) Math.max(0L, 2000L - tempoMs) / 20;          // ate +100
            int bonusConsistencia = Math.min(sequenciaAcertos - 1, 6) * 10;          // ate +60
            int ganho = 100 + bonusVelocidade + bonusConsistencia;
            pontos += ganho;
            ultimoTempoMs = tempoMs;
            metricas.registrar(indiceFase + 1, alvosResolvidos + 1, alvoAtivo, e.direcao, Resultado.ACERTO, tempoMs);
            efeitoAcerto(alvoAtivo, ganho);
            alvoAtivo = null;
            alvosResolvidos++;
            pausa = MathUtils.random(fase.pausaMinMs, fase.pausaMaxMs) / 1000f;
        } else {
            // ERRO: botao errado. O alvo continua aceso e o paciente pode tentar de novo.
            erros++;
            sequenciaAcertos = 0;
            metricas.registrar(indiceFase + 1, alvosResolvidos + 1, alvoAtivo, e.direcao, Resultado.ERRO, tempoMs);
            efeitoErro("Errou");
        }
    }

    private void registrarTempoEsgotado(int limiteMs) {
        omissoes++;
        sequenciaAcertos = 0;
        metricas.registrar(indiceFase + 1, alvosResolvidos + 1, alvoAtivo, null, Resultado.TEMPO_ESGOTADO, limiteMs);
        efeitoErro("Tempo!");
        alvoAtivo = null;
        alvosResolvidos++;
        pausa = MathUtils.random(fase.pausaMinMs, fase.pausaMaxMs) / 1000f;
    }

    /** Altura total da escalada da fase (um passo por alvo): onde fica o Destino Final. */
    private float alturaDestino() {
        return fase.totalAlvos * PIXELS_POR_PASSO;
    }

    /** Todos os alvos resolvidos: o astronauta termina a subida sozinho ate o Destino Final. */
    private void finalizarFase() {
        alvoAtivo = null;
        chegou = false;
        chegadaT = 0f;
        autoPassoT = 0.15f;
        autoLado = 0;
        estado = Estado.CHEGADA;
    }

    private void atualizarChegada(float dt) {
        float destino = alturaDestino();
        float restante = destino - alturaVisual;

        if (!chegou) {
            if (restante > 1.5f) {
                float vel = Math.max(120f, restante / 2.2f);
                alturaVisual = Math.min(destino, alturaVisual + vel * dt);
                autoPassoT -= dt;
                if (autoPassoT <= 0f && restante > 30f) {
                    // Sobe alternando as maos (esquerda, direita, esquerda...).
                    Direcao d = (autoLado % 2 == 0) ? Direcao.ESQUERDA : Direcao.DIREITA;
                    autoLado++;
                    astro.puxar(d, HOLD_X[d.ordinal()], HOLD_Y[d.ordinal()], Arte.corDe(d));
                    autoPassoT = 0.55f;
                }
            } else {
                alturaVisual = destino;
                chegou = true;
                chegadaT = 0f;
                astro.celebrar();
                efeitoChegada();
            }
        } else {
            chegadaT += dt;
            if (chegadaT > 3.4f) {
                resumoFase = metricas.resumo(indiceFase + 1);
                bloqueioEntrada = 0.8f;
                estado = Estado.RESUMO;
            }
        }
    }

    private void avancarDepoisDoResumo() {
        if (indiceFase + 1 < Fase.TODAS.length) {
            indiceFase++;
            fase = Fase.TODAS[indiceFase];
            alturaVisual = 0f;
            passos = 0;
            astro.resetar();
            estado = Estado.ESPERANDO_INICIO;
            bloqueioEntrada = 0.5f;
        } else {
            resumoTotal = metricas.resumo(0);
            arquivoCsv = metricas.exportarCsv();     // exporta os dados da sessao completa
            Gdx.app.log("Metricas", "CSV salvo em " + arquivoCsv.file().getAbsolutePath());
            estado = Estado.FIM;
            bloqueioEntrada = 0.8f;
        }
    }

    private void reiniciarTudo() {
        metricas = new Metricas();
        indiceFase = 0;
        fase = Fase.TODAS[0];
        pontos = 0;
        passos = 0;
        alturaVisual = 0f;
        astro.resetar();
        arquivoCsv = null;
        confetes.clear();
        estado = Estado.MENU;
        bloqueioEntrada = 0.5f;
    }

    // ================================================================ EFEITOS

    private void efeitoAcerto(Direcao d, int ganho) {
        flash = 1f;
        corFlash.set(Arte.corDe(d));
        if (somAcerto != null) somAcerto.play(0.8f);
        astro.puxar(d, HOLD_X[d.ordinal()], HOLD_Y[d.ordinal()], Arte.corDe(d));

        float hx = HOLD_X[d.ordinal()];
        float hy = HOLD_Y[d.ordinal()];

        Onda o = new Onda();
        o.x = hx;
        o.y = hy;
        o.vida = 1f;
        o.cor = Arte.corDe(d);
        ondas.add(o);

        for (int i = 0; i < 28; i++) {
            Particula p = new Particula();
            p.x = hx;
            p.y = hy;
            float ang = MathUtils.random(MathUtils.PI2);
            float vel = MathUtils.random(90f, 300f);
            p.vx = MathUtils.cos(ang) * vel;
            p.vy = MathUtils.sin(ang) * vel;
            p.vida = 1f;
            p.cor = (i % 3 == 0) ? Color.WHITE : Arte.corDe(d);
            particulas.add(p);
        }

        Flutuante f = new Flutuante();
        f.texto = "+" + ganho;
        f.x = hx;
        f.y = hy + 50f;
        f.vida = 1f;
        f.cor = Arte.DOURADO;
        flutuantes.add(f);
    }

    /** Comemoracao ao chegar ao destino: confete, onda dourada e flash. */
    private void efeitoChegada() {
        flash = 1f;
        corFlash.set(Arte.DOURADO);
        for (int i = 0; i < 110; i++) novoConfete(MathUtils.random(Main.ALTURA * 0.45f, Main.ALTURA + 10f));
        Onda o = new Onda();
        o.x = ASTRO_X;
        o.y = ASTRO_Y + 80f;
        o.vida = 1f;
        o.cor = Arte.DOURADO;
        ondas.add(o);
    }

    private void novoConfete(float y) {
        Confete c = new Confete();
        c.x = MathUtils.random(0f, Main.LARGURA);
        c.y = y;
        c.vx = MathUtils.random(-40f, 40f);
        c.vy = MathUtils.random(-170f, -80f);
        c.tam = MathUtils.random(4f, 8f);
        c.ang = MathUtils.random(MathUtils.PI2);
        c.vang = MathUtils.random(-6f, 6f);
        c.cor = CORES_CONFETE[MathUtils.random(CORES_CONFETE.length - 1)];
        confetes.add(c);
    }

    private void efeitoErro(String mensagem) {
        flash = 1f;
        corFlash.set(1f, 0.2f, 0.2f, 1f);
        tremor = 1f;
        if (somErro != null) somErro.play(0.8f);

        Flutuante f = new Flutuante();
        f.texto = mensagem;
        f.x = ASTRO_X;
        f.y = ASTRO_Y + 175f;
        f.vida = 1f;
        f.cor = new Color(1f, 0.35f, 0.35f, 1f);
        flutuantes.add(f);
    }

    private void atualizarEfeitos(float dt) {
        flash = Math.max(0f, flash - dt * 3f);
        tremor = Math.max(0f, tremor - dt * 4f);

        for (int i = particulas.size - 1; i >= 0; i--) {
            Particula p = particulas.get(i);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= (1f - 1.5f * dt);
            p.vy *= (1f - 1.5f * dt);
            p.vida -= dt * 1.5f;
            if (p.vida <= 0f) particulas.removeIndex(i);
        }
        for (int i = ondas.size - 1; i >= 0; i--) {
            Onda o = ondas.get(i);
            o.vida -= dt * 1.8f;
            if (o.vida <= 0f) ondas.removeIndex(i);
        }
        for (int i = flutuantes.size - 1; i >= 0; i--) {
            Flutuante f = flutuantes.get(i);
            f.y += 55f * dt;
            f.vida -= dt * 1.1f;
            if (f.vida <= 0f) flutuantes.removeIndex(i);
        }

        // Confete na tela final.
        boolean emFesta = estado == Estado.FIM || estado == Estado.RESUMO || (estado == Estado.CHEGADA && chegou);
        if (emFesta && confetes.size < 160) {
            for (int n = 0; n < 2; n++) novoConfete(Main.ALTURA + 10f);
        }
        for (int i = confetes.size - 1; i >= 0; i--) {
            Confete c = confetes.get(i);
            c.x += c.vx * dt;
            c.y += c.vy * dt;
            c.ang += c.vang * dt;
            if (c.y < -20f) confetes.removeIndex(i);
        }

        // Estrutura "desce" suavemente conforme o astronauta sobe.
        if (estado == Estado.JOGANDO || estado == Estado.ESPERANDO_INICIO) {
            alturaVisual = MathUtils.lerp(alturaVisual, passos * PIXELS_POR_PASSO, Math.min(1f, 6f * dt));
        }

        astro.atualizar(dt, (estado == Estado.JOGANDO) ? alvoAtivo : null, HOLD_X, HOLD_Y);
        astroMenu.atualizar(dt, null, HOLD_X, HOLD_Y);
    }

    // ================================================================ DESENHO (formas)

    private void desenhar() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Tremor de camera quando o paciente erra.
        float dx = MathUtils.sin(tempo * 70f) * 7f * tremor;
        jogo.camera.position.set(Main.LARGURA / 2f + dx, Main.ALTURA / 2f, 0f);
        jogo.aplicarCamera();

        ShapeRenderer s = jogo.shapes;
        s.begin(ShapeType.Filled);

        cenario.desenhar(s, alturaVisual, tempo, indiceFase, alturaDestino());
        if (estado == Estado.MENU) {
            desenharMenu(s);
        } else {
            desenharAgarradeiras(s);
            desenharEfeitos(s);
            astro.desenhar(s, tempo);

            if (estado == Estado.JOGANDO) {
                desenharHud(s);
            } else if (estado != Estado.CHEGADA) {
                desenharOverlay(s);
            }
        }
        desenharConfetes(s);

        if (flash > 0f) {
            s.setColor(corFlash.r, corFlash.g, corFlash.b, flash * 0.18f);
            s.rect(0, 0, Main.LARGURA, Main.ALTURA);
        }
        s.end();

        desenharTextos();
    }

    private void desenharAgarradeiras(ShapeRenderer s) {
        for (Direcao d : Direcao.values()) {
            int i = d.ordinal();
            float x = HOLD_X[i];
            float y = HOLD_Y[i];
            Color c = Arte.corDe(d);
            boolean ativa = estado == Estado.JOGANDO && d == alvoAtivo;

            if (ativa) {
                float pulso = 1f + 0.10f * MathUtils.sin(tempo * 9f);
                Arte.brilho(s, x, y, 120f * pulso, c, 1f);
                float expansao = (tempo * 1.5f) % 1f;
                Arte.cor(s, c, (1f - expansao) * 0.8f);
                Arte.anel(s, x, y, RAIO_HOLD + 6f + expansao * 36f, 3f, 36);
                s.setColor(c);
                s.circle(x, y, RAIO_HOLD * pulso, 36);
                s.setColor(1f, 1f, 1f, 0.88f);
                s.circle(x, y, RAIO_HOLD * 0.64f * pulso, 32);
                s.setColor(0.05f, 0.08f, 0.20f, 1f);
                Arte.seta(s, x, y, 17f * pulso, d);
            } else {
                s.setColor(0.13f, 0.16f, 0.30f, 1f);
                s.circle(x, y, RAIO_HOLD + 7f, 32);
                Arte.cor(s, c, 0.40f);
                Arte.anel(s, x, y, RAIO_HOLD + 4f, 2.5f, 32);
                s.setColor(0.22f, 0.26f, 0.44f, 1f);
                s.circle(x, y, RAIO_HOLD, 32);
                s.setColor(0.28f, 0.33f, 0.54f, 1f);
                s.circle(x, y, RAIO_HOLD * 0.70f, 28);
                Arte.cor(s, c, 0.60f);
                Arte.seta(s, x, y, 13f, d);
            }
        }
    }

    private void desenharEfeitos(ShapeRenderer s) {
        for (int i = 0; i < ondas.size; i++) {
            Onda o = ondas.get(i);
            Arte.cor(s, o.cor, o.vida * 0.8f);
            Arte.anel(s, o.x, o.y, RAIO_HOLD + (1f - o.vida) * 120f, 4f, 40);
        }
        for (int i = 0; i < particulas.size; i++) {
            Particula p = particulas.get(i);
            Arte.cor(s, p.cor, Math.max(0f, p.vida));
            s.circle(p.x, p.y, 2f + 4f * p.vida, 8);
        }
    }

    private void desenharConfetes(ShapeRenderer s) {
        for (int i = 0; i < confetes.size; i++) {
            Confete c = confetes.get(i);
            s.setColor(c.cor);
            float ex = MathUtils.cos(c.ang) * c.tam;
            float ey = MathUtils.sin(c.ang) * c.tam;
            s.rectLine(c.x - ex, c.y - ey, c.x + ex, c.y + ey, 4f);
        }
    }

    /** Cartao opaco com borda colorida. */
    private void cartao(ShapeRenderer s, float x, float y, float w, float h, Color borda) {
        s.setColor(borda.r * 0.9f, borda.g * 0.9f, borda.b * 0.9f, 1f);
        Arte.retanguloArredondado(s, x - 2f, y - 2f, w + 4f, h + 4f, 16f);
        s.setColor(0.06f, 0.08f, 0.17f, 1f);
        Arte.retanguloArredondado(s, x, y, w, h, 14f);
    }

    private void desenharHud(ShapeRenderer s) {
        // Cartoes superiores
        cartao(s, 20f, 448f, 300f, 78f, Arte.COR_ESQ);
        cartao(s, 700f, 448f, 240f, 78f, Arte.DOURADO);

        // Escada de progresso (um degrau por alvo)
        int n = fase.totalAlvos;
        float alturaTotal = 300f;
        float folga = 4f;
        float alturaDegrau = (alturaTotal - (n - 1) * folga) / n;
        for (int i = 0; i < n; i++) {
            float y = 80f + i * (alturaDegrau + folga);
            if (i < passos) s.setColor(0.30f, 0.90f, 1f, 1f);
            else s.setColor(0.12f, 0.15f, 0.28f, 1f);
            Arte.retanguloArredondado(s, 34f, y, 36f, alturaDegrau, 4f);
        }
        if (passos >= n) s.setColor(Arte.DOURADO);
        else s.setColor(0.25f, 0.28f, 0.42f, 1f);
        Arte.estrela(s, 52f, 404f, 18f);

        // Barra de tempo do alvo atual (so nas fases com limite)
        if (alvoAtivo != null) {
            int limite = fase.limiteParaAlvo(alvosResolvidos);
            if (limite > 0) {
                long decorrido = (System.nanoTime() - acendimentoNs) / 1_000_000L;
                float restante = MathUtils.clamp(1f - (float) decorrido / limite, 0f, 1f);
                s.setColor(0.06f, 0.08f, 0.17f, 1f);
                Arte.retanguloArredondado(s, 300f, 498f, 360f, 18f, 9f);
                if (restante > 0.03f) {
                    s.setColor(1f - restante, 0.2f + 0.75f * restante, 0.3f, 1f);
                    Arte.retanguloArredondado(s, 303f, 501f, 354f * restante, 12f, 6f);
                }
            }
        }

        // Cartao inferior (contadores) e indicador do painel
        cartao(s, 100f, 14f, 600f, 44f, new Color(0.35f, 0.40f, 0.65f, 1f));
        s.setColor(0.35f, 0.9f, 0.5f, 1f);
        s.circle(118f, 36f, 6f, 12);
        s.setColor(1f, 0.35f, 0.35f, 1f);
        s.circle(236f, 36f, 6f, 12);
        s.setColor(1f, 0.75f, 0.25f, 1f);
        s.circle(340f, 36f, 6f, 12);

        cartao(s, 715f, 14f, 225f, 44f, new Color(0.35f, 0.40f, 0.65f, 1f));
        if (jogo.serial.isConectado()) s.setColor(0.35f, 0.95f, 0.5f, 1f);
        else s.setColor(1f, 0.65f, 0.25f, 1f);
        s.circle(735f, 36f, 6f, 12);
    }

    private void desenharOverlay(ShapeRenderer s) {
        s.setColor(0f, 0f, 0f, 0.55f);
        s.rect(0, 0, Main.LARGURA, Main.ALTURA);

        switch (estado) {
            case PAUSADO:
                cartao(s, 170f, 110f, 620f, 340f, Arte.DOURADO);
                for (Direcao d : Direcao.values()) {
                    opcaoCirculo(s, 330f + d.ordinal() * 150f, 270f, d);
                }
                break;

            case ESPERANDO_INICIO:
                cartao(s, 170f, 110f, 620f, 340f, Arte.COR_ESQ);
                // Legenda: qual botao do painel corresponde a qual cor/seta
                for (Direcao d : Direcao.values()) {
                    float x = 330f + d.ordinal() * 150f;
                    Arte.brilho(s, x, 245f, 62f, Arte.corDe(d), 0.9f);
                    s.setColor(Arte.corDe(d));
                    s.circle(x, 245f, 27f, 28);
                    s.setColor(0.05f, 0.08f, 0.20f, 1f);
                    Arte.seta(s, x, 245f, 12f, d);
                }
                break;

            case RESUMO:
                cartao(s, 240f, 100f, 480f, 360f, Arte.COR_ALTO);
                desenharEstrelas(s, 480f, 345f, estrelasPara(resumoFase));
                break;

            case FIM:
                cartao(s, 200f, 80f, 560f, 400f, Arte.DOURADO);
                desenharEstrelas(s, 480f, 335f, estrelasPara(resumoTotal));
                break;

            default:
                break;
        }
    }

    /** Botao redondo do menu com a cor e a seta do botao fisico correspondente. */
    private void opcaoCirculo(ShapeRenderer s, float x, float y, Direcao d) {
        Arte.brilho(s, x, y, 62f, Arte.corDe(d), 0.9f);
        s.setColor(Arte.corDe(d));
        s.circle(x, y, 27f, 28);
        s.setColor(0.05f, 0.08f, 0.20f, 1f);
        Arte.seta(s, x, y, 12f, d);
    }

    private static final float[] MENU_Y = { 272f, 188f, 104f };   // base de cada botao do menu
    private static final float MENU_X = 60f;
    private static final float MENU_L = 460f;
    private static final float MENU_A = 74f;

    /** Menu principal: titulo, tres botoes (um por botao fisico) e o astronauta comemorando. */
    private void desenharMenu(ShapeRenderer s) {
        // Escurece o lado esquerdo para destacar o titulo e os botoes.
        s.rect(0, 0, 640f, Main.ALTURA, corMenuEscuro, corMenuClaro, corMenuClaro, corMenuEscuro);

        // Brilho atras do titulo e barra com as tres cores dos botoes.
        Arte.brilho(s, 230f, 450f, 240f, Arte.COR_ESQ, 0.22f);
        s.setColor(Arte.COR_ESQ);
        s.rect(62f, 388f, 72f, 5f);
        s.setColor(Arte.COR_ALTO);
        s.rect(138f, 388f, 72f, 5f);
        s.setColor(Arte.COR_DIR);
        s.rect(214f, 388f, 72f, 5f);

        // Astronauta comemorando, com brilho colorido atras.
        Arte.brilho(s, 780f, 300f, 200f, Arte.COR_ALTO, 0.45f);
        Arte.brilho(s, 780f, 300f, 130f, Arte.COR_ESQ, 0.35f);
        astroMenu.desenhar(s, tempo);

        botaoMenu(s, MENU_X, MENU_Y[0], MENU_L, MENU_A, Direcao.ESQUERDA, true);
        botaoMenu(s, MENU_X, MENU_Y[1], MENU_L, MENU_A, Direcao.ALTO, false);
        botaoMenu(s, MENU_X, MENU_Y[2], MENU_L, MENU_A, Direcao.DIREITA, false);

        // Indicador de conexao do painel.
        if (jogo.serial.isConectado()) s.setColor(0.35f, 0.95f, 0.5f, 1f);
        else s.setColor(1f, 0.65f, 0.25f, 1f);
        s.circle(70f, 40f, 6f, 12);

        if (mostrarAjuda) {
            s.setColor(0f, 0f, 0f, 0.6f);
            s.rect(0, 0, Main.LARGURA, Main.ALTURA);
            cartao(s, 150f, 90f, 660f, 360f, Arte.DOURADO);
            for (Direcao d : Direcao.values()) {
                opcaoCirculo(s, 330f + d.ordinal() * 150f, 335f, d);
            }
        }
    }

    /** Botao do menu: icone com a seta, area de texto e uma "etiqueta" com o nome do botao fisico. */
    private void botaoMenu(ShapeRenderer s, float x, float y, float w, float h, Direcao d, boolean destaque) {
        Color c = Arte.corDe(d);
        if (destaque) {
            Arte.brilho(s, x + w / 2f, y + h / 2f, w * 0.55f, c, 0.35f + 0.20f * MathUtils.sin(tempo * 4f));
        }
        cartao(s, x, y, w, h, c);

        // Icone redondo com a seta
        s.setColor(c);
        s.circle(x + 44f, y + h / 2f, 26f, 28);
        s.setColor(0.05f, 0.08f, 0.20f, 1f);
        Arte.seta(s, x + 44f, y + h / 2f, 12f, d);

        // Etiqueta com o nome do botao fisico (cor escurecida da propria cor do botao)
        s.setColor(c.r * 0.22f + 0.04f, c.g * 0.22f + 0.05f, c.b * 0.22f + 0.08f, 1f);
        Arte.retanguloArredondado(s, x + w - 120f, y + h / 2f - 15f, 104f, 30f, 15f);
    }

    private void desenharEstrelas(ShapeRenderer s, float cx, float cy, int cheias) {
        for (int k = 0; k < 3; k++) {
            float x = cx + (k - 1) * 76f;
            if (k < cheias) {
                Arte.brilho(s, x, cy, 48f, Arte.DOURADO, 0.8f);
                s.setColor(Arte.DOURADO);
            } else {
                s.setColor(0.20f, 0.23f, 0.38f, 1f);
            }
            Arte.estrela(s, x, cy, 32f);
        }
    }

    /** 3 estrelas = 90% ou mais de precisao, 2 = 70% ou mais, 1 = concluiu a fase. */
    private int estrelasPara(Resumo r) {
        if (r == null) return 0;
        int tentativas = r.acertos + r.erros + r.omissoes;
        if (tentativas == 0) return 1;
        float p = (float) r.acertos / tentativas;
        if (p >= 0.9f) return 3;
        if (p >= 0.7f) return 2;
        return 1;
    }

    private int precisaoPct(Resumo r) {
        if (r == null) return 0;
        int tentativas = r.acertos + r.erros + r.omissoes;
        return tentativas == 0 ? 0 : Math.round(100f * r.acertos / tentativas);
    }

    // ================================================================ TEXTOS

    private void txt(BitmapFont f, float r, float g, float b, float a, String t, float x, float y) {
        f.setColor(r, g, b, a);
        jogo.texto(f, t, x, y);
    }

    private void txtC(BitmapFont f, Color c, float a, String t, float cx, float y) {
        f.setColor(c.r, c.g, c.b, a);
        jogo.textoCentro(f, t, cx, y);
    }

    private void txtL(BitmapFont f, Color c, float a, String t, float x, float y) {
        f.setColor(c.r, c.g, c.b, a);
        jogo.texto(f, t, x, y);
    }

    private void desenharTextos() {
        jogo.batch.begin();
        switch (estado) {
            case MENU: textosMenu(); break;
            case PAUSADO: textosPausa(); break;
            case ESPERANDO_INICIO: textosEsperando(); break;
            case JOGANDO: textosJogando(); break;
            case CHEGADA: textosChegada(); break;
            case RESUMO: textosResumo(); break;
            case FIM: textosFim(); break;
        }
        jogo.fonte.setColor(Color.WHITE);
        jogo.fonteMedia.setColor(Color.WHITE);
        jogo.fonteGrande.setColor(Color.WHITE);
        jogo.fonteTitulo.setColor(Color.WHITE);
        jogo.batch.end();
    }

    private void textosJogando() {
        BitmapFont f = jogo.fonte;
        BitmapFont m = jogo.fonteMedia;

        // Cartao superior esquerdo: fase e alvo atual
        txt(f, 0.45f, 0.9f, 1f, 1f, "FASE " + (indiceFase + 1), 40f, 512f);
        txt(m, 1f, 1f, 1f, 1f, fase.nome, 40f, 482f);
        int alvoAtual = Math.min(alvosResolvidos + 1, fase.totalAlvos);
        txt(f, 0.75f, 0.8f, 0.95f, 1f, "Alvo " + alvoAtual + "/" + fase.totalAlvos, 224f, 512f);

        // Cartao superior direito: pontos e sequencia
        txt(f, 1f, 0.85f, 0.3f, 1f, "PONTOS", 720f, 512f);
        txt(m, 1f, 1f, 1f, 1f, String.valueOf(pontos), 720f, 482f);
        if (sequenciaAcertos >= 2) {
            txt(f, 1f, 0.85f, 0.3f, 1f, "Seq x" + sequenciaAcertos, 842f, 512f);
        }

        // Instrucao central
        if (alvoAtivo != null) {
            txtC(m, Arte.corDe(alvoAtivo), 1f, "Aperte " + alvoAtivo.rotulo, Main.LARGURA / 2f, 482f);
        } else {
            txtC(m, Color.LIGHT_GRAY, 0.8f, "Prepare-se...", Main.LARGURA / 2f, 482f);
        }

        // Rotulos sob as agarradeiras
        for (Direcao d : Direcao.values()) {
            int i = d.ordinal();
            float a = (d == alvoAtivo) ? 1f : 0.5f;
            float yRotulo = (d == Direcao.ALTO) ? HOLD_Y[i] + RAIO_HOLD + 22f : HOLD_Y[i] - RAIO_HOLD - 16f;
            txtC(f, Arte.corDe(d), a, d.rotulo, HOLD_X[i], yRotulo);
        }

        // Cartao inferior: contadores
        txt(f, 1f, 1f, 1f, 1f, "Acertos " + acertos, 130f, 42f);
        txt(f, 1f, 1f, 1f, 1f, "Erros " + erros, 248f, 42f);
        txt(f, 1f, 1f, 1f, 1f, "Omissoes " + omissoes, 352f, 42f);
        if (ultimoTempoMs >= 0) {
            txt(f, 0.75f, 0.85f, 1f, 1f, "Ultimo: " + ultimoTempoMs + " ms", 500f, 42f);
        }

        // Indicador do painel
        String status = jogo.serial.isConectado() ? "Painel conectado"
                : (ACEITAR_TECLADO ? "Teclado (setas)" : "Painel offline");
        txt(f, 0.85f, 0.9f, 1f, 1f, status, 750f, 42f);

        txt(f, 0.7f, 0.75f, 0.9f, 0.9f, "P = pausar   R = reiniciar fase", 110f, 74f);
        textoDestino();
        textosFlutuantes();
    }

    /** Escreve "DESTINO FINAL" e o nome do destino na placa da plataforma, quando ela esta visivel. */
    private void textoDestino() {
        float base = cenario.yPlataforma(alturaVisual, alturaDestino()) + Cenario.SINAL_DY;
        float topoPlaca = base + Cenario.SINAL_ALTURA;
        if (topoPlaca > 440f || base < 0f) return;       // fora da tela ou sob os cartoes do topo
        Color acento = Arte.COR_ESQ;
        txtC(jogo.fonte, acento, 1f, "DESTINO FINAL", Cenario.SINAL_X, base + 36f);
        txtC(jogo.fonte, Color.WHITE, 1f, fase.destino, Cenario.SINAL_X, base + 17f);
    }

    private void textosChegada() {
        float cx = Main.LARGURA / 2f;
        if (!chegou) {
            txtC(jogo.fonteMedia, Color.WHITE, 0.9f, "Rumo ao destino final...", cx, 492f);
        } else {
            float a = Math.min(1f, chegadaT * 2.5f);
            txtC(jogo.fonteGrande, Arte.DOURADO, a, "DESTINO ALCANCADO!", cx, 495f);
            txtC(jogo.fonteMedia, Color.WHITE, a, fase.destino, cx, 448f);
        }
        textoDestino();
    }

    private void textosFlutuantes() {
        for (int i = 0; i < flutuantes.size; i++) {
            Flutuante fl = flutuantes.get(i);
            txtC(jogo.fonteMedia, fl.cor, Math.max(0f, Math.min(1f, fl.vida)), fl.texto, fl.x, fl.y);
        }
    }

    private void textosMenu() {
        if (mostrarAjuda) {
            textosAjuda();
            return;
        }
        BitmapFont m = jogo.fonteMedia;
        BitmapFont n = jogo.fonteNota;

        // Titulo e subtitulo
        txtL(jogo.fonteTitulo, Color.WHITE, 1f, "ESCALADA", 58f, 512f);
        txtL(jogo.fonteTitulo, Arte.COR_ESQ, 1f, "ESPACIAL", 58f, 450f);
        txtL(jogo.fonte, Color.LIGHT_GRAY, 0.95f, "Reabilitacao de ombros, cotovelos e bracos", 62f, 374f);

        // Botoes: titulo, descricao curta e o nome do botao fisico na etiqueta
        Fase inicial = Fase.TODAS[faseInicial];
        String[] titulos = { "Iniciar jogo", "Trocar fase", "Como jogar" };
        String[] descricoes = { "Comece a escalada",
                "Atual: Fase " + (faseInicial + 1) + " - " + inicial.nome,
                "Instrucoes e dicas" };
        Direcao[] ds = Direcao.values();
        for (int i = 0; i < ds.length; i++) {
            float y = MENU_Y[i];
            txtL(m, Color.WHITE, 1f, titulos[i], MENU_X + 90f, y + 58f);
            txtL(n, Color.LIGHT_GRAY, 0.95f, descricoes[i], MENU_X + 90f, y + 28f);
            txtC(n, Arte.corDe(ds[i]), 1f, ds[i].rotulo, MENU_X + MENU_L - 68f, y + MENU_A / 2f + 7f);
        }

        // Rodape: atalhos e estado do painel
        String musica = jogo.temMusica() ? "   N = musica " + (jogo.isMusicaLigada() ? "(ligada)" : "(desligada)") : "";
        txtL(n, Color.LIGHT_GRAY, 0.85f, "ENTER = iniciar   H = como jogar" + musica + "   ESC = sair", 62f, 74f);
        txtL(n, Color.LIGHT_GRAY, 0.85f, "Painel: " + jogo.serial.getStatus()
                + (ACEITAR_TECLADO ? "   |   setas ESQ / CIMA / DIR simulam os botoes" : ""), 84f, 48f);
    }

    private void textosAjuda() {
        float cx = Main.LARGURA / 2f;
        txtC(jogo.fonteGrande, Arte.DOURADO, 1f, "COMO JOGAR", cx, 425f);
        for (Direcao d : Direcao.values()) {
            txtC(jogo.fonte, Arte.corDe(d), 1f, "Botao " + d.rotulo, 330f + d.ordinal() * 150f, 285f);
        }
        txtC(jogo.fonte, Color.WHITE, 1f, "Uma agarradeira acende: aperte o botao da mesma cor.", cx, 245f);
        txtC(jogo.fonte, Color.WHITE, 1f, "Acertou? O astronauta sobe um passo e voce ganha pontos.", cx, 217f);
        txtC(jogo.fonte, Color.WHITE, 1f, "Quanto mais rapido e sem errar, mais pontos.", cx, 189f);
        txtC(jogo.fonte, Color.WHITE, 1f, "Resolva todos os alvos para chegar ao Destino Final.", cx, 161f);
        txtC(jogo.fonte, Color.LIGHT_GRAY, 0.85f, "Durante o jogo: P = pausar   |   R = reiniciar a fase", cx, 133f);
        float pulso = 0.65f + 0.35f * MathUtils.sin(tempo * 4f);
        txtC(jogo.fonte, Color.WHITE, pulso, "Aperte qualquer botao para voltar", cx, 108f);
    }

    private void textosPausa() {
        float cx = Main.LARGURA / 2f;
        txtC(jogo.fonteGrande, Arte.DOURADO, 1f, "PAUSADO", cx, 405f);
        String[] rotulos = { "Continuar", "Reiniciar fase", "Menu principal" };
        String[] teclas = { "tecla P", "tecla R", "tecla M" };
        for (Direcao d : Direcao.values()) {
            float x = 330f + d.ordinal() * 150f;
            txtC(jogo.fonteMedia, Arte.corDe(d), 1f, rotulos[d.ordinal()], x, 205f);
            txtC(jogo.fonte, Color.LIGHT_GRAY, 0.9f, "Botao " + d.rotulo, x, 178f);
            txtC(jogo.fonte, Color.LIGHT_GRAY, 0.7f, teclas[d.ordinal()], x, 156f);
        }
    }

    private void textosEsperando() {
        float cx = Main.LARGURA / 2f;
        if (indiceFase == 0) {
            txtC(jogo.fonteGrande, Color.WHITE, 1f, "ESCALADA ESPACIAL", cx, 418f);
            txtC(jogo.fonteMedia, Arte.COR_ESQ, 1f, "Fase 1: " + fase.nome, cx, 368f);
        } else {
            txtC(jogo.fonteGrande, Color.WHITE, 1f, "Fase " + (indiceFase + 1) + ": " + fase.nome, cx, 418f);
        }
        txtC(jogo.fonte, Color.WHITE, 0.9f, fase.descricao, cx, 325f);

        for (Direcao d : Direcao.values()) {
            float x = 330f + d.ordinal() * 150f;
            txtC(jogo.fonte, Arte.corDe(d), 1f, "Botao " + d.rotulo, x, 190f);
        }

        float pulso = 0.65f + 0.35f * MathUtils.sin(tempo * 4f);
        txtC(jogo.fonteMedia, Color.WHITE, pulso, "Pressione qualquer botao", cx, 158f);

        String status = jogo.serial.isConectado() ? jogo.serial.getStatus() : jogo.serial.getStatus()
                + (ACEITAR_TECLADO ? "  |  teclado: setas ESQ / CIMA / DIR" : "");
        txtC(jogo.fonte, Color.LIGHT_GRAY, 0.8f, "Painel: " + status, cx, 130f);
    }

    private void textosResumo() {
        float cx = Main.LARGURA / 2f;
        txtC(jogo.fonteGrande, Color.WHITE, 1f, "Fase " + (indiceFase + 1) + " completa!", cx, 430f);
        linhasResumo(resumoFase, 285f);
        float pulso = 0.65f + 0.35f * MathUtils.sin(tempo * 4f);
        txtC(jogo.fonte, Color.WHITE, pulso, "Pressione qualquer botao para continuar", cx, 135f);
    }

    private void textosFim() {
        float cx = Main.LARGURA / 2f;
        txtC(jogo.fonteGrande, Color.WHITE, 1f, "Escalada completa!", cx, 445f);
        txtC(jogo.fonteMedia, Arte.DOURADO, 1f, "Pontuacao final: " + pontos, cx, 392f);
        linhasResumo(resumoTotal, 268f);
        if (arquivoCsv != null) {
            txtC(jogo.fonte, Color.LIGHT_GRAY, 0.9f, "Dados salvos em metricas/" + arquivoCsv.name(), cx, 158f);
        }
        float pulso = 0.65f + 0.35f * MathUtils.sin(tempo * 4f);
        txtC(jogo.fonte, Color.WHITE, pulso, "Pressione qualquer botao para voltar ao menu", cx, 115f);
    }

    private void linhasResumo(Resumo r, float yInicial) {
        if (r == null) return;
        float cx = Main.LARGURA / 2f;
        txtC(jogo.fonte, Color.WHITE, 1f, "Acertos: " + r.acertos + "    Erros: " + r.erros + "    Omissoes: " + r.omissoes, cx, yInicial);
        txtC(jogo.fonte, Arte.COR_ESQ, 1f, "Precisao: " + precisaoPct(r) + "%", cx, yInicial - 30f);
        txtC(jogo.fonte, Color.WHITE, 1f, "Tempo medio de resposta: " + r.mediaMs + " ms", cx, yInicial - 60f);
        txtC(jogo.fonte, Color.WHITE, 1f, "Melhor: " + r.melhorMs + " ms    Pior: " + r.piorMs + " ms", cx, yInicial - 90f);
    }
}
