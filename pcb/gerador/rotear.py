"""Roteia um projeto (uma face), com jumpers só quando não há outro caminho; depois faz o plano de GND."""
import math
import time

import numpy as np
from shapely.geometry import Point, LineString, Polygon, box
from shapely.ops import unary_union

from pcblib import Router, as_polys
import footprints as F


SIG_ORDER = ['WS1', 'DOUT', 'BCLK1_ESP', 'BCLK1', 'MCLK1_ESP', 'MCLK1', 'DIN1',
             'BCLK2_ESP', 'BCLK2', 'MCLK2_ESP', 'MCLK2', 'WS2', 'DIN2',
             'IN1L', 'IN1R', 'IN2L', 'IN2R', 'OUTR', 'OUTL',
             'EXP_IO18', 'EXP_IO8', 'EXP_IO3', 'EXP_IO46', 'EXP_IO9', 'EXP_IO47', 'EXP_IO21']
PWR_ORDER = ['5V_EXT', '+5V', '3V3']


def make_router(d):
    R = Router(d.outline, res=d.P['res'], edge_cl=d.P['edge'])
    R.keepouts = list(getattr(d, 'route_ko', None) or [(p, allowed) for (p, allowed, name) in d.keepouts])
    # furos M3: sem cobre num raio hole_ko
    for c in d.comps:
        if c.ref.startswith('H'):
            R.keepouts.append((Point(c.x, c.y).buffer(d.P['hole_ko'], 24), set()))
    return R


def gnd_ties(d):
    """Liga MD0/MD1/PMT ao GND do próprio módulo com uma trilha ao longo da fileira."""
    for u in ('U2', 'U3'):
        c = d.comp(u)
        a, b = c.pad('GNDa'), c.pad('PMT')
        d.tracks.append(dict(net='GND', w=d.P['w_pwr'], pts=[(a.x, a.y), (b.x, b.y)], tie=True))


def route_all(d, order=None, verbose=True, allow_jump=True):
    from motor import route_chain
    t0 = time.time()
    R = make_router(d)
    gnd_ties(d)
    order = order or (SIG_ORDER + PWR_ORDER)
    fails = []
    for net, chain, jmp in getattr(d, 'chains', []):
        w = d.P['w_pwr'] if net in ('+5V', '3V3', '5V_EXT', 'GND') else d.P['w_sig']
        ok = route_chain(d, R, net, w, chain, allow_jump=jmp)
        if verbose:
            print(f'  guia {net:6s} {str(chain[0])[:14]:14s}->{str(chain[-1])[:14]:14s} {"ok" if ok else "FALHOU"} ({time.time()-t0:.1f}s)', flush=True)
        if not ok:
            fails.append(net + ' (guia)')
    for net in order:
        if not d.net_pads(net):
            continue
        w = d.P['w_pwr'] if net in ('+5V', '3V3', '5V_EXT') else d.P['w_sig']
        ok = d.route_net(R, net, w)
        if not ok and allow_jump:
            d.log.append(f'tentando com jumper: {net}')
            # desfaz trilhas parciais desta rede e refaz com jumper
            d.tracks = [t for t in d.tracks if t['net'] != net]
            ok = d.route_net(R, net, w, allow_jump=True)
        if not ok:
            fails.append(net)
        if verbose:
            print(f'  {net:10s} {"ok" if ok else "FALHOU"}  ({time.time()-t0:.1f}s)', flush=True)
    return R, fails


def pour_gnd(d, R, thermal=True):
    P = d.P
    cl, edge = P['cl'], P['edge']
    area = d.outline.buffer(-edge)
    for p, allowed, name in d.keepouts:
        if name.startswith('antena'):
            area = area.difference(p)
    allowed_area = area
    for c in d.comps:
        if c.ref.startswith('H'):
            area = area.difference(Point(c.x, c.y).buffer(P['hole_ko'], 32))
    other = d.copper_of(exclude='GND')
    area = area.difference(other.buffer(cl + 0.02, 16))
    area = area.difference(d.holes_geom().buffer(cl + 0.02, 16))
    gnd_pads = [p for p in d.all_pads() if p.net == 'GND']
    if thermal:
        # alívio térmico: anel livre em volta do pad de GND + 4 raios de 0,8 mm
        for p in gnd_pads:
            ring = p.geom().buffer(cl + 0.02, 16)
            area = area.difference(ring)
            sw = 1.0
            r = p.d / 2 + cl + 0.6
            spokes = unary_union([box(p.x - r, p.y - sw / 2, p.x + r, p.y + sw / 2),
                                  box(p.x - sw / 2, p.y - r, p.x + sw / 2, p.y + r)])
            spokes = spokes.difference(other.buffer(cl + 0.02, 16)).difference(d.holes_geom().buffer(cl + 0.02, 16))
            area = area.union(spokes.intersection(allowed_area))
    # trilhas de GND (ligações MD0/MD1/PMT) entram no plano
    gtr = unary_union([d.track_geom(t) for t in d.tracks if t['net'] == 'GND'] or [Polygon()])
    area = unary_union([area, gtr]).buffer(0)
    # remove pedaços pequenos demais para o laser (< 0,6 mm de largura)
    area = area.buffer(-0.3, 8).buffer(0.3, 8).intersection(area.buffer(0.01))
    return area


def gnd_islands(d, pour):
    """Componentes conexos do GND (plano + pads + trilhas de GND). Ligações internas dos módulos contam."""
    gnd_pads = [p for p in d.all_pads() if p.net == 'GND']
    copper = unary_union([pour] + [p.geom() for p in gnd_pads] +
                         [d.track_geom(t) for t in d.tracks if t['net'] == 'GND'])
    comps = as_polys(copper)
    # quais pads em qual ilha
    idx = {}
    for i, g in enumerate(comps):
        for p in gnd_pads:
            if g.distance(Point(p.x, p.y)) < 1e-6:
                idx.setdefault(i, []).append(p)
    # une ilhas ligadas por dentro de um módulo
    parent = list(range(len(comps)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for grp in d.internal:
        ids = [i for i, ps in idx.items() if any(p.full in grp for p in ps)]
        for a in ids[1:]:
            parent[find(a)] = find(ids[0])
    groups = {}
    for i in idx:
        groups.setdefault(find(i), []).extend(idx[i])
    return comps, groups


def connect_gnd_islands(d, R, verbose=True, max_iter=12):
    """Liga ilhas de GND ao GND principal (trilha; jumper só se não houver caminho)."""
    from motor import route_chain
    for it in range(max_iter):
        pour = pour_gnd(d, R)
        comps, groups = gnd_islands(d, pour)
        main = None
        for root, pads in groups.items():
            if any(p.full == 'U1.L22' for p in pads):
                main = root
        others = [(root, pads) for root, pads in groups.items() if root != main]
        if not others:
            return pour, comps, groups
        root, pads = others[0]
        # alvo: todo o cobre do GND principal
        main_geom = unary_union([comps[i] for i in range(len(comps)) if _same(groups, i, main, comps)])
        main_geom = main_geom.buffer(-0.35) if not main_geom.buffer(-0.35).is_empty else main_geom
        ok = False
        for p in sorted(pads, key=lambda p: main_geom.distance(Point(p.x, p.y))):
            ok = route_chain(d, R, 'GND', 0.8, [p.full, main_geom], allow_jump=False)
            if ok:
                break
        if not ok:
            for p in sorted(pads, key=lambda p: main_geom.distance(Point(p.x, p.y))):
                ok = route_chain(d, R, 'GND', 0.8, [p.full, main_geom], allow_jump=True)
                if ok:
                    break
        if verbose:
            print('  ilha GND', [p.full for p in pads][:6], 'ok' if ok else 'FALHOU', flush=True)
        if not ok:
            d.log.append('ilha GND sem ligação: ' + ','.join(p.full for p in pads))
            return pour, comps, groups
        d.log = [l for l in d.log if not l.startswith('FALHOU trecho GND')]
    pour = pour_gnd(d, R)
    comps, groups = gnd_islands(d, pour)
    return pour, comps, groups


def _same(groups, i, main, comps):
    # a ilha i pertence ao grupo principal se algum pad do grupo principal toca a ilha i
    for p in groups[main]:
        if comps[i].distance(Point(p.x, p.y)) < 1e-6:
            return True
    return False


def remove_dead_copper(d, pour):
    """Tira do plano os pedaços que não tocam nenhum pad/trilha de GND (cobre flutuante)."""
    gnd = unary_union([p.geom() for p in d.all_pads() if p.net == 'GND'] +
                      [d.track_geom(t) for t in d.tracks if t['net'] == 'GND'])
    keep = [pg for pg in as_polys(pour) if pg.intersects(gnd)]
    return unary_union(keep)
