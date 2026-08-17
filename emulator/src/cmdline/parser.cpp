#include <cmdline/parser.hpp>
#include <common/state.hpp>

#include <refmodels/spike/spike_wrapper.hpp>
#include <refmodels/spike/spike_state.hpp>

#include <iostream>
#include <cassert>

namespace emulator {
  void parse(const std::string& cmd) {
    Parser::getInstance().parse(cmd);
  }
  
  void Parser::step(int hart_id, unsigned int steps) {
    spike_step(hart_id, steps);
    // get gprs and csrs, update spike cpu states
    get_spike_gprs(hart_id, spike_gprs_[hart_id], 32);
    for(auto& p : spike_csrs_[hart_id]) {
      auto value = get_spike_csr(hart_id, p.first);
      p.second = value;
    }
  }

  void Parser::print_regs(int hart_id) {
    assert(hart_id <= MAX_HART_ID);
    for(int i=0;i<32;++i) {
      printf("GPR [%d]: 0x%llx\n", i, spike_gprs_[hart_id][i]);
    }
  }

  void Parser::print_csrs(int hart_id) {
    assert(hart_id <= MAX_HART_ID);
    auto csr305 = get_spike_csr(0, 0x305);
    spike_csrs_[0][0x305] = csr305; 
    for(auto& p: spike_csrs_[hart_id]) {
      printf("CSR [%ld]: 0x%llx\n", p.first, p.second);
    }
  }

  void Parser::parse(const std::string& cmd) {
    if(cmd.empty()) {
      return;
    }

    switch(cmd[0]) {
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
        int idx = cmd.find_first_of(' ');
        if(idx == std::string::npos) {
          step(0, 1);
        }
        else {
          int digital_start = idx + 1;
          std::string digital = cmd.substr(digital_start);
          int steps = std::stoi(digital);
          step(0, steps);
        }
        break;
      }
      case 'p': {
        // print traces
        printf("pc: 0x%llx\n", get_spike_pc(0));
        print_regs(0);
        print_csrs(0);
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