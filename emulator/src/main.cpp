#include <iostream>
#include <getopt.h>
#include <limits>

#include <common/state.hpp>
#include <cmdline/parser.hpp>
#include <cmdline/argparser.hpp>

using namespace emulator;

int main(int argc, char *argv[]) {
  // parse arguments
  // to do: implement argument parsing
  ArgParser::Instance().parse(argc, argv);
  while(emu_state_ == State::RUNNING) {
    // Main loop of the emulator
    std::cout << "emu> ";
    std::string cmd = "";
    char c;
    while((c = std::cin.get()) != '\n') {
      cmd += c;
    }
    if(cmd.empty()) continue;
    parse(cmd);
    // check emu state
    if(emu_state_ == State::EXITED) {
      std::cout << "Exiting emulator." << std::endl;
    }
    else if(emu_state_ == State::HALTED) {
      std::cout << "Emulator halted." << std::endl;
      return -1;
    }
  }
  return 0;
}