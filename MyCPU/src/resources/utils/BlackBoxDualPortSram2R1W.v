`timescale 1ns/1ps

//`include "config.v"

`ifdef SIMFLAG
// DPI-C
import "DPI-C" function void DualPortSram2R1W_write(input string label, input longint waddr, input longint wmask, input longint wdata);
import "DPI-C" function longint DualPortSram2R1W_read(input string label, input longint raddr);
`endif

// 2 x DEPTH x DATA_WIDTH
module BlackBoxDualPortSram2R1W #(
    parameter ADDR_WIDTH = 8,
    parameter DATA_WIDTH = 32,
    parameter LABEL = ""
)(
    input clk,
    input rst,
    input [ADDR_WIDTH - 1: 0] raddr0,
    input [ADDR_WIDTH - 1: 0] raddr1,
    input [ADDR_WIDTH - 1: 0] waddr,
    input en0,
    input en1,
    input wen,
    input [DATA_WIDTH - 1: 0] wmask,
    input [DATA_WIDTH - 1: 0] wdata,
    output [DATA_WIDTH - 1: 0] rdata0,
    output [DATA_WIDTH - 1: 0] rdata1    
);
integer i;
`ifndef SIMFLAG
    reg [DATA_WIDTH - 1 : 0] mem0 [0 : 2 ** ADDR_WIDTH - 1];
    reg [DATA_WIDTH - 1 : 0] mem1 [0 : 2 ** ADDR_WIDTH - 1];

    reg [DATA_WIDTH - 1 : 0] dout0;
    reg [DATA_WIDTH - 1 : 0] dout1;
    always @(posedge clk) begin
        if(!rst) begin
            for(i=0;i<2 ** ADDR_WIDTH;i=i+1) begin
                mem0[i] <= 0;
                mem1[i] <= 0;
            end
            dout0 <= 0;
        end
        else begin
            if((en0 | en1) & wen) begin
                mem0[waddr] <= (mem0[waddr] & ~wmask) | (wdata & wmask); // clear and reset
                mem1[waddr] <= (mem1[waddr] & ~wmask) | (wdata & wmask); // clear and reset
            end
            if(en0) begin
                dout0 <= mem0[raddr0];
            end           
            if(en1) begin
                dout1 <= mem1[raddr1];
            end                       
        end
    end
    assign rdata0 = dout0;
    assign rdata1 = dout1;
`else
    reg [DATA_WIDTH - 1 : 0] dout0;
    reg [DATA_WIDTH - 1 : 0] dout1;
    always @(posedge clk) begin
        if(!rst) begin
            integer i;
            for(i=0;i<2 ** ADDR_WIDTH;i=i+1) begin
                DualPortSram2R1W_write({LABEL, "_0"}, i, 64'h0, 64'h0);
                DualPortSram2R1W_write({LABEL, "_1"}, i, 64'h0, 64'h0);
            end
            dout0 <= 0;
            dout1 <= 0;            
        end
        else begin
            if(wen) begin
                DualPortSram2R1W_write({LABEL, "_0"}, waddr, wmask, wdata);
                DualPortSram2R1W_write({LABEL, "_1"}, waddr, wmask, wdata);
            end
            if(en0) begin
                dout0 <= DualPortSram2R1W_read({LABEL, "_0"}, raddr0);
            end
            if(en1) begin
                dout1 <= DualPortSram2R1W_read({LABEL, "_1"}, raddr1);
            end
            
        end
    end
    assign rdata0 = dout0;
    assign rdata1 = dout1;    
`endif

endmodule