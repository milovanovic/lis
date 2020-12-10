// SPDX-License-Identifier: Apache-2.0

package lis

import dspblocks._
import dsptools._
import dsptools.numbers._
import chisel3._
import chisel3.experimental._
import chisel3.util._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

import uart._
import splitter._


trait LISTestPins extends LISTest {
  val beatBytes = 4
    // Generate AXI4 slave output
  def standaloneParams = AXI4BundleParameters(addrBits = beatBytes*8, dataBits = beatBytes*8, idBits = 1)
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))
    m := BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) := ioMemNode
    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}

  // Generate AXI-stream output
  val ioStreamNode = BundleBridgeSink[AXI4StreamBundle]()
  ioStreamNode := AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) := out_adapt
  val outStream = InModuleBody { ioStreamNode.makeIO() }

  val ioparallelin = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = 1)))
  in_queue.node := BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 1)) := ioparallelin
  val inStream = InModuleBody { ioparallelin.makeIO() }
}


// With FixedPoint data
case class LISTestFixedParameters (
  lisFIFOParams       : LISParams[FixedPoint],
  lisInputParams      : LISParams[FixedPoint],
  lisFixedParams      : LISParams[FixedPoint],
  lisFIFOAddress       : AddressSet,
  lisFIFOMuxAddress0  : AddressSet,
  lisInputAddress      : AddressSet,
  lisInputMuxAddress0 : AddressSet,
  lisFixedAddress     : AddressSet,
  lisFixedMuxAddress0 : AddressSet,
  inSplitAddress      : AddressSet,
  bistAddress         : AddressSet,
  bistSplitAddress    : AddressSet,
  outMuxAddress       : AddressSet,
  outSplitAddress     : AddressSet,
  uartParams          : UARTParams,
  uRxSplitAddress     : AddressSet,
  divisorInit         : Int,
  beatBytes           : Int
)

case class LISTestSIntParameters (
  lisFIFOParams       : LISParams[SInt],
  lisInputParams      : LISParams[SInt],
  lisFixedParams      : LISParams[SInt],
  lisFIFOddress       : AddressSet,
  lisFIFOMuxAddress0  : AddressSet,
  lisInputddress      : AddressSet,
  lisInputMuxAddress0 : AddressSet,
  lisFixedAddress     : AddressSet,
  lisFixedMuxAddress0 : AddressSet,
  inSplitAddress      : AddressSet,
  bistAddress         : AddressSet,
  bistSplitAddress    : AddressSet,
  outMuxAddress       : AddressSet,
  outSplitAddress     : AddressSet,
  uartParams          : UARTParams,
  uRxSplitAddress     : AddressSet,
  divisorInit         : Int,
  beatBytes           : Int
)

class AllOnes(beatBytes: Int) extends LazyModule()(Parameters.empty) {
  val streamNode = AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters(Seq(AXI4StreamMasterParameters( "allones", n = beatBytes)))))
  lazy val module = new LazyModuleImp(this) {
    val (out, _) = streamNode.out(0)
    val data: BigInt = (0xFFFFFFFF)
    out.valid := true.B
    out.ready := DontCare
    out.bits.data := -1.S((beatBytes*8).W).asUInt
    out.bits.last := false.B
  }
}

class AllZeros(beatBytes: Int) extends LazyModule()(Parameters.empty) {
  val streamNode = AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters(Seq(AXI4StreamMasterParameters( "allzeroes", n = beatBytes)))))
  lazy val module = new LazyModuleImp(this) {
    val (out, _) = streamNode.out(0)
    out.valid := true.B
    out.ready := DontCare
    out.bits.data := 0.U
    out.bits.last := false.B
  }
}

class AlwaysReady extends LazyModule()(Parameters.empty) {
  val streamNode = AXI4StreamSlaveNode(AXI4StreamSlaveParameters())
  lazy val module = new LazyModuleImp(this) {
    val (in, _) = streamNode.in(0)
    val data = RegInit(0.U(32.W))
    in.valid := DontCare
    in.ready := true.B
    in.bits.data := DontCare
    in.bits.last := DontCare
  }
}

class StreamBuffer(params: BufferParams, beatBytes: Int) extends LazyModule()(Parameters.empty){
  val innode  = AXI4StreamSlaveNode(AXI4StreamSlaveParameters())
  val outnode = AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters(Seq(AXI4StreamMasterParameters( "buffer", n = beatBytes)))))
  val node = NodeHandle(innode, outnode)

  lazy val module = new LazyModuleImp(this) {
    
    val (in, _)  = innode.in(0)
    val (out, _) = outnode.out(0)

    val queue = Queue.irrevocable(in, params.depth, pipe=params.pipe, flow=params.flow)
    out.valid := queue.valid
    out.bits := queue.bits
    queue.ready := out.ready
  }
}

class LISTest(params: LISTestFixedParameters) extends LazyModule()(Parameters.empty) {

  val in_adapt  = AXI4StreamWidthAdapter.nToOne(params.beatBytes) // 32 or 16????????
  val in_split  = LazyModule(new AXI4Splitter(address = params.inSplitAddress, beatBytes = params.beatBytes))
  val in_queue  = LazyModule(new StreamBuffer(BufferParams(1, true, true), beatBytes = 1))

  
  //AXI4LISBlock
  val lisFifo       = LazyModule(new AXI4LISBlock(params.lisFIFOParams, params.lisFIFOAddress, params.beatBytes))
  val lisFifo_mux0  = LazyModule(new AXI4StreamMux(address = params.lisFIFOMuxAddress0,  beatBytes = params.beatBytes))
  val lisFifo_ones  = LazyModule(new AllOnes(beatBytes = params.beatBytes))
  val lisFifo_zeros = LazyModule(new AllZeros(beatBytes = params.beatBytes))
  val lisFifo_rdy_0 = LazyModule(new AlwaysReady)
  
  
  val lisInput       = LazyModule(new AXI4LISBlock(params.lisInputParams, params.lisInputAddress, params.beatBytes))
  val lisInput_mux0  = LazyModule(new AXI4StreamMux(address = params.lisInputMuxAddress0,  beatBytes = params.beatBytes))
  val lisInput_ones  = LazyModule(new AllOnes(beatBytes = params.beatBytes))
  val lisInput_zeros = LazyModule(new AllZeros(beatBytes = params.beatBytes))
  val lisInput_rdy_0 = LazyModule(new AlwaysReady)
  
  
  val lisFixed       = LazyModule(new AXI4LISBlock(params.lisFixedParams, params.lisFixedAddress, params.beatBytes))
  val lisFixed_mux0  = LazyModule(new AXI4StreamMux(address = params.lisFixedMuxAddress0,  beatBytes = params.beatBytes))
  val lisFixed_ones  = LazyModule(new AllOnes(beatBytes = params.beatBytes))
  val lisFixed_zeros = LazyModule(new AllZeros(beatBytes = params.beatBytes))
  val lisFixed_rdy_0 = LazyModule(new AlwaysReady)

  val bist           = LazyModule(new AXI4StreamBIST(address = params.bistAddress, beatBytes = params.beatBytes))
  val bist_split     = LazyModule(new AXI4Splitter(address = params.bistSplitAddress, beatBytes = params.beatBytes))

  
  val out_mux   = LazyModule(new AXI4StreamMux(address = params.outMuxAddress, beatBytes = params.beatBytes))
  //val out_split = LazyModule(new AXI4Splitter(address  = params.outSplitAddress, beatBytes = params.beatBytes))
  val out_queue = LazyModule(new StreamBuffer(BufferParams(1, true, true), beatBytes = params.beatBytes))
  val out_adapt = AXI4StreamWidthAdapter.oneToN(params.beatBytes)
  val out_rdy   = LazyModule(new AlwaysReady)

  val uTx_queue = LazyModule(new StreamBuffer(BufferParams(params.beatBytes), beatBytes = params.beatBytes))
  val uTx_adapt = AXI4StreamWidthAdapter.oneToN(params.beatBytes)
  val uRx_adapt = AXI4StreamWidthAdapter.nToOne(params.beatBytes)
  val uRx_split = LazyModule(new AXI4Splitter(address = params.uRxSplitAddress, beatBytes = params.beatBytes))
  val uart      = LazyModule(new AXI4UARTBlock(params.uartParams, AddressSet(params.uartParams.address,0xFF), divisorInit = params.divisorInit, _beatBytes = params.beatBytes){
    // Add interrupt bundle
    val ioIntNode = BundleBridgeSink[Vec[Bool]]()
    ioIntNode := IntToBundleBridge(IntSinkPortParameters(Seq(IntSinkParameters()))) := intnode
    val ioInt = InModuleBody {
        val io = IO(Output(ioIntNode.bundle.cloneType))
        io.suggestName("int")
        io := ioIntNode.bundle
        io
    }
  })

  // define mem
  lazy val blocks = Seq(in_split, lisFifo, lisFifo_mux0, lisInput, lisInput_mux0, lisFixed, lisFixed_mux0, bist, bist_split, out_mux, uart, uRx_split)
  
  val bus = LazyModule(new AXI4Xbar)
  val mem = Some(bus.node)
  for (b <- blocks) {
    b.mem.foreach { _ := AXI4Buffer() := bus.node }
  } 
 
  // connect nodes
  in_split.streamNode  := in_adapt := in_queue.node                       // in_queue    -----> in_adapt   -----> in_split

  lisFifo_mux0.streamNode := AXI4StreamBuffer() := bist_split.streamNode                       // bist_split  --0--> lisFifo_mux0
  
  lisFifo_mux0.streamNode := in_split.streamNode                         // in_split       --1--> lisFifo_mux0
  lisFifo_mux0.streamNode := AXI4StreamBuffer() := uRx_split.streamNode                        // uRx_split      --2--> lisFifo_mux0
  lisFifo_mux0.streamNode := lisFifo_ones.streamNode                     // lisFifo_ones   --3--> lisFifo_mux0
  lisFifo_mux0.streamNode := lisFifo_zeros.streamNode                    // lisFifo_zeros  --4--> lisFifo_mux0
  lisFifo.streamNode       := lisFifo_mux0.streamNode                    // lisFifo_mux0  --0--> lisFifo
  lisFifo_rdy_0.streamNode := lisFifo_mux0.streamNode                    // lisFifo_mux0  --1--> lisFifo_rdy_0

  lisInput_mux0.streamNode := AXI4StreamBuffer() := bist_split.streamNode                      // bist_split  --0--> lisFixed_mux0

  lisInput_mux0.streamNode  := AXI4StreamBuffer() := in_split.streamNode                        // in_split        --1--> lisInput_mux0
  lisInput_mux0.streamNode  := AXI4StreamBuffer() := uRx_split.streamNode                       // uRx_split       --2--> lisInput_mux0
  lisInput_mux0.streamNode  := AXI4StreamBuffer() := lisInput_ones.streamNode                   // lisInput_ones   --3--> lisInput_mux0
  lisInput_mux0.streamNode  := AXI4StreamBuffer() := lisInput_zeros.streamNode                  // lisInput_zeros  --4--> lisInput_mux0
  lisInput.streamNode       := AXI4StreamBuffer() := lisInput_mux0.streamNode                   // lisInput_mux0  --0--> lisInput
  lisInput_rdy_0.streamNode := lisInput_mux0.streamNode                   // lisInput_mux0  --1--> lisInput_rdy_0
  
  lisFixed_mux0.streamNode := AXI4StreamBuffer() := bist_split.streamNode                      // bist_split      --0--> lisFixed_mux0

  lisFixed_mux0.streamNode := in_split.streamNode                        // in_split        --1--> lisFixed_mux0
  lisFixed_mux0.streamNode := AXI4StreamBuffer() := uRx_split.streamNode                       // uRx_split       --2--> lisFixed_mux0
  lisFixed_mux0.streamNode := lisFixed_ones.streamNode                   // lisFixed_ones   --3--> lisFixed_mux0
  lisFixed_mux0.streamNode := lisFixed_zeros.streamNode                  // lisFixed_zeros  --4--> lisFixed_mux0
  lisFixed.streamNode       := lisFixed_mux0.streamNode                  // lisFixed_mux0  --0--> lisFixed
  lisFixed_rdy_0.streamNode := lisFixed_mux0.streamNode                  // lisFixed_mux0  --1--> lisFixed_rdy_0
  
  
  out_mux.streamNode    := AXI4StreamBuffer() := lisFifo.streamNode           // lisFifo   --0--> out_mux
  out_mux.streamNode    := AXI4StreamBuffer() := lisInput.streamNode          // lisInput  --1--> out_mux
  out_mux.streamNode    := AXI4StreamBuffer() := lisFixed.streamNode          // lisFixed  --2--> out_mux
  bist_split.streamNode := bist.streamNode
  out_mux.streamNode    := AXI4StreamBuffer() := bist_split.streamNode        // bist_split --3--> out_mux
  out_mux.streamNode    := AXI4StreamBuffer() := in_split.streamNode          // in_split   --4--> out_mux
  out_mux.streamNode    := AXI4StreamBuffer() := uRx_split.streamNode         // uRx_split  --5--> out_mux
  out_queue.node        := AXI4StreamBuffer() := out_mux.streamNode           // out_mux    --0--> out_queue
  uTx_adapt := uTx_queue.node := out_mux.streamNode                           // out_mux    --1--> uTx_queue -----> uTx_adapt  
  out_rdy.streamNode    := out_mux.streamNode                             // out_mux    --2--> out_rdy

  //out_split.streamNode  := out_queue.node                                 // out_queue  ----> out_split
  out_adapt             := out_queue.node//out_split.streamNode                           // out_split  --0-> out_adapt

  uRx_adapt := uart.streamNode := uTx_adapt                               // uTx_adapt  -----> uart      -----> uRx_adapt
  uRx_split.streamNode  := uRx_adapt                                      // uRx_adapt  -----> uRx_split

  lazy val module = new LazyModuleImp(this) {
    // generate interrupt output
    val int = IO(Output(uart.ioInt.cloneType))
    int := uart.ioInt

    // generate uart input/output
    val uTx = IO(Output(uart.module.io.txd.cloneType))
    val uRx = IO(Input(uart.module.io.rxd.cloneType))

    uTx := uart.module.io.txd
    uart.module.io.rxd := uRx
  }
}

object LISTestApp extends App
{
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

  implicit val p: Parameters = Parameters.empty
  val standaloneModule = LazyModule(new LISTest(params) with LISTestPins)

  chisel3.Driver.execute(Array("--target-dir", "./rtl/LISTest", "--top-name", "LISTest"), ()=> standaloneModule.module)
}
 




