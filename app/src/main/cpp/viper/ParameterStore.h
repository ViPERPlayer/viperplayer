#ifndef VIPER_PARAMETER_STORE_H
#define VIPER_PARAMETER_STORE_H

#include <array>
#include <atomic>
#include <cstdint>
#include <cstring>

namespace viper {

    /**
     * Every effect parameter that is handed over to the audio thread rather than written to the
     * effect directly. See ParameterStore for what belongs here and what does not.
     *
     * The order is not significant — a drain applies whatever is dirty in enum order, and no two
     * parameters here depend on being applied in a particular sequence (they are independent
     * setters on independent effects, or independent indices of FETCompressor::SetParameter).
     *
     * kCount must stay last.
     */
    enum class Param : uint16_t {
        MasterLimiterGainL,
        MasterLimiterGainR,
        MasterLimiterThresholdLimit,

        SpectrumExtensionEnabled,
        SpectrumExtensionStrength,

        FieldSurroundEnabled,
        FieldSurroundStrength,
        FieldSurroundMidImageStrength,

        DifferentialSurroundEnabled,
        DifferentialSurroundDelay,

        DynamicSystemEnabled,
        DynamicSystemDeviceType,
        DynamicSystemBassStrength,

        TubeSimulatorEnabled,

        ViperBassEnabled,
        ViperBassMode,
        ViperBassFrequency,
        ViperBassGain,

        ViperClarityEnabled,
        ViperClarityMode,
        ViperClarityGain,

        AuditorySystemProtectionEnabled,
        AuditorySystemProtectionLevel,

        AnalogXEnabled,
        AnalogXLevel,

        SpeakerOptimizationEnabled,

        PlaybackGainEnabled,
        PlaybackGainStrength,
        PlaybackGainMaxGain,
        PlaybackGainOutputThreshold,

        FetCompressorEnabled,
        FetCompressorThreshold,
        FetCompressorRatio,
        FetCompressorKnee,
        FetCompressorAutoKnee,
        FetCompressorGain,
        FetCompressorAutoGain,
        FetCompressorAttack,
        FetCompressorAutoAttack,
        FetCompressorRelease,
        FetCompressorAutoRelease,
        FetCompressorKneeMulti,
        FetCompressorMaxAttack,
        FetCompressorMaxRelease,
        FetCompressorCrest,
        FetCompressorAdapt,
        FetCompressorNoClip,

        ReverberationEnabled,
        ReverberationRoomSize,
        ReverberationWidth,
        ReverberationDamp,
        ReverberationWet,
        ReverberationDry,

        kCount
    };

    constexpr size_t kParamCount = static_cast<size_t>(Param::kCount);

    /**
     * Number of Dynamic System device presets, i.e. the valid range of
     * Param::DynamicSystemDeviceType. Declared here so the JNI boundary can reject an out-of-range
     * ordinal (where it can be logged) without pulling in the preset table itself, which lives next
     * to the code that reads it in ViPER::applyParameter.
     */
    constexpr int kDynamicSystemDeviceTypeCount = 23;

    /**
     * Latest-value-wins handover of scalar effect parameters from the control thread to the audio
     * thread.
     *
     * ## Why
     *
     * The effects are configured from a coroutine on `Dispatchers.Default`
     * (`ViperAudioProcessor.updateNativeDriverConfiguration`) while `ViPER::process` reads the same
     * fields on the playback thread. Almost none of the effects synchronise anything, so every
     * parameter was a plain unsynchronised cross-thread write — a data race, and in practice a
     * chance to render a buffer against a half-updated parameter set.
     *
     * Rather than lock each effect (a lock on the audio thread is exactly what must not happen),
     * the control thread stores values here and the audio thread applies them to the effects at the
     * top of `process`. Only the audio thread ever mutates effect state, so the race is gone by
     * construction rather than by careful locking.
     *
     * ## What belongs here
     *
     * Scalar parameters whose effect setter does NOT itself take a lock. Convolver, VHE, ViPERDDC
     * and IIREqualizer each own a mutex and already have a defined cross-thread protocol (their
     * `process` try-locks and passes through on contention), and their setters reach that mutex —
     * routing those through here would make the audio thread block on it, reintroducing the very
     * hazard the protocol exists to avoid. Those stay direct calls. Bulk payloads (an impulse
     * response, a DDC coefficient table, a whole EQ curve) stay direct for the same reason, plus
     * they would have to allocate.
     *
     * ## Ordering
     *
     * A store publishes the value before the dirty flag (release) and a drain reads the dirty flag
     * before the value (acquire), so the audio thread can never see a stale value for a flag it has
     * observed. Latest-value-wins means a slider dragged across many positions between two buffers
     * costs exactly one application, not one per intermediate position.
     *
     * A value stored concurrently with a drain either lands in that drain or leaves `anyDirty_` set
     * for the next one; it cannot be lost. Nothing here blocks, allocates, or grows without bound —
     * in particular, parameters set while playback is stopped simply sit until it resumes, rather
     * than backing up in a queue that nothing is draining.
     */
    class ParameterStore {
    public:
        void setBool(Param param, bool value) { setRaw(param, value ? 1 : 0); }

        void setInt(Param param, int32_t value) { setRaw(param, value); }

        void setFloat(Param param, float value) {
            int32_t raw;
            std::memcpy(&raw, &value, sizeof(raw));
            setRaw(param, raw);
        }

        /**
         * Applies every parameter changed since the last drain, calling
         * `apply(Param, int32_t rawValue)` for each. Audio-thread side: no locks, no allocation.
         */
        template<typename Apply>
        void drain(Apply &&apply) {
            // Clearing this first means a concurrent store either gets picked up by the scan below
            // or re-sets the flag for the next drain. Either way it is not lost; the worst case is
            // one redundant scan.
            if (!anyDirty_.exchange(false, std::memory_order_acquire)) {
                return;
            }
            for (size_t i = 0; i < kParamCount; ++i) {
                if (dirty_[i].exchange(false, std::memory_order_acquire)) {
                    apply(static_cast<Param>(i), values_[i].load(std::memory_order_relaxed));
                }
            }
        }

        /** Marks every parameter dirty, so the next drain re-applies the whole configuration. */
        void markAllDirty() {
            for (size_t i = 0; i < kParamCount; ++i) {
                dirty_[i].store(true, std::memory_order_release);
            }
            anyDirty_.store(true, std::memory_order_release);
        }

        /** Decodes a raw value stored by setBool / setInt / setFloat. */
        static bool asBool(int32_t raw) { return raw != 0; }

        static int32_t asInt(int32_t raw) { return raw; }

        static float asFloat(int32_t raw) {
            float value;
            std::memcpy(&value, &raw, sizeof(value));
            return value;
        }

    private:
        void setRaw(Param param, int32_t raw) {
            const size_t index = static_cast<size_t>(param);
            values_[index].store(raw, std::memory_order_relaxed);
            // Release: a drain that observes this flag must also observe the value above.
            dirty_[index].store(true, std::memory_order_release);
            anyDirty_.store(true, std::memory_order_release);
        }

        std::array<std::atomic<int32_t>, kParamCount> values_{};
        std::array<std::atomic<bool>, kParamCount> dirty_{};
        // Lets an unchanged configuration cost a single atomic load per buffer instead of a scan.
        std::atomic<bool> anyDirty_{false};
    };

} // namespace viper

#endif // VIPER_PARAMETER_STORE_H
