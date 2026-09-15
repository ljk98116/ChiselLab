#ifndef REFMODEL_SPIKE_STATE_HPP
#define REFMODEL_SPIKE_STATE_HPP

#include <unordered_map>

namespace emulator {
  #define MAX_HART_ID 3
  extern unsigned long long spike_pc_[MAX_HART_ID + 1];
  extern unsigned long long spike_gprs_[MAX_HART_ID + 1][32];
  extern std::unordered_map<std::size_t, unsigned long long> spike_csrs_[MAX_HART_ID + 1];
}

#endif