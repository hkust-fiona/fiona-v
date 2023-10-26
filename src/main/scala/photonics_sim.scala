package fiona

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import freechips.rocketchip.rocket._
import FIONADecodeConstants._
import org.chipsalliance.cde.config._
import freechips.rocketchip.tile._

class puc_sim_adapter(PySrcName: String, PythonFuncName: String, 
DWIDTH: Int, ILEN1_row: Int, ILEN1_col: Int, ILEN2_row: Int, ILEN2_col: Int, OLEN_col: Int, OLEN_row: Int, PHOOp: BitPat) extends BlackBox(
  Map(
    "PY_SRC_NAME" -> PySrcName, 
    "PY_FUNC_NAME" -> PythonFuncName, 
    "DWIDTH" -> DWIDTH, 
    "ILEN1_row" -> ILEN1_row,  
    "ILEN1_col" -> ILEN1_col,  
    "ILEN2_row" -> ILEN2_row, 
    "ILEN2_col" -> ILEN2_col, 
    "OLEN_row" -> OLEN_row,
    "OLEN_col" -> OLEN_col
    )
  ) {
  val iwidth_1 = DWIDTH * ILEN1_row * ILEN1_col 
  val iwidth_2 = DWIDTH * ILEN2_row * ILEN2_col
  val owidth = DWIDTH * OLEN_row * OLEN_col
  val fn = PHOOp
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val ena = Input(Bool())
    val in1_cols = Input(UInt(32.W))
    val in1_rows = Input(UInt(32.W))
    val in2_cols = Input(UInt(32.W))
    val in2_rows = Input(UInt(32.W))
    val out_cols = Input(UInt(32.W))
    val out_rows = Input(UInt(32.W))
    val ivalue_1 = Input(UInt(iwidth_1.W))
    val ivalue_2 = Input(UInt(iwidth_2.W))
    val ovalue = Output(UInt(owidth.W))
  })
}

class PUCSim(implicit p: Parameters) extends CoreModule()(p) with HasFIONAParameters {
  val io = IO(new Bundle {
    val decode_in = Input(new FIONACtrlSigs)
    val srcBundle = Input(new SrcBundle)
    val WbBundle = Output(new WbBundle)
    val vmask_1 = Input(UInt(FIONAVLenMax.W))
    val vmask_2 = Input(UInt(FIONAVLenMax.W))
    val vlen = Input(UInt(xLen.W))
    val matRegVal = Input(Vec(FIONAVLenMax, Vec(FIONAVLenMax, UInt(FIONAXLen.W))))
  })
  val decodeOps = io.decode_in
  val in_valid = decodeOps.fu_type === FU_PHO && decodeOps.valid
  val src = io.srcBundle
  val res = io.WbBundle
  // Tie off the (maybe) unused values
  res := 0.U.asTypeOf(res)
  // Write back to vector registers
  res.wb_vec := decodeOps.res_type === OP_VREG
  res.rd_num := src.rd_num
  res.wb_en := decodeOps.wb_en
  
  val fn = decodeOps.fu_cmd

  val dotp = Module(new puc_sim_adapter("ideal_numerical","dotp", FIONAXLen, FIONAVLenMax, 1, FIONAVLenMax, 1, 1, 1, PHO_DOTP))
  val mvm = Module(new puc_sim_adapter("ideal_numerical","mvm", FIONAXLen, FIONAVLenMax, 1, FIONAVLenMax, FIONAVLenMax, FIONAVLenMax, 1, PHO_MVM))

  val activate = RegInit(false.B)
  val pucs = List(dotp, mvm)
  pucs.map ( u => {
    val src_vs1_flattened = src.vs1_val.asTypeOf(u.io.ivalue_1)
    val src_vs2_flattened = src.vs2_val.asTypeOf(u.io.ivalue_2)
    u.io.clock := clock
    u.io.reset := reset
    u.io.ena := activate
    u.io.in1_cols := io.vlen
    u.io.in1_rows := io.vlen
    u.io.in2_cols := io.vlen
    u.io.in2_rows := io.vlen
    u.io.out_cols := 1.U
    u.io.out_rows := io.vlen   
    u.io.ivalue_1 := src_vs1_flattened
    u.io.ivalue_2 := src_vs2_flattened
  })
  mvm.io.ivalue_2 := io.matRegVal.asTypeOf(mvm.io.ivalue_2)
  mvm.io.in1_cols := 1.U
  dotp.io.in1_cols := 1.U
  dotp.io.in2_cols := 1.U
  dotp.io.out_rows := 1.U
  dotp.io.out_cols := 1.U
  res.vd_val := MuxCase(0.U.asTypeOf(res.vd_val), Array(
    // (fn === PHO_DOTP) -> dotp.ovalue.asTypeOf(res.vd_val),
    (fn === PHO_MVM) -> mvm.io.ovalue.asTypeOf(res.vd_val),
    )
  )
  res.rd_val := MuxCase(0.U, Array(
    (fn === PHO_DOTP) -> dotp.io.ovalue.asTypeOf(res.rd_val),
    )
  )

  // Simulate the delay for photonic devices
  val busy = RegInit(false.B)
  val cycles = RegInit(0.U(32.W))
  when(in_valid) {
    busy := true.B
    activate := true.B
  }
  when(busy) {
    cycles := cycles + 1.U
    activate := false.B
    when(cycles === 32.U) { // TODO: different cycles for different ops (Using a map)
      res.valid := true.B
      cycles := 0.U
      busy := false.B
    }
  }
}
