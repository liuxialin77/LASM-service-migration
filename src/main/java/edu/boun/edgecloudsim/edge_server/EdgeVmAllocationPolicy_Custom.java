/*
 * Title:        EdgeCloudSim - Custom Vm Allocation Policy for Edge VMs
 * 
 * Description: 
 * VmAllocationPolicy_Custom implements VmAllocationPolicy to decide which.
 * VM is created on which host located on the datacenters. For those
 * who wants to add another Vm Allocation Policy to EdgeCloudSim should
 * provide another concrete instance of VmAllocationPolicy via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.util.ExecutionTimeMeasurer;
import org.deeplearning4j.gym.StepReply;
import org.deeplearning4j.rl4j.learning.Learning;
import org.deeplearning4j.rl4j.learning.sync.qlearning.QLearning;
import org.deeplearning4j.rl4j.learning.sync.qlearning.discrete.QLearningDiscreteDense;
import org.deeplearning4j.rl4j.network.dqn.DQNFactoryStdDense;
import org.deeplearning4j.rl4j.policy.DQNPolicy;
import org.deeplearning4j.rl4j.util.DataManager;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.nativeblas.Nd4jCpu.double_absolute_difference_loss;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

//import cn.live.wangongxi.GameState;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.DefaultMobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.DefaultEdgeServerManager.Edge;
import edu.boun.edgecloudsim.edge_server.*;
import edu.boun.edgecloudsim.network.*;

/*
 * Same as VmAllocationPolicySimple.  VmAllocationPolicySimple is an VmAllocationPolicy that chooses, as the host for a VM, the host
 * with less PEs in use. It is therefore a Worst Fit policy, allocating VMs into the 
 * host with most available PE.
 */
public class EdgeVmAllocationPolicy_Custom extends VmAllocationPolicy {
	/** The vm table. */
	private Map<String, Host> vmTable; //vm->pm的映射表 
	private static int createdVmNum;  //已分配好的vm数目
	private int DataCenterIndex;  //数据中心下标
	//以下是新添加的成员变量
//	DefaultEdgeServerManager edgeServerManager =(DefaultEdgeServerManager)SimManager.getInstance().getEdgeServerManager();
	MyEdgeServerManager edgeServerManager =(MyEdgeServerManager)SimManager.getInstance().getEdgeServerManager();
	public List<? extends Cloudlet> cloudletList = edgeServerManager.getCloudletList();
	DefaultMobileDeviceManager mobileDeviceManager = (DefaultMobileDeviceManager)SimManager.getInstance().getMobileDeviceManager();
	private final List<Map<String, Object>> savedAllocation = new ArrayList<Map<String, Object>>();	
	//建立更多的服务
	ArrayList<ArrayList<Task>>  svcCbrList = new ArrayList<ArrayList<Task>>();
	ArrayList<ArrayList<Task>>  svcOnList = new ArrayList<ArrayList<Task>>() ;
	ArrayList<ArrayList<Task>>  svcPossList = new ArrayList<ArrayList<Task>>();
	ArrayList<ArrayList<Task>>  svcSelfList = new ArrayList<ArrayList<Task>>();
	int [][] directLink = new int [4][11];  //记录每个服务的直连节点，共44个服务
	//int  migCount; //迁移数
	//原版
	/*
	public EdgeVmAllocationPolicy_Custom(List<? extends Host> list,int _DataCenterIndex) {
		super(list);		
		setVmTable(new HashMap<String, Host>());
		DataCenterIndex=_DataCenterIndex;
		createdVmNum = 0;
	}
	*/
	//编号为_DataCenterIndex的数据中心的所有主机hostList使用的VM分配策略
	public EdgeVmAllocationPolicy_Custom(List<? extends Host> hostList,List<? extends Cloudlet> cloudletList, int _DataCenterIndex) {
		super(hostList);
		this.cloudletList =cloudletList;
		setVmTable(new HashMap<String, Host>());
		DataCenterIndex=_DataCenterIndex;		
		createdVmNum = 0;
		svcCbrList.add(mobileDeviceManager.getSvcCbr()); //原来的svcCbr作为第一个CBR服务,下同
		svcOnList.add(mobileDeviceManager.getSvcOn());
		svcPossList.add(mobileDeviceManager.getSvcPoss());    
		svcSelfList.add(mobileDeviceManager.getSvcSelf());   		
		
		for(int i=1;i<11;i++) {   //为每种类型初始化其它的9个服务.注意：每种类型多添加了1个服务是因为防止下面的for循环中svcCbrList.get(i+1)越界 		  
		  svcCbrList.add(new ArrayList<Task>());
		  svcOnList.add(new ArrayList<Task>());
		  svcPossList.add(new ArrayList<Task>());
		  svcSelfList.add(new ArrayList<Task>());
		}	
		
		Iterator<ArrayList<Task>>  iter = svcCbrList.iterator();    	
    	int index = 0;
    	while(iter.hasNext()) {
    		iter.next();
    		int u = (int)(Math.random()*10);   //为每个CBR服务生成直连节点并记录下来，下同
    		directLink[0][index] = u;
    		index++;
    	}
    	
    	 iter = svcOnList.iterator();
         index = 0;
     	while(iter.hasNext()) {
     		iter.next();
     		int u = (int)(Math.random()*10);   //为每个CBR服务生成直连节点并记录下来，下同
     		directLink[1][index] = u;
     		index++;
     	}
     	
     	iter = svcPossList.iterator();        
        index = 0;
    	while(iter.hasNext()) {
    		iter.next();
    		int u = (int)(Math.random()*10);   //为每个CBR服务生成直连节点并记录下来，下同
    		directLink[2][index] = u;
    		index++;
    	}
    	
    	iter = svcSelfList.iterator();        
        index = 0;
    	while(iter.hasNext()) {
    		iter.next();
    		int u = (int)(Math.random()*10);   //为每个CBR服务生成直连节点并记录下来，下同
    		directLink[3][index] = u;
    		index++;
    	}
	}
    
	
	//按照边缘设备文档中的VM的特征，在主机上创建一个vm。注意：不是寻找目标主机，不需要任何分配策略,VM->Host的映射是文档中配置好的。所以本函数只在最开始调用了一次	
	@Override
	public boolean allocateHostForVm(Vm vm) {
		boolean result = false;	
		
		if (!getVmTable().containsKey(vm.getUid()) && vm instanceof EdgeVM) { // if this vm was not created
			boolean vmFound = false;
			int vmCounter = 0;
			int hostIndex = 0;
			int dataCenterIndex = 0;
			
			//find proper datacenter id and host id for this VM
			Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
			NodeList datacenterList = doc.getElementsByTagName("datacenter");
			for (int i = 0; (!vmFound && i < datacenterList.getLength()); i++) {  //遍历数据中心
				Node datacenterNode = datacenterList.item(i);
				Element datacenterElement = (Element) datacenterNode;
				NodeList hostNodeList = datacenterElement.getElementsByTagName("host");
				for (int j = 0; (!vmFound  && j < hostNodeList.getLength()); j++) {  //遍历主机列表
					Node hostNode = hostNodeList.item(j);
					Element hostElement = (Element) hostNode;
					NodeList vmNodeList = hostElement.getElementsByTagName("VM");
					for (int k = 0; (!vmFound && k < vmNodeList.getLength()); k++) {  //遍历VM列表

						if(vmCounter == vm.getId()){    //vm的id从0开始编号;找到了  
							dataCenterIndex = i;
							hostIndex = j;  //找到了将要创建vm的host
							vmFound = true;
						}
						vmCounter++;
					}
				}
			}

			//注意： hostList是父类VmAllocationPolicy的成员变量。数据中心下标比较是为了确认使用的VM分配策略是指定的数据中心的分配策略
			if(vmFound && dataCenterIndex == DataCenterIndex && hostIndex < getHostList().size()){ //还有可供分配的host；dataCenterIndex == DataCenterIndex说明该数据中心是指定的数据中心
				Host host = getHostList().get(hostIndex);
				result = host.vmCreate(vm);  //在host上创建vm
	
				if (result) { // if vm were successfully created in the host
					getVmTable().put(vm.getUid(), host);//在vmTable中修改vm->host的映射   
					createdVmNum++;   //已分配的vm数目加1
					Log.formatLine("%.2f: Edge VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),CloudSim.clock());
		//			SimLogger.printLine("a Edge VM is allocated!");  //会输出
					result = true;
				}
			}
		}
		
		return result;
	}

	@Override
	public boolean allocateHostForVm(Vm vm, Host host) {
		SimLogger.printLine("a vm is allocated!");
		if (host.vmCreate(vm)) { // if vm has been successfully created in the host
			getVmTable().put(vm.getUid(), host);
			createdVmNum++;			
			Log.formatLine("%.2f: Edge VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),CloudSim.clock());
			
			return true;
		}
		
		return false;
	}
	/*
	//原版本。即非迁移策略
	@Override
	public List<Map<String, Object>> optimizeAllocation(
			List<? extends Vm> vmList){
		return null;
	}
	*/
	
	/*
	//就近迁移策略
	@Override
	public List<Map<String, Object>> optimizeAllocation(
			List<? extends Vm> vmList){		
		Cloudlet cloudlet = null;
		if(cloudletList.size()>4500&&cloudletList.size()<5500){
			
		  Iterator<? extends Cloudlet> iter = cloudletList.iterator();		  
		  while(iter.hasNext()){
			cloudlet = iter.next();
			Task task = (Task)cloudlet;			
			if (cloudlet.getStatus()==4)
			  continue;
			
			//SimLogger.printLine("task is in execute!");
			//查找task所在的VM
			Vm srcVm = null; int i = 0;
			labelOne:
			for(i=0;i<SimSettings.getInstance().getNumOfEdgeHosts();i++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
			  	Iterator<EdgeVM> iter2 = edgeServerManager.getVmList(i).iterator();
				while(iter2.hasNext()){
				  srcVm =iter2.next();				
				  if (task.getVmId()==srcVm.getId())
				    break labelOne;
			    }
			}
			if (i==SimSettings.getInstance().getNumOfEdgeHosts()+SimSettings.getInstance().getNumOfCloudHost())  
				SimLogger.printLine("VM not found");	
			
			Location loc = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock());//根据设备所在的位置获取接入点
			
			//对所有主机按照与设备的当前位置loc的距离进行排序
			List<Datacenter> localDataCenters = edgeServerManager.getDatacenterList();			
			Iterator<Datacenter> iter3 = localDataCenters.iterator();  //准备遍历边缘数据中心	
			HashMap<Integer,Double> distanceMap = new HashMap<Integer,Double>();
			List<Integer> hostIdList = new ArrayList<Integer>();
			while(iter3.hasNext()) {   //将host和距离的映射加入Map			
				Datacenter datacenter= iter3.next();
				EdgeHost host = (EdgeHost) datacenter.getHostList().get(0);
				double x = loc.getXPos()-host.getLocation().getXPos();
				double y = loc.getYPos()-host.getLocation().getYPos();			
				distanceMap.put(host.getId(),Math.sqrt(Math.pow(x,2)+Math.pow(y,2)));
				hostIdList.add(host.getId());
			}		
		    hostIdList.sort(Comparator.comparingDouble(distanceMap::get));//注意：这里用hostID代表主机	，这个写法模仿的是deepseek给出的排序写法，升序排列。
		    
		    //如果hostIdList上的某个主机能满足task_i的执行要求，将task_i迁移到该主机的VM上
		    Iterator<Integer>  iter4 = hostIdList.iterator();  //准备遍历hostIdList
		    labelTwo:
		    while(iter4.hasNext()) {
		    	Integer hostId = iter4.next();
		    	EdgeHost host = (EdgeHost) localDataCenters.get(hostId).getHostList().get(0);
		    	for(i=0;i<2;i++) {   
		    	   Vm vm = host.getVmList().get(i);
		    	   if (100-vm.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock())>((CpuUtilizationModel_Custom)(task.getUtilizationModelCpu())).predictUtilization(((EdgeVM)vm).getVmType()))
		    		 if (edgeServerManager.getEdge()[srcVm.getHost().getId()][vm.getHost().getId()]<Integer.MAX_VALUE&&srcVm!=vm&&srcVm!=null&&vm!=null) {
		    			    //SimLogger.printLine("task "+task.getCloudletId()+" 将要移动到 vm= "+vm.getId());	    				
		    				Cloudlet cl=null;				
		    				cl = srcVm.getCloudletScheduler().cloudletCancel(task.getCloudletId()); //task在源VM上撤销			
		    				int datacenterId  = mobileDeviceManager.getVmsToDatacentersMap().get(srcVm.getId());	    	 		
		    		        Datacenter datacenter = edgeServerManager.getDatacenterList().get(datacenterId-5);
		    				if(cl!=null){  //task_i撤销成功									    	    				
		    				   //SimLogger.printLine("task  "+cl.getCloudletId()+" 在源VM上撤销成功");
		    				   task.setVmId(vm.getId());   //task_i的宿主vm设置为新VM,下同
		    				   // the cloudlet will migrate from one vm to another does the destination VM exist?
		    				   // time to transfer the files			  
		    			       double fileTransferTime = datacenter.predictFileTransferTime(cl.getRequiredFiles());//计算cloudlet的文件所需的传输时间，传输时间由源datacenter决定
		    			 	   double processTime = vm.getCloudletScheduler().cloudletSubmit(cl, fileTransferTime); //传输完成后由目标vm的云任务调度器提交cloudlet，核心工作是将cloudlet加入执行队列，处理时间由目标vm决定
	    	    			   //if (processTime > 0)
		    			 	   //SimLogger.printLine("task  "+cl.getCloudletId()+" 在目标VM上提交成功");
		    				   task.setAssociatedDatacenterId(vm.getHost().getDatacenter().getId());	    				 
		    				   task.setAssociatedHostId(vm.getHost().getId());	    			
		    				   task.setAssociatedVmId(vm.getId());
		    				   double cost = task.getCost();
		    				   cost += getCost(task, (EdgeHost)srcVm.getHost(), (EdgeHost)vm.getHost()); 
		    				   task.setCost(cost); //设置成本
		    				   mobileDeviceManager.bindCloudletToVm(task.getCloudletId(),vm.getId()); //云任务和VM绑定.与cl.setVmId(vm.getId())应该等效	    				  
		    				}
		    	            break labelTwo;
		    		 }	    	   
		    	}
		    }
		  }
		    	
		}		
				
		return null;
	}
	*/
	
	/*
	//最低负载策略
	@Override
	public List<Map<String, Object>> optimizeAllocation(
			List<? extends Vm> vmList){		
		Cloudlet cloudlet = null;
		if(cloudletList.size()>4500&&cloudletList.size()<5500){
			
		  Iterator<? extends Cloudlet> iter = cloudletList.iterator();		  
		  while(iter.hasNext()){
			cloudlet = iter.next();
			Task task = (Task)cloudlet;			
			if (cloudlet.getStatus()==4) //task已执行完
			  continue;
			
			//SimLogger.printLine("task is in execute!");
			//查找task所在的VM
			Vm srcVm = null; int i = 0;
			labelOne:
			for(i=0;i<SimSettings.getInstance().getNumOfEdgeHosts();i++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
			  	Iterator<EdgeVM> iter2 = edgeServerManager.getVmList(i).iterator();
				while(iter2.hasNext()){
				  srcVm =iter2.next();				
				  if (task.getVmId()==srcVm.getId())
				    break labelOne;
			    }
			}
			if (i==SimSettings.getInstance().getNumOfEdgeHosts()+SimSettings.getInstance().getNumOfCloudHost())  
				SimLogger.printLine("VM not found");			
		    
			//在所有边缘数据中心上查找负载最低的主机
		    List<Datacenter> localDataCenters = edgeServerManager.getDatacenterList();
		    Iterator<Datacenter> iter3 = localDataCenters.iterator();  //准备遍历边缘数据中心
		    
		    //如果localDataCenters中的某个主机能满足task_i的执行要求，将task_i迁移到该主机的VM上
		    double minUtilizationOfCpu = Double.MAX_VALUE; 
		    Vm minVm = null;	    
		   
			 		  
		    
		    while(iter3.hasNext()) {  //查找负载最低的并且容量能满足task的VM
		    	
		    	EdgeHost host = (EdgeHost) iter3.next().getHostList().get(0);
		    	for(i=0;i<2;i++) {   
		    	   Vm vm = host.getVmList().get(i);		    	   
		    	   if (100-vm.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock())>((CpuUtilizationModel_Custom)(task.getUtilizationModelCpu())).predictUtilization(((EdgeVM)vm).getVmType())) {
		    		 double availableUtilization =  100-vm.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
		    		 if (availableUtilization < minUtilizationOfCpu){
                        minUtilizationOfCpu = availableUtilization;
                        minVm = vm;		    		   
		    	     }
		    	   }
		       }//for
		    }//while
		    
		    if (edgeServerManager.getEdge()[srcVm.getHost().getId()][minVm.getHost().getId()]<Integer.MAX_VALUE&&srcVm!=minVm&&srcVm!=null&&minVm!=null) {
		        //SimLogger.printLine("task "+task.getCloudletId()+" 将要移动到 vm= "+vm.getId());	    				
		    	Cloudlet cl=null;				
		    	cl = srcVm.getCloudletScheduler().cloudletCancel(task.getCloudletId()); //task在源VM上撤销			
		    	int datacenterId  = mobileDeviceManager.getVmsToDatacentersMap().get(srcVm.getId());	    	 		
		        Datacenter datacenter = edgeServerManager.getDatacenterList().get(datacenterId-5);
		      	if(cl!=null){  //task_i撤销成功									    	    				
		    	   //SimLogger.printLine("task  "+cl.getCloudletId()+" 在源VM上撤销成功");
		    	   task.setVmId(minVm.getId());   //task_i的宿主vm设置为新VM,下同
		    	   // the cloudlet will migrate from one vm to another does the destination VM exist?
		    	   // time to transfer the files			  
		    	   double fileTransferTime = datacenter.predictFileTransferTime(cl.getRequiredFiles());//计算cloudlet的文件所需的传输时间，传输时间由源datacenter决定
		    	   double processTime = minVm.getCloudletScheduler().cloudletSubmit(cl, fileTransferTime); //传输完成后由目标vm的云任务调度器提交cloudlet，核心工作是将cloudlet加入执行队列，处理时间由目标vm决定
	    	       //if (processTime > 0)
		    		   //SimLogger.printLine("task  "+cl.getCloudletId()+" 在目标VM上提交成功");
		    		   task.setAssociatedDatacenterId(minVm.getHost().getDatacenter().getId());	    				 
		    		   task.setAssociatedHostId(minVm.getHost().getId());	    			
		    		   task.setAssociatedVmId(minVm.getId());
		    		   double cost = task.getCost();
		    		   cost += getCost(task, (EdgeHost)srcVm.getHost(), (EdgeHost)minVm.getHost()); 
		    		   task.setCost(cost); //设置成本
		    		   mobileDeviceManager.bindCloudletToVm(task.getCloudletId(),minVm.getId()); //云任务和VM绑定.与cl.setVmId(vm.getId())应该等效	    				  
		    		}
		       }	    	   
		    	
		    } //while
		  
		    	
		}//if		
				
		return null;
	}
		
	*/
	
	
	
	//DRL策略。该策略的执行时间比DRL+姻亲策略的长
	public List<Map<String, Object>> optimizeAllocation(
			List<? extends Vm> vmList){
       if(cloudletList.size()>4500&&cloudletList.size()<5500){ 	    		
    		
    	  //执行最佳策略。game.policy在哪里体现？对每一个需要迁移的cloudlet，会给出action，然后转换成目标VM
    	  GameMDP mdp = initMDP();  //创建一个空的GameMDP
    	  Learning.InitMdp<GameState> initMdp;  //InitMdp<GameState>是内部类
    	  initMdp = Learning.initMdp(mdp, null); //initMdp是方法.首先调用reset方法,从第0个cloudlet开始；如果第二个参数不为空，则利用无操作动作（no-op action）把mdp跑一遍，计算累计奖励和最后一个状态，返回包括总步数、累计奖励和最后一个状态在内的mdp初始状态；否则，不进行这种"热身")
    	  GameState initState = initMdp.getLastObs(); //从初始化结果中提取最后的状态，作为mdp工作的起点。这一句似乎也应该修改    	  
    	  //System.out.println("initState= "+initState.getTaskId()); 输出是第0个task的id
    	  
    	  boolean success = false;    	  
    	  try {
    	    DQNPolicy<GameState> policy = DQNPolicy.load("game.policy"); //game.policy在窗口左侧目录下 
    	    int count = 0; 
  		    while(true) {  	      
  		       if(!playByStep(mdp ,policy))  //执行结束的task不迁移，不计数
  		    	  continue;
  		       count++;
  		       success = isSuccess(mdp); 
  		       //注意这里把400改成了200，否则，执行时间过长。
  		       if( success||count == 200 ) {  //只迁移400个task，因为目前建立的四种服务一共是400个task。迁移任务数设为与DRL+姻亲策略相同的值，好做对比
  		       //SimLogger.printLine("所有task都被处理（迁移到新VM/保持源VM不变）");
  				  break;
  			   } 			  
  		    }
  		    //SimLogger.printLine("count=  "+count);
    	 }catch (IOException e){ }
  	    }
		
       return null;
	}
		
	
	/*
	//DRL+姻亲策略
    @Override
	public List<Map<String, Object>> optimizeAllocation(
			List<? extends Vm> vmList){	//在这个函数中进行task迁移，而不是VM迁移，没有采用异步事件处理机制，即：发送CLOUDLET_MOVE事件。getNewVmPlacement()，saveAllocation()，restoreAllocation()应该都用不上
    //	SimLogger.printLine("\n");
    //	SimLogger.printLine("Edge VM Allocation is optimized here!" );//将新编译的cloudsim导出成jar，然后作为库添加到本项目后，该函数正常工作    	
	     	  
     //	SimLogger.printLine("cloudletList.size= "+cloudletList.size());
    	
    	
    	 if(cloudletList.size()==1000){    
        //  learning();  //训练
 	    //  SimLogger.printLine("训练完成！"); 
    	}    	
    	else if(cloudletList.size()>4500&&cloudletList.size()<5500){ //把直连节点的迁移和非直连节点的迁移合并到了同一个语句块。
    		
    		//SimLogger.printLine("cloudletList.size=  "+cloudletList.size()+"  >700");    		
    		int fromIndex = Math.max(0, cloudletList.size()-400);   		
    		for(int i=0;i<10;i++) {   //给每个CBR服务添加task
    		svcCbrList.get(i+1).clear();
    		  for(int j=0;j<10;j++)     			
    		  	svcCbrList.get(i+1).add((Task)cloudletList.get(fromIndex+i*10+j)); //task从40往后开始，是因为前40个task在createTask（）中已经添加到服务了
    		  }    		
    		for(int i=0;i<10;i++) {   //给每个On服务添加task
    		  svcOnList.get(i+1).clear();    		
      		  for(int j=0;j<10;j++)      			
      			svcOnList.get(i+1).add((Task)cloudletList.get(100+fromIndex+i*10+j));
      		  }
    		for(int i=0;i<10;i++) {   //给每个Poss服务添加task
    		  svcPossList.get(i+1).clear();  
      		  for(int j=0;j<10;j++)       			
      			svcPossList.get(i+1).add((Task)cloudletList.get(200+fromIndex+i*10+j));      			
      		  }
    		for(int i=0;i<10;i++) {   //给每个Self服务添加task
    		  svcSelfList.get(i+1).clear();	    		
        	  for(int j=0;j<10;j++)         		        	  
        		svcSelfList.get(i+1).add((Task)cloudletList.get(300+fromIndex+i*10+j));
        	  } 	      	    		
    		
    	  //执行最佳策略。game.policy在哪里体现？对每一个需要迁移的cloudlet，会给出action，然后转换成目标VM
    	  GameMDP mdp = initMDP();  //创建一个空的GameMDP
    	  Learning.InitMdp<GameState> initMdp;   //InitMdp<GameState>是内部类
    	  initMdp = Learning.initMdp(mdp, null);  //initMdp是方法.首先调用reset方法,从第0个cloudlet开始；如果第二个参数不为空，则利用无操作动作（no-op action）把mdp跑一遍，计算累计奖励和最后一个状态，返回包括总步数、累计奖励和最后一个状态在内的mdp初始状态；否则，不进行这种"热身")
    	  GameState initState = initMdp.getLastObs(); //从初始化结果中提取最后的状态，作为mdp工作的起点。这一句似乎也应该修改    	  
    	  //System.out.println("initState= "+initState.getTaskId()); 输出是第0个task的id
    	  //step1: 对所有服务的直连节点按照最佳策略执行action（即执行task迁移）
    	  boolean success = false;    	  
    	  try {
    	    DQNPolicy<GameState> policy = DQNPolicy.load("game.policy"); //game.policy在窗口左侧目录下    	 
  		    while(true) {  		      
  		       playByStep(mdp ,policy,svcCbrList,svcOnList,svcPossList,svcSelfList);  			  
  		       success = isSuccess(mdp); 
  		       if( success ) {
  		       //SimLogger.printLine("所有task都被处理（迁移到新VM/保持源VM不变）");
  				break;
  			   } 			  
  		    }
    	 }catch (IOException e){ }
    	//   catch (InterruptedException e) { }
    	
    	//step2:对所有服务的非直连节点进行聚类，然后进行迁移
    	ArrayList<ArrayList<HashMap<Integer,Integer>>> c; //聚类集合
  		//SimLogger.printLine("对CBR服务进行聚类和迁移");
  		int k = 4; //聚类个数    		
        int u =0;            
  		for(int i=0;i<10;i++) {    		 
  		  c = cluster((mobileDeviceManager.getAffiCbrArray())[i],k); //对CBR服务进行聚类
  		  u = directLink[0][i];  //第i个CBR服务的直连节点  		 
  		  //SimLogger.printLine("svcCbrList.size=  "+svcCbrList.size());
  		  placement(svcCbrList.get(i),c,u);//将聚类c中和直连节点u具有姻亲的组件放置在u的附近
  		}
  		
  		//SimLogger.printLine("对ON-OFF服务进行聚类和迁移");
  		for(int i=0;i<10;i++) {    			
  		  c = cluster((mobileDeviceManager.getAffiOnArray())[i],k); //对ON-OFF服务进行聚类             
  		  u = directLink[0][i];  //第i个ON服务的直连节点
  		  placement(svcOnList.get(i),c,u);//将聚类c中和直连节点u具有姻亲的组件放置在u的附近
  		}    		
  		
  		//SimLogger.printLine("对POSSION服务进行聚类和迁移");
  		for(int i=0;i<10;i++) {
  		  c = cluster((mobileDeviceManager.getAffiPossArray())[i],k); //对POSSION服务进行聚类             
  		  u = directLink[0][i];  //第i个POSS服务的直连节点
  		  placement(svcPossList.get(i),c,u);//将聚类c中和直连节点u具有姻亲的组件放置在u的附近
  		}
  		
  		//SimLogger.printLine("对Self服务进行聚类和迁移");
  		for(int i=0;i<10;i++) {
  		  c = cluster((mobileDeviceManager.getAffiSelfArray())[i],k); //对Self服务进行聚类             
  		  u = directLink[0][i];  //第i个Self服务的直连节点
  		  placement(svcSelfList.get(i),c,u);//将聚类c中和直连节点u具有姻亲的组件放置在u的附近
  		}
  	    }//else if(cloudletList.size()>15000   		
  		   	     		
		return null;  	    
	}
	*/
	
	
   
	@Override
	public void deallocateHostForVm(Vm vm) {
		SimLogger.printLine("a Edge VM is deallocated!");
		Host host = getVmTable().remove(vm.getUid());
		if (host != null) {
			host.vmDestroy(vm);  //在host上销毁vm
		}
		
	}

	@Override
	public Host getHost(Vm vm) {
		return getVmTable().get(vm.getUid());
	}

	@Override
	public Host getHost(int vmId, int userId) {
		return getVmTable().get(Vm.getUid(userId, vmId));
	}

	public static int getCreatedVmNum(){
		return createdVmNum;
	}
	
	/**
	 * Gets the vm table.
	 * 
	 * @return the vm table
	 */
	public Map<String, Host> getVmTable() {
		return vmTable;
	}

	/**
	 * Sets the vm table.
	 * 
	 * @param vmTable the vm table
	 */
	protected void setVmTable(Map<String, Host> vmTable) {
		this.vmTable = vmTable;
	}	
	
	//扩展的函数由此开始
	
	//返回迁移映射图，vmsToMigrate指的是所有需要迁移的VM。该函数是实现优化策略的核心函数.由于我们迁移的是task，而不是vm，所以返回的映射图是null。
	protected List<Map<String, Object>>getNewVmPlacement (//暂时还没有实现任何分配策略
			List<? extends Vm> vmsToMigrate,
			Set<? extends Host> excludedHosts) throws Exception{		
		learning();
		return null;  		
	}
	
	
	double getCost(Task task,EdgeHost srcHost, EdgeHost dstHost){
		
		double delay=0;
		delay = getCommCost(task,srcHost,dstHost) + getCompCost(task,srcHost,dstHost) + getMigCost(task,srcHost,dstHost);
		//SimLogger.printLine("Totalcost=  "+delay);
		//通信延迟使用srcHost为参数
		return delay;
	}
	
	//使用公式（6）计算通信成本.实际计算目标主机和源主机的通信延迟的差值.注意：不包括回程延迟，也就是公式（7）的成本
	double getCommCost(Task task, EdgeHost srcHost,EdgeHost dstHost){
		double srcUploadDelay = 0,dstUploadDelay = 0,srcDownloadDelay = 0,dstDownloadDelay = 0;
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		srcUploadDelay = networkModel.getUploadDelay(task.getMobileDeviceId(),SimSettings.GENERIC_EDGE_DEVICE_ID,task,srcHost);//在srcHost上上传的延迟
		dstUploadDelay = networkModel.getUploadDelay(task.getMobileDeviceId(),SimSettings.GENERIC_EDGE_DEVICE_ID,task,dstHost);//在dstHost上上传的延迟		
		srcDownloadDelay = networkModel.getDownloadDelay(task.getAssociatedHostId(),task.getMobileDeviceId(),task,srcHost);//在srcHost上上传的延迟
		dstDownloadDelay = networkModel.getDownloadDelay(task.getAssociatedHostId(),task.getMobileDeviceId(),task,dstHost);//在dstHost上下载的延迟
		return 	(dstUploadDelay - srcUploadDelay) + (dstDownloadDelay - srcDownloadDelay);	
	}	
	
	
	//使用公式（9）计算计算成本,以秒为单位，是目标VM和源VM上执行的计算成本差值
	double getCompCost(Task task, EdgeHost srcHost, EdgeHost dstHost){		
		double delay=0;
		delay = task.getCloudletLength()*(dstHost.getVmList().size())/dstHost.getAvailableMips()-task.getCloudletLength()*(srcHost.getVmList().size())/srcHost.getAvailableMips();
		//SimLogger.printLine("Compcost=  "+delay);
		return delay;
	}
	
	//使用公式（10）计算迁移成本
	double getMigCost(Task task, EdgeHost srcHost, EdgeHost dstHost){
		double delay = 0, λ =0.0002;
		//SimLogger.printLine("srcHost.id= "+srcHost.getId()+"  dstHost.id= "+dstHost.getId());		 
    	double dist = SimManager.getInstance().getEdgeServerManager().dist(srcHost.getId(),dstHost.getId());
		
    	if (dist>=Integer.MAX_VALUE )
    	  delay = Integer.MAX_VALUE;
    	else 
    	  delay = task.getCloudletFileSize()/(SimSettings.getInstance().getWanBandwidth()*1024*1024/8) + 2*λ*dist/100;//原来采用的是getManBandwidth(),读数是0
    	//SimLogger.printLine("Migcost=  "+delay);
    	return delay; 		
			
	}
	
	
	
	public static QLearning.QLConfiguration QL_CONFIG =
            new QLearning.QLConfiguration(
                    123,   	//Random seed  随机种子
                    1000,	//Max step Every epoch 每一轮的最大步数（step函数被调用的次数）。比如，对于遍历cloudList（假设，每一步遍历一个元素）,如果列表大小是21，那么实际步数就是21
               //    30,   //这个数值是0.5秒模拟时间内，任务数的最大值
                    1000*100,     //Max step            最大步数.这一行/上一行就是训练轮次
               //     100*2000,                
                    100*100, //Max size of experience replay 经验回放内存大小               
                    30,    //size of batches      批量大小
                    10,   //target update (hard) 目标网络更新频率（每10步更新一次参数），如果最后一个参数为false，则该参数无实际意义
                    0,     //num step noop warmup   步数从0开始
                    0.01,  //reward scaling        
                    0.4,  //gamma      折扣因子
                    1.0,  //td-error clipping    初始探索率
                    0.1f,  //min epsilon    最小探索率
                    300,  //num step for eps greedy anneal    探索衰减步数
                    false   //double DQN   单DQN，需要clone一个DQN
            );
	
    public static DQNFactoryStdDense.Configuration DQN_NET =      //创建的是一个3层网络：输入层：节点数由环境状态空间决定。隐藏层1：16个神经元。隐藏层2：16个神经元。输出层：节点数由动作空间决定。每层包含16个神经元的全连接网络，优化器用的是Adam，学习率是0.001
            DQNFactoryStdDense.Configuration.builder()     //这个配置适合处理中等复杂度的强化学习任务，如经典的CartPole、MountainCar等环境。      
                    .updater(new Adam(0.001))
                    .numLayer(2)      //2个隐藏层   
                    .numHiddenNodes(64)   //每个隐藏层64个神经元
                    .build();
    /*原版	
    public static DQNFactoryStdDense.Configuration DQN_NET =      //创建的是一个3层网络：输入层：节点数由环境状态空间决定。隐藏层1：16个神经元。隐藏层2：16个神经元。输出层：节点数由动作空间决定。每层包含16个神经元的全连接网络，优化器用的是Adam，学习率是0.001
            DQNFactoryStdDense.Configuration.builder()     //这个配置适合处理中等复杂度的强化学习任务，如经典的CartPole、MountainCar等环境。      
                    .updater(new Adam(0.001))
                    .numLayer(2)      //2个隐藏层   
                    .numHiddenNodes(16)   //每个隐藏层16个神经元
                    .build();
    
	*/
	
    //进行训练
    public static void learning(){
    	try {
        DataManager manager = new DataManager();  //用来管理训练数据

        GameMDP mdp = new GameMDP(); //创建一个mdp

        QLearningDiscreteDense<GameState> dql = new QLearningDiscreteDense<GameState>(mdp, DQN_NET, QL_CONFIG, manager);

        DQNPolicy<GameState> pol = dql.getPolicy();

        dql.train(); //进行训练。轮数在训练过程中包含
        
        pol.save("game.policy2"); //训练好的最优策略存储到该目录下
       
        mdp.close();
    	}catch (IOException e) {System.out.println("IO Exception");}   

    }
    
    
    //DRL策略对所有task执行迁移
    public  boolean playByStep(GameMDP mdp, DQNPolicy<GameState> policy) throws IOException{
    	GameState curState = mdp.getCurState(); //获取当前状态，对于paper5,就是遍历一个task
    	//在cloudletList上查找task
    	Cloudlet cloudlet = null;  boolean isMig = true;
		Iterator<? extends Cloudlet> iter = cloudletList.iterator();
		labelOne:
		while(iter.hasNext()){
		  cloudlet = iter.next();				
		  if (curState.getTaskId() == cloudlet.getCloudletId())
		    break labelOne;    //跳出标号所表示的范围
	    }
		Task task = (Task)cloudlet;   //找到task
		//SimLogger.printLine("task.statues=  "+task.getStatusString(task.getStatus()));
		if (task.getStatus()==4)    //说明task的状态是Success，已经执行结束，不能实施迁移
			isMig =false;
		
    	INDArray input = Learning.getInput(mdp, curState);  //把mdp的当前状态curState转换成DQN的输入input
        int action = policy.nextAction(input).intValue();  //按照最佳策略，当前状态下（已转换成input）应该采取什么action
        mdp.step(action); 
        return isMig;
    }
    
    
        
    /*
    //DRL+姻亲策略对所有服务的直连节点按照最佳策略执行action（即执行task迁移）
    public  void playByStep(GameMDP mdp, DQNPolicy<GameState> policy,ArrayList<ArrayList<Task>> svcCbrList,ArrayList<ArrayList<Task>> svcOnList,ArrayList<ArrayList<Task>> svcPossList,ArrayList<ArrayList<Task>> svcSelfList) throws IOException{  //该方法只在实施game.policy时调用，训练时不调用
    	
    	GameState curState = mdp.getCurState(); //获取当前状态，对于paper5,就是遍历一个task     
    	//SimLogger.printLine("curState= "+curState.getTaskId());
    	//SimLogger.printLine("cloudletList.size=  "+cloudletList.size());
    	boolean foundCbr = false, foundOn = false, foundPoss = false,foundSelf = false;  //标志变量表示curState是四种类型服务中的task    	
    	boolean found = false;
    	
    	//找每一个CBR服务的直连节点，如果这个节点是curstate,就执行action，并把直连节点记录到directLink数组
    	int index = 0;    	
    	Iterator<ArrayList<Task>> iter = svcCbrList.iterator();  //重新移到表头
        while(!found&&iter.hasNext()) {
        	ArrayList<Task> svcCbr = iter.next();    				
    		Task task = svcCbr.get(directLink[0][index]);  //获取直连节点
    		//SimLogger.printLine("task.getCloudletId=   "+task.getCloudletId()+"   curState.taskId=  "+curState.taskId);
    		if (task.getCloudletId()==curState.taskId) {  		
     		   INDArray input = Learning.getInput(mdp, curState);  //把mdp的当前状态curState转换成DQN的输入input
               int action = policy.nextAction(input).intValue();  //按照最佳策略，当前状态下（已转换成input）应该采取什么action
               mdp.step(action);  //按照action，向前走一步.对于paper5,就是一个task移动到新VM上
               foundCbr = found = true;   //至少这个CBR服务的直连节点得到了迁移，下同               
               break ;  //curState只可能与10个CBR服务的直连节点中的一个相同
            }
    		index++;
    	}
    	if(!found) {
    	//找每一个On服务的直连节点，如果这个节点是curstate,就执行action，并把直连节点记录到directLink数组       
    	  index = 0;
    	  iter = svcOnList.iterator();    	
    	  while(!found&&iter.hasNext()) {
    		ArrayList<Task> svcOn = iter.next();
    		Task task = svcOn.get(directLink[1][index]);  //获取直连节点
    		if (task.getCloudletId()==curState.taskId) {  		
     		   INDArray input = Learning.getInput(mdp, curState);  //把mdp的当前状态curState转换成DQN的输入input
               int action = policy.nextAction(input).intValue();  //按照最佳策略，当前状态下（已转换成input）应该采取什么action
               mdp.step(action);  //按照action，向前走一步.对于paper5,就是一个task移动到新VM上
               foundOn = found = true;               
               break;
            }
    		index ++;
    	  }
    	}
    	//找每一个possion服务的直连节点，如果这个节点是curstate,就执行action，并把直连节点记录到directLink数组   
    	if(!found) {
    	  index = 0;
    	  iter = svcPossList.iterator();  
    	  while(!found&&iter.hasNext()) {
    		ArrayList<Task> svcPoss = iter.next();
    		Task task = svcPoss.get(directLink[2][index]);  //获取直连节点
    		if (task.getCloudletId()==curState.taskId) {  //curState表示的task是直连节点		
     		   INDArray input = Learning.getInput(mdp, curState);  //把mdp的当前状态curState转换成DQN的输入input
               int action = policy.nextAction(input).intValue();  //按照最佳策略，当前状态下（已转换成input）应该采取什么action
               mdp.step(action);  //按照action，向前走一步.对于paper5,就是一个task移动到新VM上
               foundPoss = found = true;
               break;                 
            }  
    		index ++;
    	  } 
    	}
    	//找每一个Self服务的直连节点，如果这个节点是curstate,就执行action，并把直连节点记录到directLink数组
    	if(!found) {
    	  index = 0;
    	  iter = svcSelfList.iterator();    	        	
    	  while(!found&&iter.hasNext()) {
    		ArrayList<Task> svcSelf = iter.next();
    		Task task = svcSelf.get(directLink[3][index]);  //获取直连节点
    		if (task.getCloudletId()==curState.taskId) {  		
     		   INDArray input = Learning.getInput(mdp, curState);  //把mdp的当前状态curState转换成DQN的输入input
               int action = policy.nextAction(input).intValue();  //按照最佳策略，当前状态下（已转换成input）应该采取什么action
               mdp.step(action);  //按照action，向前走一步.对于paper5,就是一个task移动到新VM上
               foundSelf = found = true;               
               break;
            }
    		index ++;
    	 }
    	}
    	
    	//SimLogger.printLine("svcCbrList.size=  "+svcCbrList.size());
    	if(found == false ) {  //curState表示的task不是任何一个服务中的直连节点,仍然需要转换到下一个状态.前面对四个服务的处理，在step()中会转换到下一个状态
    		curState.setTaskId(curState.getTaskNextId());
    		int taskId = curState.getTaskId();
    		
    		//在cloudletList上查找task
    		Cloudlet cloudlet = null;
    		Iterator<? extends Cloudlet> iter2 = cloudletList.iterator();
    		labelOne:
    		while(iter2.hasNext()){
    		  cloudlet = iter2.next();				
    		  if (taskId == cloudlet.getCloudletId())
    		    break labelOne;    //跳出标号所表示的范围
    	    }
    		Task task = (Task)cloudlet;   //找到task
    		
    		//查找task所在的VM
    		Vm srcVm=null; int i=0;
    		labeltwo:
    		for(i=0;i<SimSettings.getInstance().getNumOfEdgeHosts();i++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
    		  	Iterator<EdgeVM> iter3 = edgeServerManager.getVmList(i).iterator();
    			while(iter3.hasNext()){
    			  srcVm =iter3.next();				
    			  if (task.getVmId()==srcVm.getId())
    			    break labeltwo;
    		    }
    		}		
    		if (i==SimSettings.getInstance().getNumOfEdgeHosts()+SimSettings.getInstance().getNumOfCloudHost())  
    			SimLogger.printLine("VM not found");
    		
    		//设置新状态。task不迁移，仍保持在源VM上
    		curState.setRequiredVmCapacity(((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(((EdgeVM)srcVm).getVmType()));
			curState.setTargetVmCapacity((double)100-srcVm.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock())+task.getUtilizationOfCpu(CloudSim.clock())); //应该计算task在srcVm上提交前的可用CPU利用率
			
			//taskNext指向下一个cloudlet，这一版成熟后，可以尝试用service建立状态的方案
			for(int j=0;j<cloudletList.size();j++) 
			  if (task.getCloudletId()==cloudletList.get(j).getCloudletId()&&j!=cloudletList.size()-1) {
				curState.setTaskNextId(cloudletList.get(j+1).getCloudletId()); //设置新状态
				break;
			  }else if (j==cloudletList.size()-1)    //task已经是最后一个cloudlet
				curState.setTaskNextId(-1);	   //设置新状态					
			
		    // new StepReply<>(curState, 0, mdp.isDone(), null); //将MDP的下一个state和reward存入重放内存
    	}
    }  
    */
    
    public static GameMDP initMDP(){
    	GameMDP mdp = null;
    	try {
    	 mdp = new GameMDP();
    	}catch (Exception e) {     }
    	return mdp;   	
    }
    
    public static boolean isSuccess(GameMDP mdp){
    	boolean ret= false;
    	//
    	GameState state = mdp.getCurState();
    	//SimLogger.printLine("curState= "+state.getTaskId());
    	if( state.getTaskId()==-1)ret = true;
    	//
    	return ret;
    }
    
    public ArrayList<ArrayList<HashMap<Integer,Integer>>> cluster(double[][] affinity,int k) {
    	
    	ArrayList<ArrayList<HashMap<Integer,Integer>>> c = new ArrayList<ArrayList<HashMap<Integer,Integer>>>(); //k个聚类的集合   	
    	double center[] = new double[k], centerOld[] = new double[k];
    	double ε=0.1;  int maxGeneration=100; boolean flag=true;
    	
    	//初始化
    	for (int t=0;t<k;t++) {  //随机得到k个中心
    		int random1 = (int) (Math.random() * 10);
    		int random2 = (int) (Math.random() * 10);
    		if(random1!=random2) 
    		   center[t] = affinity[random1][random2];
    		else {
    			random2 = (int) (Math.random() * 10);  //重新生成random2
    			center[t] = affinity[random1][random2];  //保证center[t]不是无穷大    			
    		}
    		//SimLogger.print("  初始化： center["+t+"]= "+center[t]);
    	}    	
    	for(int t=0;t<k;t++){  //创建k个聚类
    	  ArrayList<HashMap<Integer,Integer>> c_A = new ArrayList<HashMap<Integer,Integer>>();
    	  c.add(t,c_A);  
    	} 
    	
    	int gen=0;
    	while(flag&&gen<maxGeneration) {
    	  double distance[][][] = new double[10][10][k];
    	  for(int t=0;t<k;t++)  //准备重新生成聚类
    		c.get(t).clear(); 
    	   
    	  //计算affinity[i][j]到k个中心的距离
    	  for(int i=0;i<10;i++)  
    	    for(int j=0;j<10;j++)
    		  for(int t=0;t<k;t++)
    		    distance[i][j][t] = Math.abs(affinity[i][j]-center[t]);
    	  
      	  //保存k个中心到centerOld数组
    	  System.arraycopy(centerOld, 0, center,0,k);    	
    	
    	  //找出affinity[i][j]到k个中心的最小距离，并把(i,j)加入该中心对应的聚类
    	  double minDistance[][] = new double[10][10]; int minIndex=0;
    	  for(int i=0;i<10;i++)   
      	    for(int j=0;j<10;j++) {    
      		  minDistance[i][j]= Double.MAX_VALUE;      		  
      		  for(int t=0;t<k;t++){     		   
      		    if (distance[i][j][t] < minDistance[i][j]) {
      		      minDistance[i][j] = distance[i][j][t]; 
      		      minIndex = t;    
      	        }
      		  }
      		  if (i==j) continue;   //跳过affinity[][]的对角线元素
      		  HashMap<Integer,Integer> affi = new HashMap<Integer,Integer>();
      		  affi.put(i,j);
      		  c.get(minIndex).add(affi);    //与第minIndex个中心的距离最小,把(i,j)加入第minIndex个聚类。
      	    }
    	  
          //重新计算k个中心    	
    	  for (int t=0;t<k;t++) {
    		double sum=0;
    		Iterator<HashMap<Integer,Integer>> iter = c.get(t).iterator();//准备遍历第t个聚类
    		while(iter.hasNext()) {
    		  Set<Map.Entry<Integer, Integer>> entrySet = iter.next().entrySet();
    		  for(Map.Entry<Integer, Integer> entry:entrySet) {
    		    int i=entry.getKey();
    			int j=entry.getValue();
    			sum += affinity[i][j];
    		  }
    		}
    		if (c.get(t).size()==0) {  //随机生成一个中心
    			int random1 = (int) (Math.random() * 10);
        		int random2 = (int) (Math.random() * 10);
        		if(random1!=random2) 
        		   center[t] = affinity[random1][random2];
        		else {
        			random2 = (int) (Math.random() * 10);  //重新生成random2
        			center[t] = affinity[random1][random2];  //保证center[t]不是无穷大    			
        		}
    		}
    	    else    center[t] = sum/(c.get(t).size());  
    	    //SimLogger.print("  center["+t+"]= "+center[t]);
    	  }    	  
    	  
    	  boolean allSatisfied= true;
    	  for(int t=0;t<k;t++)
    		if (Math.abs(center[t]-centerOld[t])>=ε) { //如果有任何一对新旧中心距离的误差>ε，则while循环应该继续。
    		  allSatisfied = false;
    		  break;
    	  }
    	  if (allSatisfied)    flag = false;
    	  gen++;   		  
    		  
        }//while  
    	
    	//输出聚类结果
    	 /*
    	 for(int t=0;t<k;t++) {
    	   ArrayList<HashMap<Integer,Integer>> c_A = c.get(t); 
    	   Iterator<HashMap<Integer,Integer>> iter = c_A.iterator();
    	   SimLogger.printLine("  ");
    	   SimLogger.printLine("第"+t+"个聚类有"+c_A.size()+"个姻亲节点：");
    	   while (iter.hasNext()) {
    		 HashMap<Integer,Integer> affi = iter.next();
  	         Set<Map.Entry<Integer, Integer>> entrySet = affi.entrySet();	        
  	         for(Map.Entry<Integer, Integer> entry:entrySet) {
  	           int i=entry.getKey();
  	    	   int j=entry.getValue();
    		   SimLogger.printLine(i+"->"+j+":  "+affinity[i][j]);
    	     }    	 
    	   }
    	  
    	 }*/
    	
    	return c;
  
   }
    
   //将聚类集合c中和直连组件u具有姻亲的组件放置在u的附近
   public void placement(ArrayList<Task> service,ArrayList<ArrayList<HashMap<Integer,Integer>>> c,int u) {//u是一个直连组件。这个方法是针对同一个服务内部的组件。不同的服务有赖上层调用者或添加参数来控制
	   
	   int i=u;  //i是临时变量
	   ArrayList<HashMap<Integer,Integer>> cl; 
	   
	   //将c中的聚类按姻亲的平均值排序，使得姻亲高的聚类先得到放置。	   
	   
	   for(int t=0;t<c.size();t++) {
		   cl = c.get(t);
		   //SimLogger.printLine("cl.size=  "+cl.size());
		   
 		   //step1:将涉及到i的姻亲节点放入线性表neighbour。本步属于广度优先
		   Iterator<HashMap<Integer,Integer>> iter = cl.iterator();//准备遍历聚类cl			 
		   ArrayList<HashMap<Integer,Integer>> neighbour = new ArrayList<HashMap<Integer,Integer>>();
		   while(iter.hasNext()) {  
			 HashMap<Integer,Integer> affi = iter.next();
	         Set<Map.Entry<Integer, Integer>> entrySet = affi.entrySet();	        
	         for(Map.Entry<Integer, Integer> entry:entrySet) {
	           int j=entry.getKey();
	    	   int k=entry.getValue();
	    	 if(i==j||i==k) neighbour.add(affi);			  	 
		     }//for(Map.Entry<Integer, Integer> entry:entrySet)	     
		   }
		   
		   //step2:对于neighbour中的每个姻亲节点，放置与i相邻的节点，并对i的子孙节点也完成放置。本步属于深度优先		         
		   Iterator<HashMap<Integer,Integer>> iter2 = neighbour.iterator(); 
		   int size = cl.size();
		   ArrayList<Integer>  list = new ArrayList<Integer>();	
		   while (iter2.hasNext()) {
			   int count = 0;
			  		   
			   while(i!=-1) {				 
				 iter = cl.iterator();  //重新遍历cl				 
				 HashMap<Integer,Integer> affi = iter.next();  //在cl中取一个姻亲节点
				 Set<Map.Entry<Integer, Integer>> entrySet = affi.entrySet();        
		         for(Map.Entry<Integer, Integer> entry:entrySet) {
		           int j=entry.getKey();
		    	   int k=entry.getValue();
		    	   if(j==i) {   
		  			 placementNear(service,i,k); //将service的组件k放置在i的附近，下同
		  			 list.add(k);
		  			 iter.remove();  //当使用迭代器（例如通过 iterator() 方法获取）遍历一个集合（如 List, Set 等）时，如果直接通过集合本身的 add(), remove() 等方法修改集合的结构（而不是通过迭代器自身的 remove() 方法），就会抛出 ConcurrentModificationException
		  			 //SimLogger.printLine("  节点"+k+"已放置！");
		  			 i=k;   //沿着k深度遍历，直到cl中的姻亲节点都被遍历完
		  			 break;  // for(Map.Entry<Integer, Integer> entry:entrySet)
		  		   }else if(k==i) {
		  			 placementNear(service,i,j);
		  			 list.add(j);
		  			 iter.remove();
		  			 //SimLogger.printLine("  节点"+j+"已放置！");
		  			 i=j;   //沿着j深度遍历，直到cl中的姻亲节点都被遍历完
		  			 break; // for(Map.Entry<Integer, Integer> entry:entrySet)
		  		   }		    	   
		         }
		         count++;
		         if (count==size)  i=-1;
			   }//while(i!=-1)
			   HashMap<Integer,Integer> affi = iter2.next();  //在neighbour中取一个姻亲节点
			   Set<Map.Entry<Integer, Integer>> entrySet = affi.entrySet();        
		         for(Map.Entry<Integer, Integer> entry:entrySet) {
		           int j=entry.getKey();
		    	   int k=entry.getValue();
			       if(i==j)  i=k;  //以k为根遍历cl，使得它的子孙节点被放置，就像最初的i那样
			       if(i==k)  i=j;  //以j为根遍历cl，使得它的子孙节点被放置，就像最初的i那样			        
	           }		 
         }//while (iter2.hasNext())
		   
		 //将cl中剩余的姻亲节点进行放置
		 //SimLogger.printLine("cl.size=  "+cl.size());
		 iter = cl.iterator();//再次遍历cl
		 HashMap<Integer,Integer> affi;
		 while(iter.hasNext()) {
		   affi = iter.next();  //在cl中取一个姻亲节点
		   Set<Map.Entry<Integer, Integer>> entrySet = affi.entrySet();
		  
           for(Map.Entry<Integer, Integer> entry:entrySet) {
             int j=entry.getKey();
    	     int k=entry.getValue();
    	     if (list.contains(j)) {
    	       placementNear(service,j,k); 
    	       iter.remove();
  			   //SimLogger.printLine("  节点"+k+"已放置:在cl剩余节点的处理中");
  			   break;
    	     }else if (list.contains(k)) {
      	       placementNear(service,k,j); 
      	       iter.remove();
    		   //SimLogger.printLine("  节点"+j+"已放置：在cl剩余节点的处理中");    		   
  			   break;
             }
		   }           
		 }// while(iter.hasNext())
		 
	    //SimLogger.printLine("cl.size=  "+cl.size());		 
       }//for(int t=0;t<c.size();t++)  
	   
   }
   
   //将服务service的组件i放置在u的最近处
   public void placementNear(ArrayList<Task> service,int u,int i) { 
	    Task task_u = service.get(u);
	    Task task_i = service.get(i);
	    Vm vmU=null,vmI=null;  
	    int j=0;
		
		//查找task_u所在的VM
		labelOne:
		for(j=0;j<SimSettings.getInstance().getNumOfEdgeHosts();j++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
		  	Iterator<EdgeVM> iter = edgeServerManager.getVmList(j).iterator();
			while(iter.hasNext()){
			  vmU =iter.next();				
			  if (task_u.getVmId()==vmU.getId())
			    break labelOne;
		    }
		}		
		if (j==SimSettings.getInstance().getNumOfEdgeHosts()+SimSettings.getInstance().getNumOfCloudHost())  
			SimLogger.printLine("VM not found");
		EdgeHost hostU = (EdgeHost)vmU.getHost(); //获取task_u所在的host
		
		//查找task_i所在的VM
		labelTwo:
		for( i=0;i<SimSettings.getInstance().getNumOfEdgeHosts();i++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
		  	Iterator<EdgeVM> iter2 = edgeServerManager.getVmList(i).iterator();
			while(iter2.hasNext()){
			  vmI =iter2.next();				
			  if (task_i.getVmId()==vmI.getId())
			    break labelTwo;
		    }
		}		
		if (i==SimSettings.getInstance().getNumOfEdgeHosts()+SimSettings.getInstance().getNumOfCloudHost())  
			SimLogger.printLine("VM not found");		
		
		//对所有主机按照与hostU的距离进行排序
		List<Datacenter> localDataCenters = edgeServerManager.getDatacenterList();		
		Iterator<Datacenter> iter = localDataCenters.iterator();  //准备遍历边缘数据中心	
		HashMap<Integer,Double> distanceMap = new HashMap<Integer,Double>();
		List<Integer> hostIdList = new ArrayList<Integer>();
		while(iter.hasNext()) {   //将host和距离的映射加入Map			
			Datacenter datacenter= iter.next();
			EdgeHost host = (EdgeHost) datacenter.getHostList().get(0);
			double x = hostU.getLocation().getXPos()-host.getLocation().getXPos();
			double y = hostU.getLocation().getYPos()-host.getLocation().getYPos();			
			distanceMap.put(host.getId(),Math.sqrt(Math.pow(x,2)+Math.pow(y,2)));
			hostIdList.add(host.getId());
		}		
	    hostIdList.sort(Comparator.comparingDouble(distanceMap::get));//注意：这里用hostID代表主机	，这个写法模仿的是deepseek给出的排序写法，升序排列。    
	    
	    
	    //如果hostIdList上的某个主机能满足task_i的执行要求，将task_i迁移到该主机的VM上
	    Iterator<Integer>  iter2 = hostIdList.iterator();  //准备遍历hostIdList
	    labelThree:
	    while(iter2.hasNext()) {
	    	Integer hostId = iter2.next();
	    	EdgeHost host = (EdgeHost) localDataCenters.get(hostId).getHostList().get(0);
	    	for(j=0;j<2;j++) {   
	    	   Vm vm = host.getVmList().get(j);
	    	   if (100-vm.getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock())>((CpuUtilizationModel_Custom)(task_i.getUtilizationModelCpu())).predictUtilization(((EdgeVM)vm).getVmType()))
	    		 if (edgeServerManager.getEdge()[vmI.getHost().getId()][vm.getHost().getId()]<Integer.MAX_VALUE&&vmI!=vm&&vmI!=null&&vm!=null) {
	    			    //SimLogger.printLine("task "+task.getCloudletId()+" 将要移动到 vm= "+vm.getId());	    				
	    				Cloudlet cl=null;				
	    				cl = vmI.getCloudletScheduler().cloudletCancel(task_i.getCloudletId()); //task在源VM上撤销			
	    				int datacenterId  = mobileDeviceManager.getVmsToDatacentersMap().get(vmI.getId());	    	 		
	    		        Datacenter datacenter = edgeServerManager.getDatacenterList().get(datacenterId-5);
	    				if(cl!=null){  //task_i撤销成功									    	    				
	    				   //SimLogger.printLine("task  "+cl.getCloudletId()+" 在源VM上撤销成功");
	    				   task_i.setVmId(vm.getId());   //task_i的宿主vm设置为新VM,下同
	    				   // the cloudlet will migrate from one vm to another does the destination VM exist?
	    				   // time to transfer the files			  
	    			       double fileTransferTime = datacenter.predictFileTransferTime(cl.getRequiredFiles());//计算cloudlet的文件所需的传输时间，传输时间由源datacenter决定
	    			 	   double processTime = vm.getCloudletScheduler().cloudletSubmit(cl, fileTransferTime); //传输完成后由目标vm的云任务调度器提交cloudlet，核心工作是将cloudlet加入执行队列，处理时间由目标vm决定
    	    			   //if (processTime > 0)
	    			 	   //SimLogger.printLine("task  "+cl.getCloudletId()+" 在目标VM上提交成功");
	    				   task_i.setAssociatedDatacenterId(vm.getHost().getDatacenter().getId());	    				 
	    				   task_i.setAssociatedHostId(vm.getHost().getId());	    			
	    				   task_i.setAssociatedVmId(vm.getId());
	    				   double cost = task_i.getCost();
	    				   cost += getCost(task_i, (EdgeHost)vmI.getHost(), (EdgeHost)vm.getHost()); //暂时只考虑迁移成本。后续加上通信成本和计算成本
	    				   task_i.setCost(cost); //设置成本
	    				   mobileDeviceManager.bindCloudletToVm(task_i.getCloudletId(),vm.getId()); //云任务和VM绑定.与cl.setVmId(vm.getId())应该等效	    				  
	    				}
	    	            break labelThree;
	    		 }	    	   
	    	}
	    	
	   }  
	   
   }
   
   
	//获取savedAllocation
	protected List<Map<String, Object>> getSavedAllocation() {
		return savedAllocation;
	}
	
	/**将vm->host的当前分配保存到线性表savedAllocation中
	 * Updates the list of maps between a VM and the host where it is place.
     * @see #savedAllocation
     */
	protected void saveAllocation() {
		getSavedAllocation().clear();
		for (Host host : getHostList()) {
			for (Vm vm : host.getVmList()) {
				if (host.getVmsMigratingIn().contains(vm)) {
					continue;
				}
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("host", host);
				map.put("vm", vm);
				getSavedAllocation().add(map);
			}
		}
	}
	
	protected void restoreAllocation() {
		for (Host host : getHostList()) {
			//销毁host上的所有VM，相当于clear
			host.vmDestroyAll();
			//为将要迁入的vm分配host
			host.reallocateMigratingInVms();
		}
		//恢复vm原有的分配
		for (Map<String, Object> map : getSavedAllocation()) {
			//获取对象vm
			Vm vm = (Vm) map.get("vm");
			//获取对象host
			Host host = (Host)map.get("host");
			//在host上创建vm
			if (!host.vmCreate(vm)) {
				SimLogger.printLine("Couldn't restore VM #"+vm.getId()+" on host #"+host.getId());
				System.exit(0);
			}
			//修改vm映射表
			getVmTable().put(vm.getUid(), host);
		}
	
   }
   
   public <T extends Cloudlet> List<T> getCloudletList() {
		return (List<T>) cloudletList;
   }
	
}
