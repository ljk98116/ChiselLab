// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VMySyncReadMem.h for the primary calling header

#include "VMySyncReadMem__pch.h"
#include "VMySyncReadMem___024root.h"

VL_ATTR_COLD void VMySyncReadMem___024root___eval_static(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___eval_static\n"); );
}

VL_ATTR_COLD void VMySyncReadMem___024root___eval_initial(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___eval_initial\n"); );
    // Body
    vlSelf->__Vtrigprevexpr___TOP__clock__0 = vlSelf->clock;
}

VL_ATTR_COLD void VMySyncReadMem___024root___eval_final(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___eval_final\n"); );
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VMySyncReadMem___024root___dump_triggers__stl(VMySyncReadMem___024root* vlSelf);
#endif  // VL_DEBUG
VL_ATTR_COLD bool VMySyncReadMem___024root___eval_phase__stl(VMySyncReadMem___024root* vlSelf);

VL_ATTR_COLD void VMySyncReadMem___024root___eval_settle(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___eval_settle\n"); );
    // Init
    IData/*31:0*/ __VstlIterCount;
    CData/*0:0*/ __VstlContinue;
    // Body
    __VstlIterCount = 0U;
    vlSelf->__VstlFirstIteration = 1U;
    __VstlContinue = 1U;
    while (__VstlContinue) {
        if (VL_UNLIKELY((0x64U < __VstlIterCount))) {
#ifdef VL_DEBUG
            VMySyncReadMem___024root___dump_triggers__stl(vlSelf);
#endif
            VL_FATAL_MT("/home/mach/Arch/ChiselLab/verilog/MyCPU/MySyncReadMem.sv", 2, "", "Settle region did not converge.");
        }
        __VstlIterCount = ((IData)(1U) + __VstlIterCount);
        __VstlContinue = 0U;
        if (VMySyncReadMem___024root___eval_phase__stl(vlSelf)) {
            __VstlContinue = 1U;
        }
        vlSelf->__VstlFirstIteration = 0U;
    }
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VMySyncReadMem___024root___dump_triggers__stl(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___dump_triggers__stl\n"); );
    // Body
    if ((1U & (~ (IData)(vlSelf->__VstlTriggered.any())))) {
        VL_DBG_MSGF("         No triggers active\n");
    }
    if ((1ULL & vlSelf->__VstlTriggered.word(0U))) {
        VL_DBG_MSGF("         'stl' region trigger index 0 is active: Internal 'stl' trigger - first iteration\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD void VMySyncReadMem___024root___stl_sequent__TOP__0(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___stl_sequent__TOP__0\n"); );
    // Body
    vlSelf->MySyncReadMem__DOT____Vcellinp__mem_ext__W0_en 
        = ((IData)(vlSelf->io_Enable) & (IData)(vlSelf->io_IsWrite));
    vlSelf->io_readData = ((IData)(vlSelf->MySyncReadMem__DOT__mem_ext__DOT___R0_en_d0)
                            ? vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory
                           [vlSelf->MySyncReadMem__DOT__mem_ext__DOT___R0_addr_d0]
                            : 0ULL);
}

VL_ATTR_COLD void VMySyncReadMem___024root___eval_stl(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___eval_stl\n"); );
    // Body
    if ((1ULL & vlSelf->__VstlTriggered.word(0U))) {
        VMySyncReadMem___024root___stl_sequent__TOP__0(vlSelf);
    }
}

VL_ATTR_COLD void VMySyncReadMem___024root___eval_triggers__stl(VMySyncReadMem___024root* vlSelf);

VL_ATTR_COLD bool VMySyncReadMem___024root___eval_phase__stl(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___eval_phase__stl\n"); );
    // Init
    CData/*0:0*/ __VstlExecute;
    // Body
    VMySyncReadMem___024root___eval_triggers__stl(vlSelf);
    __VstlExecute = vlSelf->__VstlTriggered.any();
    if (__VstlExecute) {
        VMySyncReadMem___024root___eval_stl(vlSelf);
    }
    return (__VstlExecute);
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VMySyncReadMem___024root___dump_triggers__ico(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___dump_triggers__ico\n"); );
    // Body
    if ((1U & (~ (IData)(vlSelf->__VicoTriggered.any())))) {
        VL_DBG_MSGF("         No triggers active\n");
    }
    if ((1ULL & vlSelf->__VicoTriggered.word(0U))) {
        VL_DBG_MSGF("         'ico' region trigger index 0 is active: Internal 'ico' trigger - first iteration\n");
    }
}
#endif  // VL_DEBUG

#ifdef VL_DEBUG
VL_ATTR_COLD void VMySyncReadMem___024root___dump_triggers__act(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___dump_triggers__act\n"); );
    // Body
    if ((1U & (~ (IData)(vlSelf->__VactTriggered.any())))) {
        VL_DBG_MSGF("         No triggers active\n");
    }
    if ((1ULL & vlSelf->__VactTriggered.word(0U))) {
        VL_DBG_MSGF("         'act' region trigger index 0 is active: @(posedge clock)\n");
    }
}
#endif  // VL_DEBUG

#ifdef VL_DEBUG
VL_ATTR_COLD void VMySyncReadMem___024root___dump_triggers__nba(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___dump_triggers__nba\n"); );
    // Body
    if ((1U & (~ (IData)(vlSelf->__VnbaTriggered.any())))) {
        VL_DBG_MSGF("         No triggers active\n");
    }
    if ((1ULL & vlSelf->__VnbaTriggered.word(0U))) {
        VL_DBG_MSGF("         'nba' region trigger index 0 is active: @(posedge clock)\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD void VMySyncReadMem___024root___ctor_var_reset(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___ctor_var_reset\n"); );
    // Body
    vlSelf->clock = 0;
    vlSelf->reset = 0;
    vlSelf->io_addr = 0;
    vlSelf->io_writeData = 0;
    vlSelf->io_writeMask = 0;
    vlSelf->io_Enable = 0;
    vlSelf->io_IsWrite = 0;
    vlSelf->io_readData = 0;
    vlSelf->MySyncReadMem__DOT____Vcellinp__mem_ext__W0_en = 0;
    for (int __Vi0 = 0; __Vi0 < 32; ++__Vi0) {
        vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[__Vi0] = 0;
    }
    vlSelf->MySyncReadMem__DOT__mem_ext__DOT___R0_en_d0 = 0;
    vlSelf->MySyncReadMem__DOT__mem_ext__DOT___R0_addr_d0 = 0;
    vlSelf->__Vtrigprevexpr___TOP__clock__0 = 0;
    for (int __Vi0 = 0; __Vi0 < 2; ++__Vi0) {
        vlSelf->__Vm_traceActivity[__Vi0] = 0;
    }
}
