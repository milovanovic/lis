package lis

import chisel3._
import chisel3.iotesters.PeekPokeTester
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4stream._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.chipsalliance.cde.config.Parameters

import scala.collection.mutable.ArrayBuffer
import scala.util.{Random}
import dspblocks._
import chisel3.util._
import chisel3.experimental._

trait TLLISStandaloneBlock extends TLLISBlock[UInt] {

  def standaloneParams = TLBundleParameters(
    addressBits = 16,
    dataBits = 64,
    sourceBits = 16,
    sinkBits = 16,
    sizeBits = 3,
    echoFields = Seq.empty,
    requestFields = Seq.empty,
    responseFields = Seq.empty,
    hasBCE = false
  )

  val beatBytes = 8

  val clientParams = TLClientParameters(
    name = "BundleBridgeToTL",
    sourceId = IdRange(0, 1),
    nodePath = Seq(),
    requestFifo = false,
    visibility = Seq(AddressSet(0, ~0)),
    supportsProbe = TransferSizes(1, beatBytes),
    supportsArithmetic = TransferSizes(1, beatBytes),
    supportsLogical = TransferSizes(1, beatBytes),
    supportsGet = TransferSizes(1, beatBytes),
    supportsPutFull = TransferSizes(1, beatBytes),
    supportsPutPartial = TransferSizes(1, beatBytes),
    supportsHint = TransferSizes(1, beatBytes)
  )

  val ioMem = mem.map { m =>
    {
      val ioMemNode = BundleBridgeSource(() => TLBundle(standaloneParams))
      m :=
        //  BundleBridgeToTL(TLClientPortParameters(Seq(TLClientParameters("bundleBridgeToTL")))) :=
        BundleBridgeToTL(TLClientPortParameters(Seq(clientParams))) :=
        ioMemNode
      val ioMem = InModuleBody { ioMemNode.makeIO() }
      ioMem
    }
  }

  val ioInNode = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = 2)))
  val ioOutNode = BundleBridgeSink[AXI4StreamBundle]()

  ioOutNode :=
    AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) :=
    streamNode :=
    BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 2)) :=
    ioInNode

  val in = InModuleBody { ioInNode.makeIO() }
  val out = InModuleBody { ioOutNode.makeIO() }
}

class TLLISTester(
  dut:        TLLISBlock[UInt] with TLLISStandaloneBlock,
  csrAddress: AddressSet,
  beatBytes:  Int = 4,
  sorterSize: Int = 6)
    extends PeekPokeTester(dut.module)
    with TLMasterModel {
  //AXI4StreamModel is commented in current version of rocket-dsp-utils, anyhow it is not that much important so we do not need to take care about that one
  //extends PeekPokeTester(dut.module) with AXI4StreamModel with TLMasterModel {
  def memTL: TLBundle = dut.ioMem.get
  val inputSize = if (dut.params.rtcSize == true) sorterSize else dut.params.LISsize

  val in = Seq.fill(inputSize)(Random.nextInt(1 << (dut.params.proto.getWidth)).toInt)

  val mod = dut.module
  val params = dut.params
  val out = ArrayBuffer[BigInt]()
  val input1 = in.iterator

  var tmpData: BigInt = 0

  var cntValidOut = 0
  poke(dut.out.ready, 0)
  poke(dut.in.valid, 0)
  step(2)

  if (dut.params.rtcSize == true) {
    memWriteWord(csrAddress.base + beatBytes, sorterSize, beatBytes)
  }

  if (dut.params.LISsubType == "LIS_input") {
    memWriteWord(csrAddress.base + 3 * beatBytes, dut.params.LISsize / 2, beatBytes)
  }
  step(2)
  poke(dut.in.valid, 1)
  poke(dut.out.ready, 1)

  while (out.length < in.size) {
    if (input1.hasNext && (peek(dut.in.ready) == BigInt(1))) {
      poke(dut.in.bits.data, BigInt(input1.next()))
      if (input1.hasNext == false) {
        poke(dut.in.bits.last, 1)
      }
    } else {
      poke(dut.in.valid, 0)
    }
    if (peek(dut.out.valid) == BigInt(1)) {
      tmpData = peek(dut.out.bits.data)
      if (dut.params.LISsubType == "LIS_FIFO") {
        expect(dut.out.bits.data, BigInt(in(out.length)))
        if (cntValidOut == in.size - 1) {
          expect(dut.out.bits.last, 1)
        }
        cntValidOut += 1
      } else {
        println("Currently not supported this kind of test!")
      }
      out += tmpData
    }
    step(1)
  }
  step(10)
}

class TLLISBlockSpec extends AnyFlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty

  val paramsLIS: LISParams[UInt] = LISParams(
    proto = UInt(16.W),
    LISsize = 32,
    LISsubType = "LIS_FIFO",
    rtcSize = true,
    sortDir = true,
    rtcSortDir = true
  )
  val baseAddress = 0x0000

  it should "Test LIS module with TileLink" in {
    val lazyDut =
      LazyModule(new TLLISBlock(paramsLIS, AddressSet(baseAddress, 0xfff), beatBytes = 8) with TLLISStandaloneBlock)
    chisel3.iotesters.Driver.execute(Array[String]("-tbn", "verilator"), () => lazyDut.module) { c =>
      new TLLISTester(lazyDut, AddressSet(baseAddress, 0xfff), beatBytes = 8)
    } should be(true)
  }
}
