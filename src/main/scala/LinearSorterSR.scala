package lis

import chisel3._
import chisel3.util._
import dsptools.numbers._
import chisel3.experimental.FixedPoint

import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

class LinearSorterSR [T <: Data: Real] (val params: LISParams[T]) extends Module {
  require(params.LISsize > 1, s"Sorter size must be > 1")
  params.checkLISType()
  params.checkLISsubType()
  params.checkLIS_fixedSettings()
  params.checkDiscardPosition()

  val io = IO(LISIO(params))

  val initialInDone = RegInit(false.B)
  val cntInData = RegInit(0.U(log2Up(params.LISsize).W))
  val cellIndices = (0 until params.LISsize)
  val goToIdle = RegInit(false.B)

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////// reset logic /////////////////////////
  val rstProto = Wire(params.proto.cloneType)
  val n_min = params.proto match {
    case f: FixedPoint => -(1 << (f.getWidth - f.binaryPoint.get - 1)).toDouble
    case s: SInt => -(1<< (s.getWidth-1)).toDouble
    case u: UInt =>  0.0
    case d: DspReal => Double.MinValue
  }

  val n_max = params.proto match {
    case f: FixedPoint => ((1 << (f.getWidth - f.binaryPoint.get - 1)) - math.pow(2, -f.binaryPoint.get)).toDouble
    case s: SInt => ((1<< (s.getWidth-1))-1).toDouble
    case u: UInt =>  ((1 << u.getWidth)-1).toDouble
    case d: DspReal => Double.MaxValue
  }

  rstProto := Mux(io.sortDir.getOrElse(params.sortDir.B), Real[T].fromDouble(n_max), Real[T].fromDouble(n_min))
  val sortedDataExt = RegInit(VecInit(Seq.fill(params.LISsize + 1)(rstProto)))
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // FSM states for control logic
  val sIdle :: sProcess :: sFlush :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val state_next = Wire(state.cloneType)

  val lisSizeReg = RegInit(params.LISsize.U)
  val sortDirReg = RegInit(params.sortDir.B)

  val lose_data = WireInit(rstProto)
  val outDataFifo = if (params.LISsubType == "LIS_FIFO") Some(WireInit(rstProto)) else None

  if (params.LISsubType == "LIS_FIFO") {
    val shiftRegLISsr =  Module(new AdjustableShiftRegisterStream(params.proto, params.LISsize, sendCnt = true, adjust = params.rtcSize))
    shiftRegLISsr.io.in <> io.in
    shiftRegLISsr.io.depth := io.lisSize.getOrElse(params.LISsize.U)
    shiftRegLISsr.io.lastIn := io.lastIn
    shiftRegLISsr.io.out.ready := io.out.ready // should be confirmed!
    val srLISOutVector = shiftRegLISsr.io.parallelOut
    outDataFifo.get := shiftRegLISsr.io.out.bits

    when (~initialInDone) {
      lose_data := rstProto
    }
    .otherwise {
      lose_data := shiftRegLISsr.io.out.bits
    }
  }
  else if (params.LISsubType == "LIS_fixed") {
    lose_data := Mux(initialInDone, sortedDataExt(params.discardPos.get), rstProto)
  }
  else if (params.LISsubType == "LIS_input") {
    lose_data := Mux(initialInDone, sortedDataExt(io.discardPos.get), rstProto)
  }

  when (io.in.fire()) {
    cntInData := cntInData +% 1.U
  }
  .elsewhen (state === sIdle) {
    cntInData := 0.U
  }

  when (cntInData === (lisSizeReg - 1.U) && io.in.fire()) {
    initialInDone := true.B
  }
  .elsewhen (state_next === sIdle) {
    initialInDone := false.B
  }
  state_next := state
  val enable = io.out.valid && io.out.ready && state === sFlush
  val cntOutDataWire = Wire(UInt(log2Up(params.LISsize).W))
  val (cntOutData, wrap) = CounterWithReset(cond = enable, initValue = 0.U((log2Up(params.LISsize)).W), reset = state === sIdle, n = params.LISsize)

  cntOutDataWire := cntOutData
  val fireLastIn = io.lastIn && io.in.fire()

  val inputData = Wire(io.in.bits.cloneType)
  if (params.LISsubType != "LIS_FIFO")
    inputData := Mux(state_next =/= sFlush, io.in.bits, sortedDataExt(lisSizeReg-1.U))
  else
    inputData := io.in.bits

  val LISnetworkSR = Module(new LISNetworkSR(params))
  if (params.rtcSortDir) {
    LISnetworkSR.io.sortDir.get := io.sortDir.get
  }
  LISnetworkSR.io.data_rm := lose_data
  LISnetworkSR.io.data_insert := inputData //io.in.bits
  when (state =/= sIdle || (state === sIdle && io.in.fire())) {
    sortedDataExt := LISnetworkSR.io.nextSortedData
  }
  LISnetworkSR.io.sortedData := sortedDataExt
  if (params.rtcSize == true) {
    LISnetworkSR.io.lisSize.get := io.lisSize.get
  }

  switch (state) {
    is (sIdle) {
      lisSizeReg := io.lisSize.getOrElse(params.LISsize.U)
      sortDirReg := io.sortDir.getOrElse(params.sortDir.B)

      when (io.in.fire()) { state_next := sProcess }
    }
    is (sProcess) {
      when ((io.flushData.getOrElse(false.B) || fireLastIn)) {
        state_next := sFlush
      }
    }
    is (sFlush) {
      when (cntOutDataWire === (lisSizeReg-1.U)) {
        state_next := sIdle
      }
    }
  }
  state := state_next


  if (params.useSorterFull) {
    io.sorterFull.get := initialInDone && state =/= sFlush
  }
  if (params.useSorterEmpty) {
    io.sorterEmpty.get := state === sIdle
  }

  io.sortedData := sortedDataExt.take(params.LISsize)
  if (params.LISsubType == "LIS_FIFO")
    io.out.bits := outDataFifo.get //sortedDataExt(0) // send the largest data to the output
  else //if (params.LISsubType == "LIS_fixed")
    io.out.bits := sortedDataExt(io.discardPos.getOrElse(params.discardPos.get.U))
  io.lastOut  := cntOutDataWire === (lisSizeReg-1.U)
  io.in.ready := ~initialInDone || io.out.ready && state =/= sFlush
  io.out.valid := initialInDone && io.in.valid || state === sFlush //initialInDone && RegNext(io.in.valid) || state === sFlush
}

object LISsrApp extends App
{
  val params: LISParams[FixedPoint] = LISParams(
    proto = FixedPoint(16.W, 14.BP),
    LISsize = 64,
    LISsubType = "LIS_FIFO",
    discardPos = None,
    rtcSize = false,
    rtcSortDir = false,
    sortDir = true
  )
  chisel3.Driver.execute(args,()=>new LinearSorterSR(params))
}

object LinearSorterSRApp extends App
{
  implicit def int2bool(b: Int) = if (b == 1) true else false
  if (args.length < 5) {
    println("This application requires at least 5 arguments")
  }
  val buildDirName = args(0).toString
  val wordSize = args(1).toInt
  val sorterSize = args(2).toInt
  val isRunTime = int2bool(args(3).toInt)
  val sorterSubType = args(4).toString
  val separateVerilog = int2bool(args(5).toInt)

  val params: LISParams[FixedPoint] = LISParams(
    proto = FixedPoint(16.W, 14.BP),
    LISsize = sorterSize,
    LIStype = "LIS_SR",
    LISsubType = sorterSubType,
    discardPos = if (sorterSubType == "LIS_fixed") Some(sorterSize/2) else None,
    rtcSize = isRunTime,
    rtcSortDir = false,
    sortDir = true
  )
  if (separateVerilog == true) {
    val arguments = Array(
      "--target-dir", buildDirName,
      "-e", "verilog",
      "-X", "verilog",
      "--log-level", "info"
    )
    (new ChiselStage).execute(arguments, Seq(ChiselGeneratorAnnotation(() =>new LinearSorterSR(params))))
  }
  else {
    val arguments = Array(
      "--target-dir", buildDirName,
      "-X", "verilog",
      "--log-level", "info"
    )
    (new ChiselStage).execute(arguments, Seq(ChiselGeneratorAnnotation(() =>new LinearSorterSR(params))))
  }
}
