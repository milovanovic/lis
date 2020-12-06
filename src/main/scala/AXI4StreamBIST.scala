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
    val cntrOrLFSR = RegInit(false.B) 
    val upOrdown = RegInit(false.B) // do not use it for know
    
    val bistOn = enable && out.ready

    // make it configurable!
    val lfsr = LFSR(16, bistOn && ~cntrOrLFSR)
    // make it configurable!
    val cnt = RegInit(0.U(8.W))
    val cntEn = bistOn && cntrOrLFSR
    val max = (scala.math.pow(2,8).toInt - 1).U
    
    // maybe not necessary
    when (cntEn && cnt === max) {
      cnt := 0.U
    }
    .elsewhen (cntEn) {
      cnt := cnt + 1.U
    }
    
    // connect output
    out.valid := enable && ~terminate
    out.bits.data := Mux(cntrOrLFSR, cnt, lfsr.asUInt)
    out.bits.last := terminate // make it last one clock cycle
    
    val fields = Seq(
      // settable registers
      RegField(1, enable,
        RegFieldDesc(name = "enable", desc = "enable lfsr module")),
      RegField(1, terminate,
        RegFieldDesc(name = "terminate", desc = "terminate sending data on output, connected to output last signal")),
      RegField(1, cntrOrLFSR,
        RegFieldDesc(name = "cntrOrLFSR", desc = "send on output counter value or LFSR value")),
      RegField(1, upOrdown ,
        RegFieldDesc(name = "upOrdown", desc = "counter should count up or down"))
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
