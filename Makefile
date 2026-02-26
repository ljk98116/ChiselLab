# build verilator CPU C++ model
# chisel -> verilog -> cpp      -> obj_file
#            		   dpicpp   -> obj_file
PROJ=MyCPU
ARCH=RV32E
TOP=L1BTB

CC = g++
VERILATOR = verilator

# verilator compile speed up
VERILATOR_FLAGS = --cc -O3 -Wall --x-assign fast --x-initial fast --Wno-lint
SIMFLAGS = --trace --trace-fst -DSIMFLAG=1

# find all sources
V_SRC = $(shell find $(abspath ./verilog/) -name "*.v")
SV_SRC = $(shell find $(abspath ./verilog/) -name "*.sv")
RTL_SRC = $(V_SRC) $(SV_SRC)

CXXSRC = $(shell find $(abspath ./verilator_model/) -name "*.c" -or -name "*.cc" -or -name "*.cpp")

# include path
INC_PATH = verilator_model/include /usr/share/verilator/include/ 

# CXXFLAG & LDFLAGS
# only visible function
INCFLAGS = $(addprefix -I , $(INC_PATH))

CXXFLAG = -fvisibility=hidden $(INCFLAGS) -DTOP_NAME="\"V$(PROJ)\"" -MMD -MP -pg -DARCH=$(ARCH) -fPIC -shared
VERILATOR_FLAGS += -I$(PROJ)/src/resources/

LDFLAGS = -lpthread

OBJ_DIR = build
VERILATOR_CSRC = $(wildcard $(OBJ_DIR)/*.cpp)

SIM_OBJ_DIR = build_sim
VERILATOR_SIM_CSRC = $(wildcard $(SIM_OBJ_DIR)/*.cpp)

TARGET = $(OBJ_DIR)/lib$(PROJ).so
SIM_TARGET = $(SIM_OBJ_DIR)/lib$(PROJ)_sim.so

# firtool path
export PATH:=./firtool-1.43.0/bin:$(PATH)

.PHONY: all
all: model $(TARGET) sim_model $(SIM_TARGET)

.PHONY: model
model: verilog cpp

.PHONY: sim_model
sim_model: verilog sim_cpp

.PHONY: verilog
verilog:
	@mill $(PROJ).compile
	@mill $(PROJ).runMain Elaborate --target-dir=./verilog/$(PROJ)
	@rm -rf ./verilog/$(PROJ)/verification

.PHONY: cpp
cpp: $(RTL_SRC)
	@rm -rf $(OBJ_DIR)
	@mkdir $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_FLAGS) \
	$(RTL_SRC)  \
	--top $(TOP) \
	--Mdir $(OBJ_DIR)

.PHONY: sim_cpp
sim_cpp: $(RTL_SRC)
	@rm -rf $(SIM_OBJ_DIR)
	@mkdir $(SIM_OBJ_DIR)
	$(VERILATOR) $(VERILATOR_FLAGS) $(SIMFLAGS) \
	$(RTL_SRC)  \
	--top $(TOP) \
	--Mdir $(SIM_OBJ_DIR)

$(TARGET): $(VERILATOR_CSRC)
	$(CC) $(CXXFLAG) $(LDFLAGS) -o $@ $(VERILATOR_CSRC) $(CXXSRC)

$(SIM_TARGET): $(VERILATOR_SIM_CSRC)
	$(CC) $(CXXFLAG) -DSIMFLAG=1 $(LDFLAGS) -o $@ $(VERILATOR_SIM_CSRC) $(CXXSRC)

.PHONY: clean
clean:
	@rm -rf $(TARGET) $(SIM_TARGET)
	@rm -rf $(OBJ_DIR) $(SIM_OBJ_DIR)
	@rm -rf verilog/$(PROJ)
	@mill -i clean
