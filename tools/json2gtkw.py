#!/usr/bin/env python3
"""
将 signal_map.json 转换为 GTKWave 的 .gtkw 保存文件。
用法：
    python json_to_gtkw.py signal_map.json --top dut --vcd my.vcd -o wave.gtkw
"""

import json
import argparse

def generate_gtkw(json_file, top_inst, vcd_file, output_file):
    with open(json_file, 'r') as f:
        mapping = json.load(f)

    if not mapping:
        print("Error: mapping is empty")
        return

    # 提取模块名（第一个 key 的第一个点之前的部分）
    first_key = next(iter(mapping))
    module_name = first_key.split('.')[0] if '.' in first_key else ''

    # 构建路径前缀
    if module_name:
        if top_inst == module_name:
            prefix = module_name          # 去重：只保留一层
        else:
            prefix = f"{top_inst}.{module_name}"
    else:
        prefix = top_inst

    with open(output_file, 'w') as f:
        f.write("[*]\n")
        if vcd_file:
            f.write(f'[dumpfile] "{vcd_file}"\n')
        f.write("[width] 0\n")
        f.write("[height] 0\n")
        f.write("\n")

        for key, alias in mapping.items():
            signal_name = key.split('.')[-1]
            vcd_path = f"{prefix}.{signal_name}" if prefix else signal_name
            # 正确的格式：+{别名} 信号路径
            f.write(f'+{{{alias}}} {vcd_path}\n')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('json_file', help='输入 JSON 映射文件')
    parser.add_argument('--top', required=True, help='顶层实例名（例如 dut, DivUnit）')
    parser.add_argument('--vcd', help='VCD 文件路径（可选）')
    parser.add_argument('--output', '-o', default='wave.gtkw', help='输出 .gtkw 文件')
    args = parser.parse_args()

    generate_gtkw(args.json_file, args.top, args.vcd, args.output)
    print(f"Generated {args.output} with top='{args.top}'.")