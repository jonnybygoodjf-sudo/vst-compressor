"""Gera netlist.md e teste-continuidade.md direto dos dados do projeto (sem digitação manual).
Uso: cd pcb/gerador && python3 documentar.py
"""
import json
import os
import pickle
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
PCB = os.path.dirname(AQUI)
sys.path.insert(0, AQUI)
import footprints as F  # noqa

d, pour = pickle.load(open(os.path.join(AQUI, '.ultimo.pkl'), 'rb'))
rel = json.load(open(os.path.join(PCB, 'relatorio.json')))

MOD = {'U1': 'ESP32', 'U2': 'ADC1 (PCM1808 CH1/CH2)', 'U3': 'ADC2 (PCM1808 CH3/CH4)', 'U4': 'DAC (PCM5102A)',
       'X1': 'Borne IN1', 'X2': 'Borne IN2', 'X3': 'Borne OUT', 'X4': 'Borne 5V EXT', 'X5': 'Régua EXP-A',
       'X6': 'Régua EXP-B', 'D1': 'D1 1N5819'}
PADN = {'3V3a': '3.3V (fileira de cima)', '3V3b': '3.3V (fileira de baixo)', 'GNDa': 'GND (fileira de cima)',
        'GNDb': 'GND (fileira de baixo)', 'GNDc': 'GND (entre LIN e RIN)', 'PMT': 'PMT/FMT', 'A': 'anodo',
        'K': 'catodo (faixa)', 'AGND': 'AGND (entre ROUT e LROUT)'}
COR = {'MCLK1': 'clock', 'MCLK2': 'clock', 'BCLK1': 'clock', 'BCLK2': 'clock', 'WS1': 'clock', 'WS2': 'clock'}


def nome(full):
    ref, pad = full.split('.', 1)
    if ref == 'U1':
        k = int(pad[1:]) - 1
        lbl = (F.ESP_LEFT if pad[0] == 'L' else F.ESP_RIGHT)[k]
        lbl = ('GPIO' + lbl) if lbl.isdigit() else lbl
        lado = 'esq.' if pad[0] == 'L' else 'dir.'
        return f'ESP32 pino {pad} ({lbl}, fileira {lado})'
    if ref.startswith('R'):
        return f'{ref} 33 Ω pino {pad} ' + ('(lado do ESP)' if pad == '1' else '(lado do módulo)')
    if ref.startswith('C'):
        c = d.comp(ref)
        return f'{ref} {c.val} pino {pad}'
    if ref.startswith('J'):
        return f'jumper de fio {ref}, ponta {pad}'
    return f'{MOD.get(ref, ref)} {PADN.get(pad, pad)}'


nets = {}
for p in d.all_pads():
    if p.npth or p.net is None or p.net.startswith('NC:'):
        continue
    nets.setdefault(p.net, []).append(p.full)

ORDEM = ['MCLK1_ESP', 'MCLK1', 'BCLK1_ESP', 'BCLK1', 'WS1', 'DIN1', 'DOUT',
         'MCLK2_ESP', 'MCLK2', 'BCLK2_ESP', 'BCLK2', 'WS2', 'DIN2',
         'IN1L', 'IN1R', 'IN2L', 'IN2R', 'OUTL', 'OUTR', '5V_EXT', '+5V', '3V3', 'GND']
ORDEM += sorted(n for n in nets if n not in ORDEM)
DESC = {
    'MCLK1_ESP': 'MCLK1 (ESP até o resistor)', 'MCLK1': 'MCLK1 12,288 MHz → ADC1',
    'BCLK1_ESP': 'BCLK1 (ESP até o resistor)', 'BCLK1': 'BCLK1 3,072 MHz → ADC1 e DAC',
    'WS1': 'WS1/LRCK 48 kHz → ADC1 e DAC', 'DIN1': 'dados do ADC1 → ESP', 'DOUT': 'dados do ESP → DAC',
    'MCLK2_ESP': 'MCLK2 (ESP até o resistor)', 'MCLK2': 'MCLK2 → ADC2',
    'BCLK2_ESP': 'BCLK2 (ESP até o resistor)', 'BCLK2': 'BCLK2 → ADC2', 'WS2': 'WS2 → ADC2', 'DIN2': 'dados do ADC2 → ESP',
    'IN1L': 'entrada CH1 (L do ADC1)', 'IN1R': 'entrada CH2 (R do ADC1)', 'IN2L': 'entrada CH3 (L do ADC2)',
    'IN2R': 'entrada CH4 (R do ADC2)', 'OUTL': 'saída L', 'OUTR': 'saída R',
    '5V_EXT': '5 V da fonte externa, antes do diodo', '+5V': '5 V da placa (depois do D1)', '3V3': '3,3 V (do ESP32)',
    'GND': 'terra'}

L = []
L.append('# Netlist da PCB (gerada do projeto)\n')
L.append('> Arquivo gerado por `gerador/documentar.py` a partir do mesmo projeto que gera o `.unipcb` e o Gerber. '
         'O ERC do gerador e a conferência independente do Gerber (`fabricacao/verificacao-gerber.json`) confirmam '
         'que o cobre liga exatamente estes pinos.\n')
L.append('## GPIO usados (remapeados para caber numa face só)\n')
L.append('| Sinal | GPIO na placa | GPIO no firmware v9e (antes) | Pino físico do ESP | Vai para |')
L.append('|---|---|---|---|---|')
antes = dict(MCLK1=5, BCLK1=6, WS1=7, DIN1=16, DOUT=15, MCLK2=4, BCLK2=17, WS2=18, DIN2=8)
dest = dict(MCLK1='R2 33 Ω → ADC1 SCK', BCLK1='R1 33 Ω → ADC1 BCK e DAC BCK', WS1='ADC1 LRC e DAC LCK',
            DIN1='ADC1 OUT', DOUT='DAC DIN', MCLK2='R4 33 Ω → ADC2 SCK', BCLK2='R3 33 Ω → ADC2 BCK',
            WS2='ADC2 LRC', DIN2='ADC2 OUT')
import projeto as Pj  # noqa
for s in ['MCLK1', 'BCLK1', 'WS1', 'DIN1', 'DOUT', 'MCLK2', 'BCLK2', 'WS2', 'DIN2']:
    g = d.gpio[s]
    muda = '' if g == antes[s] else ' **(mudou)**'
    L.append(f'| {s} | **GPIO{g}**{muda} | GPIO{antes[s]} | {Pj.esp_pin_of_gpio(g)} | {dest[s]} |')
L.append('')
L.append('## Todas as ligações, rede por rede\n')
L.append('Cada linha é um ponto que precisa estar ligado aos outros da mesma rede.\n')
for n in ORDEM:
    if n not in nets:
        continue
    pins = nets[n]
    if n == 'GND':
        L.append(f'### GND ({len(pins)} pontos) — plano de terra (área de cobre) + trilhas curtas\n')
        L.append(', '.join(nome(x) for x in sorted(pins)) + '\n')
        continue
    L.append(f'### {n} — {DESC.get(n, "")}\n')
    for x in pins:
        L.append(f'- {nome(x)}')
    L.append('')
L.append('## Ligações feitas DENTRO dos módulos (não estão na PCB)\n')
L.append('- PCM1808: os dois pinos "3.3V" são o mesmo ponto no módulo; idem os três GND. A PCB liga só um dos 3.3V '
         '(ADC1: fileira de cima; ADC2: fileira de baixo). **Confirmar com o multímetro antes de soldar** (ver teste-continuidade.md).')
L.append('- PCM5102A: GND e AGND do módulo são o mesmo terra; FLT, DEMP, XSMT e FMT são definidos pelas pontes de solda H1–H4 no verso '
         '(ver montagem.md). Esses pinos ficam em furos **sem cobre** na PCB.')
L.append('')
L.append('## Jumpers de fio (lado dos componentes)\n')
L.append('| Jumper | Rede | Comprimento | Por quê |')
L.append('|---|---|---|---|')
why = {'J1': 'leva o 5 V da linha de baixo até o pino 5Vin do ESP passando por cima da trilha de 3V3; '
             'por baixo do fio o terra do lado esquerdo continua ligado ao GND do ESP',
       'J2': 'liga o GND principal (lado do ESP) ao GND da entrada de 5 V/ADC2, passando por cima das linhas de 3V3 e 5V',
       'J3': 'liga o pino SCK do DAC ao GND (o pino fica preso entre as trilhas BCLK1/WS1 que vão do DAC ao ADC1)'}
for j in d.jumpers:
    L.append(f'| {j["ref"]} | {j["net"]} | {j["L"]:.2f} mm | {why.get(j["ref"], "")} |')
L.append('')
open(os.path.join(PCB, 'netlist.md'), 'w').write('\n'.join(L))

# ------------------------------------------------------------------ teste de continuidade
T = []
T.append('# Teste de continuidade (multímetro no "bip")\n')
T.append('Faça **na ordem**, primeiro com a placa nua (sem nada soldado), depois de novo com os componentes soldados '
         'mas **sem** os módulos encaixados. Marque cada item. Pontas: ponha a ponta no **centro do pad**.\n')
T.append('## 0. Antes de tudo: os módulos (com o multímetro, fora da placa)\n')
T.append('- [ ] PCM1808 (cada um): pino "3.3V" de cima ↔ pino "3.3V" de baixo = **bip** (a PCB usa só um deles).')
T.append('- [ ] PCM1808 (cada um): os três GND entre si = **bip**.')
T.append('- [ ] PCM1808 (cada um): "+5V" ↔ "3.3V" = **sem bip** (o módulo não tem regulador; são alimentações separadas).')
T.append('- [ ] PCM5102A: GND ↔ AGND (pino do meio entre ROUT e LROUT) = **bip**.')
T.append('- [ ] PCM5102A: depois de fechar as pontes (montagem.md), FLT, DEMP e FMT ↔ GND = **bip**; XSMT ↔ GND = **sem bip**.\n')
T.append('## 1. Curto de alimentação (o mais importante)\n')
T.append('- [ ] GND ↔ +5V = **sem bip** (medir entre o pad GND e o pad "+5V" do borne 5V EXT e também entre o pino GND e o 5VIN do ESP).')
T.append('- [ ] GND ↔ 3V3 = **sem bip** (pino 3V3 do ESP ↔ pino GND do ESP).')
T.append('- [ ] +5V ↔ 3V3 = **sem bip**.')
T.append('- [ ] 5V EXT (antes do diodo) ↔ +5V: o multímetro em modo diodo mostra ~0,2–0,4 V num sentido e nada no outro.\n')
T.append('## 2. Cada ligação da netlist (deve dar **bip**)\n')
for n in ORDEM:
    if n not in nets or n == 'GND':
        continue
    pins = nets[n]
    if len(pins) < 2:
        continue
    base = pins[0]
    for x in pins[1:]:
        T.append(f'- [ ] **{n}**: {nome(base)} ↔ {nome(x)}')
T.append('')
T.append('## 3. Terra (deve dar **bip** de cada ponto até o GND do ESP, pino L22)\n')
for x in sorted(nets['GND']):
    if x == 'U1.L22':
        continue
    T.append(f'- [ ] {nome(x)}')
T.append('')
T.append('## 4. Configuração fixa dos módulos\n')
T.append('- [ ] ADC1 e ADC2: MD0, MD1 e PMT/FMT ↔ GND = **bip** (escravo, I2S 24 bits).')
T.append('- [ ] DAC: SCK ↔ GND = **bip** (pelo jumper J3; usa a PLL interna).')
T.append('- [ ] Nenhum par de pinos vizinhos do ESP em curto (passe a ponta pelos 44 pinos: só devem bipar entre si os GND '
         '(L22, R1, R21, R22) e os 3V3 (L1, L2) — e esses dois grupos **não** podem bipar um com o outro).\n')
T.append('## 5. Primeira energização (sem os módulos)\n')
T.append('1. Ligue a fonte de 5 V no borne 5V EXT com amperímetro em série (ou fonte com limite de 100 mA).')
T.append('2. Meça +5V no pad "+5V" de cada soquete de módulo e no 5VIN do ESP (~4,6–4,8 V, por causa do diodo).')
T.append('3. Desligue, encaixe o ESP32, ligue: meça 3,3 V nos pinos "3.3V" dos soquetes dos ADC.')
T.append('4. Só então encaixe os módulos de áudio (sempre com a placa desligada: MD0/MD1 são lidos na partida).')
open(os.path.join(PCB, 'teste-continuidade.md'), 'w').write('\n'.join(T) + '\n')
print('ok netlist.md, teste-continuidade.md;', len(nets), 'redes')
