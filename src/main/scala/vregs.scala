package fiona

import chisel3._
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._

class VRegFile extends Module with HasFIONAParameters {
  val io = IO( new Bundle {
    val rs1 = Input(UInt(5.W))
    val rs2 = Input(UInt(5.W))
    val rs1_val = Output(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
    val rs2_val = Output(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))

    val wd = Input(UInt(5.W))
    val wen = Input(Bool())
    val wval = Input(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
  } )
  val regs = Reg(Vec(32, Vec(FIONAVLenMax, UInt(FIONAXLen.W))))
  when(io.wen) {
    regs(io.wd) := io.wval
  }
  io.rs1_val := regs(io.rs1)
  io.rs2_val := regs(io.rs2)
  when(io.rs1 === 0.U) {
    io.rs1_val := 0.U.asTypeOf(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
  }
  when(io.rs2 === 0.U) {
    io.rs2_val := 0.U.asTypeOf(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
  }
}

class VRegFileMem extends Module with HasFIONAParameters {
  val io = IO( new Bundle {
    val rs1 = Input(UInt(5.W))
    val rs2 = Input(UInt(5.W))
    val rs1_val = Output(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
    val rs2_val = Output(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))

    val wd = Input(UInt(5.W))
    val wen = Input(Bool())
    val wval = Input(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
  } )
  val mem = SyncReadMem(32, Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
  // Write
  when (io.wen) {
    mem.write(io.wd, io.wval)
  }
  io.rs1_val := Mux( io.rs1 === 0.U, 0.U.asTypeOf(io.rs1_val), mem.read(io.rs1, true.B))
  io.rs2_val := Mux( io.rs2 === 0.U, 0.U.asTypeOf(io.rs2_val), mem.read(io.rs2, true.B))
}

class VRegFileMemDualBank extends Module with HasFIONAParameters {
  val io = IO( new Bundle {
    val rs1 = Input(UInt(5.W))
    val rs2 = Input(UInt(5.W))
    val rs1_val = Output(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
    val rs2_val = Output(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))

    val wd = Input(UInt(5.W))
    val wen = Input(Bool())
    val wval = Input(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
  } )
  val mem_bank1 = SyncReadMem(32, Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
  val mem_bank2 = SyncReadMem(32, Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
  // Write
  when (io.wen) {
    mem_bank1.write(io.wd, io.wval)
    mem_bank2.write(io.wd, io.wval)
  }
  io.rs1_val := Mux( io.rs1 === 0.U, 0.U.asTypeOf(io.rs1_val), mem_bank1.read(io.rs1, true.B))
  io.rs2_val := Mux( io.rs2 === 0.U, 0.U.asTypeOf(io.rs2_val), mem_bank2.read(io.rs2, true.B))
}

