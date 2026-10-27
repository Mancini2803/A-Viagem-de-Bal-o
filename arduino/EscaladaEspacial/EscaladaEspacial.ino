/*
  Escalada Espacial - painel de 3 botoes arcade (Arduino Uno R3)

  Cada botao liga o pino digital ao GND quando pressionado, por isso usamos INPUT_PULLUP:
    - solto      -> pino le HIGH
    - pressionado-> pino le LOW

  Protocolo Serial (9600 baud), uma letra + '\n' a cada clique:
    Botao ESQUERDO -> "L\n"   (pino 2)
    Botao ALTO     -> "U\n"   (pino 3)
    Botao DIREITO  -> "R\n"   (pino 4)

  Debounce: o contato mecanico "treme" por alguns milissegundos ao fechar. So aceitamos
  uma mudanca de estado depois que o pino ficou estavel por DEBOUNCE_MS.
  Apenas a borda de DESCIDA (HIGH -> LOW) gera envio: segurar o botao nao repete o sinal.
*/

const uint8_t NUM_BOTOES = 3;
const uint8_t PINOS[NUM_BOTOES]   = {2, 3, 4};      // Esquerda, Alto, Direita
const char    CODIGOS[NUM_BOTOES] = {'L', 'U', 'R'};

const unsigned long DEBOUNCE_MS = 30;               // ajuste se o botao ainda "duplicar" cliques

bool          estadoEstavel[NUM_BOTOES];            // ultimo estado aceito (HIGH = solto)
bool          ultimaLeitura[NUM_BOTOES];            // leitura bruta anterior
unsigned long instanteMudanca[NUM_BOTOES];          // quando a leitura bruta mudou pela ultima vez

void setup() {
  Serial.begin(9600);
  for (uint8_t i = 0; i < NUM_BOTOES; i++) {
    pinMode(PINOS[i], INPUT_PULLUP);
    estadoEstavel[i] = HIGH;
    ultimaLeitura[i] = HIGH;
    instanteMudanca[i] = 0;
  }
}

void loop() {
  unsigned long agora = millis();

  for (uint8_t i = 0; i < NUM_BOTOES; i++) {
    bool leitura = digitalRead(PINOS[i]);

    // Leitura bruta mudou: reinicia a contagem de estabilidade.
    if (leitura != ultimaLeitura[i]) {
      instanteMudanca[i] = agora;
      ultimaLeitura[i] = leitura;
    }

    // Estado ficou estavel por tempo suficiente e e diferente do aceito: e um clique/soltura real.
    if ((agora - instanteMudanca[i]) >= DEBOUNCE_MS && leitura != estadoEstavel[i]) {
      estadoEstavel[i] = leitura;

      if (estadoEstavel[i] == LOW) {        // botao acabou de ser pressionado
        Serial.write(CODIGOS[i]);
        Serial.write('\n');                  // exatamente "L\n", "U\n" ou "R\n"
      }
    }
  }
}
