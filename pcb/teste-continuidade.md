# Teste de continuidade (multímetro no "bip")

Faça **na ordem**, primeiro com a placa nua (sem nada soldado), depois de novo com os componentes soldados mas **sem** os módulos encaixados. Marque cada item. Pontas: ponha a ponta no **centro do pad**.

## 0. Antes de tudo: os módulos (com o multímetro, fora da placa)

- [ ] PCM1808 (cada um): pino "3.3V" de cima ↔ pino "3.3V" de baixo = **bip** (a PCB usa só um deles).
- [ ] PCM1808 (cada um): os três GND entre si = **bip**.
- [ ] PCM1808 (cada um): "+5V" ↔ "3.3V" = **sem bip** (o módulo não tem regulador; são alimentações separadas).
- [ ] PCM5102A: GND ↔ AGND (pino do meio entre ROUT e LROUT) = **bip**.
- [ ] PCM5102A: depois de fechar as pontes (montagem.md), FLT, DEMP e FMT ↔ GND = **bip**; XSMT ↔ GND = **sem bip**.

## 1. Curto de alimentação (o mais importante)

- [ ] GND ↔ +5V = **sem bip** (medir entre o pad GND e o pad "+5V" do borne 5V EXT e também entre o pino GND e o 5VIN do ESP).
- [ ] GND ↔ 3V3 = **sem bip** (pino 3V3 do ESP ↔ pino GND do ESP).
- [ ] +5V ↔ 3V3 = **sem bip**.
- [ ] 5V EXT (antes do diodo) ↔ +5V: o multímetro em modo diodo mostra ~0,2–0,4 V num sentido e nada no outro.

## 2. Cada ligação da netlist (deve dar **bip**)

- [ ] **MCLK1_ESP**: ESP32 pino L18 (GPIO12, fileira esq.) ↔ R2 33 Ω pino 1 (lado do ESP)
- [ ] **MCLK1**: ADC1 (PCM1808 CH1/CH2) SCK ↔ R2 33 Ω pino 2 (lado do módulo)
- [ ] **BCLK1_ESP**: ESP32 pino L9 (GPIO16, fileira esq.) ↔ R1 33 Ω pino 1 (lado do ESP)
- [ ] **BCLK1**: DAC (PCM5102A) BCK ↔ ADC1 (PCM1808 CH1/CH2) BCK
- [ ] **BCLK1**: DAC (PCM5102A) BCK ↔ R1 33 Ω pino 2 (lado do módulo)
- [ ] **WS1**: ESP32 pino L7 (GPIO7, fileira esq.) ↔ DAC (PCM5102A) LCK
- [ ] **WS1**: ESP32 pino L7 (GPIO7, fileira esq.) ↔ ADC1 (PCM1808 CH1/CH2) LRC
- [ ] **DIN1**: ESP32 pino L16 (GPIO10, fileira esq.) ↔ ADC1 (PCM1808 CH1/CH2) OUT
- [ ] **DOUT**: ESP32 pino L8 (GPIO15, fileira esq.) ↔ DAC (PCM5102A) DIN
- [ ] **MCLK2_ESP**: ESP32 pino R9 (GPIO39, fileira dir.) ↔ R4 33 Ω pino 1 (lado do ESP)
- [ ] **MCLK2**: ADC2 (PCM1808 CH3/CH4) SCK ↔ R4 33 Ω pino 2 (lado do módulo)
- [ ] **BCLK2_ESP**: ESP32 pino R6 (GPIO42, fileira dir.) ↔ R3 33 Ω pino 1 (lado do ESP)
- [ ] **BCLK2**: ADC2 (PCM1808 CH3/CH4) BCK ↔ R3 33 Ω pino 2 (lado do módulo)
- [ ] **WS2**: ESP32 pino R8 (GPIO40, fileira dir.) ↔ ADC2 (PCM1808 CH3/CH4) LRC
- [ ] **DIN2**: ESP32 pino R7 (GPIO41, fileira dir.) ↔ ADC2 (PCM1808 CH3/CH4) OUT
- [ ] **IN1L**: ADC1 (PCM1808 CH1/CH2) LIN ↔ Borne IN1 L
- [ ] **IN1R**: ADC1 (PCM1808 CH1/CH2) RIN ↔ Borne IN1 R
- [ ] **IN2L**: ADC2 (PCM1808 CH3/CH4) LIN ↔ Borne IN2 L
- [ ] **IN2R**: ADC2 (PCM1808 CH3/CH4) RIN ↔ Borne IN2 R
- [ ] **OUTL**: DAC (PCM5102A) LROUT ↔ Borne OUT L
- [ ] **OUTR**: DAC (PCM5102A) ROUT ↔ Borne OUT R
- [ ] **5V_EXT**: Borne 5V EXT 1 ↔ D1 1N5819 anodo
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ DAC (PCM5102A) VIN
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ ADC1 (PCM1808 CH1/CH2) +5V
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ ADC2 (PCM1808 CH3/CH4) +5V
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ D1 1N5819 catodo (faixa)
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ C1 100uF pino +
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ C2 100nF pino 1
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ C3 100uF pino +
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ C4 100nF pino 1
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ C6 100uF pino +
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ C7 100nF pino 1
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ jumper de fio J1, ponta 1
- [ ] **+5V**: ESP32 pino L21 (5Vin, fileira esq.) ↔ jumper de fio J1, ponta 2
- [ ] **3V3**: ESP32 pino L1 (3V3, fileira esq.) ↔ ESP32 pino L2 (3V3, fileira esq.)
- [ ] **3V3**: ESP32 pino L1 (3V3, fileira esq.) ↔ ADC1 (PCM1808 CH1/CH2) 3.3V (fileira de cima)
- [ ] **3V3**: ESP32 pino L1 (3V3, fileira esq.) ↔ ADC1 (PCM1808 CH1/CH2) 3.3V (fileira de baixo)
- [ ] **3V3**: ESP32 pino L1 (3V3, fileira esq.) ↔ ADC2 (PCM1808 CH3/CH4) 3.3V (fileira de cima)
- [ ] **3V3**: ESP32 pino L1 (3V3, fileira esq.) ↔ ADC2 (PCM1808 CH3/CH4) 3.3V (fileira de baixo)
- [ ] **3V3**: ESP32 pino L1 (3V3, fileira esq.) ↔ C5 100nF pino 1
- [ ] **3V3**: ESP32 pino L1 (3V3, fileira esq.) ↔ C8 100nF pino 1
- [ ] **3V3**: ESP32 pino L1 (3V3, fileira esq.) ↔ Régua EXP-B 3V3
- [ ] **EXP_IO18**: ESP32 pino L11 (GPIO18, fileira esq.) ↔ Régua EXP-A IO18
- [ ] **EXP_IO21**: ESP32 pino R18 (GPIO21, fileira dir.) ↔ Régua EXP-B IO21
- [ ] **EXP_IO3**: ESP32 pino L13 (GPIO3, fileira esq.) ↔ Régua EXP-A IO3
- [ ] **EXP_IO46**: ESP32 pino L14 (GPIO46, fileira esq.) ↔ Régua EXP-A IO46
- [ ] **EXP_IO47**: ESP32 pino R17 (GPIO47, fileira dir.) ↔ Régua EXP-B IO47
- [ ] **EXP_IO8**: ESP32 pino L12 (GPIO8, fileira esq.) ↔ Régua EXP-A IO8
- [ ] **EXP_IO9**: ESP32 pino L15 (GPIO9, fileira esq.) ↔ Régua EXP-A IO9

## 3. Terra (deve dar **bip** de cada ponto até o GND do ESP, pino L22)

- [ ] C1 100uF pino -
- [ ] C2 100nF pino 2
- [ ] C3 100uF pino -
- [ ] C4 100nF pino 2
- [ ] C5 100nF pino 2
- [ ] C6 100uF pino -
- [ ] C7 100nF pino 2
- [ ] C8 100nF pino 2
- [ ] jumper de fio J2, ponta 1
- [ ] jumper de fio J2, ponta 2
- [ ] jumper de fio J3, ponta 1
- [ ] jumper de fio J3, ponta 2
- [ ] ESP32 pino R1 (GND, fileira dir.)
- [ ] ESP32 pino R21 (GND, fileira dir.)
- [ ] ESP32 pino R22 (GND, fileira dir.)
- [ ] ADC1 (PCM1808 CH1/CH2) GND (fileira de cima)
- [ ] ADC1 (PCM1808 CH1/CH2) GND (fileira de baixo)
- [ ] ADC1 (PCM1808 CH1/CH2) GND (entre LIN e RIN)
- [ ] ADC1 (PCM1808 CH1/CH2) MD0
- [ ] ADC1 (PCM1808 CH1/CH2) MD1
- [ ] ADC1 (PCM1808 CH1/CH2) PMT/FMT
- [ ] ADC2 (PCM1808 CH3/CH4) GND (fileira de cima)
- [ ] ADC2 (PCM1808 CH3/CH4) GND (fileira de baixo)
- [ ] ADC2 (PCM1808 CH3/CH4) GND (entre LIN e RIN)
- [ ] ADC2 (PCM1808 CH3/CH4) MD0
- [ ] ADC2 (PCM1808 CH3/CH4) MD1
- [ ] ADC2 (PCM1808 CH3/CH4) PMT/FMT
- [ ] DAC (PCM5102A) AGND (entre ROUT e LROUT)
- [ ] DAC (PCM5102A) GND
- [ ] DAC (PCM5102A) SCK
- [ ] Borne IN1 G
- [ ] Borne IN2 G
- [ ] Borne OUT G
- [ ] Borne 5V EXT 2
- [ ] Régua EXP-B GND

## 4. Configuração fixa dos módulos

- [ ] ADC1 e ADC2: MD0, MD1 e PMT/FMT ↔ GND = **bip** (escravo, I2S 24 bits).
- [ ] DAC: SCK ↔ GND = **bip** (pelo jumper J3; usa a PLL interna).
- [ ] Nenhum par de pinos vizinhos do ESP em curto (passe a ponta pelos 44 pinos: só devem bipar entre si os GND (L22, R1, R21, R22) e os 3V3 (L1, L2) — e esses dois grupos **não** podem bipar um com o outro).

## 5. Primeira energização (sem os módulos)

1. Ligue a fonte de 5 V no borne 5V EXT com amperímetro em série (ou fonte com limite de 100 mA).
2. Meça +5V no pad "+5V" de cada soquete de módulo e no 5VIN do ESP (~4,6–4,8 V, por causa do diodo).
3. Desligue, encaixe o ESP32, ligue: meça 3,3 V nos pinos "3.3V" dos soquetes dos ADC.
4. Só então encaixe os módulos de áudio (sempre com a placa desligada: MD0/MD1 são lidos na partida).
