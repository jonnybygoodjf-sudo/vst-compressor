#pragma once

#include <juce_audio_processors/juce_audio_processors.h>
#include <juce_gui_extra/juce_gui_extra.h>
#include "PluginProcessor.h"

class GainReductionMeter final : public juce::Component, private juce::Timer
{
public:
    explicit GainReductionMeter (CompressorAudioProcessor& p) : proc (p)
{
        startTimerHz (30);
}

    void paint (juce::Graphics& g) override
{
          auto bounds = getLocalBounds().toFloat();
        g.setColour (juce::Colours::black.withAlpha (0.4f));
        g.fillRoundedRectangle (bounds, 3.0f);

        // 0 a 24 dB de redução mapeados na altura do medidor, de cima para baixo.
        const float maxDb = 24.0f;
        const float frac = juce::jlimit (0.0f, 1.0f, currentGrDb / maxDb);
        auto meterBounds = bounds.reduced (2.0f);
        auto filled = meterBounds.removeFromTop (meterBounds.getHeight() * frac);

        g.setColour (juce::Colours::orange);
        g.fillRoundedRectangle (filled, 2.0f);

        g.setColour (juce::Colours::white.withAlpha (0.7f));
        g.setFont (11.0f);
        g.drawFittedText (juce::String (currentGrDb, 1) + " dB", getLocalBounds(),
                                     juce::Justification::centredBottom, 1);
}

private:
    void timerCallback() override
{
          currentGrDb = proc.getGainReductionDbForMeter();
        repaint();
}

    CompressorAudioProcessor& proc;
    float currentGrDb = 0.0f;
};

class CompressorAudioProcessorEditor final : public juce::AudioProcessorEditor
{
public:
    explicit CompressorAudioProcessorEditor (CompressorAudioProcessor&);
    ~CompressorAudioProcessorEditor() override = default;

    void paint (juce::Graphics&) override;
    void resized() override;

private:
    struct KnobWithLabel
{
        juce::Slider slider { juce::Slider::RotaryHorizontalVerticalDrag, juce::Slider::TextBoxBelow };
        juce::Label label;
        std::unique_ptr<juce::AudioProcessorValueTreeState::SliderAttachment> attachment;
};

    void setupKnob (KnobWithLabel& knob, const juce::String& labelText,
                    juce::AudioProcessorValueTreeState& apvts, const juce::String& paramID);

    CompressorAudioProcessor& audioProcessor;

    KnobWithLabel thresholdKnob, ratioKnob, kneeKnob, attackKnob, releaseKnob, makeupKnob;

    juce::ToggleButton sidechainButton { "Sidechain Listen" };
    std::unique_ptr<juce::AudioProcessorValueTreeState::ButtonAttachment> sidechainAttachment;

    GainReductionMeter meter;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (CompressorAudioProcessorEditor)
};
