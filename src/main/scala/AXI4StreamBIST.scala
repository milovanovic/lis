package lis

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR

import chisel3.experimental._
import dsptools._
import dsptools.numbers._

import dspblocks._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._

// Perhaps can be simplified, this is just fast way to generate build in self test model for LISTest
// DspBlock maybe should not be used!
abstract class StreamBIST [D, U, E, O, B <: Data] (beatBytes: Int) extends LazyModule()(Parameters.empty) with DspBlock[D, U, E, O, B] with HasCSR {

  val masterParams = AXI4StreamMasterParameters(
    name = "AXI4 Stream LFSR",
    n = beatBytes, // perhaps other value
    numMasters = 1
  )
  val streamNode = AXI4StreamMasterNode(masterParams)
 
  lazy val module = new LazyModuleImp(this) {
    val (out, edge) = streamNode.out.head
    // Control register!
    val enable = RegInit(false.B)
    val terminate = RegInit(false.B)
    
    val lfsrOn = enable && out.ready
    val lfsr = LFSR(16, lfsrOn)
    // connect output
    out.valid := enable && ~terminate
    out.bits.data := lfsr.asUInt
    out.bits.last := terminate // make it last one clock cycle
    
    val fields = Seq(
      // settable registers
      RegField(1, enable,
        RegFieldDesc(name = "enable", desc = "enable lfsr module")),
      RegField(1, terminate,
        RegFieldDesc(name = "terminate", desc = "terminate sending data on output, connected to output last signal")),
    )
    //define abract register map so it can be AXI4, Tilelink, APB, AHB
    regmap(
      fields.zipWithIndex.map({ case (f, i) =>
          i * beatBytes -> Seq(f)
      }): _*
    )
  }
}

class AXI4StreamBIST(address: AddressSet, beatBytes: Int = 4)(implicit p: Parameters) extends StreamBIST [AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle](beatBytes) with AXI4DspBlock with AXI4HasCSR {
  val mem = Some(AXI4RegisterNode(address = address, beatBytes = beatBytes)) 
}
