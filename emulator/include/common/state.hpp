#ifndef COMMON_STATE_HPP
#define COMMON_STATE_HPP

#include <cstdint>
#include <string>

namespace emulator {
  enum class State : int32_t {
    RUNNING = 0,
    HALTED = 1,
    EXITED = 2
  };

  enum class Mode: int32_t {
    EMU = 0,
    DUT_ONLY = 1,
    DIFF_TEST = 2,
  };

  enum class ProgState: int32_t {
    NONE = 0,
    LOADED = 1
  };

  enum class ProgMode: int32_t {
    DEFAULT = 0,
    GRAPH = 1
  };

  enum class DUTState: int32_t {
    NONE = 0,
    LOADED = 1
  };

  enum class ARCHType: int32_t {
    X86 = 0,
    X64 = 1,
    RV32E = 2,
    RV32I = 3,
    RV32IM = 4,
    RV32IMC = 5,
    RV64I = 6
  };

  extern State emu_state_;
  extern Mode emu_mode_;
  extern ProgState emu_prog_state_;
  extern ProgMode emu_prog_mode_;
  extern DUTState dut_state_;
  extern ARCHType ref_arch_type_;

  std::string toString(const State& state);
  std::string toString(const Mode& mode);
  std::string toString(const ProgState& state);
  std::string toString(const ProgMode& mode);
  std::string toString(const DUTState& state);
  std::string toString(const ARCHType& type);
}

#endif