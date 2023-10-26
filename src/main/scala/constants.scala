package fiona

import chisel3._
import chisel3.util._

object FIONADecodeConstants extends DecodeConstants

trait DecodeConstants
extends DecodeFUConstants
with ALUOpConstants
with MDUOpConstants
with ACTIVATIONOpConstants
with LSUOpConstants
with PHOOpConstants
with CFGRegAlias
with OperandsType {
  val Y = BitPat("b1")
  val N = BitPat("b0")
  val X = BitPat("b?")
}

trait DecodeFUConstants {
  val FU_X   = BitPat("b?")
  val FU_ALU = BitPat(0.U(3.W))
  val FU_MDU = BitPat(1.U(3.W))
  val FU_ACT = BitPat(2.U(3.W))
  val FU_LSU = BitPat(3.U(3.W))
  val FU_MSC = BitPat(4.U(3.W))
  val FU_CFG = BitPat(5.U(3.W))
  val FU_PHO = BitPat(6.U(3.W))
}

trait ALUOpConstants {
  val CMD_X       = BitPat(0.U(3.W))
  val ALU_ADD     = BitPat(0.U(3.W))
  val ALU_SUB     = BitPat(1.U(3.W))
  val ALU_SHFL    = BitPat(2.U(3.W))
  val ALU_MIN     = BitPat(3.U(3.W))
  val ALU_MAX     = BitPat(4.U(3.W))
  val ALU_SUB_INV = BitPat(5.U(3.W))
}

trait MDUOpConstants {
  val MDU_DIV = BitPat(0.U(3.W))
  val MDU_MUL = BitPat(1.U(3.W))
}

trait ACTIVATIONOpConstants {
  val ACT_RELU = BitPat(0.U(3.W))
  val ACT_TANH = BitPat(1.U(3.W))
  val ACT_SIGM = BitPat(2.U(3.W))
}

trait LSUOpConstants {
  val LSU_LD = BitPat(0.U(3.W))
  val LSU_ST = BitPat(1.U(3.W))
  val LSU_MAT= BitPat(2.U(3.W))
}

trait PHOOpConstants {
  val PHO_DOTP = BitPat(0.U(3.W))
  val PHO_MVM = BitPat(1.U(3.W))
}

trait OperandsType {
  val OP_X    = BitPat(0.U(3.W))
  val OP_SREG = BitPat(1.U(3.W))
  val OP_VREG = BitPat(2.U(3.W))
}

trait CFGRegAlias {
  val CFG_VLEN  = BitPat(0.U(5.W))
  val CFG_VMASK = BitPat(1.U(5.W))
  val CFG_MAT   = BitPat(2.U(5.W))
}
