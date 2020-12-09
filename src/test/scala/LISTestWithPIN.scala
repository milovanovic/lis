package lis

import freechips.rocketchip.interrupts._
import dsptools._
import dsptools.numbers._
import chisel3._
import chisel3.util._
import chisel3.experimental._
import chisel3.iotesters.{Driver, PeekPokeTester}

import dspblocks.{AXI4DspBlock, AXI4StandaloneBlock, TLDspBlock, TLStandaloneBlock}
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system.BaseConfig

import org.scalatest.{FlatSpec, Matchers}
import scala.util.{Random}

import uart._
import java.io._

// Initial tests, checking only whether streaming is ok or not and check splitter and mux configurations

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
//   
  val inData = Seq.fill(params.lisFIFOParams.LISsize)((Random.nextInt((1<<(params.lisFIFOParams.proto.getWidth-1))*2) - (1<<(params.lisFIFOParams.proto.getWidth-1))))

  // split 32 bit data to 4 bytes 
  var dataByte = Seq[Int]()
  for (i <- inData) {
    // LSB byte
    dataByte = dataByte :+ 0
    dataByte = dataByte :+ 0
    // MSB byte
    dataByte = dataByte :+ ((i)        & 0xFF)
    dataByte = dataByte :+ ((i >>> 8)  & 0xFF)
  }
  
    
  val filein = new File("./test_run_dir/LISTest/PIN_LISFIFO_POUT/input.txt")
  val win = new BufferedWriter(new FileWriter(filein))
  
  for (i <- 0 until dataByte.length ) {
    win.write(f"${dataByte(i)}%02x" + "\n")
  }
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
    fifoOut = java.lang.Integer.parseInt(LISTesterUtils.asNdigitBinary(outSeq(i + 3), 8) ++ LISTesterUtils.asNdigitBinary(outSeq(i + 2), 8), 2).toShort
    inputSelected = java.lang.Long.parseLong(LISTesterUtils.asNdigitBinary(outSeq(i + 1), 8)   ++ LISTesterUtils.asNdigitBinary(outSeq(i), 8), 2).toShort
    fifoOutSeq = fifoOutSeq :+ fifoOut.toInt
    inputSelectedSeq = inputSelectedSeq :+ inputSelected.toInt
  }
  
  //println(counterInValues.length.toString)

  //LISTesterUtils.checkError(fifoOutSeq, counterInValues)
  //LISTesterUtils.checkError(inputSelectedSeq, Seq.range(indexCell, indexCell + sorterLength))
  
  // write output in file
  val file = new File("./test_run_dir/LISTest/PIN_LISFIFO_POUT/output.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until fifoOutSeq.length ) {
    w.write(f"${fifoOutSeq(i)}%04x" + f"${inputSelectedSeq(i)}%04x" + "\n")
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
 
 // generate random data but with seed don't forget that!
  val inData = Seq.fill(params.lisInputParams.LISsize)((Random.nextInt((1<<(params.lisInputParams.proto.getWidth-1))*2) - (1<<(params.lisInputParams.proto.getWidth-1))))
  
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
  
  
  //LISTesterUtils.checkError(inputOutSeq, counterInValues)
  //LISTesterUtils.checkError(inputSelectedSeq, Seq.range(indexCell, indexCell + sorterLength))
   
  // write output in file
  val file = new File("./test_run_dir/LISTest/PIN_LISInput_POUT/output.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until inputOutSeq.length ) {
    w.write(f"${inputOutSeq(i)}%04x" + f"${inputSelectedSeq(i)}%04x" + "\n")
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

  val outData = params.lisFixedParams.discardPos.get
  val indexCell = params.lisInputParams.LISsize/2

//   // This signals should be always ready!
//   poke(dut.laInside.ready, true.B)
//   poke(dut.laOutside.ready, true.B)
//  

  val inData = Seq.fill(params.lisFixedParams.LISsize)((Random.nextInt((1<<(params.lisFixedParams.proto.getWidth-1))*2) - (1<<(params.lisFixedParams.proto.getWidth-1))))
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
  // val filein = new File("./../top/dv/LISTest/linsorter/pin_lisFixed_pout/input.txt")
  val filein = new File("./test_run_dir/LISTest/PIN_LISFixed_POUT/input.txt")
  val win = new BufferedWriter(new FileWriter(filein))
  
  for (i <- 0 until dataByte.length ) {
    win.write(f"${dataByte(i)}%02x" + "\n")
  }

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
   
  //LISTesterUtils.checkError(fixedOutSeq, Seq.range(outData, outData + sorterLength))
  //LISTesterUtils.checkError(inputSelectedSeq, Seq.range(indexCell, indexCell + sorterLength)) 
  
  // write output in file
  val file = new File("./test_run_dir/LISTest/PIN_LISFixed_POUT/output.txt")
  
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until fixedOutSeq.length) {
    w.write(f"${fixedOutSeq(i)}%04x" + f"${inputSelectedSeq(i)}%04x" + "\n")
  }
  w.close
  
  // here just check LSB or MSB byte!
  step(1024)
}

class LISTestWithPINSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty
  
  val params =
  LISTestFixedParameters (
    lisFIFOParams = LISParams(
      proto = FixedPoint(16.W, 0.BP),
      LISsize = 24,
      LIStype = "LIS_FIFO",
      discardPos = None,
      useSorterEmpty = true,
      useSorterFull = true,
      rtcSize = true,
      sortDir = true
    ),
    lisInputParams = LISParams(
      proto = FixedPoint(16.W, 0.BP),
      LISsize = 24,
      LIStype = "LIS_input",
      discardPos = None,
      useSorterEmpty = true,
      useSorterFull = true,
      rtcSize = true,
      sortDir = true
    ),
    lisFixedParams = LISParams(
      proto = FixedPoint(16.W, 0.BP),
      LISsize = 24,
      LIStype = "LIS_fixed",
      discardPos = Some(8),
      useSorterEmpty = true,
      useSorterFull = true,
      rtcSize = true,
      sortDir = true
    ),
    inSplitAddress       = AddressSet(0x30000000, 0xF),
    lisFIFOAddress       = AddressSet(0x30001000, 0xFF),
    lisFIFOMuxAddress0   = AddressSet(0x30001100, 0xF),
    lisFixedAddress      = AddressSet(0x30002000, 0xFF),
    lisFixedMuxAddress0  = AddressSet(0x30002100, 0xF),
    lisInputAddress      = AddressSet(0x30003000, 0xFF),
    lisInputMuxAddress0  = AddressSet(0x30003100, 0xF),
    bistAddress          = AddressSet(0x30004000, 0xFF),
    bistSplitAddress     = AddressSet(0x30004100, 0xF),
    outMuxAddress        = AddressSet(0x30005000, 0xF),
    outSplitAddress      = AddressSet(0x30005010, 0xF),
    uartParams           = UARTParams(address = 0x30006000, nTxEntries = 256, nRxEntries = 256),
    uRxSplitAddress      = AddressSet(0x30006100, 0xF),
    divisorInit          =  (173).toInt, // baudrate = 115200 for 20MHz
    beatBytes            = 4)
  
  // check parameters inside iotester
  // For now only use verilator backend
  it should "test lisFIFO with parallel input (pin) and parallel output (pout)" in {
    val lazyDut = LazyModule(new LISTest(params) with LISTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/LISTest/PIN_LISFIFO_POUT", "--top-name", "LISTest"), () => lazyDut.module) {
      c => new PIN_LISFIFO_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }
    
  it should "test lisFixed with parallel input (pin) and parallel output (pout)" in {
    val lazyDut = LazyModule(new LISTest(params) with LISTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/LISTest/PIN_LISFixed_POUT", "--top-name", "LISTest"), () => lazyDut.module) {
      c => new PIN_LISFixed_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }
  
 it should "test lisInput with parallel input (pin) and parallel output (pout)" in {
   val lazyDut = LazyModule(new LISTest(params) with LISTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/LISTest/PIN_LISInput_POUT", "--top-name", "LISTest"), () => lazyDut.module) {
      c => new PIN_LISInput_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }
}
