#ifndef DUTMODEL_CPUSTATE_HPP
#define DUTMODEL_CPUSTATE_HPP

#include <refmodels/spike/spike_state.hpp>
#include <utils/elf_loader.hpp>

#include <vector>
#include <string>
#include <deque>
#include <cstdint>

namespace emulator {
  #define ITRACE_MAX_SIZE 60
  #define MTRACE_MAX_SIZE 30
  #define FTRACE_MAX_SIZE 30

  struct InstInfo {
    unsigned long long pc;
    unsigned long long inst;
    std::string disasm_string;
  };

  #define MAX_FETCH_WIDTH 8
  extern std::vector<int> supported_csr_ids;
  extern size_t commit_nums[MAX_HART_ID + 1];
  extern unsigned long long commit_pcs[MAX_HART_ID + 1][MAX_FETCH_WIDTH];
  extern unsigned long long dut_gprs[MAX_HART_ID + 1][32];
  extern std::vector<uint8_t> dut_memory;
  extern std::vector<ElfSymInfo> dut_symtab;
  extern std::vector<ElfSymInfo> dut_dynsymtab;

  extern std::unordered_map<size_t, unsigned long long> csrs[MAX_HART_ID + 1];
  extern std::unordered_map<size_t, unsigned long long> rats[MAX_HART_ID + 1];

  extern std::unordered_map<std::string, std::vector<unsigned long long> > srams[MAX_HART_ID + 1];
  extern std::unordered_map<std::string, std::vector<unsigned long long> > lut_rams[MAX_HART_ID + 1];

  extern std::deque<InstInfo> itrace, mtrace, ftrace;

  void print_itrace();
  void print_mtrace();
  void print_ftrace();
  void print_bpu_stats();
  void print_rats();
  void print_cache_stats();

  static constexpr const char *regnames[32] = {
    "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
    "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
    "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
    "s8", "s9", "s10","s11", "t3", "t4", "t5", "t6"
  };

  
}

#endif