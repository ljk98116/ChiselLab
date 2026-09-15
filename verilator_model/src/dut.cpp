#include <dpic.hpp>

#include <svdpi.h>
#include <cassert>
#include <cstring>

size_t commit_nums[MAX_HART_ID + 1];
unsigned long long commit_pcs[MAX_HART_ID + 1][MAX_FETCH_WIDTH];
unsigned long long dut_gprs[MAX_HART_ID + 1][32];
std::vector<uint8_t> dut_memory;

std::unordered_map<size_t, unsigned long long> csrs[MAX_HART_ID + 1];
std::unordered_map<size_t, unsigned long long> rats[MAX_HART_ID + 1];

std::unordered_map<std::string, std::vector<unsigned long long> > srams[MAX_HART_ID + 1];
std::unordered_map<std::string, std::vector<unsigned long long> > lut_rams[MAX_HART_ID + 1];

extern "C" void update_sram(int hart_id, const char *name, unsigned long long addr, unsigned long long mask, unsigned long long content) {
  try {
    assert(hart_id < MAX_HART_ID);
    srams[hart_id][std::string(name)][addr] = (srams[hart_id][std::string(name)][addr] & ~mask) | (content & mask);
  }
  catch(std::exception& e) {
    printf("update %dth sram failed at %s with addr 0x%llx, because %s\n", hart_id, name, addr, e.what());
  }
}

extern "C" void update_lutram(int hart_id, const char *name, unsigned long long addr, unsigned long long mask, unsigned long long content) {
  try {
    lut_rams[hart_id][std::string(name)][addr] = (lut_rams[hart_id][std::string(name)][addr] & ~mask) | (content & mask);
  }
  catch(std::exception& e) {
    printf("update %dth lutram failed at %s with addr 0x%llx, because %s\n", hart_id, name, addr, e.what());
  }    
}

// save commit pcs and notify emulator to do final diff check
extern "C" void commit(int hart_id, unsigned long long pcs[], int num) {
  for(int i=0;i<num;++i) {
    commit_pcs[hart_id][i] = pcs[i];
  }
  commit_nums[hart_id] = num;
}

extern "C" void sram_read(int hart_id, const char *name, const svBitVecVal *va, const svBitVecVal *mask, svBitVecVal *result) {
  uint64_t real_va = (((uint64_t)va[1]) << 32) | va[0];
  uint64_t real_mask = (((uint64_t)mask[1]) << 32) | mask[0];
  uint64_t retValue = 0;
  // mapping to pa
  if(strcmp(name, "memory") == 0) {
    assert(real_va >= 0x80000000);
    uint64_t pa = real_va - 0x80000000;
    assert(pa < dut_memory.size());
    retValue = dut_memory[pa] & real_mask;
  }
  else {
    retValue = srams[hart_id][name][real_va];
  }
  result[0] = (svBitVecVal)(retValue & 0xFFFFFFFF);
  result[1] = (svBitVecVal)(retValue >> 32);
}

extern "C" void sram_write(int hart_id, const char *name, const svBitVecVal *va, const svBitVecVal *mask, const svBitVecVal *wdata) {
  uint64_t real_va = (((uint64_t)va[1]) << 32) | va[0];
  uint64_t real_mask = (((uint64_t)mask[1]) << 32) | mask[0];
  uint64_t real_wdata = (((uint64_t)wdata[1]) << 32) | wdata[0];
  if(strcmp(name, "memory") == 0) {
    assert(real_va >= 0x80000000);
    unsigned long long pa = real_va - 0x80000000;
    assert(pa < dut_memory.size());
    dut_memory[pa] = (dut_memory[pa] & ~real_mask) | (real_wdata & real_mask);
  }
  else {
    srams[hart_id][name][real_va] = (srams[hart_id][name][real_va] & ~real_mask) | (real_wdata & real_mask);
  }
}

extern "C" void sram_register(int hart_id, const char *name, const svBitVecVal *value, const svBitVecVal *size) {
  if(strcmp(name, "memory") != 0 && hart_id <= MAX_HART_ID) {
    uint64_t real_value = (((uint64_t)value[1]) << 32) | value[0];
    uint64_t real_size = (((uint64_t)size[1]) << 32) | size[0];
    srams[hart_id][name].reserve(real_size);
    memset(srams[hart_id][name].data(), real_value, sizeof(uint64_t) * real_size);
  }
}

extern "C" void lutram_read(int hart_id, const char *name, const svBitVecVal *va, const svBitVecVal *mask, svBitVecVal *result) {
  uint64_t real_va = (((uint64_t)va[1]) << 32) | va[0];
  uint64_t real_mask = (((uint64_t)mask[1]) << 32) | mask[0];
  uint64_t retValue = 0;
  // mapping to pa
  if(strcmp(name, "memory") != 0){
    retValue = lut_rams[hart_id][name][real_va];
  }
  result[0] = (svBitVecVal)(retValue & 0xFFFFFFFF);
  result[1] = (svBitVecVal)(retValue >> 32);
}

extern "C" void lutram_write(int hart_id, const char *name, const svBitVecVal *va, const svBitVecVal *mask, const svBitVecVal *wdata) {
  uint64_t real_va = (((uint64_t)va[1]) << 32) | va[0];
  uint64_t real_mask = (((uint64_t)mask[1]) << 32) | mask[0];
  uint64_t real_wdata = (((uint64_t)wdata[1]) << 32) | wdata[0];
  if(strcmp(name, "memory") != 0) {
    lut_rams[hart_id][name][real_va] = (lut_rams[hart_id][name][real_va] & ~real_mask) | (real_wdata & real_mask);
  }
}

extern "C" void lutram_register(int hart_id, const char *name, const svBitVecVal *value, const svBitVecVal *size) {
  if(strcmp(name, "memory") != 0 && hart_id <= MAX_HART_ID) {
    uint64_t real_value = (((uint64_t)value[1]) << 32) | value[0];
    uint64_t real_size = (((uint64_t)size[1]) << 32) | size[0];
    lut_rams[hart_id][name].reserve(real_size);
    memset(lut_rams[hart_id][name].data(), real_value, sizeof(uint64_t) * real_size);
  }  
}