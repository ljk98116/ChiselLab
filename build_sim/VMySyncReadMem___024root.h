// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design internal header
// See VMySyncReadMem.h for the primary calling header

#ifndef VERILATED_VMYSYNCREADMEM___024ROOT_H_
#define VERILATED_VMYSYNCREADMEM___024ROOT_H_  // guard

#include "verilated.h"


class VMySyncReadMem__Syms;

class alignas(VL_CACHE_LINE_BYTES) VMySyncReadMem___024root final : public VerilatedModule {
  public:

    // DESIGN SPECIFIC STATE
    VL_IN8(clock,0,0);
    VL_IN8(reset,0,0);
    VL_IN8(io_addr,4,0);
    VL_IN8(io_Enable,0,0);
    VL_IN8(io_IsWrite,0,0);
    CData/*0:0*/ MySyncReadMem__DOT____Vcellinp__mem_ext__W0_en;
    CData/*0:0*/ MySyncReadMem__DOT__mem_ext__DOT___R0_en_d0;
    CData/*4:0*/ MySyncReadMem__DOT__mem_ext__DOT___R0_addr_d0;
    CData/*0:0*/ __VstlFirstIteration;
    CData/*0:0*/ __VicoFirstIteration;
    CData/*0:0*/ __Vtrigprevexpr___TOP__clock__0;
    CData/*0:0*/ __VactContinue;
    IData/*31:0*/ __VactIterCount;
    VL_IN64(io_writeData,63,0);
    VL_IN64(io_writeMask,63,0);
    VL_OUT64(io_readData,63,0);
    VlUnpacked<QData/*63:0*/, 32> MySyncReadMem__DOT__mem_ext__DOT__Memory;
    VlUnpacked<CData/*0:0*/, 2> __Vm_traceActivity;
    VlTriggerVec<1> __VstlTriggered;
    VlTriggerVec<1> __VicoTriggered;
    VlTriggerVec<1> __VactTriggered;
    VlTriggerVec<1> __VnbaTriggered;

    // INTERNAL VARIABLES
    VMySyncReadMem__Syms* const vlSymsp;

    // CONSTRUCTORS
    VMySyncReadMem___024root(VMySyncReadMem__Syms* symsp, const char* v__name);
    ~VMySyncReadMem___024root();
    VL_UNCOPYABLE(VMySyncReadMem___024root);

    // INTERNAL METHODS
    void __Vconfigure(bool first);
};


#endif  // guard
