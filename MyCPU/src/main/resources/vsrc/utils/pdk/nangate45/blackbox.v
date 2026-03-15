module fakeram45_64x96 (
    input clk,
    input [5:0] addr_in,
    input [95:0] wd_in,
    input we_in,
    input ce_in,
    output [95:0] rd_out,
    input [95:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule


module fakeram45_64x15 (
    input clk,
    input [5:0] addr_in,
    input [14:0] wd_in,
    input we_in,
    input ce_in,
    output [14:0] rd_out,
    input [14:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule

module fakeram45_64x7 (
    input clk,
    input [5:0] addr_in,
    input [6:0] wd_in,
    input we_in,
    input ce_in,
    output [6:0] rd_out,
    input [6:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule


module fakeram45_512x64 (
    input clk,
    input [8:0] addr_in,
    input [63:0] wd_in,
    input we_in,
    input ce_in,
    output [63:0] rd_out,
    input [63:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule


module fakeram45_256x95 (
    input clk,
    input [7:0] addr_in,
    input [94:0] wd_in,
    input we_in,
    input ce_in,
    output [94:0] rd_out,
    input [94:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule


module fakeram45_1024x32 (
    input clk,
    input [9:0] addr_in,
    input [31:0] wd_in,
    input we_in,
    input ce_in,
    output [31:0] rd_out,
    input [31:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule


module fakeram45_256x34 (
    input clk,
    input [7:0] addr_in,
    input [33:0] wd_in,
    input we_in,
    input ce_in,
    output [33:0] rd_out,
    input [33:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule


module fakeram45_32x64 (
    input clk,                // 时钟信号
    input [31:0] addr_in,     // 32 位地址输入
    input ce_in,              // 启用信号
    output reg [63:0] rd_out, // 64 位数据输出
    input [63:0] w_mask_in,   // 写掩码
    input [63:0] wd_in,       // 写入数据
    input we_in               // 写使能信号
);
endmodule


module fakeram45_64x32 (
    input clk,
    input [5:0] addr_in,
    input [31:0] wd_in,
    input we_in,
    input ce_in,
    output [31:0] rd_out,
    input [31:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule


module fakeram45_256x96 (
    input clk,
    input [7:0] addr_in,
    input [95:0] wd_in,
    input we_in,
    input ce_in,
    output [95:0] rd_out,
    input [95:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule


module fakeram45_2048x39 (
    input clk,
    input [10:0] addr_in,
    input [38:0] wd_in,
    input we_in,
    input ce_in,
    output [38:0] rd_out,
    input [38:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule


module fakeram45_64x21 (
    input clk,
    input [5:0] addr_in,
    input [20:0] wd_in,
    input we_in,
    input ce_in,
    output [20:0] rd_out,
    input [20:0] w_mask_in
);
    // Black-box definition, no internal implementation
    // The functionality is not described here
endmodule