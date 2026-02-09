import os
import re
from collections import defaultdict

def parse_lef_file(lef_file):
    """
    解析 LEF 文件，提取端口名称、方向以及位宽信息。
    """
    with open(lef_file, 'r') as f:
        lef_content = f.read()

    # 正则表达式提取 PIN 和 DIRECTION
    pin_pattern = re.compile(r'PIN (\S+)\s+.*DIRECTION (\S+)')
    pins = re.findall(pin_pattern, lef_content)

    # 将端口名按名称分组（例如 din0 -> din0[0]...din0[43]）
    grouped_pins = defaultdict(list)
    for pin, direction in pins:
        base_name = re.sub(r'\[\d+\]', '', pin)  # 去掉方括号和索引，得到基本端口名
        grouped_pins[base_name].append((pin, direction))

    return grouped_pins

def generate_verilog_blackbox(module_name, grouped_pins):
    """
    生成 Verilog 黑盒模块代码，将相同端口名的端口合并为数组。
    """
    verilog_code = f"(* blackbox *)module {module_name} (\n"
    
    # 输入端口
    for base_name, pin_list in grouped_pins.items():
        # # 获取方向，假设所有相同名称的端口方向相同
        # direction = pin_list[0][1]
        
        # # 判断是否是数组类型（即端口带有 [ ] 的）
        # if '[' in pin_list[0][0]:
        #     bit_range = f"[{len(pin_list)-1}:0]"  # 生成数组位宽
        # else:
        #     bit_range = ""
        
        # # 根据端口方向生成 Verilog 代码
        # if direction == "INOUT":
        #     verilog_code += f"    inout {base_name},\n"
        # elif direction == "INPUT":
        #     verilog_code += f"    input {base_name},\n"
        # elif direction == "OUTPUT":
        #     verilog_code += f"    output {base_name},\n"
        if(base_name == 'VSS' or base_name == 'VDD'):
            continue
        verilog_code += f"{base_name},"
    # 去除最后一个逗号和换行
    verilog_code = verilog_code.rstrip(',\n') + ");\n"
    for base_name, pin_list in grouped_pins.items():
        if(base_name == 'VSS' or base_name == 'VDD'):
            continue
        # 获取方向，假设所有相同名称的端口方向相同
        direction = pin_list[0][1]
        
        # 判断是否是数组类型（即端口带有 [ ] 的）
        if '[' in pin_list[0][0]:
            bit_range = f"[{len(pin_list)-1}:0]"  # 生成数组位宽
        else:
            bit_range = ""
        
        # 根据端口方向生成 Verilog 代码
        if direction == "INOUT":
            verilog_code += f"    inout {bit_range} {base_name};\n"
        elif direction == "INPUT":
            verilog_code += f"    input {bit_range} {base_name};\n"
        elif direction == "OUTPUT":
            verilog_code += f"    output {bit_range} {base_name};\n"
    verilog_code += "endmodule\n\n"  # 多加一个换行方便区分模块
    
    return verilog_code

def process_lef_files(lef_dir, verilog_out_file):
    """
    遍历 LEF 文件夹中的所有 LEF 文件，生成相应的 Verilog 黑盒代码并写入一个文件。
    """
    with open(verilog_out_file, 'w') as verilog_file:
        # 遍历 LEF 文件夹
        for filename in os.listdir(lef_dir):
            if filename.endswith(".lef"):
                lef_file_path = os.path.join(lef_dir, filename)
                module_name = os.path.splitext(filename)[0]  # 使用文件名作为模块名

                print(f"Processing LEF file: {lef_file_path}")

                # 解析 LEF 文件
                grouped_pins = parse_lef_file(lef_file_path)

                # 生成 Verilog 黑盒模块代码
                verilog_code = generate_verilog_blackbox(module_name, grouped_pins)

                # 将 Verilog 代码写入到指定的文件中
                verilog_file.write(verilog_code)

                print(f"Appended Verilog blackbox for {module_name} to {verilog_out_file}")

def main():
    # 设置 LEF 文件夹路径和 Verilog 输出文件路径
    lef_dir = './lef'  # 这里替换为你的 LEF 文件夹路径
    verilog_out_file = './verilog/blackbox.v'  # 这里替换为你希望保存所有黑盒模块的 Verilog 输出文件

    # 如果输出文件已存在，先清空文件
    if os.path.exists(verilog_out_file):
        os.remove(verilog_out_file)

    # 处理 LEF 文件夹下的所有 LEF 文件并生成 Verilog 黑盒模块代码
    process_lef_files(lef_dir, verilog_out_file)
    print(f"All LEF files processed successfully, all blackboxes are written to {verilog_out_file}!")

if __name__ == "__main__":
    main()
