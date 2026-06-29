#pragma once


#include "IIR_NOrder_BW_LH.h"
#include "WaveBuffer.h"
#include "IIR_NOrder_BW_BP.h"

class HiFi {
public:
    HiFi(uint32_t samplingRate);

    ~HiFi();

    void Process(float *samples, uint32_t size);

    void Reset();

    void SetClarity(float value);

    void SetSamplingRate(uint32_t samplingRate);

    // Heap-allocated to mirror the original, which builds each buffer/filter with operator_new
    // in the constructor and releases them with operator_delete in the destructor.
    // buffers[0] carries the band-pass branch, buffers[1] the low-pass branch.
    WaveBuffer *buffers[2];
    struct {
        IIR_NOrder_BW_LH *lowpass;  // 1st order
        IIR_NOrder_BW_LH *highpass; // 3rd order
        IIR_NOrder_BW_BP *bandpass; // 3rd order
    } filters[2];
    float gain; // linear; original stores Q25 (1.0 == 0x2000000)
    uint32_t samplingRate;
};


