package edu.boun.edgecloudsim.network;

/*
 * Title:        EdgeCloudSim - M/M/1 Queue model implementation
 * 
 * Description: 
 * MM1Queue implements M/M/1 Queue model for WLAN and WAN communication
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */
import org.cloudbus.cloudsim.core.CloudSim;
import edu.boun.edgecloudsim.utils.SimLogger;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.utils.Location;

public class MyNetworkModel extends NetworkModel {
	private double WlanPoissonMean; //seconds
	private double WanPoissonMean; //seconds
	private double avgTaskInputSize; //bytes
	private double avgTaskOutputSize; //bytes
	private int maxNumOfClientsInPlace;

	public MyNetworkModel(int _numberOfMobileDevices, String _simScenario) {
		super(_numberOfMobileDevices, _simScenario);
	}

    //计算任务平均到达间隔时间和平均任务大小（输入大小、输出大小）
	@Override
	public void initialize() {
		WlanPoissonMean=0;
		WanPoissonMean=0;
		avgTaskInputSize=0;
		avgTaskOutputSize=0;
		maxNumOfClientsInPlace=0;

		//Calculate interarrival time and task sizes
		double numOfTaskType = 0;
		SimSettings SS = SimSettings.getInstance();
		for (int i=0; i<SimSettings.getInstance().getTaskLookUpTable().length; i++) {
			double weight = SS.getTaskLookUpTable()[i][0]/(double)100;  //[0]是使用百分比
			if(weight != 0) {
				WlanPoissonMean += (SS.getTaskLookUpTable()[i][2])*weight;

				double percentageOfCloudCommunication = SS.getTaskLookUpTable()[i][1];
				WanPoissonMean += (WlanPoissonMean)*((double)100/percentageOfCloudCommunication)*weight;

				avgTaskInputSize += SS.getTaskLookUpTable()[i][5]*weight;

				avgTaskOutputSize += SS.getTaskLookUpTable()[i][6]*weight;

				numOfTaskType++;
			}
		}

		WlanPoissonMean = WlanPoissonMean/numOfTaskType;
		avgTaskInputSize = avgTaskInputSize/numOfTaskType;
		avgTaskOutputSize = avgTaskOutputSize/numOfTaskType;
	}

	/**
	 * source device is always mobile device in our simulation scenarios!
	 */
	@Override
	//计算上传延迟。wlan是无线局域网，wan是广域网。由EdgeCloudsim自己的方法调用
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		double delay = 0;
		
		//由sourceDeviceId利用移动模型计算接入点位置
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(sourceDeviceId,CloudSim.clock());

		//mobile device to cloud server
		if(destDeviceId == SimSettings.CLOUD_DATACENTER_ID){
			double wlanDelay = getWlanUploadDelay(accessPointLocation, CloudSim.clock());  //从移动设备到边缘服务器
			double wanDelay = getWanUploadDelay(accessPointLocation, CloudSim.clock() + wlanDelay);  //从边缘服务器到云			
			if(wlanDelay > 0 && wanDelay >0)
				delay = wlanDelay + wanDelay;  //两部分延迟
		}
		//mobile device to edge orchestrator
		else if(destDeviceId == SimSettings.EDGE_ORCHESTRATOR_ID){
			//为什么是两部分？getWlanUploadDelay是从移动设备到边缘服务器的延迟，getInternalLanDelay是LAN间的延迟（边缘服务器到边缘协调器）
			delay = getWlanUploadDelay(accessPointLocation, CloudSim.clock()) +
					SimSettings.getInstance().getInternalLanDelay();
			
		}
		//mobile device to edge device (wifi access point)
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			delay = getWlanUploadDelay(accessPointLocation, CloudSim.clock());
			
		}

		return delay;
	}
	
	
	//新添加的函数。考虑了边缘主机的能力
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task, EdgeHost host) {
		double delay = 0;
		
		//利用移动模型计算设备sourceDeviceId当前时刻的接入点位置
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(sourceDeviceId,CloudSim.clock());

		//mobile device to cloud server
		if(destDeviceId == SimSettings.CLOUD_DATACENTER_ID){
			double wlanDelay = getWlanUploadDelay(accessPointLocation, CloudSim.clock(),task,host);  //从移动设备到边缘服务器
			double wanDelay = getWanUploadDelay(accessPointLocation, CloudSim.clock() + wlanDelay);  //从边缘服务器到云
			if(wlanDelay > 0 && wanDelay >0)
				delay = wlanDelay + wanDelay;  //两部分延迟			
		}
		//mobile device to edge orchestrator
		else if(destDeviceId == SimSettings.EDGE_ORCHESTRATOR_ID){
			
			delay = getWlanUploadDelay(accessPointLocation, CloudSim.clock(),task,host) +
					SimSettings.getInstance().getInternalLanDelay();
		}
		//mobile device to edge device (wifi access point)
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {  //从移动设备到边缘服务器
			delay = getWlanUploadDelay(accessPointLocation, CloudSim.clock(),task,host);
						
		}	

		return delay;
	}
	
	//新添加的函数。利用公式（7）计算回程,srcHost是接入host，dstHost是服务host.目前，调用该函数时，认为这两个host是同一个，所以该函数返回值为0.
    public double getBackDelay(Task task, EdgeHost srcHost,EdgeHost dstHost){
    	double delay = 0, λ =1.0;
    	if (srcHost == dstHost)   //接入host和服务host是同一个
    	   return 0;     
    	double dist = SimManager.getInstance().getEdgeServerManager().dist(srcHost.getId(),dstHost.getId());
    	if (dist>=Integer.MAX_VALUE )
    	  delay = Integer.MAX_VALUE;
    	else 
    	  delay = task.getCloudletFileSize()/SimSettings.getInstance().getManBandwidth() + 2*λ*dist;
    	
    	return delay;
    	
    }

	/**
	 * destination device is always mobile device in our simulation scenarios!
	 */
	@Override
	//计算下载延迟
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		//Special Case -> edge orchestrator to edge device
		if(sourceDeviceId == SimSettings.EDGE_ORCHESTRATOR_ID &&
				destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			return SimSettings.getInstance().getInternalLanDelay();  //返回局域网间延迟
		}

		double delay = 0;
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(destDeviceId,CloudSim.clock());

		//cloud server to mobile device
		if(sourceDeviceId == SimSettings.CLOUD_DATACENTER_ID){
			double wlanDelay = getWlanDownloadDelay(accessPointLocation, CloudSim.clock());//wlan延迟
			double wanDelay = getWanDownloadDelay(accessPointLocation, CloudSim.clock() + wlanDelay);//wan延迟
			if(wlanDelay > 0 && wanDelay >0)
				delay = wlanDelay + wanDelay;
		}
		//edge device (wifi access point) to mobile device
		else{
			delay = getWlanDownloadDelay(accessPointLocation, CloudSim.clock()); //只有wlan延迟

			EdgeHost host = (EdgeHost)(SimManager.
					getInstance().
					getEdgeServerManager().
					getDatacenterList().get(sourceDeviceId).
					getHostList().get(0));

			//if source device id is the edge server which is located in another location, add internal lan delay
			//in our scenario, serving wlan ID is equal to the host id, because there is only one host in one place
			if(host.getLocation().getServingWlanId() != accessPointLocation.getServingWlanId())
				delay += (SimSettings.getInstance().getInternalLanDelay() * 2);
		}

		return delay;
	}
	
	//新添加的函数
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task, EdgeHost host) {
		//Special Case -> edge orchestrator to edge device
		if(sourceDeviceId == SimSettings.EDGE_ORCHESTRATOR_ID &&
				destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){ //从边缘协调器向边缘服务器下载，跨越1个LAN
			return SimSettings.getInstance().getInternalLanDelay();  //返回局域网间延迟
		}

		double delay = 0;
		Location accessPointLocation = SimManager.getInstance().getMobilityModel().getLocation(destDeviceId,CloudSim.clock());//根据设备所在的位置获取接入点

		//cloud server to mobile device
		if(sourceDeviceId == SimSettings.CLOUD_DATACENTER_ID){   //从云向移动设备下载
			double wlanDelay = getWlanDownloadDelay(accessPointLocation, CloudSim.clock(),task,host);//wlan延迟.下载是给设备下载，所以使用接入点的位置，而不是task的位置
			double wanDelay = getWanDownloadDelay(accessPointLocation, CloudSim.clock() + wlanDelay);//wan延迟
			if(wlanDelay > 0 && wanDelay >0)
				delay = wlanDelay + wanDelay;
		}
		//edge device (wifi access point) to mobile device   //从边缘服务器向移动设备下载
		else{
			delay = getWlanDownloadDelay(accessPointLocation, CloudSim.clock(),task,host); //只有wlan延迟

			EdgeHost srcHost = (EdgeHost)(SimManager.     //获取task所在的主机
					getInstance().
					getEdgeServerManager().
					getDatacenterList().get(sourceDeviceId).
					getHostList().get(0));

			//if source device id is the edge server which is located in another location, add internal lan delay
			//in our scenario, serving wlan ID is equal to the host id, because there is only one host in one place
			if(srcHost.getLocation().getServingWlanId() != accessPointLocation.getServingWlanId())  //说明task和移动设备不在一处(因为设备是移动的)，下载要跨越2个LAN?
				delay += (SimSettings.getInstance().getInternalLanDelay() * 2);  
		}

		return delay;
	}


	public int getMaxNumOfClientsInPlace(){
		return maxNumOfClientsInPlace;
	}
    
	
	//获取设备序号
	private int getDeviceCount(Location deviceLocation, double time){
		int deviceCount = 0;
        //当前时刻time，哪些设备的位置等于deviceLocation
		for(int i=0; i<numberOfMobileDevices; i++) {
			Location location = SimManager.getInstance().getMobilityModel().getLocation(i,time);
			if(location.equals(deviceLocation))
				deviceCount++;
		}

		//record max number of client just for debugging
		if(maxNumOfClientsInPlace<deviceCount)
			maxNumOfClientsInPlace = deviceCount;

		return deviceCount;
	}
	
	
    //MM1模型计算的是排队延迟，没有考虑无线传输延迟
	private double calculateMM1(double propagationDelay, int bandwidth /*Kbps*/, double PoissonMean, double avgTaskSize /*KB*/, int deviceCount){
		double Bps=0, mu=0, lamda=0;

		avgTaskSize = avgTaskSize * (double)1000; //convert from KB to Byte

		Bps = bandwidth * (double)1000 / (double)8; //convert from Kbps to Byte per seconds
		lamda = ((double)1/(double)PoissonMean); //task per seconds
		mu = Bps / avgTaskSize ; //task per seconds
		double result = (double)1 / (mu-lamda*(double)deviceCount); //deviceCount越大，说明排在后面，等待时间长，上传延迟大

		result += propagationDelay;

		return (result > 5) ? -1 : result;
	}
	
	
	//利用公式（6）计算上行或下行延迟。
	private double calculateMyNetworkModel(Task task, double d, double bandWidth,boolean up){
		double SNR=0, transPower=0 /*dBm*/,σ2=0 /*dBm/Hz*/, g=0, g0=0.1, d0=0, γ=0;      
		
		transPower = 48;
	//	σ2 = 90; //或者是平方根
		σ2 = 9.5; 
	//	γ = 128+37*Math.log10(d);   //另一说，在（1.6-3.5）的范围中
		γ = 3.0; //或者3.0
		d0 = 1000;	//或者1.0	
		if(d==0) 
		  return 0;		
        g = g0*(Math.pow(d0/d, γ));  //公式（4）
       
        SNR = transPower*g/σ2;   //公式（3）
      
        double transRate = bandWidth*1024*1024/8*Math.log10(1+SNR);  //公式（5）.bandWidth的单位是Mbps
        
        double delay;
        
        if (up)
          delay = task.getCloudletFileSize()/transRate;  //CloudletFileSize的单位是Byte
        else delay = task.getCloudletOutputSize()/transRate;
		return delay;
        
	}
	

	private double getWlanDownloadDelay(Location accessPointLocation, double time) {
		return calculateMM1(0,
				SimSettings.getInstance().getWlanBandwidth(),
				WlanPoissonMean,
				avgTaskOutputSize,
				getDeviceCount(accessPointLocation, time));
	}
	
	//新添加的函数.复制的getWlanUploadDelay（）函数的代码，需检验正确性
	private double getWlanDownloadDelay(Location accessPointLocation, double time,Task task, EdgeHost host) {
   		    double delay = 0;
		    int xPos = accessPointLocation.getXPos();
		    int yPos = accessPointLocation.getYPos();		
		    int xPosHost = host.getLocation().getXPos();
		    int yPosHost = host.getLocation().getYPos();		
	        //  SimLogger.printLine("xPos= "+xPos+" yPos= "+yPos+" xPosHost= "+xPosHost+" yPosHost= "+yPosHost);
		    double dist = Math.sqrt(Math.pow(xPosHost-xPos,2)+Math.pow(yPosHost-yPos,2)); 
			return calculateMyNetworkModel(task,dist,SimSettings.getInstance().getWlanBandwidth(),false);
	}
	
	private double getWlanUploadDelay(Location accessPointLocation, double time) {
		return calculateMM1(0,
				SimSettings.getInstance().getWlanBandwidth(),
				WlanPoissonMean,
				avgTaskInputSize,
				getDeviceCount(accessPointLocation, time));
	}
	
	//新添加的函数。考虑了task的输入大小、位置移动和主机的能力
	private double getWlanUploadDelay(Location accessPointLocation, double time, Task task, EdgeHost host) {
		
		double delay = 0;
		int xPos = accessPointLocation.getXPos();
		int yPos = accessPointLocation.getYPos();		
		int xPosHost = host.getLocation().getXPos();
		int yPosHost = host.getLocation().getYPos();
		
	//	SimLogger.printLine("xPos= "+xPos+" yPos= "+yPos+" xPosHost= "+xPosHost+" yPosHost= "+yPosHost);
		double dist = Math.sqrt(Math.pow(xPosHost-xPos,2)+Math.pow(yPosHost-yPos,2)); 
	//	SimLogger.printLine("dist= "+dist);
		delay = calculateMyNetworkModel(task,dist,SimSettings.getInstance().getWlanBandwidth(),true);
	//	SimLogger.printLine("wlandelay = "+delay);
		return delay;  //计算task到host的上传延迟
	}
	
	
	

	private double getWanDownloadDelay(Location accessPointLocation, double time) {
		return calculateMM1(SimSettings.getInstance().getWanPropagationDelay(),
				SimSettings.getInstance().getWanBandwidth(),
				WanPoissonMean,
				avgTaskOutputSize,
				getDeviceCount(accessPointLocation, time));
	}
	
	

	private double getWanUploadDelay(Location accessPointLocation, double time) {
		return calculateMM1(SimSettings.getInstance().getWanPropagationDelay(),
				SimSettings.getInstance().getWanBandwidth(),
				WanPoissonMean,
				avgTaskInputSize,
				getDeviceCount(accessPointLocation, time));
	}
	 //上传启动
	@Override
	public void uploadStarted(Location accessPointLocation, int destDeviceId) {
		// TODO Auto-generated method stub

	}
    
	//上传完成
	@Override
	public void uploadFinished(Location accessPointLocation, int destDeviceId) {
		// TODO Auto-generated method stub

	}
    
	//下载启动
	@Override
	public void downloadStarted(Location accessPointLocation, int sourceDeviceId) {
		// TODO Auto-generated method stub

	}
	
    //下载完成
	@Override
	public void downloadFinished(Location accessPointLocation, int sourceDeviceId) {
		// TODO Auto-generated method stub

	}
}
