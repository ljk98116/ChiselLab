#ifndef COMMON_EVAL_HPP
#define COMMON_EVAL_HPP

#include <cstdint>
#include <string>

namespace emulator {
  class Eval {
    public:
      static Eval& getInstance() {
        static Eval eval;
        return eval;
      }
      bool eval(const std::string& expr, uint64_t& result);
  };

  bool eval(const std::string& expr, uint64_t& result);
}

#endif