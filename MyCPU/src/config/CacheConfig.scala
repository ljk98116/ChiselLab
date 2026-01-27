package MyCPU.config

object CacheConfig {
    // icache
    val icache_line_idx_width = 10; // 1KB x 16 x 4bytes = 64KB
    val icache_way_idx_width = 2;
    val icache_off_idx_width = 4;
    val icache_tag_width = CPUConfig.ADDR_WIDTH - 2 - icache_way_idx_width - icache_off_idx_width - icache_line_idx_width; // 14 bits
    val icache_line_data_num = 1 << icache_off_idx_width;

    //dcache
    val dcache_line_idx_width = 10; // 1KB x 16 x 4bytes = 64KB
    val dcache_way_idx_width = 2;
    val dcache_off_idx_width = 4;
    val dcache_tag_width = CPUConfig.ADDR_WIDTH - 2 - dcache_way_idx_width - dcache_off_idx_width - dcache_line_idx_width; // 14 bits
    val dcache_line_data_num = 1 << dcache_off_idx_width;
}