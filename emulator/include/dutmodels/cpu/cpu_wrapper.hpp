#ifndef DUTMODEL_CPUWRAPPER_HPP
#define DUTMODEL_CPUWRAPPER_HPP

#include <unordered_map>
#include <condition_variable>
#include <vector>
#include <thread>
#include <atomic>

#define DECLARE_CPUWRAPPER_FUNCT_TYPE(name) \
  using name##_func_t = decltype(name)

namespace emulator {
  struct CPUWrapper {
    bool (*dut_init)(int argc, char *argv[]);
    bool (*dut_init_memory)(unsigned char *content, size_t size);
    bool (*dut_deinit)();
    bool (*get_dut_gprs)(int hart_id, unsigned long long gprs[], int gpr_num);
    unsigned long long (*get_dut_csr)(int hart_id, int csr_id);
    bool (*read_dut_mem)(unsigned long long va, uint64_t num, uint8_t *value);
    bool (*get_dut_rat)(int hart_id, std::unordered_map<size_t, unsigned long long> &dut_rat);
    bool (*dut_clock_step)();
    bool (*dut_get_sram_content)(int hart_id, const std::string& name, std::vector<unsigned long long>& sram);
    bool (*get_dut_commit_infos)(int hart_id, size_t& commit_num, unsigned long long commit_pcs[]);
    
    DECLARE_CPUWRAPPER_FUNCT_TYPE(dut_init);
    DECLARE_CPUWRAPPER_FUNCT_TYPE(dut_init_memory);
    DECLARE_CPUWRAPPER_FUNCT_TYPE(dut_deinit);
    DECLARE_CPUWRAPPER_FUNCT_TYPE(get_dut_gprs);
    DECLARE_CPUWRAPPER_FUNCT_TYPE(get_dut_csr);
    DECLARE_CPUWRAPPER_FUNCT_TYPE(read_dut_mem);
    DECLARE_CPUWRAPPER_FUNCT_TYPE(get_dut_rat);
    DECLARE_CPUWRAPPER_FUNCT_TYPE(dut_clock_step);
    DECLARE_CPUWRAPPER_FUNCT_TYPE(dut_get_sram_content);
    DECLARE_CPUWRAPPER_FUNCT_TYPE(get_dut_commit_infos);

    static void CPUThread(void *self);
    void Start();
    bool Status() const {
      return exec_;
    }
    std::thread cpu_thread_;
    std::atomic_bool stop_;
    std::atomic_bool exec_;

    CPUWrapper(int argc, char *argv[]);
    ~CPUWrapper();
    std::mutex mut_;
  };

  bool dut_init(int argc, char *argv[]);
  bool dut_init_memory(unsigned char *content, size_t size);
  void dut_start();
  bool dut_status();
  bool dut_deinit();
  bool get_dut_pc(int hart_id, unsigned long long pcs[], int& commit_num);
  bool get_dut_gprs(int hart_id, unsigned long long gprs[], int gpr_num);
  unsigned long long get_dut_csr(int hart_id, int csr_id);
  bool read_dut_mem(unsigned long long va, uint64_t num, uint8_t *value);
  bool get_dut_rat(int hart_id, std::unordered_map<size_t, unsigned long long> &dut_rat);
  bool dut_clock_step();
  bool dut_get_sram_content(int hart_id, const std::string& name, std::vector<unsigned long long>& sram);
  bool get_dut_commit_infos(int hart_id, size_t& commit_num, unsigned long long commit_pcs[]);
}

#endif