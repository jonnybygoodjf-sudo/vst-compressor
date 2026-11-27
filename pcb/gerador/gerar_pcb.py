"""Gera a PCB do Mixer DSP (variante B), verifica e exporta.

Uso:  cd pcb/gerador && python3 gerar_pcb.py
Saídas (pasta pcb/): mixer-dsp-pcb.unipcb, relatorio.json, imagens/*.png
"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import projeto
import rotear
import verificar
import exportar
import render

AQUI = os.path.dirname(os.path.abspath(__file__))
PCB = os.path.dirname(AQUI)
IMG = os.path.join(PCB, 'imagens')

# Tabela da especificação (seção 2): sinal -> pinos dos módulos. Os resistores de 33 R em série
# são "transparentes" nesta conferência (o ESP liga ao resistor, o resistor liga ao módulo).
SPEC = [
    ('MCLK1', 'ESP', ['U2.SCK']),
    ('BCLK1', 'ESP', ['U2.BCK', 'U4.BCK']),
    ('WS1', 'ESP', ['U2.LRC', 'U4.LCK']),
    ('DIN1', 'ESP', ['U2.OUT']),
    ('DOUT', 'ESP', ['U4.DIN']),
    ('MCLK2', 'ESP', ['U3.SCK']),
    ('BCLK2', 'ESP', ['U3.BCK']),
    ('WS2', 'ESP', ['U3.LRC']),
    ('DIN2', 'ESP', ['U3.OUT']),
]
SPEC_POWER = [
    ('+5V', ['U1.L21', 'U2.+5V', 'U3.+5V', 'U4.VIN']),
    ('3V3', ['U1.L1', 'U2.3V3a', 'U3.3V3b']),
    ('GND', ['U1.L22', 'U2.GNDa', 'U2.MD0', 'U2.MD1', 'U2.PMT', 'U3.GNDa', 'U3.MD0', 'U3.MD1', 'U3.PMT',
             'U4.GND', 'U4.SCK']),
]


def logical_net(d, full):
    """Rede 'lógica': atravessa os resistores série (R1..R4)."""
    n = d.pad(full).net
    for r in ('R1', 'R2', 'R3', 'R4'):
        a, b = d.pad(r + '.1').net, d.pad(r + '.2').net
        if n in (a, b):
            return b  # nome do lado do módulo
    return n


def conferir_spec(d):
    import projeto as Pj
    linhas, ok = [], True
    for sig, _, pins in SPEC:
        g = d.gpio[sig]
        esp = 'U1.' + Pj.esp_pin_of_gpio(g)
        ln_esp = logical_net(d, esp)
        for p in pins:
            ln = logical_net(d, p)
            good = ln == ln_esp
            ok &= good
            linhas.append(dict(sinal=sig, gpio=g, pino_esp=esp, destino=p, rede=ln, ok=good))
    for net, pins in SPEC_POWER:
        for p in pins:
            good = d.pad(p).net == net
            ok &= good
            linhas.append(dict(sinal=net, gpio=None, pino_esp='', destino=p, rede=d.pad(p).net, ok=good))
    return ok, linhas


def main():
    t0 = time.time()
    os.makedirs(IMG, exist_ok=True)
    d = projeto.build_B()
    R, fails = rotear.route_all(d, verbose=False)
    pour, comps, groups = rotear.connect_gnd_islands(d, R, verbose=False)
    pour = rotear.remove_dead_copper(d, pour)
    e = verificar.erc(d, pour)
    r = verificar.drc(d, pour)
    ok_spec, tab = conferir_spec(d)
    exportar.silk(d)
    doc = exportar.to_unipcb(d, pour)
    out = os.path.join(PCB, 'mixer-dsp-pcb.unipcb')
    with open(out, 'w') as f:
        json.dump(doc, f, separators=(',', ':'))
    # imagens
    render.render(d, os.path.join(IMG, 'placa-vista-de-cima.png'), scale=10, pour=pour,
                  title='Mixer DSP - vista de cima (cobre embaixo, visto atraves da placa)')
    render.render(d, os.path.join(IMG, 'placa-cobre-espelhado.png'), scale=10, pour=pour, show_bodies=False,
                  show_zone=False, mirror=True, title='Cobre visto POR BAIXO (espelhado) - como fica na placa')
    jpts = [((j['a'][0] + j['b'][0]) / 2, (j['a'][1] + j['b'][1]) / 2) for j in d.jumpers]
    render.render(d, os.path.join(IMG, 'mapa-jumpers.png'), scale=10, pour=None,
                  title='Mapa dos jumpers de fio (circulos magenta)', crossings=jpts)
    clk = {n: sum(__import__('shapely.geometry', fromlist=['LineString']).LineString(t['pts']).length
                  for t in d.tracks if t['net'] == n)
           for n in ('MCLK1_ESP', 'MCLK1', 'BCLK1_ESP', 'BCLK1', 'MCLK2_ESP', 'MCLK2', 'BCLK2_ESP', 'BCLK2', 'WS1', 'WS2')}
    x0, y0, x1, y1 = d.outline.bounds
    rel = dict(
        variante=d.name, tempo_s=round(time.time() - t0, 1), redes_nao_roteadas=fails,
        jumpers=[dict(ref=j['ref'], rede=j['net'], comprimento_mm=round(j['L'], 2),
                      de=[round(v, 2) for v in j['a']], ate=[round(v, 2) for v in j['b']]) for j in d.jumpers],
        placa_mm=[round(x1 - x0, 2), round(y1 - y0, 2)], area_mm2=round(d.outline.area, 1),
        gpio=d.gpio, erc=e, drc={k: v for k, v in r.items()}, conferencia_spec_ok=ok_spec, conferencia_spec=tab,
        comprimento_clock_mm={k: round(v, 1) for k, v in clk.items()},
        trilhas=len(d.tracks), componentes=len([c for c in d.comps if not hasattr(c, 'jump')]),
        log=d.log,
    )
    with open(os.path.join(PCB, 'relatorio.json'), 'w') as f:
        json.dump(rel, f, indent=1, ensure_ascii=False, default=str)
    print(json.dumps(dict(jumpers=[(j['ref'], j['net']) for j in d.jumpers], placa=rel['placa_mm'],
                          erc=e['erros'], drc=r['erros'], spec_ok=ok_spec, fails=fails,
                          clock=rel['comprimento_clock_mm']), ensure_ascii=False, indent=1))
    import pickle
    with open(os.path.join(AQUI, '.ultimo.pkl'), 'wb') as f:
        pickle.dump((d, pour), f)
    return d, pour


if __name__ == '__main__':
    main()
