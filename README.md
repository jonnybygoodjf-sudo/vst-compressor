# Sidechain Compressor (VST3)

Compressor com sidechain externo, attack/release amplos, knee e ratio até
infinito (modo limiter). DSP validado por testes automatizados antes de
virar plugin (veja `tests/`).

## Parâmetros

| Parâmetro | Faixa | Observação |
|---|---|---|
| Threshold | -60 a 0 dB | |
| Ratio | 1:1 até ∞:1 | topo da faixa = brickwall limiter |
| Knee | 0 a 24 dB | 0 = joelho duro |
| Attack | 1 ms a 2000 ms (2 s) | pode ser bem lento, como pedido |
| Release | 1 ms a 2000 ms | |
| Makeup Gain | -24 a +24 dB | compensa o ganho perdido na compressão |
| Sidechain Listen | on/off | ver seção "Sidechain" abaixo |

## Como funciona o sidechain

O plugin tem **dois buses de entrada**:
1. **Input** — o áudio da própria track, que É comprimido.
2. **Sidechain** — um áudio externo (de outra track), usado **só para
   detectar o nível**. Ele nunca é ouvido, apenas decide quando e quanto
   comprimir o Input.

Com **Sidechain Listen** ligado e algo roteado para o bus de sidechain, a
redução de ganho passa a reagir ao sinal externo em vez do próprio sinal da
track — exatamente o comportamento clássico de "duck" (ex.: locução
abaixando a trilha, kick abaixando o baixo etc.). Sem nada conectado ali (ou
com o botão desligado), o plugin cai automaticamente para detecção no
próprio sinal, funcionando como compressor normal.

### Roteando o sidechain no host (Windows)
O nome do menu muda por DAW, mas a ideia é sempre a mesma:
- **Reaper**: no FX, clique na engrenagem do plugin → *Pin connector* / roteie o
  canal de sidechain a partir do envio da track externa.
- **Cubase/Nuendo**: crie o plugin em uma track de áudio, ative
  *Sidechain Input* nas configurações da inserção e escolha a track de
  origem.
- **Studio One**: arraste a track externa para o "cabide" de sidechain que
  aparece ao lado do plugin.

## Opção mais fácil: compilar na nuvem (sem instalar nada)

Se você não tem Visual Studio instalado, use o `.github/workflows/build-vst3.yml`
já incluído neste projeto:

1. Crie um repositório novo (pode ser privado) em https://github.com/new
2. Suba esta pasta inteira pra ele (dá pra arrastar os arquivos direto na
   interface web do GitHub, em "uploading an existing file" — não precisa
   saber usar git pela linha de comando).
3. Abra a aba **Actions** do repositório → o build começa sozinho
   (leva uns 10-15 minutos, porque baixa o JUCE inteiro).
4. Quando o run ficar verde, abra ele e baixe o artifact
   **SidechainCompressor-VST3** no fim da página — vem um `.zip` com o
   `Sidechain Compressor.vst3` pronto.
5. Copie esse `.vst3` para `C:\Program Files\Common Files\VST3` e abra o
   Reaper — ele aparece na lista de FX depois de um "Re-scan" (Options →
   Preferences → Plug-ins → VST → Re-scan).

Essa é a forma mais direta de conseguir o arquivo final sem precisar
instalar Visual Studio na sua máquina.

## Build no Windows (Visual Studio)

Pré-requisitos:
- Visual Studio 2022 (workload "Desktop development with C++")
- CMake ≥ 3.22 (já vem com o VS 2022, ou instale separado)
- Git

Passos:

```bat
cd VstCompressor
cmake -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release
```

A primeira configuração baixa o JUCE automaticamente via `FetchContent`
(precisa de internet nesse passo; builds seguintes usam o cache local em
`build/_deps`).

O `.vst3` final sai em:
```
build\SidechainCompressor_artefacts\Release\VST3\Sidechain Compressor.vst3
```

Com `COPY_PLUGIN_AFTER_BUILD` ativado no `CMakeLists.txt`, o JUCE já copia
esse `.vst3` para a pasta padrão do sistema
(`C:\Program Files\Common Files\VST3`) automaticamente a cada build.

### Alternativa: abrir direto no Visual Studio
Depois do `cmake -B build ...` acima, abra
`build\SidechainCompressor.sln` no Visual Studio normalmente — dá para
debugar o plugin (com o host de testes do JUCE) igual a qualquer projeto
C++.

## Testando o DSP isoladamente (opcional)

Antes de mexer no `CompressorDSP.h`, dá pra validar a lógica sem precisar
recompilar o plugin inteiro:

```bash
g++ -std=c++17 -O2 tests/test_compressor.cpp -o test_compressor
./test_compressor
```

Isso testa: ratio 1:1 = identidade, ratio infinito = brickwall no threshold,
e attack lento respondendo muito mais devagar que attack rápido.

## Estrutura de arquivos

```
VstCompressor/
├── CMakeLists.txt
├── Source/
│   ├── CompressorDSP.h       <- lógica de compressão pura (sem JUCE)
│   ├── PluginProcessor.h/.cpp<- parâmetros, buses, processBlock
│   └── PluginEditor.h/.cpp   <- interface gráfica (knobs + medidor)
└── tests/
    └── test_compressor.cpp   <- validação standalone do DSP
```
