package com.escalada;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.escalada.serial.SerialListener;
import com.escalada.telas.GameScreen;

import java.util.ArrayList;
import java.util.List;

/** Ponto de entrada: cria os recursos compartilhados, a musica e inicia a leitura da porta Serial. */
public class Main extends Game {
    public static final float LARGURA = 960f;
    public static final float ALTURA = 540f;

    /** Nome da porta (ex.: "COM3" no Windows, "/dev/ttyACM0" no Linux). Vazio = detectar sozinho. */
    private static final String PORTA_SERIAL = "";
    private static final int BAUD = 9600;

    private static final float VOLUME_MUSICA = 0.35f;

    public SpriteBatch batch;
    public ShapeRenderer shapes;
    public BitmapFont fonteNota;    // textos pequenos (descricoes)
    public BitmapFont fonte;        // texto normal
    public BitmapFont fonteMedia;   // titulos de cartoes e botoes
    public BitmapFont fonteGrande;  // titulos de tela
    public BitmapFont fonteTitulo;  // titulo do menu
    public OrthographicCamera camera;
    public FitViewport viewport;
    public SerialListener serial;

    /** true se as fontes TrueType do sistema foram carregadas (texto nitido); false = fonte padrao do libGDX. */
    public boolean fonteTrueType;

    private Music musica;
    private boolean musicaLigada = true;
    private final GlyphLayout layout = new GlyphLayout();

    @Override
    public void create() {
        serial = new SerialListener(PORTA_SERIAL, BAUD);
        serial.iniciar();

        batch = new SpriteBatch();
        shapes = new ShapeRenderer();

        // Fontes: tenta usar fontes TrueType do proprio sistema (nitidas); se nao achar, usa a fonte padrao
        // do libGDX ampliada (sem acentos). Os tamanhos sao em pixels.
        List<String> regular = caminhosFonte(false, false);
        List<String> negrito = caminhosFonte(true, false);
        List<String> preta = caminhosFonte(true, true);
        fonteNota = criarFonte(regular, 15, 1.0f);
        fonte = criarFonte(regular, 18, 1.2f);
        fonteMedia = criarFonte(negrito, 28, 1.8f);
        fonteGrande = criarFonte(negrito, 42, 2.6f);
        fonteTitulo = criarFonte(preta, 74, 4.4f);

        camera = new OrthographicCamera();
        viewport = new FitViewport(LARGURA, ALTURA, camera);

        iniciarMusica();
        setScreen(new GameScreen(this));
    }

    // ------------------------------------------------------------------ fontes

    private List<String> caminhosFonte(boolean negrito, boolean preta) {
        List<String> c = new ArrayList<>();
        String win = System.getenv("WINDIR");
        String pastaWin = (win != null ? win.replace('\\', '/') : "C:/Windows") + "/Fonts/";
        if (preta) c.add(pastaWin + "seguibl.ttf");                       // Segoe UI Black
        c.add(pastaWin + (negrito ? "segoeuib.ttf" : "segoeui.ttf"));     // Segoe UI
        c.add(pastaWin + (negrito ? "arialbd.ttf" : "arial.ttf"));
        c.add(negrito ? "/System/Library/Fonts/Supplemental/Arial Bold.ttf" : "/System/Library/Fonts/Supplemental/Arial.ttf");
        c.add(negrito ? "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" : "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        c.add(negrito ? "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf" : "/usr/share/fonts/TTF/DejaVuSans.ttf");
        return c;
    }

    private BitmapFont criarFonte(List<String> caminhos, int tamanhoPx, float escalaPadrao) {
        for (String caminho : caminhos) {
            FileHandle arquivo = Gdx.files.absolute(caminho);
            if (!arquivo.exists()) continue;
            try {
                FreeTypeFontGenerator gerador = new FreeTypeFontGenerator(arquivo);
                FreeTypeFontParameter p = new FreeTypeFontParameter();
                p.size = tamanhoPx;
                p.minFilter = TextureFilter.Linear;
                p.magFilter = TextureFilter.Linear;
                p.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                        + "\u00e1\u00e0\u00e2\u00e3\u00e9\u00ea\u00ed\u00f3\u00f4\u00f5\u00fa\u00e7"
                        + "\u00c1\u00c0\u00c2\u00c3\u00c9\u00ca\u00cd\u00d3\u00d4\u00d5\u00da\u00c7";
                BitmapFont f = gerador.generateFont(p);
                gerador.dispose();
                fonteTrueType = true;
                return f;
            } catch (Throwable e) {
                Gdx.app.error("Fonte", "Nao foi possivel carregar " + caminho + ": " + e);
            }
        }
        // Reserva: fonte padrao do libGDX ampliada, com filtro linear.
        BitmapFont f = new BitmapFont();
        f.getData().setScale(escalaPadrao);
        f.getRegion().getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
        f.setUseIntegerPositions(false);
        return f;
    }

    // ------------------------------------------------------------------ musica

    private void iniciarMusica() {
        FileHandle arquivo = Gdx.files.internal("musica.ogg");
        if (!arquivo.exists()) arquivo = Gdx.files.internal("musica.wav");
        if (!arquivo.exists()) return;                         // sem arquivo: o jogo roda sem musica
        try {
            musica = Gdx.audio.newMusic(arquivo);
            musica.setLooping(true);
            musica.setVolume(VOLUME_MUSICA);
            musica.play();
        } catch (Exception e) {
            Gdx.app.error("Musica", "Nao foi possivel tocar a musica: " + e);
            musica = null;
        }
    }

    /** Liga ou desliga a musica de fundo (tecla N). */
    public void alternarMusica() {
        if (musica == null) return;
        musicaLigada = !musicaLigada;
        if (musicaLigada) musica.play();
        else musica.pause();
    }

    public boolean isMusicaLigada() {
        return musica != null && musicaLigada;
    }

    public boolean temMusica() {
        return musica != null;
    }

    // ------------------------------------------------------------------ tela

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

    /** Largura do texto na fonte dada. */
    public float larguraTexto(BitmapFont f, String t) {
        layout.setText(f, t);
        return layout.width;
    }

    @Override
    public void dispose() {
        if (serial != null) serial.parar();
        if (musica != null) musica.dispose();
        batch.dispose();
        shapes.dispose();
        fonteNota.dispose();
        fonte.dispose();
        fonteMedia.dispose();
        fonteGrande.dispose();
        fonteTitulo.dispose();
    }
}
