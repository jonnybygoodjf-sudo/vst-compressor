#include "PluginProcessor.h"
#include "PluginEditor.h"

namespace
{
    // Mapeia 1..100 na UI para 1:1..∞:1 no DSP (ver kInfiniteRatioThreshold).
    juce::String ratioTextFromValue (float value, int)
{
          if (value >= CompressorDSP::kInfiniteRatioThreshold)
                        return juce::String (char (0x221E)); // "∞"
        return juce::String (value, 1) + ":1";
}
}

CompressorAudioProcessor::CompressorAudioProcessor()
    : AudioProcessor (BusesProperties()
          .withInput  ("Input",     juce::AudioChannelSet::stereo(), true)
          .withOutput ("Output",    juce::AudioChannelSet::stereo(), true)
          .withInput  ("Sidechain", juce::AudioChannelSet::stereo(), true)),
      apvts (*this, nullptr, "PARAMETERS", createParameterLayout())
{
}

juce::AudioProcessorValueTreeState::ParameterLayout CompressorAudioProcessor::createParameterLayout()
{
      using Range = juce::NormalisableRange<float>;
    std::vector<std::unique_ptr<juce::RangedAudioParameter>> params;

    params.push_back (std::make_unique<juce::AudioParameterFloat> (
              ParamIDs::threshold, "Threshold",
              Range (-60.0f, 0.0f, 0.01f), -18.0f,
              juce::AudioParameterFloatAttributes().withLabel ("dB")));

    // Skew para dar mais resolução perto de ratios baixos, onde a diferença
    // se percebe mais; topo da faixa = comportamento de limiter (infinito).
    Range ratioRange (CompressorDSP::kMinRatio, CompressorDSP::kMaxRatio, 0.01f);
    ratioRange.setSkewForCentre (4.0f);
    params.push_back (std::make_unique<juce::AudioParameterFloat> (
              ParamIDs::ratio, "Ratio", ratioRange, 4.0f,
              juce::AudioParameterFloatAttributes().withStringFromValueFunction (ratioTextFromValue)));

    params.push_back (std::make_unique<juce::AudioParameterFloat> (
              ParamIDs::knee, "Knee",
              Range (0.0f, 24.0f, 0.01f), 6.0f,
              juce::AudioParameterFloatAttributes().withLabel ("dB")));

    // Attack: 1 ms até 2000 ms (2 s), como pedido — "attack bem lento".
    Range attackRange (CompressorDSP::kMinTimeMs, CompressorDSP::kMaxTimeMs, 0.01f);
    attackRange.setSkewForCentre (100.0f);
    params.push_back (std::make_unique<juce::AudioParameterFloat> (
              ParamIDs::attack, "Attack", attackRange, 10.0f,
              juce::AudioParameterFloatAttributes().withLabel ("ms")));

    // Release: 1 ms até 2000 ms.
    Range releaseRange (CompressorDSP::kMinTimeMs, CompressorDSP::kMaxTimeMs, 0.01f);
    releaseRange.setSkewForCentre (150.0f);
    params.push_back (std::make_unique<juce::AudioParameterFloat> (
              ParamIDs::release, "Release", releaseRange, 150.0f,
              juce::AudioParameterFloatAttributes().withLabel ("ms")));

    params.push_back (std::make_unique<juce::AudioParameterBool> (
              ParamIDs::sidechainOn, "Sidechain Listen", false));

    params.push_back (std::make_unique<juce::AudioParameterFloat> (
              ParamIDs::makeupGain, "Makeup Gain",
              Range (-24.0f, 24.0f, 0.01f), 0.0f,
              juce::AudioParameterFloatAttributes().withLabel ("dB")));

    return { params.begin(), params.end() };
}

void CompressorAudioProcessor::prepareToPlay (double sampleRate, int)
{
      const int numOutChannels = getTotalNumOutputChannels();
    compressors.assign (static_cast<size_t> (juce::jmax (1, numOutChannels)), CompressorDSP());
    for (auto& c : compressors)
              c.prepare (sampleRate);

    detectorBuffer.setSize (1, 8192);
}

bool CompressorAudioProcessor::isBusesLayoutSupported (const BusesLayout& layouts) const
{
      // Main in/out precisam bater (mono ou estéreo).
    if (layouts.getMainOutputChannelSet() != layouts.getMainInputChannelSet())
              return false;

    if (layouts.getMainOutputChannelSet() != juce::AudioChannelSet::mono()
              && layouts.getMainOutputChannelSet() != juce::AudioChannelSet::stereo())
              return false;

    // Sidechain é opcional: pode vir desligado (disabled), mono ou estéreo.
    const auto sc = layouts.getChannelSet (true, 1);
    if (! sc.isDisabled() && sc != juce::AudioChannelSet::mono() && sc != juce::AudioChannelSet::stereo())
              return false;

    return true;
}

void CompressorAudioProcessor::processBlock (juce::AudioBuffer<float>& buffer, juce::MidiBuffer&)
{
      juce::ScopedNoDenormals noDenormals;

    const int numSamples = buffer.getNumSamples();
    const int numMainChannels = getMainBusNumOutputChannels();

    const float thresholdDb = apvts.getRawParameterValue (ParamIDs::threshold)->load();
    const float ratio       = apvts.getRawParameterValue (ParamIDs::ratio)->load();
    const float kneeDb      = apvts.getRawParameterValue (ParamIDs::knee)->load();
    const float attackMs    = apvts.getRawParameterValue (ParamIDs::attack)->load();
    const float releaseMs   = apvts.getRawParameterValue (ParamIDs::release)->load();
    const bool  scOn        = apvts.getRawParameterValue (ParamIDs::sidechainOn)->load() > 0.5f;
    const float makeupDb    = apvts.getRawParameterValue (ParamIDs::makeupGain)->load();
    const float makeupGain  = juce::Decibels::decibelsToGain (makeupDb);

    for (auto& c : compressors)
{
        c.setThresholdDb (thresholdDb);
        c.setRatio (ratio);
        c.setKneeDb (kneeDb);
        c.setAttackMs (attackMs);
        c.setReleaseMs (releaseMs);
}

    // --- Monta o sinal de detecção (mono, stereo-linked) -----------------
    auto sidechainBus = getBus (true, 1);
    const bool sidechainConnected = sidechainBus != nullptr
                                     && sidechainBus->isEnabled()
                                     && sidechainBus->getNumberOfChannels() > 0;

    detectorBuffer.setSize (1, numSamples, false, false, true);
    detectorBuffer.clear();

    if (scOn && sidechainConnected)
{
        auto scBlock = sidechainBus->getBusBuffer (buffer);
        const int scChannels = scBlock.getNumChannels();
        for (int ch = 0; ch < scChannels; ++ch)
                      detectorBuffer.addFrom (0, 0, scBlock, ch, 0, numSamples, 1.0f / (float) scChannels);
}
    else
{
        // Sem sidechain conectado (ou desligado): detecta no próprio sinal principal.
        for (int ch = 0; ch < numMainChannels; ++ch)
                      detectorBuffer.addFrom (0, 0, buffer, ch, 0, numSamples, 1.0f / (float) numMainChannels);
}

    // --- Aplica a mesma redução de ganho (stereo-linked) a todos os canais principais ---
    auto* detector = detectorBuffer.getReadPointer (0);
    float lastGrDb = 0.0f;

    for (int i = 0; i < numSamples; ++i)
{
        // Um único envelope "mestre" (canal 0) decide a redução deste sample;
        // os demais canais reusam o mesmo valor para manter o link estéreo.
        const float gain = compressors[0].processSample (detector[i]);
        lastGrDb = compressors[0].getGainReductionDb();

        for (int ch = 0; ch < numMainChannels; ++ch)
{
            auto* data = buffer.getWritePointer (ch);
            data[i] = data[i] * gain * makeupGain;
}
}

    meterGainReductionDb.store (lastGrDb);

    // Zera qualquer bus de sidechain remanescente no buffer de saída (JUCE
    // já cuida disso na maioria dos hosts, isto é apenas defensivo).
    for (int ch = numMainChannels; ch < buffer.getNumChannels(); ++ch)
              buffer.clear (ch, 0, numSamples);
}

juce::AudioProcessorEditor* CompressorAudioProcessor::createEditor()
{
      return new CompressorAudioProcessorEditor (*this);
}

void CompressorAudioProcessor::getStateInformation (juce::MemoryBlock& destData)
{
      if (auto state = apvts.copyState(); state.isValid())
{
        juce::MemoryOutputStream stream (destData, true);
        state.writeToStream (stream);
}
}

void CompressorAudioProcessor::setStateInformation (const void* data, int sizeInBytes)
{
      auto tree = juce::ValueTree::readFromData (data, (size_t) sizeInBytes);
    if (tree.isValid())
              apvts.replaceState (tree);
}

// Ponto de entrada exigido pelo JUCE para instanciar o plugin.
juce::AudioProcessor* JUCE_CALLTYPE createPluginFilter()
{
      return new CompressorAudioProcessor();
}
