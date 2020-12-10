// SPDX-License-Identifier: Apache-2.0

package lis

import chisel3._
import chisel3.experimental._
import chisel3.iotesters.{Driver, PeekPokeTester}

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

import org.scalatest.{FlatSpec, Matchers}

import uart._
import java.io._


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

  it should "test lisFIFO connected to bist module and parallel output (pout)" in {
    val lazyDut = LazyModule(new LISTest(params) with LISTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/LISTest/BIST_LISFIFO_POUT", "--top-name", "LISTest"), () => lazyDut.module) {
      c => new BIST_LISFIFO_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }
    
  it should "test lisInput connected to bist module and parallel output (pout)" in {
    val lazyDut = LazyModule(new LISTest(params) with LISTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/LISTest/BIST_LISInput_POUT", "--top-name", "LISTest"), () => lazyDut.module) {
      c => new BIST_LISInput_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }
  
 it should "test lisFixed connected to bist module and parallel output (pout)" in {
   val lazyDut = LazyModule(new LISTest(params) with LISTestPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/LISTest/BIST_LISFixed_POUT", "--top-name", "LISTest"), () => lazyDut.module) {
      c => new BIST_LISFixed_POUT_SpectrometerTester(lazyDut, params, true)
    } should be (true)
  }
  
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
