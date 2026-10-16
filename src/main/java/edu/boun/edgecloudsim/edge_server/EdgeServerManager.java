/*
 * Title:        EdgeCloudSim - EdgeServerManager
 * 
 * Description: 
 * EdgeServerManager is responsible for creating and terminating
 * the edge datacenters which operates the hosts and VMs.
 * It also provides the list of VMs running on the hosts and
 * the average utilization of all VMs.
 *
 * Please note that, EdgeCloudSim is built on top of CloudSim
 * Therefore, all the computational units are handled by CloudSim
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_server;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.VmAllocationPolicy;

public abstract class EdgeServerManager {
	protected List<Datacenter> localDatacenters;  //所有的数据中心。没有hostList成员，如果需要，可通过此成员获取
	protected List<List<EdgeVM>> vmList; // 所有host的VM都放在这个列表上，第一台host的VMs就是第一个List<EdgeVM>，依次类推
	//新添加的成员,在SimManager对象的构造方法中对其进行了传入
	public List<? extends Cloudlet> cloudletList;	
	
	public EdgeServerManager() {
		localDatacenters=new ArrayList<Datacenter>();
		vmList = new ArrayList<List<EdgeVM>>();
	}

	public List<EdgeVM> getVmList(int hostId){
		return vmList.get(hostId);
	}
	
	public List<Datacenter> getDatacenterList(){
		return localDatacenters;
	}
	
	/*
	 * initialize edge server manager if needed
	 */
	public abstract void initialize();

	/*
	 * provides abstract Vm Allocation Policy for Edge Datacenters
	 *///原版
	//public abstract VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex);
	//
	public abstract VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list,List<? extends Cloudlet> cloudletList, int dataCenterIndex);
	/*
	 * Starts Datacenters
	 */
	public abstract void startDatacenters() throws Exception;
	
	/*
	 * Terminates Datacenters
	 */
	public abstract void terminateDatacenters();
	/*
	 * Creates VM List
	 */
	public abstract void createVmList(int brokerId);
	
	/*
	 * returns average utilization of all VMs
	 */
	public abstract double getAvgUtilization();
	public abstract double dist(int start,int end);//新添加的方法
	
	public <T extends Cloudlet> List<T> getCloudletList() {
		return (List<T>) cloudletList;
	}
	
	
}