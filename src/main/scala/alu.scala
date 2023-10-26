package fiona

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import FIONADecodeConstants._

class SimpleALU extends Module with HasFIONAParameters {
  val io = IO(new Bundle {
    val a = Input(UInt(FIONAXLen.W))
    val b = Input(UInt(FIONAXLen.W))
    val fn = Input(UInt(4.W))
    val o = Output(UInt(FIONAXLen.W))
  })
  io.o := MuxCase(0.S, Array(
    ( io.fn === ALU_ADD ) -> (io.a.asSInt + io.b.asSInt),
    ( io.fn === ALU_SUB ) -> (io.a.asSInt - io.b.asSInt),
    ( io.fn === ALU_SUB_INV ) -> (io.b.asSInt - io.a.asSInt),
    // ...
    )
  ).asUInt
}

class VecSelector extends Module with HasFIONAParameters {
  val io = IO(new Bundle {
    val src = Input(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
    val pos = Input(UInt(FIONAXLen.W))
    val o = Output(UInt(FIONAXLen.W))
  })
    io.o := io.src(io.pos)
}

class MinMaxFinder(Width: Int, isMin: Boolean) extends Module with HasFIONAParameters {
  val io = IO(new Bundle {
    val src = Input(Vec(Width, UInt(FIONAXLen.W)))
    val o = Output(UInt(FIONAXLen.W))
  })
  def findMin(a: SInt, b: SInt) = Mux( a < b, a, b )
  def findMax(a: SInt, b: SInt) = Mux( a > b, a, b )
  val signed_src = Wire(Vec(Width, SInt(FIONAXLen.W)))
  signed_src := io.src.asTypeOf(signed_src)
  // Tree-like structure
  require(isPow2(Width))
  val NLevels = log2Up(Width) + 1
  val temp_res = Array.fill(NLevels)(Wire(Vec(Width, SInt(FIONAXLen.W))))
  temp_res(0) := signed_src
  var elementOutputCurrentLevel = Width / 2
  // If width = 8,
  // temp_res(level 1)(0) = min(0...j)
  for( i <- 1 until NLevels ) {
    temp_res(i) := 0.U.asTypeOf(temp_res(i))
    for (j <- 0 until elementOutputCurrentLevel) {
      if(isMin) {
        temp_res(i)(j) := findMin(temp_res(i-1)(j*2), temp_res(i-1)(j*2+1))
      } else {
        temp_res(i)(j) := findMax(temp_res(i-1)(j*2), temp_res(i-1)(j*2+1))
      }
    }
    elementOutputCurrentLevel = elementOutputCurrentLevel / 2
  }

  io.o := temp_res(NLevels - 1)(0).asUInt
}

class vALU(implicit p: Parameters) extends AbstractEXU(FU_ALU)(p) {
  // TODO: Slice the vector into sub-vector (0, vlen)
  val busy = RegInit(false.B)

  // Arithmetic element independent
  val ariths = Array.fill(FIONANLane)(Module(new SimpleALU))
  val alu_res = Reg(Vec(Nbatches,  Vec(FIONANLane, UInt(FIONAXLen.W))))
  for ( i <- 0 until FIONANLane ) {
    ariths(i).io.a := op1_batch(i)
    ariths(i).io.b := op2_batch(i)
    ariths(i).io.fn := fn
  }
  for ( i <- 0 until FIONANLane ) {
    when((fn === ALU_ADD || fn === ALU_SUB || fn === ALU_SUB_INV) && busy) {
      alu_res(beatCount)(i) := ariths(i).io.o
    }
  }

  // Vector shuffle
  val shfls = Array.fill(FIONANLane)(Module(new VecSelector))
  val shfl_res = Reg(Vec(Nbatches,  Vec(FIONANLane, UInt(FIONAXLen.W))))
  for ( i <- 0 until FIONANLane ) {
    shfls(i).io.src := src1_masked
    shfls(i).io.pos := op2_batch(i)
  }
  for ( i <- 0 until FIONANLane ) {
    when(fn === ALU_SHFL && busy) {
      shfl_res(beatCount)(i) := shfls(i).io.o
    }
  }


  // Reduce Ops
  val grpSize = 4 // vec = vec / 4 per cycle
  val NMinMaxCycles = log2Up(FIONAVLenMax) / log2Up(grpSize)  // = log(grpSize, FIONAVLenMax)
  val log2GrpSize = log2Up(grpSize)
  val NFinders = FIONAVLenMax / grpSize
  val min_find = Array.fill(NFinders)(Module(new MinMaxFinder(grpSize, isMin = true)))
  val max_find = Array.fill(NFinders)(Module(new MinMaxFinder(grpSize, isMin = false)))
  val minmax_res_reg = Reg(Vec(FIONAVLenMax, UInt(FIONAXLen.W)))
  val minmax_input = Wire(Vec(NFinders, Vec(grpSize, UInt(FIONAXLen.W))))  // Divide it into sub groups
  val minmax_out = Wire(Vec(NFinders, UInt(FIONAXLen.W)))  // Divide it into sub groups
  minmax_input := minmax_res_reg.asTypeOf(minmax_input)
  // Reduce operation needs fewer cycles
  when(in_valid && (fn === ALU_MIN || fn === ALU_MAX)) {
    minmax_res_reg := src1_masked
  }
  val isMin = fn === ALU_MIN
  for (i <- 0 until NFinders) {
    min_find(i).io.src := minmax_input(i)
    max_find(i).io.src := minmax_input(i)
    minmax_out(i) := Mux(isMin, min_find(i).io.o, max_find(i).io.o)
  }
  // Collect the result, update the register following the rule:
  // 0, ... Grp0end -> 0
  // Grp0end+1, ... Grp1end -> 1
  // 0, 1, ..... N
  // [0] = min(g0), [1] = min(g1), [2] = min(g2), [3] = min(g3), ... (Don't care)
  // [0] = min(g0, g1, g2, g3) ... (Don't care)
  for(i <- 0 until NFinders) {
    when((fn === ALU_MIN || fn === ALU_MAX) && busy) {
      // Update rule remain the same (dont care about other values)
      minmax_res_reg(i) := minmax_out(i)
    }
  }
  val minmax_res = minmax_res_reg(0)

  // Control
  val beatCountMax = RegInit(0.U(xLen.W))
  val processed_element = RegInit(0.U(32.W))
  when(in_valid && !busy) {
    beatCount := 0.U
    busy := true.B
    alu_res := 0.U.asTypeOf(alu_res)
    shfl_res := 0.U.asTypeOf(shfl_res)
    processed_element := 0.U
    beatCountMax := MuxCase(0.U, Array(
      in_batches -> Nbatches.U,
      (fn === ALU_MAX || fn === ALU_MIN) -> NMinMaxCycles.U,
      )
    )
  }
  
  when(busy) {
    beatCount := beatCount + 1.U
    processed_element := processed_element + FIONANLane.U
    when(beatCount === beatCountMax || (processed_element >= io.vlen && in_batches)) {
      busy := false.B
      res.valid := true.B
    }
  }
  // Write back
  res.vd_val := MuxCase(0.U.asTypeOf(res.vd_val), Array(
    (fn === ALU_ADD || fn === ALU_SUB || fn === ALU_SUB_INV) -> alu_res.asTypeOf(res.vd_val),
    (fn === ALU_SHFL) -> shfl_res.asTypeOf(res.vd_val),
    )
  )
  res.rd_val := MuxCase(0.U, Array(
    (fn === ALU_MAX || fn === ALU_MIN) -> minmax_res,
    )
  )
}

