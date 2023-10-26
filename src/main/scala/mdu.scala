package fiona

import chisel3._
import chisel3.util._
import freechips.rocketchip.rocket._
import FIONADecodeConstants._
import org.chipsalliance.cde.config._

class MUL extends Module with HasFIONAParameters {
  val io = IO(new Bundle {
    val ivalid = Input(Bool())
    val a = Input(UInt(FIONAXLen.W))
    val b = Input(UInt(FIONAXLen.W))
    val o = Output(UInt(FIONAXLen.W))
    val ovalid = Output(Bool())
  })
  io.o := io.a * io.b
  io.ovalid := io.ivalid
}

class DIV extends Module with HasFIONAParameters {
  val io = IO(new Bundle {
    val ivalid = Input(Bool())
    val a = Input(UInt(FIONAXLen.W))
    val b = Input(UInt(FIONAXLen.W))
    val o = Output(UInt(FIONAXLen.W))
    val ovalid = Output(Bool())
  })
  val div = Module(new MulDiv(MulDivParams(divEarlyOut = false), width = 32, nXpr = 1))  // We will reuse the divider, remaining the higher 16 bits 0..
  div.io.kill := false.B
  val divreq = div.io.req
  divreq.valid := io.ivalid
  divreq.bits.dw := DW_32
  def sext(x: Bits, toWidth: Int) = {
    val w = x.getWidth
    val sign = x(w-1)
    val hi = Fill((toWidth -w), sign)
    Cat(hi, x)
  }
  divreq.bits.fn := (new ALUFN).FN_DIV
  divreq.bits.in1 := sext(io.a, 32)
  divreq.bits.in2 := sext(io.b, 32)
  divreq.bits.tag := 0.U

  io.ovalid := div.io.resp.valid
  div.io.resp.ready := true.B
  io.o := div.io.resp.bits.data

}

class vMDU(implicit p: Parameters) extends AbstractEXU(FU_MDU)(p) {
  val busy = RegInit(false.B)
  val muls = Array.fill(FIONANLane)(Module(new MUL))
  val mul_res = Reg(Vec(Nbatches, Vec(FIONANLane, UInt(FIONAXLen.W))))
  for ( i <- 0 until FIONANLane ) {
    muls(i).io.a := op1_batch(i)
    muls(i).io.b := op2_batch(i)
    muls(i).io.ivalid := (fn === MDU_MUL && in_valid)
  }
  val divs = Array.fill(FIONANLane)(Module(new DIV))
  val div_res = Reg(Vec(Nbatches, Vec(FIONANLane, UInt(FIONAXLen.W))))
  for ( i <- 0 until FIONANLane ) {
    divs(i).io.a := op2_batch(i)
    divs(i).io.b := op1_batch(i)
    divs(i).io.ivalid := (fn === MDU_DIV && in_valid)
  }
  val beatCountMax = RegInit(0.U(xLen.W))
  val processed_element = RegInit(0.U(32.W))
  when(in_valid && ~busy) {
    beatCount := 0.U // The assignment will be overwritten by the next condition statements
    busy := true.B
    processed_element := 0.U
    beatCountMax := Nbatches.U
    mul_res := 0.U.asTypeOf(mul_res)
    div_res := 0.U.asTypeOf(div_res)
  }
  for ( i <- 0 until FIONANLane ) {
    when(busy && muls(0).io.ovalid) {
      mul_res(beatCount)(i) := muls(i).io.o
      beatCount := beatCount + 1.U
      processed_element := processed_element + FIONANLane.U
    }
    when(busy && divs(0).io.ovalid) {
      div_res(beatCount)(i) := divs(i).io.o
      beatCount := beatCount + 1.U
      processed_element := processed_element + FIONANLane.U
    }
  }
  when(beatCount === Nbatches.U || (processed_element >= io.vlen)) {
    busy := false.B
    res.valid := true.B
    beatCount := 0.U
    processed_element := 0.U
  }
  res.vd_val := MuxCase(0.U.asTypeOf(res.vd_val), Array(
    (fn === MDU_MUL) -> mul_res.asTypeOf(res.vd_val), 
    (fn === MDU_DIV) -> div_res.asTypeOf(res.vd_val)
  )
)
}
