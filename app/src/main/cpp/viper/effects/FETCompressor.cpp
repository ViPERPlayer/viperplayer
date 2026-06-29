#include "FETCompressor.h"

#include <cmath>

// ---------------------------------------------------------------------------
// Constants recovered from libv4a_fx_jb_NEON.so (image base 0x10000).
// ---------------------------------------------------------------------------
namespace {
    // SetParameter time-mapping literals (SetParameter literal pool @ 0x68214..):
    constexpr float kLn2000  = 7.600902557373047f;   // 0x68214 = 7.600902557  ln(2000)  attack/maxAttack
    constexpr float kLn10000 = 9.21034049987793f;    // 0x68218 = 9.210340500  ln(10000)
    constexpr float kLn400   = 5.991464614868164f;   // 0x6821c = 5.991464615  ln(400)   release/maxRelease/crest
    constexpr float kLn200   = 5.2983174324035645f;  // 0x68220 = 5.298317432  ln(200)
    constexpr float kLn4     = 1.3862943649291992f;  // 0x68224 = 1.386294365  ln(4)     adapt

    // dB->natural-log-amplitude range literals (SetParameter literal pool):
    constexpr float kThresholdDb = -60.0f;           // 0x6820c = -60.0  threshold span
    constexpr float kBoostDb     = 60.0f;            // 0x68210 =  60.0  knee/makeup span

    // Crest squared-mean floor (ProcessSidechain compare 0x6863c) and the matching
    // peak/mean reset seed (Reset immediate 0x358637bd) are the same bit pattern.
    constexpr float kRmsFloor = 1.0e-6f;             // 0x6863c / 0x358637bd = 9.999999975e-07

    // Per-sample threshold/makeup smoothing time constant.
    constexpr float kParamSmoothSeconds = 0.05f;     // 0x68330 = 0.05000000075

    // noclip ceiling margin == ln(10)/2000 nepers == 0.01 dB. The decomp compares
    // against -margin (0x68640) and adds +margin (0x68644).
    constexpr float kNoClipMargin = 0.0011512705f;   // 0x68644 = 0.001151270466 (0x68640 = -ditto)

    // Factory defaults (FETCompressor::GetParameterDefault table @ 0xce870).
    constexpr float kDefaults[FETCompressor::PARAM_COUNT] = {
            1.0f,                   // enable
            0.0f,                   // threshold
            0.0f,                   // ratio
            0.0f,                   // knee
            1.0f,                   // autoKnee
            0.0f,                   // gain (makeup)
            1.0f,                   // autoGain
            0.5146787762641907f,    // attack
            1.0f,                   // autoAttack
            0.38431090116500854f,   // release
            1.0f,                   // autoRelease
            0.5f,                   // kneeMulti
            0.879449725151062f,     // maxAttack
            0.8843109011650085f,    // maxRelease
            0.6156890988349915f,    // crest
            0.6609640717506409f,    // adapt
            1.0f,                   // noClip
    };

    // Denormal flush: promote to double, add/sub 1e-18, demote (DAT_00068860).
    inline float FlushDenormal(float x) {
        return static_cast<float>((static_cast<double>(x) + 1.0e-18) - 1.0e-18);
    }
}

FETCompressor::FETCompressor(uint32_t samplingRate) : mSamplingRate(samplingRate) {
    // Mirror the original constructor: apply every default through SetParameter
    // (so all derived coefficients are built), then Reset() the state.
    for (int i = 0; i < PARAM_COUNT; i++) {
        SetParameter(i, GetParameterDefault(i));
    }
    Reset();
}

float FETCompressor::CalcTimeCoeff(float seconds) const {
    // FUN_00067ef8(sampleRate, seconds) = 1 - exp(-1 / (sampleRate * seconds)).
    // The original guards a non-positive time by returning 1.0 (instant).
    float denom = static_cast<float>(mSamplingRate) * seconds;
    if (denom <= 0.0f) {
        return 1.0f;
    }
    return 1.0f - expf(-1.0f / denom);
}

void FETCompressor::SetParameter(int index, float value) {
    if (index < 0 || index >= PARAM_COUNT) {
        return;
    }
    mParameters[index] = value;

    switch (index) {
        case PARAM_ENABLE:
            mEnable = value >= 0.5f;
            break;
        case PARAM_THRESHOLD:
            // [0,1] -> [0,-60] dB, stored as natural-log amplitude (log(pow(10, dB/20))).
            mThreshold = logf(powf(10.0f, value * kThresholdDb / 20.0f));
            break;
        case PARAM_RATIO:
            mNegRatio = -value;
            break;
        case PARAM_KNEE:
            // [0,1] -> [0,+60] dB knee width (natural-log amplitude).
            mKnee = logf(powf(10.0f, value * kBoostDb / 20.0f));
            break;
        case PARAM_AUTO_KNEE:
            mAutoKnee = value >= 0.5f;
            break;
        case PARAM_GAIN:
            // [0,1] -> [0,+60] dB makeup (natural-log amplitude).
            mMakeup = logf(powf(10.0f, value * kBoostDb / 20.0f));
            break;
        case PARAM_AUTO_GAIN:
            mAutoGain = value >= 0.5f;
            break;
        case PARAM_ATTACK:
            mAttackTime = expf(value * kLn2000 - kLn10000);   // 0.1ms..200ms
            mAttackCoeff = CalcTimeCoeff(mAttackTime);
            break;
        case PARAM_AUTO_ATTACK:
            mAutoAttack = value >= 0.5f;
            break;
        case PARAM_RELEASE:
            mReleaseTime = expf(value * kLn400 - kLn200);     // 5ms..2s
            mReleaseCoeff = CalcTimeCoeff(mReleaseTime);
            break;
        case PARAM_AUTO_RELEASE:
            mAutoRelease = value >= 0.5f;
            break;
        case PARAM_KNEE_MULTI:
            // 0x68228 + value*4.0 ; the base immediate at 0x68228 is 0.0.
            mKneeMulti = 0.0f + value * 4.0f;
            break;
        case PARAM_MAX_ATTACK:
            mMaxAttackTime = expf(value * kLn2000 - kLn10000);
            break;
        case PARAM_MAX_RELEASE:
            mMaxReleaseTime = expf(value * kLn400 - kLn200);
            break;
        case PARAM_CREST:
            mCrestTime = expf(value * kLn400 - kLn200);
            mCrestCoeff = CalcTimeCoeff(mCrestTime);
            break;
        case PARAM_ADAPT:
            mAdaptTime = expf(value * kLn4);                  // 1..4
            mAdaptCoeff = CalcTimeCoeff(mAdaptTime);
            break;
        case PARAM_NO_CLIP:
            mNoClip = value >= 0.5f;
            break;
        default:
            break;
    }
}

float FETCompressor::GetParameter(int index) const {
    if (index < 0 || index >= PARAM_COUNT) {
        return 0.0f;
    }
    return mParameters[index];
}

float FETCompressor::GetParameterDefault(int index) {
    if (index < 0 || index >= PARAM_COUNT) {
        return 0.0f;
    }
    return kDefaults[index];
}

void FETCompressor::SetEnable(bool enable) {
    SetParameter(PARAM_ENABLE, enable ? 1.0f : 0.0f);
}

void FETCompressor::SetSamplingRate(uint32_t samplingRate) {
    mSamplingRate = samplingRate;
    // Re-derive every rate-dependent coefficient, then reset state.
    for (int i = 0; i < PARAM_COUNT; i++) {
        SetParameter(i, mParameters[i]);
    }
    Reset();
}

void FETCompressor::Reset() {
    mParamSmoothCoeff = CalcTimeCoeff(kParamSmoothSeconds);
    mSmThreshold = mThreshold;
    mSmMakeup = mMakeup;
    mRmsPeak = kRmsFloor;
    mRmsMean = kRmsFloor;
    mGrState1 = 0.0f;
    mGrState2 = 0.0f;
    mEnvState = 0.0f;
}

float FETCompressor::ProcessSidechain(float detector) {
    // --- crest-factor detector (squared domain) -------------------------------
    float sq = detector * detector;
    if (sq <= kRmsFloor) {
        sq = kRmsFloor;
    }

    float crestCoeff = mCrestCoeff;
    float peak = mRmsPeak + crestCoeff * (sq - mRmsPeak);
    float mean = mRmsMean + crestCoeff * (sq - mRmsMean);
    if (sq > peak) {
        peak = sq;          // instantaneous peak hold
    }
    mRmsMean = mean;
    mRmsPeak = peak;
    float crest = peak / mean;   // peak^2 / mean^2

    // --- program-dependent (auto) attack -------------------------------------
    float attackCoeff = mAttackCoeff;
    float attackTime = mAttackTime;     // also feeds the release-time formula
    if (mAutoAttack) {
        attackTime = (mMaxAttackTime + mMaxAttackTime) / crest;   // 2*maxAttack/crest
        attackCoeff = CalcTimeCoeff(attackTime);
    }

    // --- program-dependent (auto) release ------------------------------------
    float releaseCoeff = mReleaseCoeff;
    if (mAutoRelease) {
        float relTime = (mMaxReleaseTime + mMaxReleaseTime) / crest - attackTime;
        releaseCoeff = CalcTimeCoeff(relTime);   // guards relTime <= 0 -> 1.0
    }

    // --- log-domain level & soft-knee gain computer ---------------------------
    // The original feeds the raw detector straight into logf (only the crest
    // *squared* mean is floored above). At true silence that yields logIn = -inf,
    // but it never reaches the output: overshoot <= -halfKnee forces gr = 0 and the
    // noclip test fails, so the gain is finite either way. The kRmsFloor clamp here
    // is a defensive no-op that is output-equivalent for every non-silent sample.
    float det = (detector > kRmsFloor) ? detector : kRmsFloor;
    float logIn = logf(det);
    float smThr = mSmThreshold;
    float overshoot = logIn - smThr;

    float env = mEnvState;
    float slope;
    float kneeWidth;
    float halfKnee;
    float thrHalf;
    if (mAutoKnee) {
        // Auto knee width from the fed-back envelope; slope forced to 1 (limiter).
        thrHalf = smThr * 0.5f;
        float autoKnee = -((env + thrHalf) * mKneeMulti);
        slope = 1.0f;
        if (autoKnee > 0.0f) {
            kneeWidth = autoKnee;
            halfKnee = autoKnee * 0.5f;
        } else {
            kneeWidth = 0.0f;
            halfKnee = 0.0f;
        }
    } else {
        slope = -mNegRatio;                 // ratio param == compression slope
        kneeWidth = mKnee;
        halfKnee = kneeWidth * 0.5f;
        thrHalf = smThr * slope * 0.5f;
    }

    float gr;
    if (overshoot >= halfKnee) {
        gr = overshoot;                                     // above the knee
    } else if (overshoot <= -halfKnee) {
        gr = 0.0f;                                          // below the knee
    } else {
        float t = overshoot + halfKnee;                     // quadratic knee
        gr = (t * t) / (kneeWidth + kneeWidth);
    }
    gr *= slope;

    // --- ballistics: release smoother -> peak hold -> attack smoother ---------
    float grState1 = mGrState1 + (gr - mGrState1) * releaseCoeff;
    float g = (gr <= grState1) ? grState1 : gr;
    mGrState1 = grState1;

    float g2 = mGrState2 + (g - mGrState2) * attackCoeff;
    mGrState2 = g2;

    // --- fed-back envelope (adapt) state --------------------------------------
    float envNew = env + (((-g2) - thrHalf) - env) * mAdaptCoeff;
    mEnvState = envNew;

    // --- output gain ----------------------------------------------------------
    float gainLog;
    if (mAutoGain) {
        float makeupTerm = thrHalf + envNew;        // automatic makeup
        if (mNoClip) {
            float outLevel = logIn - g2;
            if ((outLevel - makeupTerm) > -kNoClipMargin) {
                // Output would exceed the ceiling: clamp the fed-back envelope so
                // the result sits ~0.01 dB below full scale.
                float clamped = outLevel - thrHalf + kNoClipMargin;
                mEnvState = clamped;
                makeupTerm = clamped + thrHalf;
            }
        }
        gainLog = -g2 - makeupTerm;
    } else {
        gainLog = mSmMakeup - g2;                    // fixed makeup gain
    }
    return expf(gainLog);
}

void FETCompressor::Process(float *samples, uint32_t size) {
    if (!mEnable) {
        return;
    }

    for (uint32_t i = 0; i < size; i++) {
        float l = samples[2 * i];
        float r = samples[2 * i + 1];

        // Stereo-linked detector: max(|L|, |R|).
        float absL = fabsf(l);
        float absR = fabsf(r);
        float detector = absL > absR ? absL : absR;

        float gain = ProcessSidechain(detector);

        samples[2 * i] = l * gain;
        samples[2 * i + 1] = r * gain;

        // Per-sample smoothing of the threshold / makeup targets (zipper-free).
        mSmThreshold += (mThreshold - mSmThreshold) * mParamSmoothCoeff;
        mSmMakeup += (mMakeup - mSmMakeup) * mParamSmoothCoeff;
    }

    // Flush denormals out of every recursive state variable.
    mGrState1 = FlushDenormal(mGrState1);
    mGrState2 = FlushDenormal(mGrState2);
    mEnvState = FlushDenormal(mEnvState);
    mRmsPeak = FlushDenormal(mRmsPeak);
    mRmsMean = FlushDenormal(mRmsMean);
    mSmThreshold = FlushDenormal(mSmThreshold);
    mSmMakeup = FlushDenormal(mSmMakeup);
}

float FETCompressor::GetMeter(int index) const {
    // Gain-reduction meter (1.0 == no reduction, 0.0 == full-scale reduction).
    //
    // Original (FETCompressor::GetMeter @ 0x68260):
    //   if (index != 0) return 0.0;                 // 0x682d8 = 0.0
    //   meter = (-mGrState2 - ref) / (0.0 - ref);   // ref @ 0xd3894
    //   if (!enable || meter > 1.0) return 1.0;
    //   return max(meter, 0.0);
    // which simplifies to meter = 1 + mGrState2/ref, clamped to [0,1].
    //
    // The reference amplitude `ref` lives at 0xd3894 (PC-relative: 0x682dc value
    // 0x6d608 + 0x6828c), a data region NOT present in the available static dumps,
    // so it could not be recovered. Matching the simplified form, kMeterRange == -ref.
    // ln(10) (= 20 dB in nepers) is the most likely value but remains a GUESS; this
    // only affects the meter readout, never the processed audio.
    if (index != 0) {
        return 0.0f;
    }
    if (!mEnable) {
        return 1.0f;
    }
    constexpr float kMeterRange = 2.3025851f;   // GUESS: -ref ~= ln(10) ~= 20 dB
    float meter = 1.0f - mGrState2 / kMeterRange;
    if (meter > 1.0f) {
        meter = 1.0f;
    }
    if (meter < 0.0f) {
        meter = 0.0f;
    }
    return meter;
}
