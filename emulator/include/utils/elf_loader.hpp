#ifndef UTILS_ELF_LOADER_HPP
#define UTILS_ELF_LOADER_HPP

#include <elf.h>
#include <string>
#include <vector>

namespace emulator {
  typedef union {
    Elf32_Phdr *elf32;
    Elf64_Phdr *elf64;
  } program_header_ptr_t;

  typedef union {
    Elf32_Shdr *elf32;
    Elf64_Shdr *elf64;
  } section_header_ptr_t;

  typedef union {
    Elf32_Addr elf32;
    Elf64_Addr elf64;
  } Elf_Addr;

  typedef union {
    Elf32_Half elf32;
    Elf64_Half elf64;
  } Elf_Half;

  typedef union {
    Elf32_Sym elf32;
    Elf64_Sym elf64;
  } Elf_Sym;

  typedef union {
    Elf32_Word elf32;
    Elf64_Xword elf64;
  } ELf_SymSize;

  struct ElfSymInfo {
    std::string name;
    Elf_Addr value;
    ELf_SymSize size;
    char ei_class;
  };

  class elf_loader {
    public:
      static bool load_elf(const std::string& filepath);
    private:
      elf_loader() = default;
      ~elf_loader() = default;
      bool init(const std::string& filepath);
      bool parse_elf_header();
      bool load_sections();
    private:
      std::vector<char> buffer_;
      program_header_ptr_t program_header_table_;
      section_header_ptr_t section_header_table_;
      Elf_Half phnum_;
      Elf_Half phtable_entry_sz_;
      Elf_Half shnum_;
      Elf_Half shstrtable_idx_;
      Elf_Half shtable_entry_sz_;
      char ei_class_;
  };
}

#endif