#include <cmdline/parser.hpp>
#include <common/state.hpp>
#include <common/eval.hpp>

#include <refmodels/spike/spike_wrapper.hpp>
#include <refmodels/spike/spike_state.hpp>

#include <dutmodels/cpu/cpu_wrapper.hpp>
#include <dutmodels/cpu/cpu_state.hpp>

#include <iostream>
#include <sstream>
#include <cassert>

namespace emulator {
  static std::vector<std::string> split(const std::string& str, char delimiter) {
    std::vector<std::string> tokens;
    std::string token;
    std::istringstream tokenStream(str);
    while (std::getline(tokenStream, token, delimiter)) {
      tokens.push_back(token);
    }
    return tokens;
  }

  void parse(const std::string& cmd) {
    Parser::getInstance().parse(cmd);
  }
  
  void Parser::step(int hart_id, unsigned int steps) {
    static bool has_exec = false;
    if(emu_mode_ == Mode::DIFF_TEST || emu_mode_ == Mode::DUT_ONLY) {
      if(!has_exec) {
        dut_start();
        has_exec = true;
      }
      for(int i = 0; i < steps; ++i) {
        dut_clock_step();
        if(dut_status() == false) {
          std::cout << "[INFO]: DUT Model running finished" << std::endl;
          break;
        }
      }
    }
    else {
      spike_step(hart_id, steps);
      // get gprs and csrs, update spike cpu states
      get_spike_gprs(hart_id, spike_gprs_[hart_id], 32);
      for(auto& p : spike_csrs_[hart_id]) {
        auto value = get_spike_csr(hart_id, p.first);
        p.second = value;
      }
    }
  }

  void Parser::print_regs(int hart_id) {
    assert(hart_id <= MAX_HART_ID);
    for(int i=0;i<32;++i) {
      printf("GPR [%s]: 0x%016llx ", regnames[i], spike_gprs_[hart_id][i]);
      if(i % 4 == 3) {
        printf("\n");
      }
    }
  }

  void Parser::print_csrs(int hart_id) {
    assert(hart_id <= MAX_HART_ID);
    for(auto& p: spike_csrs_[hart_id]) {
      printf("CSR [%ld]: 0x%llx\n", p.first, p.second);
    }
  }

  void Parser::print_itrace(int hart_id) {
    assert(hart_id <= MAX_HART_ID);
    for(auto& t: itrace) {
      printf("ITRACE: PC: 0x%llx INST: 0x%llx %s\n", t.pc, t.inst, t.disasm_string.c_str());
    }
  }

  void Parser::print_mtrace(int hart_id) {
    assert(hart_id <= MAX_HART_ID);
    for(auto& t: mtrace) {
      printf("MTRACE: PC: 0x%llx INST: 0x%llx %s\n", t.pc, t.inst, t.disasm_string.c_str());
    }
  }

  void Parser::print_ftrace(int hart_id) {
    assert(hart_id <= MAX_HART_ID);
    for(auto& t: ftrace) {
      printf("FTRACE: PC: 0x%llx INST: 0x%llx %s\n", t.pc, t.inst, t.disasm_string.c_str());
    }
  }

  void Parser::print_rats(int hart_id) {
    assert(hart_id <= MAX_HART_ID);
    for(auto& p: rats[hart_id]) {
      printf("RAT [%ld]: 0x%llx\n", p.first, p.second);
    }
  }

  void Parser::print_cache_stats(int hart_id) {
    assert(hart_id <= MAX_HART_ID);
  }

  void Parser::print_bpu_stats(int hart_id) {
    assert(hart_id <= MAX_HART_ID);
  }
  
  void Parser::print_func(const std::string& token) {
    if(token == "itrace") {
      print_itrace(0);
    }
    else if(token == "mtrace") {
      print_mtrace(0);
    }
    else if(token == "ftrace") {
      print_ftrace(0);
    }
    else if(token == "rats") {
      print_rats(0);
    }
    else if(token == "cache") {
      print_cache_stats(0);
    }
    else if(token == "bpu") {
      print_bpu_stats(0);
    }
    else if(token == "regs") {
      if(emu_mode_ == Mode::DIFF_TEST || emu_mode_ == Mode::DUT_ONLY) {
        if(commit_nums[0] == 0) {
          std::cout << "[INFO]: DUT Model has not executed any instruction yet." << std::endl;
          return;
        }
        std::cout << "[INFO]: DUT Model PC: 0x" << std::hex << commit_pcs[0][commit_nums[0] - 1] << std::dec << std::endl;
      }
      else {
        std::cout << "[INFO]: Spike Model PC: 0x" << std::hex << get_spike_pc(0) << std::dec << std::endl;
      }
      print_regs(0);
    }
    else if(token == "csrs") {
      print_csrs(0);
    }
    else if(token == "info") {
      print_info();
    }
    else if(token == "all") {
      print_info();
      print_regs(0);
      print_csrs(0);
      print_itrace(0);
      print_mtrace(0);
      print_ftrace(0);
      print_rats(0);
      print_cache_stats(0);
      print_bpu_stats(0);
    }
    else if(token == "trace") {
      print_itrace(0);
      print_mtrace(0);
      print_ftrace(0);
    }
    else {
      std::cerr << "[ERROR]: Unknown trace type: " << token << std::endl;
    }
  }

  void Parser::parse(const std::string& cmd) {
    if(cmd.empty()) {
      return;
    }
    std::vector<std::string> tokens;
    tokens = split(cmd, ' ');

    switch(tokens[0][0]) {
      case 'h': {
        print_help();
        break;
      }
      case 'r': {
        // run loaded program
        break;
      }
      case 'n': {
        // run n insts
        if(tokens.size() == 1) {
          step(0, 1);
        }
        else if(tokens.size() == 2) {
          uint64_t steps = 0;
          if(!eval(tokens[1], steps)) {
            std::cerr << "Invalid expression: " << tokens[1] << std::endl;
          }
          else {
            step(0, steps);
          }
        }
        else {
          std::cerr << "Invalid command: " << cmd << std::endl;
        }
        break;
      }
      case 'p': {
        // print traces
        print_func(tokens.size() > 1 ? tokens[1] : "");
        break;
      }
      case 'x': {
        // print memory
        break;
      }
      case 'c': {
        // continue running until exit or halt
        step(0, -1);
        break;
      }
      case 'b': {
        // set breakpoint
        break;
      }
      case 'd': {
        // delete breakpoint
        break;
      }
      case 'q': {
        emu_state_ = State::EXITED;
        deinit_spike();
        std::cout << "quit from emulator" << std::endl;
        break;
      }
      case 'e': {
        // eval expression
        break;
      }
      case 'i': {
        // print emu info
        print_info();
        break;
      }
      case 'f': {
        // load program
        break;
      }
      case 'l': {
        // list all eval vars
        break;
      }
      default: {
        std::cerr << "Unknown command: " << cmd << std::endl;
      }
    }
  }

  void Parser::print_help() {
    std::cout << "Available commands:\n";
    std::cout << "  h - Show this help message\n";
    std::cout << "  r - Run the emulator\n";
    std::cout << "  n [number] - Run n instructions (not implemented)\n";
    std::cout << "  p - Print traces (not implemented)\n";
    std::cout << "  x [address] - Print memory at address (not implemented)\n";
    std::cout << "  c - Continue running until exit or halt (not implemented)\n";
    std::cout << "  b [address] - Set breakpoint at address (not implemented)\n";
    std::cout << "  d [address] - Delete breakpoint at address (not implemented)\n";
    std::cout << "  q - Quit the emulator\n";
    std::cout << "  e [expression] - Evaluate expression (not implemented)\n";
    std::cout << "  i - Print emulator info (not implemented)\n";
    std::cout << "  f [elf file] - load elf format program\n";
    std::cout << "  l - list all vars\n";
  }

  void Parser::print_info() {
    std::cout << "Emulator State: ";
    std::cout << toString(emu_state_) << std::endl;
    std::cout << "Emulator Mode: ";
    std::cout << toString(emu_mode_) << std::endl;
    std::cout << "Program State: ";
    std::cout << toString(emu_prog_state_) << std::endl;
    std::cout << "Program Mode: ";
    std::cout << toString(emu_prog_mode_) << std::endl;
    std::cout << "Dut State: ";
    std::cout << toString(dut_state_) << std::endl;
    std::cout << "REF ARCH: ";
    std::cout << toString(ref_arch_type_) << std::endl;
    std::cout << std::endl;
  }


}