
A Linear Insertion Sorter (LIS) Chisel Generator
================================================
[![Build](https://github.com/milovanovic/lis/actions/workflows/test.yml/badge.svg)](https://github.com/milovanovic/lis/actions/workflows/test.yml)
## Overview

This repository contains a generator of parameterizable and runtime reconfigurable fully streaming linear insertion sorters writen in [Chisel ](http://www.chisel-lang.org) hardware design language. Fully streaming linear sorters with their rather simple and low-cost hardware architecture are widely used as the fundamental building blocks in many digital signal processing applications that require sorting operations and continuous data streaming interface.

### Linear streaming sorters

Linear insertion sorters use the same principle as the well- known insertion sort algorithm. The incoming data are inserted at the appropriate location inside the sorting array thus keeping the array sorted at every moment.

The linear insertion sorter is composed of basic processing elements (PEs) connected in a cascade. The sorter generator scheme featuring streaming I/O data and block diagram of one processing element (PE) for two different linear insertion sorter types is sketched below. The illustrated generator supports, for the each sorter type, three subtypes differing only in decision which cell should be discarded in the insertion process and sent to the output.

Design generator of linear insertion sorters scheme:

![Linear sorters generator scheme](./doc/images/svg/LinearSorterGenerator.svg)

Processing elements of two different LIS types:

![Processing elements](./doc/images/svg/ProcessingElements.svg)

The Chisel generator is described with following Scala files available inside`src/main/scala` directory:

* `LIS_util.scala` - contains useful objects such as `CounterWithReset` and `LifeCounter`
* `ControlLogic.scala`- description of Control Logic block used inside each `PEcnt` module
* `PEcnt.scala` - description of the basic processing element (PE) for the `LIS_CNT` sorter type
* `PEsr.scala` - description of the basic processing element (PE) for the `LIS_SR` sorter type
* `LinearSorterCNT.scala` -  description of module `LinearSorterCNT`
* `LinearSorterSR.scala` -  description of module `LinearSorterSR`
* `LISNetworkSR.scala` - connects all processing elements `PEcnt`
* `LinearSorter.scala` - contains parameters description and top level modul `LinearSorter`

#### Inputs

[Decoupled](http://github.com/freechipsproject/chisel3/wiki/Interfaces-Bulk-Connections) interface is used where .bits are data that should be sorted.

* `in: Flipped(Decoupled(params.proto))` - input data  wrapped with valid/ready signals
* `lastIn: Input(Bool())` - indicates the last sample in the input stream and triggers data flushing
* Control registers: `sorterSize`, `flushData`, `sortDirection`, `discardPos`

#### Outputs

[Decoupled](http://github.com/freechipsproject/chisel3/wiki/Interfaces-Bulk-Connections) interface is used where .bits are data that should be sent to the streaming output.
* `out: Decoupled(params.proto)` - output streaming data wrapped with valid/ready signals
* `lastOut: Output(Bool())` - indicates the last sample in the output stream
* `sortedData: Output(Vec(params.LISsize, params.proto))` - sorted data
* Status registers: `sorterEmpty`, `sorterFull`

## Parameter settings

Design parameters are defined inside `case class LISParams`. Users can customize design per use case by setting the appropriate parameters.

    case class LISParams[T <: Data: Real](
      proto: T,
      LISsize: Int = 16,
      LIStype: String = "LIS_CNT",
      LISsubType: String = "LIS_FIFO",
      rtcSize: Boolean = false,
      rtcSortDir: Boolean = false,
      discardPos: Option[Int] = None,
      useSorterFull: Boolean = false,
      useSorterEmpty: Boolean = false,
      flushData: Boolean = false,
      sortDir: Boolean = true,
    ) { . . .
    }

The explanation of each parameter is given below:
* `proto:` represents type of the sorted data. Users can choose among following Chisel types: `UInt`, `SInt`, `FixedPoint`, `DspReal`. Type `DspReal`is used to make golden model of the digital design.
* `LISsize:` is sorter size and it is not limited to be a power of 2
* `LIStype:` used to define linear insertion sorter type
* `LISsubType:` used to define linear insertion sorter subtype
  * Fixed discarding element position scheme - `"fixed"`
  * Fifo based scheme -  `"FIFO"`
  * Input selected discarding element position scheme - `"input"`
* `rtcSize` - used to enable runtime configurable sorter size
* `rtcSortDir` - used to enable runtime configurable sorting direction
* `discardPos` - should be defined only if fixed discarding element scheme is chosen
* `useSorterFull` - enable corresponding status register
* `useSorterEmpty` - enable corresponding status register
* `flushData` - include flushing data functionality
* `sortDir` - used to define sorting direction (`true` denotes ascending, `false` denotes descending sorting direction)

## Prerequisites

The following software packages should be installed prior to running this project:
* [sbt](http://www.scala-sbt.org)
* [Verilator](http://www.veripool.org/wiki/verilator)

## Setup

Proposed design generator is intended to be used inside [chipyard](https://github.com/ucb-bar/chipyard) environment as one of the generators located inside `generators/dsp-blocks`. Anyhow, if you want to use this repository standalone then follow instructions below:

*  Clone this repository.
*  Switch directory.
*  Initialize all tools and submodules.
*  Compile code, generate verilog or run tests.

```
git clone https://github.com/milovanovic/lis.git
cd lis
./init_submodules_and_build_sbt.sh
sbt test
```

#### Note
The shell script `init_submodules_and_build_sbt.sh`, initializes all tools and generators required to run this project. Besides that, it initializes `bulid.sbt` with all correctly defined dependencies. Versions of tools and generators correspond to chipyard 1.8.1 release. The user can replace versions by changing corresponding checkout commits inside the same script.
The shell script `remove_submodules.sh` executes commands that reverse the commands listed in `init_submodules_and_build_sbt.sh`.

## Tests

To run all tests written in Scala simulation environment a user should execute the following command: `testOnly lis.LinearSortersSpec`. Various test cases can be found in `LinearTestersSpec.scala` which is available inside `src/test/scala` directory. Two LIS testers are accessible inside `LinearTesters.scala`:
* `LinearSorterTester` - used for testing design when only compile time configurable parameters are set.
* `LinearSorterTesterRunTime` - used for testing proposed design when run time configurable parameters are included.
* `LinearSorterTLSpec` - simple test with TileLink memory master model

Tester functions such as `peek`, `poke` and `except`, available inside `DspTester` (check [dsptools Chisel library](http://github.com/ucb-bar/dsptools)), are extensively used for design testing.

----------

Much more useful information about this work (focused on `LIStype = LIS_CNT` type) can be found inside "A Chisel Generator of Parameterizable and Runtime Reconfigurable Linear Insertion Streaming Sorters" paper published on International Conference on Microelectronics, MIEL 2021. Please let us know if you have any questions/feedback!

