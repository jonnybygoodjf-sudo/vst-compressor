// Abre o projeto no UniPCB (Chrome headless), confere que carregou sem erro,
// tira foto da tela e exporta Gerber/Excellon com a própria função buildFab() do programa.
// Uso: cd pcb/gerador && node validar_unipcb.mjs
import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const PCB = path.dirname(AQUI);
const html = 'file://' + path.join(PCB, 'unipcb.html') + '#fresh';
const proj = path.join(PCB, 'mixer-dsp-pcb.unipcb');
const outFab = path.join(PCB, 'fabricacao');
fs.mkdirSync(outFab, { recursive: true });
fs.mkdirSync(path.join(PCB, 'imagens'), { recursive: true });

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 1 });
const erros = [];
page.on('pageerror', e => erros.push('pageerror: ' + e.message));
page.on('console', m => { if (m.type() === 'error') erros.push('console: ' + m.text()); });
page.on('dialog', async d => { erros.push('dialog: ' + d.message()); await d.dismiss(); });
await page.goto(html);
await page.waitForFunction(() => window.__unipcb);
await page.setInputFiles('#fOpen', proj);
await page.waitForFunction(() => window.__unipcb.doc.items.length > 10, null, { timeout: 15000 });
const info = await page.evaluate(() => {
  const d = window.__unipcb.doc, c = {};
  for (const it of d.items) c[it.type] = (c[it.type] || 0) + 1;
  return { name: d.name, total: d.items.length, porTipo: c };
});
// visualização: só cobre inferior + serigrafia + contorno
await page.evaluate(() => { window.__unipcb.fit(); });
await page.waitForTimeout(400);
await page.screenshot({ path: path.join(PCB, 'imagens', 'unipcb-tela.png') });
// exporta com o buildFab do próprio UniPCB
const files = await page.evaluate(() => window.__unipcb.buildFab());
for (const [n, txt] of Object.entries(files)) fs.writeFileSync(path.join(outFab, n), txt);
// impressão 1:1 do cobre inferior (espelhado e normal) via printSVG do UniPCB
for (const [lay, mir, nome] of [['B.Cu', true, 'cobre-inferior-espelhado.svg'], ['B.Cu', false, 'cobre-inferior-normal.svg'],
  ['F.Silk', false, 'serigrafia-superior.svg'], ['DRILL', false, 'mapa-furos.svg'], ['Edge', false, 'contorno.svg']]) {
  const svg = await page.evaluate(([l, m]) => window.__unipcb.printSVG({ layer: l, mirror: m, holes: true, edge: true }).svg, [lay, mir]);
  fs.writeFileSync(path.join(outFab, nome), svg);
}
await browser.close();
const rel = { info, arquivos: Object.keys(files), erros };
fs.writeFileSync(path.join(outFab, 'validacao-unipcb.json'), JSON.stringify(rel, null, 1));
console.log(JSON.stringify(rel, null, 1));
