# Montagem da PCB do Mixer DSP

Placa de **uma face**: o cobre fica **embaixo** e os componentes ficam **em cima**. Tudo é de furo passante e soldado à mão.

## 0. Antes de cortar a placa

1. Imprima `gabarito-1a1.pdf` **em 100 %**, sem "ajustar à página". Confira a régua impressa: ela tem de medir **50,0 mm**.
2. Ponha as placas reais em cima da página 1 (o ESP32 e os três módulos, com os pinos para baixo). Cada pino tem de cair no centro do seu círculo.
3. As medidas dos módulos PCM1808 e PCM5102A vieram de fotos da internet (**PROVISÓRIO**). Se algum pino não bater, me mande a medida com paquímetro: o gerador refaz tudo em minutos.
4. Faça os testes do item 0 de `teste-continuidade.md` nos módulos, ainda fora da placa.

## 1. Ordem de soldagem (do mais baixo para o mais alto)

| # | Peça | Quantidade | Observação |
|---|---|---|---|
| 1 | **Jumpers de fio J1, J2, J3** | 3 | Fio rígido de 0,5 mm encapado (serve o de cabo de rede), deitado por cima da placa, do lado dos componentes. J1 e J3 ficam embaixo de módulos, por isso vão primeiro e bem rentes. |
| 2 | R1, R2, R3, R4: 33 Ω, 1/4 W, **em pé** | 4 | Ficam em série no MCLK/BCLK, perto do ESP. Se não tiver, use fio (0 Ω). |
| 3 | C2, C4, C5, C7, C8: 100 nF cerâmico (passo 2,54) | 5 | |
| 4 | D1: 1N5819 (Schottky) | 1 | A faixa (catodo) vai no **pad quadrado**, do lado de cima (para o ADC2). |
| 5 | C1, C3, C6: 100 µF / 16 V eletrolítico Ø6,3 | 3 | O **pad quadrado é o +**. |
| 6 | X1, X2, X3: barra de pinos macho 1×3 | 3 | Entradas IN1, IN2 e saída OUT. |
| 7 | X5 (1×5) e X6 (1×4): barra de pinos macho | 2 | Expansão (opcional). |
| 8 | X4: borne de parafuso de 2 vias, passo 5,08 | 1 | Entrada da fonte de 5 V. A entrada do fio fica virada para fora. |
| 9 | Soquetes (barra fêmea) | ver abaixo | Solde com o módulo **encaixado**, para alinhar, e tire o módulo antes de terminar de soldar. |

Soquetes de barra fêmea, passo 2,54:
- **ESP32-S3**: 2 × 1×22. As fileiras ficam a 25,40 mm uma da outra.
- **Cada PCM1808**: 2 × 1×6 e 1 × 1×3 (o 1×3 é das entradas LIN/GND/RIN).
- **PCM5102A**: 1 × 1×6 (cabeçalho) e 1 × 1×9 (fileira longa).
  - Só 3 dos 9 furos têm cobre: ROUT, AGND e LROUT.
  - Os outros 6 (FLT, DEMP, XSMT, FMT, A3V3, AGND) são furos **sem cobre**, só para o pino encaixar. **Não precisa soldar** esses 6.

Se preferir soldar os módulos direto, sem soquete, também funciona. Nesse caso os jumpers J1 e J3 têm de ficar bem rentes à placa.

## 2. Pontes de solda nos módulos

**PCM5102A (GY-PCM5102)**: as pontes ficam no verso. Feche assim:

| Ponte | Pino | Feche em | Por quê |
|---|---|---|---|
| H1 | FLT | **L** | filtro normal |
| H2 | DEMP | **L** | sem de-ênfase |
| H3 | XSMT | **H** | **obrigatório**: em L o DAC fica mudo |
| H4 | FMT | **L** | formato I2S |

O SCK do DAC vai ao GND pela PCB, pelo jumper J3. Assim o DAC usa a PLL interna a partir do BCK.

**PCM1808**: não precisa mexer em nada.
- MD0, MD1 e FMT ("PMT") vão ao GND pela própria PCB, por uma trilha curta ao lado da fileira. Isso deixa o ADC como escravo, em I2S 24 bits, com SCK = 256·fs.
- Se o seu módulo tiver resistor de pull-up nesses pinos, não tem problema: a ligação ao GND prevalece.
- MD0 e MD1 são lidos quando o módulo liga. **Encaixe e tire os módulos sempre com a placa desligada.**

**ESP32-S3 (YD, "5Vin")**: deixe a ponte **IN-OUT** do verso **aberta**, que é o padrão.

## 3. Alimentação

```
Fonte 5 V ──► X4 (+5V) ──► D1 (1N5819) ──► +5V da placa ──► PCM1808 ×2, PCM5102A e pino 5Vin do ESP
                                                      ESP32 (regulador 3,3 V) ──► 3,3 V dos dois PCM1808
```

- **Uso normal (rádio)**: fonte **regulada de 5 V, 1 A ou mais**, de boa qualidade, no borne X4. A tensão na placa fica em cerca de 4,6–4,8 V, por causa do diodo.
- **USB ao mesmo tempo que a fonte** (para programar ou monitorar): é seguro, porque o D1 da placa e os diodos do próprio ESP (D1/D3) impedem que uma alimentação volte para a outra. A serigrafia avisa: "NUNCA USB+FONTE SEM D1". **Nunca troque o D1 por um fio.**
- **Só a USB**: o ESP liga, mas os módulos de áudio **ficam sem 5 V**, porque na YD o pino 5Vin é só entrada.
  - Para alimentar tudo pela USB, feche a ponte IN-OUT do ESP.
  - Nesse caso o limite é o da porta USB (cerca de 500 mA). Para uso na rádio, prefira a fonte.

## 4. Gravação a laser

- O cobre fica **embaixo**. Use `fabricacao/mixer-dsp-pcb-B_Cu.gbl` (Gerber) ou `fabricacao/cobre-inferior-*.svg`:
  - **espelhado**: o laser vê o cobre por baixo, que é o caso normal;
  - **normal**: o software do laser já espelha sozinho.
- O plano de GND ocupa quase toda a placa, então o laser só precisa remover os isolamentos de 0,5 mm, o que economiza tempo.
- Contorno: `fabricacao/mixer-dsp-pcb-Edge_Cuts.gm1` ou `fabricacao/contorno.svg`.
- Furos: `fabricacao/mixer-dsp-pcb.drl` (Excellon) ou `fabricacao/mapa-furos.svg`. São de Ø1,0, Ø1,1, Ø1,3 e Ø3,2 mm.
- Serigrafia, que pode virar gravação rasa no lado de cima: `fabricacao/mixer-dsp-pcb-F_Silkscreen.gto` ou `fabricacao/serigrafia-superior.svg`.
- Trilhas: sinal 0,6 mm, alimentação 1,0 mm, isolamento mínimo 0,5 mm. Esses valores são conservadores. Se o seu laser fizer menos que isso, me diga os limites e eu refaço.
