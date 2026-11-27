"""Verificações: ERC (ligações certas, sem curto, sem rede aberta) e DRC (folgas, larguras, bordas)."""
import math

from shapely.geometry import Point, LineString, Polygon
from shapely.ops import unary_union
from shapely.strtree import STRtree

from pcblib import as_polys


def copper_items(d, pour=None):
    """Lista (geom, rede, descrição) de todo o cobre."""
    items = []
    for p in d.all_pads():
        if p.npth:
            continue
        items.append((p.geom(), p.net, f'pad {p.full}'))
    for i, t in enumerate(d.tracks):
        items.append((d.track_geom(t), t['net'], f'trilha {t["net"]}#{i}'))
    if pour is not None:
        for k, pg in enumerate(as_polys(pour)):
            items.append((pg, 'GND', f'plano GND#{k}'))
    return items


def drc(d, pour=None):
    P = d.P
    res = dict(erros=[])
    items = copper_items(d, pour)
    geoms = [g for g, n, s in items]
    tree = STRtree(geoms)
    min_cl = 1e9
    worst = None
    for i, (g, n, s) in enumerate(items):
        for j in tree.query(g.buffer(P['cl'] + 0.01)):
            j = int(j)
            if j <= i:
                continue
            g2, n2, s2 = items[j]
            if n2 == n:
                continue
            dist = g.distance(g2)
            if dist < min_cl:
                min_cl, worst = dist, (s, s2)
            if dist < P['cl'] - 1e-3:
                res['erros'].append(f'FOLGA {dist:.3f} mm < {P["cl"]} entre {s} e {s2}')
    res['folga_min'] = (round(min_cl, 3), worst)
    # largura mínima de trilha
    res['larg_min'] = min(t['w'] for t in d.tracks)
    # distância cobre-borda
    edge = d.outline.exterior
    dmin, wsrc = 1e9, None
    for g, n, s in items:
        dd = g.distance(edge)
        if not d.outline.contains(g.buffer(-1e-6)) and not d.outline.buffer(1e-6).contains(g):
            res['erros'].append(f'COBRE FORA DA PLACA: {s}')
        if dd < dmin:
            dmin, wsrc = dd, s
    res['borda_min'] = (round(dmin, 3), wsrc)
    if dmin < P['edge'] - 1e-3:
        res['erros'].append(f'BORDA {dmin:.3f} mm < {P["edge"]} ({wsrc})')
    # anel e furo
    anel = min((p.d - p.drill) / 2 for p in d.all_pads() if not p.npth)
    furo = min(p.drill for p in d.all_pads() if not p.npth)
    res['anel_min'] = round(anel, 3)
    res['furo_min'] = round(furo, 3)
    if anel < 0.45:
        res['erros'].append(f'ANEL {anel:.2f} mm pequeno')
    # furos sem cobre (NPTH) x cobre
    holes = [(p.geom(), p.full) for p in d.all_pads() if p.npth]
    hmin = 1e9
    for hg, hn in holes:
        for g, n, s in items:
            dd = hg.distance(g)
            hmin = min(hmin, dd)
            if dd < P['cl'] - 1e-3:
                res['erros'].append(f'FURO {hn} a {dd:.2f} mm de {s}')
    res['furo_npth_min'] = round(hmin, 3)
    # distância entre furos (borda a borda), qualquer rede
    allp = d.all_pads()
    hh = 1e9
    for i in range(len(allp)):
        for j in range(i + 1, len(allp)):
            a, b = allp[i], allp[j]
            dd = math.hypot(a.x - b.x, a.y - b.y) - a.drill / 2 - b.drill / 2
            if dd < hh:
                hh = dd
            if dd < 0.5:
                res['erros'].append(f'FUROS PRÓXIMOS {dd:.2f} mm: {a.full} e {b.full}')
    res['furo_furo_min'] = round(hh, 3)
    # zona da antena: só o que foi liberado
    if hasattr(d, 'zone'):
        z = d.zone
        intr = []
        esc = unary_union([p.geom().buffer(0.25) for p in d.comp('U1').pads])
        for g, n, s in items:
            if s.startswith('pad U1.'):
                continue
            a = g.intersection(z).difference(esc).area
            if a > 1e-4:
                intr.append((s, round(a, 2)))
        res['zona_antena'] = intr
        for s, a in intr:
            if not s.startswith('trilha 3V3'):
                res['erros'].append(f'COBRE NA ZONA DA ANTENA: {s} ({a} mm²)')
        # módulos e componentes dentro da zona
        for c in d.comps:
            if c.ref == 'U1':
                continue
            b = c.body_poly()
            if b is not None and b.intersection(z).area > 0.05:
                res['erros'].append(f'COMPONENTE NA ZONA DA ANTENA: {c.ref}')
    # sobreposição de corpos de componentes (altos)
    bodies = [(c.ref, c.body_poly()) for c in d.comps if c.body_poly() is not None and not hasattr(c, 'jump')]
    for i in range(len(bodies)):
        for j in range(i + 1, len(bodies)):
            a, pa = bodies[i]
            b, pb = bodies[j]
            if pa.intersection(pb).area > 0.05:
                res['erros'].append(f'CORPOS SE TOCAM: {a} e {b}')
    return res


def erc(d, pour=None):
    """Conectividade pela geometria: cada rede tem de formar uma ilha só (jumpers e ligações internas dos
    módulos contam), e nenhuma ilha pode conter duas redes."""
    res = dict(erros=[], redes={})
    items = copper_items(d, pour)
    # une geometrias que se tocam (mesma rede ou não: se tocar rede diferente = curto)
    geoms = [g for g, n, s in items]
    tree = STRtree(geoms)
    parent = list(range(len(items)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    for i, g in enumerate(geoms):
        for j in tree.query(g):
            j = int(j)
            if j != i and g.intersects(geoms[j]):
                union(i, j)
    # jumpers: as duas pontas são o mesmo fio
    idx_pad = {s[4:]: i for i, (g, n, s) in enumerate(items) if s.startswith('pad ')}
    for c in d.comps:
        if hasattr(c, 'jump'):
            union(idx_pad[f'{c.ref}.1'], idx_pad[f'{c.ref}.2'])
    # ligações internas dos módulos
    for grp in d.internal:
        ids = [idx_pad[p] for p in grp if p in idx_pad]
        for k in ids[1:]:
            union(ids[0], k)
    # curtos
    nets_of = {}
    for i, (g, n, s) in enumerate(items):
        nets_of.setdefault(find(i), set()).add(n)
    for root, ns in nets_of.items():
        if len(ns) > 1:
            res['erros'].append(f'CURTO entre redes {sorted(ns)}')
    # redes abertas
    by_net = {}
    for i, (g, n, s) in enumerate(items):
        if s.startswith('pad ') and n and not n.startswith('NC:'):
            by_net.setdefault(n, set()).add(find(i))
    for n, roots in sorted(by_net.items()):
        res['redes'][n] = len(roots)
        if len(roots) > 1:
            # quais pads ficaram de fora
            grupos = {}
            for i, (g, nn, s) in enumerate(items):
                if nn == n and s.startswith('pad '):
                    grupos.setdefault(find(i), []).append(s[4:])
            res['erros'].append(f'REDE ABERTA {n}: ' + ' | '.join(','.join(v) for v in grupos.values()))
    return res


def check_netlist(d, spec):
    """Confere pino a pino contra a tabela da especificação: spec = [(rede, [pinos...])]."""
    out = []
    ok = True
    for net, pins in spec:
        for p in pins:
            real = d.pad(p).net
            good = real == net
            ok &= good
            out.append((net, p, real, 'OK' if good else 'DIVERGE'))
    return ok, out
