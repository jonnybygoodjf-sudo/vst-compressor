#pragma once

#include <juce_audio_processors/juce_audio_processors.h>
#include "CompressorDSP.h"

// IDs dos parâmetros — usados tanto no Processor quanto no Editor.
namespace ParamIDs
{
    static constexpr auto threshold      = "threshold";
    static constexpr auto ratio          = "ratio";
    static constexpr auto knee           = "knee";
    static constexpr auto attack         = "attack";
    static constexpr auto release        = "release";
    static constexpr auto sidechainOn    = "sidechainOn";
    static constexpr auto makeupGain     = "makeupGain";
}

class CompressorAudioProcessor final : public juce::AudioProcessor
{
public:
    CompressorAudioProcessor();
    ~CompressorAudioProcessor() override = default;

    void prepareToPlay (double sampleRate, int samplesPerBlock) override;
    void releaseResources() override {}

    bool isBusesLayoutSupported (const BusesLayout& layouts) const override;
    void processBlock (juce::AudioBuffer<float>&, juce::MidiBuffer&) override;

    juce::AudioProcessorEditor* createEditor() override;
    bool hasEditor() const override { return true; }

    const juce::String getName() const override { return JucePlugin_Name; }
    bool acceptsMidi() const override { return false; }
    bool producesMidi() const override { return false; }
    double getTailLengthSeconds() const override { return 0.0; }

    int getNumPrograms() override { return 1; }
    int getCurrentProgram() override { return 0; }
    void setCurrentProgram (int) override {}
    const juce::String getProgramName (int) override { return {}; }
    void changeProgramName (int, const juce::String&) override {}

    void getStateInformation (juce::MemoryBlock& destData) override;
    void setStateInformation (const void* data, int sizeInBytes) override;

    // Exposto para o Editor (medidor de redução de ganho).
    float getGainReductionDbForMeter() const { return meterGainReductionDb.load(); }

    juce::AudioProcessorValueTreeState apvts;

private:
    juce::AudioProcessorValueTreeState::ParameterLayout createParameterLayout();

    // Um CompressorDSP por canal de saída, mas todos recebem o MESMO nível de
    // detecção (stereo-linked) — assim a imagem estéreo não desloca quando
    // um lado comprime mais que o outro.
    std::vector<CompressorDSP> compressors;

    std::atomic<float> meterGainReductionDb { 0.0f };

    // Buffer auxiliar mono com o sinal de detecção (sidechain externo OU
    // o próprio sinal principal, dependendo do parâmetro sidechainOn e de
    // haver algo conectado no bus de sidechain).
    juce::AudioBuffer<float> detectorBuffer;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (CompressorAudioProcessor)
};
