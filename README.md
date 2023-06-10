# Linear Insertion Sorters (LIS) Design Generator
[![Build](https://github.com/milovanovic/lis/actions/workflows/test.yml/badge.svg)](https://github.com/milovanovic/lis/actions/workflows/test.yml)

The LIS design generator is a generator of parameterizable and runtime reconfigurable fully streaming linear insertion sorters (LIS) written in the [Chisel](http://www.chisel-lang.org) hardware design language.

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
The shell script `init_submodules_and_build_sbt.sh`, initializes all tools and generators required to run this project. Besides that, it initializes `bulid.sbt` with all correctly defined dependencies. Versions of tools and generators correspond to chipyard 1.9.1 release. The user can replace versions by changing corresponding checkout commits inside the same script.
The shell script `remove_submodules.sh` executes commands that reverse the commands listed in `init_submodules_and_build_sbt.sh`.

## Documentation

* doc/lis_generator.md - detailed documentation about design generator
* doc/images - contains design block diagrams and waveform diagrams

Much more useful information about this work (focused on `LIStype = LIS_CNT` type) can be found inside "A Chisel Generator of Parameterizable and Runtime Reconfigurable Linear Insertion Streaming Sorters" paper published on International Conference on Microelectronics, MIEL 2021.

If you are using LIS generator for research, please cite it by the following publication:

    @INPROCEEDINGS{lis,
   	  author={Petrović, M. L. and Milovanović, V. M.},
   	  booktitle={2021 IEEE 32nd International Conference on Microelectronics (MIEL)},
   	  title={A Chisel Generator of Parameterizable and Runtime Reconfigurable Linear Insertion Streaming Sorters},
   	  year={2021},
   	  pages={251-254},
   	  doi={10.1109/MIEL52794.2021.9569153}}

## Guide For New Contributors

If you are trying to make a contribution to this project, please guide following:
1. You can contribute by submitting pull requests from your fork to the upstream repository.
2. If you need help on making a pull request, follow this [guide](https://docs.github.com/en/github/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests).
3. To understand how to compile and test from the source code, follow the instructions inside setup section.
