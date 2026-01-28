#pragma once

#include <cstdint>
#include <vector>
#include <array>
#include <map>

class ViPERDDC {
public:
    ViPERDDC(uint32_t samplingRate);

    void Process(float *samples, uint32_t size);
    void Reset();
    void ClearCoeffs();
    void AddCoeffs(uint32_t rate, const std::vector<std::array<float, 5>>& coeffs);
    void SetEnable(bool enable);
    void SetSamplingRate(uint32_t samplingRate);

private:
    bool enable = false;
    bool setCoeffsOk;
    uint32_t samplingRate;
    uint32_t arrSize;
    std::map<uint32_t, std::vector<std::array<float, 5>>> coeffsMap;
    std::vector<float> x1L;
    std::vector<float> x1R;
    std::vector<float> x2L;
    std::vector<float> x2R;
    std::vector<float> y1L;
    std::vector<float> y1R;
    std::vector<float> y2L;
    std::vector<float> y2R;

    void ReleaseResources();
    bool isSamplingRateValid() const;
};


