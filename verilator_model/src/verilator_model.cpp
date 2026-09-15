#include <verilator_model.hpp>
#include <dpic.hpp>
#include <cassert>

#include <verilated.h>
#ifdef SIMFLAG
#include <verilated_fst_c.h>
#endif
#include <bits/ranges_base.h>

#include "VMyCPU.h"
#include <iostream>

class MyCPUTester {
  public:
    explicit MyCPUTester(const char *fst_file) {
      dut = new VMyCPU;
#ifdef SIMFLAG
      Verilated::traceEverOn(true);
      tfp = new VerilatedFstC;
      dut->trace(tfp, 10);
      tfp->open(fst_file);
      assert(tfp->isOpen());
#endif
    }

    ~MyCPUTester() {
#ifdef SIMFLAG
      if(tfp) {
        tfp->close();
      }
      delete tfp;
#endif
      delete dut;
    }

    void tick() {
      dut->clock = 0; dut->eval();
#ifdef SIMFLAG
      tfp->dump(tm++);
#endif
      dut->clock = 1; dut->eval();
#ifdef SIMFLAG
      tfp->dump(tm++);
#endif
      ++period;
    }

    unsigned long long get_period() const {
      return period;
    }

    void set_stop_flag(bool value) {
      stop_flag = value;
    }

    bool get_stop_flag() const {
      return stop_flag;
    }

  private:
#ifdef SIMFLAG
    VerilatedFstC *tfp;
#endif
    uint64_t tm;
    unsigned long long period;
    bool stop_flag = false;
    VMyCPU *dut;
};

static MyCPUTester *tester = nullptr;

extern "C" {
  VISABLE bool get_dut_pc(int hart_id, unsigned long long pcs[], int& commit_num) {
    if(hart_id > MAX_HART_ID) {
      return false;
    }
    for(int i=0;i<commit_nums[hart_id];++i) {
      pcs[i] = commit_pcs[hart_id][i];
    }
    commit_num = commit_nums[hart_id];
    return true;
  }

  VISABLE bool dut_init(int argc, char *argv[]) {
    Verilated::commandArgs(argc, argv);
    tester = new MyCPUTester("mycpu_waveform.fst");
    std::cout << "DUT init with so: " << std::string(argv[1]) << std::endl;
    memset(commit_pcs, 0, sizeof(unsigned long long) * (MAX_HART_ID + 1) * MAX_FETCH_WIDTH);
    memset(commit_nums, 0, sizeof(size_t) * (MAX_HART_ID + 1));
    memset(dut_gprs, 0, sizeof(unsigned long long) * (MAX_HART_ID + 1) * 32);
    return true;
  }

  VISABLE bool dut_init_memory(unsigned char *content, size_t size) {
    std::cout << "DUT init memory with size: " << size << std::endl;
    dut_memory.resize(size);
    for(int i = 0; i < size; ++i) {
      dut_memory[i] = content[i];
    }
    std::cout << "DUT memory initialized with size: " << dut_memory.size() << std::endl;
    return true;
  }

  VISABLE bool dut_deinit() {
    if(tester != nullptr) {
      delete tester;
    }
    return true;
  }

  VISABLE bool get_dut_gprs(int hart_id, unsigned long long gprs[], int gpr_num) {
    if(hart_id > MAX_HART_ID) {
      return false;
    }
    for(int i=0;i<gpr_num;++i) {
      gprs[i] = dut_gprs[hart_id][i];
    }
    return true;
  }

  VISABLE unsigned long long get_dut_csr(int hart_id, int csr_id) {
    if(hart_id > MAX_HART_ID || csrs[hart_id].find(csr_id) == csrs[hart_id].end()) {
      return 0;
    }
    return csrs[hart_id][csr_id];
  }

  VISABLE bool read_dut_mem(unsigned long long va, uint64_t num, uint8_t *value) {
    // mapping to pa
    if(va < 0x80000000ULL || va >= 0x80000000ULL + dut_memory.size()) {
      std::cout << "[ERROR]: read dut mem at va: 0x" << std::hex << va << std::dec << 
        " is out of range, dut memory size is " << dut_memory.size() << std::endl;
      return false;
    }
    unsigned long long pa = va - 0x80000000ULL;
    std::cout << "read dut mem at va: 0x" << 
      std::hex << va << ", pa: 0x" << pa << std::dec << ", size: " << num <<
      ",dut memory size:"<< dut_memory.size() << std::endl;
    for(int i = 0; i < num; ++i) {
      value[i] = dut_memory[pa + i];
    }
    return true;
  }

  VISABLE bool get_dut_rat(int hart_id, std::unordered_map<size_t, unsigned long long>& dut_rat) {
    if(hart_id >= MAX_HART_ID) {
      return false;
    }
    dut_rat = rats[hart_id];
    return true;
  }

  VISABLE bool dut_clock_step() {
    if(tester == nullptr) {
      return false;
    }
    tester->tick();
    return true;
  }

  VISABLE bool dut_get_sram_content(
    int hart_id, const std::string& name, 
    std::vector<unsigned long long>& sram
  ) {
    if(hart_id >= MAX_HART_ID || srams[hart_id].find(name) == srams[hart_id].end()) {
      return false;
    }
    sram = srams[hart_id][name];
    return true;
  }

}