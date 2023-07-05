package lis

import chisel3._
import chisel3.util._
import chisel3.experimental.DataMirror

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config.{Config, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.BaseSubsystem

import dsptools.numbers._

/* LIS parameters and addresses */
case class LISParamsAndAddress[T <: Data: Real: BinaryRepresentation](
  lisParams:  LISParams[T],
  lisAddress: AddressSet,
  useAXI4:    Boolean)

/* AXI4LIS UInt Key */
case object LISKey extends Field[Option[LISParamsAndAddress[UInt]]](None)

trait CanHavePeripheryLIS { this: BaseSubsystem =>
  private val portName = "lis"

  val lis = p(LISKey) match {
    case Some(params) => {
      val lis = if (params.useAXI4) {
        val lis = LazyModule(
          new AXI4LISBlock(
            address = params.lisAddress,
            params = params.lisParams,
            _beatBytes = pbus.beatBytes
          )
        )
        // Connect mem
        pbus.coupleTo("lis") {
          lis.mem.get := AXI4Buffer() := TLToAXI4() := TLFragmenter(
            pbus.beatBytes,
            pbus.blockBytes,
            holdFirstDeny = true
          ) := _
        }
        // return
        Some(lis)
      } else {
        val lis = LazyModule(
          new TLLISBlock(
            address = params.lisAddress,
            params = params.lisParams,
            beatBytes = pbus.beatBytes
          )
        )
        // Connect mem
        pbus.coupleTo("lis") { lis.mem.get := TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _ }
        // return
        Some(lis)
      }
      // streamNode
      val ioInNode = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = pbus.beatBytes)))
      val ioOutNode = BundleBridgeSink[AXI4StreamBundle]()

      ioOutNode := AXI4StreamToBundleBridge(
        AXI4StreamSlaveParameters()
      ) := lis.get.streamNode := BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = pbus.beatBytes)) := ioInNode

      val lis_in = InModuleBody { ioInNode.makeIO() }
      val lis_out = InModuleBody { ioOutNode.makeIO() }

      // return
      Some(Seq(lis_in, lis_out))
    }
    case None => None
  }
}

trait CanHavePeripheryLISModuleImp extends LazyModuleImp {
  val outer: CanHavePeripheryLIS
}

class LISMirrorIO[T <: Data](private val gen: T) extends Bundle {
  val in = DataMirror.internal.chiselTypeClone[T](gen)
  val out = Flipped(DataMirror.internal.chiselTypeClone[T](gen))
}

/* Mixin to add AXI4LIS to rocket config */
class WithLIS(lisParams: LISParams[UInt], lisAddress: AddressSet = AddressSet(0x3000, 0xff), useAXI4: Boolean)
    extends Config((site, here, up) => {
      case LISKey =>
        Some(
          (LISParamsAndAddress(
            lisParams = lisParams,
            lisAddress = lisAddress,
            useAXI4 = useAXI4
          ))
        )
    })

case object LISAdapter {
  def tieoff(lis: Option[LISMirrorIO[AXI4StreamBundle]]): Unit = {
    lis.foreach { s =>
      s.in.valid := false.B
      s.in.bits := DontCare
      s.out.ready := true.B
    }
  }

  def tieoff(lis: LISMirrorIO[AXI4StreamBundle]): Unit = { tieoff(Some(lis)) }
}
