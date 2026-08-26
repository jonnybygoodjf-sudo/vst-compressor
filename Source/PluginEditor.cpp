#include "PluginEditor.h"

CompressorAudioProcessorEditor::CompressorAudioProcessorEditor (CompressorAudioProcessor& p)
    : AudioProcessorEditor (&p), audioProcessor (p), meter (p)
{
    setupKnob (thresholdKnob, "Threshold", p.apvts, ParamIDs::threshold);
    setupKnob (ratioKnob,     "Ratio",     p.apvts, ParamIDs::ratio);
    setupKnob (kneeKnob,      "Knee",      p.apvts, ParamIDs::knee);
    setupKnob (attackKnob,    "Attack",    p.apvts, ParamIDs::attack);
    setupKnob (releaseKnob,   "Release",   p.apvts, ParamIDs::release);
    setupKnob (makeupKnob,    "Makeup",    p.apvts, ParamIDs::makeupGain);

    sidechainButton.setColour (juce::ToggleButton::textColourId, juce::Colours::white);
    addAndMakeVisible (sidechainButton);
    sidechainAttachment = std::make_unique<juce::AudioProcessorValueTreeState::ButtonAttachment> (
              p.apvts, ParamIDs::sidechainOn, sidechainButton);

    addAndMakeVisible (meter);

    setResizable (true, true);
    setResizeLimits (560, 320, 1000, 560);
    setSize (620, 340);
}

void CompressorAudioProcessorEditor::setupKnob (KnobWithLabel& knob, const juce::String& labelText,
                                                 juce::AudioProcessorValueTreeState& apvts,
                                                 const juce::String& paramID)
{
      knob.slider.setSliderStyle (juce::Slider::RotaryHorizontalVerticalDrag);
    knob.slider.setTextBoxStyle (juce::Slider::TextBoxBelow, false, 70, 18);
    addAndMakeVisible (knob.slider);

    knob.label.setText (labelText, juce::dontSendNotification);
    knob.label.setJustificationType (juce::Justification::centred);
    knob.label.setColour (juce::Label::textColourId, juce::Colours::white);
    addAndMakeVisible (knob.label);

    knob.attachment = std::make_unique<juce::AudioProcessorValueTreeState::SliderAttachment> (
              apvts, paramID, knob.slider);
}

void CompressorAudioProcessorEditor::paint (juce::Graphics& g)
{
      g.fillAll (juce::Colour (0xff2b2d31));

    g.setColour (juce::Colours::white);
    g.setFont (juce::Font (18.0f, juce::Font::bold));
    g.drawFittedText ("Compressor VST3 — sidechain", getLocalBounds().removeFromTop (30),
                             juce::Justification::centred, 1);
}

void CompressorAudioProcessorEditor::resized()
{
      auto area = getLocalBounds().reduced (16);
    area.removeFromTop (26); // espaço do título

    auto meterArea = area.removeFromRight (48);
    meter.setBounds (meterArea.reduced (4));

    area.removeFromRight (12);

    auto bottomRow = area.removeFromBottom (36);
    sidechainButton.setBounds (bottomRow.removeFromLeft (200));

    KnobWithLabel* knobs[] = { &thresholdKnob, &ratioKnob, &kneeKnob, &attackKnob, &releaseKnob, &makeupKnob };
    const int numKnobs = 6;
    const int knobWidth = area.getWidth() / numKnobs;

    for (int i = 0; i < numKnobs; ++i)
{
        auto slot = area.removeFromLeft (knobWidth);
        auto labelSlot = slot.removeFromBottom (18);
        knobs[i]->label.setBounds (labelSlot);
        knobs[i]->slider.setBounds (slot.reduced (6));
}
}
