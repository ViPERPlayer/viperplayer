#include "TestFramework.h"

// Entry point for the host-side native DSP tests. Every test registers itself via VIPER_TEST at
// static-initialisation time, so there is nothing to list here — adding a new *Test.cpp to
// CMakeLists.txt is enough. Exit code is the number of failed tests, so a non-zero exit fails the
// Gradle `nativeDspTest` task and, through it, `check`.
int main() {
    return ::viper_test::runAll();
}
