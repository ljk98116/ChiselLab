#!/usr/bin/env python3
"""
从 Verilog 和 Scala 源码中提取信号映射（包括 IO、reg、wire）。
用法:
    python extract_signal_map.py <verilog_file> [scala_root] [output_json]
示例:
    python extract_signal_map.py DivUnit.sv src/main/scala signal_map.json
"""

import re
import sys
import json
from pathlib import Path

def parse_verilog_signals(verilog_path):
    """解析 Verilog 文件，返回模块名和信号字典 {信号名: 类型}"""
    with open(verilog_path, 'r') as f:
        content = f.read()

    # 去除注释（只去除行注释和块注释，但保留换行）
    content = re.sub(r'//.*?$', '', content, flags=re.MULTILINE)
    content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)

    # 提取模块名
    mod_match = re.search(r'\bmodule\s+(\w+)', content)
    if not mod_match:
        raise ValueError("Cannot find module name")
    module_name = mod_match.group(1)

    # 提取模块主体（从 "module xxx (" 到 endmodule）
    # 注意：端口可能跨行，所以先获取整个模块体
    module_body_match = re.search(
        r'module\s+\w+\s*(?:\(([^)]*)\))?\s*;\s*(.*?)\s*endmodule',
        content, re.DOTALL
    )
    if not module_body_match:
        raise ValueError("Cannot find module body")
    port_list_str = module_body_match.group(1) or ''
    body = module_body_match.group(2)

    signals = {}

    # --- 1. 解析端口 ---
    # 端口列表可能是多行的，按逗号分割，但需注意方向分组
    if port_list_str.strip():
        # 按逗号分割端口项，但忽略括号内的逗号（这里没有）
        port_items = [p.strip() for p in port_list_str.split(',') if p.strip()]
        current_dir = None  # 当前方向，用于后续没有方向的行
        for item in port_items:
            # 检查是否包含方向关键字
            dir_match = re.match(r'\b(input|output|inout)\b', item)
            if dir_match:
                current_dir = dir_match.group(1)
                # 去除方向关键字及后续可能的多余空格
                rest = re.sub(r'\b(input|output|inout)\s+', '', item)
            else:
                rest = item
            if current_dir is None:
                continue  # 没有方向且之前没有，忽略
            # 提取信号名：可能是最后一个单词，也可能有位宽前缀
            # 例如 "clock", "io_divResult", "io_divReady );" 等
            # 去掉可能的逗号、分号、括号
            rest = re.sub(r'[;,)]$', '', rest).strip()
            # 提取最后一个单词作为信号名
            name_match = re.search(r'(\w+)\s*$', rest)
            if name_match:
                sig_name = name_match.group(1)
                signals[sig_name] = current_dir

    # --- 2. 解析内部 reg/wire ---
    # 匹配形如 "reg [5:0] cycleCounter;" 或 "wire io_divReady_0 = ...;"
    internal_re = re.compile(
        r'\b(reg|wire)\s+(?:signed\s+)?(?:\[[^\]]*\]\s+)?(\w+)\s*(?:=.*?)?[;,)]'
    )
    for match in internal_re.finditer(body):
        kind, name = match.groups()
        if name not in signals:  # 避免覆盖端口（优先端口）
            signals[name] = kind

    return module_name, signals

def get_package_name(scala_root, class_name):
    """在 scala_root 中搜索 class_name 的包名"""
    if not scala_root or not Path(scala_root).is_dir():
        return ""
    for path in Path(scala_root).rglob('*.scala'):
        with open(path, 'r') as f:
            content = f.read()
        if re.search(r'\bclass\s+' + re.escape(class_name) + r'\b', content):
            pkg_match = re.search(r'^package\s+([\w.]+)', content, re.MULTILINE)
            if pkg_match:
                return pkg_match.group(1)
    return ""

def build_mapping(module_name, signals, package_name):
    """构建映射字典 { '模块.类型.信号' : '包.模块.信号' }"""
    mapping = {}
    for sig, typ in signals.items():
        key = f"{module_name}.{typ}.{sig}"
        if package_name:
            value = f"{package_name}.{module_name}.{sig}"
        else:
            value = f"{module_name}.{sig}"
        mapping[key] = value
    return mapping

def main():
    if len(sys.argv) < 2:
        print("Usage: python extract_signal_map.py <verilog_file> [scala_root] [output_json]")
        sys.exit(1)

    verilog_file = sys.argv[1]
    scala_root = sys.argv[2] if len(sys.argv) > 2 else "src/main/scala"
    output_file = sys.argv[3] if len(sys.argv) > 3 else "signal_map.json"

    module_name, signals = parse_verilog_signals(verilog_file)
    print(f"Found module '{module_name}' with {len(signals)} signals")

    package = get_package_name(scala_root, module_name)
    if package:
        print(f"Found package: {package}")
    else:
        print("Warning: Could not determine package, using module name only")

    mapping = build_mapping(module_name, signals, package)

    with open(output_file, 'w') as f:
        json.dump(mapping, f, indent=2)
    print(f"Written {len(mapping)} mappings to {output_file}")

if __name__ == '__main__':
    main()