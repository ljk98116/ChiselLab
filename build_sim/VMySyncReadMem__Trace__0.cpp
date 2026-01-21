// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Tracing implementation internals
#include "verilated_fst_c.h"
#include "VMySyncReadMem__Syms.h"


void VMySyncReadMem___024root__trace_chg_0_sub_0(VMySyncReadMem___024root* vlSelf, VerilatedFst::Buffer* bufp);

void VMySyncReadMem___024root__trace_chg_0(void* voidSelf, VerilatedFst::Buffer* bufp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root__trace_chg_0\n"); );
    // Init
    VMySyncReadMem___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<VMySyncReadMem___024root*>(voidSelf);
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    if (VL_UNLIKELY(!vlSymsp->__Vm_activity)) return;
    // Body
    VMySyncReadMem___024root__trace_chg_0_sub_0((&vlSymsp->TOP), bufp);
}

void VMySyncReadMem___024root__trace_chg_0_sub_0(VMySyncReadMem___024root* vlSelf, VerilatedFst::Buffer* bufp) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root__trace_chg_0_sub_0\n"); );
    // Init
    uint32_t* const oldp VL_ATTR_UNUSED = bufp->oldp(vlSymsp->__Vm_baseCode + 1);
    // Body
    if (VL_UNLIKELY(vlSelf->__Vm_traceActivity[1U])) {
        bufp->chgQData(oldp+0,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[0]),64);
        bufp->chgQData(oldp+2,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[1]),64);
        bufp->chgQData(oldp+4,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[2]),64);
        bufp->chgQData(oldp+6,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[3]),64);
        bufp->chgQData(oldp+8,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[4]),64);
        bufp->chgQData(oldp+10,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[5]),64);
        bufp->chgQData(oldp+12,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[6]),64);
        bufp->chgQData(oldp+14,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[7]),64);
        bufp->chgQData(oldp+16,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[8]),64);
        bufp->chgQData(oldp+18,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[9]),64);
        bufp->chgQData(oldp+20,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[10]),64);
        bufp->chgQData(oldp+22,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[11]),64);
        bufp->chgQData(oldp+24,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[12]),64);
        bufp->chgQData(oldp+26,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[13]),64);
        bufp->chgQData(oldp+28,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[14]),64);
        bufp->chgQData(oldp+30,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[15]),64);
        bufp->chgQData(oldp+32,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[16]),64);
        bufp->chgQData(oldp+34,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[17]),64);
        bufp->chgQData(oldp+36,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[18]),64);
        bufp->chgQData(oldp+38,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[19]),64);
        bufp->chgQData(oldp+40,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[20]),64);
        bufp->chgQData(oldp+42,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[21]),64);
        bufp->chgQData(oldp+44,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[22]),64);
        bufp->chgQData(oldp+46,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[23]),64);
        bufp->chgQData(oldp+48,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[24]),64);
        bufp->chgQData(oldp+50,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[25]),64);
        bufp->chgQData(oldp+52,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[26]),64);
        bufp->chgQData(oldp+54,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[27]),64);
        bufp->chgQData(oldp+56,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[28]),64);
        bufp->chgQData(oldp+58,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[29]),64);
        bufp->chgQData(oldp+60,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[30]),64);
        bufp->chgQData(oldp+62,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[31]),64);
    }
    bufp->chgBit(oldp+64,(vlSelf->clock));
    bufp->chgBit(oldp+65,(vlSelf->reset));
    bufp->chgCData(oldp+66,(vlSelf->io_addr),5);
    bufp->chgQData(oldp+67,(vlSelf->io_writeData),64);
    bufp->chgQData(oldp+69,(vlSelf->io_writeMask),64);
    bufp->chgBit(oldp+71,(vlSelf->io_Enable));
    bufp->chgBit(oldp+72,(vlSelf->io_IsWrite));
    bufp->chgQData(oldp+73,(vlSelf->io_readData),64);
    bufp->chgBit(oldp+75,(vlSelf->MySyncReadMem__DOT____Vcellinp__mem_ext__W0_en));
}

void VMySyncReadMem___024root__trace_cleanup(void* voidSelf, VerilatedFst* /*unused*/) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root__trace_cleanup\n"); );
    // Init
    VMySyncReadMem___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<VMySyncReadMem___024root*>(voidSelf);
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    // Body
    vlSymsp->__Vm_activity = false;
    vlSymsp->TOP.__Vm_traceActivity[0U] = 0U;
    vlSymsp->TOP.__Vm_traceActivity[1U] = 0U;
}
