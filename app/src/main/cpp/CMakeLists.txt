cmake_minimum_required(VERSION 3.22.1)

project("viper")

add_library(${CMAKE_PROJECT_NAME} SHARED
        # Common
        viper/ViPER.cpp

        # Effects
        viper/effects/AnalogX.cpp
        viper/effects/ColorfulMusic.cpp
        viper/effects/Cure.cpp
        viper/effects/DiffSurround.cpp
        viper/effects/DynamicSystem.cpp
        viper/effects/Reverberation.cpp
        viper/effects/SoftwareLimiter.cpp
        viper/effects/PlaybackGain.cpp
        viper/effects/SpeakerCorrection.cpp
        viper/effects/SpectrumExtend.cpp
        viper/effects/TubeSimulator.cpp
        viper/effects/ViPERBass.cpp
        viper/effects/ViPERClarity.cpp
        viper/effects/ViPERDDC.cpp
        
        # DSP
        viper/dsp/Convolver.cpp
        viper/dsp/PartitionedConvolver.cpp
        viper/dsp/IIREqualizer.cpp

        # Utils
        viper/utils/AdaptiveBuffer.cpp
        viper/utils/Biquad.cpp
        viper/utils/CAllpassFilter.cpp
        viper/utils/CCombFilter.cpp
        viper/utils/CRevModel.cpp
        viper/utils/Crossfeed.cpp
        viper/utils/DepthSurround.cpp
        viper/utils/DynamicBass.cpp
        viper/utils/FIR.cpp
        viper/utils/Harmonic.cpp
        viper/utils/HiFi.cpp
        viper/utils/HighShelf.cpp
        viper/utils/IIR_1st.cpp
        viper/utils/IIR_NOrder_BW_BP.cpp
        viper/utils/IIR_NOrder_BW_LH.cpp
        viper/utils/MultiBiquad.cpp
        viper/utils/NoiseSharpening.cpp
        viper/utils/PassFilter.cpp
        viper/utils/PolesFilter.cpp
        viper/utils/Polyphase.cpp
        viper/utils/Stereo3DSurround.cpp
        viper/utils/Subwoofer.cpp
        viper/utils/TimeConstDelay.cpp
        viper/utils/CircularBuffer.cpp
        viper/utils/WaveBuffer.cpp
        viper/utils/FFT.cpp

        viperplayer.cpp)

# FFTW Configuration
set(ENABLE_FLOAT ON CACHE BOOL "Build single precision" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libs" FORCE)

if(${ANDROID_ABI} MATCHES "armeabi-v7a" OR ${ANDROID_ABI} MATCHES "arm64-v8a")
    set(ENABLE_NEON ON CACHE BOOL "Enable NEON" FORCE)
endif()

add_subdirectory(viper/external/fftw)
include_directories(viper/external/fftw/api)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        fftw3f)
