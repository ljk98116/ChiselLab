#include <dlfcn.h>
#include <iostream>

#include <common/state.hpp>
#include <refmodels/spike/spike_wrapper.hpp>

namespace emulator {
  static spike_wrapper spike_wrapper_;
  static void *spike_handle = nullptr;


  void init_spike(const std::string& filepath) {
    spike_handle = dlopen(filepath.c_str(), RTLD_LAZY);
    if (!spike_handle) {
      std::cout << "dlopen error: " << dlerror() << std::endl;
      return;
    }    
    dlerror();
    spike_wrapper_.spike_create = 
      (spike_wrapper::spike_create_func_t)dlsym(spike_handle, "spike_create");  
    spike_wrapper_.spike_delete = 
      (spike_wrapper::spike_delete_func_t)dlsym(spike_handle, "spike_delete");
    spike_wrapper_.spike_set_isa = 
      (spike_wrapper::spike_set_isa_func_t)dlsym(spike_handle, "spike_set_isa");
    spike_wrapper_.spike_set_dram_base = 
      (spike_wrapper::spike_set_dram_base_func_t)dlsym(spike_handle, "spike_set_dram_base");
    spike_wrapper_.spike_set_dram_size = 
      (spike_wrapper::spike_set_dram_size_func_t)dlsym(spike_handle, "spike_set_dram_size");
    spike_wrapper_.spike_set_pc = 
      (spike_wrapper::spike_set_pc_func_t)dlsym(spike_handle, "spike_set_pc");
    spike_wrapper_.spike_step = 
      (spike_wrapper::spike_step_func_t)dlsym(spike_handle, "spike_step");
    
    spike_wrapper_.spike_get_pc = 
      (spike_wrapper::spike_get_pc_func_t)dlsym(spike_handle, "spike_get_pc");
    spike_wrapper_.spike_get_csr = 
      (spike_wrapper::spike_get_csr_func_t)dlsym(spike_handle, "spike_get_csr");
    spike_wrapper_.spike_get_gprs = 
      (spike_wrapper::spike_get_gprs_func_t)dlsym(spike_handle, "spike_get_gprs");
    spike_wrapper_.spike_get_vl = 
      (spike_wrapper::spike_get_vl_func_t)dlsym(spike_handle, "spike_get_vl");
    spike_wrapper_.spike_get_vtype = 
      (spike_wrapper::spike_get_vtype_func_t)dlsym(spike_handle, "spike_get_vtype");
    spike_wrapper_.read_spike_mem = 
      (spike_wrapper::read_spike_mem_funct_t)dlsym(spike_handle, "read_spike_mem");
    spike_wrapper_.spike_disasm = 
      (spike_wrapper::spike_disasm_funct_t)dlsym(spike_handle, "spike_disasm");

  }

  void deinit_spike() {
    if(spike_wrapper_.spike_delete) {
      spike_wrapper_.spike_delete();
    }
    if(spike_handle) {
      dlclose(spike_handle);
    }
  }

  void spike_create(const std::string& program_path) {
    std::cout << program_path << std::endl;
    if(spike_wrapper_.spike_create) {
      spike_wrapper_.spike_create(program_path);
    }
    auto insn = read_spike_mem(0x80000000);
    std::cout << "First inst is:" << spike_disasm(insn) << std::endl;
  }

  void spike_delete() {
    if(spike_wrapper_.spike_delete) {
      spike_wrapper_.spike_delete();
    }
  }
  
  void set_spike_isa(const std::string& isa_str) {
    spike_wrapper_.spike_set_isa(isa_str);
  }

  void set_spike_dram_base(unsigned long long base) {
    spike_wrapper_.spike_set_dram_base(base);
  }

  void set_spike_dram_size(unsigned long long size) {
    spike_wrapper_.spike_set_dram_size(size);
  }

  void set_spike_pc(unsigned long long pc) {
    spike_wrapper_.spike_set_pc(pc);
  }

  int spike_step(int hart_id, size_t n) {
    return spike_wrapper_.spike_step(hart_id, n);
  }

  unsigned long long get_spike_pc(int hart_id) {
    return spike_wrapper_.spike_get_pc(hart_id);
  }

  unsigned long long get_spike_csr(int hart_id, int csr_id) {
    return spike_wrapper_.spike_get_csr(hart_id, csr_id);
  }

  unsigned long long get_spike_gprs(int hart_id, unsigned long long gprs[], int gpr_num) {
    return spike_wrapper_.spike_get_gprs(hart_id, gprs, gpr_num);
  }

  unsigned long long get_spike_vl(int hart_id) {
    return spike_wrapper_.spike_get_vl(hart_id);
  }

  unsigned long long get_spike_vtype(int hart_id) {
    return spike_wrapper_.spike_get_vtype(hart_id);
  }

  unsigned long long read_spike_mem(unsigned long long va) {
    return spike_wrapper_.read_spike_mem(va);
  }
  
  std::string spike_disasm(unsigned long long insn_bits) {
    return spike_wrapper_.spike_disasm(insn_bits);
  }
}