#ifndef CMDLINE_ARGPARSER_HPP
#define CMDLINE_ARGPARSER_HPP

#include <getopt.h>

namespace emulator {
  class ArgParser {
    public:
      static ArgParser Instance() {
        static ArgParser parser;
        return parser;
      }
      void parse(int argc, char *argv[]);
    private:
      bool init_ref_model(const std::string& ref_model_path);
      bool init_program(const std::string& program_path);
  };

}

#endif