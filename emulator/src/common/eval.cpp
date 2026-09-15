#include <common/eval.hpp>

namespace emulator {
  bool Eval::eval(const std::string& expr, uint64_t& result) {
    return true;
  }

  bool eval(const std::string& expr, uint64_t& result) {
    return Eval::getInstance().eval(expr, result);
  }
}