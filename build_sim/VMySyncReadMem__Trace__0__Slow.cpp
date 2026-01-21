// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Tracing implementation internals
#include "verilated_fst_c.h"
#include "VMySyncReadMem__Syms.h"


VL_ATTR_COLD void VMySyncReadMem___024root__trace_init_sub__TOP__0(VMySyncReadMem___024root* vlSelf, VerilatedFst* tracep) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root__trace_init_sub__TOP__0\n"); );
    // Init
    const int c = vlSymsp->__Vm_baseCode;
    // Body
    tracep->declBit(c+65,0,"clock",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+66,0,"reset",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBus(c+67,0,"io_addr",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 4,0);
    tracep->declQuad(c+68,0,"io_writeData",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 63,0);
    tracep->declQuad(c+70,0,"io_writeMask",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 63,0);
    tracep->declBit(c+72,0,"io_Enable",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+73,0,"io_IsWrite",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declQuad(c+74,0,"io_readData",-1, VerilatedTraceSigDirection::OUTPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 63,0);
    tracep->pushPrefix("MySyncReadMem", VerilatedTracePrefixType::SCOPE_MODULE);
    tracep->declBit(c+65,0,"clock",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+66,0,"reset",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBus(c+67,0,"io_addr",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 4,0);
    tracep->declQuad(c+68,0,"io_writeData",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 63,0);
    tracep->declQuad(c+70,0,"io_writeMask",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 63,0);
    tracep->declBit(c+72,0,"io_Enable",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+73,0,"io_IsWrite",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declQuad(c+74,0,"io_readData",-1, VerilatedTraceSigDirection::OUTPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 63,0);
    tracep->pushPrefix("mem_ext", VerilatedTracePrefixType::SCOPE_MODULE);
    tracep->declBus(c+67,0,"R0_addr",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 4,0);
    tracep->declBit(c+72,0,"R0_en",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+65,0,"R0_clk",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declQuad(c+74,0,"R0_data",-1, VerilatedTraceSigDirection::OUTPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 63,0);
    tracep->declBus(c+67,0,"W0_addr",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 4,0);
    tracep->declBit(c+76,0,"W0_en",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+65,0,"W0_clk",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declQuad(c+68,0,"W0_data",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 63,0);
    tracep->declQuad(c+70,0,"W0_mask",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 63,0);
    tracep->pushPrefix("Memory", VerilatedTracePrefixType::ARRAY_UNPACKED);
    for (int i = 0; i < 32; ++i) {
        tracep->declQuad(c+1+i*2,0,"",-1, VerilatedTraceSigDirection::NONE, VerilatedTraceSigKind::VAR, VerilatedTraceSigType::LOGIC, true,(i+0), 63,0);
    }
    tracep->popPrefix();
    tracep->popPrefix();
    tracep->popPrefix();
}

VL_ATTR_COLD void VMySyncReadMem___024root__trace_init_top(VMySyncReadMem___024root* vlSelf, VerilatedFst* tracep) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root__trace_init_top\n"); );
    // Body
    VMySyncReadMem___024root__trace_init_sub__TOP__0(vlSelf, tracep);
}

VL_ATTR_COLD void VMySyncReadMem___024root__trace_const_0(void* voidSelf, VerilatedFst::Buffer* bufp);
VL_ATTR_COLD void VMySyncReadMem___024root__trace_full_0(void* voidSelf, VerilatedFst::Buffer* bufp);
void VMySyncReadMem___024root__trace_chg_0(void* voidSelf, VerilatedFst::Buffer* bufp);
void VMySyncReadMem___024root__trace_cleanup(void* voidSelf, VerilatedFst* /*unused*/);

VL_ATTR_COLD void VMySyncReadMem___024root__trace_register(VMySyncReadMem___024root* vlSelf, VerilatedFst* tracep) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root__trace_register\n"); );
    // Body
    tracep->addConstCb(&VMySyncReadMem___024root__trace_const_0, 0U, vlSelf);
    tracep->addFullCb(&VMySyncReadMem___024root__trace_full_0, 0U, vlSelf);
    tracep->addChgCb(&VMySyncReadMem___024root__trace_chg_0, 0U, vlSelf);
    tracep->addCleanupCb(&VMySyncReadMem___024root__trace_cleanup, vlSelf);
}

VL_ATTR_COLD void VMySyncReadMem___024root__trace_const_0(void* voidSelf, VerilatedFst::Buffer* bufp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root__trace_const_0\n"); );
    // Init
    VMySyncReadMem___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<VMySyncReadMem___024root*>(voidSelf);
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
}

VL_ATTR_COLD void VMySyncReadMem___024root__trace_full_0_sub_0(VMySyncReadMem___024root* vlSelf, VerilatedFst::Buffer* bufp);

VL_ATTR_COLD void VMySyncReadMem___024root__trace_full_0(void* voidSelf, VerilatedFst::Buffer* bufp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root__trace_full_0\n"); );
    // Init
    VMySyncReadMem___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<VMySyncReadMem___024root*>(voidSelf);
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    // Body
    VMySyncReadMem___024root__trace_full_0_sub_0((&vlSymsp->TOP), bufp);
}

VL_ATTR_COLD void VMySyncReadMem___024root__trace_full_0_sub_0(VMySyncReadMem___024root* vlSelf, VerilatedFst::Buffer* bufp) {
    if (false && vlSelf) {}  // Prevent unused
    VMySyncReadMem__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    VMySyncReadMem___024root__trace_full_0_sub_0\n"); );
    // Init
    uint32_t* const oldp VL_ATTR_UNUSED = bufp->oldp(vlSymsp->__Vm_baseCode);
    // Body
    bufp->fullQData(oldp+1,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[0]),64);
    bufp->fullQData(oldp+3,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[1]),64);
    bufp->fullQData(oldp+5,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[2]),64);
    bufp->fullQData(oldp+7,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[3]),64);
    bufp->fullQData(oldp+9,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[4]),64);
    bufp->fullQData(oldp+11,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[5]),64);
    bufp->fullQData(oldp+13,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[6]),64);
    bufp->fullQData(oldp+15,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[7]),64);
    bufp->fullQData(oldp+17,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[8]),64);
    bufp->fullQData(oldp+19,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[9]),64);
    bufp->fullQData(oldp+21,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[10]),64);
    bufp->fullQData(oldp+23,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[11]),64);
    bufp->fullQData(oldp+25,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[12]),64);
    bufp->fullQData(oldp+27,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[13]),64);
    bufp->fullQData(oldp+29,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[14]),64);
    bufp->fullQData(oldp+31,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[15]),64);
    bufp->fullQData(oldp+33,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[16]),64);
    bufp->fullQData(oldp+35,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[17]),64);
    bufp->fullQData(oldp+37,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[18]),64);
    bufp->fullQData(oldp+39,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[19]),64);
    bufp->fullQData(oldp+41,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[20]),64);
    bufp->fullQData(oldp+43,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[21]),64);
    bufp->fullQData(oldp+45,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[22]),64);
    bufp->fullQData(oldp+47,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[23]),64);
    bufp->fullQData(oldp+49,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[24]),64);
    bufp->fullQData(oldp+51,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[25]),64);
    bufp->fullQData(oldp+53,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[26]),64);
    bufp->fullQData(oldp+55,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[27]),64);
    bufp->fullQData(oldp+57,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[28]),64);
    bufp->fullQData(oldp+59,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[29]),64);
    bufp->fullQData(oldp+61,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[30]),64);
    bufp->fullQData(oldp+63,(vlSelf->MySyncReadMem__DOT__mem_ext__DOT__Memory[31]),64);
    bufp->fullBit(oldp+65,(vlSelf->clock));
    bufp->fullBit(oldp+66,(vlSelf->reset));
    bufp->fullCData(oldp+67,(vlSelf->io_addr),5);
    bufp->fullQData(oldp+68,(vlSelf->io_writeData),64);
    bufp->fullQData(oldp+70,(vlSelf->io_writeMask),64);
    bufp->fullBit(oldp+72,(vlSelf->io_Enable));
    bufp->fullBit(oldp+73,(vlSelf->io_IsWrite));
    bufp->fullQData(oldp+74,(vlSelf->io_readData),64);
    bufp->fullBit(oldp+76,(vlSelf->MySyncReadMem__DOT____Vcellinp__mem_ext__W0_en));
}
