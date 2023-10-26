package fiona

import chisel3._
import chisel3.util._
import FIONADecodeConstants._
import org.chipsalliance.cde.config._

// Table-lookup signals
class ActivationSrcBundle extends Bundle {
  val sign = Bool()
  val idx = UInt(8.W)  // TODO: Should be parameterizable
  val remaining = UInt(7.W)  // TODO: Should be parameterizable
}

// Table-lookup method for activation functions
abstract class ActFunction(CMD_Type: BitPat) extends Module with HasFIONAParameters {
  val io = IO(new Bundle {
    val fn = Input(UInt(3.W))
    val a = Input(UInt(FIONAXLen.W))
    val o = Output(UInt(FIONAXLen.W))
    val ovalid = Output(Bool())
    val tableIdx = Output(UInt(8.W))
    val table_va = Input(UInt(16.W))
    val table_vb = Input(UInt(16.W))
  })
  val cmd = CMD_Type
  val valid_in = io.fn === cmd
  io.ovalid := valid_in
  io.o := io.a

  val sign = io.a.asTypeOf(new ActivationSrcBundle).sign
  val activation_src = WireInit(io.a.asTypeOf(new ActivationSrcBundle))
  when(sign) {
    activation_src := (-io.a.asSInt).asTypeOf(new ActivationSrcBundle)
  }
  val index = activation_src.idx
  io.tableIdx := activation_src.idx
  val remaining = activation_src.remaining

  val ua = io.table_va
  val ub = io.table_vb
}

class ReLU extends ActFunction(ACT_RELU) {
  io.o := Mux( io.a.asSInt > 0.S, io.a, 0.U )
}

class Activation extends Module with HasFIONAParameters {
  val io = IO(new Bundle {
    val a = Input(UInt(FIONAXLen.W))
    val fn = Input(UInt(4.W))
    val o = Output(UInt(FIONAXLen.W))
    val ovalid = Output(Bool())
  })
  val relu = Module(new ReLU)

  relu.io.a := io.a

  relu.io.fn := io.fn

  // Tie off unused input
  relu.io.table_va := 0.U
  relu.io.table_vb := 0.U

  io.o := MuxCase(0.U, Array(
    ( io.fn === ACT_RELU) -> relu.io.o,
    // ( io.fn === ACT_TANH) -> tanh.io.o,
    // ( io.fn === ACT_SIGM) -> sigmoid.io.o,
    // ...
    )
  )
  io.ovalid := relu.io.ovalid //| sigmoid.io.ovalid | tanh.io.ovalid
}

class vActivation(implicit p: Parameters) extends AbstractEXU(FU_ACT)(p) {
  val busy = RegInit(false.B)
  val activations = Array.fill(FIONANLane)(Module(new Activation))
  val act_res = Reg(Vec(Nbatches, Vec(FIONANLane, UInt(FIONAXLen.W))))
  for ( i <- 0 until FIONANLane ) {
    activations(i).io.a := op2_batch(i)
    activations(i).io.fn := fn
  }
  val step = activations(0).io.ovalid
  val processed_element = RegInit(0.U(32.W))
  for ( i <- 0 until FIONANLane ) {
    when(busy && step) {
      act_res(beatCount)(i) := activations(i).io.o
      beatCount := beatCount + 1.U
      processed_element := processed_element + FIONANLane.U
    }
  }
  val beatCountMax = RegInit(0.U(xLen.W))
  when(in_valid && ~busy) { // Initialize
    beatCount := 0.U
    busy := true.B
    beatCountMax := Nbatches.U
    processed_element := 0.U
    act_res := 0.U.asTypeOf(act_res)
  }
  when(beatCount === Nbatches.U || (processed_element >= io.vlen)) {
    busy := false.B
    res.valid := true.B
    beatCount := 0.U
    processed_element := 0.U
  }
  res.vd_val := act_res.asTypeOf(res.vd_val)
}
