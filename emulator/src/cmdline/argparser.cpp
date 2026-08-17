#include <iostream>

#include <cmdline/argparser.hpp>
#include <cmdline/parser.hpp>

#include <common/state.hpp>

#include <refmodels/spike/spike_wrapper.hpp>

namespace emulator {

  static struct option long_options[] = {
    {"program", required_argument, 0, 'f'}, // --program=<program name>
    {"info", no_argument, 0, 'i'},
    {"help", no_argument, 0, 'h'},
    {"arch", required_argument, 0, 'a'},
    {"refModel", required_argument, 0, 'r'},
    {"difftest", no_argument, 0, 'd'},
    {0, 0, 0, 0}
  };

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
      return true;
    }
    std::cout << "[FATAL]: init ref model failed, path is " << ref_model_path << std::endl;
    return false;
  }

  bool ArgParser::init_program(const std::string& program_path) {
    spike_create(program_path);
    return true;
  }

  void ArgParser::parse(int argc, char *argv[]) {
    const char *short_opts = "f:iha:r:d";
    int opt, opt_idx = 0;
    std::string ref_model_path;
    std::string program_path;
    while(true) {
      int opt = getopt_long(argc, argv, short_opts, long_options, &opt_idx);
      if(opt == -1) break;
      switch(opt) {
        case 'f': {
          // load elf program
          program_path = optarg;
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
        case 'a': {
          set_ref_arch_state(optarg);
          break;
        }
        case 'r': {
          ref_model_path = std::string(optarg);
          break;
        }
        case 'd': {
          emu_mode_ = Mode::DIFF_TEST;
          break;
        }
      }
    }
    if(!init_ref_model(ref_model_path)) {
      std::cout << "[FATAL]: init ref model failed, path is " << ref_model_path << std::endl;
      emu_state_ = State::HALTED;
      return;
    }
    std::cout << "init ref model done" << std::endl;
    if(!init_program(program_path)) {
      std::cout << "[FATAL]: init program failed, path is " << program_path << std::endl;
      emu_state_ = State::HALTED;
      return;
    }
  }

}