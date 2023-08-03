package lis

import chisel3.{fromDoubleToLiteral => _, fromIntToBinaryPoint => _, _}
import fixedpoint._
import chiseltest._
import dsptools.numbers._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class LinearSorterSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("Linear streaming insertion sorters")

  for (sorterType <- Seq("LIS_CNT", "LIS_SR")) {
    for (sorterSubType <- Seq("LIS_fixed", "LIS_FIFO", "LIS_input")) {
      for (sorterSize <- Seq(8, 16, 64)) {
        for (sortDir <- Seq(true, false)) {
          it should f"work for UInt, sorter type $sorterType, sorter subtype $sorterSubType, sorter size = $sorterSize and parameter sortDir = $sortDir" ignore { //ignore {//in {
            val params: LISParams[UInt] = LISParams(
              proto = UInt(16.W),
              LISsize = sorterSize,
              LIStype = sorterType,
              LISsubType = sorterSubType,
              discardPos = if (sorterSubType == "LIS_fixed") Some(sorterSize / 2) else None,
              rtcSize = false,
              sortDir = sortDir
            )
            val in =
              Seq.fill(params.LISsize)(
                Random.nextInt(1 << params.proto.getWidth).toDouble
              )
            test(new LinearSorter(params))
              .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation))
              .runPeekPoke(new LinearSorterTester(_, in, 0))
          }
        }
      }
    }
  }

  for (sorterType <- Seq("LIS_CNT", "LIS_SR")) {
    for (sorterSubType <- Seq("LIS_fixed", "LIS_FIFO", "LIS_input")) {
      for (sorterSize <- Seq(2, 16, 32)) {
        for (sortDir <- Seq(true, false)) {
          it should f"work for SInt, sorter type $sorterType, sorter subtype $sorterSubType, sorter size = $sorterSize and sortDir = $sortDir" ignore {
            val params: LISParams[SInt] = LISParams(
              proto = SInt(16.W),
              LIStype = sorterType,
              LISsize = sorterSize,
              LISsubType = sorterSubType,
              discardPos = if (sorterSubType == "LIS_fixed") Some(0) else None,
              sortDir = true
            )
            val in = Seq.fill(params.LISsize)(
              (Random.nextInt((1 << (params.proto.getWidth - 1)) * 2) - (1 << (params.proto.getWidth - 1))).toDouble
            )
            test(new LinearSorter(params))
              .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation))
              .runPeekPoke(new LinearSorterTester(_, in, 0))
          }
        }
      }
    }
  }

  for (sorterType <- Seq("LIS_CNT", "LIS_SR")) {
    for (sorterSubType <- Seq("LIS_fixed", "LIS_FIFO", "LIS_input")) {
      for (sorterSize <- Seq(16)) {
        for (sortDir <- Seq(true, false)) {
          it should f"work for FixedPoint, sorter type $sorterType, sorter subtype $sorterSubType, sorter size = $sorterSize and sortDir = $sortDir" ignore {
            val params: LISParams[FixedPoint] = LISParams(
              proto = FixedPoint(16.W, 8.BP),
              LISsize = sorterSize,
              LIStype = sorterType,
              LISsubType = sorterSubType,
              discardPos = if (sorterSubType == "LIS_fixed") Some(sorterSize / 2) else None,
              sortDir = true
            )
            val in: Seq[Double] = Seq.fill(params.LISsize)(
              (Random.nextDouble() * 2 - 1) * (1 << params.proto.getWidth - params.proto.binaryPoint.get - 1)
            )
            test(new LinearSorter(params))
              .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation))
              .runPeekPoke(new LinearSorterTester(_, in, 0.02))
          }
        }
      }
    }
  }

  for (sorterType <- Seq("LIS_CNT", "LIS_SR")) {
    for (sorterSubType <- Seq("LIS_fixed", "LIS_FIFO", "LIS_input")) {
      for (sorterSize <- Seq(2, 16, 32)) {
        for (sortDir <- Seq(true, false)) {
          it should f"work for DspReal, sorter type $sorterType, sorter subtype $sorterSubType and  sorter size = $sorterSize and sortDir = $sortDir" ignore { //ignore {
            val params: LISParams[DspReal] = LISParams(
              proto = DspReal(),
              LISsize = sorterSize,
              LIStype = sorterType,
              LISsubType = sorterSubType,
              discardPos = if (sorterSubType == "LIS_fixed") Some(sorterSize / 2) else None,
              sortDir = true
            )
            val in = Seq.fill(params.LISsize)((Random.nextInt(Double.MaxValue.toInt) - Double.MaxValue.toInt).toDouble)
            test(new LinearSorter(params))
              .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation))
              .runPeekPoke(new LinearSorterTester(_, in, 0.0002))
          }
        }
      }
    }
  }

  for (sorterType <- Seq("LIS_CNT", "LIS_SR")) {
    // test run time configurability!
    for (sorterSubType <- Seq("LIS_fixed", "LIS_FIFO", "LIS_input")) {
      for (sorterSize <- Seq(8, 16, 32)) {
        it should f"work for FixedPoint, sorter type $sorterType, sorter subtype $sorterSubType, compile sorter size = $sorterSize and rtcSize configurable sorter size" in { //in {
          val params: LISParams[FixedPoint] = LISParams(
            proto = FixedPoint(16.W, 8.BP),
            LISsize = sorterSize,
            LIStype = sorterType,
            LISsubType = sorterSubType,
            discardPos = if (sorterSubType == "LIS_fixed") Some(0) else None,
            rtcSize = true,
            sortDir = true
          )
          val in: Seq[Double] = Seq.fill(params.LISsize)(
            (Random.nextDouble() * 2 - 1) * (1 << params.proto.getWidth - params.proto.binaryPoint.get - 1)
          )
          test(new LinearSorter(params))
            .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation))
            .runPeekPoke(new LinearSorterTesterRunTime(_, in, 0))
        }
      }
    }
  }
  // test rtcSize configurable sorting direction together with rtcSize configurable sorter size
  for (sorterType <- Seq("LIS_CNT", "LIS_SR")) {
    for (sorterSubType <- Seq("LIS_fixed", "LIS_FIFO", "LIS_input")) {
      for (sorterSize <- Seq(4, 15, 32)) { // test power of 2 and non power of 2 sorter size
        it should f"work for FixedPoint, sorter type $sorterType, sorter subtype $sorterSubType, compile sorter size = $sorterSize and rtcSize configurable sorting direction and sorter size" in {
          val params: LISParams[FixedPoint] = LISParams(
            proto = FixedPoint(16.W, 8.BP),
            LISsize = sorterSize,
            LISsubType = sorterSubType,
            discardPos = if (sorterSubType == "LIS_fixed") Some(0) else None,
            rtcSize = true,
            rtcSortDir = true, //false - works for both
            sortDir = true
          )
          val in: Seq[Double] = Seq.fill(params.LISsize)(
            (Random.nextDouble() * 2 - 1) * (1 << params.proto.getWidth - params.proto.binaryPoint.get - 1)
          )
          test(new LinearSorter(params))
            .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation))
            .runPeekPoke(new LinearSorterTesterRunTime(_, in, 0))
        }
      }
    }
  }

// test flushData parameter
//     for (sorterSubType <- Seq("LIS_FIFO", "LIS_fixed", "LIS_input")) {
//       for (sorterSize <- Seq(2,16,32)) {
//         for (sortDir <- Seq(true,false)) {
//           it should f"work for FixedPoint, sorter subtype $sorterSubType, sorter size = $sorterSize, sortDir = $sortDir and included flushData parameter" ignore {
//             val params: LISParams[FixedPoint] = LISParams(
//               proto = FixedPoint(16.W, 8.BP),
//               LISsize = sorterSize,
//               LISsubType = sorterSubType,
//               discardPos = if (sorterSubType == "LIS_fixed") Some(sorterSize/2) else None,
//               rtcSize = false,
//               flushData = true,
//               sortDir = sortDir
//             )
//           val in: Seq[Double] = Seq.fill(params.LISsize)((Random.nextDouble()*2-1) * ((1<<params.proto.getWidth - params.proto.binaryPoint.get-1)))
//             dsptools.Driver.execute(
//                 () => new LinearSorter(params),
//                 Array(
//                 "--backend-name", "firrtl", // "treadle", // "verilator",
//                 "--target-dir", s"test_run_dir/$sorterSubType-$sorterSize-FixedPoint")
//             ){ c =>
//                   new LinearSorterTester(c, in, 1)
//             } should be (true)
//           }
//         }
//       }
//     }

  for (sorterType <- Seq("LIS_CNT")) {
    for (sorterSubType <- Seq("LIS_FIFO", "LIS_fixed", "LIS_input")) {
      for (sorterSize <- Seq(2, 4, 16, 25)) {
        for (sortDir <- Seq(true, false)) {
          it should f"work for FixedPoint, sorter type $sorterType, sorter subtype $sorterSubType, sorter size = $sorterSize, sortDir = $sortDir and with lastIn/ lastOut" in {
            val params: LISParams[FixedPoint] = LISParams(
              proto = FixedPoint(16.W, 8.BP),
              LISsize = sorterSize,
              LIStype = sorterType,
              LISsubType = sorterSubType,
              discardPos = if (sorterSubType == "LIS_fixed") Some(sorterSize / 2) else None,
              sortDir = sortDir
            )
            val in: Seq[Double] = Seq.fill(params.LISsize)(
              (Random.nextDouble() * 2 - 1) * (1 << params.proto.getWidth - params.proto.binaryPoint.get - 1)
            )
            test(new LinearSorter(params))
              .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation))
              .runPeekPoke(new LinearSorterTesterRunTime(_, in, 0))
          }
        }
      }
    }
  }

  for (sorterSubType <- Seq("LIS_FIFO")) {
    for (sorterSize <- Seq(2, 4, 16, 25)) {
      for (sortDir <- Seq(true, false)) {
        it should f"work for FixedPoint, sorter type LIS_SR, sorter subtype $sorterSubType, sorter size = $sorterSize, sortDir = $sortDir and with lastIn/ lastOut" in {
          val params: LISParams[FixedPoint] = LISParams(
            proto = FixedPoint(16.W, 8.BP),
            LISsize = sorterSize,
            LIStype = "LIS_SR",
            LISsubType = sorterSubType,
            discardPos = if (sorterSubType == "LIS_fixed") Some(sorterSize / 2) else None,
            sortDir = sortDir
          )
          val in: Seq[Double] = Seq.fill(params.LISsize)(
            (Random.nextDouble() * 2 - 1) * (1 << params.proto.getWidth - params.proto.binaryPoint.get - 1)
          )
          test(new LinearSorter(params))
            .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation))
            .runPeekPoke(new LinearSorterTesterRunTime(_, in, 0))
        }
      }
    }
  }
}
