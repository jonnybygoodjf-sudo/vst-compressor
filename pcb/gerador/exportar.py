"""Serigrafia e exportação para o formato de projeto do UniPCB (.unipcb = JSON)."""
import json
import math

from shapely.geometry import Point, LineString
from shapely.ops import unary_union

from pcblib import as_polys, poly_to_keyhole

R4 = lambda v: round(float(v), 4)

# rótulos dos pinos dos módulos (texto curto que cabe na serigrafia do UniPCB: A-Z 0-9 . - + / _ : ( ) = #)
PIN_TXT = {
    'U2': {'+5V': '+5V', '3V3a': '3V3', 'GNDa': 'GND', 'MD0': 'MD0', 'MD1': 'MD1', 'PMT': 'FMT',
           '3V3b': '3V3', 'GNDb': 'GND', 'SCK': 'SCK', 'LRC': 'LRC', 'OUT': 'OUT', 'BCK': 'BCK',
           'RIN': 'RIN', 'GNDc': 'G', 'LIN': 'LIN'},
    'U4': {'SCK': 'SCK', 'BCK': 'BCK', 'DIN': 'DIN', 'LCK': 'LCK', 'GND': 'GND', 'VIN': 'VIN',
           'ROUT': 'R', 'AGND': 'G', 'LROUT': 'L', 'FLT': 'FLT', 'DEMP': 'DMP', 'XSMT': 'XMT', 'FMT': 'FMT',
           'A3V3': '3V3', 'AGND1': 'G'},
}
PIN_TXT['U3'] = PIN_TXT['U2']


def silk(d):
    """Gera textos e linhas da serigrafia (camada F.Silk = lado dos componentes)."""
    T = []
    boxes = []
    pads = [(p.x, p.y, (p.d if not p.npth else p.drill) / 2) for p in d.all_pads()]

    def tbox(s, x, y, size):
        w = max(0.1, (len(s) * 5 - 1) * size / 6)
        return (x - w / 2 - 0.15, y - size / 2 - 0.15, x + w / 2 + 0.15, y + size / 2 + 0.15)

    def hits(b):
        for o in boxes:
            if not (b[2] < o[0] or b[0] > o[2] or b[3] < o[1] or b[1] > o[3]):
                return True
        for (px, py, r) in pads:
            cx, cy = min(max(px, b[0]), b[2]), min(max(py, b[1]), b[3])
            if (cx - px) ** 2 + (cy - py) ** 2 < (r + 0.1) ** 2:
                return True
        return False

    def add(s, x, y, size=1.0, rot=0, alts=((0, 0),), force=False):
        for (ax, ay) in alts:
            b = tbox(s, x + ax, y + ay, size)
            if force or not hits(b):
                boxes.append(b)
                T.append(dict(str=s, x=R4(x + ax), y=R4(y + ay), size=size, rot=rot))
                return True
        return False
    AL = ((0, 0), (0, -1.2), (0, 1.2), (1.5, 0), (-1.5, 0), (0, -2.2), (0, 2.2), (2.5, 0), (-2.5, 0))
    # nomes dos módulos (no centro do corpo)
    names = {'U1': 'ESP32-S3 N16R8', 'U2': 'ADC1 PCM1808  CH1 CH2', 'U3': 'ADC2 PCM1808  CH3 CH4',
             'U4': 'DAC PCM5102A'}
    for ref, txt in names.items():
        c = d.comp(ref)
        b = c.body_poly().centroid
        if ref == 'U1':
            add('ESP32-S3', b.x, b.y - 6, 1.6, force=True)
            add('USB', b.x, 54.0, 1.4, force=True)
            add('ANTENA', b.x, 3.0, 1.2, force=True)
        elif ref == 'U2':
            add('ADC1 PCM1808', b.x - 2, b.y + 4, 1.5, force=True)
            add('CH1 CH2', b.x - 2, b.y + 6.4, 1.3, force=True)
        elif ref == 'U3':
            add('ADC2 PCM1808', b.x + 1, b.y + 2, 1.5, force=True)
            add('CH3 CH4', b.x + 1, b.y + 4.4, 1.3, force=True)
        else:
            add('DAC PCM5102A', b.x - 3, b.y - 3, 1.5, force=True)
    # rótulos dos pinos dos módulos: do lado de dentro do módulo
    for ref, table in PIN_TXT.items():
        c = d.comp(ref)
        cen = c.body_poly().centroid
        for p in c.pads:
            txt = table.get(p.name)
            if not txt:
                continue
            vx, vy = cen.x - p.x, cen.y - p.y
            # empurra só no eixo dominante (fileiras ficam alinhadas)
            if abs(vx) > abs(vy):
                ox, oy = math.copysign(1.6 + 0.4 * len(txt), vx), 0
            else:
                ox, oy = 0, math.copysign(2.0, vy)
            add(txt, p.x + ox, p.y + oy, 0.8, alts=((0, 0), (0, 0.9), (0, -0.9), (0.8 * (1 if ox >= 0 else -1), 0)))
    # ESP: número do GPIO dos pinos usados, por dentro (entre as fileiras)
    esp = d.comp('U1')
    for p in esp.pads:
        if p.net is None or p.net.startswith('NC:'):
            continue
        import footprints as F
        k = int(p.name[1:]) - 1
        lbl = (F.ESP_LEFT if p.name[0] == 'L' else F.ESP_RIGHT)[k]
        lbl = lbl if not lbl.isdigit() else 'IO' + lbl
        if lbl == '5Vin':
            lbl = '5VIN'
        dx = 2.2 + 0.35 * len(lbl)
        add(lbl, p.x + (dx if p.name[0] == 'L' else -dx), p.y, 0.8)
    # conectores
    con = {'X1': ['ENTRADA CH1/CH2', {'R': 'R CH2', 'G': 'GND', 'L': 'L CH1'}],
           'X2': ['ENTRADA CH3/CH4', {'L': 'L', 'G': 'G', 'R': 'R'}],
           'X3': ['SAIDA', {'R': 'R', 'G': 'GND', 'L': 'L'}],
           'X4': ['5V EXT', {'1': '+5V', '2': 'GND'}],
           'X5': ['EXP-A', {}],
           'X6': ['EXP-B', {}]}
    for ref, (title, pins) in con.items():
        c = d.comp(ref)
        b = c.body_poly()
        x0, y0, x1, y1 = b.bounds
        for p in c.pads:
            t = pins.get(p.name, p.name)
            if ref in ('X1', 'X3'):
                add(t, p.x + 2.4 + 0.3 * len(t), p.y, 0.8)
            elif ref == 'X2':
                add(t, p.x, p.y - 2.2, 0.8)
            elif ref == 'X4':
                add(t, p.x - 2.6 if t == '+5V' else p.x + 2.6, p.y - 3.0, 0.9, force=True)
            elif ref == 'X6' and t in ('3V3', 'GND'):
                add(t, p.x + 2.6, p.y, 0.7, alts=AL)
        if ref == 'X1':
            add('IN', x0 + 1.3, y0 - 1.0, 0.9)
        elif ref == 'X3':
            add('OUT', x0 + 1.8, y0 - 1.0, 0.9)
        elif ref == 'X2':
            add(title, (x0 + x1) / 2, y0 - 3.4, 0.9)
        elif ref == 'X4':
            pass
        else:
            add(title, (x0 + x1) / 2, y1 + 1.3, 0.8)
    # jumpers de fio
    for c in d.comps:
        if hasattr(c, 'jump'):
            (ax, ay), (bx, by) = c.jump
            mx, my = (ax + bx) / 2, (ay + by) / 2
            if abs(ax - bx) < 1e-6:
                add(c.ref, mx - 1.8, my, 1.0, alts=AL + ((-3, 2), (-3, -2)), force=False) or add(c.ref, mx - 1.8, my, 1.0, force=True)
            else:
                add(c.ref, mx, my - 1.4, 1.0, alts=AL + ((0, 1.4), (-3, 1.4), (3, 1.4)), force=False) or add(c.ref, mx, my - 1.4, 1.0, force=True)
    # avisos e título
    W, H = d.meta['W'], d.meta['H']
    dd = d.comp('D1')
    add('D1', dd.x + 2.6, dd.y, 1.0, force=True)
    add('NUNCA USB+FONTE SEM D1', 93.0, 49.0, 0.8, force=True)
    add('MIXER DSP V1', 93.0, 11.2, 1.0, force=True)
    add('COBRE EMBAIXO', 25.0, 2.0, 0.8, force=True)
    d.silk_texts = T
    # contorno pontilhado da zona da antena
    lines = []
    ring = d.zone.exterior
    L = ring.length
    t = 0.0
    while t < L:
        a = ring.interpolate(t)
        b = ring.interpolate(min(L, t + 1.2))
        if d.outline.buffer(-0.6).contains(Point(a.x, a.y)):
            lines.append([[R4(a.x), R4(a.y)], [R4(b.x), R4(b.y)]])
        t += 2.0
    d.silk_lines = lines
    return T


def comp_item(c, nid):
    fp = c.fp
    pads = []
    for (n, px, py, dd, drill, shape, npth) in fp.pads:
        if npth:
            pads.append(dict(n=n, x=R4(px), y=R4(py), shape='hole', w=R4(drill), h=R4(drill), drill=R4(drill)))
        else:
            pads.append(dict(n=n, x=R4(px), y=R4(py), shape=shape, w=R4(dd), h=R4(dd), drill=R4(drill)))
    lines = [[R4(a), R4(b), R4(e), R4(f)] for (a, b, e, f) in fp.lines]
    show = c.ref[0] in 'RC'
    x, y, rot = c.x, c.y, c.rot
    if hasattr(c, 'jump'):
        (ax, ay), (bx, by) = c.jump
        L = math.hypot(bx - ax, by - ay)
        ang = round(math.degrees(math.atan2(by - ay, bx - ax))) % 360
        pads = [dict(n='1', x=0, y=0, shape='round', w=2.0, h=2.0, drill=1.0),
                dict(n='2', x=R4(L), y=0, shape='round', w=2.0, h=2.0, drill=1.0)]
        lines = [[1.1, 0, R4(L - 1.1), 0]]
        x, y, rot = ax, ay, ang
        show = False
    ref_off = [0, -2.6]
    return dict(id=nid, type='comp', fp=fp.name, desc=c.val, ref=c.ref, val=c.val, x=R4(x), y=R4(y),
                rot=int(rot) % 360, side='F', showRef=show, lw=0.15, refSize=1.0, pads=pads, lines=lines,
                refOff=ref_off)


def to_unipcb(d, pour, name='mixer-dsp-pcb', copper='B.Cu'):
    items = []
    nid = 1

    def nxt():
        nonlocal nid
        nid += 1
        return nid - 1
    # contorno
    ext = [[R4(x), R4(y)] for x, y in d.outline.exterior.coords]
    items.append(dict(id=nxt(), type='line', layer='Edge', w=0.15, pts=ext))
    # componentes
    for c in d.comps:
        items.append(comp_item(c, nxt()))
    # trilhas
    for t in d.tracks:
        items.append(dict(id=nxt(), type='track', layer=copper, w=R4(t['w']), pts=[[R4(x), R4(y)] for x, y in t['pts']]))
    # plano de GND (cada pedaço vira uma área com cortes de largura zero para os recortes)
    for pg in as_polys(pour):
        items.append(dict(id=nxt(), type='zone', layer=copper, pts=poly_to_keyhole(pg)))
    # serigrafia
    for t in getattr(d, 'silk_texts', []):
        items.append(dict(id=nxt(), type='text', layer='F.Silk', **t))
    for ln in getattr(d, 'silk_lines', []):
        items.append(dict(id=nxt(), type='line', layer='F.Silk', w=0.15, pts=ln))
    doc = dict(app='UniPCB', v=1, name=name, items=items, nid=nid, counters={})
    return doc
