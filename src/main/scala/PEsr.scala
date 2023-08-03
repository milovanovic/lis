package lis

import dsptools.numbers._

import chisel3.{fromDoubleToLiteral => _, fromIntToBinaryPoint => _, _}
import fixedpoint._

import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

class PEsrIO[T <: Data: Real](params: LISParams[T], index: Int) extends Bundle {
  val sortDir = if (params.rtcSortDir) Some(Input(Bool())) else None
  val xor_input = Input(Bool())
  val data_rm = if (index == (params.LISsize + 1)) None else Some(Input(params.proto.cloneType))
  val data_insert = Input(params.proto.cloneType)
  val sorted_data1 = if (index == (params.LISsize + 1)) None else Some(Input(params.proto.cloneType))
  val sorted_data2 = Input(params.proto.cloneType)
  val prev_data = if (index == 1 || index == (params.LISsize + 1)) None else Some(Input(params.proto.cloneType))
  val next_data = if (index == (params.LISsize + 1)) None else Some(Output(params.proto.cloneType))
  val data_out = Output(params.proto.cloneType)
  val isLast = Input(Bool())
  val xor_output = Output(Bool())
}

object PEsrIO {
  def apply[T <: Data: Real](params: LISParams[T], index: Int): PEsrIO[T] = new PEsrIO(params, index)
}

class PEsr[T <: Data: Real](val params: LISParams[T], index: Int) extends Module {
  val io = IO(PEsrIO(params, index))
  if (index < params.LISsize + 1) {
    val compare_1_sel = Mux(
      io.sortDir.getOrElse(params.sortDir.B),
      io.sorted_data1.get < io.data_rm.get,
      io.sorted_data1.get > io.data_rm.get
    )
    dontTouch(compare_1_sel)
    compare_1_sel.suggestName("compare_1_sel")
    val compare_1 = Mux(compare_1_sel, true.B, false.B)
    dontTouch(compare_1)
    compare_1.suggestName("compare_1")
    val mux_1 = Mux(compare_1, io.sorted_data1.get, io.sorted_data2)
    dontTouch(mux_1)
    mux_1.suggestName("mux_1")
    val compare_2_sel = Mux(io.sortDir.getOrElse(params.sortDir.B), mux_1 < io.data_insert, mux_1 > io.data_insert)
    dontTouch(compare_2_sel)
    compare_2_sel.suggestName("compare_2_sel")
    val compare_2 = Mux(compare_2_sel, true.B, false.B)
    dontTouch(compare_2)
    compare_2.suggestName("compare_2")
    val xor_1 = if (index == 1) ~compare_2 else compare_2 ^ io.xor_input
    dontTouch(xor_1)
    xor_1.suggestName("xor_1")
    val run_time_mux_0 = xor_1
    val run_time_mux_1 = compare_2
    val mux_2 =
      if (index == 1) Mux(compare_2, mux_1, io.data_insert)
      else Mux(run_time_mux_1, mux_1, Mux(run_time_mux_0, io.data_insert, io.prev_data.get))
    dontTouch(mux_2)
    mux_2.suggestName("mux_2")
    io.data_out := mux_2
    io.xor_output := compare_2
    io.next_data.get := mux_1
  } else {
    val mux_1 = Mux(io.xor_input, io.data_insert, io.sorted_data2)
    io.data_out := mux_1
    io.xor_output := false.B
  }
}
// comment: suggestName and dontTouch are added for easier debugging and testing

object PEsrApp extends App {
  val params: LISParams[FixedPoint] = LISParams(
    proto = FixedPoint(16.W, 14.BP),
    LISsize = 16,
    LISsubType = "LIS_FIFO",
    rtcSize = true,
    sortDir = true,
    rtcSortDir = true
  )
  (new ChiselStage)
    .execute(Array("--target-dir", "verilog/PEsr"), Seq(ChiselGeneratorAnnotation(() => new PEsr(params, 5))))
}
