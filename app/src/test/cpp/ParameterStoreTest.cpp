#include "TestFramework.h"

#include "viper/ParameterStore.h"

#include <atomic>
#include <thread>
#include <vector>

using viper::Param;
using viper::ParameterStore;

namespace {

    struct Applied {
        Param param;
        int32_t raw;
    };

    /** Drains [store] into a list, standing in for the audio thread's apply callback. */
    std::vector<Applied> drainToVector(ParameterStore &store) {
        std::vector<Applied> applied;
        store.drain([&applied](Param param, int32_t raw) {
            applied.push_back(Applied{param, raw});
        });
        return applied;
    }

} // namespace

// The point of the store: a drain touches only what actually changed, so an untouched effect is
// never re-configured (and never has its filter state disturbed) because a neighbour moved.
VIPER_TEST(parameterStore_drainAppliesOnlyChangedParameters) {
    ParameterStore store;
    store.setInt(Param::ReverberationWet, 40);
    store.setBool(Param::TubeSimulatorEnabled, true);

    const std::vector<Applied> applied = drainToVector(store);

    CHECK(applied.size() == 2);
    // Enum order, not call order.
    CHECK(applied[0].param == Param::TubeSimulatorEnabled);
    CHECK(ParameterStore::asBool(applied[0].raw));
    CHECK(applied[1].param == Param::ReverberationWet);
    CHECK(ParameterStore::asInt(applied[1].raw) == 40);
}

// The common case by far — nothing moved since the last buffer — must cost nothing.
VIPER_TEST(parameterStore_drainIsANoOpWhenNothingChanged) {
    ParameterStore store;
    store.setInt(Param::ReverberationWet, 40);

    CHECK(drainToVector(store).size() == 1);
    CHECK(drainToVector(store).empty());
    CHECK(drainToVector(store).empty());
}

// Latest-value-wins: dragging a slider across 100 positions between two buffers costs one
// application at the final value, not 100 at intermediate ones.
VIPER_TEST(parameterStore_latestValueWins) {
    ParameterStore store;
    for (int value = 0; value <= 100; ++value) {
        store.setInt(Param::ReverberationRoomSize, value);
    }

    const std::vector<Applied> applied = drainToVector(store);

    CHECK(applied.size() == 1);
    CHECK(applied[0].param == Param::ReverberationRoomSize);
    CHECK(ParameterStore::asInt(applied[0].raw) == 100);
}

// Values are carried as a raw int32 payload, so the float and bool encodings have to survive the
// round trip exactly — a mangled float here would silently detune an effect.
VIPER_TEST(parameterStore_roundTripsEveryValueType) {
    ParameterStore store;
    store.setFloat(Param::MasterLimiterGainL, 0.7071068f);
    store.setFloat(Param::PlaybackGainOutputThreshold, -1.5f);
    store.setInt(Param::AnalogXLevel, -7);
    store.setBool(Param::SpeakerOptimizationEnabled, false);

    const std::vector<Applied> applied = drainToVector(store);

    CHECK(applied.size() == 4);
    for (const Applied &entry: applied) {
        switch (entry.param) {
            case Param::MasterLimiterGainL:
                CHECK(ParameterStore::asFloat(entry.raw) == 0.7071068f);
                break;
            case Param::PlaybackGainOutputThreshold:
                CHECK(ParameterStore::asFloat(entry.raw) == -1.5f);
                break;
            case Param::AnalogXLevel:
                CHECK(ParameterStore::asInt(entry.raw) == -7);
                break;
            case Param::SpeakerOptimizationEnabled:
                CHECK(!ParameterStore::asBool(entry.raw));
                break;
            default:
                CHECK(false);
                break;
        }
    }
}

// A `false` / `0` write must still count as a change. Storing the flag separately from the value is
// what buys this: a store that only compared values would drop the very first write of a zero.
VIPER_TEST(parameterStore_treatsZeroAndFalseAsChanges) {
    ParameterStore store;
    store.setBool(Param::FetCompressorNoClip, false);
    store.setInt(Param::ReverberationDry, 0);

    CHECK(drainToVector(store).size() == 2);
}

// Backs re-applying the whole configuration after the engine has been reset underneath us.
VIPER_TEST(parameterStore_markAllDirtyReAppliesEverything) {
    ParameterStore store;
    store.setInt(Param::ReverberationWet, 40);
    drainToVector(store);
    CHECK(drainToVector(store).empty());

    store.markAllDirty();

    const std::vector<Applied> applied = drainToVector(store);
    CHECK(applied.size() == viper::kParamCount);
    // The value staged before the reset is what gets re-applied, not a zero.
    for (const Applied &entry: applied) {
        if (entry.param == Param::ReverberationWet) {
            CHECK(ParameterStore::asInt(entry.raw) == 40);
        }
    }
}

// The whole reason the store exists. A control thread storing while the audio thread drains must
// never lose the last value: whatever the interleaving, a store either lands in the drain that is
// running or leaves the store dirty for the next one.
VIPER_TEST(parameterStore_doesNotLoseValuesUnderConcurrentDrains) {
    constexpr int kIterations = 20000;

    ParameterStore store;
    std::atomic<bool> writerDone{false};
    int32_t lastSeen = -1;

    std::thread writer([&store, &writerDone]() {
        for (int value = 0; value < kIterations; ++value) {
            store.setInt(Param::ReverberationWet, value);
        }
        writerDone.store(true, std::memory_order_release);
    });

    // Stand-in for the audio thread: drain in a tight loop while the writer runs.
    while (!writerDone.load(std::memory_order_acquire)) {
        store.drain([&lastSeen](Param param, int32_t raw) {
            if (param == Param::ReverberationWet) lastSeen = ParameterStore::asInt(raw);
        });
    }
    writer.join();

    // One final drain, exactly as the next buffer would do.
    store.drain([&lastSeen](Param param, int32_t raw) {
        if (param == Param::ReverberationWet) lastSeen = ParameterStore::asInt(raw);
    });

    CHECK(lastSeen == kIterations - 1);
}
