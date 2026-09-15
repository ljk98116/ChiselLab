`timescale 1ns/1ps

import "DPI-C" function longint sram_read(
  input int hart_id, input string name, input bit[63: 0] va, 
  input bit[63: 0] mask
);
import "DPI-C" function void sram_write(
  input int hart_id, input string name, input bit[63: 0] va, 
  input bit[63: 0] mask, input bit[63: 0] wdata
);
import "DPI-C" function void sram_register(
  input int hart_id, input string name, input bit[63: 0] value, 
  input bit[63: 0] size
);

module internal_sram_impl #(
  parameter string name = "",
  parameter int idx_width = 1,
  parameter int data_width = 1
) (
  input io_clock,
  input io_reset,
  input io_en,
  input io_wen,
  input [idx_width - 1: 0] io_addr,
  input [data_width - 1: 0] io_mask,
  input [data_width - 1: 0] io_wdata,
  output [data_width - 1: 0] io_rdata
);
  reg [idx_width - 1: 0] raddr;
  always @(posedge io_clock) begin
    if(io_reset || io_wen) begin
      raddr <= 0;
    end
    else begin
      raddr <= io_addr;
    end
  end

`ifdef SIM_FLAG
  always @(posedge io_clock) begin
    if(!io_reset) begin
      sram_write(0, name, io_addr, io_mask, io_wdata);
    end
    else begin
      sram_register(0, name, 0, (1 << idx_width));
    end
  end
  assign io_rdata = sram_read(0, name, raddr, io_mask);
`else
  reg [data_width - 1: 0] sram[0: (1 << idx_width) - 1];
  always @(posedge io_clock) begin
    if(io_reset) begin
      integer i;
      for(i = 0; i < (1 << idx_width); i = i + 1) begin
        sram[i] <= 0;
      end
    end
    else if(io_en && io_wen) begin
      sram[io_addr] <= (sram[io_addr] & ~io_mask) | (io_wdata & io_mask);
    end
  end
  assign io_rdata = sram[raddr];
`endif

endmodule