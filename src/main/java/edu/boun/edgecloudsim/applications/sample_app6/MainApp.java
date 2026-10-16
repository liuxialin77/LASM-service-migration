/*
 * Title:        EdgeCloudSim - Main Application
 * 
 * Description:  Main application for Simple App
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app6;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class MainApp {
	
	/**
	 * Creates main() to run this example
	 */
	public static void main(String[] args) {
		//disable console output of cloudsim library
		Log.disable();
		
		//enable console output and file output of this application
		SimLogger.enablePrintLog();
		
		int iterationNumber = 1;   //默认为1 ，可在命令行修改
		String configFile = "";
		String outputFolder = "";
		String edgeDevicesFile = "";
		String applicationsFile = "";
		if (args.length == 5){   //命令行的读取
			configFile = args[0];
			edgeDevicesFile = args[1];
			applicationsFile = args[2];
			outputFolder = args[3];
			iterationNumber = Integer.parseInt(args[4]);
		}
		else{
			SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
			configFile = "scripts/sample_app6/config/default_config.properties";
			applicationsFile = "scripts/sample_app6/config/applications.xml";
			edgeDevicesFile = "scripts/sample_app6/config/edge_devices.xml";
			outputFolder = "sim_results/ite" + iterationNumber;
		}

		//load settings from configuration file
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		if(SS.getFileLoggingEnabled()){
			SimLogger.enableFileLog();
			SimUtils.cleanOutputFolder(outputFolder);
		}
		
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");
        //为什么是这三重循环？
		for(int j=SS.getMinNumOfMobileDev(); j<=SS.getMaxNumOfMobileDev(); j+=SS.getMobileDevCounterSize())  //j是移动设备的数量  当读取默认的特征文件时，设备数从100到1000
		{
			for(int k=0; k<SS.getSimulationScenarios().length; k++) // 当读取默认的特征文件时，场景指SINGLE_TIER、TWO_TIER、TWO_TIER_WITH_EO
			{
				for(int i=0; i<SS.getOrchestratorPolicies().length; i++)  // 当读取默认的特征文件时，策略只有NEXT_FIT
				{
					String simScenario = SS.getSimulationScenarios()[k];   //第k个场景
					String orchestratorPolicy = SS.getOrchestratorPolicies()[i];  //第i个策略
					Date ScenarioStartDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioStartDate);
					
					SimLogger.printLine("Scenario started at " + now);
					SimLogger.printLine("Scenario: " + simScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
					SimLogger.printLine("Duration: " + SS.getSimulationTime()/3600 + " hour(s) - Poisson: " + SS.getTaskLookUpTable()[0][2] + " - #devices: " + j);
					SimLogger.getInstance().simStarted(outputFolder,"SIMRESULT_" + simScenario + "_"  + orchestratorPolicy + "_" + j + "DEVICES");
					
					try
					{
						// First step: Initialize the CloudSim package. It should be called
						// before creating any entities.
						int num_user = 2;   // number of grid users
						Calendar calendar = Calendar.getInstance();
						boolean trace_flag = false;  // mean trace events
				
						// Initialize the CloudSim library
						CloudSim.init(num_user, calendar, trace_flag, 0.01);
						
						// Generate EdgeCloudsim Scenario Factory，SampleScenarioFactory是一个真实的场景工厂,根据设备数量、场景和策略来建立
						//
						ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario);
						
						// Generate EdgeCloudSim Simulation Manager
						SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
						
						// Start simulation.启动边缘数据中心、云数据中心和本地数据中心，并为任务创建虚拟机，让数据中心执行这些虚拟机
						manager.startSimulation();
					}
					catch (Exception e)
					{
						SimLogger.printLine("The simulation has been terminated due to an unexpected error");
						e.printStackTrace();
						System.exit(0);
					}
					
					Date ScenarioEndDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioEndDate);
					SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
					SimLogger.printLine("----------------------------------------------------------------------");
				}//End of orchestrators loop
			}//End of scenarios loop
		}//End of mobile devices loop

		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
