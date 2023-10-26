# FIONA-V: Register-Transfer Level Implementation of FIONA-V ISA

[![version](https://img.shields.io/badge/version-1.1-orange)](https://github.com/hkust-fiona/) 
[![appear](https://img.shields.io/badge/appear-at_ICCAD_2023-blue)](https://iccad.com/)
[![license](https://img.shields.io/badge/license-Apache%202.0-light)](https://github.com/hkust-fiona/fiona-v/LICENSE)

🎉Welcome to the **FIONA-V** repository!   
🎯This sub-project is an integral part of the [FIONA-Toolchain](https://github.com/hkust-fiona/) and aims to provide a scala-based register-transfer level implementation of FIONA-V baseline ISA. It can be synthesized and physically run on [FIONA-Hardware](https://github.com/hkust-fiona/fiona-hardware) for end-to-end verification.

## Quickstarts for Developing FIONA-V
🚩This is a submodule for [chipyard](https://github.com/ucb-bar/chipyard). Please first set up your chipyard environment according to the official document. 

```bash
cd chipyard/
git submodule add https://github.com/hkust-fiona/fiona-v.git ./generators/fiona-v
```

After cloning this module, add the configuration to `generators/chipyard/src/main/scala/config/RocketFionaConfigs.scala`.

```scala
package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}
import chipyard.config._

// --------------
// Rocket+Fiona-v Configs
// --------------

class FionaRocketConfig extends Config(
  new fiona.WithFionaV ++                                // fiona-v rocc accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class FionaRocketDebugConfig extends Config(
  new fiona.WithFionaV ++                                // fiona-v rocc accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
```

Make the following modifications to `build.sbt`:

```diff
--- a/build.sbt
+++ b/build.sbt
@@ -152,7 +152,7 @@ lazy val chipyard = (project in file("generators/chipyard"))
   .dependson(testchipip, rocketchip, boom, hwacha, sifive_blocks, sifive_cache, iocell,
     sha3, // on separate line to allow for cleaner tutorial-setup patches
     dsptools, `rocket-dsp-utils`,
-    gemmini, icenet, tracegen, cva6, nvdla, sodor, ibex, fft_generator,
+    gemmini, icenet, tracegen, cva6, nvdla, sodor, ibex, fft_generator, fiona,
     constellation, mempress, barf, shuttle)
   .settings(librarydependencies ++= rocketlibdeps.value)
   .settings(
@@ -240,6 +240,11 @@ lazy val nvdla = (project in file("generators/nvdla"))
   .settings(librarydependencies ++= rocketlibdeps.value)
   .settings(commonsettings)
 
+lazy val fiona = (project in file("generators/fiona-v"))
+  .dependson(rocketchip)
+  .settings(librarydependencies ++= rocketlibdeps.value)
+  .settings(commonsettings)
+
 lazy val iocell = project(id = "iocell", base = file("./tools/barstools/") / "src")
   .settings(
     compile / scalasource := basedirectory.value / "main" / "scala" / "barstools" / "iocell",
```

Also, make the following changes to `./sims/verilator/Makefile`: (You need to point the `FIONA_PHOTONIC_DIR` to the corresponding repository)

```diff
--- a/sims/verilator/Makefile
+++ b/sims/verilator/Makefile
@@ -44,8 +44,16 @@ debug: $(sim_debug)
 #########################################################################################
 # simulaton requirements
 #########################################################################################
+FIONA_PHOTONIC_DIR=<path/to/fiona-photonic>
 SIM_FILE_REQS += \
-	$(ROCKETCHIP_RSRCS_DIR)/vsrc/TestDriver.v
+	$(ROCKETCHIP_RSRCS_DIR)/vsrc/TestDriver.v \
+	$(FIONA_PHOTONIC_DIR)/bridge/register.h \
+	$(FIONA_PHOTONIC_DIR)/bridge/verilator/puc_adapter.sv \
+	$(FIONA_PHOTONIC_DIR)/bridge/verilator/engine.cc \
+	$(FIONA_PHOTONIC_DIR)/bridge/verilator/engine.h \
+
+EXTRA_SIM_CXXFLAGS += $(shell python3.10-config --cflags --embed)
+EXTRA_SIM_LDFLAGS += $(shell python3.10-config --ldflags --embed)
 
 # copy files and add -FI for *.h files in *.f
 $(sim_files): $(SIM_FILE_REQS) $(ALL_MODS_FILELIST) | $(GEN_COLLATERAL_DIR)
```

To simulate the compiled binary, e.g., `dotp.riscv`, just type:

```
make run-binary-debug CONFIG=FionaRocketConfig BINARY=../../generators/fiona-v/test/dotp.riscv LOADMEM=1 -j
```



## Citations
🎓Please cite the following paper if you find this work useful:

> Yinyi Liu, Bohan Hu, Zhenguo Liu, Peiyu Chen, Linfeng Du, Jiaqi Liu, Xianbin Li, Wei Zhang, Jiang Xu. **FIONA: Photonic-Electronic Co-Simulation Framework and Transferable Prototyping for Photonic Accelerator.** In *2023 IEEE/ACM International Conference on Computer-Aided Design (ICCAD).* IEEE, 2023.

## Acknowledgements
❤️I would like to express my sincere gratitude and appreciation to several organizations for their invaluable contributions to my work. Without their support, this endeavor would not have been possible:

|**Organizations**|**Supports**|
|---|---|
|[University Grants Committee (Hong Kong)](https://www.ugc.edu.hk/eng/ugc/index.html)|Research Funds|
|Guangzhou-HKUST(GZ) Joint Funding Program|Research Funds|
|[Chipyard Framework](https://github.com/ucb-bar/chipyard)|Open-source Projects|

