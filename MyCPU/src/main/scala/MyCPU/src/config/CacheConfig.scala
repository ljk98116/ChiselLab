package MyCPU.config

import org.chipsalliance.cde.config._

case object icache_line_idx_width extends WidthConfig
case object icache_way_idx_width extends WidthConfig
case object icache_off_idx_width extends WidthConfig
case object icache_tag_width extends WidthConfig
case object icache_line_data_num extends WidthConfig

case object dcache_line_idx_width extends WidthConfig
case object dcache_way_idx_width extends WidthConfig
case object dcache_off_idx_width extends WidthConfig
case object dcache_tag_width extends WidthConfig
case object dcache_line_data_num extends WidthConfig

class CacheConfig(implicit p: Parameters) extends Config(
    (site, here, up) => {
        // icache
        case `icache_line_idx_width` => 10; // 1KB x 16 x 4bytes = 64KB
        case `icache_way_idx_width` => 2;
        case `icache_off_idx_width` => 4;
        case `icache_tag_width` => p(ADDR_WIDTH) - 2 - here(icache_way_idx_width) - here(icache_off_idx_width) - here(icache_line_idx_width); // 14 bits
        case `icache_line_data_num` => 1 << here(icache_off_idx_width);

        //dcache
        case `dcache_line_idx_width` => 10; // 1KB x 16 x 4bytes = 64KB
        case `dcache_way_idx_width` => 2;
        case `dcache_off_idx_width` => 4;
        case `dcache_tag_width` => p(ADDR_WIDTH) - 2 - here(dcache_way_idx_width) - here(dcache_off_idx_width) - here(dcache_line_idx_width); // 14 bits
        case `dcache_line_data_num` => 1 << here(dcache_off_idx_width);
    }
)