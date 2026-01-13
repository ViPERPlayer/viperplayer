cmake_minimum_required(VERSION 3.22.1)

project("viper")

add_library(${CMAKE_PROJECT_NAME} SHARED
        # Common
        viper/ViPER.cpp

        # Effects
        viper/effects/AnalogX.cpp
        viper/effects/ColorfulMusic.cpp
        viper/effects/Convolver.cpp
        viper/effects/Cure.cpp
        viper/effects/DiffSurround.cpp
        viper/effects/DynamicSystem.cpp
        viper/effects/FETCompressor.cpp
        viper/effects/IIRFilter.cpp
        viper/effects/PlaybackGain.cpp
        viper/effects/Reverberation.cpp
        viper/effects/SoftwareLimiter.cpp
        viper/effects/SpeakerCorrection.cpp
        viper/effects/SpectrumExtend.cpp
        viper/effects/TubeSimulator.cpp
        viper/effects/VHE.cpp
        viper/effects/ViPERBass.cpp
        viper/effects/ViPERClarity.cpp
        viper/effects/ViPERDDC.cpp

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
        viper/utils/MinPhaseIIRCoeffs.cpp
        viper/utils/MultiBiquad.cpp
        viper/utils/NoiseSharpening.cpp
        viper/utils/PassFilter.cpp
        viper/utils/PConvSingle.cpp
        viper/utils/PolesFilter.cpp
        viper/utils/Polyphase.cpp
        viper/utils/Stereo3DSurround.cpp
        viper/utils/Subwoofer.cpp
        viper/utils/TimeConstDelay.cpp
        viper/utils/WaveBuffer.cpp

        viperplayer.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log)
