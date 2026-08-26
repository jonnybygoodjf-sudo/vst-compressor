cmake_minimum_required(VERSION 3.22)

project(SidechainCompressor VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- JUCE ---------------------------------------------------------------
# Baixa o JUCE automaticamente na primeira configuração (precisa de internet
# só nesse passo). Se preferir usar um clone local do JUCE, comente o bloco
# FetchContent e descomente a linha add_subdirectory abaixo.
include(FetchContent)
FetchContent_Declare(
    JUCE
    GIT_REPOSITORY https://github.com/juce-framework/JUCE.git
    GIT_TAG        8.0.4
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(JUCE)

# add_subdirectory(C:/caminho/para/seu/JUCE JUCE)   # alternativa offline

juce_add_plugin(SidechainCompressor
    COMPANY_NAME            "SuaEmpresa"
    IS_SYNTH                FALSE
    NEEDS_MIDI_INPUT        FALSE
    NEEDS_MIDI_OUTPUT       FALSE
    IS_MIDI_EFFECT          FALSE
    EDITOR_WANTS_KEYBOARD_FOCUS FALSE
    COPY_PLUGIN_AFTER_BUILD TRUE
    PLUGIN_MANUFACTURER_CODE Jonf
    PLUGIN_CODE             Scmp
    FORMATS                 VST3
    PRODUCT_NAME            "Sidechain Compressor"
)

target_sources(SidechainCompressor
    PRIVATE
        Source/PluginProcessor.cpp
        Source/PluginEditor.cpp
)

target_compile_definitions(SidechainCompressor
    PUBLIC
        JUCE_WEB_BROWSER=0
        JUCE_USE_CURL=0
        JUCE_VST3_CAN_REPLACE_VST2=0
)

target_link_libraries(SidechainCompressor
    PRIVATE
        juce::juce_audio_utils
        juce::juce_dsp
    PUBLIC
        juce::juce_recommended_config_flags
        juce::juce_recommended_lto_flags
        juce::juce_recommended_warning_flags
)
