package edu.boun.edgecloudsim.edge_server;


import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.io.IOException;
import java.util.Iterator;
//import lombok.Getter;
//import lombok.extern.slf4j.Slf4j;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.deeplearning4j.gym.StepReply;
import org.deeplearning4j.rl4j.learning.Learning;
import org.deeplearning4j.rl4j.learning.sync.qlearning.QLearning;
import org.deeplearning4j.rl4j.learning.sync.qlearning.discrete.QLearningDiscreteDense;
import org.deeplearning4j.rl4j.mdp.MDP;
import org.deeplearning4j.rl4j.network.dqn.DQNFactoryStdDense;
import org.deeplearning4j.rl4j.policy.DQNPolicy;
import org.deeplearning4j.rl4j.space.ArrayObservationSpace;
import org.deeplearning4j.rl4j.space.DiscreteSpace;
import org.deeplearning4j.rl4j.space.ObservationSpace;
import org.deeplearning4j.rl4j.util.DataManager;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.learning.config.Adam;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.cloud_server.*;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.edge_client.*;

import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.*;


//@Slf4j
//@Getter
public class GameMDP implements MDP<GameState,Integer, DiscreteSpace> {
	
	private double reward;
	public int step;
	private GameState curState;	
	private DiscreteSpace actionSpace = new DiscreteSpace(SimSettings.getInstance().getNumOfEdgeHosts()*2);  //应以VM的总数目建立动作空间
	private ObservationSpace<GameState> observationSpace = new ArrayObservationSpace<>(new int[]{10});  //状态是二
	
	//新添加的成员变量
	MyEdgeServerManager edgeServerManager =(MyEdgeServerManager)SimManager.getInstance().getEdgeServerManager();
	DefaultCloudServerManager cloudServerManager = (DefaultCloudServerManager)SimManager.getInstance().getCloudServerManager();
	DefaultMobileDeviceManager mobileDeviceManager = (DefaultMobileDeviceManager)SimManager.getInstance().getMobileDeviceManager();
	public List<? extends Cloudlet> cloudletList = edgeServerManager.getCloudletList();
	
	public GameMDP(){
		
	}
	  
	@Override
	public void close() {
		reward = 0.0;
	}

	@Override
	public DiscreteSpace getActionSpace() {
		return actionSpace;
	}

	@Override
	public ObservationSpace<GameState> getObservationSpace() {
		return observationSpace;
	}
	
	GameState getCurState() {
		return curState;		
	}
	
	
	@Override
	public boolean isDone() { 
		
		if (curState.getTaskId()==-1){
	    //   SimLogger.printLine("Game Over, Steps of this epoch:{}+ "+ step);
	    //   SimLogger.printLine("reward= "+reward);
		   return true;
		}
		return false;	
	}
	

	@Override
	public MDP<GameState, Integer, DiscreteSpace> newInstance() {
		MDP<GameState, Integer, DiscreteSpace> ret = null;
		try {
		ret = new GameMDP();
		}catch(Exception e) {}
		return ret;
	}

	@Override
	public GameState reset(){    //这是创建第一个curState的方法，对curState的初始化应该放在这里。调用Learning.initMdp方法时调用，训练时也会调用
		
	//	SimLogger.printLine("cloudletList.size= "+cloudletList.size());		
		Task task = (Task)cloudletList.get(0); //第0个task
		Task taskNext = task;
	//	SimLogger.printLine("从第一个云任务开始：reset()");
		Vm vm=null;int i=0;
		
		//查找task所在的VM
		labelTwo:
		for( i=0;i<SimSettings.getInstance().getNumOfEdgeHosts();i++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
		  	Iterator<EdgeVM> iter = edgeServerManager.getVmList(i).iterator();
			while(iter.hasNext()){
			  vm =iter.next();				
			  if (task.getVmId()==vm.getId())
			    break labelTwo;
		    }
		}
		/*
		for(Cloudlet cloudlet:cloudletList){
			SimLogger.printLine("task.id= "+((Task)cloudlet).getCloudletId()+"  vm.id= "+((Task)cloudlet).getAssociatedVmId());
		}
		*/
		if (i==SimSettings.getInstance().getNumOfEdgeHosts()+SimSettings.getInstance().getNumOfCloudHost())  
			SimLogger.printLine("VM not found");
		
		//创建并初始化curState对象	
		double requiredVmCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(((EdgeVM)vm).getVmType());//task所在的vm具备的CPU能力
		double targetVmCapacity = (double)100-vm.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
		//初始化第一个状态，初始化为第0个云任务的信息
		this.curState = new GameState(task.getCloudletId(),taskNext.getCloudletId(),requiredVmCapacity,targetVmCapacity);
		this.reward = 0.0;
		this.step = 0;
	
		return curState;
	}

	@Override
	//步数在Q学习的配置中配置
	public StepReply<GameState> step(Integer action){  //该方法会在训练好模型后，执行最佳策略（playByStep方法）和初始化mdp（Learning.initMdp方法，且initMdp方法的第二个参数不为空)，以及训练的时候调用。action指第几台VM（包括边缘VM和云VM）
		
		curState.setTaskId(curState.getTaskNextId()); //设置新状态，相当于取下一个task
		//SimLogger.printLine("curState= "+curState.getTaskId());
	    //SimLogger.printLine("action= "+action);
		int taskId = curState.getTaskId();
		
		//在cloudletList上查找task
		Cloudlet cloudlet = null;
		Iterator<? extends Cloudlet> iter = cloudletList.iterator();
		labelOne:
		while(iter.hasNext()){
		  cloudlet = iter.next();				
		  if (taskId == cloudlet.getCloudletId())
		    break labelOne;    //跳出标号所表示的范围
	    }
		Vm srcVm=null;int i=0;
		Task task = (Task)cloudlet;   //找到task
		if (task.getStatus()!=4) { //还未执行结束
	        
		List<Datacenter> datacenterList = edgeServerManager.getDatacenterList();		
		//查找task所在的VM
		labelTwo:
		for( i=0;i<SimSettings.getInstance().getNumOfEdgeHosts();i++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
		  	Iterator<EdgeVM> iter2 = edgeServerManager.getVmList(i).iterator();
			while(iter2.hasNext()){
			  srcVm =iter2.next();				
			  if (task.getVmId()==srcVm.getId())
			    break labelTwo;
		    }
		}		
		if (i==SimSettings.getInstance().getNumOfEdgeHosts()+SimSettings.getInstance().getNumOfCloudHost())  
			SimLogger.printLine("VM not found");		
				
		//根据action，修改task的状态		
		EdgeHost host=null; Vm vm=null;
		if (action<SimSettings.getInstance().getNumOfEdgeVMs()){	  //action表示第几台VM
		  host = (EdgeHost)datacenterList.get(action/2).getHostList().get(0);
		  vm = host.getVmList().get(action%2);
		}else throw new RuntimeException("Out of Action Space");
		
	//	SimLogger.printLine("task的CPU需求=  "+((CpuUtilizationModel_Custom)(task.getUtilizationModelCpu())).predictUtilization(((EdgeVM)vm).getVmType()));
	//	SimLogger.printLine("目标VM的cpu能力=    "+(100-vm.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock())));
	//	SimLogger.printLine("VM之间是否有链接？=  "+(edgeServerManager.getEdge()[srcVm.getHost().getId()][vm.getHost().getId()]));
		int DataCenterIndex = 0;  //数据中心下标。边缘数据中心都用的是EdgeVmAllocationPolicy_Custom策略，所以随意指定一个数据中心用来获取EdgeVmAllocationPolicy_Custom策略
		List<? extends Host> hostList =  edgeServerManager.getDatacenterList().get(DataCenterIndex).getHostList();		
		EdgeVmAllocationPolicy_Custom policy = (EdgeVmAllocationPolicy_Custom)edgeServerManager.getVmAllocationPolicy(hostList,cloudletList, DataCenterIndex);
		//目标vm的能力能满足task的需求
		if (100-vm.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock())>((CpuUtilizationModel_Custom)(task.getUtilizationModelCpu())).predictUtilization(((EdgeVM)vm).getVmType()))		
		  //更新task为状态。task将移动到目标VM，并将它与新的vm、host、datacenter绑定
		  if (edgeServerManager.getEdge()[srcVm.getHost().getId()][vm.getHost().getId()]<Integer.MAX_VALUE&&srcVm!=vm&&srcVm!=null&&vm!=null){ //找到目标VM。要求：1.所在主机之间机有链接；2.源VM和目标VM不是同一个VM；3.源VM和目标VM不为空            
			//SimLogger.printLine("task "+task.getCloudletId()+" 将要移动到 vm= "+vm.getId());
			Cloudlet cl=null;				
			cl = srcVm.getCloudletScheduler().cloudletCancel(task.getCloudletId()); //task在源VM上撤销			
			int datacenterId  = mobileDeviceManager.getVmsToDatacentersMap().get(srcVm.getId());
 		//	SimLogger.printLine("srcVm= "+srcVm.getId()+"  host= "+srcVm.getHost().getId()+"  datacenterId= "+datacenterId);
	        Datacenter datacenter = edgeServerManager.getDatacenterList().get(datacenterId-5);//边缘数据中心的Id是从5到44开始的，原因未知.云数据中心是45
			if(cl!=null){  //对于已经执行结束的task，即状态为“Success”,执行不到这里
									    
			// prepare cloudlet for migration
			   //SimLogger.printLine("task  "+cl.getCloudletId()+" 在源VM上撤销成功");
			   task.setVmId(vm.getId());
			   // the cloudlet will migrate from one vm to another does the destination VM exist?
			   // time to transfer the files			  
		       double fileTransferTime = datacenter.predictFileTransferTime(cl.getRequiredFiles());//计算cloudlet的文件所需的传输时间，传输时间由源datacenter决定
		 	   double processTime = vm.getCloudletScheduler().cloudletSubmit(cl, fileTransferTime); //传输完成后由目标vm的云任务调度器提交cloudlet，核心工作是将cloudlet加入执行队列，处理时间由目标vm决定
		 	//   if (processTime > 0)
		 		  //SimLogger.printLine("task  "+cl.getCloudletId()+" 在目标VM上提交成功");
			   task.setAssociatedDatacenterId(vm.getHost().getDatacenter().getId());
			   //save related host id. task和主机绑定
			   task.setAssociatedHostId(vm.getHost().getId());				
			   //set related vm id task和vm绑定
			   task.setAssociatedVmId(vm.getId());
			   double cost = task.getCost();
			   cost += policy.getCost(task,(EdgeHost)srcVm.getHost(),(EdgeHost) vm.getHost()); //			   
			   task.setCost(cost); //设置成本
			   int migCount = task.getMigCount();
			   migCount++;
			   task.setMigCount(migCount);
			   //SimLogger.printLine("task.migCount=  "+task.getMigCount());
			   //SimLogger.printLine("task.cost=  "+task.getCost());
			   //bind task to related VM
			   mobileDeviceManager.bindCloudletToVm(task.getCloudletId(),vm.getId()); //云任务和VM绑定.与cl.setVmId(vm.getId())应该等效。
			   //设置新状态
			   curState.setRequiredVmCapacity(((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(((EdgeVM)vm).getVmType()));
			   curState.setTargetVmCapacity((double)100-vm.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock()));
			   reward -= policy.getCost(task,(EdgeHost)srcVm.getHost(),(EdgeHost)vm.getHost());  //更新回报，本质上是计算如果task迁移到目标vm上付出的成本是多少。如果不迁移，就不更新
			   
		    }		  				    
		}else { //源vm不变
			//设置新状态
			curState.setRequiredVmCapacity(((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(((EdgeVM)srcVm).getVmType()));
			curState.setTargetVmCapacity((double)100-srcVm.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock())+task.getUtilizationOfCpu(CloudSim.clock())); //应该计算task在srcVm上提交前的可用CPU利用率
		 }		
		}
		//taskNext指向下一个cloudlet
		for(int j=0;j<cloudletList.size();j++) 
		  if (task.getCloudletId()==cloudletList.get(j).getCloudletId()&&j!=cloudletList.size()-1) {
			curState.setTaskNextId(cloudletList.get(j+1).getCloudletId()); //设置新状态
			break;
		  }else if (j==cloudletList.size()-1)    //task已经是最后一个cloudlet
			curState.setTaskNextId(-1);	   //设置新状态
		//至此，curState已全部更新完毕			
		++step;
		
		return new StepReply<>(curState, reward, isDone(), null); //将MDP的下一个state和reward存入重放内存
			
	}
	

	

}

