/*
 * Title:        EdgeCloudSim - Mobile Device Manager
 * 
 * Description: 
 * DefaultMobileDeviceManager is responsible for submitting the tasks to the related
 * device by using the Edge Orchestrator. It also takes proper actions 
 * when the execution of the tasks are finished.
 * By default, DefaultMobileDeviceManager sends tasks to the edge servers or
 * cloud servers. If you want to use different topology, for example
 * MAN edge server, you should modify the flow defined in this class.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.edge_server.*;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.cloud_server.*;

import java.io.*;
import java.util.*;

public class DefaultMobileDeviceManager extends MobileDeviceManager {
	private static final int BASE = 100000; //start from base in order not to conflict cloudsim tag!
	private static final int REQUEST_RECEIVED_BY_CLOUD = BASE + 1;
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 2;
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 3;
	private int taskIdCounter=0;   //任务号
	//以下是新添加的变量
	public   ArrayList<Task> svcCbr = new ArrayList<Task>();
			 //CBR服务,假设一个服务由10个task组成，下同
	public ArrayList<Task> svcOn = new ArrayList<Task>();  //ON-OFF服务
	public ArrayList<Task> svcPoss = new ArrayList<Task>();   //possion服务
	public ArrayList<Task> svcSelf = new ArrayList<Task>();   //自相似服务
	double[][] traffCbr = new double[10][10];  
	double[][] traffOn = new double[10][10];
	double[][] traffPoss = new double[10][10];
	double[][] traffSelf = new double[10][10];
	double[][] distCbr = new double[10][10];  
	double[][] distOn = new double[10][10];
	double[][] distPoss = new double[10][10];
	double[][] distSelf = new double[10][10];
	double[][] affiCbr = new double[10][10]; 
	double[][] affiOn = new double[10][10];
	double[][] affiPoss = new double[10][10];
	double[][] affiSelf = new double[10][10];
	boolean cbrFinish = false, onFinish = false, possFinish =false, selfFinish = false; 
	double[][][] affiCbrArray = new double[10][10][10];
	double[][][] affiOnArray = new double[10][10][10];
	double[][][] affiPossArray = new double[10][10][10];
	double[][][] affiSelfArray = new double[10][10][10];
	
	
	public DefaultMobileDeviceManager() throws Exception{
	}

	@Override
	public void initialize() { //新添加的方法实现。利用这个方法初始化流量矩阵、距离矩阵和姻亲矩阵以及四种类型的服务		 
		 
		 //初始化流量矩阵
		 for (int i=0;i<traffCbr.length;i++)
		   Arrays.fill(traffCbr[i],0.0);
		 for(int i=0;i<traffCbr[0].length;i++)   //对角线上的元素赋最大值,是否正确，有待检验
		   traffCbr[i][i] = Double.MAX_VALUE;         
		 for (int i=0;i<traffOn.length;i++)
		   Arrays.fill(traffOn[i],0.0);
		 for(int i=0;i<traffOn[0].length;i++)   //对角线上的元素赋最大值
		   traffOn[i][i] = Double.MAX_VALUE;      
		 for (int i=0;i<traffPoss.length;i++)
		   Arrays.fill(traffPoss[i],0.0);
		 for(int i=0;i<traffPoss[0].length;i++)   //对角线上的元素赋最大值
		   traffPoss[i][i] = Double.MAX_VALUE; 
		 for (int i=0;i<traffSelf.length;i++)
			   Arrays.fill(traffSelf[i],0.0);
		 for(int i=0;i<traffSelf[0].length;i++)   //对角线上的元素赋最大值
			   traffSelf[i][i] = Double.MAX_VALUE;     
		 
		 //初始化距离矩阵
		 for (int i=0;i<distCbr.length;i++)
		   Arrays.fill(distCbr[i],Double.MAX_VALUE);
		 for(int i=0;i<distCbr[0].length;i++)   //对角线上的元素赋0值
		   distCbr[i][i] = 0.0;   
		 for (int i=0;i<distOn.length;i++)
		   Arrays.fill(distOn[i],Double.MAX_VALUE);
		 for(int i=0;i<distOn[0].length;i++)   //对角线上的元素赋0值
		   distOn[i][i] = 0.0;
		 for (int i=0;i<distPoss.length;i++)
		   Arrays.fill(distPoss[i],Double.MAX_VALUE);
		 for(int i=0;i<distPoss[0].length;i++)   //对角线上的元素赋0值
		   distPoss[i][i] = 0.0; 
		 for (int i=0;i<distSelf.length;i++)
		   Arrays.fill(distSelf[i],Double.MAX_VALUE);
		 for(int i=0;i<distSelf[0].length;i++)   //对角线上的元素赋0值
		   distPoss[i][i] = 0.0; 
		 
		 //初始化姻亲矩阵
		 for (int i=0;i<affiCbr.length;i++)
		   Arrays.fill(affiCbr[i],0.0);
		 for(int i=0;i<affiCbr[0].length;i++)   //对角线上的元素赋最大值
		   affiCbr[i][i] = Double.MAX_VALUE;
		 for (int i=0;i<affiOn.length;i++)
		   Arrays.fill(affiOn[i],0.0);
		 for(int i=0;i<affiOn[0].length;i++)   //对角线上的元素赋最大值
		   affiOn[i][i] = Double.MAX_VALUE; 
		 for (int i=0;i<affiPoss.length;i++)
		   Arrays.fill(affiPoss[i],0.0);
		 for(int i=0;i<affiPoss[0].length;i++)   //对角线上的元素赋最大值
		   affiPoss[i][i] = Double.MAX_VALUE; 
		 for (int i=0;i<affiSelf.length;i++)
		   Arrays.fill(affiSelf[i],0.0);
		 for(int i=0;i<affiSelf[0].length;i++)   //对角线上的元素赋最大值
		   affiSelf[i][i] = Double.MAX_VALUE; 
		
	}
	
	@Override
	public UtilizationModel getCpuUtilizationModel() {
		return new CpuUtilizationModel_Custom();
	}
	
	/**
	 * Submit cloudlets to the created VMs.
	 * 
	 * @pre $none
	 * @post $none
	 */
	protected void submitCloudlets() {
		//do nothing!
	}
	
	/**
	 * Process a cloudlet return event.  将执行结果传回设备，对应的通信是下载
	 * 
	 * @param ev a SimEvent object
	 * @pre ev != $null
	 * @post $none
	 */
	protected void processCloudletReturn(SimEvent ev) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();
		
		SimLogger.getInstance().taskExecuted(task.getCloudletId());

		if(task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID){  //云任务是从cloud返回的
			//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": task #" + task.getCloudletId() + " received from cloud");
			double WanDelay = networkModel.getDownloadDelay(SimSettings.CLOUD_DATACENTER_ID, task.getMobileDeviceId(), task);//广域网的延迟
			if(WanDelay > 0)
			{
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+WanDelay);
				if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())   //task提交时的服务Wlan和当前位置的服务Wlan相同，说明设备仍然在原wlan的覆盖范围
				{
					networkModel.downloadStarted(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID); //下载启动
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), WanDelay, NETWORK_DELAY_TYPES.WAN_DELAY);
					schedule(getId(), WanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);  //给getId()发送一个事件，从当前时刻开始WanDelay后，事件送达，事件内容是task
				}
				else
				{
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
				}
			}
			else
			{
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WAN_DELAY);  //广域网带宽不够
			}
		}
		else{   //云任务是从边缘服务器返回的
			//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": task #" + task.getCloudletId() + " received from edge");
			double WlanDelay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task);
			if(WlanDelay > 0)
			{
				Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+WlanDelay);
				if(task.getSubmittedLocation().getServingWlanId() == currentLocation.getServingWlanId())
				{
					networkModel.downloadStarted(currentLocation, SimSettings.GENERIC_EDGE_DEVICE_ID);
					SimLogger.getInstance().setDownloadDelay(task.getCloudletId(), WlanDelay, NETWORK_DELAY_TYPES.WLAN_DELAY);
					schedule(getId(), WlanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				}
				else
				{
					SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
				}
			}
			else
			{
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock(), NETWORK_DELAY_TYPES.WLAN_DELAY);
			}
		}
	}
	
	//处理几种事件，另外一个处理事件的方法processEvent在SimManager.java中
	protected void processOtherEvent(SimEvent ev) {
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(1);
			return;
		}
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		switch (ev.getTag()) {
			case REQUEST_RECEIVED_BY_CLOUD:   //被云收到
			{
				Task task = (Task) ev.getData();

				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);  //任务上传完成

				submitTaskToVm(task,0,SimSettings.CLOUD_DATACENTER_ID);  //将任务交给VM，VM有具体的类型，比如云类型，边缘类型等
				
				break;
			}
			case REQUEST_RECEIVED_BY_EDGE_DEVICE:   //被边缘服务器收到
			{
				Task task = (Task) ev.getData();
				
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);//任务上传完成
				
				submitTaskToVm(task, 0, SimSettings.GENERIC_EDGE_DEVICE_ID);  //将任务交给VM，VM有具体的类型，比如云类型，边缘类型等
				
				break;
			}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:    //移动设备收到一个响应，说明task从云上或边缘服务器上返回了（执行结束）
			{
				Task task = (Task) ev.getData();
				
				if(task.getAssociatedDatacenterId() == SimSettings.CLOUD_DATACENTER_ID)
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);//任务上传完成
				else if(task.getAssociatedDatacenterId() != SimSettings.MOBILE_DATACENTER_ID)
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID); //任务上传完成
							
				SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
				break;
			}
			default:
				SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
				System.exit(1);
				break;
		}
	}
	
	
    //用指定的任务特征创建task，并将task卸载到恰当的服务器执行
	public void submitTask(TaskProperty edgeTask) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		//create a task。按照任务特征edgeTask创建一个任务
		Task task = createTask(edgeTask);		
		
		//获取当前设备的位置
		Location currentLocation = SimManager.getInstance().getMobilityModel().
				getLocation(task.getMobileDeviceId(),CloudSim.clock());
		
		//set location of the mobile device which generates this task.设置任务的提交位置
		task.setSubmittedLocation(currentLocation);

		//add related task to log list 添加一条日志记录。所有的日志记录利用taskMap来管理，该Map的键是taskId，值是一个LogItem对象。对于一个task来说，只在创建的时候生成一条该task的日志记录。后续将由recordLog（）在任务结束、任务被VM拒绝、任务被WLan拒绝等时候对这条日志记录对象第计算各项性能指标。
		SimLogger.getInstance().addLog(task.getMobileDeviceId(),
				task.getCloudletId(),
				task.getTaskType(),
				(int)task.getCloudletLength(),
				(int)task.getCloudletFileSize(),
				(int)task.getCloudletOutputSize());
        //找到将task卸载的设备
		int nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task);
		
		if(nextHopId == SimSettings.CLOUD_DATACENTER_ID){ //卸载到云上
			double WanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task);
			
			if(WanDelay>0){   //广域网延迟大于0
				networkModel.uploadStarted(currentLocation, nextHopId);//上传开启
				SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());
				SimLogger.getInstance().setUploadDelay(task.getCloudletId(), WanDelay, NETWORK_DELAY_TYPES.WAN_DELAY);
				schedule(getId(), WanDelay, REQUEST_RECEIVED_BY_CLOUD, task);  //任务被调度并触发一个REQUEST_RECEIVED_BY_CLOUD事件，表示任务已被云收到
			}
			else
			{
				//SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
				SimLogger.getInstance().rejectedDueToBandwidth(
						task.getCloudletId(),
						CloudSim.clock(),
						SimSettings.VM_TYPES.CLOUD_VM.ordinal(),
						NETWORK_DELAY_TYPES.WAN_DELAY);  //由于广域网带宽不够，上传失败
			}
		}
		else if(nextHopId == SimSettings.GENERIC_EDGE_DEVICE_ID) {  //卸载到边缘服务器上
			double WlanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId,task);
		//	SimLogger.printLine("注意：getUploadDelay的参数是3个");
			
			if(WlanDelay > 0){ //无线局域网延迟>0
				networkModel.uploadStarted(currentLocation, nextHopId);
				schedule(getId(), WlanDelay, REQUEST_RECEIVED_BY_EDGE_DEVICE, task); //任务被调度并触发一个REQUEST_RECEIVED_BY_EDGE_DEVICE事件，表示任务已被边缘服务器收到
				SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());
				SimLogger.getInstance().setUploadDelay(task.getCloudletId(), WlanDelay, NETWORK_DELAY_TYPES.WLAN_DELAY);
			}
			else {
				SimLogger.getInstance().rejectedDueToBandwidth(
						task.getCloudletId(),
						CloudSim.clock(),
						SimSettings.VM_TYPES.EDGE_VM.ordinal(),
						NETWORK_DELAY_TYPES.WLAN_DELAY);
			}
		}
		else {
			SimLogger.printLine("Unknown nextHopId! Terminating simulation...");
			System.exit(1);
		}
		
	}
	
	//将task卸载到VM并执行,该函数是将task和VM绑定，VM由cloudsim最终执行的关键函数。由于task又是和设备绑定的，所以设备的移动会反应为VM的移动，就会带来成本的变化，进而带来成本优化的必要性。
	private void submitTaskToVm(Task task, double delay, int datacenterId){
		
		//select a VM。由EdgeOrchestrator为task分配一个可用的VM。
		Vm selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task, datacenterId);		
		int vmType = 0;
		if(datacenterId == SimSettings.CLOUD_DATACENTER_ID)
			vmType = SimSettings.VM_TYPES.CLOUD_VM.ordinal();
		else
			vmType = SimSettings.VM_TYPES.EDGE_VM.ordinal();
		
		if(selectedVM != null){
			if(datacenterId == SimSettings.CLOUD_DATACENTER_ID)  //task分配在云数据中心，云数据中心只有1个
				task.setAssociatedDatacenterId(SimSettings.CLOUD_DATACENTER_ID);
			else   //task分配在边缘数据中心
				task.setAssociatedDatacenterId(selectedVM.getHost().getDatacenter().getId());

			//save related host id task和主机绑定
			task.setAssociatedHostId(selectedVM.getHost().getId());
			
			//set related vm id task和vm绑定
			task.setAssociatedVmId(selectedVM.getId());
			
			
			//bind task to related VM
			getCloudletList().add(task);
			bindCloudletToVm(task.getCloudletId(),selectedVM.getId()); //云任务和VM绑定			
//			SimLogger.printLine(CloudSim.clock() + ": Cloudlet#" + task.getCloudletId() + " is submitted to VM#" + task.getVmId());
			//给vm所在的数据中心发送一个CLOUDLET_SUBMIT事件
			schedule(getVmsToDatacentersMap().get(task.getVmId()), delay, CloudSimTags.CLOUDLET_SUBMIT, task);  //给vm所在的数据中心发送一个CLOUDLET_SUBMIT事件，CLOUDLET_SUBMIT事件会导致cloudsim的数据中心去执行该任务
			SimLogger.getInstance().taskAssigned(task.getCloudletId(),
					selectedVM.getHost().getDatacenter().getId(),
					selectedVM.getHost().getId(),
					selectedVM.getId(),
					vmType);
		}
		else{
			//SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
			SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), vmType);
		}		
	}
	
	private Task createTask(TaskProperty edgeTask){
		UtilizationModel utilizationModel = new UtilizationModelFull(); /*UtilizationModelStochastic*///RAM和BW的利用率使用100%模型
		UtilizationModel utilizationModelCPU = getCpuUtilizationModel();

		Task task = new Task(edgeTask.getMobileDeviceId(), ++taskIdCounter,
				edgeTask.getLength(), edgeTask.getPesNumber(),
				edgeTask.getInputFileSize(), edgeTask.getOutputFileSize(),
				utilizationModelCPU, utilizationModel, utilizationModel);   //CPU,RAM,BW
		
		//set the owner of this task
		
		task.setUserId(this.getId());
		task.setTaskType(edgeTask.getTaskType());
		
		if (utilizationModelCPU instanceof CpuUtilizationModel_Custom) {
			((CpuUtilizationModel_Custom)utilizationModelCPU).setTask(task);
		}
		MyEdgeServerManager edgeServerManager =(MyEdgeServerManager)SimManager.getInstance().getEdgeServerManager();
	//	DefaultEdgeServerManager edgeServerManager =(DefaultEdgeServerManager)SimManager.getInstance().getEdgeServerManager();
		//以task为基础，建立服务和服务内的流量矩阵。目前共四个服务，后续，服务的数量和服务内部任务数量都可以增加。在EgdeCloudSim建立第一个任务的时候就开始建立服务。

    	if (svcCbr.size()<10)  svcCbr.add(task); 
		  else if (svcOn.size() <10)  svcOn.add(task);
		   else if (svcPoss.size() <10) svcPoss.add(task);
		    else if (svcSelf.size() <10) svcSelf.add(task);
		     else if(svcCbr.size()==10) {   //建立CBR服务的流量矩阵
		       if (cbrFinish == false)	 //这个变量保证了CBR流量矩阵只建一次，防止了多次重建，下同
		    	 try {
		    	   //SimLogger.printLine("建立CBR流量矩阵！");
		    	   String fileTrfc = "I:\\EdgeCloudSim-master\\EdgeCloudSim-master\\scripts\\sample_app6\\config\\traffic1.csv";//traffic1是拓扑，下同。
		    	   String fileCbr = "I:\\EdgeCloudSim-master\\EdgeCloudSim-master\\scripts\\sample_app6\\config\\cbr.csv"; //cbr是流量，下同。注意：应该读本项目所在的目录，暂且将就
		    	   RandomAccessFile inputTrfc = new RandomAccessFile(fileTrfc,"r");
		    	   RandomAccessFile inputCbr = new RandomAccessFile(fileCbr,"r");
	    	       String lineTrfc = inputTrfc.readLine();   //跳过表头 
	    	       String lineCbr = inputCbr.readLine();   //跳过表头 
	    	       lineTrfc = inputTrfc.readLine();   //读第一行 
	    	       lineCbr = inputCbr.readLine();   // 读第一行
	    	       int origin, dst;
	    	       
	    	       //读入两个文件，用来设置traffCbr矩阵的拓扑和流量
	    	       while (lineTrfc != null){   	
	    	      	    String[] res1 = lineTrfc.split(",");
	    	      	    String[] res2 = lineCbr.split(",");
	    	      	    origin = Integer.valueOf(res1[1]);   //起点的任务序号（服务内部）
	    	    	    dst = Integer.valueOf(res1[2]);      //终点的任务序号（服务内部）	    	    	    
	    	    	    traffCbr[origin][dst] = Double.valueOf(res2[2]).doubleValue();  //权重，即CBR流量值
	    	      //    SimLogger.print(" edge["+origin+"]["+dst+"]= "+edge[origin][dst]);
	    	    	    lineTrfc = inputTrfc.readLine();
	    	    	    lineCbr = inputCbr.readLine();
	    	    	    //SimLogger.print("traffCbr["+origin+"]["+dst+"]= "+traffCbr[origin][dst]);
	    	       }
	    	      cbrFinish = true;    
	    	      inputTrfc.close();
		    	  inputCbr.close();
		       }catch(FileNotFoundException e) {SimLogger.printLine("文件找不到！");}
		        catch(IOException e) {}
		      
		      //建立CBR距离矩阵和姻亲矩阵
		      int k=0,l=0;
		      for (int i=0;i<10;i++)
			   	for (int j=0;j<10;j++) {
			   	  Task task_i = svcCbr.get(i);
			   	  Task task_j = svcCbr.get(j);
			   	  Vm vm_i=null,  vm_j=null;   int s=0,t=0;
			   	  //查找task_i所在的host_i
			   	  labelOne:
				  for(k=0;k<SimSettings.getInstance().getNumOfEdgeHosts();k++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
				    Iterator<EdgeVM> iter = edgeServerManager.getVmList(k).iterator();
				    while(iter.hasNext()){
					   vm_i =iter.next();				
				       if (task_i.getVmId()==vm_i.getId())
				    	  break labelOne;
				    }
				  }				  
				  if (k>=40) {					
					DefaultCloudServerManager cloudServerManager =(DefaultCloudServerManager)SimManager.getInstance().getCloudServerManager();
					labelTwo:
					for(s=0;s<SimSettings.getInstance().getNumOfCloudHost();s++) {
					  Iterator<CloudVM> iter = cloudServerManager.getVmList(s).iterator();
					  while(iter.hasNext()){
						   vm_i =iter.next();				
					       if (task_i.getVmId()==vm_i.getId())
					    	  break labelTwo;
					  }
					}
				  }//if (k>=40)
				  if (s==SimSettings.getInstance().getNumOfCloudHost())  
				     //SimLogger.printLine("VM not found：CBR");
				 //查找task_j所在的host_j
				  labelTwo:
				  for(l=0;l<SimSettings.getInstance().getNumOfEdgeHosts();l++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
					Iterator<EdgeVM> iter = edgeServerManager.getVmList(l).iterator();
					while(iter.hasNext()){
					   vm_j =iter.next();				
					   if (task_j.getVmId()==vm_j.getId())
					 	  break labelTwo;
				    }
				  }				  
				  if (l>=40) {						
					DefaultCloudServerManager cloudServerManager =(DefaultCloudServerManager)SimManager.getInstance().getCloudServerManager();
					labelThree:
					for(t=0;t<SimSettings.getInstance().getNumOfCloudHost();t++) {
					  Iterator<CloudVM> iter = cloudServerManager.getVmList(t).iterator();
					  while(iter.hasNext()){
						   vm_i =iter.next();				
					       if (task_i.getVmId()==vm_i.getId())
					    	  break labelThree;
					  }
					}
				  }//if (l>=40)
				  if (t==SimSettings.getInstance().getNumOfCloudHost())  
					   //SimLogger.printLine("VM not found:CBR");  //task已执行结束
			   	  
				 //SimLogger.printLine(" k= "+k+"      l= "+l+"  s= "+s+"  t=  "+t);
				 //任务i和任务j之间的最短距离
				  if(k<40&&l<40)   //task都在边缘host上					    
					 distCbr[i][j] = edgeServerManager.dist(k, l);			   	    
			   	    else if (s==0||t==0) // 至少其中一个task在云上执行，这时任务间的距离设一个比较大的值（link文件中流量最大值的100倍）
					 distCbr[i][j] = 1051*100;
			   	      else  distCbr[i][j] = Double.MAX_VALUE;   //至少一个task已执行结束
				  if (traffCbr[i][j]==0&&distCbr[i][j]==0)   //task i和j在同一台主机上且之间无流量  
					 affiCbr[i][j] = 0;
				  else if (traffCbr[i][j]!=0&&distCbr[i][j]==0)
					 affiCbr[i][j] = 10000;  //有实际意义的姻亲最大值
				  else
					 affiCbr[i][j] = traffCbr[i][j]/distCbr[i][j]; 
			   	
			    } 
		      //SimLogger.printLine("CBR姻亲矩阵已建立！");
		      
		      //复制10个afficCbr		      
		      for(int i=0;i<10;i++) {   //经验证，已复制成功
		    	  System.arraycopy( affiCbr,0,affiCbrArray[i],0,affiCbr.length);
		      }
		      
		      
		      if(svcOn.size()==10){
		    	 if (onFinish == false)	
			    	try {
			    	  //SimLogger.printLine("建立ON-OFF流量矩阵！");
				      String fileTrfc = "I:\\EdgeCloudSim-master\\EdgeCloudSim-master\\scripts\\sample_app6\\config\\traffic2.csv";
				      String fileOn = "I:\\EdgeCloudSim-master\\EdgeCloudSim-master\\scripts\\sample_app6\\config\\on-off.csv"; //注意：应该读本项目所在的目录，暂且将就
				      RandomAccessFile inputTrfc = new RandomAccessFile(fileTrfc,"r");
				      RandomAccessFile inputOn = new RandomAccessFile(fileOn,"r");
			    	  String lineTrfc = inputTrfc.readLine();   //跳过表头 
			    	  String lineOn = inputOn.readLine();   //跳过表头 
			    	  lineTrfc = inputTrfc.readLine();   //读第一行 
			    	  lineOn = inputOn.readLine();   // 读第一行
			    	  int origin, dst;
			    	       
			    	  //读入两个文件，用来设置traffOn矩阵的拓扑和流量
			    	  while (lineTrfc != null){   	
			    	     String[] res1 = lineTrfc.split(",");
			    	     String[] res2 = lineOn.split(",");
			    	     origin = Integer.valueOf(res1[1]);   //起点的任务序号（服务内部）
			    	     dst = Integer.valueOf(res1[2]);      //终点的任务序号（服务内部）	    	    	    
			    	     traffOn[origin][dst] = Double.valueOf(res2[4]).doubleValue();  //权重，即ON-OFF流量值		    	      
			    	     lineTrfc = inputTrfc.readLine();
			    	     lineOn = inputOn.readLine(); 
			    	  }	    	 
			    	     
			    	   onFinish = true;
				       inputTrfc.close();
				       inputOn.close();
				    }catch(FileNotFoundException e) {}
				     catch(IOException e) {}
		    	 
		    	 //建立ON-OFF距离矩阵和姻亲矩阵
			     for (int i=0;i<10;i++)
				   for (int j=0;j<10;j++) {
				     Task task_i = svcOn.get(i);
				     Task task_j = svcOn.get(j);
				     Vm vm_i=null,  vm_j=null; int s=0,t=0;
				     //查找task_i所在的host_i				     
				     labelOne:
					 for(k=0;k<SimSettings.getInstance().getNumOfEdgeHosts();k++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
					   Iterator<EdgeVM> iter = edgeServerManager.getVmList(k).iterator();
					   while(iter.hasNext()){
					     vm_i =iter.next();				
					     if (task_i.getVmId()==vm_i.getId())
					    	  break labelOne;
					   }
					 }					 
					 if (k>=40) {					
						DefaultCloudServerManager cloudServerManager =(DefaultCloudServerManager)SimManager.getInstance().getCloudServerManager();
						labelTwo:
						for(s=0;s<SimSettings.getInstance().getNumOfCloudHost();s++) {
						  Iterator<CloudVM> iter = cloudServerManager.getVmList(s).iterator();
						  while(iter.hasNext()){
							   vm_i =iter.next();				
						       if (task_i.getVmId()==vm_i.getId())
						    	  break labelTwo;
						  }
						}
				     }//if (k>=40)
					 if (s==SimSettings.getInstance().getNumOfCloudHost())  
						 //  SimLogger.printLine("VM not found:ON");
 					 //查找task_j所在的host_j
					 labelTwo:
					 for(l=0;l<SimSettings.getInstance().getNumOfEdgeHosts();l++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
					   Iterator<EdgeVM> iter = edgeServerManager.getVmList(l).iterator();
					   while(iter.hasNext()){
						  vm_j =iter.next();				
						  if (task_j.getVmId()==vm_j.getId())
						      break labelTwo;
					  }
				     }
					 if (l>=40) {						
						DefaultCloudServerManager cloudServerManager =(DefaultCloudServerManager)SimManager.getInstance().getCloudServerManager();
						labelThree:
						for(t=0;t<SimSettings.getInstance().getNumOfCloudHost();t++) {
						  Iterator<CloudVM> iter = cloudServerManager.getVmList(t).iterator();
						  while(iter.hasNext()){
							   vm_i =iter.next();				
						       if (task_i.getVmId()==vm_i.getId())
						    	  break labelThree;
						  }
						}
					  }//if (l>=40)
					  if (t==SimSettings.getInstance().getNumOfCloudHost())  
						 //  SimLogger.printLine("VM not found:ON");	//task已执行结束			   	 
					 //SimLogger.printLine(" k= "+k+"      l= "+l+"  s= "+s+"  t=  "+t);					 	  
				   	 //任务i和任务j之间的最短距离
					  if(k<40&&l<40)  //task都在边缘host上					  
					   	  distOn[i][j] = edgeServerManager.dist(k, l);
					   	else if (s==0||t==0) // 至少其中一个task在云上执行，这时任务间的距离设一个比较大的值（link文件中流量最大值的100倍）
						  distOn[i][j] = 1051*100;
					   	   else  distOn[i][j] = Double.MAX_VALUE;   //至少一个task已执行结束
					  if (traffOn[i][j]==0&&distOn[i][j]==0)   //task i和j在同一台主机上且之间无流量  
						  affiOn[i][j] = 0;
					  else if (traffOn[i][j]!=0&&distOn[i][j]==0)
							 affiOn[i][j] = 10000;  //有实际意义的姻亲最大值
					  else 
						  affiOn[i][j] = traffOn[i][j]*100/distOn[i][j];   //乘以100是为了和其它类型服务的流量量级对齐
					  //SimLogger.printLine("  affiOn["+i+"]["+j+"]= "+affiOn[i][j]);
				     
				 }
			     for(int i=0;i<10;i++) {   //经验证，已复制成功
			    	  System.arraycopy( affiOn,0,affiOnArray[i],0,affiOn.length);
			     } 
                			     
		    	 if(svcPoss.size()==10){
		    	    if ( possFinish == false)
					   try {
						 //SimLogger.printLine("建立POSSION流量矩阵！");
						 String fileTrfc = "I:\\EdgeCloudSim-master\\EdgeCloudSim-master\\scripts\\sample_app6\\config\\traffic3.csv";
						 String filePoss = "I:\\EdgeCloudSim-master\\EdgeCloudSim-master\\scripts\\sample_app6\\config\\possion.csv"; //注意：应该读本项目所在的目录，暂且将就
						 RandomAccessFile inputTrfc = new RandomAccessFile(fileTrfc,"r");
						 RandomAccessFile inputPoss = new RandomAccessFile(filePoss,"r");
					     String lineTrfc = inputTrfc.readLine();   //跳过表头 
					     String linePoss = inputPoss.readLine();   //跳过表头 
					     lineTrfc = inputTrfc.readLine();   //读第一行 
				    	 linePoss = inputPoss.readLine();   // 读第一行
					     int origin, dst;
					    	       
					    	       //读入两个文件，用来设置traffPoss矩阵的拓扑和流量
					     while (lineTrfc != null){   	
					        String[] res1 = lineTrfc.split(",");
					        String[] res2 = linePoss.split(",");
					        origin = Integer.valueOf(res1[1]);   //起点的任务序号（服务内部）
					        dst = Integer.valueOf(res1[2]);      //终点的任务序号（服务内部）	    	    	    
					        traffPoss[origin][dst] = Double.valueOf(res2[0]).doubleValue();  //权重，即POSS流量值
					        lineTrfc = inputTrfc.readLine();
					    	linePoss = inputPoss.readLine(); 
					     } 
					    	    
					     possFinish = true;      	
						 inputTrfc.close();
						 inputPoss.close();
					  }catch(FileNotFoundException e) {}
					   catch(IOException e) {}
		    	  //建立Possion距离矩阵和姻亲矩阵
				  for (int i=0;i<10;i++)
					for (int j=0;j<10;j++) {
					  Task task_i = svcPoss.get(i);
					  Task task_j = svcPoss.get(j);
					  Vm vm_i=null,  vm_j=null; int s=0,t=0;
					  //查找task_i所在的host_i
					  labelOne:
					  for( k=0;k<SimSettings.getInstance().getNumOfEdgeHosts();k++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
					    Iterator<EdgeVM> iter = edgeServerManager.getVmList(k).iterator();
					    while(iter.hasNext()){
					      vm_i =iter.next();				
						  if (task_i.getVmId()==vm_i.getId())
						   	  break labelOne;
						}
					  }		
					  if (k>=40) {					
						DefaultCloudServerManager cloudServerManager =(DefaultCloudServerManager)SimManager.getInstance().getCloudServerManager();
						labelTwo:
						for(s=0;s<SimSettings.getInstance().getNumOfCloudHost();s++) {
						  Iterator<CloudVM> iter = cloudServerManager.getVmList(s).iterator();
						  while(iter.hasNext()){
							   vm_i =iter.next();				
						       if (task_i.getVmId()==vm_i.getId())
						    	  break labelTwo;
						  }
						}
					   }//if (k>=40)
				
					  if (s==SimSettings.getInstance().getNumOfCloudHost())  
						   //SimLogger.printLine("VM not found:POSS");
					  //查找task_j所在的host_j
					  labelTwo:
					  for( l=0;l<SimSettings.getInstance().getNumOfEdgeHosts();l++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
						Iterator<EdgeVM> iter = edgeServerManager.getVmList(l).iterator();
						while(iter.hasNext()){
						   vm_j =iter.next();				
						   if (task_j.getVmId()==vm_j.getId())
						 	  break labelTwo;  //注意：这里的标号和下面的labelTwo不会冲突，因为作用域不同,上同
						}
					  }
					  if (l>=40) {						
						DefaultCloudServerManager cloudServerManager =(DefaultCloudServerManager)SimManager.getInstance().getCloudServerManager();
						labelThree:
						for(t=0;t<SimSettings.getInstance().getNumOfCloudHost();t++) {
						  Iterator<CloudVM> iter = cloudServerManager.getVmList(t).iterator();
						  while(iter.hasNext()){
							   vm_i =iter.next();				
						       if (task_i.getVmId()==vm_i.getId())
						    	  break labelThree;
						  }
						}
					  }//if (l>=40)
					  if (t==SimSettings.getInstance().getNumOfCloudHost())  
					  //  SimLogger.printLine("VM not found:POSS");  //说明task已执行结束
				//	  SimLogger.printLine(" k= "+k+"      l= "+l+"  s= "+s+"  t=  "+t);
					  //任务i和任务j之间的最短距离
					  if(k<40&&l<40)  //task都在边缘host上					  
					   	  distPoss[i][j] = edgeServerManager.dist(k, l);
					    else if (s==0||t==0) // 至少其中一个task在云上执行，这时任务间的距离设一个比较大的值（link文件中流量最大值的100倍）
						  distPoss[i][j] = 1051*100;
					   	  else  distPoss[i][j] = Double.MAX_VALUE;   //至少一个task已执行结束
					  if (traffPoss[i][j]==0&&distPoss[i][j]==0)   //task i和j在同一台主机上且之间无流量  
						  affiPoss[i][j] = 0;
					  else if (traffPoss[i][j]!=0&&distPoss[i][j]==0)
							 affiPoss[i][j] = 10000;  //有实际意义的姻亲最大值
					  else
					      affiPoss[i][j] = traffPoss[i][j]*10000/distPoss[i][j]; //乘以10000是为了和其它类型服务的流量量级对齐
					 // SimLogger.printLine("  affiPoss["+i+"]["+j+"]= "+affiPoss[i][j]);
				  }
				  for(int i=0;i<10;i++) {   //经验证，已复制成功
			    	  System.arraycopy( affiPoss,0,affiPossArray[i],0,affiPoss.length);
			      }
				  
				  if(svcSelf.size()==10){  //从这修改起
			    	    if ( selfFinish == false)
						   try {
							 //SimLogger.printLine("建立Self流量矩阵！");
							 String fileTrfc = "I:\\EdgeCloudSim-master\\EdgeCloudSim-master\\scripts\\sample_app6\\config\\traffic4.csv";
							 String fileSelf = "I:\\EdgeCloudSim-master\\EdgeCloudSim-master\\scripts\\sample_app6\\config\\self.csv"; //注意：应该读本项目所在的目录，暂且将就
							 RandomAccessFile inputTrfc = new RandomAccessFile(fileTrfc,"r");
							 RandomAccessFile inputSelf = new RandomAccessFile(fileSelf,"r");
						     String lineTrfc = inputTrfc.readLine();   //跳过表头 
						     String lineSelf = inputSelf.readLine();   //跳过表头 
						     lineTrfc = inputTrfc.readLine();   //读第一行 
					    	 lineSelf = inputSelf.readLine();   //读第一行
						     int origin, dst;
						    	       
						     //读入两个文件，用来设置traffSelf矩阵的拓扑和流量
						     while (lineTrfc != null){   	
						        String[] res1 = lineTrfc.split(",");
						        String[] res2 = lineSelf.split(",");
						        origin = Integer.valueOf(res1[1]);   //起点的任务序号（服务内部）
						        dst = Integer.valueOf(res1[2]);      //终点的任务序号（服务内部）	    	    	    
						        traffSelf[origin][dst] = Double.valueOf(res2[2]).doubleValue();  //权重，即self流量值
						        lineTrfc = inputTrfc.readLine();
						    	lineSelf = inputSelf.readLine(); 
						     } 
						    	    
						     selfFinish = true;      	
							 inputTrfc.close();
							 inputSelf.close();
						  }catch(FileNotFoundException e) {}
						   catch(IOException e) {}
			    	  //建立Self距离矩阵和姻亲矩阵
					  for (int i=0;i<10;i++)
						for (int j=0;j<10;j++) {
						  Task task_i = svcSelf.get(i);
						  Task task_j = svcSelf.get(j);
						  Vm vm_i=null,  vm_j=null; int s=0,t=0;
						  //查找task_i所在的host_i
						  labelOne:
						  for( k=0;k<SimSettings.getInstance().getNumOfEdgeHosts();k++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
						    Iterator<EdgeVM> iter = edgeServerManager.getVmList(k).iterator();
						    while(iter.hasNext()){
						      vm_i =iter.next();				
							  if (task_i.getVmId()==vm_i.getId())
							   	  break labelOne;
							}
						  }		
						  if (k>=40) {					
							DefaultCloudServerManager cloudServerManager =(DefaultCloudServerManager)SimManager.getInstance().getCloudServerManager();
							labelTwo:
							for(s=0;s<SimSettings.getInstance().getNumOfCloudHost();s++) {
							  Iterator<CloudVM> iter = cloudServerManager.getVmList(s).iterator();
							  while(iter.hasNext()){
								   vm_i =iter.next();				
							       if (task_i.getVmId()==vm_i.getId())
							    	  break labelTwo;   //注意：这里的标号和下面的labelTwo不会冲突，因为作用域不同
							  }
							}
						   }//if (k>=40)
						   if (s==SimSettings.getInstance().getNumOfCloudHost())  
							  // SimLogger.printLine("VM not found:Self");
						  //查找task_j所在的host_j
						  labelTwo:
						  for( l=0;l<SimSettings.getInstance().getNumOfEdgeHosts();l++) {  //遍历边缘数据中心的所有主机，主机数目是在配置文档中设置的				
							Iterator<EdgeVM> iter = edgeServerManager.getVmList(l).iterator();
							while(iter.hasNext()){
							   vm_j =iter.next();				
							   if (task_j.getVmId()==vm_j.getId())
							 	  break labelTwo;
							}
						  }
						  if (l>=40) {						
							DefaultCloudServerManager cloudServerManager =(DefaultCloudServerManager)SimManager.getInstance().getCloudServerManager();
							labelThree:
							for(t=0;t<SimSettings.getInstance().getNumOfCloudHost();t++) {
							  Iterator<CloudVM> iter = cloudServerManager.getVmList(t).iterator();
							  while(iter.hasNext()){
								   vm_i =iter.next();				
							       if (task_i.getVmId()==vm_i.getId())
							    	  break labelThree;
							  }
							}
						  }//if (l>=40)
						  if (t==SimSettings.getInstance().getNumOfCloudHost())  
						    //SimLogger.printLine("VM not found:Self");  //说明task已执行结束
					//	  SimLogger.printLine(" k= "+k+"      l= "+l+"  s= "+s+"  t=  "+t);
						  //任务i和任务j之间的最短距离
						  if(k<40&&l<40)  //task都在边缘host上					  
						   	  distSelf[i][j] = edgeServerManager.dist(k, l);
						    else if (s==0||t==0) // 至少其中一个task在云上执行，这时任务间的距离设一个比较大的值（link文件中流量最大值的100倍）
							  distSelf[i][j] = 1051*100;
						   	  else  distSelf[i][j] = Double.MAX_VALUE;   //至少一个task已执行结束
						  if (traffSelf[i][j]==0&&distSelf[i][j]==0)   //task i和j在同一台主机上且之间无流量  
							  affiSelf[i][j] = 0;
						  else if (traffSelf[i][j]!=0&&distSelf[i][j]==0)
								 affiSelf[i][j] = 10000;  //有实际意义的姻亲最大值
						  else
						      affiSelf[i][j] = traffSelf[i][j]*10000/distSelf[i][j]; //乘以10000是为了和其它类型服务的流量量级对齐
						 // SimLogger.printLine("  affiPoss["+i+"]["+j+"]= "+affiPoss[i][j]);
				   }
				   for(int i=0;i<10;i++) {   //经验证，已复制成功
				   	  System.arraycopy( affiSelf,0,affiSelfArray[i],0,affiSelf.length);
				   }		
				 } //if(svcSelf.size()==10)
			   }//if(svcPoss.size()==10)
			 }//if(svcOn.size()==10)	 
		   }//else if(svcCbr.size()==10)
		
			
		return task;
	}
	
	//以下是新添加的函数
	public ArrayList<Task> getSvcCbr(){
        return svcCbr;
	}
	
	public ArrayList<Task> getSvcOn(){
        return svcOn;
	}
	
	public ArrayList<Task> getSvcPoss(){
        return svcPoss;
	}
	
	public ArrayList<Task> getSvcSelf(){
        return svcSelf;
	}
	
	public double[][] getAffiCbr(){
		return affiCbr;
	}
	
	public double[][] getAffiOn(){
		return affiOn;
	}
	
	public double[][] getAffiPoss(){
		return affiPoss;
	}
	
	public double[][] getAffiSelf(){
		return affiSelf;
	}
	
	public double[][][] getAffiCbrArray(){
		return affiCbrArray;
	}
	public double[][][] getAffiOnArray(){
		return affiOnArray;
	}
	public double[][][] getAffiPossArray(){
		return affiPossArray;
	}
	
	public double[][][] getAffiSelfArray(){
		return affiSelfArray;
	}
}