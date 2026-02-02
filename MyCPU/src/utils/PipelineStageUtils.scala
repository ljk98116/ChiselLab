package MyCPU.utils

import chisel3._
import chisel3.util._

class PipelineStageNaive(width: Int) extends Module {

    val io = IO(new Bundle {
        val in = Input(UInt(width.W));
        val out = Output(UInt(width.W));
        val stall = Input(Bool());
        val flush = Input(Bool());
    })

    var stageReg = RegInit((0.U)(width.W));

    stageReg := MuxCase(
        io.in,
        Array(
            io.stall -> stageReg,
            io.flush -> 0.U
        ).toSeq
    );

    io.out := stageReg;
}

class PipelineRegisterStages [T <: Data] (gen: T, stages: Int) extends Module {

    val io = IO(new Bundle {
        val in = Input(gen);
        val out = Output(gen);
        val stall = Input(UInt(gen.getWidth.W));
        val flush = Input(UInt(gen.getWidth.W));
    });

    var stageRegArray = RegInit(VecInit(
        Seq.fill(stages)(0.U.asTypeOf(gen))
    ));

    for(i <- 0 until stages) {
        var defaultValue = io.in;
        if(i > 0) {
            defaultValue = stageRegArray(i - 1);
        }
        stageRegArray(i) := MuxCase(
            defaultValue,
            Array(
                io.stall(i) -> stageRegArray(i),
                io.flush(i) -> 0.U.asTypeOf(gen)
            ).toSeq
        );
    }

    io.out := stageRegArray(stages - 1);
}

class PipelineRegisterStage [T<: Data] (gen: T) extends Module {
    val io = IO(new Bundle {
        val in = Input(gen);
        val out = Output(gen);
        val stall = Input(Bool());
        val flush = Input(Bool());
    });

    var stageReg = RegInit(0.U.asTypeOf(gen));

    stageReg := MuxCase(
        io.in,
        Array(
            io.stall -> stageReg,
            io.flush -> 0.U
        ).toSeq
    );

    io.out := stageReg;
}

object StageConnect {
    def apply[T <: Data](left: DecoupledIO[T], right: DecoupledIO[T]): Unit = {
        val arch = "riscv32E";
        left <> right;
    }
}