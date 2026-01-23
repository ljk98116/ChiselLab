`timescale 1ns/1ps

`include "config.v"

`ifdef SIMFLAG
// DPI-C
import "DPI-C" function void DualPortSram1R1W_write(input string label, input longint waddr, input longint wmask, input longint wdata);
import "DPI-C" function longint DualPortSram1R1W_read(input string label, input longint raddr);
`endif

// DEPTH x DATA_WIDTH
module BlackBoxDualPortSram1R1W #(
    parameter ADDR_WIDTH = 8,
    parameter DATA_WIDTH = 32,
    parameter LABEL = ""
)(
    input clk,
    input rst,
    input [ADDR_WIDTH - 1: 0] raddr,
    input [ADDR_WIDTH - 1: 0] waddr,
    input en,
    input wen,
    input [DATA_WIDTH - 1: 0] wmask,
    input [DATA_WIDTH - 1: 0] wdata,
    output [DATA_WIDTH - 1: 0] rdata
);
`ifndef SIMFLAG
    reg [DATA_WIDTH - 1 : 0] mem[0 : 2 ** ADDR_WIDTH - 1];
    reg [DATA_WIDTH - 1 : 0] dout;
    always @(posedge clk) begin
        if(!rst) begin
            integer i;
            for(i=0;i<2 ** ADDR_WIDTH;i=i+1) begin
                mem[i] <= 0;
            end
            dout <= 0;
        end
        else begin
            if(en & wen) begin
                mem[waddr] <= (mem[waddr] & ~wmask) | (wdata & wmask); // clear and reset
            end
            if(en) begin
                dout <= mem[raddr];
            end                      
        end
    end
    assign rdata = dout;
`else
    reg [DATA_WIDTH - 1 : 0] dout;
    always @(posedge clk) begin
        if(!rst) begin
            integer i;
            for(i=0;i<2 ** ADDR_WIDTH;i=i+1) begin
                DualPortSram1R1W_write(LABEL, i, 64'h0, 64'h0);
            end
            dout <= 0;            
        end
        else begin
            if(en & wen) begin
                DualPortSram1R1W_write(LABEL, waddr, wmask, wdata);
            end
            if(en) begin
                dout <= DualPortSram1R1W_read(LABEL, raddr);
            end
        end
    end
    assign rdata = dout;
`endif

endmodule


