#include <iostream>
#include <cstring>

#include <cmdline/argparser.hpp>
#include <cmdline/parser.hpp>

#include <common/state.hpp>

#include <refmodels/spike/spike_wrapper.hpp>
#include <dutmodels/cpu/cpu_wrapper.hpp>
#include <dutmodels/cpu/cpu_state.hpp>

#include <utils/elf_loader.hpp>

namespace emulator {

  static struct option long_options[] = {
    {"program", required_argument, 0, 'f'}, // --program=<program name>
    {"info", no_argument, 0, 'i'},
    {"help", no_argument, 0, 'h'},
    {"refModel", required_argument, 0, 'r'},
    {"dutModel", no_argument, 0, 'd'},
    {"difftest", no_argument, 0, 't'},
    {0, 0, 0, 0}
  };

  static std::string to_spike_isa(const ARCHType& arch) {
    switch(arch) {
      case ARCHType::RV32E: return "rv32e";
      case ARCHType::RV32I: return "rv32i";
      case ARCHType::RV32IM: return "rv32im";
      case ARCHType::RV32IMC: return "rv32imc";
      case ARCHType::RV64I: return "rv64i";
      case ARCHType::X86:
      case ARCHType::X64:
      default:
        std::cout << "Unsupported Spike ISA for arch: " << toString(arch) << std::endl;
        abort();
    }
    return "rv64i";
  }

  static void set_ref_arch_state(const char *arch) {
    std::string arch_str = arch;
    if(arch_str == "X86") {
      ref_arch_type_ = ARCHType::X86;
    }
    else if(arch_str == "X64") {
      ref_arch_type_ = ARCHType::X64;
    }
    else if(arch_str == "RV32E") {
      ref_arch_type_ = ARCHType::RV32E;
    }
    else if(arch_str == "RV32I") {
      ref_arch_type_ = ARCHType::RV32I;
    }
    else if(arch_str == "RV32IM") {
      ref_arch_type_ = ARCHType::RV32IM;
    }
    else if(arch_str == "RV32IMC") {
      ref_arch_type_ = ARCHType::RV32IMC;
    }
    else if(arch_str == "RV64I") {
      ref_arch_type_ = ARCHType::RV64I;
    }
    else {
      std::cout << "Unsupported Arch, abort" << std::endl;
      abort();
    }
  }

  bool ArgParser::init_ref_model(const std::string& ref_model_path) {
    if(ref_model_path.find("dpi") != std::string::npos) {
      init_spike(ref_model_path);
      // set_spike_isa(to_spike_isa(ref_arch_type_));
      return true;
    }
    std::cout << "[FATAL]: init ref model failed, path is " << ref_model_path << std::endl;
    return false;
  }

  bool ArgParser::init_ref_program(const std::string& program_path) {
    spike_create(program_path);
    return true;
  }

  bool ArgParser::init_dut_model(const std::string& dut_model_path) {
    if(dut_model_path.find("MyCPU") != std::string::npos) {
      char *p = new char[dut_model_path.size()];
      strcpy(p, dut_model_path.c_str());
      char *argv[2] = {"emu_main", p};
      bool ret = dut_init(2, argv);
      delete [] p;
      return ret;
    }
    return false;
  }

  bool ArgParser::init_dut_program(const std::string& program_path) {
    // load program elf to dut_memory;
    if(!elf_loader::load_elf(program_path)) {
      return false;
    }
    // initialize dut cpu mem state
    if(!dut_init_memory(dut_memory.data(), dut_memory.size())) {
      std::cout << "[FATAL]: init dut memory failed, size is " << dut_memory.size() << std::endl;
      return false;
    }
    uint64_t first_inst = 0;
    if(!read_dut_mem(0x80000000, 4, (uint8_t*)&first_inst)) {
      std::cout << "[FATAL]: read dut memory failed at 0x80000000" << std::endl;
      return false;
    }
    std::cout << "Dut First Instruction: " << "0x" << std::hex << 
      first_inst << " " << std::dec << spike_disasm(first_inst) << std::endl;
    return true;
  }

  void ArgParser::parse(int argc, char *argv[]) {
    const char *short_opts = "r:f:d:iht";
    int opt, opt_idx = 0;
    std::string ref_model_path, dut_model_path, program_path;
    while(true) {
      int opt = getopt_long(argc, argv, short_opts, long_options, &opt_idx);
      if(opt == -1) break;
      switch(opt) {
        case 'f': {
          // load elf program
          if(optarg) {
            program_path = optarg;
          }
          break;
        }
        case 'i': {
          // print info
          Parser::getInstance().print_info();
          break;
        }
        case 'h': {
          Parser::getInstance().print_help();
          break;
        }
        case 'r': {
          if(optarg) {
            ref_model_path = std::string(optarg);
            std::cout << "ref model path is " << ref_model_path << std::endl;
          }
          break;
        }
        case 'd': {
          if(optarg) {
            dut_model_path = std::string(optarg);
            std::cout << "dut model path is " << dut_model_path << std::endl;
          }
          if(emu_mode_ == Mode::EMU) {
            emu_mode_ = Mode::DUT_ONLY;
          }
          break;
        }
        case 't': {
          std::cout << "diff test mode enabled" << std::endl;
          emu_mode_ = Mode::DIFF_TEST;
          break;
        }
      }
    }

    if(!ref_model_path.empty()) {
      if(!init_ref_model(ref_model_path)) {
        std::cout << "[FATAL]: init ref model failed, path is " << ref_model_path << std::endl;
        emu_state_ = State::HALTED;
        return;
      }
      std::cout << "init ref model done" << std::endl;
      if(!init_ref_program(program_path)) {
        std::cout << "[FATAL]: init refmodel program failed, path is " << program_path << std::endl;
        emu_state_ = State::HALTED;
        return;
      }
      std::cout << "init ref model program done" << std::endl;
    }

    if(!dut_model_path.empty()) {
      if(!init_dut_model(dut_model_path) && emu_mode_ == Mode::DIFF_TEST) {
        std::cout << "[FATAL]: init dut model failed, path is " << dut_model_path << std::endl;
        emu_state_ = State::HALTED;
        return;      
      }
      std::cout << "init dut model done" << std::endl;
      if(!init_dut_program(program_path) && emu_mode_ == Mode::DIFF_TEST) {
        std::cout << "[FATAL]: init dutmodel program failed, path is " << program_path << std::endl;
        emu_state_ = State::HALTED;
        return;
      }
      std::cout << "init dut model program done" << std::endl;
    }

  }

}