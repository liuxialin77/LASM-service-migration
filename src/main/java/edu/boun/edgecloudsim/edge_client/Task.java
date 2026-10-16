/*
 * Title:        EdgeCloudSim - Task
 * 
 * Description: 
 * Task adds app type, task submission location, mobile device id and host id
 * information to CloudSim's Cloudlet class.
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.utils.Location;

public class Task extends Cloudlet {
	private Location submittedLocation;   //提交位置
	private double creationTime;  //创建时间
	private int type;           //应用类型
	private int mobileDeviceId;  //所在的移动设备
	private int hostIndex;    //所在的host，即接入host
	private int vmIndex;     //为之分配的VM
	private int datacenterId;   //所属的数据中心
	//以下是新添加的成员
	boolean  isDirectInteracted;  //是否和用户直接交互
	private int servingHostIndex;  //服务host
	double cost;   //迁移成本
	int migCount;  //迁移数目

	public Task(int _mobileDeviceId, int cloudletId, long cloudletLength, int pesNumber,
			long cloudletFileSize, long cloudletOutputSize,
			UtilizationModel utilizationModelCpu,
			UtilizationModel utilizationModelRam,
			UtilizationModel utilizationModelBw) {
		super(cloudletId, cloudletLength, pesNumber, cloudletFileSize,
				cloudletOutputSize, utilizationModelCpu, utilizationModelRam,
				utilizationModelBw);
		
		mobileDeviceId = _mobileDeviceId;
		creationTime = CloudSim.clock();
	}

	
	public void setSubmittedLocation(Location _submittedLocation){
		submittedLocation =_submittedLocation;
	}

	public void setAssociatedDatacenterId(int _datacenterId){
		datacenterId=_datacenterId;
	}
	
	public void setAssociatedHostId(int _hostIndex){
		hostIndex=_hostIndex;
	}

	public void setAssociatedVmId(int _vmIndex){
		vmIndex=_vmIndex;
	}
	
	public void setTaskType(int _type){
		type=_type;
	}

	public int getMobileDeviceId(){
		return mobileDeviceId;
	}
	
	public Location getSubmittedLocation(){
		return submittedLocation;
	}
	
	public int getAssociatedDatacenterId(){
		return datacenterId;
	}
	
	public int getAssociatedHostId(){
		return hostIndex;
	}

	public int getAssociatedVmId(){
		return vmIndex;
	}
	
	public int getTaskType(){
		return type;
	}
	
	public double getCreationTime() {
		return creationTime;
	}
	
	public double getCost() {
		return cost;
	}
	
	public double setCost(double cost) {
		return this.cost = cost;
	}
	
	public int getMigCount() {
		return migCount;
	}
	
	public void setMigCount(int migCount) {
		this.migCount = migCount;
	}
	
}
