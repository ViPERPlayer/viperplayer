#pragma once

#include <cstdint>
#include <array>

// Iscle: Verified with the latest version at 13/12/2022

class TubeSimulator {
public:
    void Reset();
    void SetEnable(bool enable);
    void TubeProcess(float *buffer, uint32_t size);

private:
    std::array<double, 2> acc = { 0, 0 };
    bool enable = false;
};

