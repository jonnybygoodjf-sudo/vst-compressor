"""Footprints (vista de cima, mm). Medidas marcadas PROVISÓRIO vieram de fotos/desenhos
da internet e precisam ser conferidas com paquímetro e com o gabarito 1:1."""
from pcblib import Footprint

PAD = 2.0      # pad redondo (spec: Ø2,0)
DRILL = 1.0    # furo (spec: Ø1,0 para pino quadrado 0,64)
P = 2.54


def rect(x0, y0, x1, y1):
    return [(x0, y0, x1, y0), (x1, y0, x1, y1), (x1, y1, x0, y1), (x0, y1, x0, y0)]


def circle(cx, cy, r, n=24):
    import math
    out = []
    for i in range(n):
        a, b = 2 * math.pi * i / n, 2 * math.pi * (i + 1) / n
        out.append((cx + r * math.cos(a), cy + r * math.sin(a), cx + r * math.cos(b), cy + r * math.sin(b)))
    return out


def dashed(x0, y0, x1, y1, on=1.2, off=0.8):
    import math
    L = math.hypot(x1 - x0, y1 - y0)
    out, t = [], 0.0
    while t < L:
        t2 = min(L, t + on)
        out.append((x0 + (x1 - x0) * t / L, y0 + (y1 - y0) * t / L, x0 + (x1 - x0) * t2 / L, y0 + (y1 - y0) * t2 / L))
        t = t2 + off
    return out


def pad(n, x, y, d=PAD, drill=DRILL, shape='round'):
    return (n, x, y, d, drill, shape, False)


def hole(n, x, y, drill):
    return (n, x, y, drill, drill, 'hole', True)


# ---------------------------------------------------------------- ESP32-S3 (YD-ESP32-S3 "5Vin")
# Fonte: desenho métrico do fabricante (vcc-gnd/YD-ESP32-S3, ESP32-S3-Metric.pdf):
# placa 27,94 x 57,15 mm; fileiras a 25,40 mm; 22 pinos/lado; 1º pino a 1,91 mm da ponta;
# antena do WROOM-1 sai 6,24 mm além da ponta de cima; USB-C (2x) na ponta de baixo.
# Origem = pino 1 da fileira esquerda (3V3). Pinos L1..L22 (esq.), R1..R22 (dir.).
ESP_LEFT = ['3V3', '3V3', 'RST', '4', '5', '6', '7', '15', '16', '17', '18', '8', '3', '46',
            '9', '10', '11', '12', '13', '14', '5Vin', 'GND']
ESP_RIGHT = ['GND', 'TX43', 'RX44', '1', '2', '42', '41', '40', '39', '38', '37', '36', '35',
             '0', '45', '48', '47', '21', '20', '19', 'GND', 'GND']
ESP_X0, ESP_Y0, ESP_X1, ESP_Y1 = -1.27, -1.91, 26.67, 55.24
ANT = (3.58, -8.15, 21.73, -1.91)   # retângulo da antena que fica para fora da placa do ESP


def esp32():
    pads = []
    for k in range(22):
        pads.append(pad(f'L{k+1}', 0.0, k * P, shape='rect' if k == 0 else 'round'))
        pads.append(pad(f'R{k+1}', 25.4, k * P))
    lines = rect(ESP_X0, ESP_Y0, ESP_X1, ESP_Y1)
    ax0, ay0, ax1, ay1 = ANT
    lines += dashed(ax0, ay0, ax1, ay0) + dashed(ax0, ay0, ax0, ay1) + dashed(ax1, ay0, ax1, ay1)
    # USB-C (2x) na ponta de baixo
    lines += rect(2.45, 51.2, 11.0, 55.24) + rect(14.4, 51.2, 23.1, 55.24)
    return Footprint('ESP32-S3 DevKit (YD, 5Vin)', pads, lines, body=(ESP_X0, ESP_Y0, ESP_X1, ESP_Y1))


# ---------------------------------------------------------------- PCM1808 (módulo roxo, "PMT")
# PROVISÓRIO: medido na foto do módulo (tapbox/component_images/PCM1808.png), escala pelo passo 2,54.
# Fileira de cima (y=0): +5V 3.3V GND MD0 MD1 PMT ; fileira de baixo (y=24,69): 3.3V GND SCK LRC OUT BCK
# Coluna lateral (x=21,2): RIN GND LIN.   Módulo sem regulador: precisa de +5V e 3,3V.
ADC_ROW = 24.69   # PROVISÓRIO
ADC_INX = 21.2    # PROVISÓRIO
ADC_INY = (9.81, 12.35, 14.89)  # PROVISÓRIO
ADC_BODY = (-10.8, -1.27, 22.3, 25.96)  # PROVISÓRIO (≈33 x 27 mm)


def pcm1808():
    top = ['+5V', '3V3a', 'GNDa', 'MD0', 'MD1', 'PMT']
    bot = ['3V3b', 'GNDb', 'SCK', 'LRC', 'OUT', 'BCK']
    pads = [pad(n, i * P, 0.0, shape='rect' if i == 0 else 'round') for i, n in enumerate(top)]
    pads += [pad(n, i * P, ADC_ROW) for i, n in enumerate(bot)]
    pads += [pad(n, ADC_INX, y) for n, y in zip(['RIN', 'GNDc', 'LIN'], ADC_INY)]
    lines = rect(*ADC_BODY)
    return Footprint('PCM1808 (módulo roxo)', pads, lines, body=ADC_BODY)


# ---------------------------------------------------------------- PCM5102A (GY-PCM5102, roxo)
# PROVISÓRIO: medido nas fotos (todbot / mt32-pi wiki). Cabeçalho (x=0): SCK BCK DIN LCK GND VIN.
# Fileira longa (y=-0,72): FLT DEMP XSMT FMT A3V3 AGND ROUT AGND LROUT, a partir de x=6,35.
# FLT/DEMP/XSMT/FMT/A3V3/AGND(1) ficam como furos SEM cobre (configurados pelas pontes H1..H4).
DAC_BODY = (-1.57, -1.6, 29.8, 14.8)   # PROVISÓRIO (≈31 x 16 mm)
DAC_LONG_Y = -0.72                      # PROVISÓRIO
DAC_LONG_X0 = 6.35                      # PROVISÓRIO


def pcm5102():
    hdr = ['SCK', 'BCK', 'DIN', 'LCK', 'GND', 'VIN']
    pads = [pad(n, 0.0, i * P, shape='rect' if i == 0 else 'round') for i, n in enumerate(hdr)]
    longrow = ['FLT', 'DEMP', 'XSMT', 'FMT', 'A3V3', 'AGND1', 'ROUT', 'AGND', 'LROUT']
    for j, n in enumerate(longrow):
        x = DAC_LONG_X0 + j * P
        if n in ('ROUT', 'AGND', 'LROUT'):
            pads.append(pad(n, x, DAC_LONG_Y))
        else:
            pads.append(hole(n, x, DAC_LONG_Y, 1.1))
    lines = rect(*DAC_BODY)
    lines += rect(22.0, 14.8, 27.5, 16.3)   # jack 3,5 mm saindo pela borda
    return Footprint('PCM5102A (GY-PCM5102)', pads, lines, body=DAC_BODY)


# ---------------------------------------------------------------- passivos e conectores
def resistor(pitch=10.16):
    h = pitch / 2
    return Footprint('Resistor axial 1/4 W', [pad('1', -h, 0), pad('2', h, 0)],
                     rect(-3.2, -1.25, 3.2, 1.25) + [(-h + 1.1, 0, -3.2, 0), (3.2, 0, h - 1.1, 0)],
                     body=(-h - 1, -1.3, h + 1, 1.3))


def diode(pitch=10.16):
    h = pitch / 2
    return Footprint('Diodo 1N5819 (DO-41)', [pad('A', -h, 0), pad('K', h, 0, shape='rect')],
                     rect(-2.7, -1.4, 2.7, 1.4) + [(1.9, -1.4, 1.9, 1.4), (2.2, -1.4, 2.2, 1.4),
                                                   (-h + 1.1, 0, -2.7, 0), (2.7, 0, h - 1.1, 0)],
                     body=(-2.7, -1.5, 2.7, 1.5))


def elko(dia=6.3, pitch=2.54):
    h = pitch / 2
    r = dia / 2 + 0.15
    return Footprint(f'Eletrolítico Ø{dia}', [pad('+', -h, 0, shape='rect'), pad('-', h, 0)],
                     circle(0, 0, r) + [(-r - 1.3, -1.6, -r - 1.3, -0.4), (-r - 1.9, -1.0, -r - 0.7, -1.0)],
                     body=(-r, -r, r, r))


def ceramic(pitch=2.54):
    h = pitch / 2
    return Footprint('Cerâmico 100 nF', [pad('1', -h, 0), pad('2', h, 0)],
                     rect(-h - 1.3, -1.5, h + 1.3, 1.5), body=(-h - 1.3, -1.5, h + 1.3, 1.5))


def header(n, names=None):
    names = names or [str(i + 1) for i in range(n)]
    pads = [pad(names[i], i * P, 0, shape='rect' if i == 0 else 'round') for i in range(n)]
    return Footprint(f'Barra de pinos 1x{n}', pads, rect(-1.27, -1.27, (n - 1) * P + 1.27, 1.27),
                     body=(-1.27, -1.27, (n - 1) * P + 1.27, 1.27))


def screw2():
    return Footprint('Borne parafuso 2 vias 5,08', [pad('1', 0, 0, 2.6, 1.3, 'rect'), pad('2', 5.08, 0, 2.6, 1.3)],
                     rect(-2.54, -3.6, 7.62, 3.6), body=(-2.54, -3.6, 7.62, 3.6))


def m3():
    return Footprint('Furo M3', [hole('H', 0, 0, 3.2)], circle(0, 0, 3.0), body=(-3, -3, 3, 3))


def jumper(length):
    return Footprint('Jumper de fio', [pad('1', 0, 0), pad('2', length, 0)],
                     [(1.1, 0, length - 1.1, 0)], body=None)
