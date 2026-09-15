# 顶层 Makefile
CXX = g++ # default
CXXFLAGS = -Iinclude -std=c++2a -ggdb
LDFLAGS = -L../build_sim

VPATH = src:\
				src/common:\
				src/cmdline:\
				src/disasm:\
				src/refmodels/spike:\
				src/dutmodels/cpu:\
				src/utils/ # 指定源文件搜索路径

SRCS = $(wildcard src/*.cpp \
									src/common/*.cpp \
									src/cmdline/*.cpp \
									src/disasm/*.cpp \
									src/refmodels/spike/*.cpp \
									src/dutmodels/cpu/*.cpp \
									src/utils/*.cpp \
				) # 获取所有源文件路径

OBJS = $(SRCS:.cpp=.o) # 将cpp文件转换为obj文件，保持目录结构
DEPS = $(SRCS:.cpp=.d) # 依赖文件，用于自动生成依赖关系，可选但推荐使用gcc的-MMD选项生成它们。
TMPDEPS = $(SRCS:.cpp=.d.tmp)
all: build

build: $(OBJS)
	$(CXX) $(CXXFLAGS) -o ../apps/emu_main $(OBJS) $(LDFLAGS)

%.o: %.cpp
	@mkdir -p $(@D)
	$(CXX) $(CXXFLAGS) -c $< -o $@
	@$(CXX) -MM $< > $*.d
	@cp $*.d $*.d.tmp

.PHONY:clean
clean:
	rm -rf $(OBJS) $(DEPS) $(TMPDEPS) emu_main ../apps/emu_main
