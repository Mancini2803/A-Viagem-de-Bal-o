#!/usr/bin/env python3
"""
Gera o audio do jogo (composicao original, sintetizada por codigo, sem direitos autorais de terceiros):

  assets/musica.wav   trilha ambiente em loop (16 compassos, la menor: Am - F - C - G)
  assets/acerto.wav   som de acerto (sino de 3 notas subindo)
  assets/erro.wav     som de erro (zumbido grave curto)

Uso:  python3 tools/gerar_audio.py       (precisa de numpy:  pip install numpy)
Depois, se quiser um arquivo menor, converta a musica para OGG (o jogo prefere musica.ogg):
  ffmpeg -y -i assets/musica.wav -c:a libvorbis -q:a 4 assets/musica.ogg

Para mudar o clima: edite BPM, ACORDES, VOLUME_* ou o padrao do arpejo (PADRAO).
"""
import os
import wave

import numpy as np

SR = 44100
BPM = 76
BEAT = 60.0 / BPM
COMPASSO = 4 * BEAT
N_COMPASSOS = 16
VOLUME_PAD = 0.045
VOLUME_BAIXO = 0.12
VOLUME_SINO = 0.05

# Acordes (notas MIDI) com conducao suave de vozes, e a nota do baixo de cada um.
ACORDES = [
    ([57, 60, 64], 45),   # Am
    ([57, 60, 65], 41),   # F
    ([55, 60, 64], 48),   # C
    ([55, 59, 62], 43),   # G
]
PADRAO = [0, 1, 2, 1, 2, 1, 0, 1]   # indices das notas do acorde tocadas a cada colcheia


def freq(midi):
    return 440.0 * 2 ** ((midi - 69) / 12.0)


def somar(buf, inicio_s, sinal):
    """Soma um sinal no buffer com 'wrap-around', para a musica emendar sem emenda audivel."""
    n = len(buf)
    i0 = int(inicio_s * SR) % n
    resto = sinal
    pos = i0
    while len(resto) > 0:
        k = min(len(resto), n - pos)
        buf[pos:pos + k] += resto[:k]
        resto = resto[k:]
        pos = 0


def envelope(t, duracao, ataque, solta):
    total = duracao + solta
    env = np.minimum(1.0, t / ataque) * np.minimum(1.0, np.maximum(0.0, (total - t) / solta))
    return env


def voz_pad(f, duracao):
    solta = 2.2
    t = np.arange(int((duracao + solta) * SR)) / SR
    sinal = np.zeros_like(t)
    for detune in (-3.0, 3.0):                       # duas copias levemente desafinadas (cents)
        ff = f * 2 ** (detune / 1200.0)
        for h in range(1, 5):
            sinal += np.sin(2 * np.pi * ff * h * t) / (h ** 1.6)
    lfo = 1.0 + 0.15 * np.sin(2 * np.pi * 0.15 * t)
    return sinal * lfo * envelope(t, duracao, 1.6, solta) * VOLUME_PAD


def voz_baixo(f, duracao):
    solta = 1.0
    t = np.arange(int((duracao + solta) * SR)) / SR
    sinal = np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 2 * f * t)
    return sinal * envelope(t, duracao, 0.25, solta) * VOLUME_BAIXO


def sino(f, duracao=2.6, vol=1.0):
    t = np.arange(int(duracao * SR)) / SR
    sinal = (np.sin(2 * np.pi * f * t) * np.exp(-t / 0.55)
             + 0.35 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / 0.22)
             + 0.15 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t / 0.10))
    ataque = np.minimum(1.0, t / 0.004)
    return sinal * ataque * vol


def gerar_musica():
    n = int(N_COMPASSOS * COMPASSO * SR)
    buf = np.zeros(n)

    for c in range(N_COMPASSOS):
        notas, raiz = ACORDES[c % len(ACORDES)]
        inicio = c * COMPASSO
        for m in notas:
            somar(buf, inicio, voz_pad(freq(m), COMPASSO))
        somar(buf, inicio, voz_baixo(freq(raiz), COMPASSO * 0.95))

        # Arpejo de sinos: esparso no comeco de cada bloco de 8 compassos, mais cheio depois.
        cheio = (c % 8) >= 2
        passo = BEAT / 2 if cheio else BEAT
        qtd = int(COMPASSO / passo)
        for i in range(qtd):
            idx = PADRAO[i % len(PADRAO)]
            nota = notas[idx % len(notas)] + (24 if (i % 4 == 3 and cheio) else 12)
            t0 = inicio + i * passo
            vol = VOLUME_SINO * (0.7 + 0.3 * ((i % 2) == 0))
            s = sino(freq(nota), vol=vol)
            somar(buf, t0, s)
            somar(buf, t0 + BEAT * 0.75, s * 0.38)    # eco
            somar(buf, t0 + BEAT * 1.5, s * 0.14)

    buf /= np.max(np.abs(buf))
    return buf * 0.85


def gerar_acerto():
    n = int(0.9 * SR)
    buf = np.zeros(n)
    for i, f in enumerate((659.25, 880.0, 1108.73)):
        s = sino(f, duracao=0.7, vol=0.45)
        buf[int(i * 0.09 * SR):int(i * 0.09 * SR) + len(s)] += s[:n - int(i * 0.09 * SR)]
    buf /= np.max(np.abs(buf))
    return buf * 0.8


def gerar_erro():
    n = int(0.32 * SR)
    t = np.arange(n) / SR
    f = np.linspace(190.0, 105.0, n)
    fase = 2 * np.pi * np.cumsum(f) / SR
    sinal = np.sign(np.sin(fase)) * 0.5 + np.sin(fase) * 0.5
    k = 24                                              # suaviza as bordas do quadrado
    sinal = np.convolve(sinal, np.ones(k) / k, mode="same")
    env = np.exp(-t / 0.18) * np.minimum(1.0, t / 0.004)
    sinal *= env
    return sinal / np.max(np.abs(sinal)) * 0.75


def salvar(caminho, sinal):
    pcm = (np.clip(sinal, -1.0, 1.0) * 32767).astype("<i2")
    with wave.open(caminho, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print("gerado:", caminho)


if __name__ == "__main__":
    pasta = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets")
    os.makedirs(pasta, exist_ok=True)
    salvar(os.path.join(pasta, "musica.wav"), gerar_musica())
    salvar(os.path.join(pasta, "acerto.wav"), gerar_acerto())
    salvar(os.path.join(pasta, "erro.wav"), gerar_erro())
