#ifndef REFMODELS_SPIKE_WRAPPER_HPP
#define REFMODELS_SPIKE_WARPPER_HPP

#include <string>

namespace emulator {
  // use dlopen to get these functions
  struct spike_wrapper {
    void (*spike_create)(const std::string& filepath) = nullptr;
    void (*spike_delete)() = nullptr;
    void (*spike_set_isa)(const std::string& isa_str) = nullptr;
    void (*spike_set_dram_base)(unsigned long long base) = nullptr;
    void (*spike_set_dram_size)(unsigned long long size) = nullptr;
    void (*spike_set_pc)(unsigned long long pc) = nullptr;
    int (*spike_step)(int hart_id, size_t n) = nullptr;
    std::string (*spike_disasm)(unsigned long long insn_bits);

    unsigned long long (*spike_get_pc)(int hart_id) = nullptr;
    unsigned long long (*spike_get_csr)(int hart_id, int csr_id) = nullptr;
    int (*spike_get_gprs)(int hartid, unsigned long long gprs[], 
      int gpr_num) = nullptr;
    unsigned long long (*spike_get_vl)(int hartid) = nullptr;
    unsigned long long (*spike_get_vtype)(int hartid) = nullptr;
    unsigned long long (*read_spike_mem)(unsigned long long va);

    using spike_create_func_t = decltype(spike_create);
    using spike_delete_func_t = decltype(spike_delete);
    using spike_set_isa_func_t = decltype(spike_set_isa);
    using spike_set_dram_base_func_t = decltype(spike_set_dram_base);
    using spike_set_dram_size_func_t = decltype(spike_set_dram_size);
    using spike_set_pc_func_t = decltype(spike_set_pc);
    using spike_step_func_t = decltype(spike_step);

    using spike_get_pc_func_t = decltype(spike_get_pc);
    using spike_get_csr_func_t = decltype(spike_get_csr);
    using spike_get_gprs_func_t = decltype(spike_get_gprs);
    using spike_get_vl_func_t = decltype(spike_get_vl);
    using spike_get_vtype_func_t = decltype(spike_get_vtype);
    using read_spike_mem_funct_t = decltype(read_spike_mem);

    using spike_disasm_funct_t = decltype(spike_disasm);
  };

  void init_spike(const std::string& filepath);
  void deinit_spike();
  void spike_create(const std::string& program_path);

  void set_spike_isa(const std::string& isa_str);
  void set_spike_dram_base(unsigned long long base);
  void set_spike_dram_size(unsigned long long size);
  void set_spike_pc(unsigned long long pc);
  int spike_step(int hart_id, size_t n);

  unsigned long long get_spike_pc(int hart_id);
  unsigned long long get_spike_csr(int hart_id, int csr_id);
  unsigned long long get_spike_gprs(int hart_id, unsigned long long gprs[], int gpr_num);
  unsigned long long read_spike_mem(unsigned long long va);
  unsigned long long get_spike_vl(int hart_id);
  unsigned long long get_spike_vtype(int hart_id);

  std::string spike_disasm(unsigned long long insn_bits);
}

#endif