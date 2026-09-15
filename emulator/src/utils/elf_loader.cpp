#include <utils/elf_loader.hpp>
#include <dutmodels/cpu/cpu_state.hpp>

#include <fstream>
#include <iostream>
#include <cstring>

namespace emulator {

bool elf_loader::load_elf(const std::string& filepath) {
  static elf_loader loader;
  return loader.init(filepath);
}

bool elf_loader::init(const std::string& filepath) {
  std::streamsize size;
  {  
    std::ifstream file(filepath, std::ios::in | std::ios::binary);
    if(!file) {
      std::cout << "[ERROR]: open elf file:" << filepath << " failed" << std::endl;
      return false;
    }
    file.seekg(0, std::ios::end);
    size = file.tellg();
    std::cout << "read elf file:" << filepath << " has size " << size << " bytes" << std::endl;
    buffer_.clear();
    buffer_.resize(size);
    file.seekg(0, std::ios::beg);
    file.read(buffer_.data(), size);
    if (file.gcount() != size) {
      std::cout << "[ERROR]: read elf file failed, expected " << size << " bytes but got " << file.gcount() << std::endl;
      return false;
    }
    file.close();
  }

  if(!parse_elf_header()) {
    return false;
  }
  // // load section datas
  if(!load_sections()) {
    return false;
  }
  return true;
}

bool elf_loader::parse_elf_header() {
  char *buffer_ptr = buffer_.data();
  char *e_ident = buffer_ptr;
  if(memcmp(e_ident, ELFMAG, SELFMAG) != 0) {
    std::cout << "[ERROR]: elf file header is not \177ELF" << std::endl;
    return false;
  }
  char ei_class = e_ident[EI_CLASS];
  ei_class_ = ei_class;
  std::cout << "[INFO]: elf file object type: " << (ei_class == ELFCLASS32 ? "elf32" : "elf64") << std::endl;
  char ei_data = e_ident[EI_DATA];
  std::cout << "[INFO]: elf file data encoding: " << (ei_data == ELFDATA2LSB ? "little-endian" : "big-endian") << std::endl;
  char ei_version = e_ident[EI_VERSION];
  if(ei_version != EV_CURRENT) {
    std::cout << "[ERROR]: check elf file version failed, now is " << ei_version << std::endl;
    return false;
  }
  if(ei_class == ELFCLASS32) {
    Elf32_Half e_type = *(Elf32_Half*)(&buffer_ptr[EI_NIDENT]);
    if(e_type != ET_EXEC) {
      std::cout << "[ERROR]: not an executable elf32 file, abort" << std::endl;
      return false;
    }
    // skip e-machine check
    Elf32_Ehdr header = *(Elf32_Ehdr *)buffer_ptr;
    // program entry
    Elf32_Addr e_entry = header.e_entry;
    // program header offset
    Elf32_Off e_phoff = header.e_phoff;
    program_header_table_.elf32 = (Elf32_Phdr*)&buffer_ptr[e_phoff];
    // sector header offset
    Elf32_Off e_shoff = header.e_shoff;
    section_header_table_.elf32 = (Elf32_Shdr*)&buffer_ptr[e_shoff];
    // program header table size
    phnum_.elf32 = header.e_phnum;
    phtable_entry_sz_.elf32 = header.e_phentsize;
    // section header table size
    shnum_.elf32 = header.e_shnum;
    shtable_entry_sz_.elf32 = header.e_shentsize;
    // section header string table index
    shstrtable_idx_.elf32 = header.e_shstrndx;
  }
  else {
    Elf64_Half e_type = *(Elf64_Half*)(&buffer_ptr[EI_NIDENT]);
    if(e_type != ET_EXEC) {
      std::cout << "[ERROR]: not an executable elf64 file, abort" << std::endl;
      return false;
    }
    // skip e-machine check
    Elf64_Ehdr header = *(Elf64_Ehdr *)buffer_ptr;
    Elf64_Off e_phoff = header.e_phoff;
    program_header_table_.elf64 = (Elf64_Phdr*)&buffer_ptr[e_phoff];
    // sector header offset
    Elf64_Off e_shoff = header.e_shoff;
    section_header_table_.elf64 = (Elf64_Shdr*)&buffer_ptr[e_shoff];
    // program header table size
    phnum_.elf64 = header.e_phnum;
    phtable_entry_sz_.elf64 = header.e_phentsize;
    // section header table size
    shnum_.elf64 = header.e_shnum;
    shtable_entry_sz_.elf64 = header.e_shentsize;
    // section header string table index
    shstrtable_idx_.elf64 = header.e_shstrndx;
  }
  return true;
}

bool elf_loader::load_sections() {
  char *buffer_ptr = buffer_.data();
  if(ei_class_ == ELFCLASS32) {
    section_header_ptr_t strtab_hdr, dynstr_hdr, symtab_hdr, dynsymtab_hdr, shstrtab_hdr;
    size_t content_size = 0;

    shstrtab_hdr.elf32 = &(section_header_table_.elf32[shstrtable_idx_.elf32]);
    strtab_hdr.elf32 = nullptr;
    dynstr_hdr.elf32 = nullptr;
    symtab_hdr.elf32 = nullptr;

    // section string table
    char *shstrtab_ptr = &buffer_ptr[shstrtab_hdr.elf32->sh_offset];
    // first: find strtab & symtab
    for(int i = 0;i < shnum_.elf32 ; ++i) {
      Elf32_Shdr *sh_entry = &section_header_table_.elf32[i];
      char *sh_name_str = &shstrtab_ptr[sh_entry->sh_name];

      if(strcmp(sh_name_str, ".strtab") == 0 && sh_entry->sh_type == SHT_STRTAB) {
        strtab_hdr.elf32 = sh_entry;
      }
      else if(strcmp(sh_name_str, ".dynstr") == 0 && sh_entry->sh_type == SHT_STRTAB) {
        dynstr_hdr.elf32 = sh_entry;
      }

      if(sh_entry->sh_type == SHT_SYMTAB && strcmp(sh_name_str, ".symtab") == 0) {
        symtab_hdr.elf32 = sh_entry;
      }
      else if(sh_entry->sh_type == SHT_SYMTAB && strcmp(sh_name_str, ".dynsym") == 0) {
        dynsymtab_hdr.elf32 = sh_entry;
      }

      if(sh_entry->sh_addr >= 0x80000000ULL) {
        const auto end = static_cast<size_t>(sh_entry->sh_addr - 0x80000000ULL + sh_entry->sh_size);
        if (end > content_size) {
          content_size = end;
        }
      }
    }
    // std::cout << "[INFO]: elf32 file content size is " << std::dec << content_size << std::endl;
    dut_memory.clear();
    dut_memory.resize(content_size, 0);
    // // load another sections, use base addr as 0x80000000
    for(int i = 0;i < shnum_.elf32 ; ++i) {
      Elf32_Shdr *sh_entry = &section_header_table_.elf32[i];
      if(sh_entry->sh_addr >= 0x80000000ULL) {
        auto sh_internal_start = sh_entry->sh_addr - 0x80000000ULL;
        const auto sh_end = sh_internal_start + sh_entry->sh_size;
        if(sh_entry->sh_offset + sh_entry->sh_size > buffer_.size()) {
          std::cout << "[ERROR]: section offset and size exceed elf file size, abort" << std::endl;
          return false;
        }
        if (sh_end > dut_memory.size()) {
          dut_memory.resize(sh_end, 0);
        }
        std::memcpy(&dut_memory[sh_internal_start], &buffer_ptr[sh_entry->sh_offset], sh_entry->sh_size);
        // std::cout << "Copied " << std::dec << sh_entry->sh_size << " bytes to dut_memory at offset 0x" << std::hex << sh_internal_start << std::endl;
      }
    }
    // std::cout << "[INFO]: elf32 file content loaded to dut_memory, size is " << std::dec << dut_memory.size() << std::endl;

    // load .symtab
    Elf32_Sym *symtab_start = (Elf32_Sym*)(&(buffer_ptr[symtab_hdr.elf32->sh_offset]));
    size_t symtab_size = symtab_hdr.elf32->sh_size / symtab_hdr.elf32->sh_entsize;
    char *strtab_ptr = &buffer_ptr[strtab_hdr.elf32->sh_offset];

    for(int i = 0; i < symtab_size; ++i) {
      Elf32_Sym *symtab_entry = (Elf32_Sym *)(&symtab_start[i]);
      std::string sym_name;
      char *str_raw = &(strtab_ptr[symtab_entry->st_name]);
      for(int j = 0; j < strlen(str_raw); ++j) {
        sym_name += str_raw[j];
      }
      ElfSymInfo info;
      info.name = sym_name;
      info.ei_class = ei_class_;
      info.name = sym_name;
      info.value.elf32 = symtab_entry->st_value;
      info.size.elf32 = symtab_entry->st_size;
      if(symtab_entry->st_info == STT_FUNC) {
        dut_symtab.push_back(info);
        std::cout << "[INFO]: push back sym name: " << sym_name << ",st_name: " << symtab_entry->st_name << std::endl;
      }
    }
    // load .dynsym
    if(dynstr_hdr.elf32 != nullptr) {
      Elf32_Sym *dynsymtab_start = (Elf32_Sym*)(&(buffer_ptr[dynstr_hdr.elf32->sh_offset]));
      size_t dynsymtab_size = dynsymtab_hdr.elf32->sh_size / dynsymtab_hdr.elf32->sh_entsize;
      for(int i = 0; i < dynsymtab_size; ++i) {
        Elf32_Sym *dynsymtab_entry = (Elf32_Sym *)(&dynsymtab_start[i]);
        std::string sym_name;
        char *str_raw = &(strtab_ptr[dynsymtab_entry->st_name]);
        for(int j = 0; j < strlen(str_raw); ++j) {
          sym_name += str_raw[j];
        }
        ElfSymInfo info;
        info.name = sym_name;
        info.ei_class = ei_class_;
        info.name = sym_name;
        info.value.elf32 = dynsymtab_entry->st_value;
        info.size.elf32 = dynsymtab_entry->st_size;
        if(dynsymtab_entry->st_info == STT_FUNC) {
          dut_dynsymtab.push_back(info);
          std::cout << "[INFO]: push back dyn sym name: " << sym_name << ",st_name: " << dynsymtab_entry->st_name << std::endl;
        }
      }            
    }
  }
  else if(ei_class_ == ELFCLASS64) {
    section_header_ptr_t strtab_hdr, dynstr_hdr, symtab_hdr, dynsymtab_hdr, shstrtab_hdr;
    size_t content_size = 0;
    shstrtab_hdr.elf64 = &(section_header_table_.elf64[shstrtable_idx_.elf64]);
    strtab_hdr.elf64 = nullptr;
    dynstr_hdr.elf64 = nullptr;
    symtab_hdr.elf64 = nullptr;
    
    // section string table
    char *shstrtab_ptr = &buffer_[shstrtab_hdr.elf64->sh_offset];
    // first: find strtab & symtab
    for(int i = 0;i < shnum_.elf64 ; ++i) {
      Elf64_Shdr *sh_entry = &section_header_table_.elf64[i];
      char *sh_name_str = &shstrtab_ptr[sh_entry->sh_name];

      if(strcmp(sh_name_str, ".strtab") == 0 && sh_entry->sh_type == SHT_STRTAB) {
        strtab_hdr.elf64 = sh_entry;
      }
      else if(strcmp(sh_name_str, ".dynstr") == 0 && sh_entry->sh_type == SHT_STRTAB) {
        dynstr_hdr.elf64 = sh_entry;
      }

      if(sh_entry->sh_type == SHT_SYMTAB && strcmp(sh_name_str, ".symtab") == 0) {
        symtab_hdr.elf64 = sh_entry;
      }
      else if(sh_entry->sh_type == SHT_SYMTAB && strcmp(sh_name_str, ".dynsym") == 0) {
        dynsymtab_hdr.elf64 = sh_entry;
      }

      if(sh_entry->sh_addr >= 0x80000000ULL) {
        const auto end = static_cast<size_t>(sh_entry->sh_addr - 0x80000000ULL + sh_entry->sh_size);
        if (end > content_size) {
          content_size = end;
        }
      }
    }
    // std::cout << "[INFO]: elf64 file content size is " << std::dec << content_size << std::endl;
    dut_memory.clear();
    dut_memory.resize(content_size, 0);
    // // load another sections, use base addr as 0x80000000
    for(int i = 0;i < shnum_.elf64 ; ++i) {
      Elf64_Shdr *sh_entry = &section_header_table_.elf64[i];
      if(sh_entry->sh_addr >= 0x80000000ULL) {
        auto sh_internal_start = sh_entry->sh_addr - 0x80000000ULL;
        const auto sh_end = sh_internal_start + sh_entry->sh_size;
        // std::cout << "[INFO]: loading section " << i << " to dut_memory at offset 0x" << std::hex << sh_internal_start << std::dec << std::endl;
        // std::cout << "[INFO]: section size is " << std::dec << sh_entry->sh_size << std::endl;
        // std::cout << "[INFO]: section offset in file is 0x" << std::hex << sh_entry->sh_offset << std::dec << std::endl;
        if(sh_entry->sh_offset + sh_entry->sh_size > buffer_.size()) {
          std::cout << "[ERROR]: section offset and size exceed elf file size, abort" << std::endl;
          return false;
        }
        if (sh_end > dut_memory.size()) {
          dut_memory.resize(sh_end, 0);
        }
        std::memcpy(&dut_memory[sh_internal_start], &buffer_ptr[sh_entry->sh_offset], sh_entry->sh_size);
        // std::cout << "Copied " << std::dec << sh_entry->sh_size << " bytes to dut_memory at offset 0x" << std::hex << sh_internal_start << std::endl;
      }
    }
    // std::cout << "[INFO]: elf64 file content loaded to dut_memory, size is " << std::dec << dut_memory.size() << std::endl;

    // load .symtab
    Elf64_Sym *symtab_start = (Elf64_Sym*)(&(buffer_ptr[symtab_hdr.elf64->sh_offset]));
    size_t symtab_size = symtab_hdr.elf64->sh_size / symtab_hdr.elf64->sh_entsize;
    char *strtab_ptr = &buffer_ptr[strtab_hdr.elf64->sh_offset];

    for(int i = 0; i < symtab_size; ++i) {
      Elf64_Sym *symtab_entry = (Elf64_Sym *)(&symtab_start[i]);
      std::string sym_name;
      char *str_raw = &(strtab_ptr[symtab_entry->st_name]);
      for(int j = 0; j < strlen(str_raw); ++j) {
        sym_name += str_raw[j];
      }
      ElfSymInfo info;
      info.name = sym_name;
      info.ei_class = ei_class_;
      info.name = sym_name;
      info.value.elf64 = symtab_entry->st_value;
      info.size.elf64 = symtab_entry->st_size;
      if(symtab_entry->st_info == STT_FUNC) {
        dut_symtab.push_back(info);
        std::cout << "[INFO]: push back sym name: " << sym_name << ",st_name: " << symtab_entry->st_name << std::endl;
      }
    }
    // load .dynsym
    if(dynstr_hdr.elf64 != nullptr) {
      std::cout << "[INFO]: loading dynstr tbl" << std::endl;
      Elf64_Sym *dynsymtab_start = (Elf64_Sym*)(&(buffer_ptr[dynstr_hdr.elf64->sh_offset]));
      size_t dynsymtab_size = dynsymtab_hdr.elf64->sh_size / dynsymtab_hdr.elf64->sh_entsize;
      for(int i = 0; i < dynsymtab_size; ++i) {
        Elf64_Sym *dynsymtab_entry = (Elf64_Sym *)(&dynsymtab_start[i]);
        std::string sym_name;
        char *str_raw = &(strtab_ptr[dynsymtab_entry->st_name]);
        for(int j = 0; j < strlen(str_raw); ++j) {
          sym_name += str_raw[j];
        }
        ElfSymInfo info;
        info.name = sym_name;
        info.ei_class = ei_class_;
        info.name = sym_name;
        info.value.elf64 = dynsymtab_entry->st_value;
        info.size.elf64 = dynsymtab_entry->st_size;
        if(dynsymtab_entry->st_info == STT_FUNC) {
          dut_dynsymtab.push_back(info);
          std::cout << "[INFO]: push back dyn sym name: " << sym_name << ",st_name: " << dynsymtab_entry->st_name << std::endl;
        }
      }            
    }
  }
  else {
    std::cout << "[ERROR]: unknown ei_class" << std::endl;
    return false;
  }
  return true;
}

}