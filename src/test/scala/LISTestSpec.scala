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

import uart._
import java.io._

// Initial tests, checking only whether streaming is ok or not and check splitter and mux configurations

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
  
//   // This signals should be always ready!
//   poke(dut.laInside.ready, true.B)
//   poke(dut.laOutside.ready, true.B)
//   
  memWriteWord(params.bistAddress.base + 2*beatBytes, 0x1) // configure counter to be active
  memWriteWord(params.bistAddress.base + 3*beatBytes, 0x1) // configure counter to be up counter
  
  // configure muxes so that lisFIFO is propagated to output
  memWriteWord(params.lisFIFOMuxAddress0.base,  0x0) // output0
  memWriteWord(params.lisFIFOAddress.base + 4*beatBytes, params.lisFIFOParams.LISsize/2)
  memWriteWord(params.lisInputMuxAddress0.base + beatBytes,  0x0)
  memWriteWord(params.lisFixedMuxAddress0.base + beatBytes,  0x0)
  
 // configure fifo -> outMux
  memWriteWord(params.outMuxAddress.base, 0x0)
 // configure bist_split -> out_mux
  memWriteWord(params.outMuxAddress.base + 2*beatBytes, 0x3)
  
  poke(dut.outStream.ready, true.B)

  // enable bist
  memWriteWord(params.bistAddress.base, 0x1)
  
  // here just check LSB or MSB byte!
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
  
//   // This signals should be always ready!
//   poke(dut.laInside.ready, true.B)
//   poke(dut.laOutside.ready, true.B)
//   
  memWriteWord(params.bistAddress.base + 2*beatBytes, 0x1) // configure counter to be active
  memWriteWord(params.bistAddress.base + 3*beatBytes, 0x1) // configure counter to be up counter
  
  // configure muxes so that lis fixed is propagated to output
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
  
  // here just check LSB or MSB byte only!
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
  
  // here just check LSB or MSB byte!
  step(1024)
}

class LISTestSpec extends FlatSpec with Matchers {
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
  

  it should "test lisFIFO connected to bist module" in {
    val lazyDut = LazyModule(new LISTest(params) with LISTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/LISTest/BIST_LISFIFO_POUT", "--top-name", "LISTest"), () => lazyDut.module) {
      c => new BIST_LISFIFO_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }
    
  it should "test lisFixed connected to bist module" in {
    val lazyDut = LazyModule(new LISTest(params) with LISTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/LISTest/BIST_LISFixed_POUT", "--top-name", "LISTest"), () => lazyDut.module) {
      c => new BIST_LISInput_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }
  
 it should "test lisInput connected to bist modulet" in {
   val lazyDut = LazyModule(new LISTest(params) with LISTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/LISTest/BIST_LISInput_POUT", "--top-name", "LISTest"), () => lazyDut.module) {
      c => new BIST_LISFixed_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }
}
