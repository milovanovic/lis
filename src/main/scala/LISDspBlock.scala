package lis

import chisel3.util._
import chisel3.{fromDoubleToLiteral => _, fromIntToBinaryPoint => _, _}
import fixedpoint._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import dsptools.numbers._
import dspblocks._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

//noinspection TypeAnnotation
abstract class LISBlock[T <: Data: Real: BinaryRepresentation, D, U, E, O, B <: Data](
  params:    LISParams[T],
  beatBytes: Int)
    extends LazyModule()(Parameters.empty)
    with DspBlock[D, U, E, O, B]
    with HasCSR {

  require(
    params.rtcSize == true || params.rtcSortDir == true || params.flushData == true || params.useSorterEmpty == true || params.useSorterFull == true
  )

  val streamNode = AXI4StreamIdentityNode()

  lazy val module = new LazyModuleImp(this) {
    val (in, _) = streamNode.in(0)
    val (out, _) = streamNode.out(0)

    val lis = Module(new LinearSorter(params))

    // connect input
    lis.io.in.valid := in.valid
    lis.io.in.bits := in.bits.data.asTypeOf(params.proto)
    lis.io.lastIn := in.bits.last

    out.bits.last := lis.io.lastOut
    in.ready := lis.io.out.ready

    // connect output
    out.valid := lis.io.out.valid
    lis.io.out.ready := out.ready
    out.bits.data := lis.io.out.bits.asUInt

    var commonFields: Seq[RegField] = Seq[RegField]()

    if (params.rtcSortDir) {
      val sortDir = RegInit(true.B)
      lis.io.sortDir.get := sortDir
      commonFields = commonFields :+ RegField(
        1,
        sortDir,
        RegFieldDesc(
          name = "sortDir",
          desc = "define sorting direction (`true` denotes ascending, `false` denotes descending sorting direction"
        )
      )
    }

    if (params.rtcSize) {
      val lisSize = RegInit(0.U(log2Ceil(params.LISsize).W))
      lis.io.lisSize.get := lisSize
      commonFields = commonFields :+ RegField(
        log2Ceil(params.LISsize),
        lisSize,
        RegFieldDesc(name = "lisSize", desc = "contains lis size which is used for run time configurability control")
      )
    }

    if (params.flushData == true) {
      val flushData = RegInit(false.B)
      commonFields =
        commonFields :+ RegField(1, flushData, RegFieldDesc(name = "flushData", desc = "trigger data flushing"))
      lis.io.flushData.get := flushData
    }

    if (params.LISsubType == "LIS_input") {
      val discardPos = RegInit(0.U(log2Ceil(params.LISsize).W))
      commonFields = commonFields :+ RegField(
        log2Ceil(params.LISsize),
        discardPos,
        RegFieldDesc(
          name = "discardPos",
          desc = "defines position of the discarding element - used only for LIS_input scheme"
        )
      )
      lis.io.discardPos.get := discardPos
    }

    if (params.useSorterFull) {
      val sorterFull = RegInit(false.B)
      commonFields = commonFields :+ RegField.r(
        1,
        sorterFull,
        RegFieldDesc(name = "sorterFull", desc = "indicates whether sorter is full or not")
      )
      sorterFull := lis.io.sorterFull.get
    }

    if (params.useSorterEmpty) {
      val sorterEmpty = RegInit(true.B)
      commonFields = commonFields :+ RegField.r(
        1,
        sorterEmpty,
        RegFieldDesc(name = "sorterEmpty", desc = "indicates whether sorter is empty or not")
      )
      sorterEmpty := lis.io.sorterEmpty.get
    }

    regmap(
      commonFields.zipWithIndex.map({
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
    rtcSize = true,
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
    rtcSize = true,
    sortDir = true
  )
  val baseAddress = 0x500
  implicit val p: Parameters = Parameters.empty
  val lisModule = LazyModule(
    new TLLISBlock(paramsLIS, AddressSet(baseAddress + 0x100, 0xff), beatBytes = 4) with dspblocks.TLStandaloneBlock {}
  )
  (new ChiselStage).execute(args, Seq(ChiselGeneratorAnnotation(() => lisModule.module)))
}
