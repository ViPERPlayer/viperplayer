#include "HiFi.h"

HiFi::HiFi(uint32_t samplingRate) : samplingRate(samplingRate) {
    this->gain = 1.f;
    for (int i = 0; i < 2; i++) {
        this->buffers[i] = new WaveBuffer(2, 0x800);
        this->filters[i].lowpass = new IIR_NOrder_BW_LH(1);
        this->filters[i].highpass = new IIR_NOrder_BW_LH(3);
        this->filters[i].bandpass = new IIR_NOrder_BW_BP(3);
    }
    Reset();
}

HiFi::~HiFi() {
    for (int i = 0; i < 2; i++) {
        delete this->buffers[i];
        delete this->filters[i].lowpass;
        delete this->filters[i].highpass;
        delete this->filters[i].bandpass;
    }
}

void HiFi::Process(float *samples, uint32_t size) {
    if (size > 0) {
        float *bpBuf = this->buffers[0]->PushZerosGetBuffer(size);
        float *lpBuf = this->buffers[1]->PushZerosGetBuffer(size);
        if (bpBuf == nullptr || lpBuf == nullptr) {
            Reset();
            return;
        }

        for (uint32_t i = 0; i < size * 2; i++) {
            int index = i % 2;
            float out1 = do_filter_lh(this->filters[index].lowpass, samples[i]);
            float out2 = do_filter_lh(this->filters[index].highpass, samples[i]);
            float out3 = do_filter_bp(this->filters[index].bandpass, samples[i]);
            samples[i] = out2;
            lpBuf[i] = out1;
            bpBuf[i] = out3;
        }
        // The low/band-pass branches are read back delayed (group-delay alignment) while the
        // high-pass branch is the live, in-place signal in `samples`.
        float *bpOut = this->buffers[0]->GetBuffer();
        float *lpOut = this->buffers[1]->GetBuffer();
        for (uint32_t i = 0; i < size * 2; i++) {
            // out = highpass * gain * 1.2 + bandpass * gain + lowpass.
            // 1.2 is the original's Q25 constant 0x2666666 (40265318 / 2^25 = 1.2).
            float hp = samples[i] * this->gain * 1.2f;
            float bp = bpOut[i] * this->gain;
            samples[i] = hp + bp + lpOut[i];
        }
        this->buffers[0]->PopSamples(size, false);
        this->buffers[1]->PopSamples(size, false);
    }
}

void HiFi::Reset() {
    for (uint32_t i = 0; i < 2; i++) {
        // Crossover at 120 Hz / 1200 Hz (0x42f00000 = 120.0, 0x44960000 = 1200.0).
        this->filters[i].lowpass->setLPF(120.0, this->samplingRate);
        this->filters[i].lowpass->Mute();
        this->filters[i].highpass->setHPF(1200.0, this->samplingRate);
        this->filters[i].highpass->Mute();
        this->filters[i].bandpass->setBPF(120.f, 1200.f, this->samplingRate);
        this->filters[i].bandpass->Mute();
    }
    // Pre-fill the delay lines to align the band-pass/low-pass branches with the high-pass
    // branch. The original computes (double)samplingRate / 1e9 * k truncated to an integer:
    //   band-pass: k = 2.5e6 -> samplingRate / 400  (0x...ce68 = 2500000.0, 0x...ce60 = 1e9)
    //   low-pass:  k = 5.0e6 -> samplingRate / 200  (0x...ce70 = 5000000.0)
    this->buffers[0]->Reset();
    this->buffers[0]->PushZeros(this->samplingRate / 400);
    this->buffers[1]->Reset();
    this->buffers[1]->PushZeros(this->samplingRate / 200);
}

void HiFi::SetClarity(float value) {
    // The original stores round(value * 2^25) as a Q25 coefficient (0x4c000000 = 2^25);
    // this float port keeps the linear value, applied in Process().
    this->gain = value;
}

void HiFi::SetSamplingRate(uint32_t samplingRate) {
    this->samplingRate = samplingRate;
    Reset();
}
