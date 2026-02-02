#include "PlaybackGain.h"
#include <cmath>
#include <algorithm>

namespace viper {
namespace effects {

PlaybackGain::PlaybackGain(uint32_t samplingRate)
    : samplingRate(samplingRate), enabled(false),
      alpha(1.0f), energyFactor(2.0),
      beta(0.0f), counter(0), currentVolume(1.0f), maxGain(1.0f), currentGain(1.0f) {
    SetSamplingRate(samplingRate);
}

PlaybackGain::~PlaybackGain() {
}

void PlaybackGain::SetSamplingRate(uint32_t samplingRate) {
    this->samplingRate = samplingRate;
    Reset();
}

void PlaybackGain::Reset() {
    filterL.Reset();
    filterR.Reset();
    
    // BandPass at 2200Hz, Q=0.33 (from decompiled code 0.33)
    // Decompiled code uses 2200.0 for both calls in Reset/Constructor (one seems dynamic but let's stick to 2200)
    filterL.SetBandPassParameter(2200.0f, samplingRate, 0.33f);
    filterR.SetBandPassParameter(2200.0f, samplingRate, 0.33f);
    
    currentGain = 1.0f;
    counter = 0;
}

void PlaybackGain::SetEnable(bool enable) {
    if (this->enabled != enable) {
        if (!this->enabled) {
            Reset();
        }
        this->enabled = enable;
    }
}

void PlaybackGain::SetRatio(float ratio) {
    // Let's store the raw param for now as 'beta' and calculate alpha.
    // Logic: field_0x0 (alpha) = 1.0 / (1.0 + ratio)?
    alpha = 1.0f / (1.0f + ratio); 
    
    beta = ratio;
    if (beta < 0.0f) beta = 0.0f;
    alpha = 1.0f / (1.0f + beta);
}

void PlaybackGain::SetVolume(float volume) {
    // field24_0x18
    // Decompiled: `... * param_1 + ...` Linear transform?
    // Let's just store volume.
    currentVolume = volume;
}

void PlaybackGain::SetMaxGainFactor(float maxGainFactor) {
    // field28_0x1c
    maxGain = maxGainFactor;
}

double PlaybackGain::AnalyseWave(float *buffer, uint32_t size) {
    if (size == 0) return 0.0;

    double sumL = 0.0;
    double sumR = 0.0;

    // Filter and accumulate energy
    // Note: We process *copies* for analysis so we don't filter the output audio!
    // But filters *do* update state.
    
    for (uint32_t i = 0; i < size * 2; i += 2) {
        float sampleL = buffer[i];
        float sampleR = buffer[i+1];

        // Process through detection filters
        double outL = filterL.ProcessSample((double)sampleL);
        double outR = filterR.ProcessSample((double)sampleR);

        sumL += outL * outL;
        sumR += outR * outR;
    }

    // Decompiled code finds min/max of sums?
    // `if (((uVar1 <= uVar2) && ...`
    // It seems to pick the larger energy? `lVar5 = lVar3` (SumL) vs `lVar4` (SumR).
    // Actually:
    // `if (uVar1 <= uVar2) ...` check high dwords.
    // It basically calculates Max(EnergyL, EnergyR).
    
    double maxEnergy = (sumL > sumR) ? sumL : sumR;
    
    // Average over number of samples (size)
    return maxEnergy / (double)size;
}

void PlaybackGain::Process(float *buffer, uint32_t size) {
    if (!enabled || size == 0) return;

    double energy = AnalyseWave(buffer, size);
    
    // Decompile: `if (lVar12 < 0) ...` (signed check? energy is usually pos)
    // `fVar13 = logf((float)(dVar1 * energyFactor) + offset)`
    // Energy to "Log Energy" / Decibels
    
    double db = log10(energy + 1e-9); // 1e-9 to avoid log(0)
    
    // Process loop logic seems to update `field_0x14` (counter)
    if (counter < 100) {
        counter++;
    }
    
    // Gain Calculation
    // fVar13 seems to be the detected level in Log domain.
    // fVar11 = (alpha * fVar13 - fVar13) * const * counter ...
    // This looks like `(Target - Input) * Ratio`.
    // Effectively: Diff = Input * (Alpha - 1). 
    // Applied gain is derived from this Diff.
    
    // Simplification for Float Implementation:
    // We want to bring 'energy' towards a target.
    // currentVolume is our makeup gain / target level?
    
    // Let's implement a standard AGC based on the structure we see.
    // 1. Detected Level (energy)
    // 2. Error = Target - Level
    // 3. Correction = Error * Ratio
    
    // Using the reversed logic:
    // Target Factor calculation
    
    float targetFactor = 1.0f;
    
    // If energy is very low (silence), don't boost essentially noise to max.
    if (energy > 0.000001) {
        // Gain logic from decompilation is complex/obscure literals.
        // We will approximate:
        // desired_gain = pow(10, (TargetDB - InputDB) * Ratio / 20)
        
        // Let's use the explicit 'SetRatio' param mapping:
        // alpha ~ 1/(1+ratio)
        // Gain = InputEnergy ^ (alpha - 1) * Makeup?
        
        // Let's try:
        // Amplification factor to normalize energy to 1.0: 1/sqrt(energy)
        // We temper this by Ratio.
        
        double rms = sqrt(energy);
        double desiredGain = (1.0 / (rms + 1e-6));
        
        // Apply Max Gain Limit
        if (desiredGain > maxGain) desiredGain = maxGain;
        if (desiredGain < 1.0) desiredGain = 1.0; // Usually PlaybackGain allows attenuation? 
                                                  // Decompiled sets min to 0x...
        
        // Apply user volume and dynamics
        targetFactor = (float)desiredGain * currentVolume;
    } else {
        targetFactor = currentGain; // Hold
    }
    
    // Smooth transition (Ramp)
    // Decompiled does interpolation:
    // `iVar6 = (Target - Current) / Size` (Slope)
    // Loop applies `Current += Slope`.
    
    float startGain = currentGain;
    float endGain = targetFactor; // Or smooth towards it
    
    // Simple block-linear interpolation
    // If we want slower attack/release, we should use a lowpass on Gain,
    // but decompilation suggests linear ramp over buffer? 
    // "iVar6 = div(target - current, size)" -> This IS linear ramp to target over one buffer.
    // This implies very fast response (buffer size latency).
    
    float step = (endGain - startGain) / size;
    
    for (uint32_t i = 0; i < size; ++i) {
        currentGain += step;
        
        // Apply to L/R
        buffer[i*2] *= currentGain;
        buffer[i*2+1] *= currentGain;
    }
    
    // Clamp currentGain to target to avoid drift errors
    currentGain = endGain;
}

} // namespace effects
} // namespace viper
