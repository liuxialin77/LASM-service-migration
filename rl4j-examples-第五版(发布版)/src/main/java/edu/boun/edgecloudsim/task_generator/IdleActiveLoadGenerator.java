/*
 * Title:        EdgeCloudSim - Idle/Active Load Generator implementation
 * 
 * Description: 
 * IdleActiveLoadGenerator implements basic load generator model where the
 * mobile devices generate task in active period and waits in idle period.
 * Task interarrival time (load generation period), Idle and active periods
 * are defined in the configuration file.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.task_generator;

import java.util.ArrayList;

import org.apache.commons.math3.distribution.ExponentialDistribution;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;
//为每个移动设备生成任务，任务特征从taskLookUpTable表中读取，将任务特征添加到taskList上
public class IdleActiveLoadGenerator extends LoadGeneratorModel{
	int taskTypeOfDevices[];
	//_numberOfMobileDevices是移动设备的数量
	public IdleActiveLoadGenerator(int _numberOfMobileDevices, double _simulationTime, String _simScenario) {  
		super(_numberOfMobileDevices, _simulationTime, _simScenario);
	}

	@Override //初始化任务特征列表taskList，每个设备在taskList上对应一项，表示设备的任务按这种应用的特征执行
	public void initializeModel() {
		taskList = new ArrayList<TaskProperty>();  //与父类LoadGeneratorModel中的taskList有何关系？通过打印语句验证，两者值是相等的。
		
		//exponential number generator for file input size （输入大小）, file output size（输出大小） and task length（任务长度）。length表示任务数
		ExponentialDistribution[][] expRngList = new ExponentialDistribution[SimSettings.getInstance().getTaskLookUpTable().length][3];
		
		//create random number generator for each place
		for(int i=0; i<SimSettings.getInstance().getTaskLookUpTable().length; i++) { //i表示第i个任务类型，一共四种类型，所以，i不超过4
			if(SimSettings.getInstance().getTaskLookUpTable()[i][0] ==0)  //[0]是使用百分比
				continue;
			//使用百分比不为0，产生三个指数分布（泊松分布）概率模型
			expRngList[i][0] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][5]);//平均上传大小
			expRngList[i][1] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][6]);//平均下载大小
			expRngList[i][2] = new ExponentialDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][7]);//平均任务长度
		}
		
		//Each mobile device utilizes an app type (task type) 给每个设备随机分配一个任务类型
		taskTypeOfDevices = new int[numberOfMobileDevices]; //每个设备有一个任务类型，numberOfMobileDevices继承自父类LoadGeneratorModel
		for(int i=0; i<numberOfMobileDevices; i++) {  //i表示第i个设备
			int randomTaskType = -1;
			double taskTypeSelector = SimUtils.getRandomDoubleNumber(0,100);   //生成一个0-100之间的随机数
			double taskTypePercentage = 0;
			for (int j=0; j<SimSettings.getInstance().getTaskLookUpTable().length; j++) {   //j表示第j个任务类型
				taskTypePercentage += SimSettings.getInstance().getTaskLookUpTable()[j][0];  //使用百分比累加，这个数是乘以100的值
				if(taskTypeSelector <= taskTypePercentage){   //累加的百分比超过了随机数taskTypeSelector
					randomTaskType = j;       
					break;
				}
			}
			if(randomTaskType == -1){
				SimLogger.printLine("Impossible is occurred! no random task type!");
				continue;
			}
			
			taskTypeOfDevices[i] = randomTaskType;   //设备i上的任务类型是如上随机确定的
			
			double poissonMean = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][2]; //读取类型randomTaskType的平均到达间隔
			double activePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][3];//读取该类型任务的活跃时间
			double idlePeriod = SimSettings.getInstance().getTaskLookUpTable()[randomTaskType][4];  //读取该类型任务的空闲时间
			double activePeriodStartTime = SimUtils.getRandomDoubleNumber(  //活跃周期的启动时间，是个随机数,在CLIENT_ACTIVITY_START_TIME和CLIENT_ACTIVITY_START_TIME + activePeriod两个时间之间
					SimSettings.CLIENT_ACTIVITY_START_TIME, 
					SimSettings.CLIENT_ACTIVITY_START_TIME + activePeriod);  //active period starts shortly after the simulation started (e.g. 10 seconds)
			double virtualTime = activePeriodStartTime;

			ExponentialDistribution rng = new ExponentialDistribution(poissonMean);	//生成一个平均值为poissonMean的泊松对象	 	
			while(virtualTime < simulationTime) {  //simulationTime是1800秒。如果模拟还未结束，就一直生成task
				double interval = rng.sample(); //产生一个符合泊松分布的随机数，作为任务到达的间隔时间

				if(interval <= 0){
					SimLogger.printLine("Impossible is occurred! interval is " + interval + " for device " + i + " time " + virtualTime);
					continue;
				}
				//SimLogger.printLine(virtualTime + " -> " + interval + " for device " + i + " time ");
				virtualTime += interval; //经过这个时间间隔后再启动任务
				
				if(virtualTime > activePeriodStartTime + activePeriod){ //如果时间到了任务的空闲周期
					activePeriodStartTime = activePeriodStartTime + activePeriod + idlePeriod;  //活跃周期启动时间推后，跳过空闲期
					virtualTime = activePeriodStartTime;
					continue;
				}
				//给任务列表添加一个新的任务特征，这种类型的任务是设备i产生的，任务类型是randomTaskType，启动时间是virtualTime，输入大小、输出大小和任务长度是符合泊松分布的随机数
				//这种特征的任务，会在virtualTime自动生成，以模拟设备随机生成任务
				taskList.add(new TaskProperty(i,randomTaskType, virtualTime, expRngList));
			//	SimLogger.printLine("taskList.size=  "+taskList.size());
			}
		}
	}

	@Override//返回设备的任务类型
	public int getTaskTypeOfDevice(int deviceId) {
		// TODO Auto-generated method stub
		return taskTypeOfDevices[deviceId];
	}

}
