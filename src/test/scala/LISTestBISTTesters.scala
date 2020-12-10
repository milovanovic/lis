package lis

import chisel3._
import chisel3.experimental._
import chisel3.iotesters.{Driver, PeekPokeTester}

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

import org.scalatest.{FlatSpec, Matchers}
import scala.util.{Random}

import uart._
import java.io._


//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// BIST -> LISFIFO -> parallel_out
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class BIST_LISFIFO_POUT_SpectrometerTester
(
  dut: LISTest with LISTestPins,
  params: LISTestFixedParameters,
  silentFail: Boolean = false,
  beatBytes: Int = 4
) extends PeekPokeTester(dut.module) with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val indexCell = params.lisFIFOParams.LISsize/2
  
//   // This signals should be always ready!
//   poke(dut.laInside.ready, true.B)
//   poke(dut.laOutside.ready, true.B)
//   
  memWriteWord(params.bistAddress.base + 2*beatBytes, 0x1) // configure counter to be active
  memWriteWord(params.bistAddress.base + 3*beatBytes, 0x1) // configure counter to be up counter
  
  // configure muxes so that lisFIFO is propagated to output
  memWriteWord(params.lisFIFOMuxAddress0.base,  0x0) // output0
  memWriteWord(params.lisFIFOAddress.base + 4*beatBytes, indexCell)
  memWriteWord(params.lisInputMuxAddress0.base + beatBytes,  0x0)
  memWriteWord(params.lisFixedMuxAddress0.base + beatBytes,  0x0)
  
 // configure fifo -> outMux
  memWriteWord(params.outMuxAddress.base, 0x0)
 // configure bist_split -> out_mux
  memWriteWord(params.outMuxAddress.base + 2*beatBytes, 0x3)
  
  poke(dut.outStream.ready, true.B)
  // enable bist
  memWriteWord(params.bistAddress.base, 0x1)
  
  val counterInValues = Seq.range(0,24) // count from 0 to 24 
  var outSeq = Seq[Int]()
  var fifoOut: Short = 0
  var inputSelected: Short = 0
  var fifoOutSeq = Seq[Int]()
  var inputSelectedSeq = Seq[Int]()
  var peekedValOut : BigInt = 0
  val sorterLength = 24
  
  while (outSeq.length < sorterLength*4) {
    if (peek(dut.outStream.valid) == 1 && peek(dut.outStream.ready) == 1) {
      peekedValOut = peek(dut.outStream.bits.data)
      outSeq = outSeq :+ peekedValOut.toInt
    }
    step(1)
  }
  
  for (i <- 0 until outSeq.length by 4) {
    fifoOut = java.lang.Integer.parseInt(LISTesterUtils.asNdigitBinary(outSeq(i + 3), 8) ++ LISTesterUtils.asNdigitBinary(outSeq(i + 2), 8), 2).toShort
    inputSelected = java.lang.Long.parseLong(LISTesterUtils.asNdigitBinary(outSeq(i + 1), 8)   ++ LISTesterUtils.asNdigitBinary(outSeq(i), 8), 2).toShort
    fifoOutSeq = fifoOutSeq :+ fifoOut.toInt
    inputSelectedSeq = inputSelectedSeq :+ inputSelected.toInt
  }
  
  //println(counterInValues.length.toString)

  LISTesterUtils.checkError(fifoOutSeq, counterInValues, 0)
  LISTesterUtils.checkError(inputSelectedSeq, Seq.range(indexCell, indexCell + sorterLength), 0)
  
  // write output in file
  val file = new File("./test_run_dir/LISTest/BIST_LISFIFO_POUT/data.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until fifoOutSeq.length ) {
    w.write(f"${fifoOutSeq(i)}%04x" + f"${inputSelectedSeq(i)}%04x" + "\n")
  }
  w.close
  
  step(1024)
}

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// BIST -> LISInput -> parallel_out
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class BIST_LISInput_POUT_SpectrometerTester
(
  dut: LISTest with LISTestPins,
  params: LISTestFixedParameters,
  silentFail: Boolean = false,
  beatBytes: Int = 4
) extends PeekPokeTester(dut.module) with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val indexCell = params.lisInputParams.LISsize/2

//   // This signals should be always ready!
//   poke(dut.laInside.ready, true.B)
//   poke(dut.laOutside.ready, true.B)
//   
  memWriteWord(params.bistAddress.base + 2*beatBytes, 0x1) // configure counter to be active
  memWriteWord(params.bistAddress.base + 3*beatBytes, 0x1) // configure counter to be up counter
  
  memWriteWord(params.lisInputMuxAddress0.base,  0x0) // output0
  memWriteWord(params.lisInputAddress.base + 4*beatBytes, params.lisFIFOParams.LISsize/2)
  memWriteWord(params.lisFIFOMuxAddress0.base + beatBytes,  0x0)
  memWriteWord(params.lisFixedMuxAddress0.base + beatBytes,  0x0)
  
 // configure fixed -> outMux
  memWriteWord(params.outMuxAddress.base, 0x1)
 // configure bist_split -> out_mux
  memWriteWord(params.outMuxAddress.base + 2*beatBytes, 0x3)
  
  poke(dut.outStream.ready, true.B)

  // enable bist
  memWriteWord(params.bistAddress.base, 0x1)
  
  val counterInValues = Seq.range(0,24) // count from 0 to 128 
  var outSeq = Seq[Int]()
  var inputOut: Short = 0
  var inputSelected: Short = 0
  var inputOutSeq = Seq[Int]()
  var inputSelectedSeq = Seq[Int]()
  var peekedValOut : BigInt = 0
  val sorterLength = 24
  
  while (outSeq.length < sorterLength*4) {
    if (peek(dut.outStream.valid) == 1 && peek(dut.outStream.ready) == 1) {
      peekedValOut = peek(dut.outStream.bits.data)
      outSeq = outSeq :+ peekedValOut.toInt
    }
    step(1)
  }
  
  for (i <- 0 until outSeq.length by 4) {
    inputOut = java.lang.Integer.parseInt(LISTesterUtils.asNdigitBinary(outSeq(i + 3), 8) ++ LISTesterUtils.asNdigitBinary(outSeq(i + 2), 8), 2).toShort
    inputSelected = java.lang.Long.parseLong(LISTesterUtils.asNdigitBinary(outSeq(i + 1), 8)   ++ LISTesterUtils.asNdigitBinary(outSeq(i), 8), 2).toShort
    inputOutSeq = inputOutSeq :+ inputOut.toInt
    inputSelectedSeq = inputSelectedSeq :+ inputSelected.toInt
  }
  
  LISTesterUtils.checkError(inputOutSeq, counterInValues, 0)
  LISTesterUtils.checkError(inputSelectedSeq, Seq.range(indexCell, indexCell + sorterLength), 0)
   
  // write output in file
  val file = new File("./test_run_dir/LISTest/BIST_LISInput_POUT/data.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until inputOutSeq.length ) {
    w.write(f"${inputOutSeq(i)}%04x" + f"${inputSelectedSeq(i)}%04x" + "\n")
  }
  w.close
  
  step(1024)
}

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// BIST -> LISFixed -> parallel_out
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class BIST_LISFixed_POUT_SpectrometerTester
(
  dut: LISTest with LISTestPins,
  params: LISTestFixedParameters,
  silentFail: Boolean = false,
  beatBytes: Int = 4
) extends PeekPokeTester(dut.module) with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val discardPos = params.lisFixedParams.discardPos.get
  val indexCell = params.lisInputParams.LISsize/2

//   // This signals should be always ready!
//   poke(dut.laInside.ready, true.B)
//   poke(dut.laOutside.ready, true.B)
//   
  memWriteWord(params.bistAddress.base + 2*beatBytes, 0x1) // configure counter to be active
  memWriteWord(params.bistAddress.base + 3*beatBytes, 0x1) // configure counter to be up counter
  
  // configure muxes so that bist splitter is propagated to output
  memWriteWord(params.lisFixedMuxAddress0.base,  0x0) // output0
  memWriteWord(params.lisFixedAddress.base + 4*beatBytes, params.lisFIFOParams.LISsize/2)
  memWriteWord(params.lisFIFOMuxAddress0.base + beatBytes,  0x0)
  memWriteWord(params.lisInputMuxAddress0.base + beatBytes,  0x0)
  
 // configure fixed -> outMux
  memWriteWord(params.outMuxAddress.base, 0x2)
 // configure bist_split -> out_mux
  memWriteWord(params.outMuxAddress.base + 2*beatBytes, 0x3)
  poke(dut.outStream.ready, true.B)
  // enable bist
  memWriteWord(params.bistAddress.base, 0x1)
  
  val counterInValues = Seq.range(0,24) // count from 0 to 24 
  var outSeq = Seq[Int]()
  var fixedOut: Short = 0
  var inputSelected: Short = 0
  var fixedOutSeq = Seq[Int]()
  var inputSelectedSeq = Seq[Int]()
  var peekedValOut : BigInt = 0
  val sorterLength = 24
  
  // peek input data, collect it an array, compare it to output

  while (outSeq.length < sorterLength*4) {
    if (peek(dut.outStream.valid) == 1 && peek(dut.outStream.ready) == 1) {
      peekedValOut = peek(dut.outStream.bits.data)
      outSeq = outSeq :+ peekedValOut.toInt
    }
    step(1)
  }
  
  for (i <- 0 until outSeq.length by 4) {
    fixedOut = java.lang.Integer.parseInt(LISTesterUtils.asNdigitBinary(outSeq(i + 3), 8) ++ LISTesterUtils.asNdigitBinary(outSeq(i + 2), 8), 2).toShort
    inputSelected = java.lang.Long.parseLong(LISTesterUtils.asNdigitBinary(outSeq(i + 1), 8)   ++ LISTesterUtils.asNdigitBinary(outSeq(i), 8), 2).toShort
    fixedOutSeq = fixedOutSeq :+ fixedOut.toInt
    inputSelectedSeq = inputSelectedSeq :+ inputSelected.toInt
  }
   
  LISTesterUtils.checkError(fixedOutSeq, Seq.range(discardPos, discardPos + sorterLength), 0)
  LISTesterUtils.checkError(inputSelectedSeq, Seq.range(indexCell, indexCell + sorterLength), 0)
  
  // write output in file
  val file = new File("./test_run_dir/LISTest/BIST_LISFixed_POUT/data.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until fixedOutSeq.length) {
    w.write(f"${fixedOutSeq(i)}%04x" + f"${inputSelectedSeq(i)}%04x" + "\n")
  }
  w.close
  
  // here just check LSB or MSB byte!
  step(1024)
}
