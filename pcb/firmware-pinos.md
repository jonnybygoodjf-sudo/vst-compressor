# Mudança de GPIO no firmware (v9e → PCB v1)

Para a PCB caber numa face só, sem cruzamentos nos sinais, **7 dos 9 pinos I2S mudaram de GPIO**. Só a numeração muda: o DSP e o resto do firmware ficam iguais.

| Sinal (Kconfig) | v9e (antes) | **PCB v1** | Pino físico no ESP |
|---|---|---|---|
| MCLK1 | 5 | **12** | fileira esquerda, 18º pino |
| BCLK1 | 6 | **16** | esquerda, 9º |
| WS1 | 7 | 7 (igual) | esquerda, 7º |
| DIN1 | 16 | **10** | esquerda, 16º |
| DOUT | 15 | 15 (igual) | esquerda, 8º |
| MCLK2 | 4 | **39** | fileira direita, 9º |
| BCLK2 | 17 | **42** | direita, 6º |
| WS2 | 18 | **40** | direita, 8º |
| DIN2 | 8 | **41** | direita, 7º |

## Por que esses pinos

- O I2S0 (ADC1 + DAC) sai pela fileira esquerda e o I2S1 (ADC2) pela direita. Por isso o ESP fica no meio.
- Nenhum deles está na lista de pinos a evitar:
  - 26–37: flash/PSRAM octal;
  - 19/20: USB;
  - 0/3/45/46: strapping;
  - 43/44: UART0;
  - 48: LED RGB da placa YD.
- Os GPIO 4, 5 e 6 do firmware antigo ficam a menos de 15 mm da antena e foram evitados.
- Os GPIO 39–42 são os pinos de JTAG (MTCK/MTDO/MTDI/MTMS). No ESP32-S3 eles ficam livres porque a depuração vem pela USB-JTAG interna. **Não ative JTAG externo** no menuconfig/eFuse.
- No ESP32-S3, MCLK, BCLK, WS e dados do I2S podem ir a qualquer GPIO pela matriz. Não há restrição de hardware.

## O que fazer no firmware

Os arquivos do firmware **não estão neste repositório**, então não pude editá-los nem compilar. No repositório do firmware:

1. Em `main/Kconfig.projbuild`, troque os `default` dos 9 pinos pelos valores da coluna **PCB v1**.
2. Em `README.md`, atualize a tabela de ligações.
3. Em `CONTEXTO-PROJETO.md` (seção 2), atualize a tabela de ligações.
4. Se já existir um `sdkconfig`, ele guarda os valores antigos. Rode `idf.py menuconfig` e confira, ou apague as linhas dos pinos no `sdkconfig` para valerem os novos defaults.
5. Rode os testes de host do projeto:

```
cd firmware
make -C host_test test
python3 host_test/compare_with_page.py
```

Se você me mandar o firmware (pelo Drive ou adicionando o repositório à sessão), eu faço as trocas e rodo esses testes.
