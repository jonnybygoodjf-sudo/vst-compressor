# Netlist da PCB (gerada do projeto)

> Arquivo gerado por `gerador/documentar.py` a partir do mesmo projeto que gera o `.unipcb` e o Gerber. O ERC do gerador e a conferência independente do Gerber (`fabricacao/verificacao-gerber.json`) confirmam que o cobre liga exatamente estes pinos.

## GPIO usados (remapeados para caber numa face só)

| Sinal | GPIO na placa | GPIO no firmware v9e (antes) | Pino físico do ESP | Vai para |
|---|---|---|---|---|
| MCLK1 | **GPIO12** **(mudou)** | GPIO5 | L18 | R2 33 Ω → ADC1 SCK |
| BCLK1 | **GPIO16** **(mudou)** | GPIO6 | L9 | R1 33 Ω → ADC1 BCK e DAC BCK |
| WS1 | **GPIO7** | GPIO7 | L7 | ADC1 LRC e DAC LCK |
| DIN1 | **GPIO10** **(mudou)** | GPIO16 | L16 | ADC1 OUT |
| DOUT | **GPIO15** | GPIO15 | L8 | DAC DIN |
| MCLK2 | **GPIO39** **(mudou)** | GPIO4 | R9 | R4 33 Ω → ADC2 SCK |
| BCLK2 | **GPIO42** **(mudou)** | GPIO17 | R6 | R3 33 Ω → ADC2 BCK |
| WS2 | **GPIO40** **(mudou)** | GPIO18 | R8 | ADC2 LRC |
| DIN2 | **GPIO41** **(mudou)** | GPIO8 | R7 | ADC2 OUT |

## Todas as ligações, rede por rede

Cada linha é um ponto que precisa estar ligado aos outros da mesma rede.

### MCLK1_ESP — MCLK1 (ESP até o resistor)

- ESP32 pino L18 (GPIO12, fileira esq.)
- R2 33 Ω pino 1 (lado do ESP)

### MCLK1 — MCLK1 12,288 MHz → ADC1

- ADC1 (PCM1808 CH1/CH2) SCK
- R2 33 Ω pino 2 (lado do módulo)

### BCLK1_ESP — BCLK1 (ESP até o resistor)

- ESP32 pino L9 (GPIO16, fileira esq.)
- R1 33 Ω pino 1 (lado do ESP)

### BCLK1 — BCLK1 3,072 MHz → ADC1 e DAC

- DAC (PCM5102A) BCK
- ADC1 (PCM1808 CH1/CH2) BCK
- R1 33 Ω pino 2 (lado do módulo)

### WS1 — WS1/LRCK 48 kHz → ADC1 e DAC

- ESP32 pino L7 (GPIO7, fileira esq.)
- DAC (PCM5102A) LCK
- ADC1 (PCM1808 CH1/CH2) LRC

### DIN1 — dados do ADC1 → ESP

- ESP32 pino L16 (GPIO10, fileira esq.)
- ADC1 (PCM1808 CH1/CH2) OUT

### DOUT — dados do ESP → DAC

- ESP32 pino L8 (GPIO15, fileira esq.)
- DAC (PCM5102A) DIN

### MCLK2_ESP — MCLK2 (ESP até o resistor)

- ESP32 pino R9 (GPIO39, fileira dir.)
- R4 33 Ω pino 1 (lado do ESP)

### MCLK2 — MCLK2 → ADC2

- ADC2 (PCM1808 CH3/CH4) SCK
- R4 33 Ω pino 2 (lado do módulo)

### BCLK2_ESP — BCLK2 (ESP até o resistor)

- ESP32 pino R6 (GPIO42, fileira dir.)
- R3 33 Ω pino 1 (lado do ESP)

### BCLK2 — BCLK2 → ADC2

- ADC2 (PCM1808 CH3/CH4) BCK
- R3 33 Ω pino 2 (lado do módulo)

### WS2 — WS2 → ADC2

- ESP32 pino R8 (GPIO40, fileira dir.)
- ADC2 (PCM1808 CH3/CH4) LRC

### DIN2 — dados do ADC2 → ESP

- ESP32 pino R7 (GPIO41, fileira dir.)
- ADC2 (PCM1808 CH3/CH4) OUT

### IN1L — entrada CH1 (L do ADC1)

- ADC1 (PCM1808 CH1/CH2) LIN
- Borne IN1 L

### IN1R — entrada CH2 (R do ADC1)

- ADC1 (PCM1808 CH1/CH2) RIN
- Borne IN1 R

### IN2L — entrada CH3 (L do ADC2)

- ADC2 (PCM1808 CH3/CH4) LIN
- Borne IN2 L

### IN2R — entrada CH4 (R do ADC2)

- ADC2 (PCM1808 CH3/CH4) RIN
- Borne IN2 R

### OUTL — saída L

- DAC (PCM5102A) LROUT
- Borne OUT L

### OUTR — saída R

- DAC (PCM5102A) ROUT
- Borne OUT R

### 5V_EXT — 5 V da fonte externa, antes do diodo

- Borne 5V EXT 1
- D1 1N5819 anodo

### +5V — 5 V da placa (depois do D1)

- ESP32 pino L21 (5Vin, fileira esq.)
- DAC (PCM5102A) VIN
- ADC1 (PCM1808 CH1/CH2) +5V
- ADC2 (PCM1808 CH3/CH4) +5V
- D1 1N5819 catodo (faixa)
- C1 100uF pino +
- C2 100nF pino 1
- C3 100uF pino +
- C4 100nF pino 1
- C6 100uF pino +
- C7 100nF pino 1
- jumper de fio J1, ponta 1
- jumper de fio J1, ponta 2

### 3V3 — 3,3 V (do ESP32)

- ESP32 pino L1 (3V3, fileira esq.)
- ESP32 pino L2 (3V3, fileira esq.)
- ADC1 (PCM1808 CH1/CH2) 3.3V (fileira de cima)
- ADC1 (PCM1808 CH1/CH2) 3.3V (fileira de baixo)
- ADC2 (PCM1808 CH3/CH4) 3.3V (fileira de cima)
- ADC2 (PCM1808 CH3/CH4) 3.3V (fileira de baixo)
- C5 100nF pino 1
- C8 100nF pino 1
- Régua EXP-B 3V3

### GND (36 pontos) — plano de terra (área de cobre) + trilhas curtas

C1 100uF pino -, C2 100nF pino 2, C3 100uF pino -, C4 100nF pino 2, C5 100nF pino 2, C6 100uF pino -, C7 100nF pino 2, C8 100nF pino 2, jumper de fio J2, ponta 1, jumper de fio J2, ponta 2, jumper de fio J3, ponta 1, jumper de fio J3, ponta 2, ESP32 pino L22 (GND, fileira esq.), ESP32 pino R1 (GND, fileira dir.), ESP32 pino R21 (GND, fileira dir.), ESP32 pino R22 (GND, fileira dir.), ADC1 (PCM1808 CH1/CH2) GND (fileira de cima), ADC1 (PCM1808 CH1/CH2) GND (fileira de baixo), ADC1 (PCM1808 CH1/CH2) GND (entre LIN e RIN), ADC1 (PCM1808 CH1/CH2) MD0, ADC1 (PCM1808 CH1/CH2) MD1, ADC1 (PCM1808 CH1/CH2) PMT/FMT, ADC2 (PCM1808 CH3/CH4) GND (fileira de cima), ADC2 (PCM1808 CH3/CH4) GND (fileira de baixo), ADC2 (PCM1808 CH3/CH4) GND (entre LIN e RIN), ADC2 (PCM1808 CH3/CH4) MD0, ADC2 (PCM1808 CH3/CH4) MD1, ADC2 (PCM1808 CH3/CH4) PMT/FMT, DAC (PCM5102A) AGND (entre ROUT e LROUT), DAC (PCM5102A) GND, DAC (PCM5102A) SCK, Borne IN1 G, Borne IN2 G, Borne OUT G, Borne 5V EXT 2, Régua EXP-B GND

### EXP_IO18 — 

- ESP32 pino L11 (GPIO18, fileira esq.)
- Régua EXP-A IO18

### EXP_IO21 — 

- ESP32 pino R18 (GPIO21, fileira dir.)
- Régua EXP-B IO21

### EXP_IO3 — 

- ESP32 pino L13 (GPIO3, fileira esq.)
- Régua EXP-A IO3

### EXP_IO46 — 

- ESP32 pino L14 (GPIO46, fileira esq.)
- Régua EXP-A IO46

### EXP_IO47 — 

- ESP32 pino R17 (GPIO47, fileira dir.)
- Régua EXP-B IO47

### EXP_IO8 — 

- ESP32 pino L12 (GPIO8, fileira esq.)
- Régua EXP-A IO8

### EXP_IO9 — 

- ESP32 pino L15 (GPIO9, fileira esq.)
- Régua EXP-A IO9

## Ligações feitas DENTRO dos módulos (não estão na PCB)

- PCM1808: os dois pinos "3.3V" são o mesmo ponto no módulo; idem os três GND. A PCB liga só um dos 3.3V (ADC1: fileira de cima; ADC2: fileira de baixo). **Confirmar com o multímetro antes de soldar** (ver teste-continuidade.md).
- PCM5102A: GND e AGND do módulo são o mesmo terra; FLT, DEMP, XSMT e FMT são definidos pelas pontes de solda H1–H4 no verso (ver montagem.md). Esses pinos ficam em furos **sem cobre** na PCB.

## Jumpers de fio (lado dos componentes)

| Jumper | Rede | Comprimento | Por quê |
|---|---|---|---|
| J1 | +5V | 10.16 mm | leva o 5 V da linha de baixo até o pino 5Vin do ESP passando por cima da trilha de 3V3; por baixo do fio o terra do lado esquerdo continua ligado ao GND do ESP |
| J2 | GND | 10.16 mm | liga o GND principal (lado do ESP) ao GND da entrada de 5 V/ADC2, passando por cima das linhas de 3V3 e 5V |
| J3 | GND | 5.00 mm | liga o pino SCK do DAC ao GND (o pino fica preso entre as trilhas BCLK1/WS1 que vão do DAC ao ADC1) |
