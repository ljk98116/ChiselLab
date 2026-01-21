// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VMySyncReadMem.h for the primary calling header

#include "VMySyncReadMem__pch.h"
#include "VMySyncReadMem__Syms.h"
#include "VMySyncReadMem___024root.h"

#ifdef VL_DEBUG
VL_ATTR_COLD void VMySyncReadMem___024root___dump_triggers__ico(VMySyncReadMem___024root* vlSelf);
#endif  // VL_DEBUG

void VMySyncReadMem___024root___eval_triggers__ico(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___eval_triggers__ico\n"); );
    // Body
    vlSelf->__VicoTriggered.set(0U, (IData)(vlSelf->__VicoFirstIteration));
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        VMySyncReadMem___024root___dump_triggers__ico(vlSelf);
    }
#endif
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VMySyncReadMem___024root___dump_triggers__act(VMySyncReadMem___024root* vlSelf);
#endif  // VL_DEBUG

void VMySyncReadMem___024root___eval_triggers__act(VMySyncReadMem___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root___eval_triggers__act\n"); );
    // Body
    vlSelf->__VactTriggered.set(0U, ((IData)(vlSelf->clock) 
                                     & (~ (IData)(vlSelf->__Vtrigprevexpr___TOP__clock__0))));
    vlSelf->__Vtrigprevexpr___TOP__clock__0 = vlSelf->clock;
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        VMySyncReadMem___024root___dump_triggers__act(vlSelf);
    }
#endif
}
