/*
 * Title:        EdgeCloudSim - Simulation Manager
 * 
 * Description: 
 * SimManager is an singleton class providing many abstract classeses such as
 * Network Model, Mobility Model, Edge Orchestrator to other modules
 * Critical simulation related information would be gathered via this class 
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.core;

import java.io.IOException;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_server.EdgeVmAllocationPolicy_Custom;
import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.SimLogger;

public class SimManager extends SimEntity {    //父类是抽象类
	//定义一些事件。由processEvent()处理
	private static final int CREATE_TASK = 0;
	private static final int CHECK_ALL_VM = 1;
	private static final int GET_LOAD_LOG = 2;
	private static final int PRINT_PROGRESS = 3;
	private static final int STOP_SIMULATION = 4;
	
	private String simScenario;
	private String orchestratorPolicy;
	private int numOfMobileDevice;
	private NetworkModel networkModel;
	private MobilityModel mobilityModel;
	private ScenarioFactory scenarioFactory;
	private EdgeOrchestrator edgeOrchestrator;
	private EdgeServerManager edgeServerManager;
	private CloudServerManager cloudServerManager;
	private MobileServerManager mobileServerManager;
	private LoadGeneratorModel loadGeneratorModel;
	private MobileDeviceManager mobileDeviceManager;
	
	private static SimManager instance = null;
	
	public SimManager(ScenarioFactory _scenarioFactory, int _numOfMobileDevice, String _simScenario, String _orchestratorPolicy) throws Exception {
		super("SimManager");
		simScenario = _simScenario;
		scenarioFactory = _scenarioFactory;
		numOfMobileDevice = _numOfMobileDevice;
		orchestratorPolicy = _orchestratorPolicy;

		SimLogger.print("Creating tasks... ");
		loadGeneratorModel = scenarioFactory.getLoadGeneratorModel();
		loadGeneratorModel.initializeModel();  //初始化任务列表
		//SimLogger.printLine("taskList.size="+ loadGeneratorModel.getTaskList().size());
		SimLogger.printLine("Done, ");
		
		SimLogger.print("Creating device locations...");
		mobilityModel = scenarioFactory.getMobilityModel();
		mobilityModel.initialize(); //为每个设备初始化位置
		SimLogger.printLine("Done.");

		//Generate network model
		networkModel = scenarioFactory.getNetworkModel();
		networkModel.initialize();
		
		//Generate edge orchestrator
		edgeOrchestrator = scenarioFactory.getEdgeOrchestrator();
		edgeOrchestrator.initialize();	
		
		
		//Create Physical Servers on cloud
		cloudServerManager = scenarioFactory.getCloudServerManager();
		cloudServerManager.initialize();
		
		//Create Physical Servers on mobile devices.创建移动设备上的物理服务器
		mobileServerManager = scenarioFactory.getMobileServerManager();
		mobileServerManager.initialize();	
		
		//Create Physical Servers.原来是在cloudServerManager之前创建的，现在放到此处
		edgeServerManager = scenarioFactory.getEdgeServerManager();
		edgeServerManager.initialize();
		
		//Create Client Manager.创建移动设备上的客户端管理器
		mobileDeviceManager = scenarioFactory.getMobileDeviceManager();
		mobileDeviceManager.initialize();
		
		edgeServerManager.cloudletList = mobileDeviceManager.getCloudletList();  //getCloudletList（）是继承自DataBroker,功能是获取DataBroker的cloudList成员
		
		instance = this;
	}
	
	public static SimManager getInstance(){
		return instance;
	}
	
	/**
	 * Triggering CloudSim to start simulation
	 */
	//启动边缘数据中心、云数据中心和本地数据中心，并为任务创建虚拟机，让数据中心去执行这些虚拟机
	public void startSimulation() throws Exception{
		//Starts the simulation
		SimLogger.print(super.getName()+" is starting...");  //输出simManager is starting.
				
		//Start Edge Datacenters & Generate VMs
		edgeServerManager.startDatacenters();     //edgeServerManager的派生类是什么？DefaultEdgeServerManager是其中一个。创建数据中心时要指定vmAllocationPolicy,下同
		edgeServerManager.createVmList(mobileDeviceManager.getId());//按照边缘设备文档中的VM的特征创建vmList，此时还没有task在其上执行
		
		//Start Cloud Datacenters & Generate VMs
		cloudServerManager.startDatacenters();
		cloudServerManager.createVmList(mobileDeviceManager.getId());
		
		//Start Mobile Datacenters & Generate VMs
		mobileServerManager.startDatacenters();
		mobileServerManager.createVmList(mobileDeviceManager.getId());
		
		//SimLogger.printLine("mobileDeviceManager.cloudletList.size= "+mobileDeviceManager.getCloudletList().size()); //cloudletList.size=0，因为下一行还没执行
		CloudSim.startSimulation();   //建立了数据中心和vmList并提供了VM分配策略，便可以让Cloudsim开始模拟：任务提交给数据中心，由数据中心按照VM分配策略在某主机上找到VM，然后用该VM执行任务了
	}

	public String getSimulationScenario(){
		return simScenario;
	}

	public String getOrchestratorPolicy(){
		return orchestratorPolicy;
	}
	
	public ScenarioFactory getScenarioFactory(){
		return scenarioFactory;
	}
	
	public int getNumOfMobileDevice(){
		return numOfMobileDevice;
	}
	
	public NetworkModel getNetworkModel(){
		return networkModel;
	}

	public MobilityModel getMobilityModel(){
		return mobilityModel;
	}
	
	public EdgeOrchestrator getEdgeOrchestrator(){
		return edgeOrchestrator;
	}
	
	public EdgeServerManager getEdgeServerManager(){
		return edgeServerManager;
	}
	
	public CloudServerManager getCloudServerManager(){
		return cloudServerManager;
	}
	
	public MobileServerManager getMobileServerManager(){
		return mobileServerManager;
	}

	public LoadGeneratorModel getLoadGeneratorModel(){
		return loadGeneratorModel;
	}
	
	public MobileDeviceManager getMobileDeviceManager(){
		return mobileDeviceManager;
	}
	
	@Override
	//将所有云服务器、边缘服务器、移动设备服务器上的所有VM提交执行，submitVmList（）是cloudsim中的方法，意味着VM的真正执行。还要将移动设备上的任务调度
	//，使用的方法是schedule(),该方法也由cloudsim调用。
	public void startEntity() {
		int hostCounter=0;  //主机数目
        
		//遍历所有边缘数据中心上所有主机
		for(int i= 0; i<edgeServerManager.getDatacenterList().size(); i++) {  //i是数据中心下标
			List<? extends Host> list = edgeServerManager.getDatacenterList().get(i).getHostList();
			for (int j=0; j < list.size(); j++) {  //j是主机下标
				mobileDeviceManager.submitVmList(edgeServerManager.getVmList(hostCounter));//把边缘服务器上的VM提交执行，这里的submitVmList（）是cloudsim的方法，用来最终执行VmList
				hostCounter++;
			}
		}
		//遍历云数据中心的所有主机
		for(int i = 0; i<SimSettings.getInstance().getNumOfCloudHost(); i++) {
			mobileDeviceManager.submitVmList(cloudServerManager.getVmList(i));  //把云服务器上的VM提交执行,这里的submitVmList（）是cloudsim的方法，用来最终执行VmList
		}
		//遍历所有移动设备
		for(int i=0; i<numOfMobileDevice; i++){
			if(mobileServerManager.getVmList(i) != null)
				mobileDeviceManager.submitVmList(mobileServerManager.getVmList(i)); //把移动设备服务器上的VM提交执行,这里的submitVmList（）是cloudsim的方法，用来最终执行VmList
		}		
		
		//Creation of tasks are scheduled here!遍历任务特征列表的任务特征（这些任务有一个虚拟的启动时间），以任务特征规定的启动时间调度任务.
		for(int i=0; i< loadGeneratorModel.getTaskList().size(); i++)
			schedule(getId(), loadGeneratorModel.getTaskList().get(i).getStartTime(), CREATE_TASK, loadGeneratorModel.getTaskList().get(i));//调度一个任务，会触发一个CREATE_TASK事件
		
		//Periodic event loops starts from here!
		schedule(getId(), 5, CHECK_ALL_VM);
		schedule(getId(), SimSettings.getInstance().getSimulationTime()/100, PRINT_PROGRESS);
		schedule(getId(), SimSettings.getInstance().getVmLoadLogInterval(), GET_LOAD_LOG);
		schedule(getId(), SimSettings.getInstance().getSimulationTime(), STOP_SIMULATION);
		
		SimLogger.printLine("Done.");
	}

	@Override
	//另外一个处理事件的方法processOhterEvent在DefaultMobileDeviceManager.java中
	public void processEvent(SimEvent ev) {
		
		synchronized(this){
			switch (ev.getTag()) {
			case CREATE_TASK:   
				try {
					TaskProperty edgeTask = (TaskProperty) ev.getData();
					mobileDeviceManager.submitTask(edgeTask);  //创建一个任务特征为edgeTask的task，并将task卸载到云上或者边缘服务器上					
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(1);
				}
				break;
			case CHECK_ALL_VM:
				int totalNumOfVm = SimSettings.getInstance().getNumOfEdgeVMs();
				if(EdgeVmAllocationPolicy_Custom.getCreatedVmNum() != totalNumOfVm){
					SimLogger.printLine("All VMs cannot be created! Terminating simulation...");
					System.exit(1);
				}
				break;
			case GET_LOAD_LOG:
				SimLogger.getInstance().addVmUtilizationLog(
						CloudSim.clock(),
						edgeServerManager.getAvgUtilization(),
						cloudServerManager.getAvgUtilization(),
						mobileServerManager.getAvgUtilization());
				
				schedule(getId(), SimSettings.getInstance().getVmLoadLogInterval(), GET_LOAD_LOG);
				break;
			case PRINT_PROGRESS:
				int progress = (int)((CloudSim.clock()*100)/SimSettings.getInstance().getSimulationTime());
				if(progress % 10 == 0)
					SimLogger.print(Integer.toString(progress));
				else
					SimLogger.print(".");
				if(CloudSim.clock() < SimSettings.getInstance().getSimulationTime())
					schedule(getId(), SimSettings.getInstance().getSimulationTime()/100, PRINT_PROGRESS);

				break;
			case STOP_SIMULATION:
				SimLogger.printLine("100");
				CloudSim.terminateSimulation();
				try {
					SimLogger.getInstance().simStopped();
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
				break;
			default:
				SimLogger.printLine(getName() + ": unknown event type");
				break;
			}
		}
	}

	@Override
	public void shutdownEntity() {
		edgeServerManager.terminateDatacenters();
		cloudServerManager.terminateDatacenters();
		mobileServerManager.terminateDatacenters();
	}
}
