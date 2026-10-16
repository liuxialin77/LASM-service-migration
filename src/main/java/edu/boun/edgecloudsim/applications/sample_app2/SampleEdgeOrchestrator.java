/*
 * Title:        EdgeCloudSim - Edge Orchestrator
 * 
 * Description: 
 * SampleEdgeOrchestrator offloads tasks to proper server
 * by considering WAN bandwidth and edge server utilization.
 * After the target server is decided, the least loaded VM is selected.
 * If the target server is a remote edge server, MAN is used.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app2;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.cloud_server.CloudVM;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.SimLogger;

public class SampleEdgeOrchestrator extends EdgeOrchestrator {
	
	private int numberOfHost; //used by load balancer

	public SampleEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
	}

	/*
	 * (non-Javadoc)
	 * @see edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator#getDeviceToOffload(edu.boun.edgecloudsim.edge_client.Task)
	 * 
	 * It is assumed that the edge orchestrator app is running on the edge devices in a distributed manner
	 */
	@Override  //该方法和app1有区别，处理SINGLE_TIER、TWO_TIER_WITH_EO和NETWORK_BASED场景
	public int getDeviceToOffload(Task task) {
		int result = 0;
		
		//RODO: return proper host ID
		
		if(simScenario.equals("SINGLE_TIER")){
			result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		else if(simScenario.equals("TWO_TIER_WITH_EO")){
			//dummy (傀儡) task to simulate a task with 1 Mbit file size to upload and download 
			Task dummyTask = new Task(0, 0, 0, 0, 128, 128, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
			
			double wanDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(),
					SimSettings.CLOUD_DATACENTER_ID, dummyTask /* 1 Mbit */);   //dummyTask在核心网的传输延迟
			
			double wanBW = (wanDelay == 0) ? 0 : (1 / wanDelay); /* Mbps */ //dummyTask在核心网上占用的传输带宽
			
			double edgeUtilization = SimManager.getInstance().getEdgeServerManager().getAvgUtilization(); //获取所有边缘服务器上VM的CPU平均利用率
			

			if(policy.equals("NETWORK_BASED")){
				if(wanBW > 6)
					result = SimSettings.CLOUD_DATACENTER_ID;
				else
					result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			}
			else if(policy.equals("UTILIZATION_BASED")){
				double utilization = edgeUtilization;
				if(utilization > 80)     //边缘服务器上VM的CPU利用率已经很高了
					result = SimSettings.CLOUD_DATACENTER_ID;
				else
					result = SimSettings.GENERIC_EDGE_DEVICE_ID;  //在边缘服务器上计算
			}
			else if(policy.equals("HYBRID")){
				double utilization = edgeUtilization;
				if(wanBW > 6 && utilization > 80)  //占用的核心网的带宽多且边缘服务器的VM的CPU利用率已经很高了
					result = SimSettings.CLOUD_DATACENTER_ID;
				else
					result = SimSettings.GENERIC_EDGE_DEVICE_ID;
			}
			else {
				SimLogger.printLine("Unknown edge orchestrator policy! Terminating simulation...");
				System.exit(0);
			}
		}
		else {
			SimLogger.printLine("Unknown simulation scenario! Terminating simulation...");
			System.exit(0);
		}
		return result;
	}

	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		if(deviceId == SimSettings.CLOUD_DATACENTER_ID){
			//Select VM on cloud devices via Least Loaded algorithm!
			double selectedVmCapacity = 0; //start with min value
			List<Host> list = SimManager.getInstance().getCloudServerManager().getDatacenter().getHostList();
			for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
				List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
	            }
			}
		}
		else if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			//Select VM on edge devices via Least Loaded algorithm!
			double selectedVmCapacity = 0; //start with min value
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
				}
			}
		}
		else{
			SimLogger.printLine("Unknown device id! The simulation has been terminated.");
			System.exit(0);
		}
		
		return selectedVM;
	}

	@Override
	public void processEvent(SimEvent arg0) {
		// Nothing to do!
	}

	@Override
	public void shutdownEntity() {
		// Nothing to do!
	}

	@Override
	public void startEntity() {
		// Nothing to do!
	}

}