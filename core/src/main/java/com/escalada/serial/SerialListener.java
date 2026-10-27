package com.escalada.serial;

import com.fazecast.jSerialComm.SerialPort;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Le a porta Serial (Arduino) em uma thread propria e entrega os cliques ao jogo por uma fila.
 *
 * Por que uma fila com timestamp?
 *  - A leitura acontece fora da thread de renderizacao; a fila (thread-safe) evita problemas de concorrencia.
 *  - O instante do clique (System.nanoTime) e gravado no momento em que o byte chega, entao o
 *    tempo de resposta NAO depende da taxa de quadros do jogo.
 *
 * Uso no jogo:
 *   Entrada e;
 *   while ((e = serial.proxima()) != null) { ... }
 */
public class SerialListener {

    /** Um clique recebido do painel. */
    public static class Entrada {
        public final Direcao direcao;
        public final long nanoTime;

        public Entrada(Direcao direcao, long nanoTime) {
            this.direcao = direcao;
            this.nanoTime = nanoTime;
        }
    }

    private final String nomePorta;
    private final int baud;
    private final ConcurrentLinkedQueue<Entrada> fila = new ConcurrentLinkedQueue<>();

    private volatile boolean rodando = false;
    private volatile boolean conectado = false;
    private volatile String status = "Procurando porta serial...";
    private volatile SerialPort porta;
    private Thread leitor;

    /**
     * @param nomePorta nome da porta (ex.: "COM3", "/dev/ttyACM0"); vazio ou null = detectar o Arduino sozinho
     * @param baud      velocidade (9600, igual ao Serial.begin do Arduino)
     */
    public SerialListener(String nomePorta, int baud) {
        this.nomePorta = nomePorta == null ? "" : nomePorta.trim();
        this.baud = baud;
    }

    public void iniciar() {
        rodando = true;
        leitor = new Thread(this::loopLeitura, "leitor-serial");
        leitor.setDaemon(true);
        leitor.start();
    }

    /** Proximo clique pendente, ou null se nao houver. Chamar a cada frame ate esvaziar. */
    public Entrada proxima() {
        return fila.poll();
    }

    /** Descarta cliques pendentes (use ao trocar de tela para nao "vazar" cliques antigos). */
    public void limpar() {
        fila.clear();
    }

    public boolean isConectado() {
        return conectado;
    }

    public String getStatus() {
        return status;
    }

    public void parar() {
        rodando = false;
        SerialPort p = porta;
        if (p != null) p.closePort();
    }

    // ---------------------------------------------------------------- interno

    private SerialPort escolherPorta() {
        if (!nomePorta.isEmpty()) {
            return SerialPort.getCommPort(nomePorta);
        }
        SerialPort[] portas = SerialPort.getCommPorts();
        for (SerialPort p : portas) {
            String d = p.getDescriptivePortName().toLowerCase();
            if (d.contains("arduino") || d.contains("ch340") || d.contains("usb serial") || d.contains("usb")) {
                return p;
            }
        }
        return portas.length > 0 ? portas[0] : null;
    }

    private void loopLeitura() {
        byte[] buffer = new byte[64];

        while (rodando) {
            SerialPort p = escolherPorta();
            if (p == null) {
                conectado = false;
                status = "Nenhuma porta serial encontrada";
                dormir(1000);
                continue;
            }
            porta = p;
            p.setBaudRate(baud);
            p.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 200, 0);

            if (!p.openPort()) {
                conectado = false;
                status = "Nao foi possivel abrir " + p.getSystemPortName();
                dormir(1000);
                continue;
            }
            conectado = true;
            status = "Conectado em " + p.getSystemPortName();
            // O Arduino Uno reinicia ao abrir a porta; esperar um pouco evita ler lixo inicial.
            dormir(2000);
            fila.clear();

            while (rodando && p.isOpen()) {
                int n = p.readBytes(buffer, buffer.length);
                if (n < 0) break;                              // erro ou cabo removido
                long agora = System.nanoTime();                // instante em que os bytes chegaram
                for (int i = 0; i < n; i++) {
                    Direcao d = Direcao.deCodigo((char) buffer[i]);
                    if (d != null) fila.add(new Entrada(d, agora));   // ignora '\n', '\r' e lixo
                }
            }

            p.closePort();
            conectado = false;
            if (rodando) {
                status = "Conexao perdida, tentando de novo...";
                dormir(1000);
            }
        }
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
