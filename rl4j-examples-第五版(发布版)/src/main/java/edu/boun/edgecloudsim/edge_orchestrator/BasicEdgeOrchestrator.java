/*
 * Title:        EdgeCloudSim - Basic Edge Orchestrator implementation
 * 
 * Description: 
 * BasicEdgeOrchestrator implements basic algorithms which are
 * first/next/best/worst/random fit algorithms while assigning
 * requests to the edge devices.
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_orchestrator;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.cloud_server.CloudVM;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimUtils;

public class BasicEdgeOrchestrator extends EdgeOrchestrator {    //边缘协调器。实现了卸载策略，即task->vm的分配策略
	private int numberOfHost; //used by load balancer   //边缘主机的数目
	private int lastSelectedHostIndex; //used by load balancer
	private int[] lastSelectedVmIndexes; //used by each host individually
	
	public BasicEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();  //边缘主机的数目
		
		lastSelectedHostIndex = -1;
		lastSelectedVmIndexes = new int[numberOfHost];
		for(int i=0; i<numberOfHost; i++)
			lastSelectedVmIndexes[i] = -1;
	}
    
	//此函数由MobileDeviceManager调用，它会调用边缘协调器，由后者调用该函数。
	@Override  //将task卸载到云上还是边缘服务器上，随机确定
	public int getDeviceToOffload(Task task) {
		int result = SimSettings.GENERIC_EDGE_DEVICE_ID;  //默认是卸载到边缘服务器上
		if(!simScenario.equals("SINGLE_TIER")){  //单层场景，本函数目前只处理这一种场景
			//decide to use cloud or Edge VM
			int CloudVmPicker = SimUtils.getRandomNumber(0, 100);
			
			if(CloudVmPicker <= SimSettings.getInstance().getTaskLookUpTable()[task.getTaskType()][1])
				result = SimSettings.CLOUD_DATACENTER_ID;     //卸载到云上
			else
				result = SimSettings.GENERIC_EDGE_DEVICE_ID;    //卸载到边缘服务器上
		}
		
		return result;
	}
	
	//为task找到能够满足执行需求的VM进行卸载,或在云数据中心，或在边缘服务器，或在接入点上
	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		//在云数据中心的所有主机当中为task选择能够满足执行需求的最大的VM  (WorstFit)
		if(deviceId == SimSettings.CLOUD_DATACENTER_ID){  //卸载到云上
			//Select VM on cloud devices via Least Loaded algorithm!
			double selectedVmCapacity = 0; //start with min value
			//在云数据中新的所有主机中选择满足task执行需求的最大的VM   WorstFit
			List<Host> list = SimManager.getInstance().getCloudServerManager().getDatacenter().getHostList();
			for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
				List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());//task如果卸载到下标为vmIndex的VM上，该VM所需要的CPU能力。这里，VmType就是CLOUD_VM，task卸载到不同类型的VM上，VM所需的能力是不一样的，所以需要查表获知。
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());//下标为vmIndex的vm当前可用的CPU能力
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){//1.目标vm的可用能力能够满足具体类型的vm的需要 2.选择策略采用WORST_FIT
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
	            }
			}
		}
		else if(simScenario.equals("TWO_TIER_WITH_EO"))  //两层且带有协调器的场景
			selectedVM = selectVmOnLoadBalancer(task);   //在边缘服务器上，按照一定的策略为task选择能够满足执行需求的VM
		else
			selectedVM = selectVmOnHost(task);    //卸载到边缘。根据产生任务的设备的当前物理位置，找到覆盖范围内的唯一的边缘服务器，在其上选择能够满足执行需求的VM
		
		return selectedVM;
	}
	
	//根据当前位置，找到覆盖范围内的唯一台主机，在其上查找可以满足task执行需求的VM。自定义的卸载策略，可以添加到此函数，作为卸载策略的一种。
	public EdgeVM selectVmOnHost(Task task){
		EdgeVM selectedVM = null;
		//获取产生task的设备在当前时刻的位置
		Location deviceLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
		//in our scenasrio, serving wlan ID is equal to the host id
		//because there is only one host in one place  一个位置只有一台主机
		int relatedHostId=deviceLocation.getServingWlanId();   
		List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(relatedHostId);
		
		if(policy.equalsIgnoreCase("RANDOM_FIT")){
			//随机地找到一台VM
			int randomIndex = SimUtils.getRandomNumber(0, vmArray.size()-1);
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(randomIndex).getVmType());
			double targetVmCapacity = (double)100 - vmArray.get(randomIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			if(requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(randomIndex);
		}
		else if(policy.equalsIgnoreCase("WORST_FIT")){
			double selectedVmCapacity = 0; //start with min value
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					selectedVmCapacity = targetVmCapacity;
				}
			}
		}
		else if(policy.equalsIgnoreCase("BEST_FIT")){
			double selectedVmCapacity = 101; //start with max value
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity && targetVmCapacity < selectedVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					selectedVmCapacity = targetVmCapacity;
				}
			}
		}
		else if(policy.equalsIgnoreCase("FIRST_FIT")){
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					break;
				}
			}
		}
		else if(policy.equalsIgnoreCase("NEXT_FIT")){
			int tries = 0;
			while(tries < vmArray.size()){
				lastSelectedVmIndexes[relatedHostId] = (lastSelectedVmIndexes[relatedHostId]+1) % vmArray.size();
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(lastSelectedVmIndexes[relatedHostId]).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(lastSelectedVmIndexes[relatedHostId]).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity){
					selectedVM = vmArray.get(lastSelectedVmIndexes[relatedHostId]);
					break;
				}
				tries++;
			}
		}
		
		return selectedVM;
	}
	
	
    //在所有边缘服务器上为task选择能够满足执行需求的VM，不仅限于移动设备就近的边缘服务器。自定义的卸载策略，可以添加到此函数，作为卸载策略的一种。
	public EdgeVM selectVmOnLoadBalancer(Task task){
		EdgeVM selectedVM = null;
		
		if(policy.equalsIgnoreCase("RANDOM_FIT")){
			//随机找一台host
			int randomHostIndex = SimUtils.getRandomNumber(0, numberOfHost-1);
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(randomHostIndex);
			//随机找一台VM
			int randomIndex = SimUtils.getRandomNumber(0, vmArray.size()-1);
			//task所需的VM需要的CPU能力
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(randomIndex).getVmType());//这里，VmType就是EDGE_VM
			//目标VM剩余的CPU能力
			double targetVmCapacity = (double)100 - vmArray.get(randomIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			if(requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(randomIndex);
		}
		else if(policy.equalsIgnoreCase("WORST_FIT")){//找到满足容量要求的最大的VM
			double selectedVmCapacity = 0; //start with min value
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());//得到task所需的VM的利用率
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());//目标VM在当前时刻的可用CPU能力
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
				}
			}
		}
		else if(policy.equalsIgnoreCase("BEST_FIT")){//找到满足容量要求的最小的VM
			double selectedVmCapacity = 101; //start with max value。利用率最大不会超过100
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity < selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
				}
			}
		}
		else if(policy.equalsIgnoreCase("FIRST_FIT")){
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						break;
					}
				}
			}
		}
		else if(policy.equalsIgnoreCase("NEXT_FIT")){
			int hostCheckCounter = 0;	
			while(selectedVM == null && hostCheckCounter < numberOfHost){
				int tries = 0;
				lastSelectedHostIndex = (lastSelectedHostIndex+1) % numberOfHost;

				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(lastSelectedHostIndex);
				while(tries < vmArray.size()){
					lastSelectedVmIndexes[lastSelectedHostIndex] = (lastSelectedVmIndexes[lastSelectedHostIndex]+1) % vmArray.size();
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(lastSelectedVmIndexes[lastSelectedHostIndex]).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(lastSelectedVmIndexes[lastSelectedHostIndex]).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity){
						selectedVM = vmArray.get(lastSelectedVmIndexes[lastSelectedHostIndex]);
						break;
					}
					tries++;
				}

				hostCheckCounter++;
			}
		}
		
		return selectedVM;
	}

	@Override
	public void processEvent(SimEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void shutdownEntity() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startEntity() {
		// TODO Auto-generated method stub
		
	}
}