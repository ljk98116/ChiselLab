#include <refmodels/spike/spike_state.hpp>

namespace emulator {
  unsigned long long spike_pc_;
  unsigned long long spike_gprs_[MAX_HART_ID + 1][32];
  std::unordered_map<std::size_t, unsigned long long> spike_csrs_[MAX_HART_ID + 1];
}