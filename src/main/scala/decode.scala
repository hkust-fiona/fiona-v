package fiona

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import FIONADecodeConstants._

class FIONACtrlSigs(implicit p: Parameters) extends Bundle {
  val valid       = Bool()
  val is_photonic = Bool()
  val fu_type     = UInt()
  val fu_cmd      = UInt()
  val op1_type    = UInt()
  val op2_type    = UInt()
  val res_type    = UInt()
  val wb_en       = UInt()

  def decode(inst: UInt) = {
    val decoder = freechips.rocketchip.rocket.DecodeLogic(inst, FIONADecodeTable.default, FIONADecodeTable.table)
    val sigs = Seq(
      valid      , 
      is_photonic, 
      fu_type    , 
      fu_cmd     , 
      op1_type   , 
      op2_type   , 
      res_type   , 
      wb_en      , 
      )
    sigs zip decoder map {case(s,d) => s := d}
    this
  }
  def decode_special(inst: UInt) = {
    val decoder = freechips.rocketchip.rocket.DecodeLogic(inst, FIONADecodeTableSpecial.default, FIONADecodeTableSpecial.table)
    val sigs = Seq(
      valid      , 
      is_photonic, 
      fu_type    , 
      fu_cmd     , 
      op1_type   , 
      op2_type   , 
      res_type   , 
      wb_en      , 
      )
    sigs zip decoder map {case(s,d) => s := d}
    this
  }
}

object FIONADecodeTable {
  import FIONAInstructions._
  val default: List[BitPat] =
                  List(N,     N,           FU_ALU,  ALU_ADD, OP_X,      OP_X,     OP_X,     N    )
  val table: Array[(BitPat, List[BitPat])] = Array(
    // val sigs =      valid, is_photonic, fu_type, fu_cmd,      op1_type,  op2_type, res_type, wb_en)
    // Arithmetic
    ADD_V       -> List(Y,     N,           FU_ALU,  ALU_ADD,     OP_VREG,   OP_VREG,  OP_VREG,  Y    ),
    SUB_V       -> List(Y,     N,           FU_ALU,  ALU_SUB,     OP_VREG,   OP_VREG,  OP_VREG,  Y    ),
    ADD_VS      -> List(Y,     N,           FU_ALU,  ALU_ADD,     OP_SREG,   OP_VREG,  OP_VREG,  Y    ),
    SUB_VS      -> List(Y,     N,           FU_ALU,  ALU_SUB_INV, OP_SREG,   OP_VREG,  OP_VREG,  Y    ),
    MUL_VS      -> List(Y,     N,           FU_MDU,  MDU_MUL,     OP_SREG,   OP_VREG,  OP_VREG,  Y    ),
    DIV_VS      -> List(Y,     N,           FU_MDU,  MDU_DIV,     OP_SREG,   OP_VREG,  OP_VREG,  Y    ),
    // MISC
    VSHFL       -> List(Y,     N,           FU_ALU, ALU_SHFL,     OP_VREG,   OP_VREG,  OP_VREG,  Y    ),
    VMAX        -> List(Y,     N,           FU_ALU,  ALU_MAX,     OP_VREG,      OP_X,  OP_SREG,  Y    ),
    VMIN        -> List(Y,     N,           FU_ALU,  ALU_MIN,     OP_VREG,      OP_X,  OP_SREG,  Y    ),
    // Activation
    VRELU       -> List(Y,     N,           FU_ACT, ACT_RELU,     OP_VREG,      OP_X,  OP_VREG,  Y    ),
    // VTANH       -> List(Y,     N,           FU_ACT, ACT_TANH,     OP_VREG,      OP_X,  OP_VREG,  Y    ),
    // VSIGM       -> List(Y,     N,           FU_ACT, ACT_SIGM,     OP_VREG,      OP_X,  OP_VREG,  Y    ),
    // Load/Store
    VLD         -> List(Y,     N,           FU_LSU,   LSU_LD,     OP_SREG,      OP_X,  OP_VREG,  Y    ),
    VST         -> List(Y,     N,           FU_LSU,   LSU_ST,     OP_SREG,   OP_VREG,     OP_X,  N    ),
    // Config
    CONFIG      -> List(Y,     N,           FU_CFG,    CMD_X,     OP_SREG,   OP_SREG,     OP_X,  N    ),
    // Photonic
    DOTP        -> List(Y,     Y,           FU_PHO, PHO_DOTP,     OP_VREG,   OP_VREG,  OP_SREG,  N    ),
    MVM         -> List(Y,     Y,           FU_PHO,  PHO_MVM,     OP_VREG,      OP_X,  OP_VREG,  Y    ),
    )
}

object FIONADecodeTableSpecial { // Special rules for decoding new instructions (overriding)
  import FIONAInstructions._
  val default: List[BitPat] =
                  List(N,     N,           FU_ALU,  ALU_ADD, OP_X,      OP_X,     OP_X,     N    )
  val table: Array[(BitPat, List[BitPat])] = Array(
    // val sigs =      valid, is_photonic, fu_type, fu_cmd,      op1_type,  op2_type, res_type, wb_en)
    CONFIG_MAT  -> List(Y,     N,          FU_LSU,  LSU_MAT, OP_SREG,   OP_X,     OP_X,  N    ),
    )
}