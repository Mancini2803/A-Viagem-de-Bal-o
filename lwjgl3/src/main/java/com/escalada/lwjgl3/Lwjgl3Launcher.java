package com.escalada.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.escalada.Main;

/** Inicia o jogo no Desktop. Para rodar pelo IntelliJ: clique na seta verde ao lado do main(). */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Escalada Espacial");
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setWindowedMode(960, 540);
        config.setResizable(true);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);   // antialiasing (MSAA 4x): bordas mais suaves
        new Lwjgl3Application(new Main(), config);
    }
}
