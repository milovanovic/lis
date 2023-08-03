package lis

import chisel3.util._
import dsptools.numbers._
import chisel3.{fromDoubleToLiteral => _, fromIntToBinaryPoint => _, _}
import fixedpoint._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chisel3.experimental.requireIsChiselType

case class LISParams[T <: Data: Real](
  proto:          T,
  LISsize:        Int,
  LISsubType:     String = "LIS_FIFO",
  LIStype:        String = "LIS_CNT",
  rtcSize:        Boolean = false,
  rtcSortDir:     Boolean = false,
  discardPos:     Option[Int] = None,
  flushData:      Boolean = false,
  sendMiddle:     Boolean = false,
  enInitStore:    Boolean = true, // in.ready not equal to out.ready during the init data storing
  useSorterFull:  Boolean = false,
  useSorterEmpty: Boolean = false,
  sortDir:        Boolean = true) {

  final val allowedLISsubTypes = Seq("LIS_FIFO", "LIS_input", "LIS_fixed")
  final val allowedLISTypes = Seq("LIS_CNT", "LIS_SR")

  requireIsChiselType(proto, s"($proto) must be chisel type")

  def checkLISType(): Unit = {
    require(
      allowedLISTypes.contains(LIStype),
      s"""LIS type must be one of the following: ${allowedLISTypes.mkString(", ")}"""
    )
  }
  def checkLISsubType(): Unit = {
    require(
      allowedLISsubTypes.contains(LISsubType),
      s"""LIS type must be one of the following: ${allowedLISTypes.mkString(", ")}"""
    )
  }
  def checkLIS_fixedSettings(): Unit = {
    require(
      (LISsubType == "LIS_fixed" && !discardPos.isEmpty) || LISsubType != "LIS_fixed",
      s"Position of discarding element must be defined for LIS_fixed linear sorter scheme"
    )
  }
  def checkDiscardPosition(): Unit = {
    require((discardPos.getOrElse(0) < LISsize), s"Position of discarding element must be less than sorter size")
  }
}

//dut.io.flushData.get
class LISIO[T <: Data: Real](params: LISParams[T]) extends Bundle {
  val in = Flipped(Decoupled(params.proto))
  val lastIn = Input(Bool())
  val flushData = if (params.flushData) Some(Input(Bool())) else None
  val out = Decoupled(params.proto)
  val lastOut = Output(Bool())
  val sortedData = Output(Vec(params.LISsize, params.proto)) // for fpga testing this output is not used
  val middleData = if (params.sendMiddle && params.LIStype == "LIS_SR") Some(Output(params.proto)) else None
  val sortDir = if (params.rtcSortDir) Some(Input(Bool())) else None
  val sorterFull = if (params.useSorterFull) Some(Output(Bool())) else None
  val sorterEmpty = if (params.useSorterEmpty) Some(Output(Bool())) else None
  val lisSize = if (params.rtcSize == true) Some(Input(UInt((log2Up(params.LISsize) + 1).W))) else None
  val discardPos = if (params.LISsubType == "LIS_input") Some(Input(UInt(log2Up(params.LISsize).W))) else None
}

object LISIO {
  def apply[T <: Data: Real](params: LISParams[T]): LISIO[T] = new LISIO(params)
}

class LinearSorter[T <: Data: Real](val params: LISParams[T]) extends Module {
  require(params.LISsize > 1, s"Sorter size must be > 1")
  params.checkLISType()
  params.checkLISsubType()
  params.checkLIS_fixedSettings()
  params.checkDiscardPosition()

  // module name
  val run_flag = if (params.rtcSize) "on" else "off"

  override def desiredName =
    params.LIStype + "_" + params.LISsubType + "_size_" + params.LISsize.toString + "_width_" + params.proto.getWidth.toString + "_runtime_" + run_flag

  val io = IO(LISIO(params))

  if (params.LIStype == "LIS_CNT") {
    val lisCNT = Module(new LinearSorterCNT(params))
    lisCNT.io <> io
  } else {
    val lisSR = Module(new LinearSorterSR(params))
    lisSR.io <> io
  }
}

object LISsimpleApp extends App {
  val params: LISParams[FixedPoint] = LISParams(
    proto = FixedPoint(16.W, 14.BP),
    LISsize = 8,
    LIStype = "LIS_CNT",
    LISsubType = "LIS_input",
    rtcSize = false,
    sortDir = true
  )
  (new ChiselStage)
    .execute(Array("--target-dir", "verilog/LISsimple"), Seq(ChiselGeneratorAnnotation(() => new LinearSorter(params))))
}

object LISApp extends App {
  implicit def int2bool(b: Int) = if (b == 1) true else false
  if (args.length < 5) {
    println("This application requires at least 5 arguments")
  }
  val buildDirName = args(0).toString
  val wordSize = args(1).toInt
  val sorterSize = args(2).toInt
  val isRunTime = int2bool(args(3).toInt)
  val sorterType = args(4).toString
  val sorterSubType = args(5).toString
  val separateVerilog = int2bool(args(6).toInt)

  val params: LISParams[FixedPoint] = LISParams(
    proto = FixedPoint(wordSize.W, (wordSize - 2).BP),
    LISsize = sorterSize,
    LIStype = sorterType,
    LISsubType = sorterSubType,
    discardPos = if (sorterSubType == "LIS_fixed") Some(sorterSize / 2) else None,
    rtcSize = isRunTime,
    rtcSortDir = false,
    sortDir = true
  )
  if (separateVerilog == true) {
    val arguments = Array(
      "--target-dir",
      buildDirName,
      "-e",
      "verilog",
      "-X",
      "verilog",
      "--log-level",
      "info"
    )
    (new ChiselStage).execute(arguments, Seq(ChiselGeneratorAnnotation(() => new LinearSorter(params))))
  } else {
    val arguments = Array(
      "--target-dir",
      buildDirName,
      "-X",
      "verilog",
      "--log-level",
      "info"
    )
    (new ChiselStage).execute(arguments, Seq(ChiselGeneratorAnnotation(() => new LinearSorter(params))))
  }
}
