#include <dlfcn.h>
#include <iostream>

int main() {
  void* h = dlopen("libs/libdpi.so", RTLD_LAZY);
  // 不调用任何函数
  std::cin.get(); // 此时检查终端回显
  dlclose(h);
  return 0;
}