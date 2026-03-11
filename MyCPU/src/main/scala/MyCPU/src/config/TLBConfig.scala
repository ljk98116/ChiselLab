package MyCPU.config

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class Perm(implicit p: Parameters) extends Bundle {
    val V = Bool()
    val R = Bool()
    val W = Bool()
    val X = Bool()
    val U = Bool()
    val G = Bool()
    val A = Bool()
    val D = Bool()
    val RSW = UInt(2.W)
    val PBMT = UInt(2.W)
    val N = Bool() 
}

class Sv32VirtualAddress(implicit p: Parameters) extends Bundle {
    val pageOffset = UInt(12.W)
    val vpn0 = UInt(10.W)
    val vpn1 = UInt(10.W)
    def GetVPN = Cat(vpn1, vpn0)
}

class Sv39VirtualAddress(implicit p: Parameters) extends Bundle {
    val pageOffset = UInt(12.W)
    val vpn0 = UInt(9.W)
    val vpn1 = UInt(9.W)
    val vpn2 = UInt(9.W)
    def GetVPN = Cat(vpn2, vpn1, vpn0)
}

class Sv48VirtualAddress(implicit p: Parameters) extends Bundle {
    val pageOffset = UInt(12.W)
    val vpn0 = UInt(9.W)
    val vpn1 = UInt(9.W)
    val vpn2 = UInt(9.W)
    val vpn3 = UInt(9.W)
    def GetVPN = Cat(vpn3, vpn2, vpn1, vpn0)
}

class Sv57VirtualAddress(implicit p: Parameters) extends Bundle {
    val pageOffset = UInt(12.W)
    val vpn0 = UInt(9.W)
    val vpn1 = UInt(9.W)
    val vpn2 = UInt(9.W)
    val vpn3 = UInt(9.W)
    val vpn4 = UInt(9.W)
    def GetVPN = Cat(vpn4, vpn3, vpn2, vpn1, vpn0)
}

class VirtualAddress(implicit p: Parameters) extends Bundle {
    def GetWidth = p(ADDR_WIDTH)
    val bits = UInt(p(ADDR_WIDTH).W)
    def asSv32 = {
        require(p(ADDR_WIDTH) == 32, "vaddr convert to Sv32 failed")
        bits.asTypeOf(new Sv32VirtualAddress)
    }
    def asSv39 = {
        require(p(ADDR_WIDTH) == 39, "vaddr convert to Sv39 failed")
        bits.asTypeOf(new Sv39VirtualAddress)
    }
    def asSv48 = {
        require(p(ADDR_WIDTH) == 48, "vaddr convert to Sv48 failed")
        bits.asTypeOf(new Sv48VirtualAddress)
    }
    def asSv57 = {
        require(p(ADDR_WIDTH) == 57, "vaddr convert to Sv57 failed")
        bits.asTypeOf(new Sv57VirtualAddress)
    }
    def getVPN: UInt = {
        require(
            p(UseSv32) || p(UseSv39) || p(UseSv48) || p(UseSv57),
            "unsupported mmu detected while access VA"
        )
        if(p(UseSv32)) return asSv32.GetVPN
        if(p(UseSv39)) return asSv39.GetVPN
        if(p(UseSv48)) return asSv48.GetVPN
        if(p(UseSv57)) return asSv57.GetVPN
        return 0.U
    }
}

class Sv32PhysicalAddress(implicit p: Parameters) extends Bundle {
    val pageOffset = UInt(12.W)
    val ppn0 = UInt(10.W)
    val ppn1 = UInt(12.W)
    def GetPPN = Cat(ppn1, ppn0)
}

class Sv39PhysicalAddress(implicit p: Parameters) extends Bundle {
    val pageOffset = UInt(12.W)
    val ppn0 = UInt(9.W)
    val ppn1 = UInt(9.W)
    val ppn2 = UInt(26.W)
    def GetPPN = Cat(ppn2, ppn1, ppn0)
}

class Sv48PhysicalAddress(implicit p: Parameters) extends Bundle {
    val pageOffset = UInt(12.W)
    val ppn0 = UInt(9.W)
    val ppn1 = UInt(9.W)
    val ppn2 = UInt(9.W) 
    val ppn3 = UInt(17.W)  
    def GetPPN = Cat(ppn3, ppn2, ppn1, ppn0) 
}

class Sv57PhysicalAddress(implicit p: Parameters) extends Bundle {
    val pageOffset = UInt(12.W)
    val ppn0 = UInt(9.W)
    val ppn1 = UInt(9.W)
    val ppn2 = UInt(9.W) 
    val ppn3 = UInt(9.W) 
    val ppn4 = UInt(8.W)
    def GetPPN = Cat(ppn4, ppn3, ppn2, ppn1, ppn0)   
}

class PhysicalAddress(implicit p: Parameters) extends Bundle {
    def GetWidth: Int = {
        require(
            p(UseSv32) || p(UseSv39) || p(UseSv48) || p(UseSv57),
            "unsupported mmu detected while access PA"
        )
        if(p(UseSv32)) return (new Sv32PhysicalAddress).getWidth
        if(p(UseSv39)) return (new Sv39PhysicalAddress).getWidth
        if(p(UseSv48)) return (new Sv48PhysicalAddress).getWidth
        if(p(UseSv57)) return (new Sv57PhysicalAddress).getWidth
        return 0
    }
    val bits = UInt(GetWidth.W)
    def asSv32 = bits.asTypeOf(new Sv32PhysicalAddress)
    def asSv39 = bits.asTypeOf(new Sv39PhysicalAddress)
    def asSv48 = bits.asTypeOf(new Sv48PhysicalAddress)
    def asSv57 = bits.asTypeOf(new Sv57PhysicalAddress)
    def GetPPN: UInt = {
        require(
            p(UseSv32) || p(UseSv39) || p(UseSv48) || p(UseSv57),
            "unsupported mmu detected while access PA"
        )
        if(p(UseSv32)) return asSv32.GetPPN
        if(p(UseSv39)) return asSv39.GetPPN
        if(p(UseSv48)) return asSv48.GetPPN
        if(p(UseSv57)) return asSv57.GetPPN
        return 0.U
    }
}

class Sv32PTE(implicit p: Parameters) extends Bundle {
    val V = Bool()
    val R = Bool()
    val W = Bool()
    val X = Bool()
    val U = Bool()
    val G = Bool()
    val A = Bool()
    val D = Bool()
    val RSW = UInt(2.W)
    val PPN0 = UInt(10.W)
    val PPN1 = UInt(12.W)

    def GetPPN = Cat(PPN1, PPN0)
}

class Sv39PTE(implicit p: Parameters) extends Bundle {
    val V = Bool()
    val R = Bool()
    val W = Bool()
    val X = Bool()
    val U = Bool()
    val G = Bool()
    val A = Bool()
    val D = Bool()
    val RSW = UInt(2.W)
    val PPN0 = UInt(9.W)
    val PPN1 = UInt(9.W)
    val PPN2 = UInt(26.W)
    val Reserved = UInt(7.W)
    val PBMT = UInt(2.W)
    val N = Bool()
    def GetPPN = Cat(PPN2, PPN1, PPN0)
}

class Sv48PTE(implicit p: Parameters) extends Bundle {
    val V = Bool()
    val R = Bool()
    val W = Bool()
    val X = Bool()
    val U = Bool()
    val G = Bool()
    val A = Bool()
    val D = Bool()
    val RSW = UInt(2.W)
    val PPN0 = UInt(9.W)
    val PPN1 = UInt(9.W)
    val PPN2 = UInt(9.W)
    val PPN3 = UInt(17.W)
    val Reserved = UInt(7.W)
    val PBMT = UInt(2.W)
    val N = Bool()
    def GetPPN = Cat(PPN3, PPN2, PPN1, PPN0)
}

class Sv57PTE(implicit p: Parameters) extends Bundle {
    val V = Bool()
    val R = Bool()
    val W = Bool()
    val X = Bool()
    val U = Bool()
    val G = Bool()
    val A = Bool()
    val D = Bool()
    val RSW = UInt(2.W)
    val PPN0 = UInt(9.W)
    val PPN1 = UInt(9.W)
    val PPN2 = UInt(9.W)
    val PPN3 = UInt(9.W)
    val PPN4 = UInt(8.W)
    val Reserved = UInt(7.W)
    val PBMT = UInt(2.W)
    val N = Bool()
    def GetPPN = Cat(PPN4, PPN3, PPN2, PPN1, PPN0)
}

class PTE(implicit p: Parameters) extends Bundle {
    require(
        p(UseSv32) || p(UseSv39) || p(UseSv48) || p(UseSv57),
        "unsupported mmu detected while access PTE"
    )
    def GetWidth: Int = {
        if(p(UseSv32)) return 32
        return 64
    }
    val bits = UInt(GetWidth.W)
    def asSv32 = bits.asTypeOf(new Sv32PTE)
    def asSv39 = bits.asTypeOf(new Sv39PTE)
    def asSv48 = bits.asTypeOf(new Sv48PTE)
    def asSv57 = bits.asTypeOf(new Sv57PTE) 
    def GetPPN: UInt = {
        require(
            p(UseSv32) || p(UseSv39) || p(UseSv48) || p(UseSv57),
            "unsupported mmu detected while access PTE"
        )
        if(p(UseSv32)) return asSv32.GetPPN
        if(p(UseSv39)) return asSv39.GetPPN
        if(p(UseSv48)) return asSv48.GetPPN
        if(p(UseSv57)) return asSv57.GetPPN
        return 0.U
    }   
    def V = asSv32.V
    def R = asSv32.R
    def W = asSv32.W
    def X = asSv32.X
    def U = asSv32.U
    def G = asSv32.G
    def A = asSv32.A
    def D = asSv32.D
    def RSW = asSv32.RSW
    def PBMT = asSv39.PBMT
    def N = asSv39.N
}

class TLBOption extends Field[Boolean]

case object UseSv32 extends TLBOption
case object UseSv39 extends TLBOption
case object UseSv48 extends TLBOption
case object UseSv57 extends TLBOption

class UseSv32Config extends Config(
    (site, here, up) => {
        case UseSv32 => true
        case UseSv39 => false
        case UseSv48 => false
        case UseSv57 => false
    }
)

class UseSv39Config extends Config(
    (site, here, up) => {
        case UseSv32 => false
        case UseSv39 => true
        case UseSv48 => false
        case UseSv57 => false
    }
)

class UseSv48Config extends Config(
    (site, here, up) => {
        case UseSv32 => false
        case UseSv39 => false
        case UseSv48 => true
        case UseSv57 => false
    }
)

class UseSv57Config extends Config(
    (site, here, up) => {
        case UseSv32 => false
        case UseSv39 => false
        case UseSv48 => false
        case UseSv57 => true
    }
)