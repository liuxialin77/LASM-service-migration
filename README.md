LASM: Latency- and Affinity-Aware Service Migration in Mobile Edge Computing
This repository contains the simulation code, configuration files, network topologies, and raw results for the paper:

A Latency-and-Affinity-Based Service Migration in Mobile Edge Computing

The code implements the LASM framework, including:

Stage 1: DRL-based UFC migration (multi-agent Double DQN)

Stage 2: Affinity-based DPC placement (clustering + UFC-anchored greedy placement)

It also includes the implementation of five baseline strategies: NM, AM, DRLM, LL, DM.

1. Requirements
Dependency	Version
Java	JDK 1.8 or higher
Deeplearning4j	1.0.0-beta7
EdgeCloudSim	1.0
BRITE	1.0
Python (for plotting & Wilcoxon test)	3.8+
Python packages	numpy, pandas, matplotlib, scipy
2. Repository Structure
LASM-Service-Migration/
│
LASM-Service-Migration/
├── src/    # LASM 框架、仿真主程序app6、LASM算法+基线算法EdgeVmAllocationPolicy_Custom、牧马人模型/随机游走模型等
├── scripts/    # 配置文件
├── sim_results/    # 仿真原始结果
├── imgs/           # 论文相关图片
├── lib/            # 依赖库
├── pom.xml         # Maven 配置
└── README.md       # LASM 项目说明
3. How to Run
3.1 Compile the code
bash
cd src/main/java/edu.boun.edgecloudsim.applications.sample_app6
javac -cp MainApp.java
3.2 Run LASM
bash
java -cp MainApp.java
3.3 Run baselinesz
在EdgeVmAllocationPolicy_Custom文件中切换不同的optimizeAllocation()函数
4. Notes on Reproducibility
All results in the paper are averaged over 10 independent runs.
The EUA dataset validation (Fig.8) uses the Melbourne CBD subset; the raw data is in scripts/sample_app6/config/users-melbcbd-generated.csv.
