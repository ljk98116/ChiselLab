#ifndef VERILATOR_MODEL_DPIC_HPP_
#define VERILATOR_MODEL_DPIC_HPP_

#include <unordered_map>

#define MAX_HART_ID 3
#define VISABLE __attribute__((visibility("default")))

extern "C" {
  extern unsigned long long dut_pcs[MAX_HART_ID + 1];
  extern unsigned long long gprs[MAX_HART_ID + 1][32];
  extern std::unordered_map<size_t, unsigned long long> csrs[MAX_HART_ID + 1];
  extern std::unordered_map<size_t, size_t> rats[MAX_HART_ID + 1];

  VISABLE unsigned long long get_dut_pc(int hart_id);
  VISABLE unsigned long long get_dut_gprs(int hart_id, unsigned long long gprs[], int gpr_num);
  VISABLE unsigned long long get_dut_csr(int hart_id, int csr_id);
  VISABLE unsigned long long get_dut_mem(unsigned long long va);
  VISABLE int get_dut_rat(int hart_id, std::unordered_map<size_t, size_t>& dut_rat);
  VISABLE int dut_clock_step();


}

#endif // VERILATOR_MODEL_DPIC_HPP_