package com.escalada;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.escalada.serial.SerialListener;
import com.escalada.telas.GameScreen;

/** Ponto de entrada: cria os recursos compartilhados e inicia a leitura da porta Serial. */
public class Main extends Game {
    public static final float LARGURA = 960f;
    public static final float ALTURA = 540f;

    /** Nome da porta (ex.: "COM3" no Windows, "/dev/ttyACM0" no Linux). Vazio = detectar sozinho. */
    private static final String PORTA_SERIAL = "";
    private static final int BAUD = 9600;

    public SpriteBatch batch;
    public ShapeRenderer shapes;
    public BitmapFont fonte;        // texto pequeno
    public BitmapFont fonteMedia;   // titulos de cartoes
    public BitmapFont fonteGrande;  // titulos de tela
    public OrthographicCamera camera;
    public FitViewport viewport;
    public SerialListener serial;

    private final GlyphLayout layout = new GlyphLayout();

    @Override
    public void create() {
        serial = new SerialListener(PORTA_SERIAL, BAUD);
        serial.iniciar();

        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        // Fonte padrao do libGDX: use apenas texto SEM acentos na interface
        // (ou gere uma fonte .fnt/FreeType com Latin-1 para usar acentos).
        fonte = criarFonte(1.2f);
        fonteMedia = criarFonte(1.8f);
        fonteGrande = criarFonte(2.6f);

        camera = new OrthographicCamera();
        viewport = new FitViewport(LARGURA, ALTURA, camera);

        setScreen(new GameScreen(this));
    }

    /** Fonte padrao ampliada, com filtro linear para as bordas ficarem suaves. */
    private BitmapFont criarFonte(float escala) {
        BitmapFont f = new BitmapFont();
        f.getData().setScale(escala);
        f.getRegion().getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
        f.setUseIntegerPositions(false);
        return f;
    }

    @Override
    public void resize(int largura, int altura) {
        viewport.update(largura, altura, true);
        super.resize(largura, altura);
    }

    public void aplicarCamera() {
        viewport.apply();
        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);
    }

    /** Desenha texto com sombra (mais legivel sobre o cenario). Usa a cor atual da fonte. Entre batch.begin/end. */
    public void texto(BitmapFont f, String t, float x, float y) {
        Color c = f.getColor();
        float r = c.r, g = c.g, b = c.b, a = c.a;
        f.setColor(0f, 0f, 0f, a * 0.65f);
        f.draw(batch, t, x + 2f, y - 2f);
        f.setColor(r, g, b, a);
        f.draw(batch, t, x, y);
    }

    /** Texto com sombra centralizado em cx. */
    public void textoCentro(BitmapFont f, String t, float cx, float y) {
        layout.setText(f, t);
        texto(f, t, cx - layout.width / 2f, y);
    }

    @Override
    public void dispose() {
        if (serial != null) serial.parar();
        batch.dispose();
        shapes.dispose();
        fonte.dispose();
        fonteMedia.dispose();
        fonteGrande.dispose();
    }
}
