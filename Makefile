PROJ=MyCPU

verilog:
	@mill -i $(PROJ).run Verilog --target-dir=./verilog/$(PROJ)

.PHONY: verilog

.PHONY: clean
clean:
	@rm -rf verilog/$(PROJ)
	@mill -i clean