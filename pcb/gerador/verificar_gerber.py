"""Conferência INDEPENDENTE dos arquivos de fabricação exportados pelo UniPCB.

Lê o Gerber do cobre inferior, o contorno e a furação com o gerbonara, reconstrói a geometria do cobre
e confere: (1) cada rede da netlist forma uma única ilha de cobre (jumpers de fio e ligações internas dos
módulos contam), (2) nenhuma ilha junta duas redes (curto), (3) folga mínima entre ilhas, (4) cobre a >= 1 mm
da borda, (5) cada pad tem o seu furo no Excellon.
Uso: cd pcb/gerador && python3 verificar_gerber.py
"""
import json
import math
import os
import pickle
import sys

from gerbonara import GerberFile, ExcellonFile
import gerbonara.graphic_primitives as gp
from shapely.geometry import Point, LineString, Polygon, box
from shapely.ops import unary_union
from shapely.strtree import STRtree
from shapely import affinity

AQUI = os.path.dirname(os.path.abspath(__file__))
FAB = os.path.join(os.path.dirname(AQUI), 'fabricacao')
sys.path.insert(0, AQUI)


def prim_geom(p):
    if isinstance(p, gp.Rectangle):
        g = box(p.x - p.w / 2, p.y - p.h / 2, p.x + p.w / 2, p.y + p.h / 2)
        return affinity.rotate(g, p.rotation, origin=(p.x, p.y), use_radians=True) if p.rotation else g
    if isinstance(p, gp.Circle):
        return Point(p.x, p.y).buffer(p.r, 32)
    if isinstance(p, gp.Line):
        return LineString([(p.x1, p.y1), (p.x2, p.y2)]).buffer(p.width / 2, 16)
    if isinstance(p, gp.Arc):
        pts = [(x, y) for x, y in p.approximate(max_error=0.005)] if hasattr(p, 'approximate') else [(p.x1, p.y1), (p.x2, p.y2)]
        return LineString(pts).buffer(p.width / 2, 16)
    if isinstance(p, gp.ArcPoly):
        pts = list(p.outline)
        return Polygon(pts).buffer(0)
    raise TypeError(type(p))


def gerber_geom(path):
    g = GerberFile.open(path)
    dark, clear = [], []
    for o in g.objects:
        for p in o.to_primitives():
            (dark if p.polarity_dark else clear).append(prim_geom(p))
    geom = unary_union(dark)
    if clear:
        geom = geom.difference(unary_union(clear))
    return geom


def main():
    d, pour = pickle.load(open(os.path.join(AQUI, '.ultimo.pkl'), 'rb'))
    name = 'mixer-dsp-pcb'
    cu = gerber_geom(os.path.join(FAB, f'{name}-B_Cu.gbl'))
    edge = gerber_geom(os.path.join(FAB, f'{name}-Edge_Cuts.gm1'))
    # transformação Gerber -> coordenadas do projeto: x = gx - x0, y = y1 - gy  (x0,y1 = canto do contorno)
    ex0, ey0, ex1, ey1 = edge.bounds
    bx0, by0, bx1, by1 = d.outline.bounds
    dx = ex0 - (bx0 - 0.075)
    tf = lambda g: affinity.affine_transform(g, [1, 0, 0, -1, -(ex0 - (bx0 - 0.075)) + 0, (ey1 - 0.075) + by0 + 0.075 - 0.075])
    # o UniPCB usa a caixa do contorno (com a espessura da linha) como origem
    ox = ex0 + 0.075 - bx0
    oy = ey1 - 0.075 + by0
    tf = lambda g: affinity.affine_transform(g, [1, 0, 0, -1, -ox, oy])
    cu = tf(cu)
    outline = Polygon(tf(edge).exterior.coords) if hasattr(tf(edge), 'exterior') else d.outline
    res = dict(erros=[])
    # confere alinhamento: o pad 1 do ESP tem de cair no cobre
    p1 = d.pad('U1.L1')
    res['alinhamento_ok'] = cu.contains(Point(p1.x, p1.y))
    islands = list(cu.geoms) if hasattr(cu, 'geoms') else [cu]
    res['ilhas_de_cobre'] = len(islands)
    tree = STRtree(islands)
    pads = [p for p in d.all_pads() if not p.npth]
    where = {}
    for p in pads:
        hits = [int(i) for i in tree.query(Point(p.x, p.y)) if islands[int(i)].contains(Point(p.x, p.y))]
        if not hits:
            res['erros'].append(f'pad sem cobre no Gerber: {p.full}')
            continue
        where[p.full] = hits[0]
    parent = list(range(len(islands)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for grp in d.internal:
        ids = [where[x] for x in grp if x in where]
        for k in ids[1:]:
            parent[find(k)] = find(ids[0])
    nets_in = {}
    for p in pads:
        if p.full in where:
            nets_in.setdefault(find(where[p.full]), set()).add(p.net)
    for root, ns in nets_in.items():
        ns2 = {n for n in ns if n}
        if len(ns2) > 1:
            res['erros'].append(f'CURTO no Gerber entre {sorted(ns2)}')
    by_net = {}
    for p in pads:
        if p.full in where and p.net and not p.net.startswith('NC:'):
            by_net.setdefault(p.net, set()).add(find(where[p.full]))
    res['redes'] = {n: ('OK' if len(r) == 1 else f'ABERTA ({len(r)} partes)') for n, r in sorted(by_net.items())}
    for n, r in by_net.items():
        if len(r) > 1:
            res['erros'].append(f'REDE ABERTA no Gerber: {n}')
    # folga mínima entre ilhas diferentes
    mind = 1e9
    for i, g in enumerate(islands):
        for j in tree.query(g.buffer(0.6)):
            j = int(j)
            if j > i:
                mind = min(mind, g.distance(islands[j]))
    res['folga_min_gerber_mm'] = round(mind, 3)
    if mind < d.P['cl'] - 0.002:
        res['erros'].append(f'folga {mind:.3f} < {d.P["cl"]}')
    # borda
    bd = cu.distance(d.outline.exterior) if d.outline.contains(cu) else 0
    res['cobre_borda_min_mm'] = round(min(g.distance(d.outline.exterior) for g in islands), 3)
    if res['cobre_borda_min_mm'] < d.P['edge'] - 0.002:
        res['erros'].append('cobre perto demais da borda')
    # furação
    xf = ExcellonFile.open(os.path.join(FAB, f'{name}.drl'))
    holes = []
    for o in xf.objects:
        x, y = o.x - ox, oy - o.y
        holes.append((x, y, o.aperture.diameter))
    res['furos_excellon'] = len(holes)
    falt = []
    for p in d.all_pads():
        if not any(abs(h[0] - p.x) < 0.01 and abs(h[1] - p.y) < 0.01 and abs(h[2] - p.drill) < 0.01 for h in holes):
            falt.append(p.full)
    res['pads_sem_furo'] = falt
    if falt:
        res['erros'].append(f'{len(falt)} pads sem furo correspondente')
    from collections import Counter
    res['furos_por_diametro'] = dict(Counter(round(h[2], 2) for h in holes))
    res['contorno_mm'] = [round(ex1 - ex0 - 0.15, 2), round(ey1 - ey0 - 0.15, 2)]
    with open(os.path.join(FAB, 'verificacao-gerber.json'), 'w') as f:
        json.dump(res, f, indent=1, ensure_ascii=False)
    print(json.dumps({k: v for k, v in res.items() if k != 'redes'}, indent=1, ensure_ascii=False))
    print('redes:', sum(1 for v in res['redes'].values() if v == 'OK'), 'OK de', len(res['redes']))
    return res


if __name__ == '__main__':
    main()
