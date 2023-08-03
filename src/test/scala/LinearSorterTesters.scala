package lis

import chisel3._

import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import chiseltest.iotesters.PeekPokeTester
import dsptools.misc.PeekPokeDspExtensions

import scala.math._
import dsptools.numbers._
import fixedpoint.FixedPoint

case class LISTesterBase[T <: Data](override val dut: LinearSorter[T])
    extends PeekPokeTester(dut)
    with PeekPokeDspExtensions {
  def compare_data(expected: Double, received: Double, tol: Double) {
    assert(abs(expected - received) <= tol, "Mismatch!!!")
  }
  def expect_sorted_seq[T <: Data](sig_vec: Vec[T], exp_seq: Seq[Double], tol: Double): Unit = {
    sig_vec(0).cloneType match {
      case uInt: UInt =>
        exp_seq.zipWithIndex.foreach {
          case (expected, index) => expect(sig_vec(index), expected)
        }
      case sInt: SInt =>
        exp_seq.zipWithIndex.foreach {
          case (expected, index) => expect(sig_vec(index), expected)
        }
      case _ =>
        exp_seq.zipWithIndex.foreach {
          case (expected, index) => compare_data(expected, peek(sig_vec(index)), tol)
        }
    }
  }
}

class LinearSorterTesterTestLastIn[T <: Data](dut: LinearSorter[T], in: Seq[Double], tol: Double)
    extends LISTesterBase(dut) {

  val cyclesWait = dut.params.LISsize
  val out = ArrayBuffer[Double]()
  val input1 = in.iterator
  var expOut = if (dut.params.sortDir) in.sorted.toArray else in.sorted.toArray.reverse
  var tmpData:   Double = 0.0
  var tmpDataIn: Double = 0.0
  var discardPos = 0
  var cntValidOut = 0

  //updatableDspVerbose.withValue(false) {
  poke(dut.io.out.ready, 0)
  poke(dut.io.in.valid, 0)
  step(2)

  if (dut.params.LISsubType == "LIS_input") {
    discardPos = dut.params.LISsize / 2
    poke(dut.io.discardPos.get, discardPos)
  } else if (dut.params.LISsubType == "LIS_fixed") {
    discardPos = dut.params.discardPos.get
  }
  step(2)
  poke(dut.io.in.valid, 1)
  poke(dut.io.out.ready, 1)

  while (out.length < in.size) {
    if (input1.hasNext && peek(dut.io.in.ready) == 1) {
      poke(dut.io.in.bits, input1.next())
      if (!input1.hasNext) {
        poke(dut.io.lastIn, 1)
      }
    } else {
      poke(dut.io.in.valid, 0)
    }
    if (peek(dut.io.out.valid) == 1) {
      tmpData = peek(dut.io.out.bits)
      if (dut.params.LISsubType == "LIS_FIFO") {
        /*dut.params.proto match {
            case dspR: DspReal => realTolDecPts.withValue(tol) { expect(dut.io.out.bits, in(out.length)) }
            case _ => fixTolLSBs.withValue(tol) { expect(dut.io.out.bits, in(out.length)) }
          }*/
        // Resolved!
        dut.params.proto match {
          case uInt: UInt => expect(dut.io.out.bits, in(out.length))
          case sInt: SInt => expect(dut.io.out.bits, in(out.length))
          case _ => compare_data(in(out.length), peek(dut.io.out.bits), tol)
        }
      }

      if (cntValidOut == in.size - 1) {
        expect(dut.io.lastOut, 1)
      }
      cntValidOut += 1
    } else {
      val peekedOutput = peek(dut.io.sortedData(0))
      // Resolved!
      dut.params.proto match {
        case uInt: UInt => expect(dut.io.out.bits, peekedOutput)
        case sInt: SInt => expect(dut.io.out.bits, peekedOutput)
        case _ => compare_data(in(out.length), peekedOutput, tol)
      }
      /*dut.params.proto match {
            case dspR: DspReal => realTolDecPts.withValue(tol) { expect(dut.io.out.bits, peekedOutput) }
            case _ => fixTolLSBs.withValue(tol) { expect(dut.io.out.bits, peekedOutput) }
          }*/
    }
    out += tmpData
  }
  step(1)
}

class LinearSorterTester[T <: Data](dut: LinearSorter[T], in: Seq[Double], tol: Double) extends LISTesterBase(dut) {

  val cyclesWait = dut.params.LISsize
  val out = ArrayBuffer[Double]()
  val input1 = in.iterator
  val input2 = in.iterator
  //var expOut = if (dut.params.sortDir == true) in.sorted.reverse.toArray else in.sorted.toArray
  var expOut = if (dut.params.sortDir) in.sorted.toArray else in.sorted.toArray.reverse
  var tmpData:   Double = 0.0
  var tmpDataIn: Double = 0.0
  var discardPos = 0

  //updatableDspVerbose.withValue(false) {
  poke(dut.io.out.ready, 0)
  poke(dut.io.in.valid, 0)
  if (dut.params.flushData) {
    poke(dut.io.flushData.get, 0)
  }
  step(2)

  if (dut.params.LISsubType == "LIS_input") {
    discardPos = dut.params.LISsize / 2
    poke(dut.io.discardPos.get, discardPos)
  } else if (dut.params.LISsubType == "LIS_fixed") {
    discardPos = dut.params.discardPos.get
  }
  step(2)
  poke(dut.io.in.valid, 1)
  poke(dut.io.out.ready, 1)

  while (out.length < in.size) {
    if (input1.hasNext && peek(dut.io.in.ready) == 1) {
      //if (input1.hasNext) {
      poke(dut.io.in.bits, input1.next())
    } else if (input2.hasNext && peek(dut.io.in.ready) == 1 && dut.params.flushData) {
      poke(dut.io.in.bits, input2.next())
    } else if (dut.params.flushData) { // check flush
      poke(dut.io.in.valid, 0)
    }
    //expOut = Sorting.quickSort(expOut :+ peek(dut.io.in.bits))
    if (peek(dut.io.out.valid) == 1) {
      tmpData = peek(dut.io.out.bits)
      if (dut.params.LISsubType == "LIS_FIFO") {
        //println("Expected output is: ")
        //println(expOut.map(_.toString()).mkString(", "))
        dut.params.proto match {
          case uInt: UInt => expect(dut.io.out.bits, in(out.length))
          case sInt: SInt => expect(dut.io.out.bits, in(out.length))
          case _ => compare_data(in(out.length), peek(dut.io.out.bits), tol)
        }
        /*dut.params.proto match {
            case dspR: DspReal => realTolDecPts.withValue(tol) { expect(dut.io.out.bits, in(out.length)) }
            case _ => fixTolLSBs.withValue(tol) { expect(dut.io.out.bits, in(out.length)) }
          }*/
        expect_sorted_seq(dut.io.sortedData, expOut, tol)
        discardPos = expOut.indexWhere(t => t == in(out.length))
        expOut(discardPos) = tmpDataIn
        expOut = if (dut.params.sortDir) expOut.sorted else expOut.sorted.reverse
      } else {
        //println("Expected output is: ")
        //println(expOut.map(_.toString()).mkString(", "))

        dut.params.proto match {
          case uInt: UInt => expect(dut.io.out.bits, expOut(discardPos))
          case sInt: SInt => expect(dut.io.out.bits, expOut(discardPos))
          case _ => compare_data(expOut(discardPos), peek(dut.io.out.bits), tol)
        }
        /*dut.params.proto match {
            case dspR: DspReal => realTolDecPts.withValue(tol) { expect(dut.io.out.bits, expOut(discardPos)) }
            case _ => fixTolLSBs.withValue(tol) { expect(dut.io.out.bits, expOut(discardPos)) }
          }*/
        expect_sorted_seq(dut.io.sortedData, expOut, tol)
        expOut(discardPos) = tmpDataIn
        expOut = if (dut.params.sortDir) expOut.sorted else expOut.sorted.reverse // can be optimized!
      }
      out += tmpData
    }
    tmpDataIn = peek(dut.io.in.bits)
    step(1)
  }
  poke(dut.io.in.valid, 0)
  step(5)
  if (dut.params.flushData) {
    poke(dut.io.flushData.get, 1)
    val outFlush = ArrayBuffer[Double]()
    step(1)
    while (peek(dut.io.out.valid) == 1 & outFlush.length < dut.params.LISsize) {
      tmpData = peek(dut.io.out.bits)
      if (dut.params.LISsubType == "LIS_FIFO") {
        dut.params.proto match {
          case uInt: UInt => expect(dut.io.out.bits, in(outFlush.length))
          case sInt: SInt => expect(dut.io.out.bits, in(outFlush.length))
          case _ => compare_data(in(outFlush.length), peek(dut.io.out.bits), tol)
        }
        /*dut.params.proto match {
            case dspR: DspReal => realTolDecPts.withValue(tol) { expect(dut.io.out.bits, in(outFlush.length)) }
            case _ => fixTolLSBs.withValue(tol) { expect(dut.io.out.bits, in(outFlush.length)) }
          }*/
      } else {
        val peekedOutput = peek(dut.io.sortedData(0))
        dut.params.proto match {
          case uInt: UInt => expect(dut.io.out.bits, peekedOutput)
          case sInt: SInt => expect(dut.io.out.bits, peekedOutput)
          case _ => compare_data(peekedOutput, peek(dut.io.out.bits), tol)
        }
        /*dut.params.proto match {
            case dspR: DspReal => realTolDecPts.withValue(tol) { expect(dut.io.out.bits, peekedOutput) }
            case _ => fixTolLSBs.withValue(tol) { expect(dut.io.out.bits, peekedOutput) }
          }*/
      }
      outFlush += tmpData
      step(1)
    }
  }
  step(dut.params.LISsize * 2)
}

class LinearSorterTesterRunTime[T <: Data](dut: LinearSorter[T], in: Seq[Double], tolLSB: Double)
    extends LISTesterBase(dut) {

  var out = ArrayBuffer[Double]()
  var input = in.iterator
  val sorterSizesSet = (2 to dut.params.LISsize).toList
  val runTimeSorterSizes = Random.shuffle(sorterSizesSet)

  var expOut = if (dut.params.sortDir) in.sorted.toArray else in.sorted.toArray.reverse
  var tmpData:   Double = 0.0
  var tmpDataIn: Double = 0.0
  var discardPos = 0

  //updatableDspVerbose.withValue(false) {
  if (dut.params.LISsubType == "LIS_input") {
    discardPos = 0
    poke(dut.io.discardPos.get, discardPos)
  } else if (dut.params.LISsubType == "LIS_fixed") {
    discardPos = 0
  }
  step(10)

  for ((size, index) <- runTimeSorterSizes.zipWithIndex) {
    out = ArrayBuffer[Double]()
    expOut =
      if ((dut.params.sortDir && !dut.params.rtcSortDir) || (dut.params.rtcSortDir && index % 2 == 0))
        in.take(size).sorted.toArray
      else in.take(size).sorted.toArray.reverse
    // println(expOut.map(_.toString()).mkString(", "))
    input = in.iterator

    if (dut.params.rtcSortDir) {
      poke(dut.io.sortDir.get, index % 2 == 0) // true, false, true, false ...
    }
    step(1)
    reset(5)
    poke(dut.io.lisSize.get, size)
    poke(dut.io.out.ready, 0)
    poke(dut.io.in.valid, 0)
    step(5)

    poke(dut.io.out.ready, 1)
    poke(dut.io.in.valid, 1)

    while (out.length < size) {
      if (input.hasNext && peek(dut.io.in.ready) == 1) {
        poke(dut.io.in.bits, input.next())
      }
      if (peek(dut.io.out.valid) == 1) {
        tmpData = peek(dut.io.out.bits)
        if (dut.params.LISsubType == "LIS_FIFO") {
          //println("Expected output is: ")
          //println(expOut.map(_.toString()).mkString(", "))
          dut.params.proto match {
            case uInt: UInt => expect(dut.io.out.bits, in(out.length))
            case sInt: SInt => expect(dut.io.out.bits, in(out.length))
            case _ => compare_data(in(out.length), peek(dut.io.out.bits), tolLSB)
          }
          //fixTolLSBs.withValue(tolLSB) { expect(dut.io.out.bits, in(out.length)) }
          expect_sorted_seq(dut.io.sortedData, expOut, tolLSB)
          discardPos = expOut.indexWhere(t => t == in(out.length))

          expOut(discardPos) = peek(dut.io.in.bits) //tmpDataIn
          expOut =
            if ((dut.params.sortDir && !dut.params.rtcSortDir) || (dut.params.rtcSortDir && index % 2 == 0))
              expOut.sorted
            else expOut.sorted.reverse
        } else {
          // println("Expected output is:")
          // println(expOut.map(_.toString()).mkString(", "))
          dut.params.proto match {
            case uInt: UInt => expect(dut.io.out.bits, expOut(discardPos))
            case sInt: SInt => expect(dut.io.out.bits, expOut(discardPos))
            case _ => compare_data(expOut(discardPos), peek(dut.io.out.bits), tolLSB)
          }
          //fixTolLSBs.withValue(tolLSB) { expect(dut.io.out.bits, expOut(discardPos)) }
          expect_sorted_seq(dut.io.sortedData, expOut, tolLSB)
          expOut(discardPos) = peek(dut.io.in.bits)
          expOut =
            if ((dut.params.sortDir && !dut.params.rtcSortDir) || (dut.params.rtcSortDir && index % 2 == 0))
              expOut.sorted
            else expOut.sorted.reverse
        }
        out += tmpData
      }
      tmpDataIn = peek(dut.io.in.bits)
      step(1)
    }
    poke(dut.io.in.valid, 0)
    step(1)
  }
}
