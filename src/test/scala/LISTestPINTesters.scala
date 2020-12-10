package lis

import chisel3._
import chisel3.util._
import chisel3.experimental._
import chisel3.iotesters.{Driver, PeekPokeTester}

import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

import org.scalatest.{FlatSpec, Matchers}
import scala.util.{Random, Sorting}

import uart._
import java.io._

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PIN -> LISFIFO -> parallel_out
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PIN_LISFIFO_POUT_SpectrometerTester
(
  dut: LISTest with LISTestPins,
  params: LISTestFixedParameters,
  silentFail: Boolean = false,
  beatBytes: Int = 4
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.inStream)

  val indexCell = params.lisFIFOParams.LISsize/2
  
//   // This signals should be always ready!
//   poke(dut.laInside.ready, true.B)
//   poke(dut.laOutside.ready, true.B)
  
   // Add this to get same test case every time
  Random.setSeed(11110L)
  val inData = Seq.fill(params.lisFIFOParams.LISsize)((Random.nextInt((1<<(params.lisFIFOParams.proto.getWidth-1))*2) - (1<<(params.lisFIFOParams.proto.getWidth-1))))
  
  var expOut = if (params.lisFIFOParams.sortDir == true) inData.sorted.toArray else inData.sorted.toArray.reverse
  // split 32 bit data to 4 bytes 
  var dataByte = Seq[Int]()
  for (i <- inData) {
    // LSB byte
    dataByte = dataByte :+ ((i)        & 0xFF)
    dataByte = dataByte :+ ((i >>> 8)  & 0xFF)
    // MSB byte
    dataByte = dataByte :+ 0
    dataByte = dataByte :+ 0
  }
  
  val filein = new File("./test_run_dir/LISTest/PIN_LISFIFO_POUT/input.txt")
  val win = new BufferedWriter(new FileWriter(filein))
  
  for (i <- 0 until dataByte.length ) {
    win.write(f"${dataByte(i)}%02x" + "\n")
  }
  win.close
  // configure muxes so that lisFIFO is propagated to output
  memWriteWord(params.lisFIFOMuxAddress0.base,  0x1) // output0
  memWriteWord(params.lisFIFOAddress.base + 4*beatBytes, indexCell)
  memWriteWord(params.lisInputMuxAddress0.base + beatBytes,  0x1)
  memWriteWord(params.lisFixedMuxAddress0.base + beatBytes,  0x1)
  
 // configure fifo -> outMux
  memWriteWord(params.outMuxAddress.base, 0x0)
  memWriteWord(params.outMuxAddress.base + 2*beatBytes, 0x4)

  poke(dut.outStream.ready, true.B)
  
  // send two sets of data
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  
  var outSeq = Seq[Int]()
  var fifoOut: Short = 0
  var inputSelected: Short = 0
  var fifoOutSeq = Seq[Int]()
  var inputSelectedSeq = Seq[Int]()
  var peekedValOut : BigInt = 0
  val sorterLength = params.lisFIFOParams.LISsize
  
  // peek input data, collect it an array, compare it to output

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
  
  LISTesterUtils.checkError(fifoOutSeq, inData, 0)
  LISTesterUtils.checkError(inputSelectedSeq, Seq.fill(sorterLength)(expOut(indexCell)), 0)
  
  // write output in file
  val file = new File("./test_run_dir/LISTest/PIN_LISFIFO_POUT/output.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until fifoOutSeq.length) {
    w.write(f"${fifoOutSeq(i).toShort}%04x" + f"${inputSelectedSeq(i).toShort}%04x" + "\n")
    //w.write(f"${fifoOutSeq(i)}%04x" + f"${inputSelectedSeq(i)}%04x" + "\n")
  }
  w.close
  
  step(1024)
}

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PIN -> LISInput -> parallel_out
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PIN_LISInput_POUT_SpectrometerTester
(
  dut: LISTest with LISTestPins,
  params: LISTestFixedParameters,
  silentFail: Boolean = false,
  beatBytes: Int = 4
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.inStream)
  val indexCell = params.lisInputParams.LISsize/2
    

//   // This signals should be always ready!
//   poke(dut.laInside.ready, true.B)
//   poke(dut.laOutside.ready, true.B)
 
 // Add this to get same test case every time
  Random.setSeed(11110L)
 // generate random data but with seed don't forget that!
  val inData = Seq.fill(params.lisInputParams.LISsize)((Random.nextInt((1<<(params.lisInputParams.proto.getWidth-1))*2) - (1<<(params.lisInputParams.proto.getWidth-1))))
    
  var expOut = if (params.lisFIFOParams.sortDir == true) inData.sorted.toArray else inData.sorted.toArray.reverse
  
  // split 32 bit data to 4 bytes 
  var dataByte = Seq[Int]()
  for (i <- inData) {
    // LSB byte
    dataByte = dataByte :+ ((i)        & 0xFF)
    dataByte = dataByte :+ ((i >>> 8)  & 0xFF)
    // MSB byte
    dataByte = dataByte :+ 0
    dataByte = dataByte :+ 0
  }
  
  val filein = new File("./test_run_dir/LISTest/PIN_LISInput_POUT/input.txt")
  val win = new BufferedWriter(new FileWriter(filein))
  
  for (i <- 0 until dataByte.length ) {
    win.write(f"${dataByte(i)}%02x" + "\n")
  }
  win.close
  
  memWriteWord(params.lisInputMuxAddress0.base,  0x1) // output0
  memWriteWord(params.lisInputAddress.base + 4*beatBytes, params.lisFIFOParams.LISsize/2)
  memWriteWord(params.lisFIFOMuxAddress0.base + beatBytes,  0x1)
  memWriteWord(params.lisFixedMuxAddress0.base + beatBytes,  0x1)
  
 // configure input -> outMux
  memWriteWord(params.outMuxAddress.base, 0x1)
  memWriteWord(params.outMuxAddress.base + 2*beatBytes, 0x4)

  poke(dut.outStream.ready, true.B)

  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  
  var outSeq = Seq[Int]()
  var inputOut: Short = 0
  var inputSelected: Short = 0
  var inputOutSeq = Seq[Int]()
  var inputSelectedSeq = Seq[Int]()
  var peekedValOut : BigInt = 0
  val sorterLength = params.lisFIFOParams.LISsize
  var numStep = sorterLength
  var toCompareInputSelected = Seq[Int]()
  var toCompareInputOut = Seq[Int]()
  var cntStep = 0
  // default discarded cell is at position 0
  while (cntStep < numStep) {
    toCompareInputOut = toCompareInputOut :+ expOut(0)
    toCompareInputSelected = toCompareInputSelected :+ expOut(indexCell)
    expOut(0)  = inData(cntStep)
    expOut =  if (params.lisInputParams.sortDir) expOut.sorted else expOut.sorted.reverse 
    cntStep += 1 
  }
  toCompareInputSelected.map(c => println(c.toString))
  
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
  
  //inputOutSeq.map(c => println(c.toString))
  LISTesterUtils.checkError(inputOutSeq, toCompareInputOut, 0)
  
  //inputSelectedSeq.map(c => println(c.toString))
  LISTesterUtils.checkError(inputSelectedSeq, toCompareInputSelected, 0)
   
  // write output in file
  val file = new File("./test_run_dir/LISTest/PIN_LISInput_POUT/output.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until inputOutSeq.length) {
    w.write(f"${inputOutSeq(i).toShort}%04x" + f"${inputSelectedSeq(i).toShort}%04x" + "\n")
    //w.write(f"${inputOutSeq(i)}%04x" + f"${inputSelectedSeq(i)}%04x" + "\n")
  }
  w.close
  
  step(1024)
}

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PIN -> LISFixed -> parallel_out
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PIN_LISFixed_POUT_SpectrometerTester
(
  dut: LISTest with LISTestPins,
  params: LISTestFixedParameters,
  silentFail: Boolean = false,
  beatBytes: Int = 4
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.inStream)

  val discardPos = params.lisFixedParams.discardPos.get
  val indexCell = params.lisInputParams.LISsize/2
  

//   // This signals should be always ready!
//   poke(dut.laInside.ready, true.B)
//   poke(dut.laOutside.ready, true.B)
//  
   // Add this to get same test case every time
  Random.setSeed(11110L)
  val inData = Seq.fill(params.lisFixedParams.LISsize)((Random.nextInt((1<<(params.lisFixedParams.proto.getWidth-1))*2) - (1<<(params.lisFixedParams.proto.getWidth-1))))
  
  var expOut = if (params.lisFIFOParams.sortDir == true) inData.sorted.toArray else inData.sorted.toArray.reverse
    // split 32 bit data to 4 bytes 
  var dataByte = Seq[Int]()
  
  for (i <- inData) {
    // LSB byte
    dataByte = dataByte :+ ((i)        & 0xFF)
    dataByte = dataByte :+ ((i >>> 8)  & 0xFF)
    // MSB byte
    dataByte = dataByte :+ 0
    dataByte = dataByte :+ 0
   }
  
  // Write input data to text file
  val filein = new File("./test_run_dir/LISTest/PIN_LISFixed_POUT/input.txt")
  val win = new BufferedWriter(new FileWriter(filein))
  
  for (i <- 0 until dataByte.length ) {
    win.write(f"${dataByte(i)}%02x" + "\n")
  }
  win.close
  
  memWriteWord(params.lisFixedMuxAddress0.base,  0x1) // output0
  memWriteWord(params.lisFixedAddress.base + 4*beatBytes, params.lisFIFOParams.LISsize/2)
  memWriteWord(params.lisFIFOMuxAddress0.base + beatBytes,  0x1)
  memWriteWord(params.lisInputMuxAddress0.base + beatBytes,  0x1)
  
 // configure fixed -> outMux
  memWriteWord(params.outMuxAddress.base, 0x2)

  poke(dut.outStream.ready, true.B)

  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  memWriteWord(params.outMuxAddress.base + 2*beatBytes, 0x4)
  
  var outSeq = Seq[Int]()
  var fixedOut: Short = 0
  var inputSelected: Short = 0
  var fixedOutSeq = Seq[Int]()
  var inputSelectedSeq = Seq[Int]()
  var peekedValOut : BigInt = 0
  val sorterLength = params.lisFIFOParams.LISsize
  var numStep = sorterLength
  var toCompareInputSelected = Seq[Int]()
  var toCompareFixedOut = Seq[Int]()
  var cntStep = 0
  // default discarded cell is at position 0
  while (cntStep < numStep) {
    toCompareFixedOut = toCompareFixedOut :+ expOut(discardPos)
    toCompareInputSelected = toCompareInputSelected :+ expOut(indexCell)
    expOut(discardPos)  = inData(cntStep)
    expOut =  if (params.lisFixedParams.sortDir) expOut.sorted else expOut.sorted.reverse 
    cntStep += 1 
  }
  
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
   
  LISTesterUtils.checkError(fixedOutSeq, toCompareFixedOut, 0)
  LISTesterUtils.checkError(inputSelectedSeq, toCompareInputSelected, 0)
  
  // write output in file
  val file = new File("./test_run_dir/LISTest/PIN_LISFixed_POUT/output.txt")
  
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until fixedOutSeq.length) {
    w.write(f"${fixedOutSeq(i).toShort}%04x" + f"${inputSelectedSeq(i).toShort}%04x" + "\n")
    //w.write(f"${fixedOutSeq(i)}%04x" + f"${inputSelectedSeq(i)}%04x" + "\n")
  }
  w.close
  
  step(1024)
}
