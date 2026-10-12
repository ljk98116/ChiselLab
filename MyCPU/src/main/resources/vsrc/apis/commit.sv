`timescale 1ns/1ps

import "DPI-C" function void commit(
  input int hart_id, inout bits[63: 0] pcs[], input int num 
);

module Commit #(
  parameter int hart_id = 0
) (
  input io_clock,
  input io_reset,
  input [3: 0] io_valids,
  input [3: 0][63: 0] io_pcs,
);

  wire [1 : 0] valid_count;
  assign valid_count = io_valids[0] + io_valids[1] + io_valids[2] + io_valids[3];
  always_comb begin
    if(!io_reset && |io_valids) begin
      commit(hart_id, io_pcs, valid_count);
    end
  end

endmodule