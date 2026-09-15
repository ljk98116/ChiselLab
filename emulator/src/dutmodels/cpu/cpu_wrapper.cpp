#include <dutmodels/cpu/cpu_wrapper.hpp>
#include <dutmodels/cpu/cpu_state.hpp>
#include <refmodels/spike/spike_wrapper.hpp>

#include <common/state.hpp>

#include <cassert>
#include <string>
#include <dlfcn.h>
#include <iostream>
#include <cstring>
#include <unistd.h>
#include <chrono>
#include <memory>

namespace emulator {

  static std::unique_ptr<CPUWrapper> cpu_wrapper_ = nullptr;
  static void *cpu_handle = nullptr;

  #define GETDUTSYM(name) \
    cpu_wrapper_->name = (CPUWrapper::name##_func_t)dlsym(cpu_handle, #name)

  CPUWrapper::CPUWrapper(int argc, char *argv[]): stop_(false) {
    assert(argc > 1);
  }

  CPUWrapper::~CPUWrapper() {
    exec_ = false;
    stop_ = true;
    if(cpu_thread_.joinable()) {
      cpu_thread_.join();
    }
  }

  void CPUWrapper::Start() {
    std::thread t([this]() {
      CPUThread(this);
    });
    cpu_thread_ = std::move(t);
    exec_ = true;
  }

  bool dut_init(int argc, char *argv[]) {
    if(cpu_wrapper_ == nullptr) {
      cpu_wrapper_ = std::make_unique<CPUWrapper>(argc, argv);
      std::string dut_model_path = std::string(argv[1]);
      cpu_handle = dlopen(dut_model_path.c_str(), RTLD_LAZY);
      if (!cpu_handle) {
        std::cout << "dlopen error: " << dlerror() << std::endl;
        return false;
      }    
      dlerror(); 
      GETDUTSYM(dut_init);
      GETDUTSYM(dut_init_memory);
      GETDUTSYM(dut_deinit);
      GETDUTSYM(dut_deinit);
      GETDUTSYM(get_dut_gprs);
      GETDUTSYM(get_dut_csr);
      GETDUTSYM(read_dut_mem);
      GETDUTSYM(get_dut_rat);
      GETDUTSYM(dut_clock_step);
      GETDUTSYM(dut_get_sram_content);
      GETDUTSYM(get_dut_commit_infos);
    }
    if(cpu_wrapper_->dut_init) {
      return cpu_wrapper_->dut_init(argc, argv);
    }
    return false;
  }

  bool dut_init_memory(unsigned char *content, size_t size) {
    if(cpu_wrapper_->dut_init_memory) {
      return cpu_wrapper_->dut_init_memory(content, size);
    }
    return false;
  }
  
  void dut_start() {
    if(cpu_wrapper_) {
      cpu_wrapper_->Start();
    }
    else std::cout << "[ERROR]: dut_start called before dut_init" << std::endl;
  }

  bool dut_status() {
    if(cpu_wrapper_) {
      return cpu_wrapper_->Status();
    }
    return false;
  }
  
  bool dut_deinit() {
    if(cpu_wrapper_->dut_deinit) {
      return cpu_wrapper_->dut_deinit();
    }
    if(cpu_handle) {
      dlclose(cpu_handle);
    }
    return false;
  }

  bool get_dut_gprs(int hart_id, unsigned long long gprs[], int gpr_num) {
    if(cpu_wrapper_->get_dut_gprs) {
      return cpu_wrapper_->get_dut_gprs(hart_id, gprs, gpr_num);
    }
    return false;
  }

  unsigned long long get_dut_csr(int hart_id, int csr_id) {
    if(cpu_wrapper_->get_dut_csr) {
      return cpu_wrapper_->get_dut_csr(hart_id, csr_id);
    }
    return 0;
  } 

  bool read_dut_mem(unsigned long long va, uint64_t num, uint8_t *value) {
    if(cpu_wrapper_->read_dut_mem) {
      return cpu_wrapper_->read_dut_mem(va, num, value);
    }
    return false;
  }

  bool get_dut_rat(int hart_id, std::unordered_map<size_t, unsigned long long> &dut_rat) {
    if(cpu_wrapper_->get_dut_rat) {
      return cpu_wrapper_->get_dut_rat(hart_id, dut_rat);
    }
    return false;
  }

  bool dut_clock_step() {
    if(cpu_wrapper_->dut_clock_step) {
      return cpu_wrapper_->dut_clock_step();
    }
    return false;
  }

  bool dut_get_sram_content(int hart_id, const std::string& name, std::vector<unsigned long long>& sram) {
    if(cpu_wrapper_->dut_get_sram_content) {
      return cpu_wrapper_->dut_get_sram_content(hart_id, name, sram);
    } 
    return false;    
  }

  static bool IsMemInsn(uint64_t insn) {
    return false;
  }

  static bool IsCallRetInsn(uint64_t insn) {
    return false;
  }

  static void print_dut_gprs() {

  }

  static void print_ref_gprs() {

  }

  void CPUWrapper::CPUThread(void *ptr) {
    CPUWrapper *self = reinterpret_cast<CPUWrapper*>(ptr);
    if(!self) {
      std::cout << "[ERROR]: CPUWrapper::CPUThread get nullptr self" << std::endl;
      return;
    }
    int steps_after_last_commit = 0;
    bool halt = false;
    while(!self->stop_ && !halt) {
      if(!self->exec_) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        continue;
      }
      // current emu only supports 1 core
      if(self->get_dut_commit_infos) {
        self->get_dut_commit_infos(0, commit_nums[0], commit_pcs[0]);
      }
      
      if(commit_nums[0] > 0) {
        steps_after_last_commit = 0;
      }

      // update commit states
      for(auto& csr_id: supported_csr_ids) {
        if(self->get_dut_csr == nullptr) {
          continue;
        }
        auto csr = self->get_dut_csr(0, csr_id);
        csrs[0][csr_id] = csr;
      }

      if(self->get_dut_gprs) {
        self->get_dut_gprs(0, dut_gprs[0], 32);
      }
      if(self->get_dut_rat) {
        self->get_dut_rat(0, rats[0]);
      }

      InstInfo infos[MAX_FETCH_WIDTH];
      memset(infos, 0, sizeof(InstInfo) * MAX_FETCH_WIDTH);
      // record traces
      for(int i = 0; i < commit_nums[0]; ++i) {
        uint64_t insn = 0;
        if(self->read_dut_mem) {
          self->read_dut_mem(commit_pcs[0][i], 4, (uint8_t*)&insn);
        }
        infos[i].pc = commit_pcs[0][i];
        infos[i].inst = insn;
        infos[i].disasm_string = spike_disasm(insn);

        if(itrace.size() == ITRACE_MAX_SIZE) {
          itrace.pop_front();
        }
        itrace.push_back(infos[i]);
        // to do, mtrace and ftrace

      }

      // call ref model while in difftest mode
      // check error then fall into halt
      if(emu_mode_ == Mode::DIFF_TEST) {
        bool fail = false;
        // check pc equal
        for(int i = 0; i < commit_nums[0] && !fail; ++i) {
          spike_step(0, 1);
          auto spike_pc = get_spike_pc(0);
          if(spike_pc != commit_pcs[0][i]) {
            printf("[ERROR]: difftest fail because pcs are different: [ref/dut] is 0x%llx/0x%llx\n", 
              spike_pc, commit_pcs[0][i]);
            fail = true;
          }
        }
        // check gpr equal
        get_spike_gprs(0, spike_gprs_[0], 32);
        if(memcmp(spike_gprs_[0], dut_gprs[0], sizeof(uint64_t) * 32) != 0) {
          printf("[ERROR]: difftest fail because gprs are different\n");
          printf("REF SPIKE GPRS:\n");
          print_ref_gprs();
          printf("DUT GPRS:\n");
          print_dut_gprs();
          fail = true;          
        }

        // check csrs equal
        for(auto& csr_id : supported_csr_ids) {
          auto dut_value = csrs[0][csr_id];
          auto spike_value = get_spike_csr(0, csr_id);
          if(dut_value != spike_value) {
            fail = true;
            printf("[ERROR]: difftest failed at csr check: ref is 0x%llx, dut is 0x%llx\n",
              spike_value, dut_value
            );
            break;
          }
        }
        if(fail) {
          halt = true;
        }
      }

      self->dut_clock_step();
      ++steps_after_last_commit;

      // clear commit states
      commit_nums[0] = 0;
      memset(commit_pcs[0], MAX_FETCH_WIDTH, 0);

      // commit timeout, exit from current program
      if(steps_after_last_commit > 100) {
        halt = true;
        std::cout << "[ERROR]: commit timeout for 100 clock steps" << std::endl;
      }      
    }

    // print last commited pcs and insts
    // print regs
    // print stats
    // print memtrace, itrace, ftrace
    self->exec_ = false;
    if(halt) {
      std::cout << "[ERROR]: something wrong happened, halt current program..." << std::endl;
      return;
    }
    std::cout << "[INFO]: DUT Model running finished" << std::endl;
  }
}
