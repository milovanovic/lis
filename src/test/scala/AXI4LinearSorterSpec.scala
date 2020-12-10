package lis

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.iotesters.PeekPokeTester

import dspblocks.{AXI4DspBlock, AXI4StandaloneBlock}
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

import dsptools.DspTester
import dsptools.numbers._

import org.scalatest.{FlatSpec, Matchers}
import scala.util.{Random}
import scala.collection.mutable.ArrayBuffer

class AXI4LISBlockTester
(
  dut: AXI4LISBlock[SInt] with AXI4LISStandaloneBlock,
  params: LISParams[SInt],
  lisAddress: AddressSet,
  beatBytes: BigInt = 4,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.in)
  //val slave  = bindSlave(dut.out)
  
  val inData = Seq.fill(params.LISsize*2)(Random.nextInt(2566))
  poke(dut.out.ready, true.B) // make output always ready to accept data

  master.addTransactions((0 until params.LISsize*2).map(i => AXI4StreamTransaction(data = BigInt(inData(i)))))
  
  step(2000) 
//   slave.addExpects((0 until noSamples).map(i => AXI4StreamTransactionExpect(data = Some((BigInt(outData(i)))))))

}

class AXI4LISBlockSpec extends FlatSpec with Matchers {
 
 val sorterType = "LIS_FIFO"
  
  val params: LISParams[SInt] = LISParams(
    proto = SInt(16.W),
    LISsize = 32,
    LIStype = sorterType,
    discardPos = if (sorterType == "LIS_fixed") Some(8) else None,
    useSorterEmpty = true,
    useSorterFull = true,
    rtcSize = true,
    sortDir = true
  )

  val baseAddress = 0x500
  val beatBytes = 4
  val lisAddress = AddressSet(baseAddress + 0x100, 0xFF)
  implicit val p: Parameters = Parameters.empty
  
  it should "test lis core" ignore {
    val lisModule = LazyModule(new AXI4LISBlock(params, lisAddress, _beatBytes = 4) with AXI4LISStandaloneBlock)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv"), () => lisModule.module) {
      c => new AXI4LISBlockTester(lisModule, params, lisAddress, beatBytes)
    } should be (true)
  }
}
