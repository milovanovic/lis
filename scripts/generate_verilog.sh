#!/bin/bash
echo $0

#full_path=$(realpath $0)
#dir_path=$(dirname $full_path)
RDIR=$(git rev-parse --show-toplevel)

target_dir_LIS_SR=$RDIR/generated-rtl/lis_sr
target_dir_LIS_CNT=$RDIR/generated-rtl/lis_cnt
sub_type_LIS=(LIS_input)

sorter_size_array=(32 64)
word_size_array=(16)

generate_verilog_lis_fifo_sr () {
  separate_verilog=${1:-1}

  for sorter_size in "${sorter_size_array[@]}"
  do
    for width in "${word_size_array[@]}"
    do
      lis_dir_name=$target_dir_LIS_SR/sorter_size_${sorter_size}_width_${width}
      cd $RDIR && sbt "runMain lis.LISApp $lis_dir_name $width $sorter_size 1 LIS_SR $sub_type_LIS ${separate_verilog}"
    done
  done
}


generate_verilog_lis_fifo_cnt () {
  separate_verilog=${1:-1}

  for sorter_size in "${sorter_size_array[@]}"
  do
    for width in "${word_size_array[@]}"
    do
      lis_dir_name=$target_dir_LIS_CNT/sorter_size_${sorter_size}_width_${width}
      cd $RDIR && sbt "runMain lis.LISApp $lis_dir_name $width $sorter_size 1 LIS_CNT $sub_type_LIS ${separate_verilog}"
    done
  done
}

"$@"
