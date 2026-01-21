// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Model implementation (design independent parts)

#include "VMySyncReadMem__pch.h"
#include "verilated_fst_c.h"

//============================================================
// Constructors

VMySyncReadMem::VMySyncReadMem(VerilatedContext* _vcontextp__, const char* _vcname__)
    : VerilatedModel{*_vcontextp__}
    , vlSymsp{new VMySyncReadMem__Syms(contextp(), _vcname__, this)}
    , clock{vlSymsp->TOP.clock}
    , reset{vlSymsp->TOP.reset}
    , io_addr{vlSymsp->TOP.io_addr}
    , io_Enable{vlSymsp->TOP.io_Enable}
    , io_IsWrite{vlSymsp->TOP.io_IsWrite}
    , io_writeData{vlSymsp->TOP.io_writeData}
    , io_writeMask{vlSymsp->TOP.io_writeMask}
    , io_readData{vlSymsp->TOP.io_readData}
    , rootp{&(vlSymsp->TOP)}
{
    // Register model with the context
    contextp()->addModel(this);
}

VMySyncReadMem::VMySyncReadMem(const char* _vcname__)
    : VMySyncReadMem(Verilated::threadContextp(), _vcname__)
{
}

//============================================================
// Destructor

VMySyncReadMem::~VMySyncReadMem() {
    delete vlSymsp;
}

//============================================================
// Evaluation function

#ifdef VL_DEBUG
void VMySyncReadMem___024root___eval_debug_assertions(VMySyncReadMem___024root* vlSelf);
#endif  // VL_DEBUG
void VMySyncReadMem___024root___eval_static(VMySyncReadMem___024root* vlSelf);
void VMySyncReadMem___024root___eval_initial(VMySyncReadMem___024root* vlSelf);
void VMySyncReadMem___024root___eval_settle(VMySyncReadMem___024root* vlSelf);
void VMySyncReadMem___024root___eval(VMySyncReadMem___024root* vlSelf);

void VMySyncReadMem::eval_step() {
    VL_DEBUG_IF(VL_DBG_MSGF("+++++TOP Evaluate VMySyncReadMem::eval_step\n"); );
#ifdef VL_DEBUG
    // Debug assertions
    VMySyncReadMem___024root___eval_debug_assertions(&(vlSymsp->TOP));
#endif  // VL_DEBUG
    vlSymsp->__Vm_activity = true;
    vlSymsp->__Vm_deleter.deleteAll();
    if (VL_UNLIKELY(!vlSymsp->__Vm_didInit)) {
        vlSymsp->__Vm_didInit = true;
        VL_DEBUG_IF(VL_DBG_MSGF("+ Initial\n"););
        VMySyncReadMem___024root___eval_static(&(vlSymsp->TOP));
        VMySyncReadMem___024root___eval_initial(&(vlSymsp->TOP));
        VMySyncReadMem___024root___eval_settle(&(vlSymsp->TOP));
    }
    VL_DEBUG_IF(VL_DBG_MSGF("+ Eval\n"););
    VMySyncReadMem___024root___eval(&(vlSymsp->TOP));
    // Evaluate cleanup
    Verilated::endOfEval(vlSymsp->__Vm_evalMsgQp);
}

//============================================================
// Events and timing
bool VMySyncReadMem::eventsPending() { return false; }

uint64_t VMySyncReadMem::nextTimeSlot() {
    VL_FATAL_MT(__FILE__, __LINE__, "", "%Error: No delays in the design");
    return 0;
}

//============================================================
// Utilities

const char* VMySyncReadMem::name() const {
    return vlSymsp->name();
}

//============================================================
// Invoke final blocks

void VMySyncReadMem___024root___eval_final(VMySyncReadMem___024root* vlSelf);

VL_ATTR_COLD void VMySyncReadMem::final() {
    VMySyncReadMem___024root___eval_final(&(vlSymsp->TOP));
}

//============================================================
// Implementations of abstract methods from VerilatedModel

const char* VMySyncReadMem::hierName() const { return vlSymsp->name(); }
const char* VMySyncReadMem::modelName() const { return "VMySyncReadMem"; }
unsigned VMySyncReadMem::threads() const { return 1; }
void VMySyncReadMem::prepareClone() const { contextp()->prepareClone(); }
void VMySyncReadMem::atClone() const {
    contextp()->threadPoolpOnClone();
}
std::unique_ptr<VerilatedTraceConfig> VMySyncReadMem::traceConfig() const {
    return std::unique_ptr<VerilatedTraceConfig>{new VerilatedTraceConfig{false, false, false}};
};

//============================================================
// Trace configuration

void VMySyncReadMem___024root__trace_decl_types(VerilatedFst* tracep);

void VMySyncReadMem___024root__trace_init_top(VMySyncReadMem___024root* vlSelf, VerilatedFst* tracep);

VL_ATTR_COLD static void trace_init(void* voidSelf, VerilatedFst* tracep, uint32_t code) {
    // Callback from tracep->open()
    VMySyncReadMem___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<VMySyncReadMem___024root*>(voidSelf);
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    if (!vlSymsp->_vm_contextp__->calcUnusedSigs()) {
        VL_FATAL_MT(__FILE__, __LINE__, __FILE__,
            "Turning on wave traces requires Verilated::traceEverOn(true) call before time 0.");
    }
    vlSymsp->__Vm_baseCode = code;
    tracep->pushPrefix(std::string{vlSymsp->name()}, VerilatedTracePrefixType::SCOPE_MODULE);
    VMySyncReadMem___024root__trace_decl_types(tracep);
    VMySyncReadMem___024root__trace_init_top(vlSelf, tracep);
    tracep->popPrefix();
}

VL_ATTR_COLD void VMySyncReadMem___024root__trace_register(VMySyncReadMem___024root* vlSelf, VerilatedFst* tracep);

VL_ATTR_COLD void VMySyncReadMem::trace(VerilatedFstC* tfp, int levels, int options) {
    if (tfp->isOpen()) {
        vl_fatal(__FILE__, __LINE__, __FILE__,"'VMySyncReadMem::trace()' shall not be called after 'VerilatedFstC::open()'.");
    }
    if (false && levels && options) {}  // Prevent unused
    tfp->spTrace()->addModel(this);
    tfp->spTrace()->addInitCb(&trace_init, &(vlSymsp->TOP));
    VMySyncReadMem___024root__trace_register(&(vlSymsp->TOP), tfp->spTrace());
}
