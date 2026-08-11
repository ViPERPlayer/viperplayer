cmake_minimum_required(VERSION 3.22.1)

# Host-side (NOT Android) build of the pure-C++ DSP units plus their tests. Driven by the Gradle
# `nativeDspTest` task, which wires it into `check` so CI runs it. See TestFramework.h for why this
# exists as its own tiny harness rather than an Android test or a GoogleTest dependency.
project("viper_dsp_tests" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(VIPER_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp")

add_executable(viper_dsp_tests
        main.cpp

        # Tests
        IIREqualizerTest.cpp
        ParameterStoreTest.cpp

        # Units under test. Only self-contained, Android-free DSP sources can be listed here; an
        # effect that reaches for <android/log.h> or JNI would need that dependency broken first.
        "${VIPER_CPP_DIR}/viper/dsp/IIREqualizer.cpp"
        # ParameterStore is header-only.
)

find_package(Threads REQUIRED)
target_link_libraries(viper_dsp_tests PRIVATE Threads::Threads)

target_include_directories(viper_dsp_tests PRIVATE
        "${VIPER_CPP_DIR}"
        "${CMAKE_CURRENT_SOURCE_DIR}"
)

if (NOT MSVC)
    target_compile_options(viper_dsp_tests PRIVATE -Wall -Wextra)
endif ()
