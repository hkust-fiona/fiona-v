package fiona

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._
import chisel3.util.experimental.BoringUtils
import FIONADecodeConstants._

class ConfigRegs(implicit p: Parameters) extends CoreModule()(p) with HasFIONAParameters {
  // Tie off the write back port
  val io = IO(new Bundle {
    val decode_in = Input(new FIONACtrlSigs)
    val srcBundle = Input(new SrcBundle)
    val WbBundle = Output(new WbBundle)
    val vlen = Output(UInt(xLen.W))
    val mask_v1 = Output(UInt(FIONAVLenMax.W))
    val mask_v2 = Output(UInt(FIONAVLenMax.W))
  })
  val decodeOps = io.decode_in
  val in_valid = decodeOps.fu_type === FU_CFG && decodeOps.valid
  val src = io.srcBundle
  val res = io.WbBundle
  // Tie off the (maybe) unused values
  res := 0.U.asTypeOf(res)
  // Write back to vector registers
  res.wb_vec := decodeOps.res_type === OP_VREG
  res.rd_num := src.rd_num

  val maskRegs = Reg(Vec(32, UInt(FIONAVLenMax.W)))
  for (i <- 0 to 31) {
    when(reset.asBool) {
      maskRegs(i) := 0xFFFFFFFFL.U
    }
  }
  val vlenReg = RegInit(FIONAVLenMax.U)

  io.vlen := vlenReg

  io.mask_v1 := maskRegs(src.r1_num)
  io.mask_v2 := maskRegs(src.r2_num)

  when(in_valid) {
    assert(src.rd_num =/= CFG_MAT)
    when(src.rd_num === CFG_VLEN) { // set vl
      vlenReg := src.rs1_val
    }
    when(src.rd_num === CFG_VMASK) { // set vmask
      maskRegs(src.rs2_val) := src.rs1_val
    }
    res.valid := true.B
  }
}
