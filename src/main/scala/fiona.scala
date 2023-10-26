package fiona

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import FIONADecodeConstants._
import chisel3.util.experimental.BoringUtils

class FIONA(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) with HasFIONAParameters {
  override lazy val module = new FIONAImp(this)
  // override val atlNode = TLIdentityNode()
  lazy val lsuModule = LazyModule(new VLSU())
  // lazy val cfgModule = LazyModule(new LazyConfigRegs())
  // val xbar = LazyModule(new TLXbar())
  // xbar.node := TLWidthWidget(16) := lsuModule.node
  // xbar.node := TLBuffer() := cfgModule.node
  tlNode := TLWidthWidget(8) := lsuModule.node
  // tlNode := TLFragmenter(1, FIONAVLenMax * FIONAVLenMax / FIONAXLen / 8) := lsuModule.node
  
}

class FIONAImp(outer: FIONA)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
  with HasCoreParameters
  with HasFIONAParameters
  {

  val sIDLE :: sEX :: sWB :: Nil = Enum(3)
  val state = RegInit(sIDLE)

  val cmdReg = RegInit(io.cmd.bits)
  io.cmd.ready := (state === sIDLE)

  // Vector register
  // val vrf = Module(new VRegFileMem)
  // val vrf = Module(new VRegFile)
  val vrf = Module(new VRegFileMemDualBank)  // Try to synthesize to BRAM

  // Decompose the instruction
  val srcBundle = Wire(new SrcBundle)
  srcBundle.r1_num  := io.cmd.bits.inst.rs1 holdUnless io.cmd.fire
  srcBundle.r2_num  := io.cmd.bits.inst.rs2 holdUnless io.cmd.fire
  srcBundle.rd_num  := io.cmd.bits.inst.rd holdUnless io.cmd.fire
  srcBundle.rs1_val := io.cmd.bits.rs1 holdUnless io.cmd.fire
  srcBundle.rs2_val := io.cmd.bits.rs2 holdUnless io.cmd.fire
  // srcBundle.vs1_val := vrf.io.rs2_val holdUnless io.cmd.fire
  // srcBundle.vs2_val := vrf.io.rs1_val holdUnless io.cmd.fire

  srcBundle.vs1_val := vrf.io.rs1_val
  srcBundle.vs2_val := vrf.io.rs2_val

  // Decode logic
  val decodeSigs = Wire(new FIONACtrlSigs()).decode(io.cmd.bits.inst.asUInt)
  val decodeSpecialSigs = Wire(new FIONACtrlSigs()).decode_special(io.cmd.bits.inst.asUInt)
  dontTouch(decodeSigs)
  dontTouch(decodeSpecialSigs)
  // Override special case for MATCFG: Becuase it will use LSU
  val decodeSigsReg = RegInit(decodeSigs)  // The 2nd phase

  val cfg = Module(new ConfigRegs)
  cfg.io.decode_in := decodeSigsReg
  cfg.io.srcBundle := srcBundle

  val vmask_1 = cfg.io.mask_v1
  val vmask_2 = cfg.io.mask_v2
  val vlen = cfg.io.vlen

  val alu = Module(new vALU)
  alu.io.decode_in := decodeSigsReg
  alu.io.srcBundle := srcBundle
  alu.io.vmask_1 := vmask_1
  alu.io.vmask_2 := vmask_2
  alu.io.vlen := vlen

  val activation = Module(new vActivation)
  activation.io.decode_in := decodeSigsReg
  activation.io.srcBundle := srcBundle
  activation.io.vmask_1 := vmask_1
  activation.io.vmask_2 := vmask_2
  activation.io.vlen := vlen

  val mdu = Module(new vMDU)
  mdu.io.decode_in := decodeSigsReg
  mdu.io.srcBundle := srcBundle
  mdu.io.vmask_1 := vmask_1
  mdu.io.vmask_2 := vmask_2
  mdu.io.vlen := vlen

  val photonics = Module(new PUCSim)
  photonics.io.decode_in := decodeSigsReg
  photonics.io.srcBundle := srcBundle
  photonics.io.vmask_1 := vmask_1
  photonics.io.vmask_2 := vmask_2
  photonics.io.vlen := vlen

  val lsu = outer.lsuModule.module
  lsu.io.decodeSigs := decodeSigsReg
  lsu.io.srcBundle := srcBundle
  lsu.io.vlen := vlen
  // val mat_reg = WireInit(0.U((FIONAVLenMax*FIONAVLenMax).W))
  // mat_reg := lsu.io.matRegVal.asTypeOf(mat_reg)
  // BoringUtils.addSource(mat_reg, "mat_reg")
  photonics.io.matRegVal := lsu.io.matRegVal

  val wb_sel = decodeSigsReg.fu_type
  val wbInfo = Wire(new WbBundle)
  wbInfo := MuxCase(0.U.asTypeOf(wbInfo), Array(
    (wb_sel === FU_LSU) -> lsu.io.WbBundle,
    (wb_sel === FU_ALU) -> alu.io.WbBundle,
    (wb_sel === FU_ACT) -> activation.io.WbBundle,
    (wb_sel === FU_MDU) -> mdu.io.WbBundle,
    (wb_sel === FU_PHO) -> photonics.io.WbBundle,
    (wb_sel === FU_CFG) -> cfg.io.WbBundle,
    )
  )
  val wbInfoReg = RegInit(0.U.asTypeOf(wbInfo))
  switch(state) {
    is(sIDLE) {
      when(io.cmd.fire) {
        // Generate decode signals
        decodeSigsReg := Mux(decodeSpecialSigs.valid, decodeSpecialSigs, decodeSigs)
        // Latch the instruction, register value, register number
        cmdReg := io.cmd.bits
        state := sEX
      }
    }
    is(sEX) {
      when(wbInfo.valid) { // Done
        state := sWB
        wbInfoReg := wbInfo
        decodeSigsReg.valid := false.B
      }
    }
    is(sWB) {
      when (io.resp.fire() || ~cmdReg.inst.xd) {  // Write back to scalar regs | no need to write back
        state := sIDLE 
        wbInfoReg.valid := false.B
      }
    }
  }

  // vrf rd data
  vrf.io.rs1 := srcBundle.r1_num
  vrf.io.rs2 := srcBundle.r2_num

  // vrf wb data
  vrf.io.wd := wbInfoReg.rd_num
  vrf.io.wen := wbInfoReg.valid && wbInfoReg.wb_en && wbInfoReg.wb_vec 
  vrf.io.wval := wbInfoReg.vd_val

  // scalar wb data
  io.resp.valid := (state === sWB && cmdReg.inst.xd)
  io.resp.bits.data := wbInfoReg.rd_val
  io.resp.bits.rd := cmdReg.inst.rd

  io.busy := (state =/= sIDLE)
  // Unused channels
  io.interrupt := false.B
  io.mem.req.valid := false.B

}

class WithFionaV(fastMem: Boolean = true) extends Config ((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      val fiona = LazyModule.apply(new FIONA(OpcodeSet.custom0)(p))
      fiona
    }
  )
})

