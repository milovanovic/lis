package lis

import chisel3._
import chisel3.util._
import dsptools._
import dsptools.numbers._
import chisel3.experimental.FixedPoint

import chisel3.internal.requireIsChiselType

//dut.io.flushData.get
class LISNetworkSRIO[T <: Data: Real] (params: LISParams[T]) extends Bundle {
  val sortDir = if (params.rtcSortDir) Some(Input(Bool())) else None
  val data_rm = Input(params.proto.cloneType)
  val data_insert = Input(params.proto.cloneType)
  val sortedData = Input(Vec((params.LISsize+1), params.proto.cloneType)) // top level model defines this
  val nextSortedData = Output(Vec((params.LISsize+1), params.proto.cloneType))
  val lisSize = if (params.rtcSize == true) Some(Input(UInt((log2Up(params.LISsize)+1).W))) else None

  override def cloneType: this.type = LISNetworkSRIO(params).asInstanceOf[this.type]
}

object LISNetworkSRIO {
  def apply[T <: Data : Real](params: LISParams[T]): LISNetworkSRIO[T] = new LISNetworkSRIO(params)
}

class LISNetworkSR [T <: Data: Real] (val params: LISParams[T]) extends Module {
  val io = IO(LISNetworkSRIO(params))

  val elementIndices = (0 until (params.LISsize + 1))
  val next_data_vec = Wire(Vec(params.LISsize, params.proto))
  val xor_outputs = Wire(Vec(params.LISsize, Bool()))

  // instatiate all PEsrs
  elementIndices.map {
    case (ind) => {
      val lisSRelement = Module(new PEsr(params, ind+1))
      if (ind == 0) {
        if (params.rtcSortDir) {
          lisSRelement.io.sortDir.get := io.sortDir.get
        }
        lisSRelement.io.xor_input := true.B
        lisSRelement.io.data_rm.get := io.data_rm
        lisSRelement.io.data_insert := io.data_insert
        lisSRelement.io.sorted_data1.get := io.sortedData(ind)
        lisSRelement.io.sorted_data2 := io.sortedData(ind+1)
        io.nextSortedData(ind) := lisSRelement.io.data_out
        lisSRelement.io.isLast := false.B //lastCells(ind)
        next_data_vec(ind) := lisSRelement.io.next_data.get
        xor_outputs(ind) := lisSRelement.io.xor_output
      }
      else if (ind == params.LISsize) {
        if (params.rtcSortDir) {
          lisSRelement.io.sortDir.get := io.sortDir.get
        }
        lisSRelement.io.xor_input := xor_outputs(ind-1)
        lisSRelement.io.data_insert := io.data_insert
        lisSRelement.io.sorted_data2 := io.sortedData(ind)
        io.nextSortedData(ind) := lisSRelement.io.data_out
        lisSRelement.io.isLast := false.B //lastCells(ind-1)
      }
      else {
        if (params.rtcSortDir) {
          lisSRelement.io.sortDir.get := io.sortDir.get
        }
        lisSRelement.io.xor_input := xor_outputs(ind-1)
        lisSRelement.io.data_rm.get := io.data_rm
        lisSRelement.io.data_insert := io.data_insert
        lisSRelement.io.sorted_data1.get := io.sortedData(ind)
        lisSRelement.io.sorted_data2 := io.sortedData(ind+1)
        lisSRelement.io.prev_data.get := next_data_vec(ind-1)
        next_data_vec(ind) := lisSRelement.io.next_data.get
        xor_outputs(ind) := lisSRelement.io.xor_output
        io.nextSortedData(ind) := lisSRelement.io.data_out
        lisSRelement.io.isLast := false.B //lastCells(ind)
      }
    }
  }
}

object LISNetworkSRApp extends App
{
  val params: LISParams[FixedPoint] = LISParams(
    proto = FixedPoint(16.W, 14.BP),
    LISsize = 16,
    LISsubType = "LIS_FIFO",
    rtcSize = true,
    sortDir = true
  )
  chisel3.Driver.execute(args,()=>new LISNetworkSR(params))
}
