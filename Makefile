base_dir ?= $(abspath .)
target_dir_lis_sr?= $(base_dir)/generated-rtl/lis_sr
target_dir_lis_cnt ?= $(base_dir)/generated-rtl/lis_cnt

target_list = $(target_dir_lis) $(target_dir_bss)

SBT ?= sbt
all: clean gen_all_fifo

gen_all_fifo:
	bash generate_verilog.sh generate_verilog_lis_fifo_sr 0
	bash generate_verilog.sh generate_verilog_lis_fifo_cnt 0


.PHONY: clean
clean:
	for target_dir in $(target_list); do if [ -d "$$target_dir" ]; then cd "$$target_dir" && rm  -f **/*.*;fi done
