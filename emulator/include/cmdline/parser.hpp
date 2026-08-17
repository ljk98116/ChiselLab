#ifndef COMMON_PARSER_HPP
#define COMMON_PARSER_HPP

#include <string>

namespace emulator {
  class Parser {
    public:
      static Parser& getInstance() {
        static Parser instance;
        return instance;
      }
      void parse(const std::string& cmd);
      void print_help();
      void print_info();
      void print_regs(int hart_id);
      void print_csrs(int hart_id);
    private:
      Parser() {}
      Parser(const Parser&) = delete;
      Parser& operator=(const Parser&) = delete;
    private:
      void step(int hart_id, unsigned int steps);
    private:
  };

  void parse(const std::string& cmd);
}
#endif