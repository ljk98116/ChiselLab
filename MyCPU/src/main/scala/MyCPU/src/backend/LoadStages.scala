package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// if mdp is right, when rob enable a store, load issue after store, so load can use storebuffer's store aguResult
// if mdp is wrong, load can not use store's result(no need access storebuffer), 
// return an error result(checked when store before it reached rob head)

// stage0: 
// judge dTLB hit / get dcache all ways' tagV and data / 
// send aguRes to storeBuffer check pipeline(virt addr check)

// stage1: get storeBuffer data, catch dTLB exception
// stage2: judge dcache hit and use dcache/storebuffer
// stage3: stage result, send to CDB
class LoadStages(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {

    })

    
}