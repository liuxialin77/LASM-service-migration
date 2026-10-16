package edu.boun.edgecloudsim.edge_server;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Host;

//import lombok.Getter;
//import lombok.Setter;

import org.deeplearning4j.rl4j.space.Encodable;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.*;

//@Getter
//@Setter
public class GameState implements Encodable{
	
	EdgeServerManager edgeServerManager = SimManager.getInstance().getEdgeServerManager();		
	public List<? extends Cloudlet> cloudletList = edgeServerManager.getCloudletList();

//	private Task task;               
//	private Task taskNext;
	//这些状态与task/vm的迁移相关
	int taskId;  //任务编号	
	int taskNextId;   //下一任务的编号
	double requiredVmCapacity;//task所需VM的需要具备的能力
	double targetVmCapacity; //目标VM具有的可用的能力
	/*	
	//VM的需求
	double reqMaxMips;     
	double reqTotalMips;
	double reqRam;
	double reqBw;
	*/
	
	
	/*
	public GameState(int taskId,int taskNextId,double hostPeCapacity,  //源主机的状态
	       double hostMips,
	       double hostRam,
	       double hostBw,
	       double reqMaxMips,     //task对应的vm的状态
	       double reqTotalMips,
	       double reqRam,
	       double reqBw){
		this.taskId = taskId;  this.taskNextId = taskNextId;
		this.hostPeCapacity = reqMaxMips; this.hostMips = hostMips;   this.hostRam = hostRam;	this.hostBw =hostBw;
		this.reqMaxMips =reqMaxMips; this.reqTotalMips = reqTotalMips; this.reqRam = reqRam;    this.reqBw =reqBw; 
	} */
	public GameState(int taskId,int taskNextId,double requiredVmCapacity,double targetVmCapacity) {
		this.taskId = taskId;  this.taskNextId = taskNextId;
		this.requiredVmCapacity = requiredVmCapacity;
		this.targetVmCapacity = requiredVmCapacity;
	}
	
	@Override
	public double[] toArray() {  
		double[] ret = new double[10];
		ret[0] = taskId;
		ret[1] = taskNextId;
		ret[2] = requiredVmCapacity;
		ret[3] = targetVmCapacity;
		/*
		ret[2] = hostPeCapacity;   
		ret[3] = hostMips;
		ret[4] = hostRam;
		ret[5] = hostBw;
		ret[6] = reqMaxMips;
		ret[7] = reqTotalMips;
		ret[8] = reqRam;
		ret[9] = reqBw;
		*/		
		return ret;
	}
	
	public int getTaskId(){
		   return taskId;
	}	
		
	public void setTaskId(int taskId){
		this.taskId = taskId;
	}
	
	public int getTaskNextId(){
		   return taskNextId;
	}	
	
	public void setTaskNextId(int taskNextId){
		this.taskNextId = taskNextId;
	}
	
	public double getRequiredVmCapacity(){
		   return requiredVmCapacity;
	}	
	
	public void setRequiredVmCapacity(double requiredVmCapacity){
		this.requiredVmCapacity = requiredVmCapacity;
	}
	
	public double getTargetVmCapacity(){
		   return targetVmCapacity;
	}	
	
	public void setTargetVmCapacity(double targetVmCapacity){
		this.targetVmCapacity = targetVmCapacity;
	}
	/*
	public double getHostPeCapacity(){
		   return hostPeCapacity;
	}	
		
	public void setHostPeCapacity(double hostPeCapacity){
		this.hostPeCapacity = hostPeCapacity;
	}
	
	public double getHostMips(){
		   return hostMips;
	}	
		
	public void setHostMips(double hostMips){
		this.hostMips = hostMips;
	}
	
	public double getHostRam(){
		   return hostRam;
	}	
		
	public void setHostRam(double hostRam){
		this.hostRam = hostRam;
	}
	
	public double getHostBw(){
		   return hostBw;
	}	
		
	public void setHostBw(double hostBw){
		this.hostBw = hostBw;
	}
	
	public double getReqMaxMips(){
		   return reqMaxMips;
	}	
		
	public void setReqMaxMips(double reqMaxMips){
		this.reqMaxMips = reqMaxMips;		
	}
	
	public double getReqTotalMips(){
		   return reqTotalMips;
	}	
		
	public void setReqTotalMips(double reqTotalMips){
		this.reqTotalMips = reqTotalMips;		
	}
	
	public double getReqRam(){
		   return reqRam;
	}	
		
	public void setReqRam(double reqRam){
		this.reqRam = reqRam;		
	}
	
	public double getReqBw(){
		   return reqBw;
	}	
		
	public void setReqBw(double reqBw){
		this.reqBw = reqBw;		
	}
	*/
	
	
	
	
	
	
	
	

}
