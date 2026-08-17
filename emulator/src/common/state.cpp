#include <common/state.hpp>

namespace emulator {
  State emu_state_ = State::RUNNING;
  Mode emu_mode_ = Mode::EMU;
  ProgState emu_prog_state_ = ProgState::NONE;
  ProgMode emu_prog_mode_ = ProgMode::DEFAULT;
  DUTState dut_state_ = DUTState::NONE;
  ARCHType ref_arch_type_ = ARCHType::RV64I;

  std::string toString(const State& state) {
    switch(state) {
      case State::RUNNING: return "RUNNING";
      case State::HALTED: return "HALTED";
      case State::EXITED: return "EXITED";
    }
    return "UNKNOWN";
  }

  std::string toString(const Mode& mode) {
    switch(mode) {
      case Mode::EMU: return "EMU";
      case Mode::DIFF_TEST: return "DIFF_TEST";
    }
    return "UNKNOWN";
  }

  std::string toString(const ProgState& state) {
    switch(state) {
      case ProgState::NONE: return "NONE";
      case ProgState::LOADED: return "LOADED";
    }
    return "UNKNOWN";
  }

  std::string toString(const ProgMode& mode) {
    switch(mode) {
      case ProgMode::DEFAULT: return "DEFAULT";
      case ProgMode::GRAPH: return "GRAPH";
    }
    return "UNKNOWN";
  }

  std::string toString(const DUTState& state) {
    switch(state) {
      case DUTState::NONE: return "NONE";
      case DUTState::LOADED: return "LOADED";
    }
    return "UNKNOWN";
  }

  std::string toString(const ARCHType& type) {
    switch(type) {
      case ARCHType::X86: return "X86";
      case ARCHType::X64: return "X64";
      case ARCHType::RV32E: return "RV32E";
      case ARCHType::RV32I: return "RV32I";
      case ARCHType::RV32IM: return "RV32IM";
      case ARCHType::RV32IMC: return "RV32IMC";
      case ARCHType::RV64I: return "RV64I";
    }
    return "UNKNOWN";
  }
}