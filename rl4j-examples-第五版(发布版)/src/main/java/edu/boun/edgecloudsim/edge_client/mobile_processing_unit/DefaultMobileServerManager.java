/*
 * Title:        EdgeCloudSim - Mobile Server Manager
 * 
 * Description: 
 * DefaultMobileServerManager is responsible for creating datacenters, hosts and VMs.
 *
 * Please note that the mobile processing units are simulated via
 * CloudSim. It is assumed that the mobile devices operate Hosts
 * and VMs like a server. That is why the class names are similar
 * to other Cloud and Edge components (to provide consistency).
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client.mobile_processing_unit;

import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.VmAllocationPolicy;


//该类用于支持任务的本地计算，创建并启动数据中心，建立vmList，任务交给数据中心，由数据中心为任务分配VM从而任务得到执行。现在该类未实现startDatacenters()和createVmList（）
//也未提供任何VM分配策略，所以目前不支持本地计算
public class DefaultMobileServerManager extends MobileServerManager{
	
	public DefaultMobileServerManager() {

	}

	@Override
	public void initialize() {
	}
	
	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> list, int dataCenterIndex) {
		return new MobileVmAllocationPolicy_Custom(list, dataCenterIndex);
	}

	@Override
	public void startDatacenters() throws Exception {
		//local computation is not supported in default Mobile Device Manager
	}

	@Override
	public void terminateDatacenters() {
		//local computation is not supported in default Mobile Device Manager
	}

	@Override
	public void createVmList(int brokerId) {
		//local computation is not supported in default Mobile Device Manager
	}

	@Override
	public double getAvgUtilization() {
		//local computation is not supported in default Mobile Device Manager
		return 0;
	}	
}
