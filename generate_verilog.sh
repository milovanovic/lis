#!/bin/bash
echo $0
full_path=$(realpath $0)
dir_path=$(dirname $full_path)

target_dir_LIS_SR=$dir_path/generated-rtl/lis_sr
target_dir_LIS_CNT=$dir_path/generated-rtl/lis_cnt
fifo_type_LIS=LIS_FIFO

sorter_size_array=(2 4 8 16 32 64 128 256)
word_size_array=(16)


generate_verilog_lis_fifo_sr () {
  separate_verilog=${1:-1}

  for sorter_size in "${sorter_size_array[@]}"
  do
    for width in "${word_size_array[@]}"
    do
      lis_dir_name=$target_dir_LIS_SR/sorter_size_${sorter_size}_width_${width}
      cd $dir_path && sbt "runMain lis.LISApp $lis_dir_name $width $sorter_size 0 LIS_SR $fifo_type_LIS ${separate_verilog}"
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
      cd $dir_path && sbt "runMain lis.LISApp $lis_dir_name $width $sorter_size 0 LIS_CNT $fifo_type_LIS ${separate_verilog}"
    done
  done
}

"$@"
