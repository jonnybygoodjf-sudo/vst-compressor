"""Motor: roteamento de uma face com jumpers automáticos, plano de GND, ERC/DRC e exportação."""
import heapq
import json
import math
import random

import numpy as np
from shapely import contains_xy
from shapely.geometry import Point, LineString, Polygon, box
from shapely.ops import unary_union

from pcblib import Comp, Router, simplify_path, poly_to_keyhole, as_polys
import footprints as F


class Design:
    def __init__(self, P):
        self.P = P                      # parâmetros (larguras, folgas...)
        self.comps = []
        self.netmap = {}                # 'REF.PAD' -> rede
        self.internal = []              # grupos de pads ligados dentro do módulo
        self.tracks = []                # dict(net, w, pts)
        self.jumpers = []
        self.outline = None             # Polygon
        self.keepouts = []              # (Polygon, nets permitidas, nome)
        self.pour_keepouts = []         # Polygon (sem plano de GND)
        self.texts = []                 # dict(str, x, y, size, rot)
        self.silk = []                  # dict(pts, w)
        self.log = []

    # ------------------------------------------------------------ construção
    def add(self, ref, fp, x, y, rot=0, val='', label=None):
        c = Comp(ref, fp, x, y, rot, val, label)
        self.comps.append(c)
        return c

    def comp(self, ref):
        return next(c for c in self.comps if c.ref == ref)

    def pad(self, full):
        ref, name = full.split('.', 1)
        return self.comp(ref).pad(name)

    def connect(self, net, *pins):
        for p in pins:
            if p in self.netmap and self.netmap[p] != net:
                raise ValueError(f'{p} já está em {self.netmap[p]}, não pode ir para {net}')
            self.netmap[p] = net

    def finalize_nets(self):
        for c in self.comps:
            for p in c.pads:
                if p.npth:
                    p.net = None
                    continue
                p.net = self.netmap.get(p.full, f'NC:{p.full}')

    def all_pads(self):
        return [p for c in self.comps for p in c.pads]

    def net_pads(self, net):
        return [p for p in self.all_pads() if p.net == net]

    def add_jumper(self, ref, a, b, net):
        """Jumper de fio colocado à mão (dois pads ligados por um fio por cima da placa)."""
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        jc = self.add(ref, F.jumper(round(L, 4)), a[0], a[1], 0)
        jc.pads[0].x, jc.pads[0].y = a
        jc.pads[1].x, jc.pads[1].y = b
        jc.jump = (a, b)
        self.connect(net, f'{ref}.1', f'{ref}.2')
        self.internal.append({f'{ref}.1', f'{ref}.2'})
        self.jumpers.append(dict(ref=ref, net=net, a=a, b=b, L=L))
        return jc

    # ------------------------------------------------------------ geometria de cobre
    def pad_geom(self, p, grow=0.0):
        return p.geom(grow)

    def track_geom(self, t):
        return LineString(t['pts']).buffer(t['w'] / 2, 12)

    def copper_of(self, net=None, exclude=None):
        gs = []
        for p in self.all_pads():
            if p.npth:
                continue
            if (net is None or p.net == net) and (exclude is None or p.net != exclude):
                gs.append(p.geom())
        for t in self.tracks:
            if (net is None or t['net'] == net) and (exclude is None or t['net'] != exclude):
                gs.append(self.track_geom(t))
        return unary_union(gs) if gs else Polygon()

    def holes_geom(self):
        return unary_union([p.geom() for p in self.all_pads() if p.npth] or [Polygon()])

    # ------------------------------------------------------------ roteamento
    def groups_of(self, net):
        """Pads da rede agrupados: pads do mesmo componente ligados internamente contam como 1."""
        pads = self.net_pads(net)
        groups = []
        used = set()
        for p in pads:
            if id(p) in used:
                continue
            g = [p]
            used.add(id(p))
            for grp in self.internal:
                if p.full in grp:
                    for q in pads:
                        if q.full in grp and id(q) not in used:
                            g.append(q)
                            used.add(id(q))
            groups.append(g)
        return groups

    def route_net(self, R, net, w, order_hint=None, allow_jump=False, only_groups=None):
        P = self.P
        groups = only_groups or self.groups_of(net)
        if len(groups) < 2:
            return True
        if order_hint:
            groups.sort(key=order_hint)
        done = [groups[0]]
        todo = groups[1:]
        while todo:
            other = self.copper_of(exclude=net)
            hard = unary_union([other_pads for other_pads in [self._other_pads(net)]] + [self.holes_geom()])
            blk = R.blocked_map(unary_union([other, self.holes_geom()]), w, P['cl'], net)
            # cobre da própria rede já ligado (alvo)
            tree = unary_union([p.geom() for g in done for p in g] +
                               [self.track_geom(t) for t in self.tracks if t['net'] == net and t.get('tree', True)])
            tmask = R._raster(tree) & ~blk
            # escolhe o grupo pendente mais próximo da árvore
            todo.sort(key=lambda g: min(tree.distance(Point(p.x, p.y)) for p in g))
            g = todo[0]
            smask = R._raster(unary_union([p.geom() for p in g])) & ~blk
            starts = list(zip(*np.nonzero(smask)))
            tpts = [R.cell(p.x, p.y) for gg in done for p in gg]
            jump = None
            if allow_jump:
                jump = self._jump_maps(R, net, w, other, hard)
            path = astar_jump(R, blk, starts, tmask, tpts, jump, P)
            if path is None:
                self.log.append(f'FALHOU: {net} -> {g[0].full}')
                return False
            self._commit_path(R, net, w, path, g, tree)
            done.append(g)
            todo = todo[1:]
        return True

    def _other_pads(self, net):
        return unary_union([p.geom() for p in self.all_pads() if not p.npth and p.net != net] or [Polygon()])

    def _jump_maps(self, R, net, w, other_copper, hard):
        P = self.P
        # onde cabe um pad de jumper (raio do pad + folga)
        need_pad = P['cl'] + F.PAD / 2 + 0.12
        padblk = R._raster(unary_union([other_copper, self.holes_geom()]).buffer(need_pad, 8)) | \
            (R.dist_edge < P['edge'] + F.PAD / 2 + 0.12)
        # nenhum furo de jumper perto de outro furo (de qualquer rede): centros a >= 2,6 mm
        allpads = unary_union([Point(p.x, p.y).buffer(2.6, 12) for p in self.all_pads()])
        padblk |= R._raster(allpads)
        for poly, allowed in R.keepouts:
            if net not in allowed:
                padblk |= R._raster(poly.buffer(F.PAD / 2 + 0.12, 8))
        # obstáculos que o fio do jumper não pode atravessar por cima: nada (o fio passa por cima de tudo),
        # mas não pode pular por cima de pads/furos (onde ficam outros componentes)
        hardblk = R._raster(hard.buffer(0.3, 8))
        return padblk, hardblk

    def _commit_path(self, R, net, w, path, g, tree):
        # path: lista de (r, c, jumpflag)
        segs, cur = [], []
        for (r, c, jf) in path:
            if jf and cur:
                segs.append((cur, (r, c)))
                cur = []
            cur.append(R.xy(r, c))
        segs.append((cur, None))
        # início: centro do pad mais próximo do 1º ponto; fim: se terminou dentro de pad, vai ao centro
        first = segs[0][0][0]
        sp = min(g, key=lambda p: math.hypot(p.x - first[0], p.y - first[1]))
        allpads = [p for p in self.net_pads(net)]
        polylines = []
        for i, (pts, jend) in enumerate(segs):
            pts = list(pts)
            if i == 0:
                pts = [(sp.x, sp.y)] + pts
            if i == len(segs) - 1:
                last = pts[-1]
                for p in allpads:
                    if math.hypot(p.x - last[0], p.y - last[1]) <= F.PAD / 2 - 0.05:
                        pts = pts + [(p.x, p.y)]
                        break
            polylines.append(simplify_path(pts))
        # jumpers entre segmentos
        for i in range(len(segs) - 1):
            a = polylines[i][-1]
            b = polylines[i + 1][0]
            jn = len(self.jumpers) + 1
            ref = f'J{jn}'
            while any(c.ref == ref for c in self.comps):
                jn += 1
                ref = f'J{jn}'
            L = math.hypot(b[0] - a[0], b[1] - a[1])
            ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
            self.jumpers.append(dict(ref=ref, net=net, a=a, b=b, L=L, ang=ang))
            jc = self.add(ref, F.jumper(round(L, 4)), a[0], a[1], 0)
            # posiciona os dois pads exatamente em a e b (jumper pode ser diagonal)
            jc.pads[0].x, jc.pads[0].y = round(a[0], 4), round(a[1], 4)
            jc.pads[1].x, jc.pads[1].y = round(b[0], 4), round(b[1], 4)
            jc.pads[0].net = jc.pads[1].net = net
            jc.jump = (a, b)
            self.netmap[f'{ref}.1'] = net
            self.netmap[f'{ref}.2'] = net
            self.internal.append({f'{ref}.1', f'{ref}.2'})
        for pl in polylines:
            if len(pl) >= 2:
                self.tracks.append(dict(net=net, w=w, pts=[(round(x, 4), round(y, 4)) for x, y in pl]))


def astar_jump(R, blk, starts, tmask, tpts, jump, P, turn_pen=0.35):
    ny, nx = blk.shape
    INF = 1e18
    g = np.full((ny, nx), INF)
    came = -np.ones((ny, nx), np.int64)
    jflag = np.zeros((ny, nx), bool)
    dirn = -np.ones((ny, nx), np.int8)
    moves = [(-1, 0, 1.0), (1, 0, 1.0), (0, -1, 1.0), (0, 1, 1.0),
             (-1, -1, 1.4142), (-1, 1, 1.4142), (1, -1, 1.4142), (1, 1, 1.4142)]
    tp = np.array(tpts) if tpts else None
    res = R.res
    jlens = []
    if jump is not None:
        padblk, hardblk = jump
        for L in P['jump_lens']:
            jlens.append(L)
    JPEN = P['jump_pen'] / res

    def h(r, c):
        if tp is None:
            return 0.0
        dr = np.abs(tp[:, 0] - r)
        dc = np.abs(tp[:, 1] - c)
        return float((np.maximum(dr, dc) + 0.4142 * np.minimum(dr, dc)).min()) * 0.5

    pq = []
    for (r, c) in starts:
        g[r, c] = 0
        heapq.heappush(pq, (h(r, c), 0.0, int(r), int(c)))
    while pq:
        f, gc, r, c = heapq.heappop(pq)
        if gc > g[r, c]:
            continue
        if tmask[r, c]:
            path = [(r, c, bool(jflag[r, c]))]
            k = came[r, c]
            while k >= 0:
                rr, cc = divmod(int(k), nx)
                path.append((rr, cc, bool(jflag[rr, cc])))
                k = came[rr, cc]
            path = path[::-1]
            # jflag marca a célula de chegada do salto
            return path
        d0 = dirn[r, c]
        for i, (dr, dc, cost) in enumerate(moves):
            r2, c2 = r + dr, c + dc
            if r2 < 0 or c2 < 0 or r2 >= ny or c2 >= nx or blk[r2, c2]:
                continue
            if dr and dc and (blk[r, c2] or blk[r2, c]):
                continue
            ng = gc + cost + (turn_pen if (d0 >= 0 and d0 != i) else 0.0)
            if ng < g[r2, c2]:
                g[r2, c2] = ng
                came[r2, c2] = r * nx + c
                dirn[r2, c2] = i
                jflag[r2, c2] = False
                heapq.heappush(pq, (ng + h(r2, c2), ng, r2, c2))
        if jlens and not padblk[r, c]:
            for i, (dr, dc, cost) in enumerate(moves[:4]):
                for L in jlens:
                    n = int(round(L / res))
                    r2, c2 = r + dr * n, c + dc * n
                    if r2 < 0 or c2 < 0 or r2 >= ny or c2 >= nx or padblk[r2, c2] or blk[r2, c2]:
                        continue
                    # o fio do jumper não pode passar por cima de pads/furos
                    rr = np.linspace(r, r2, n + 1).round().astype(int)
                    cc = np.linspace(c, c2, n + 1).round().astype(int)
                    if hardblk[rr, cc].any():
                        continue
                    # só vale a pena se houver obstáculo no meio
                    if not blk[rr[1:-1], cc[1:-1]].any():
                        continue
                    ng = gc + n + JPEN
                    if ng < g[r2, c2]:
                        g[r2, c2] = ng
                        came[r2, c2] = r * nx + c
                        dirn[r2, c2] = -1
                        jflag[r2, c2] = True
                        heapq.heappush(pq, (ng + h(r2, c2), ng, r2, c2))
    return None


# ------------------------------------------------------------ rotas guiadas (pontos de passagem)
def _item_geom(d, net, item):
    if hasattr(item, 'geom_type'):
        return item
    if isinstance(item, str) and item == 'TREE':
        g = [p.geom() for p in d.net_pads(net)] + [d.track_geom(t) for t in d.tracks if t['net'] == net]
        return unary_union(g)
    if isinstance(item, str):
        return d.pad(item).geom()
    return Point(item).buffer(0.35, 12)


def route_chain(d, R, net, w, chain, allow_jump=False):
    """Roteia uma sequência: chain[0] (pad, ponto ou 'TREE') -> ... -> chain[-1]. Cada trecho vira trilha."""
    P = d.P
    for a, b in zip(chain[:-1], chain[1:]):
        other = d.copper_of(exclude=net)
        blk = R.blocked_map(unary_union([other, d.holes_geom()]), w, P['cl'], net)
        ga, gb = _item_geom(d, net, a), _item_geom(d, net, b)
        smask = R._raster(ga) & ~blk
        tmask = R._raster(gb) & ~blk
        if not smask.any():
            # ponto de partida dentro de área bloqueada: libera um disco pequeno
            smask = R._raster(ga.buffer(0.3))
        starts = list(zip(*np.nonzero(smask)))
        if isinstance(b, tuple):
            tp = [R.cell(*b)]
        elif hasattr(b, 'geom_type') or b == 'TREE':
            tp = None
        else:
            pb = d.pad(b)
            tp = [R.cell(pb.x, pb.y)]
        jump = None
        if allow_jump:
            hard = unary_union([d._other_pads(net), d.holes_geom()])
            jump = d._jump_maps(R, net, w, other, hard)
        path = astar_jump(R, blk, starts, tmask, tp, jump, P)
        if path is None:
            d.log.append(f'FALHOU trecho {net}: {a} -> {b}')
            return False
        # converte e ajusta pontas
        g = [d.pad(a)] if isinstance(a, str) and a != 'TREE' else []
        _commit_chain_leg(d, R, net, w, path, a, b)
    return True


def _commit_chain_leg(d, R, net, w, path, a, b):
    segs, cur = [], []
    for (r, c, jf) in path:
        if jf and cur:
            segs.append(cur)
            cur = []
        cur.append(R.xy(r, c))
    segs.append(cur)
    if isinstance(a, str) and a != 'TREE':
        pa = d.pad(a)
        segs[0] = [(pa.x, pa.y)] + segs[0]
    elif hasattr(a, 'geom_type'):
        pass
    elif isinstance(a, tuple):
        segs[0] = [a] + segs[0]
    if isinstance(b, str) and b != 'TREE':
        pb = d.pad(b)
        segs[-1] = segs[-1] + [(pb.x, pb.y)]
    elif isinstance(b, tuple):
        segs[-1] = segs[-1] + [b]
    polylines = [simplify_path(s) for s in segs]
    for i in range(len(polylines) - 1):
        A, B = polylines[i][-1], polylines[i + 1][0]
        ref = f'J{len(d.jumpers) + 1}'
        while any(c.ref == ref for c in d.comps):
            ref = 'J%d' % (int(ref[1:]) + 1)
        L = math.hypot(B[0] - A[0], B[1] - A[1])
        d.jumpers.append(dict(ref=ref, net=net, a=A, b=B, L=L))
        jc = d.add(ref, F.jumper(round(L, 4)), A[0], A[1], 0)
        jc.pads[0].x, jc.pads[0].y = round(A[0], 4), round(A[1], 4)
        jc.pads[1].x, jc.pads[1].y = round(B[0], 4), round(B[1], 4)
        jc.pads[0].net = jc.pads[1].net = net
        jc.jump = (A, B)
        d.netmap[f'{ref}.1'] = net
        d.netmap[f'{ref}.2'] = net
        d.internal.append({f'{ref}.1', f'{ref}.2'})
    for pl in polylines:
        if len(pl) >= 2:
            d.tracks.append(dict(net=net, w=w, pts=[(round(x, 4), round(y, 4)) for x, y in pl]))
