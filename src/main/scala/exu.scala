package fiona

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import FIONADecodeConstants._

class SrcBundle(implicit p: Parameters) extends CoreBundle()(p) 
  with HasFIONAParameters {
  // Reginster specifiers
  val r1_num  = UInt(5.W)
  val r2_num  = UInt(5.W)
  val rd_num  = UInt(5.W)
  // Scalar register values
  val rs1_val  = UInt(xLen.W)
  val rs2_val  = UInt(xLen.W)
  // Vector value bundle
  val vs1_val = Vec(FIONAVLenMax, UInt(FIONAXLen.W))
  val vs2_val = Vec(FIONAVLenMax, UInt(FIONAXLen.W))
}

class WbBundle(implicit p: Parameters) extends CoreBundle()(p) 
  with HasFIONAParameters {
  val valid = Bool()       // Instr done
  val rd_num  = UInt(5.W)  
  val rd_val = UInt(xLen.W)
  val vd_val = Vec(FIONAVLenMax, UInt(FIONAXLen.W))
  val wb_en = Bool()      // Instr will wb to either scalar or vector reg
  val wb_vec = Bool()     // Instr will wb to vector reg
}

abstract class AbstractEXU(FU_Type: BitPat)(implicit p: Parameters) extends CoreModule()(p) with HasFIONAParameters {
  val io = IO(new Bundle {
    val decode_in = Input(new FIONACtrlSigs)
    val srcBundle = Input(new SrcBundle)
    val WbBundle = Output(new WbBundle)
    val vmask_1 = Input(UInt(FIONAVLenMax.W))
    val vmask_2 = Input(UInt(FIONAVLenMax.W))
    val vlen = Input(UInt(xLen.W))
  })
  val placeholder = WireInit(0.U(FIONAXLen.W))
  val decodeOps = io.decode_in
  val in_valid = decodeOps.fu_type === FU_Type && decodeOps.valid
  val src = io.srcBundle
  val res = io.WbBundle
  // Tie off the (maybe) unused values
  res := 0.U.asTypeOf(res)
  // Write back to vector registers
  res.wb_vec := decodeOps.res_type === OP_VREG
  res.rd_num := src.rd_num
  res.wb_en := decodeOps.wb_en
  
  val fn = decodeOps.fu_cmd
  val in_batches = (fn === ALU_ADD || fn === ALU_SUB || fn === ALU_SUB_INV || fn === ALU_SHFL || fn === MDU_DIV || fn === MDU_MUL)
  val op_vlen_mask = Wire(Vec(FIONAVLenMax, Bool()))
  for(i <- 0 until FIONAVLenMax) {
    op_vlen_mask(i) := (i.U(xLen.W) < io.vlen)  // Note: Should specify the width here, otherwise it will not be extended to the width as vlen
  }
  when(!in_batches) {
    when(fn === ALU_MAX) {
      placeholder := (-32768.S).asUInt()
    }.elsewhen(fn === ALU_MIN) {
      placeholder := 32767.U
    }
  }
  val masked_vlen_op1 = (op_vlen_mask.asUInt & io.vmask_1).asBools
  val masked_vlen_op2 = (op_vlen_mask.asUInt & io.vmask_2).asBools
  val Nbatches = FIONAVLenMax / FIONANLane
  // Vector op1, op2 as vec
  // TODO: Define a function to reshape the input
  val op1_vec = Wire(Vec( Nbatches,  Vec(FIONANLane, UInt(FIONAXLen.W))))
  val op2_vec = Wire(Vec( Nbatches,  Vec(FIONANLane, UInt(FIONAXLen.W))))
  val rs1_val_lsb16 = src.rs1_val(FIONAXLen - 1, 0)
  val op1_fill_scalar = VecInit( Seq.tabulate(FIONAVLenMax) { i => Mux(masked_vlen_op2(i), rs1_val_lsb16, placeholder) })
  val op1_vec_fill = op1_fill_scalar.asTypeOf(op1_vec)  // If another operand is scalar, fill that into a full vector
  val rs2_val_lsb16 = src.rs2_val(FIONAXLen - 1, 0)
  val op2_fill_scalar = VecInit( Seq.tabulate(FIONAVLenMax) { _ => rs2_val_lsb16 })
  val op2_vec_fill = op2_fill_scalar.asTypeOf(op2_vec)  // If another operand is scalar, fill that into a full vector
  // Mask the vector
  val src1_masked = VecInit( Seq.tabulate(FIONAVLenMax) { i => Mux(masked_vlen_op1(i), src.vs1_val(i), placeholder) } )
  val src2_masked = VecInit( Seq.tabulate(FIONAVLenMax) { i => Mux(masked_vlen_op2(i), src.vs2_val(i), placeholder ) } )
  // op1_vec := src1_masked.asTypeOf(op1_vec)
  // op2_vec := Mux( decodeOps.op2_type === OP_VREG,  src2_masked.asTypeOf(op2_vec), op2_vec_fill)
  op1_vec := Mux( decodeOps.op1_type === OP_VREG,  src1_masked.asTypeOf(op1_vec), op1_vec_fill)
  op2_vec := src2_masked.asTypeOf(op2_vec)
  val beatCount = RegInit(0.U(log2Up(FIONAVLenMax).W))
  // Current operand of that lane, e.g., 2 lanes, op1_batch = op1(0, 1), op1(2, 3) ...
  val op1_batch = op1_vec(beatCount)
  val op2_batch = op2_vec(beatCount)
}

