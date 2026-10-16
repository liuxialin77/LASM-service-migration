/*
 * Title:        EdgeCloudSim - M/M/1 Queue model implementation
 * 
 * Description: 
 * MM1Queue implements M/M/1 Queue model for WLAN and WAN communication
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.network;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.utils.Location;

public class MM1Queue extends NetworkModel {
	private double WlanPoissonMean; //seconds
	private double WanPoissonMean; //seconds
	private double avgTaskInputSize; //bytes
	private double avgTaskOutputSize; //bytes
	private int maxNumOfClientsInPlace;

	public MM1Queue(int _numberOfMobileDevices, String _simScenario) {
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
	//计算上传延迟。wlan是无线局域网，wan是广域网
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
			delay = getWlanUploadDelay(accessPointLocation, CloudSim.clock(),task) +
					SimSettings.getInstance().getInternalLanDelay();
		}
		//mobile device to edge device (wifi access point)
		else if (destDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			delay = getWlanUploadDelay(accessPointLocation, CloudSim.clock(),task);
		}

		return delay;
	}
	
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task, EdgeHost host){
    
	    return 0;
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

	public int getMaxNumOfClientsInPlace(){
		return maxNumOfClientsInPlace;
	}
    
	
	//获取设备序号
	private int getDeviceCount(Location deviceLocation, double time){
		int deviceCount = 0;
        //当前时刻，哪个设备的位置等于deviceLocation
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

	private double getWlanDownloadDelay(Location accessPointLocation, double time) {
		return calculateMM1(0,
				SimSettings.getInstance().getWlanBandwidth(),
				WlanPoissonMean,
				avgTaskOutputSize,
				getDeviceCount(accessPointLocation, time));
	}

	private double getWlanUploadDelay(Location accessPointLocation, double time) {
		return calculateMM1(0,
				SimSettings.getInstance().getWlanBandwidth(),
				WlanPoissonMean,
				avgTaskInputSize,
				getDeviceCount(accessPointLocation, time));
	}
	
	//新添加的函数。考虑了task的输入大小
	private double getWlanUploadDelay(Location accessPointLocation, double time, Task task) {
		return calculateMM1(0,
				SimSettings.getInstance().getWlanBandwidth(),
				WlanPoissonMean,
				task.getCloudletFileSize(),
				getDeviceCount(accessPointLocation, time));
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
