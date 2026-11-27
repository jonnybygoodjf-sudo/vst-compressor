"""Projeto da PCB do Mixer DSP: posicionamento, ligações (netlist) e regras.

Todas as medidas em mm. Itens marcados PROVISÓRIO dependem de conferência nas placas reais.
"""
import math

from shapely.geometry import Point, Polygon, box
from shapely.ops import unary_union

import footprints as F
from motor import Design

# ------------------------------------------------------------------ parâmetros (laser)
PARAM = dict(
    w_sig=0.6,      # trilha de sinal (mm)
    w_pwr=1.0,      # trilha de alimentação (mm)
    cl=0.5,         # folga/isolamento entre redes diferentes (mm)
    edge=1.0,       # cobre até a linha de corte (mm)
    ant_ko=15.0,    # zona proibida em volta da antena (mm)
    hole_ko=3.5,    # raio sem cobre em volta do furo M3 (mm)
    jump_lens=[2.54 * k for k in (2, 3, 4, 5, 6)],
    jump_pen=40.0,  # custo de um jumper, em mm de trilha equivalente
    res=0.2,        # grade do roteador (mm)
)

# Atribuição dos GPIO (padrão do firmware v9e). O gerador pode remapear (ver variantes).
GPIO_FIRMWARE = dict(MCLK1=5, BCLK1=6, WS1=7, DIN1=16, DOUT=15, MCLK2=4, BCLK2=17, WS2=18, DIN2=8)

# GPIO proibidos para os sinais (spec): flash/PSRAM, USB, strapping, UART0, LED RGB da YD (48)
GPIO_PROIBIDOS = set(range(26, 38)) | {19, 20, 0, 3, 45, 46, 43, 44, 48}


def esp_pin_of_gpio(g):
    """Nome do pad do ESP (L#/R#) para um número de GPIO."""
    s = str(g)
    if s in F.ESP_LEFT:
        return 'L%d' % (F.ESP_LEFT.index(s) + 1)
    if s in F.ESP_RIGHT:
        return 'R%d' % (F.ESP_RIGHT.index(s) + 1)
    raise KeyError(g)


def resistor_v():
    """Resistor 1/4 W em pé (passo 2,54) — cabe ao lado do ESP."""
    from pcblib import Footprint
    return Footprint('Resistor 1/4 W em pé', [F.pad('1', 0, 0), F.pad('2', 2.54, 0)],
                     F.circle(0, 0, 1.4) + [(1.4, 0, 2.54 - 1.1, 0)], body=(-1.4, -1.4, 3.1, 1.4))


# ================================================================== variante B: ESP no centro
def outline_poly(pts, r=2.0):
    p = Polygon(pts)
    return p.buffer(-r, join_style=1).buffer(r, 32, join_style=1)


def build_B(gapE=6.0, gapE2=8.0):
    """ESP no centro (antena para fora da borda de cima, USB na borda de baixo).
    Esquerda: DAC (em cima) + ADC1 (embaixo) no I2S0 (fileira esquerda do ESP).
    Direita: ADC2 no I2S1 (fileira direita do ESP)."""
    P = PARAM
    d = Design(P)
    d.name = 'B'
    Ox, Oy = 45.14, 2.21
    Rm = Ox - 1.27 - gapE           # borda direita dos módulos da esquerda
    Lr = Ox + 26.67 + gapE2         # borda esquerda do ADC2
    pin = lambda k: Oy + (k - 1) * 2.54
    d.add('U1', F.esp32(), Ox, Oy, 0, 'ESP32-S3 N16R8', 'ESP32-S3')
    dx, dy = Rm - 1.57, 25.6
    d.add('U4', F.pcm5102(), dx, dy, 180, 'PCM5102A', 'DAC PCM5102A')
    ox1, oy1 = Rm - 25.96, dy + 1.6 + 2.0 + 22.3
    d.add('U2', F.pcm1808(), ox1, oy1, 270, 'PCM1808', 'ADC1 (CH1/CH2)')
    ox2, oy2 = Lr + 25.96, 23.6
    d.add('U3', F.pcm1808(), ox2, oy2, 90, 'PCM1808', 'ADC2 (CH3/CH4)')
    W = ox2 + 1.27 + 1.2
    Hl = Hr = oy1 + 10.8 + 0.2
    d.outline = outline_poly([(0, 0), (W, 0), (W, Hl), (0, Hl)])
    d.meta = dict(Ox=Ox, Oy=Oy, W=W, H=Hl, Hr=Hr)

    # --- resistores em série (em pé), perto do ESP
    rv = resistor_v()
    d.add('R1', rv, Ox - 3.3, pin(9) + 0.25, 180, '33R', 'R1')     # BCLK1
    d.add('R2', rv, Ox - 3.3, pin(18) + 0.5, 180, '33R', 'R2')     # MCLK1
    d.add('R3', rv, 74.0, 15.6, 0, '33R', 'R3')                     # BCLK2
    d.add('R4', rv, 76.6, 21.0, 0, '33R', 'R4')                     # MCLK2

    # --- alimentação externa (embaixo, à direita do ESP) e capacitores
    el, ce = F.elko(), F.ceramic()
    d.add('X4', F.screw2(), 80.5, Hl - 2.9, 0, '5V EXT', '5V')
    d.add('D1', F.diode(), 80.5, 50.0, 270, '1N5819', 'D1')
    d.add('C1', el, 10.1, 5.6, 0, '100uF', 'C1')      # 5V DAC
    d.add('C2', ce, 16.4, 6.4, 0, '100nF', 'C2')
    d.add('C3', el, 6.2, 42.0, 0, '100uF', 'C3')     # 5V ADC1
    d.add('C4', ce, 6.2, 47.6, 0, '100nF', 'C4')
    d.add('C5', ce, 39.4, 54.4, 270, '100nF', 'C5')  # 3V3 ADC1
    d.add('C6', el, 92.0, 6.0, 0, '100uF', 'C6')     # 5V ADC2
    d.add('C7', ce, 97.0, 6.0, 90, '100nF', 'C7')
    d.add('C8', ce, 76.9, 30.0, 0, '100nF', 'C8')  # 3V3 ADC2
    # jumper de fio: 5V da linha de baixo até o pino 5Vin do ESP (o GND passa por baixo do fio)
    d.add_jumper('J1', (41.4, Hl - 1.9 - 0.15), (41.4, Hl - 1.9 - 0.15 - 10.16), '+5V')
    # jumper de fio: GND principal (lado do ESP) -> GND da entrada de 5V/ADC2, por cima das linhas 3V3/5V
    d.add_jumper('J2', (73.4, 51.6), (73.4 + 10.16, 51.6), 'GND')

    # --- conectores de áudio
    d.add('X1', F.header(3, ['R', 'G', 'L']), 4.5, 30.3, 90, 'IN CH2/CH1', 'IN1')
    d.add('X2', F.header(3, ['L', 'G', 'R']), ox2 - 14.89, Hr - 2.5, 0, 'IN CH3/CH4', 'IN2')
    d.add('X3', F.header(3, ['R', 'G', 'L']), 4.5, 19.6, 90, 'OUT R/L', 'OUT')

    # --- réguas de expansão (GPIO livres)
    d.add('X5', F.header(5, ['IO18', 'IO8', 'IO3', 'IO46', 'IO9']), 42.0, pin(11), 90, 'EXP A', 'EXP-A')
    d.add('X6', F.header(4, ['3V3', 'IO47', 'IO21', 'GND']), 73.2, pin(16), 90, 'EXP B', 'EXP-B')

    # --- furos M3
    for i, (x, y) in enumerate([(3.6, 3.6), (W - 3.6, 3.6), (3.6, Hl - 3.6), (W - 3.6, 51.0)]):
        d.add(f'H{i+1}', F.m3(), x, y, 0, 'M3', '')

    # --- zona proibida da antena + regiões reservadas
    ant = box(Ox + F.ANT[0], Oy + F.ANT[1], Ox + F.ANT[2], Oy + F.ANT[3])
    zone = ant.buffer(P['ant_ko'], 32).intersection(d.outline)
    esp_pads = unary_union([p.geom().buffer(0.7) for p in d.comp('U1').pads])
    corridor = box(Ox + 0.9, -1, Ox + 3.7, 16.5)   # única passagem do 3V3 (sob a placa do ESP)
    d.keepouts.append((zone.difference(corridor), set(), 'antena'))
    d.keepouts.append((zone.intersection(corridor), {'3V3'}, 'antena-3V3'))
    d.route_ko = [(zone.difference(corridor).difference(esp_pads), set()),
                  (zone.intersection(corridor).difference(esp_pads), {'3V3'})]
    under = box(Ox + 1.05, 0, Ox + 24.35, 57.45).difference(zone).difference(esp_pads)
    d.route_ko.append((under, {'3V3'}))
    strip = box(Ox - 1.0, 57.0, Ox + 26.4, Hr + 1).difference(esp_pads)
    d.route_ko.append((strip, {'3V3', '+5V'}))
    d.zone = zone

    # --- GPIO (remapeados para rotear numa face só)
    g = dict(WS1=7, DOUT=15, BCLK1=16, DIN1=10, MCLK1=12, BCLK2=42, DIN2=41, WS2=40, MCLK2=39)
    d.gpio = g
    E = lambda s: 'U1.' + esp_pin_of_gpio(g[s])
    C = d.connect
    C('BCLK1_ESP', E('BCLK1'), 'R1.1'); C('BCLK1', 'R1.2', 'U4.BCK', 'U2.BCK')
    C('MCLK1_ESP', E('MCLK1'), 'R2.1'); C('MCLK1', 'R2.2', 'U2.SCK')
    C('WS1', E('WS1'), 'U4.LCK', 'U2.LRC')
    C('DOUT', E('DOUT'), 'U4.DIN')
    C('DIN1', E('DIN1'), 'U2.OUT')
    C('BCLK2_ESP', E('BCLK2'), 'R3.1'); C('BCLK2', 'R3.2', 'U3.BCK')
    C('MCLK2_ESP', E('MCLK2'), 'R4.1'); C('MCLK2', 'R4.2', 'U3.SCK')
    C('WS2', E('WS2'), 'U3.LRC')
    C('DIN2', E('DIN2'), 'U3.OUT')
    for k, gp in (('IO18', 18), ('IO8', 8), ('IO3', 3), ('IO46', 46), ('IO9', 9)):
        C('EXP_' + k, 'U1.' + esp_pin_of_gpio(gp), f'X5.{k}')
    C('EXP_IO47', 'U1.' + esp_pin_of_gpio(47), 'X6.IO47')
    C('EXP_IO21', 'U1.' + esp_pin_of_gpio(21), 'X6.IO21')
    C('3V3', 'X6.3V3')
    C('GND', 'X6.GND')
    common_nets(d)

    # --- caminhos guiados da alimentação (planejados para não cruzar os sinais)
    yb = Hl - 1.9      # linha de 5V na borda de baixo
    d.chains = [
        ('+5V', ['D1.K', (77.6, 44.92), (77.6, 40.6), (103.6, 40.6), (103.6, 25.2), 'U3.+5V'], False),
        ('+5V', [(77.6, 44.92), (77.6, yb), (12.0, yb), 'U2.+5V'], False),
        ('+5V', ['U2.+5V', (1.75, 51.5), (1.75, 13.4), (34.4, 13.4), 'U4.VIN'], False),
        ('+5V', ['J1.1', (41.4, yb)], False),
        ('+5V', ['J1.2', (43.0, 50.85), 'U1.L21'], False),
        ('3V3', ['U1.L1', (47.5, 15.0), (47.5, 57.3), (75.7, 57.3), (75.7, 23.6), 'U3.3V3b'], False),
        ('3V3', [(47.5, 57.3), (14.8, 57.3), (14.8, 49.0), 'U2.3V3a'], False),
        ('3V3', [(75.7, pin(16)), 'X6.3V3'], False),
        # GND dos bornes de áudio direto no GND analógico do próprio módulo
        ('GND', ['U2.GNDc', (24.26, 32.6), (8.0, 32.6), 'X1.G'], False),
        ('GND', ['U3.GNDc', 'X2.G'], False),
        ('GND', ['U4.AGND', (12.17, 23.0), (7.4, 22.14), 'X3.G'], False),
        # I2S1 (ADC2): entram por cima, por dentro do módulo, sem cruzar
        ('BCLK2_ESP', ['U1.R6', 'R3.1'], False),
        ('BCLK2', ['R3.2', (87.8, 15.6), (87.8, 36.3), 'U3.BCK'], False),
        ('DIN2', ['U1.R7', (72.6, 17.6), (86.3, 17.6), (86.3, 33.76), 'U3.OUT'], False),
        ('WS2', ['U1.R8', (73.4, 19.05), (84.8, 19.05), (84.8, 31.22), 'U3.LRC'], False),
        ('MCLK2_ESP', ['U1.R9', 'R4.1'], False),
        ('MCLK2', ['R4.2', (83.2, 21.0), (83.2, 28.68), 'U3.SCK'], False),
    ]
    return d


def common_nets(d):
    C = d.connect
    # alimentação
    C('5V_EXT', 'X4.1', 'D1.A')
    C('+5V', 'D1.K', 'U1.L21', 'U2.+5V', 'U3.+5V', 'U4.VIN',
      'C1.+', 'C2.1', 'C3.+', 'C4.1', 'C6.+', 'C7.1')
    C('3V3', 'U1.L1', 'U1.L2', 'U2.3V3a', 'U2.3V3b', 'U3.3V3a', 'U3.3V3b', 'C5.1', 'C8.1')
    gnd = ['U1.L22', 'U1.R1', 'U1.R21', 'U1.R22', 'X4.2',
           'C1.-', 'C2.2', 'C3.-', 'C4.2', 'C5.2', 'C6.-', 'C7.2', 'C8.2',
           'X1.G', 'X2.G', 'X3.G', 'U4.GND', 'U4.SCK', 'U4.AGND']
    for u in ('U2', 'U3'):
        gnd += [f'{u}.GNDa', f'{u}.GNDb', f'{u}.GNDc', f'{u}.MD0', f'{u}.MD1', f'{u}.PMT']
    C('GND', *gnd)
    # áudio
    C('IN1L', 'U2.LIN', 'X1.L'); C('IN1R', 'U2.RIN', 'X1.R')
    C('IN2L', 'U3.LIN', 'X2.L'); C('IN2R', 'U3.RIN', 'X2.R')
    C('OUTL', 'U4.LROUT', 'X3.L'); C('OUTR', 'U4.ROUT', 'X3.R')
    # ligações internas dos módulos (mesma rede dentro do módulo)
    for u in ('U2', 'U3'):
        d.internal.append({f'{u}.3V3a', f'{u}.3V3b'})
        d.internal.append({f'{u}.GNDa', f'{u}.GNDb', f'{u}.GNDc'})
    d.internal.append({'U1.L1', 'U1.L2'})
    d.internal.append({'U1.L22', 'U1.R1', 'U1.R21', 'U1.R22'})
    d.internal.append({'U4.GND', 'U4.AGND'})
    d.finalize_nets()


# ================================================================== variante A: ESP na borda direita
def build_A(gapE=6.0):
    """ESP na borda direita (antena para fora em cima, USB embaixo); todos os módulos à esquerda,
    ligados só pela fileira esquerda do ESP. ADC2 fica 'atrás' do ADC1/DAC."""
    P = PARAM
    d = Design(P)
    d.name = 'A'
    Ox, Oy = 77.14, 2.21
    Rm = Ox - 1.27 - gapE
    pin = lambda k: Oy + (k - 1) * 2.54
    d.add('U1', F.esp32(), Ox, Oy, 0, 'ESP32-S3 N16R8', 'ESP32-S3')
    dx, dy = Rm - 1.57, 25.6
    d.add('U4', F.pcm5102(), dx, dy, 180, 'PCM5102A', 'DAC PCM5102A')
    ox1, oy1 = Rm - 25.96, dy + 1.6 + 2.0 + 22.3
    d.add('U2', F.pcm1808(), ox1, oy1, 270, 'PCM1808', 'ADC1')
    ox2, oy2 = ox1 - 1.27 - 2.0 - 25.96, 10.8 + 22.3
    d.add('U3', F.pcm1808(), ox2, oy2, 270, 'PCM1808', 'ADC2')
    W = Ox + 26.67 + 1.0
    Hl = oy1 + 10.8 + 0.2
    d.outline = outline_poly([(0, 0), (W, 0), (W, Hl), (0, Hl)])
    d.meta = dict(Ox=Ox, Oy=Oy, W=W, H=Hl, Hr=Hl)
    rv = resistor_v()
    d.add('R1', rv, Ox - 3.3, pin(9) + 0.25, 180, '33R', 'R1')
    d.add('R2', rv, Ox - 3.3, pin(18) + 0.5, 180, '33R', 'R2')
    d.add('R3', rv, Ox - 3.3, pin(13), 180, '33R', 'R3')
    d.add('R4', rv, Ox - 3.3, pin(15) + 0.4, 180, '33R', 'R4')
    el, ce = F.elko(), F.ceramic()
    d.add('X4', F.screw2(), 2.85, 50.0, 90, '5V EXT', '5V')
    d.add('D1', F.diode(), 11.0, 57.5, 0, '1N5819', 'D1')
    d.add('C1', el, 30.0, 47.5, 0, '100uF', 'C1')
    d.add('C2', ce, 36.0, 47.5, 0, '100nF', 'C2')
    d.add('C3', el, 22.0, 47.5, 0, '100uF', 'C3')
    d.add('C4', ce, 16.0, 47.5, 0, '100nF', 'C4')
    d.add('C5', ce, Ox - 5.5, 55.0, 270, '100nF', 'C5')
    d.add('C6', el, 6.0, 40.0, 0, '100uF', 'C6')
    d.add('C7', ce, 6.0, 34.5, 90, '100nF', 'C7')
    d.add('C8', ce, 6.0, 28.0, 90, '100nF', 'C8')
    d.add('X1', F.header(3, ['R', 'G', 'L']), 30.0, 58.6, 0, 'IN CH2/CH1', 'IN1')
    d.add('X2', F.header(3, ['L', 'G', 'R']), ox2 + 9.81, 4.0, 0, 'IN CH3/CH4', 'IN2')
    d.add('X3', F.header(3, ['R', 'G', 'L']), 41.0, 4.0, 0, 'OUT', 'OUT')
    for i, (x, y) in enumerate([(3.6, 3.6), (W - 3.6, Hl - 3.6), (3.6, Hl - 3.6)]):
        d.add(f'H{i+1}', F.m3(), x, y, 0, 'M3', '')
    ant = box(Ox + F.ANT[0], Oy + F.ANT[1], Ox + F.ANT[2], Oy + F.ANT[3])
    zone = ant.buffer(P['ant_ko'], 32).intersection(d.outline)
    esp_pads = unary_union([p.geom().buffer(0.7) for p in d.comp('U1').pads])
    corridor = box(Ox + 0.9, -1, Ox + 3.7, 16.5)
    d.keepouts.append((zone.difference(corridor), set(), 'antena'))
    d.keepouts.append((zone.intersection(corridor), {'3V3'}, 'antena-3V3'))
    d.route_ko = [(zone.difference(corridor).difference(esp_pads), set()),
                  (zone.intersection(corridor).difference(esp_pads), {'3V3'})]
    d.zone = zone
    g = dict(WS1=7, DOUT=15, BCLK1=16, DIN1=10, MCLK1=12, BCLK2=17, DIN2=18, WS2=8, MCLK2=9)
    d.gpio = g
    E = lambda s: 'U1.' + esp_pin_of_gpio(g[s])
    C = d.connect
    C('BCLK1_ESP', E('BCLK1'), 'R1.1'); C('BCLK1', 'R1.2', 'U4.BCK', 'U2.BCK')
    C('MCLK1_ESP', E('MCLK1'), 'R2.1'); C('MCLK1', 'R2.2', 'U2.SCK')
    C('WS1', E('WS1'), 'U4.LCK', 'U2.LRC')
    C('DOUT', E('DOUT'), 'U4.DIN')
    C('DIN1', E('DIN1'), 'U2.OUT')
    C('BCLK2_ESP', E('BCLK2'), 'R3.1'); C('BCLK2', 'R3.2', 'U3.BCK')
    C('MCLK2_ESP', E('MCLK2'), 'R4.1'); C('MCLK2', 'R4.2', 'U3.SCK')
    C('WS2', E('WS2'), 'U3.LRC')
    C('DIN2', E('DIN2'), 'U3.OUT')
    common_nets(d)
    d.chains = []
    return d
