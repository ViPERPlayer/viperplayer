#pragma once

#include <cstdint>

// FET-style feedback dynamics compressor.
//
// Reverse-engineered from ViPER4Android FX v2505 (native class `FETCompressor`,
// libv4a_fx_jb_NEON.so). It is a feedback compressor that works entirely in the
// natural-log amplitude domain. A crest-factor detector (peak^2 / mean^2) drives
// program-dependent ("auto") attack/release times, a quadratic soft knee derives
// the gain reduction, and an "adapt" smoothed envelope state is fed back into the
// gain computer (the FET feedback character). Optional auto-knee, auto-makeup and
// a clip guard ("noclip") round out the topology.
//
// The original ran on V4A's fixed-point sample buffer (unity == 2^25). This port
// operates directly on the project's interleaved-stereo float pipeline where
// unity == 1.0 (0 dBFS), so the original's 1/2^25 detector scale and 2^25 gain
// scale collapse away: the detector sees |sample| and the gain is applied by a
// plain multiply.
//
// Parameter surface mirrors the original SetParameter(int,float): continuous
// params take a normalised [0,1] value, booleans take 0/1 (compared against 0.5).
// Host command id == 65610 + index.
class FETCompressor {
public:
    enum Parameter {
        PARAM_ENABLE       = 0,
        PARAM_THRESHOLD    = 1,   // [0,1] -> [0,-60] dB
        PARAM_RATIO        = 2,   // [0,1] -> slope 0..1 (1 == inf:1 limiting)
        PARAM_KNEE         = 3,   // [0,1] -> [0,+60] dB knee width
        PARAM_AUTO_KNEE    = 4,
        PARAM_GAIN         = 5,   // makeup, [0,1] -> [0,+60] dB
        PARAM_AUTO_GAIN    = 6,
        PARAM_ATTACK       = 7,   // [0,1] -> 0.1ms..200ms
        PARAM_AUTO_ATTACK  = 8,
        PARAM_RELEASE      = 9,   // [0,1] -> 5ms..2s
        PARAM_AUTO_RELEASE = 10,
        PARAM_KNEE_MULTI   = 11,  // [0,1] -> 0..4 (auto-knee inflection gain)
        PARAM_MAX_ATTACK   = 12,  // [0,1] -> 0.1ms..200ms (auto-attack bound)
        PARAM_MAX_RELEASE  = 13,  // [0,1] -> 5ms..2s     (auto-release bound)
        PARAM_CREST        = 14,  // [0,1] -> 5ms..2s     (crest detector window)
        PARAM_ADAPT        = 15,  // [0,1] -> feedback envelope smoothing
        PARAM_NO_CLIP      = 16,
        PARAM_COUNT        = 17,
    };

    explicit FETCompressor(uint32_t samplingRate);

    void Process(float *samples, uint32_t size);
    void Reset();
    void SetEnable(bool enable);
    void SetSamplingRate(uint32_t samplingRate);

    // Faithful param surface (mirrors the original SetParameter(int,float)).
    void SetParameter(int index, float value);
    float GetParameter(int index) const;
    static float GetParameterDefault(int index);
    float GetMeter(int index) const;

private:
    // 1 - exp(-1/(sampleRate * seconds)); one-pole coefficient from a time
    // constant (original FUN_00067ef8). Returns 1.0 for non-positive times.
    float CalcTimeCoeff(float seconds) const;

    // Computes the linear gain for one (already rectified, >=0) detector sample.
    float ProcessSidechain(float detector);

    uint32_t mSamplingRate;
    float mParameters[PARAM_COUNT];   // raw [0,1] values (GetParameter source)

    // boolean flags (struct offsets 0x4c..0x50, 0xac)
    bool mEnable;
    bool mAutoKnee;
    bool mAutoGain;
    bool mAutoAttack;
    bool mAutoRelease;
    bool mNoClip;

    // derived parameter values (struct offsets noted)
    float mParamSmoothCoeff;   // 0x48  per-sample threshold/makeup smoothing
    float mThreshold;          // 0x64  log-amplitude threshold (target)
    float mKnee;               // 0x68  log-amplitude knee width
    float mMakeup;             // 0x70  log-amplitude makeup gain (target)
    float mNegRatio;           // 0x74  -ratio (slope)
    float mKneeMulti;          // 0x90  auto-knee inflection multiplier
    float mAttackTime;         // 0x80  seconds
    float mAttackCoeff;        // 0x84
    float mReleaseTime;        // 0x88  seconds
    float mReleaseCoeff;       // 0x8c
    float mMaxAttackTime;      // 0x94  seconds
    float mMaxReleaseTime;     // 0x98  seconds
    float mCrestTime;          // 0x9c  seconds
    float mCrestCoeff;         // 0xa0
    float mAdaptTime;          // 0xa4
    float mAdaptCoeff;         // 0xa8

    // runtime state
    float mSmThreshold;        // 0x60  smoothed threshold
    float mSmMakeup;           // 0x6c  smoothed makeup gain
    float mRmsPeak;            // 0x78  crest peak^2 state
    float mRmsMean;            // 0x7c  crest mean^2 state
    float mGrState1;           // 0x54  gain-reduction ballistic stage 1
    float mGrState2;           // 0x58  gain-reduction ballistic stage 2
    float mEnvState;           // 0x5c  fed-back envelope (adapt) state
};
