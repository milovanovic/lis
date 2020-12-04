package lis

import chisel3._
import chisel3.util._
import chisel3.experimental._
import dsptools._
import dsptools.numbers._

import dspblocks._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._

/**
* Can be useful for median streaming calculation
**/

trait AXI4LISStandaloneBlock extends AXI4LISBlock[SInt] {
  def standaloneParams = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 1)
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))

    m :=
      BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) :=
      ioMemNode

    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}
  
  val ioInNode = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = 4)))
  val ioOutNode = BundleBridgeSink[AXI4StreamBundle]()

  ioOutNode :=
    AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) :=
    streamNode :=
    BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 4)) :=
    ioInNode

  val in = InModuleBody { ioInNode.makeIO() }
  val out = InModuleBody { ioOutNode.makeIO() }
}


abstract class LISBlock [T <: Data : Real: BinaryRepresentation, D, U, E, O, B <: Data] (params: LISParams[T], beatBytes: Int) extends LazyModule()(Parameters.empty) with DspBlock[D, U, E, O, B] with HasCSR {

  val streamNode = AXI4StreamIdentityNode()

  lazy val module = new LazyModuleImp(this) {
    val (in, _)  = streamNode.in(0)
    val (out, _) = streamNode.out(0)

    val lis = Module(new LinearSorter(params))

    // control registers
    val sortDir = RegInit(true.B)
    val flushData = RegInit(false.B)
    val discardPos = RegInit(0.U(log2Ceil(params.LISsize + 1).W))
    val sendOnOutput = RegInit(0.U(log2Ceil(params.LISsize + 1).W))
    val lisSize = RegInit((params.LISsize).U(log2Ceil(params.LISsize + 1).W))

    // status registers
    val sorterFull = RegInit(false.B)
    val sorterEmpty = RegInit(true.B)

    // connect input
    lis.io.lastIn := in.bits.last
    lis.io.in.valid := in.valid
    lis.io.in.bits := in.bits.data.asTypeOf(params.proto)
    in.ready := lis.io.in.ready

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
    if (params.LIStype == "LIS_input") {
      lis.io.discardPos.get := discardPos
    }
// Cat(outputBufferCos2.asUInt(), outputBufferSin2.asUInt())
    
    // connect output
    out.valid := lis.io.out.valid
    lis.io.out.ready := out.ready
    out.bits.data := Cat(lis.io.out.bits.asUInt, lis.io.sortedData(sendOnOutput).asUInt)
    out.bits.last := lis.io.lastOut
    // connect status registers
    if (params.useSorterFull) {
      sorterFull := lis.io.sorterFull.get
    }
    if (params.useSorterEmpty) {
      sorterEmpty := lis.io.sorterEmpty.get
    }

    val fields = Seq(
      // settable registers
      RegField(1, sortDir,
        RegFieldDesc(name = "sortDir", desc = "define sorting direction (`true` denotes ascending, `false` denotes descending sorting direction) ")),
      RegField(log2Ceil(params.LISsize), lisSize,
        RegFieldDesc(name = "lisSize", desc = "contains lis size which is used for run time configurability control")),
      RegField(1, flushData,
        RegFieldDesc(name = "flushData", desc = "trigger data flushing")),
      RegField(log2Ceil(params.LISsize), discardPos,
        RegFieldDesc(name = "discardPos", desc = "defines position of the discarding element - used only if LIS_input scheme is used")),
      RegField(log2Ceil(params.LISsize), sendOnOutput,
        RegFieldDesc(name = "sendOnOutput", desc = "another data that should be sent on output")),
      // read-only status registers
      RegField.r(1, sorterFull,
        RegFieldDesc(name = "sorterFull", desc = "indicates whether sorter is full or not")),
      RegField.r(1, sorterEmpty,
        RegFieldDesc(name = "sorterEmpty", desc = "indicates whether sorter is empty or not"))
    )
    //define abract register map so it can be AXI4, Tilelink, APB, AHB
    regmap(
      fields.zipWithIndex.map({ case (f, i) =>
          i * beatBytes -> Seq(f)
      }): _*
    )
  }
}

class AXI4LISBlock[T <: Data : Real: BinaryRepresentation](params: LISParams[T], address: AddressSet, _beatBytes: Int = 4)(implicit p: Parameters) extends LISBlock[T, AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle](params, _beatBytes) with AXI4DspBlock with AXI4HasCSR {
  val mem = Some(AXI4RegisterNode(address = address, beatBytes = _beatBytes)) // use AXI4 memory mapped
}

object LISDspBlock extends App
{
//   val paramsLIS: LISParams[FixedPoint] = LISParams(
//     proto = FixedPoint(16.W, 14.BP),
//     LISsize = 8,
//     LIStype = "LIS_input",
//     rtcSize = false,
//     sortDir = true
//   )
  val sorterType = "LIS_input"
  
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
  implicit val p: Parameters = Parameters.empty
  val lisModule = LazyModule(new AXI4LISBlock(params, AddressSet(baseAddress + 0x100, 0xFF), _beatBytes = 4) with AXI4LISStandaloneBlock)

  chisel3.Driver.execute(args, ()=> lisModule.module) // generate verilog code
}
