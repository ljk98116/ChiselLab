#ifndef VERILATOR_MODEL_HPP
#define VERILATOR_MODEL_HPP

#include <vector>
#include <unordered_map>
#include <string>
#include <condition_variable>

#define VISABLE __attribute__((visibility("default")))

extern "C" {
  VISABLE bool dut_init(int argc, char *argv[]);
  VISABLE bool dut_init_memory(unsigned char *content, size_t size);
  VISABLE bool dut_deinit();
  VISABLE bool get_dut_pc(int hart_id, unsigned long long pcs[], int& commit_num);
  VISABLE bool get_dut_gprs(int hart_id, unsigned long long gprs[], int gpr_num);
  VISABLE unsigned long long get_dut_csr(int hart_id, int csr_id);
  VISABLE bool read_dut_mem(unsigned long long va, uint64_t num, uint8_t *value);
  VISABLE bool get_dut_rat(int hart_id, std::unordered_map<size_t, unsigned long long> &dut_rat);
  VISABLE bool dut_clock_step();
  VISABLE bool dut_get_sram_content(int hart_id, const std::string& name, std::vector<unsigned long long>& sram);
}

#endif