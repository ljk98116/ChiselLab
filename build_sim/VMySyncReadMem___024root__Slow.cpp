// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VMySyncReadMem.h for the primary calling header

#include "VMySyncReadMem__pch.h"
#include "VMySyncReadMem__Syms.h"
#include "VMySyncReadMem___024root.h"

void VMySyncReadMem___024root___ctor_var_reset(VMySyncReadMem___024root* vlSelf);

VMySyncReadMem___024root::VMySyncReadMem___024root(VMySyncReadMem__Syms* symsp, const char* v__name)
    : VerilatedModule{v__name}
    , vlSymsp{symsp}
 {
    // Reset structure values
    VMySyncReadMem___024root___ctor_var_reset(this);
}

void VMySyncReadMem___024root::__Vconfigure(bool first) {
    if (false && first) {}  // Prevent unused
}

VMySyncReadMem___024root::~VMySyncReadMem___024root() {
}
