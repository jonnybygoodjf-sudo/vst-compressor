"""Gabarito em papel, escala 1:1 (PDF A4 deitado).
Pág. 1: vista de cima (lado dos componentes): contorno, todos os furos com o diâmetro, contorno dos módulos,
        pino 1 e régua de 50 mm para conferir a escala da impressão.
Pág. 2: cobre (lado de baixo) ESPELHADO, como fica olhando a placa por baixo.
Uso: cd pcb/gerador && python3 gabarito.py
"""
import os
import pickle
import sys

from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
from pcblib import as_polys  # noqa

d, pour = pickle.load(open(os.path.join(AQUI, '.ultimo.pkl'), 'rb'))
OUT = os.path.join(os.path.dirname(AQUI), 'gabarito-1a1.pdf')
PW, PH = landscape(A4)
x0, y0, x1, y1 = d.outline.bounds
BW, BH = x1 - x0, y1 - y0
OX = (PW / mm - BW) / 2           # margem esquerda (mm)
OY = 40.0                          # distância da base da página até a borda de baixo da placa (mm)


def P(x, y, mirror=False):
    xx = (x1 - x) if mirror else (x - x0)
    return (OX + xx) * mm, (OY + (y1 - y)) * mm


def ruler(c):
    c.setLineWidth(0.3)
    bx, by = 15 * mm, 15 * mm
    c.line(bx, by, bx + 50 * mm, by)
    for k in range(0, 51):
        h = 3 if k % 10 == 0 else (2 if k % 5 == 0 else 1)
        c.line(bx + k * mm, by, bx + k * mm, by + h * mm)
    c.setFont('Helvetica', 7)
    for k in range(0, 51, 10):
        c.drawCentredString(bx + k * mm, by - 3 * mm, f'{k}')
    c.drawString(bx + 52 * mm, by, '<- tem de medir 50,0 mm com régua/paquímetro (imprimir em 100%, sem "ajustar à página")')


def outline(c, mirror=False, dash=False):
    c.setLineWidth(0.4)
    pts = [P(x, y, mirror) for x, y in d.outline.exterior.coords]
    p = c.beginPath()
    p.moveTo(*pts[0])
    for q in pts[1:]:
        p.lineTo(*q)
    p.close()
    c.drawPath(p, stroke=1, fill=0)


def page1(c):
    c.setFont('Helvetica-Bold', 11)
    c.drawString(15 * mm, PH - 12 * mm, 'Mixer DSP - GABARITO 1:1 - vista de CIMA (lado dos componentes)')
    c.setFont('Helvetica', 8)
    c.drawString(15 * mm, PH - 17 * mm, f'Placa {BW:.1f} x {BH:.1f} mm.  Coloque os módulos reais por cima: cada pino tem de cair no centro do seu círculo.')
    c.drawString(15 * mm, PH - 21 * mm, 'Tracejado = contorno do módulo (medido em foto: PROVISÓRIO). Quadrado = pino 1. X = furo de fixação M3.')
    outline(c)
    # corpos dos módulos/componentes (tracejado)
    c.setDash(2, 1.5)
    c.setLineWidth(0.25)
    for comp in d.comps:
        b = comp.body_poly()
        if b is None or hasattr(comp, 'jump'):
            continue
        pts = [P(x, y) for x, y in b.exterior.coords]
        p = c.beginPath()
        p.moveTo(*pts[0])
        for q in pts[1:]:
            p.lineTo(*q)
        c.drawPath(p, stroke=1, fill=0)
    c.setDash()
    # antena (fora da placa) e zona sem cobre
    c.setLineWidth(0.2)
    c.setDash(1, 1)
    pts = [P(x, y) for x, y in d.zone.exterior.coords]
    p = c.beginPath()
    p.moveTo(*pts[0])
    for q in pts[1:]:
        p.lineTo(*q)
    c.drawPath(p, stroke=1, fill=0)
    c.setDash()
    # furos
    for comp in d.comps:
        for pd in comp.pads:
            X, Y = P(pd.x, pd.y)
            r = pd.drill / 2 * mm
            c.setLineWidth(0.25)
            if pd.npth and pd.drill > 3:
                c.circle(X, Y, r)
                c.line(X - r, Y - r, X + r, Y + r)
                c.line(X - r, Y + r, X + r, Y - r)
                continue
            if pd.shape == 'rect' and not pd.npth:
                c.rect(X - 1.0 * mm, Y - 1.0 * mm, 2.0 * mm, 2.0 * mm)
            c.circle(X, Y, r)
            c.line(X - 0.3 * mm, Y, X + 0.3 * mm, Y)
            c.line(X, Y - 0.3 * mm, X, Y + 0.3 * mm)
        if hasattr(comp, 'jump'):
            (ax, ay), (bx, by) = comp.jump
            c.setDash(0.6, 0.6)
            c.line(*P(ax, ay), *P(bx, by))
            c.setDash()
    # nomes
    c.setFont('Helvetica', 6)
    for comp in d.comps:
        b = comp.body_poly()
        if b is None:
            continue
        X, Y = P(b.centroid.x, b.centroid.y)
        c.drawCentredString(X, Y, comp.label if comp.ref.startswith('U') else comp.ref)
    ruler(c)
    c.setFont('Helvetica', 7)
    c.drawString(15 * mm, 8 * mm, 'Furos: Ø1,0 mm (pinos e componentes), Ø1,1 (furos sem cobre do DAC), Ø1,3 (borne de parafuso), Ø3,2 (M3). '
                 'Linha tracejada fina = jumper de fio.')


def page2(c):
    c.setFont('Helvetica-Bold', 11)
    c.drawString(15 * mm, PH - 12 * mm, 'Mixer DSP - COBRE (lado de BAIXO) - ESPELHADO, como se vê olhando a placa por baixo')
    c.setFont('Helvetica', 8)
    c.drawString(15 * mm, PH - 17 * mm, 'Preto = cobre que fica. Para o laser, use os arquivos da pasta fabricacao/ (Gerber/SVG); esta folha é só referência 1:1.')
    c.setFillColorRGB(0, 0, 0)
    # plano de GND
    for pg in as_polys(pour):
        p = c.beginPath()
        ext = [P(x, y, True) for x, y in pg.exterior.coords]
        p.moveTo(*ext[0])
        for q in ext[1:]:
            p.lineTo(*q)
        p.close()
        for h in pg.interiors:
            hp = [P(x, y, True) for x, y in h.coords]
            p.moveTo(*hp[0])
            for q in hp[1:]:
                p.lineTo(*q)
            p.close()
        c.drawPath(p, stroke=0, fill=1, fillMode=0)
    c.setLineCap(1)
    c.setLineJoin(1)
    c.setStrokeColorRGB(0, 0, 0)
    for t in d.tracks:
        c.setLineWidth(t['w'] * mm)
        p = c.beginPath()
        pts = [P(x, y, True) for x, y in t['pts']]
        p.moveTo(*pts[0])
        for q in pts[1:]:
            p.lineTo(*q)
        c.drawPath(p, stroke=1, fill=0)
    for pd in d.all_pads():
        X, Y = P(pd.x, pd.y, True)
        if not pd.npth:
            if pd.shape == 'rect':
                c.rect(X - pd.d / 2 * mm, Y - pd.d / 2 * mm, pd.d * mm, pd.d * mm, stroke=0, fill=1)
            else:
                c.circle(X, Y, pd.d / 2 * mm, stroke=0, fill=1)
    c.setFillColorRGB(1, 1, 1)
    for pd in d.all_pads():
        X, Y = P(pd.x, pd.y, True)
        c.circle(X, Y, min(pd.drill / 2, 0.35) * mm, stroke=0, fill=1)
    c.setFillColorRGB(0, 0, 0)
    c.setStrokeColorRGB(0, 0, 0)
    c.setLineWidth(0.3)
    outline(c, mirror=True)
    c.setFont('Helvetica', 7)
    for j in d.jumpers:
        (ax, ay), (bx, by) = j['a'], j['b']
        X, Y = P((ax + bx) / 2, (ay + by) / 2, True)
        c.setFillColorRGB(1, 0, 0)
        c.drawCentredString(X, Y + 1.5 * mm, j['ref'])
    c.setFillColorRGB(0, 0, 0)
    ruler(c)


cv = canvas.Canvas(OUT, pagesize=(PW, PH))
cv.setTitle('Mixer DSP - gabarito 1:1')
page1(cv)
cv.showPage()
page2(cv)
cv.showPage()
cv.save()
print('ok', OUT)
