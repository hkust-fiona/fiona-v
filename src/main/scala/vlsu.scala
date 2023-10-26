package fiona

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._
import FIONADecodeConstants._
import chisel3.util.experimental.BoringUtils

class VLSU(implicit p: Parameters) extends LazyModule {
  override lazy val module = new VLSUImp(this)
  val node = TLClientNode(Seq(TLClientPortParameters(
    Seq(TLClientParameters(name = "VLSU", sourceId = IdRange(0, 8)))
  )))
}

class VLSUImp(outer: VLSU)(implicit p: Parameters) extends LazyModuleImp(outer)
  with HasCoreParameters
  with HasFIONAParameters
  {
    val io = IO( new Bundle {
      val decodeSigs = Input(new FIONACtrlSigs)
      val stride = Input(UInt(xLen.W))
      val vlen = Input(UInt(xLen.W))
      val srcBundle = Input(new SrcBundle)
      val WbBundle = Output(new WbBundle)
      val matRegVal = Output(Vec(FIONAVLenMax, Vec(FIONAVLenMax, UInt(FIONAXLen.W))))
    } )

  val decodeSigs = io.decodeSigs
  val valid = decodeSigs.valid && decodeSigs.fu_type === FU_LSU
  val baseAddr = io.srcBundle.rs1_val
  val op_vlen_mask = Wire(Vec(FIONAVLenMax, Bool()))
  for(i <- 0 until FIONAVLenMax) {
    op_vlen_mask(i) := (i.U(xLen.W) < io.vlen)  // Note: Should specify the width here, otherwise it will not be extended to the width as vlen
  }
  def extendMask(mask: UInt, widthPerPos: Int) = {
    val maskBits = mask.asBools()
    val all_ones = VecInit(Seq.fill(widthPerPos)(1.U(1.W)))
    val all_zeros = VecInit(Seq.fill(widthPerPos)(0.U(1.W)))
    val filled = VecInit( Seq.tabulate(FIONAVLenMax) { i => Mux(maskBits(i), all_ones, all_zeros) })
    filled.asUInt
  }
  
  val storeMask_vlen = extendMask(op_vlen_mask.asUInt, FIONAXLen / 8)
  val storeMaskReg = RegInit(storeMask_vlen)

  // To support arbitrary vlen:
  // For example, vlen = 17 = "b1001", first transfer 16 bytes then 1 byte
  // Currently, we simply get the full length (log2Up), for example, 17 -> 32, 16 -> 16, 0-15 -> 16
  val xferBytes = FIONAVLenMax * FIONAXLen / 8 // Fixed, TODO: Support arbitrary vlen
  val addr = Reg(UInt(coreMaxAddrBits.W))

  val cacheParams = tileParams.dcache.get

  val s_idle :: s_acq :: s_put :: s_gnt :: s_resp :: Nil = Enum(5)
  val state = RegInit(s_idle)

  val (tl_out, edgesOut) = outer.node.out(0)
  val gnt = tl_out.d.bits
  val busWidth = tl_out.a.bits.data.getWidth
  val busBytes = busWidth / 8
  val xferBeats = xferBytes / busBytes
  val storeMaskRegRSBits = busBytes
  dontTouch(storeMaskReg)

  val storeVec = VecInit(Seq.tabulate(xferBeats) { i => io.srcBundle.vs2_val.asUInt(busWidth * (i+1) - 1, busWidth * i )} )
  val beatCnt = RegInit(0.U(xLen.W))
  val rowCnt = RegInit(0.U(xLen.W))

  val res_data_vec = Reg(Vec(xferBeats, UInt(busWidth.W)))
  val res_data_mat = Reg(Vec(FIONAVLenMax * xferBeats, UInt(busWidth.W)))
  // BoringUtils.addSource(res_data_mat, "mat_reg")
  
  io.matRegVal := res_data_mat.asTypeOf(io.matRegVal)
  dontTouch(io.matRegVal)

  // A channel data
  tl_out.a.bits := Mux( decodeSigs.fu_cmd === LSU_LD || decodeSigs.fu_cmd === LSU_MAT, 
    edgesOut.Get(
      fromSource = 0.U,
      toAddress = addr,
      lgSize = log2Up(xferBytes).U)._2, 
    edgesOut.Put(
      fromSource = 1.U, 
      toAddress = addr,
      lgSize = log2Up(xferBytes).U,
      data = storeVec(beatCnt),
      mask = storeMaskReg(busBytes, 0)
      )._2, 
      )

  val res_vec = res_data_vec.asTypeOf(io.WbBundle.vd_val)
  val ld_data_masked = VecInit( Seq.tabulate(FIONAVLenMax) { i => Mux(op_vlen_mask(i), res_vec(i), 0.U) } )
  tl_out.d.ready := false.B
  tl_out.a.valid := false.B

  io.WbBundle.valid := false.B
  io.WbBundle.rd_num := io.srcBundle.rd_num
  io.WbBundle.vd_val := ld_data_masked
  io.WbBundle.rd_val := 0.U

  switch(state) {
    is(s_idle) {
      when(valid) {
        addr := baseAddr
        beatCnt := 0.U
        state := Mux(decodeSigs.fu_cmd === LSU_LD || decodeSigs.fu_cmd === LSU_MAT, s_acq, s_put)     // To acquire data state
        storeMaskReg := storeMask_vlen
      }
    }
    is(s_acq) {
      tl_out.a.valid := true.B
      when(tl_out.a.fire()) {
        state := s_gnt
      }
    }
    is(s_gnt) {
      tl_out.d.ready := true.B
      when (tl_out.d.fire() && decodeSigs.fu_cmd === LSU_LD) {
        // Collect the data
        beatCnt := beatCnt + 1.U
        res_data_vec(beatCnt) := gnt.data
      }
      when (tl_out.d.fire() && decodeSigs.fu_cmd === LSU_MAT) {
        // Collect the data
        beatCnt := beatCnt + 1.U
        res_data_mat(beatCnt) := gnt.data
      }
      when (tl_out.d.fire() && edgesOut.done(tl_out.d)) {
        when(decodeSigs.fu_cmd === LSU_LD || decodeSigs.fu_cmd === LSU_ST) {
          state := s_resp
        }.elsewhen(decodeSigs.fu_cmd === LSU_MAT) {
          when(rowCnt === (FIONAVLenMax - 1).U) { // TODO: Support any vlen
            state := s_resp
          } otherwise {
            state := s_acq
            rowCnt := rowCnt + 1.U
            addr := addr + xferBytes.U  // TODO: Support any vlen
          }
        }
      }
    }
    is(s_put) {
      tl_out.a.valid := true.B
      when(tl_out.a.fire()) {
        beatCnt := beatCnt + 1.U
        storeMaskReg := storeMaskReg >> storeMaskRegRSBits
      }
      when(beatCnt === (xferBeats-1).U) {
        state := s_gnt
      }
    }
    is(s_resp) {
      state := s_idle
      io.WbBundle.valid := true.B
      io.WbBundle.wb_en := decodeSigs.fu_cmd === LSU_LD
      io.WbBundle.wb_vec := decodeSigs.fu_cmd === LSU_LD
    }
  }

  // Tie off unused channels
  tl_out.b.ready := true.B
  tl_out.c.valid := false.B
  tl_out.e.valid := false.B
}

