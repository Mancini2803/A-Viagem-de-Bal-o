# Escalada Espacial - jogo serio para reabilitacao de membros superiores

Arduino Uno + 3 botoes arcade (Serial 9600) + libGDX (Java) + jSerialComm.

## Como abrir e rodar (IntelliJ IDEA)

1. Instale um JDK 17 ou 21 (ex.: Temurin, adoptium.net) e o IntelliJ IDEA (Community serve).
2. Extraia o ZIP e use **File > Open**, escolhendo a pasta `EscaladaEspacial` (a que contem o `build.gradle`).
   Se perguntar, escolha "Trust Project".
3. Aguarde a importacao do Gradle (barra de progresso no canto inferior direito). Na primeira vez ele baixa
   o Gradle 8.10.2 e as dependencias (libGDX 1.12.1 e jSerialComm 2.10.4); e preciso internet.
4. Para jogar, use uma destas opcoes:
   - selecione a configuracao **Jogar** na barra superior e clique no botao verde de play; ou
   - abra `lwjgl3/src/main/java/com/escalada/lwjgl3/Lwjgl3Launcher.java` e clique na seta verde ao lado de `main`.

Se o IntelliJ mostrar a mensagem "Gradle project needs to be imported", clique em "Load Gradle Project"
(ou no icone de elefante / "Reload All Gradle Projects" na aba Gradle).
Se o JDK nao for detectado: File > Project Structure > Project > SDK e escolha o JDK 17 ou 21.

### Linha de comando (opcional)

Este ZIP traz `gradle/wrapper/gradle-wrapper.properties`, que o IntelliJ usa para baixar o Gradle sozinho.
Os arquivos `gradlew`, `gradlew.bat` e `gradle-wrapper.jar` nao estao incluidos; para te-los, com o Gradle
instalado rode uma vez, na raiz do projeto: `gradle wrapper --gradle-version 8.10.2`.
Depois: `./gradlew lwjgl3:run` (Windows: `gradlew.bat lwjgl3:run`).

## Estrutura

```
build.gradle / settings.gradle / gradle.properties   configuracao do Gradle (versoes em gradle.properties)
gradle/wrapper/gradle-wrapper.properties             versao do Gradle usada pelo IntelliJ
core/                                                o jogo
  build.gradle                                       dependencias: libGDX e jSerialComm
  src/main/java/com/escalada/
    Main.java                  recursos compartilhados e inicio da leitura Serial
    serial/Direcao.java        ESQUERDA('L'), ALTO('U'), DIREITA('R')
    serial/SerialListener.java thread de leitura da porta + fila de cliques com timestamp
    jogo/Fase.java             configuracao das 3 fases
    jogo/Cenario.java          fundo: espaco, nebulosas, planeta, estrelas e estrutura espacial
    jogo/Astronauta.java       desenho, esqueleto (IK) e animacao de escalada do astronauta
    jogo/Arte.java             formas reutilizaveis (aneis, brilhos, setas, estrelas, cantos arredondados)
    jogo/Metricas.java         tempo de resposta, acertos, erros e exportacao CSV
    telas/GameScreen.java      estados do jogo, fases, tempo de resposta e renderizacao
lwjgl3/                                              lancador Desktop
  src/main/java/com/escalada/lwjgl3/Lwjgl3Launcher.java
assets/                                              musica.ogg, acerto.wav, erro.wav
tools/gerar_audio.py                                 gera a musica e os efeitos sonoros (composicao original)
arduino/EscaladaEspacial/EscaladaEspacial.ino        firmware com debounce
.idea/runConfigurations/Jogar.xml                    botao de play "Jogar"
```

## Menu, pausa e controles

- **Menu principal:** botao ESQUERDA = iniciar; botao ALTO = trocar a fase inicial; botao DIREITA = tela "Como jogar"
  (pelo teclado: ENTER inicia, H abre a ajuda, ESC fecha a ajuda ou sai).
- **Pausa:** tecla P ou ESC durante o jogo. No menu de pausa: ESQUERDA (ou P) = continuar, ALTO (ou R) = reiniciar a fase,
  DIREITA (ou M) = menu principal. O tempo parado nao entra no tempo de resposta.
- **Reiniciar a fase:** tecla R durante o jogo ou pela pausa (descarta os dados da fase reiniciada).
- A jogada em si (escalar) e controlada somente pelo painel de botoes (Arduino).

## Executavel

1. Gere o jar: no terminal `gradlew lwjgl3:dist` (ou, no IntelliJ, aba Gradle > lwjgl3 > Tasks > other > dist).
2. O arquivo fica em `lwjgl3/build/libs/EscaladaEspacial.jar`. Para abrir: `java -jar EscaladaEspacial.jar`
   (Windows: duplo clique em `Executar.bat`; Linux/macOS: `./executar.sh`). Precisa de Java 11 ou mais novo.
3. Para entregar, compacte o jar com o `Executar.bat` e a pasta `arduino`.

## Arduino

Ligue cada botao entre o pino e o GND: esquerdo no pino 2, alto no 3, direito no 4.
Abra o `.ino` na Arduino IDE, selecione "Arduino Uno" e a porta, e envie.
Teste no Monitor Serial (9600): cada clique deve imprimir `L`, `U` ou `R`.
**Feche o Monitor Serial** antes de abrir o jogo (so um programa usa a porta por vez).

## Audio

- **Musica de fundo** em loop (`assets/musica.ogg`), composicao original sintetizada por codigo em `tools/gerar_audio.py`
  (sem direitos autorais de terceiros). Tecla **N** liga/desliga. Se o arquivo faltar, o jogo roda sem musica.
- **Efeitos:** `acerto.wav` (sino subindo) e `erro.wav` (zumbido curto). Para trocar, use arquivos com os mesmos nomes.
- Para regerar ou mudar o estilo: `pip install numpy`, edite o topo do script e rode `python3 tools/gerar_audio.py`;
  depois, opcionalmente, `ffmpeg -y -i assets/musica.wav -c:a libvorbis -q:a 4 assets/musica.ogg`.

## Visual

Todo o visual e desenhado por codigo (sem imagens), entao nao ha arquivos de arte para gerenciar.
- Cada botao tem uma cor e uma seta: ESQUERDA = ciano, ALTO = magenta, DIREITA = laranja.
  Pinte ou cole adesivos dessas cores nos botoes fisicos; ajuda o paciente a associar tela e painel.
- Cada fase tem um tema de cor (azul, roxo, alerta). Edite as paletas no topo de `Cenario.java`.
- As cores das direcoes ficam em `Arte.java` (COR_ESQ, COR_ALTO, COR_DIR).
- Escalada: o astronauta tem bracos e pernas articulados (cotovelos e joelhos). A cada acerto a mao agarra a agarradeira,
  o corpo se inclina e sobe, os pes se alternam e o propulsor da mochila da um impulso.
- Destino Final: ao resolver todos os alvos, o astronauta termina a subida sozinho ate a plataforma da fase
  (Base Orbital, Estacao Lunar e Portal Estelar, definidos em `Fase.java`) e comemora, mesmo que tenha errado alguns alvos.
  A plataforma vai aparecendo no topo da tela conforme ele se aproxima, com uma placa "DESTINO FINAL".
- Ao final de cada fase aparecem de 1 a 3 estrelas pela precisao (90% = 3, 70% = 2).
- O antialiasing (MSAA 4x) esta ligado em `Lwjgl3Launcher.java`; se a placa de video nao suportar, apague a linha `setBackBufferConfig`.

## Configuracao

- Porta Serial: constante `PORTA_SERIAL` em `Main.java` (ex.: "COM3" ou "/dev/ttyACM0").
  Vazio = tenta detectar o Arduino sozinho. No Linux, se der permissao negada:
  `sudo usermod -a -G dialout $USER` e reinicie a sessao.
- Teste sem Arduino: setas ESQUERDA / CIMA / DIREITA (`ACEITAR_TECLADO` em `GameScreen.java`).
  Coloque `false` nos testes com pacientes.
- Dificuldade: edite a lista `TODAS` em `Fase.java`.
- Fontes: o jogo usa fontes TrueType do proprio sistema (Segoe UI no Windows, Arial no macOS, DejaVu no Linux) para o texto ficar nitido;
  se nao encontrar nenhuma, usa a fonte padrao do libGDX (sem acentos). Por isso os textos da interface ainda estao sem acentos.
- macOS: ao rodar pela seta verde do `Lwjgl3Launcher`, adicione `-XstartOnFirstThread` em
  Run > Edit Configurations > VM options (a configuracao "Jogar" do Gradle ja faz isso).

## Pontuacao

Cada acerto vale 100 pontos + bonus de velocidade (ate +100, cai conforme o tempo de resposta chega a 2 s)
+ bonus de consistencia (+10 por acerto seguido sem erro, ate +60). Erros e tempo esgotado zeram a sequencia.
A precisao (acertos / tentativas) define as estrelas de cada fase.

## Dados exportados

Ao final das 3 fases, o jogo grava `metricas/sessao_AAAAMMDD_HHMMSS.csv` na pasta de trabalho
(`assets/metricas` pela configuracao "Jogar") com as colunas:
`sessao, fase, alvo, esperado, pressionado, resultado, tempo_ms`
(`resultado` = ACERTO, ERRO ou TEMPO_ESGOTADO). O caminho completo tambem aparece na tela final.
O tempo de resposta vai do acendimento da agarradeira ate a chegada do byte na Serial (`System.nanoTime()`).
