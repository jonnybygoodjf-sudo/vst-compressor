// CompressorDSP.h
//
// Núcleo de processamento do compressor, independente de JUCE/VST3.
// Implementa o modelo clássico de "feed-forward gain computer + smoothing
// no domínio de dB" descrito por Giannoulis, Massberg & Reiss em
// "Digital Dynamic Range Compressor Design — A Tutorial and Analysis" (2012).
//
// Por que esse modelo:
//  - Ratio = 1:1 vira identidade exata (y_G = x_G), sem "quase-transparente".
//  - Ratio -> infinito vira um limiter de brickwall real (y_G satura em T).
//  - Knee em dB dá uma transição suave e continuamente diferenciável.
//  - Suavizar a REDUÇÃO DE GANHO (não o nível bruto) com attack/release
//    separados é o jeito padrão de evitar "zipper noise" e de deixar o
//    ratio infinito realmente se comportar como limiter estável.
//
// Este arquivo não depende de JUCE de propósito: dá para compilar e testar
// isoladamente com g++ puro (ver tests/test_compressor.cpp).

#pragma once

#include <algorithm>
#include <cmath>

class CompressorDSP
{
public:
    CompressorDSP() = default;

    void prepare (double sampleRate)
{
          sr = sampleRate > 0.0 ? sampleRate : 44100.0;
        updateAttackCoeff();
        updateReleaseCoeff();
        reset();
}

    void reset()
{
          envelopeDb = 0.0f;   // redução de ganho suavizada, em dB (>= 0)
        currentGainReductionDb = 0.0f;
}

    // ---- Parâmetros ---------------------------------------------------

    // Threshold em dBFS. Tipicamente -60 a 0 dB.
    void setThresholdDb (float dB) { thresholdDb = dB; }

    // Ratio: 1.0 = sem compressão (identidade). Use um valor bem alto
    // (ou o helper setRatioInfinite) para comportamento de limiter.
    // Internamente qualquer ratio >= kInfiniteRatioThreshold vira 1/R = 0.
    void setRatio (float ratio)
{
          ratio = std::max (1.0f, ratio);
        invRatio = (ratio >= kInfiniteRatioThreshold) ? 0.0f : (1.0f / ratio);
}

    void setRatioInfinite() { invRatio = 0.0f; }

    // Knee width em dB (0 = hard knee, tipicamente até ~24 dB de soft knee).
    void setKneeDb (float widthDb) { kneeDb = std::max (0.0f, widthDb); }

    // Attack e release em milissegundos.
    // Faixa suportada pela UI: attack 1..2000 ms, release 1..2000 ms.
    void setAttackMs (float ms)
{
          attackMs = std::clamp (ms, kMinTimeMs, kMaxTimeMs);
        updateAttackCoeff();
}

    void setReleaseMs (float ms)
{
          releaseMs = std::clamp (ms, kMinTimeMs, kMaxTimeMs);
        updateReleaseCoeff();
}

    // Faixas usadas pelos parâmetros da UI/host — mantidas aqui como fonte
    // única da verdade para não haver divergência entre DSP e GUI.
    static constexpr float kMinTimeMs = 1.0f;
    static constexpr float kMaxTimeMs = 2000.0f; // cobre "1ms até 2s" no attack
    static constexpr float kMinRatio  = 1.0f;
    static constexpr float kMaxRatio  = 100.0f;  // UI mostra "∞" no topo da faixa
    static constexpr float kInfiniteRatioThreshold = 99.5f;

    // ---- Processamento --------------------------------------------------

    // detectorSample: amostra usada só para DETECÇÃO de nível (pode vir do
    //                 sidechain externo OU do próprio sinal principal).
    // Retorna o ganho linear (0..1] a ser multiplicado no sinal principal.
    float processSample (float detectorSample)
{
          const float levelDb = linearToDb (std::abs (detectorSample));
        const float targetReductionDb = computeStaticReductionDb (levelDb);

        // Ataque = quando a redução de ganho precisa AUMENTAR (mais compressão).
        // Release = quando a redução de ganho precisa DIMINUIR.
        const float coeff = (targetReductionDb > envelopeDb) ? attackCoeff : releaseCoeff;
        envelopeDb = coeff * envelopeDb + (1.0f - coeff) * targetReductionDb;

        currentGainReductionDb = envelopeDb;
        return dbToLinear (-envelopeDb);
}

    // Redução de ganho atual, em dB positivos (para medidor na GUI).
    float getGainReductionDb() const { return currentGainReductionDb; }

private:
    // Curva estática ganho-computer com soft knee (Giannoulis et al., eq. 4).
    // x_G = nível de entrada em dB; retorna (x_G - y_G), ou seja, quanto
    // reduzir, sempre >= 0.
    float computeStaticReductionDb (float levelDb) const
{
          const float xG = levelDb;
        const float overshoot = xG - thresholdDb;
        const float halfKnee = kneeDb * 0.5f;

        float yG;
        if (2.0f * overshoot < -kneeDb)
{
            // Abaixo do joelho: sem compressão.
            yG = xG;
}
        else if (2.0f * std::abs (overshoot) <= kneeDb)
{
            // Dentro da região do joelho: interpolação quadrática suave.
            const float t = overshoot + halfKnee;
            yG = xG + (invRatio - 1.0f) * (t * t) / (2.0f * kneeDb);
}
        else
{
            // Acima do joelho: reta de compressão (ratio puro).
            yG = thresholdDb + overshoot * invRatio;
}

        return std::max (0.0f, xG - yG);
}

    void updateAttackCoeff()
{
          attackCoeff = timeToCoeff (attackMs);
}

    void updateReleaseCoeff()
{
          releaseCoeff = timeToCoeff (releaseMs);
}

    // Coeficiente de suavização one-pole clássico: tempo até ~63% do alvo.
    float timeToCoeff (float ms) const
{
          const double timeSec = std::max (0.0001, static_cast<double> (ms) / 1000.0);
        return static_cast<float> (std::exp (-1.0 / (sr * timeSec)));
}

    static float linearToDb (float x)
{
          constexpr float floorDb = -120.0f;
        return x <= 1.0e-6f ? floorDb : 20.0f * std::log10 (x);
}

    static float dbToLinear (float dB)
{
          return std::pow (10.0f, dB / 20.0f);
}

    double sr = 44100.0;

    float thresholdDb = -18.0f;
    float invRatio    = 0.25f; // ratio 4:1 por padrão
    float kneeDb      = 6.0f;
    float attackMs    = 10.0f;
    float releaseMs   = 150.0f;

    float attackCoeff  = 0.0f;
    float releaseCoeff = 0.0f;

    float envelopeDb = 0.0f;
    float currentGainReductionDb = 0.0f;
};
