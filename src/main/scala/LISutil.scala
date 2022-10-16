package lis

import chisel3._
import chisel3.util._
import chisel3.internal.{requireIsChiselType, requireIsHardware}
import chisel3.experimental.FixedPoint


//Based on already available Counter object from Chisel library, update is done in terms of
class CounterWithReset(n: Int, initValue: UInt) {
  require(n >= 0)
  val value = if (n > 1) RegInit(initValue) else 0.U(log2Up(n).W)
  def inc(): Bool = {
    if (n > 1) {
      val wrap = value === (n-1).asUInt
      value := value + 1.U
      if (!isPow2(n)) {
        when (wrap) { value := 0.U }
      }
      wrap
    } else {
      true.B
    }
  }
  def reset(): Unit = { value := 0.U }
}

object CounterWithReset {
  def apply(n: Int, initValue: UInt): CounterWithReset = new CounterWithReset(n, initValue)
  def apply(cond: Bool, initValue: UInt, reset: Bool, n: Int): UInt = {
    val c = new CounterWithReset(n, initValue)
    //var wrap: Bool = null
    when (cond) { c.inc() }
    when (reset) { c.reset() }
    c.value
  }
}

class LifeCounter(n: Int, dataToLoad: UInt, initData: UInt, sorterSize: UInt) extends CounterWithReset(n, initData) {
  val discard = value === (sorterSize - 1.U)
  override def inc(): Bool = {
    if (n > 1) {
      value := value + 1.U
      when (discard) { value := 0.U }
    }
    discard
  }
  def load(): Unit = { value := dataToLoad + 1.U }
}

object LifeCounter {
  def apply(cond: Bool, reset: Bool, initData: UInt, load: Bool, n: Int, dataToLoad: UInt, sorterSize: UInt) : (UInt, Bool) = {
    val c = new LifeCounter(n, dataToLoad, initData, sorterSize)
    var wrap: Bool = null
    when (load) { c.load() } // condition for load has a higher priority than condition for increment
    .elsewhen (cond) { wrap = c.inc() }
    when (reset && cond) { c.reset() }
    (c.value, c.discard)
  }
}

// Copied from sdf-fft utils !!!!!
object RegEnableWithReset {

  /** Returns a register with the specified next, update enable gate, manual reset and reset initialization.
    *
    * @example {{{
    * val regWithEnableAndReset = RegEnable(nextVal, 0.U, reset, ena)
    * }}}
    */
  def apply[T <: Data](next: T, init: T, reset: Bool, enable: Bool): T = {
    val r = RegInit(init)

    when (reset) { r := init }
    .elsewhen (enable) { r := next }
    r
  }
}

object ShiftRegisterWithReset
{
  /** Returns the n-cycle delayed version of the input signal.
    *
    * @param in input to delay
    * @param n number of cycles to delay
    * @param resetData reset value for each register in the shift
    * @param reset manual reset
    * @param en enable the shift
    */
  def apply[T <: Data](in: T, n: Int, resetData: T, reset: Bool, en: Bool = true.B): T = {
    // The order of tests reflects the expected use cases.
    if (n != 0) {
      RegEnableWithReset(apply(in, n-1, resetData, reset, en), resetData, reset, en)
    } else {
      in
    }
  }
}

// Copied from cfar utils and added adjust parameter for the case when run-time depth is not necessary to support!
object AdjustableShiftRegister {

  private def applyReccursive[T <: Data](in: T, n: Int, activeRegs: Vec[Bool], out: Vec[T], resetData: T, en: Bool = true.B, adjust: Boolean = true): T = {
    if (n != 0) {
      val enShift = activeRegs(n-1) && en
      out(n-1) := RegEnable(applyReccursive(in, n - 1, activeRegs, out, resetData, en, adjust), resetData, enShift)
      out(n-1)
    }
    else
      in
  }

  def apply[T <: Data](in: T, maxDepth: Int, depth: UInt, resetData: T, en: Bool = true.B, adjust: Boolean = true): T = {
    require(maxDepth > 0)
    requireIsHardware(in)
    assert(depth <= maxDepth.U)

    val activeRegs = Wire(Vec(maxDepth, Bool()))
//     activeRegs.suggestName("activeRegs")
//     dontTouch(activeRegs)
    activeRegs.zipWithIndex.map { case (active, index) => if (adjust == true) active := (index.U <= depth - 1.U).asBool else active := true.B  }
    val out = Wire(Vec(maxDepth, in.cloneType))
    applyReccursive(in, maxDepth, activeRegs, out, resetData, en)
    out(depth - 1.U)
  }

  //not sure is this right way to do this
  def returnOut[T <: Data](in: T, maxDepth: Int, depth: UInt, resetData: T, en: Bool = true.B, adjust: Boolean = true): (T, Vec[T]) = {
    require(maxDepth > 0)
    requireIsHardware(in)
    assert(depth <= maxDepth.U)

    val activeRegs = Wire(Vec(maxDepth, Bool()))
    dontTouch(activeRegs)
    activeRegs.suggestName("activeRegs")
    activeRegs.zipWithIndex.map { case (active, index) =>  if (adjust == true) active := (index.U <= depth - 1.U).asBool else active := true.B }
    //activeRegs.zipWithIndex.map { case (active, index) => active := Mux(depth === 0.U, false.B, (index.U <= depth - 1.U).asBool) }

    val out = Wire(Vec(maxDepth, in.cloneType))
    applyReccursive(in, maxDepth, activeRegs, out, resetData, en, adjust)
    (out(depth - 1.U), out)
  }
}

// this module should instatiate AdjustableShiftRegister and wrap it with AXI Stream interface
// it is assumed that signal lastIn triggers flushing
class AdjustableShiftRegisterStream[T <: Data](val proto: T, val maxDepth: Int, val parallelOut: Boolean = false, val sendCnt: Boolean = false, val enInitStore: Boolean = true, adjust: Boolean = true) extends Module { //Stream
  require(maxDepth > 1, s"Depth must be > 1, got $maxDepth")
  requireIsChiselType(proto)

  val io = IO(new Bundle {
    val depth       = Input(UInt(log2Ceil(maxDepth + 1).W))
    val in          = Flipped(Decoupled(proto.cloneType))
    val lastIn      = Input(Bool())

    val out         = Decoupled(proto.cloneType)
    val lastOut     = Output(Bool())
    val parallelOut = Output(Vec(maxDepth, proto))
    val cnt         = if (sendCnt) Some(Output(UInt(log2Ceil(maxDepth + 1).W))) else None

    val regFull     = Output(Bool())
    val regEmpty    = Output(Bool())
  })

  val initialInDone = RegInit(false.B)
  val last = RegInit(false.B)

  dontTouch(initialInDone)
  initialInDone.suggestName("InitialInDone")
  val resetData = 0.U.asTypeOf(io.in.bits)
  val en = io.in.fire() || (last && io.out.ready)
  // or drop data if io.out.ready is inactive, in that case en is:
  //val en = io.in.fire() || last

  val (adjShiftReg, adjShiftRegOut) = AdjustableShiftRegister.returnOut(io.in.bits, maxDepth, io.depth, resetData, en, adjust)
  val cntIn  = RegInit(0.U(log2Ceil(maxDepth + 1).W))

  when (io.lastIn && io.in.fire()) {
    last := true.B
  }

  when (io.in.fire()) {
    cntIn := cntIn + 1.U
  }
  // if depth is one
  when (io.depth > 1.U) {
    when (cntIn === io.depth - 1.U && io.in.fire()) {
      initialInDone := true.B
    }
  }
  .otherwise {
    when (io.in.fire() && io.depth === 1.U) {
      initialInDone := true.B
    }
  }

  val fireLastIn = io.lastIn && io.in.fire()
  val lastOut = AdjustableShiftRegister(fireLastIn, maxDepth, io.depth, resetData = false.B, en = io.out.fire())

  when (lastOut && io.out.fire()) {
    initialInDone := false.B
    last := false.B
    cntIn := 0.U
  }

  io.regEmpty  := cntIn === 0.U && ~initialInDone
  io.regFull   := initialInDone && ~last
  //io.in.ready    := ~initialInDone || io.out.ready && ~last // or without ~last

  ///////////////// CHANGED /////////////////////////
  if (enInitStore) {
    io.in.ready    :=  Mux(io.depth === 0.U, io.out.ready, ~initialInDone || io.out.ready && ~last)
  }
  else {
    io.in.ready    :=  Mux(io.depth === 0.U, io.out.ready, io.out.ready && ~last)
  }

  dontTouch(io.parallelOut)
  io.out.bits    := Mux(io.depth === 0.U, io.in.bits, adjShiftReg)
  io.parallelOut := adjShiftRegOut // parallel output is not important
  io.lastOut     := Mux(io.depth === 0.U, io.lastIn && io.in.fire(), lastOut)
  io.out.valid   := Mux(io.depth === 0.U, io.in.valid, initialInDone && io.in.valid || (last && en))
  if (sendCnt)
    io.cnt.get := cntIn
}
