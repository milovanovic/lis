package lis

import chisel3._
import chisel3.util._
import dsptools._
import dsptools.numbers._
import chisel3.experimental.FixedPoint

import chisel3.internal.requireIsChiselType

//dut.io.flushData.get
class LISIO[T <: Data: Real] (params: LISParams[T]) extends Bundle {
  val in = Flipped(Decoupled(params.proto))
  val lastIn = Input(Bool())
  val flushData = if (params.flushData) Some(Input(Bool())) else None
  val out = Decoupled(params.proto)
  val lastOut = Output(Bool())
  val sortedData = Output(Vec(params.LISsize, params.proto)) // for fpga testing this output is not used

  val sortDir = if (params.rtcSortDir) Some(Input(Bool())) else None
  val sorterFull = if (params.useSorterFull) Some(Output(Bool())) else None
  val sorterEmpty = if (params.useSorterEmpty) Some (Output(Bool())) else None
  val lisSize = if (params.rtcSize == true) Some(Input(UInt((log2Up(params.LISsize)+1).W))) else None
  val discardPos = if (params.LISsubType == "LIS_input") Some(Input(UInt(log2Up(params.LISsize).W))) else None

  override def cloneType: this.type = LISIO(params).asInstanceOf[this.type]
}

object LISIO {
  def apply[T <: Data : Real](params: LISParams[T]): LISIO[T] = new LISIO(params)
}

class LinearSorter [T <: Data: Real] (val params: LISParams[T]) extends Module {
  require(params.LISsize > 1, s"Sorter size must be > 1")
  params.checkLISType()
  params.checkLISsubType()
  params.checkLIS_fixedSettings()
  params.checkDiscardPosition()

  val io = IO(LISIO(params))

  if (params.LIStype == "LIS_CNT") {
    val lisCNT = Module(new LinearSorterCNT(params))
    lisCNT.io <> io
  }
  else {
    val lisSR =  Module(new LinearSorterSR(params))
    lisSR.io <> io
  }
}

object LISApp extends App
{
  val params: LISParams[FixedPoint] = LISParams(
    proto = FixedPoint(16.W, 14.BP),
    LISsize = 8,
    LIStype = "LIS_CNT",
    LISsubType = "LIS_input",
    rtcSize = false,
    sortDir = true
  )
  chisel3.Driver.execute(args,()=>new LinearSorter(params))
}
