"""Renderização PNG (PIL) para conferência visual."""
import colorsys
import hashlib

from PIL import Image, ImageDraw, ImageFont

from pcblib import as_polys


def _font(sz):
    for f in ['/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', '/usr/share/fonts/dejavu/DejaVuSans.ttf']:
        try:
            return ImageFont.truetype(f, sz)
        except OSError:
            pass
    return ImageFont.load_default()


def net_color(net):
    if net is None:
        return (120, 120, 120)
    if net == 'GND':
        return (60, 160, 70)
    if net in ('+5V', '5V_EXT'):
        return (220, 50, 50)
    if net == '3V3':
        return (240, 140, 20)
    if net.startswith('NC:'):
        return (150, 150, 150)
    h = int(hashlib.md5(net.encode()).hexdigest()[:6], 16) / 0xFFFFFF
    r, g, b = colorsys.hsv_to_rgb(h, 0.65, 0.95)
    return (int(r * 255), int(g * 255), int(b * 255))


def render(d, path, scale=8, pour=None, show_bodies=True, show_zone=True, title=None, mirror=False,
           crossings=None):
    x0, y0, x1, y1 = d.outline.bounds
    m = 10
    Wp, Hp = int((x1 - x0 + 2 * m) * scale), int((y1 - y0 + 2 * m) * scale) + (30 if title else 0)
    img = Image.new('RGB', (Wp, Hp), (24, 28, 34))
    dr = ImageDraw.Draw(img, 'RGBA')
    oy_title = 30 if title else 0

    def T(x, y):
        X = (x - x0 + m) * scale
        if mirror:
            X = Wp - X
        return (X, (y - y0 + m) * scale + oy_title)

    def poly(p, fill=None, outline=None, width=1):
        for pg in as_polys(p):
            dr.polygon([T(*c) for c in pg.exterior.coords], fill=fill, outline=outline, width=width)
            for h in pg.interiors:
                dr.polygon([T(*c) for c in h.coords], fill=(24, 28, 34) if fill else None, outline=outline)

    poly(d.outline, fill=(40, 46, 56), outline=(240, 210, 60), width=2)
    if pour is not None:
        poly(pour, fill=(60, 160, 70, 110))
    if show_zone:
        for p, allowed, name in d.keepouts:
            poly(p.intersection(d.outline.buffer(5)), fill=(220, 40, 40, 45), outline=(220, 60, 60, 160))
    for t in d.tracks:
        c = net_color(t['net'])
        pts = [T(*p) for p in t['pts']]
        dr.line(pts, fill=c, width=max(1, int(t['w'] * scale)), joint='curve')
        for p in pts:
            r = t['w'] * scale / 2
            dr.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=c)
    for c in d.comps:
        if show_bodies:
            b = c.body_poly()
            if b is not None and not c.ref.startswith('J') or (b is not None and c.ref.startswith('J') and not hasattr(c, 'jump')):
                poly(b, outline=(200, 200, 230, 140))
        for p in c.pads:
            X, Y = T(p.x, p.y)
            if p.npth:
                r = p.drill / 2 * scale
                dr.ellipse([X - r, Y - r, X + r, Y + r], outline=(230, 230, 230), width=1)
                continue
            r = p.d / 2 * scale
            col = net_color(p.net)
            if p.shape == 'rect':
                dr.rectangle([X - r, Y - r, X + r, Y + r], fill=col)
            else:
                dr.ellipse([X - r, Y - r, X + r, Y + r], fill=col)
            hr = p.drill / 2 * scale
            dr.ellipse([X - hr, Y - hr, X + hr, Y + hr], fill=(10, 10, 10))
        if hasattr(c, 'jump'):
            a, b = c.jump
            dr.line([T(*a), T(*b)], fill=(255, 255, 255), width=3)
            mx, my = T((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
            dr.text((mx + 4, my - 14), c.ref, fill=(255, 255, 255), font=_font(13))
    f = _font(max(10, int(scale * 1.6)))
    for c in d.comps:
        if hasattr(c, 'jump'):
            continue
        b = c.body_poly()
        if b is None:
            continue
        cx, cy = b.centroid.x, b.centroid.y
        X, Y = T(cx, cy)
        txt = c.label
        dr.text((X - len(txt) * 3.5, Y - 6), txt, fill=(255, 255, 255, 200), font=f)
    if crossings:
        for (x, y) in crossings:
            X, Y = T(x, y)
            dr.ellipse([X - 8, Y - 8, X + 8, Y + 8], outline=(255, 0, 255), width=3)
    if title:
        dr.text((10, 6), title, fill=(255, 255, 255), font=_font(18))
    img.save(path)
    return path
