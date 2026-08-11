#ifndef VIPER_TEST_FRAMEWORK_H
#define VIPER_TEST_FRAMEWORK_H

// A deliberately tiny host-side test framework for the native DSP.
//
// The DSP under app/src/main/cpp had no tests of any kind: it is only reachable through JNI, so the
// JVM unit tests cannot touch it and the instrumentation tests would need a device. Everything in
// viper/dsp is plain C++ with no Android dependencies, though, so it compiles and runs on the build
// host — which is all this harness is. No GoogleTest, no fetch, no NDK: just a compiler, so CI can
// run it as part of `check` without adding a dependency that has to be downloaded or vendored.
//
// Usage:
//
//     VIPER_TEST(myThing_doesTheRightThing) {
//         CHECK(condition);
//         CHECK_NEAR(measured, expected, tolerance);
//     }
//
// A failing check records the failure and returns from that test; the remaining tests still run, so
// one run reports every broken thing rather than only the first.

#include <cmath>
#include <cstdio>
#include <functional>
#include <string>
#include <vector>

namespace viper_test {

    struct TestCase {
        std::string name;
        std::function<void()> body;
    };

    /** Every test registered via VIPER_TEST, in declaration order within a translation unit. */
    inline std::vector<TestCase> &registry() {
        static std::vector<TestCase> tests;
        return tests;
    }

    /** Failures recorded by the currently running test. Reset by the runner before each test. */
    inline std::vector<std::string> &currentFailures() {
        static std::vector<std::string> failures;
        return failures;
    }

    inline void recordFailure(const char *file, int line, const std::string &what) {
        currentFailures().push_back(std::string(file) + ":" + std::to_string(line) + ": " + what);
    }

    struct Registrar {
        Registrar(const char *name, std::function<void()> body) {
            registry().push_back(TestCase{name, std::move(body)});
        }
    };

    /** Runs every registered test. Returns the number of FAILED tests (0 == success). */
    inline int runAll() {
        int failed = 0;
        for (const TestCase &test: registry()) {
            currentFailures().clear();
            test.body();
            if (currentFailures().empty()) {
                std::printf("[  PASS  ] %s\n", test.name.c_str());
            } else {
                failed++;
                std::printf("[  FAIL  ] %s\n", test.name.c_str());
                for (const std::string &failure: currentFailures()) {
                    std::printf("           %s\n", failure.c_str());
                }
            }
        }
        std::printf("\n%zu tests, %d failed\n", registry().size(), failed);
        return failed;
    }

} // namespace viper_test

#define VIPER_TEST(name)                                                                    \
    static void name();                                                                     \
    static ::viper_test::Registrar viper_test_registrar_##name(#name, name);                \
    static void name()

#define CHECK(condition)                                                                    \
    do {                                                                                    \
        if (!(condition)) {                                                                 \
            ::viper_test::recordFailure(__FILE__, __LINE__, "CHECK(" #condition ") failed"); \
            return;                                                                         \
        }                                                                                   \
    } while (false)

#define CHECK_NEAR(actual, expected, tolerance)                                             \
    do {                                                                                    \
        const double viperTestActual = (actual);                                            \
        const double viperTestExpected = (expected);                                        \
        if (std::fabs(viperTestActual - viperTestExpected) > (tolerance)) {                 \
            ::viper_test::recordFailure(                                                    \
                    __FILE__, __LINE__,                                                     \
                    "CHECK_NEAR(" #actual ", " #expected ") failed: got " +                 \
                    std::to_string(viperTestActual) + ", expected " +                       \
                    std::to_string(viperTestExpected) + " +/- " +                           \
                    std::to_string(static_cast<double>(tolerance)));                        \
            return;                                                                         \
        }                                                                                   \
    } while (false)

#endif // VIPER_TEST_FRAMEWORK_H
