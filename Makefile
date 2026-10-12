# build verilator CPU C++ model
# chisel -> verilog -> cpp      -> obj_file
#            		   dpicpp   -> obj_file
PROJ=MyCPU
ARCH=RV64I
TOP=L1Core
HOME=/home/ljk/Arch/ChiselLab

ISA_TESTS_DIR=/opt/riscv/target/share/riscv-tests/isa/
BENCHMARK_TESTS_DIR=/opt/riscv/target/share/riscv-tests/benchmarks/

CC = g++
VERILATOR = verilator

# verilator compile speed up
VERILATOR_FLAGS = --cc -O3 -Wall --x-assign fast --x-initial fast --Wno-lint --timescale "1ns/1ps"
SIMFLAGS = --trace --trace-fst -DSIMFLAG=1

# find all sources
V_SRC = $(shell find $(abspath ./verilog/) -name "*.v")
SV_SRC = $(shell find $(abspath ./verilog/) -name "*.sv")
RTL_SRC = $(V_SRC) $(SV_SRC)

CXXSRC = $(shell find $(abspath ./$(PROJ)/src/main/resources/csrc/verilator_model/) -name "*.c" -or -name "*.cc" -or -name "*.cpp")

# include path
INC_PATH = $(HOME)/$(PROJ)/src/main/resources/csrc/verilator_model/include /usr/share/verilator/include/ /usr/share/verilator/include/vltstd

# CXXFLAG & LDFLAGS
# only visible function
INCFLAGS = $(addprefix -I , $(INC_PATH)) -Ibuild
SIM_INCFLAGS = $(addprefix -I , $(INC_PATH)) -Ibuild_sim

CXXFLAG = $(INCFLAGS) -DTOP_NAME="\"V$(PROJ)\"" -MMD -MP -pg -DARCH=$(ARCH) -fPIC -shared
SIM_CXXFLAG = $(SIM_INCFLAGS) -DTOP_NAME="\"V$(PROJ)\"" -MMD -MP -pg -DARCH=$(ARCH) -fPIC -shared -DSIMFLAG=1

CXXFLAG := $(addprefix -CFLAGS , $(CXXFLAG))
SIM_CXXFLAG := $(addprefix -CFLAGS , $(SIM_CXXFLAG))

VERILATOR_FLAGS += -I$(PROJ)/src/resources/

LDFLAGS = -lpthread -lz

OBJ_DIR = build
VERILATOR_CSRC = $(wildcard $(OBJ_DIR)/*.cpp)

SIM_OBJ_DIR = build_sim
VERILATOR_SIM_CSRC = $(wildcard $(SIM_OBJ_DIR)/*.cpp)

TARGET = $(HOME)/$(OBJ_DIR)/lib$(PROJ).so
SIM_TARGET = $(HOME)/$(SIM_OBJ_DIR)/lib$(PROJ)_sim.so

EMU_TARGET = $(HOME)/apps/emu_main

# firtool path
export PATH:=./firtool-1.43.0/bin:$(PATH)

# used for espresso to work
export PATH:=./:$(PATH)

.PHONY: all
all: model $(TARGET) sim_model $(SIM_TARGET) $(EMU_TARGET)

.PHONY: model
model: verilog cpp

.PHONY: sim_model
sim_model: verilog sim_cpp

.PHONY: verilog
verilog:
	@mill $(PROJ).compile
	@mill $(PROJ).runMain Elaborate --target-dir=./verilog/$(PROJ)
	@rm -rf ./verilog/$(PROJ)/verification

.PHONY: test
test:
	@mill $(PROJ).test

.PHONY: cpp
cpp: $(RTL_SRC) $(CXXSRC)
	@rm -rf $(OBJ_DIR)
	@mkdir $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_FLAGS) $(CXXFLAG) \
	$(RTL_SRC) $(CXXSRC) \
	--top $(TOP) \
	--Mdir $(OBJ_DIR)

.PHONY: sim_cpp
sim_cpp: $(RTL_SRC) $(CXXSRC)
	@rm -rf $(SIM_OBJ_DIR)
	@mkdir $(SIM_OBJ_DIR)
	$(VERILATOR) $(VERILATOR_FLAGS) $(SIM_CXXFLAG) $(SIMFLAGS) \
	$(RTL_SRC) $(CXXSRC) \
	--top $(TOP) \
	--Mdir $(SIM_OBJ_DIR)

$(TARGET): cpp
	cd $(OBJ_DIR) && make -f V$(PROJ).mk && ar -x libV$(PROJ).a && g++ -shared -o $@ *.o $(LDFLAGS) && cd ..

$(SIM_TARGET): sim_cpp
	cd $(SIM_OBJ_DIR) && make -f V$(PROJ).mk && ar -x libV$(PROJ).a && g++ -shared -o $@ *.o $(LDFLAGS) && cd ..

.PHONY: clean
clean:
	@rm -rf $(TARGET) $(SIM_TARGET)
	@rm -rf $(OBJ_DIR) $(SIM_OBJ_DIR)
	@rm -rf verilog/$(PROJ)
	@rm -rf apps/emu_main
	@mill -i clean

$(EMU_TARGET):
	@echo "Building emulator..."
	make -C emulator

spike:
	@echo "Building spike..."
	mkdir -p tools/spike/build
	cd tools/spike/build && ../configure --prefix=$(RISCV)
	make -C tools/spike/dpi/
	cp tools/spike/dpi/libdpi.so apps/

run_coremark_spike: $(EMU_TARGET) spike
	$(EMU_TARGET) -r $(HOME)/apps/libdpi.so -f $(HOME)/apps/riscv-coremark/coremark.bare.riscv

# use spike disasm to see the disasm result
run_coremark_dut: $(EMU_TARGET) $(TARGET)
	$(EMU_TARGET) -r $(HOME)/apps/libdpi.so -d $(TARGET) -f $(HOME)/apps/riscv-coremark/coremark.bare.riscv

run_coremark_diff: $(EMU_TARGET) $(SIM_TARGET) spike
	$(EMU_TARGET) -r $(HOME)/apps/libdpi.so -d $(SIM_TARGET) -f $(HOME)/apps/riscv-coremark/coremark.bare.riscv -t
