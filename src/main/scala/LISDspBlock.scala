package lis

import chisel3._
import chisel3.util._
import chisel3.experimental._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import dsptools._
import dsptools.numbers._

import dspblocks._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

abstract class LISBlock[T <: Data: Real: BinaryRepresentation, D, U, E, O, B <: Data](
  params:    LISParams[T],
  beatBytes: Int)
    extends LazyModule()(Parameters.empty)
    with DspBlock[D, U, E, O, B]
    with HasCSR {

  val streamNode = AXI4StreamIdentityNode()

  lazy val module = new LazyModuleImp(this) {
    val (in, _) = streamNode.in(0)
    val (out, _) = streamNode.out(0)

    val lis = Module(new LinearSorterCNT(params))

    // control registers
    val sortDir = RegInit(true.B)
    val flushData = RegInit(false.B)
    val discardPos = RegInit(0.U(log2Ceil(params.LISsize).W))
    val lisSize = RegInit(0.U(log2Ceil(params.LISsize).W))

    // status registers
    val sorterFull = RegInit(false.B)
    val sorterEmpty = RegInit(true.B)

    // connect input
    lis.io.in.valid := in.valid
    lis.io.in.bits := in.bits.data.asTypeOf(params.proto)
    lis.io.lastIn := in.bits.last

    out.bits.last := lis.io.lastOut
    in.ready := lis.io.out.ready

    // connect control registers
    if (params.rtcSortDir) {
      lis.io.sortDir.get := sortDir
    }
    if (params.rtcSize == true) {
      lis.io.lisSize.get := lisSize
    }
    if (params.flushData == true) {
      lis.io.flushData.get := flushData
    }
    if (params.LISsubType == "LIS_input") {
      lis.io.discardPos.get := discardPos
    }

    // connect output
    out.valid := lis.io.out.valid
    lis.io.out.ready := out.ready
    out.bits.data := lis.io.out.bits.asUInt

    // connect status registers
    if (params.useSorterFull) {
      sorterFull := lis.io.sorterFull.get
    }
    if (params.useSorterEmpty) {
      sorterEmpty := lis.io.sorterEmpty.get
    }

    val fields = Seq(
      // settable registers
      RegField(
        1,
        sortDir,
        RegFieldDesc(
          name = "sortDir",
          desc = "define sorting direction (`true` denotes ascending, `false` denotes descending sorting direction) "
        )
      ),
      RegField(
        log2Ceil(params.LISsize),
        lisSize,
        RegFieldDesc(name = "lisSize", desc = "contains lis size which is used for run time configurability control")
      ),
      RegField(1, flushData, RegFieldDesc(name = "flushData", desc = "trigger data flushing")),
      RegField(
        log2Ceil(params.LISsize),
        discardPos,
        RegFieldDesc(
          name = "discardPos",
          desc = "defines position of the discarding element - used only if LIS_input scheme is used"
        )
      ),
      // read-only status registers
      RegField.r(1, sorterFull, RegFieldDesc(name = "sorterFull", desc = "indicates whether sorter is full or not")),
      RegField.r(1, sorterEmpty, RegFieldDesc(name = "sorterEmpty", desc = "indicates whether sorter is empty or not"))
    )
    //define abract register map so it can be AXI4, Tilelink, APB, AHB
    regmap(
      fields.zipWithIndex.map({
        case (f, i) =>
          i * beatBytes -> Seq(f)
      }): _*
    )
  }
}

class TLLISBlock[T <: Data: Real: BinaryRepresentation](
  val params: LISParams[T],
  address:    AddressSet,
  beatBytes:  Int = 8
)(
  implicit p: Parameters)
    extends LISBlock[T, TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle](
      params,
      beatBytes
    )
    with TLDspBlock
    with TLHasCSR {
  val devname = "TLLISBlock"
  val devcompat = Seq("lis", "radardsp")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  override val mem = Some(TLRegisterNode(address = Seq(address), device = device, beatBytes = beatBytes))
}

class AXI4LISBlock[T <: Data: Real: BinaryRepresentation](
  params:     LISParams[T],
  address:    AddressSet,
  _beatBytes: Int = 4
)(
  implicit p: Parameters)
    extends LISBlock[
      T,
      AXI4MasterPortParameters,
      AXI4SlavePortParameters,
      AXI4EdgeParameters,
      AXI4EdgeParameters,
      AXI4Bundle
    ](params, _beatBytes)
    with AXI4DspBlock
    with AXI4HasCSR {
  val mem = Some(AXI4RegisterNode(address = address, beatBytes = _beatBytes)) // use AXI4 memory mapped
}

object LISAXI4DspBlock extends App {
  val paramsLIS: LISParams[FixedPoint] = LISParams(
    proto = FixedPoint(16.W, 14.BP),
    LISsize = 8,
    LISsubType = "LIS_input",
    rtcSize = false,
    sortDir = true
  )
  val baseAddress = 0x500
  implicit val p: Parameters = Parameters.empty
  val lisModule = LazyModule(
    new AXI4LISBlock(paramsLIS, AddressSet(baseAddress + 0x100, 0xff), _beatBytes = 4)
      with dspblocks.AXI4StandaloneBlock {
      override def standaloneParams = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 1)
    }
  )
  (new ChiselStage).execute(args, Seq(ChiselGeneratorAnnotation(() => lisModule.module)))
}

object LISTLDspBlock extends App {
  val paramsLIS: LISParams[FixedPoint] = LISParams(
    proto = FixedPoint(16.W, 14.BP),
    LISsize = 8,
    LISsubType = "LIS_input",
    rtcSize = false,
    sortDir = true
  )
  val baseAddress = 0x500
  implicit val p: Parameters = Parameters.empty
  val lisModule = LazyModule(
    new TLLISBlock(paramsLIS, AddressSet(baseAddress + 0x100, 0xff), beatBytes = 4) with dspblocks.TLStandaloneBlock {}
  )
  (new ChiselStage).execute(args, Seq(ChiselGeneratorAnnotation(() => lisModule.module)))
}
