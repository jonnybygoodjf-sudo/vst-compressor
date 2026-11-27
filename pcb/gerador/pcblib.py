"""Biblioteca do gerador da PCB do Mixer DSP (uma face).

Geometria em mm, eixo y para baixo (igual ao UniPCB).
Rotação igual ao UniPCB (função ctf): rot=90 -> (x,y)->(-y,x); rot=270 -> (x,y)->(y,-x).
"""
import heapq
import math

import numpy as np
from scipy import ndimage as nd
from shapely.geometry import Point, LineString, Polygon, MultiPolygon, box
from shapely.ops import unary_union

# ---------------------------------------------------------------- geometria


def rot_pt(x, y, rot):
    r = int(round(rot)) % 360
    cs, sn = {0: (1, 0), 90: (0, 1), 180: (-1, 0), 270: (0, -1)}[r]
    return x * cs - y * sn, x * sn + y * cs


class Pad:
    def __init__(self, name, x, y, d, drill, shape='round', net=None, npth=False):
        self.name, self.x, self.y, self.d, self.drill = name, x, y, d, drill
        self.shape, self.net, self.npth = shape, net, npth
        self.comp = None

    def geom(self, grow=0.0):
        if self.npth:
            return Point(self.x, self.y).buffer(self.drill / 2 + grow, 32)
        if self.shape == 'rect':
            h = self.d / 2 + grow
            return box(self.x - h, self.y - h, self.x + h, self.y + h)
        return Point(self.x, self.y).buffer(self.d / 2 + grow, 32)

    @property
    def full(self):
        return f'{self.comp.ref}.{self.name}'


class Footprint:
    """pads: lista (nome, x, y, d, furo, forma, npth); lines: [(x1,y1,x2,y2)]"""

    def __init__(self, name, pads, lines, keepouts=None, body=None):
        self.name, self.pads, self.lines = name, pads, lines
        self.body = body  # retângulo (x0,y0,x1,y1) do corpo, p/ verificar sobreposição

class Comp:
    def __init__(self, ref, fp, x, y, rot=0, val='', label=None):
        self.ref, self.fp, self.x, self.y, self.rot, self.val = ref, fp, x, y, rot, val
        self.label = label if label is not None else ref
        self.pads = []
        for (n, px, py, d, drill, shape, npth) in fp.pads:
            ax, ay = rot_pt(px, py, rot)
            p = Pad(n, round(x + ax, 4), round(y + ay, 4), d, drill, shape, None, npth)
            p.comp = self
            self.pads.append(p)

    def pad(self, name):
        for p in self.pads:
            if p.name == name:
                return p
        raise KeyError(f'{self.ref}.{name}')

    def to_abs(self, px, py):
        ax, ay = rot_pt(px, py, self.rot)
        return self.x + ax, self.y + ay

    def body_poly(self):
        if not self.fp.body:
            return None
        x0, y0, x1, y1 = self.fp.body
        pts = [self.to_abs(x0, y0), self.to_abs(x1, y0), self.to_abs(x1, y1), self.to_abs(x0, y1)]
        return Polygon(pts)


# ---------------------------------------------------------------- roteador A*


class Router:
    """Roteador em grade, uma camada, com folga por distância euclidiana."""

    def __init__(self, board_poly, res=0.2, edge_cl=1.0):
        self.board = board_poly
        x0, y0, x1, y1 = board_poly.bounds
        self.ox, self.oy, self.res = x0, y0, res
        self.nx = int(math.ceil((x1 - x0) / res)) + 1
        self.ny = int(math.ceil((y1 - y0) / res)) + 1
        xs = self.ox + np.arange(self.nx) * res
        ys = self.oy + np.arange(self.ny) * res
        self.X, self.Y = np.meshgrid(xs, ys)
        # distância até a borda (para dentro)
        inside = self._raster(board_poly)
        self.dist_edge = nd.distance_transform_edt(inside) * res
        self.edge_cl = edge_cl
        self.keepouts = []  # (poly, nets_permitidas)

    def _raster(self, geom):
        from shapely import contains_xy
        return contains_xy(geom, self.X, self.Y)

    def cell(self, x, y):
        return int(round((y - self.oy) / self.res)), int(round((x - self.ox) / self.res))

    def xy(self, r, c):
        return self.ox + c * self.res, self.oy + r * self.res

    def blocked_map(self, other_copper, w, cl, net, margin=0.12):
        """True onde o centro de uma trilha de largura w NÃO pode passar."""
        need = cl + w / 2 + margin
        occ = np.zeros((self.ny, self.nx), bool)
        if other_copper is not None and not other_copper.is_empty:
            # rasteriza o cobre das outras redes já engordado pela folga
            grown = other_copper.buffer(need, 8)
            occ = self._raster(grown)
        blk = occ | (self.dist_edge < self.edge_cl + w / 2 + margin)
        for poly, allowed in self.keepouts:
            if net in allowed:
                continue
            blk |= self._raster(poly.buffer(w / 2 + margin, 8))
        return blk

    def astar(self, blk, starts, targets_mask, target_pts, turn_pen=0.4):
        ny, nx = blk.shape
        INF = 1e18
        g = np.full((ny, nx), INF)
        came = -np.ones((ny, nx), np.int64)
        dirn = -np.ones((ny, nx), np.int8)
        moves = [(-1, 0, 1.0), (1, 0, 1.0), (0, -1, 1.0), (0, 1, 1.0),
                 (-1, -1, 1.4142), (-1, 1, 1.4142), (1, -1, 1.4142), (1, 1, 1.4142)]
        tp = np.array(target_pts) if target_pts else None

        def h(r, c):
            if tp is None:
                return 0.0
            dr = np.abs(tp[:, 0] - r)
            dc = np.abs(tp[:, 1] - c)
            d = np.maximum(dr, dc) + 0.4142 * np.minimum(dr, dc)
            return float(d.min()) * 0.999

        pq = []
        for (r, c) in starts:
            if 0 <= r < ny and 0 <= c < nx:
                g[r, c] = 0
                heapq.heappush(pq, (h(r, c), 0.0, r, c))
        while pq:
            f, gc, r, c = heapq.heappop(pq)
            if gc > g[r, c]:
                continue
            if targets_mask[r, c]:
                path = [(r, c)]
                k = came[r, c]
                while k >= 0:
                    rr, cc = divmod(int(k), nx)
                    path.append((rr, cc))
                    k = came[rr, cc]
                return path[::-1]
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
                    heapq.heappush(pq, (ng + h(r2, c2), ng, r2, c2))
        return None


def simplify_path(pts):
    """Remove pontos colineares (pts em mm)."""
    if len(pts) <= 2:
        return pts
    out = [pts[0]]
    for i in range(1, len(pts) - 1):
        ax, ay = out[-1]
        bx, by = pts[i]
        cx, cy = pts[i + 1]
        cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
        if abs(cross) > 1e-9:
            out.append(pts[i])
    out.append(pts[-1])
    return out


# ---------------------------------------------------------------- polígonos -> UniPCB


def poly_to_keyhole(poly):
    """Converte polígono com furos em um contorno único (cortes de largura zero),
    aceito como região G36 no Gerber e preenchido corretamente no canvas/SVG."""
    from shapely.geometry.polygon import orient
    poly = orient(poly, 1.0)
    ext = list(poly.exterior.coords)[:-1]
    holes = [list(h.coords)[:-1] for h in poly.interiors]
    # liga cada furo ao contorno pelo ponto mais à esquerda do furo -> ponto mais próximo
    ring = ext[:]
    for hole in sorted(holes, key=lambda h: min(p[0] for p in h)):
        hi = min(range(len(hole)), key=lambda i: (hole[i][0], hole[i][1]))
        hp = hole[hi]
        # vértice do anel atual mais próximo cujo segmento até hp não cruza outros furos (aprox.)
        best, bd = None, 1e18
        for i, p in enumerate(ring):
            d = (p[0] - hp[0]) ** 2 + (p[1] - hp[1]) ** 2
            if d < bd:
                seg = LineString([p, hp])
                ok = True
                for other in holes:
                    if other is hole:
                        continue
                    if seg.crosses(Polygon(other)):
                        ok = False
                        break
                if ok:
                    best, bd = i, d
        if best is None:
            best = min(range(len(ring)), key=lambda i: (ring[i][0] - hp[0]) ** 2 + (ring[i][1] - hp[1]) ** 2)
        hseq = hole[hi:] + hole[:hi] + [hole[hi]]
        ring = ring[:best + 1] + hseq + ring[best:]
    return [[round(x, 4), round(y, 4)] for x, y in ring]


def as_polys(g):
    if g.is_empty:
        return []
    if isinstance(g, Polygon):
        return [g]
    if isinstance(g, MultiPolygon):
        return list(g.geoms)
    return [p for p in getattr(g, 'geoms', []) if isinstance(p, Polygon)]
