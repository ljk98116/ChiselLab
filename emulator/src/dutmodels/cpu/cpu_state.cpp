#include <dutmodels/cpu/cpu_state.hpp>

namespace emulator {
  enum class CSRID : int32_t {
    mtvec = 0x305
  };

  std::vector<int> supported_csr_ids = {
    (int)CSRID::mtvec
  };

  size_t commit_nums[MAX_HART_ID + 1];
  unsigned long long commit_pcs[MAX_HART_ID + 1][MAX_FETCH_WIDTH];
  unsigned long long dut_gprs[MAX_HART_ID + 1][32];
  std::vector<uint8_t> dut_memory;
  std::vector<ElfSymInfo> dut_symtab;
  std::vector<ElfSymInfo> dut_dynsymtab;

  std::unordered_map<size_t, unsigned long long> csrs[MAX_HART_ID + 1];
  std::unordered_map<size_t, unsigned long long> rats[MAX_HART_ID + 1];

  std::unordered_map<std::string, std::vector<unsigned long long> > srams[MAX_HART_ID + 1];
  std::unordered_map<std::string, std::vector<unsigned long long> > lut_rams[MAX_HART_ID + 1];
  std::deque<InstInfo> itrace, mtrace, ftrace;
}