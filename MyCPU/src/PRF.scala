package MyCPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class PRF extends Module {
    val io = IO(new Bundle {
        val read_reqs = Input(Vec(CPUConfig.FETCH_WIDTH * 2, new PRFReadReq));
        val write_reqs = Input(Vec(CPUConfig.FETCH_WIDTH, new PRFWriteReq));
        val read_res = Output(Vec(CPUConfig.FETCH_WIDTH * 2, new PRFReadRes));
    });

    // Labels
    val names = Seq.tabulate(CPUConfig.FETCH_WIDTH)(
        t => "PRF_DualPortSram2R1W_" + t.toString()
    );

    // srams
    var prf_sram_vec = Seq.tabulate(CPUConfig.FETCH_WIDTH)(
        t => Module(new DualPortSram2R1W(names(t), CPUConfig.ADDR_WIDTH, CPUConfig.DATA_WIDTH))
    );

    // connect
    for(i <- 0 until CPUConfig.FETCH_WIDTH) {
        prf_sram_vec(i).io.en0 := io.read_reqs(2 * i).en;
        prf_sram_vec(i).io.en1 := io.read_reqs(2 * i + 1).en;

        prf_sram_vec(i).io.raddr0 := io.read_reqs(2 * i).addr;
        prf_sram_vec(i).io.raddr1 := io.read_reqs(2 * i + 1).addr;

        io.read_res(2 * i).data := prf_sram_vec(i).io.rdata0;
        io.read_res(2 * i + 1).data := prf_sram_vec(i).io.rdata1;

        prf_sram_vec(i).io.wen := io.write_reqs(i).en;
        prf_sram_vec(i).io.waddr := io.write_reqs(i).addr;
        prf_sram_vec(i).io.wmask := io.write_reqs(i).mask;
        prf_sram_vec(i).io.wdata := io.write_reqs(i).data;       
    }
} 