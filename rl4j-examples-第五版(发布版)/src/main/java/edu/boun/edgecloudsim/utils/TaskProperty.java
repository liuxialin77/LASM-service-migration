/*
 * Title:        EdgeCloudSim - EdgeTask
 * 
 * Description: 
 * A custom class used in Load Generator Model to store tasks information
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.utils;

import org.apache.commons.math3.distribution.ExponentialDistribution;

import edu.boun.edgecloudsim.core.SimSettings;


//描述任务的特征
public class TaskProperty {
	private double startTime;  // 启动时间
	private long length, inputFileSize, outputFileSize;  //任务长度，输入文件大小，输出文件大小
	private int taskType;   //任务类型
	private int pesNumber;     //所需的核数
	private int mobileDeviceId;   //任务所在的设备

	public TaskProperty(double _startTime, int _mobileDeviceId, int _taskType, int _pesNumber, long _length, long _inputFileSize, long _outputFileSize) {
		startTime=_startTime;       //启动时间
		mobileDeviceId=_mobileDeviceId;   //所在设备
		taskType=_taskType;    //任务类型
		pesNumber = _pesNumber;              
		length = _length;
		outputFileSize = _inputFileSize;
		inputFileSize = _outputFileSize;
	}

	public TaskProperty(int _mobileDeviceId, int _taskType, double _startTime, ExponentialDistribution[][] expRngList) {
		mobileDeviceId=_mobileDeviceId;
		startTime=_startTime;
		taskType=_taskType;

		inputFileSize = (long)expRngList[_taskType][0].sample(); //生成一个泊松分布的随机数，下同
		outputFileSize =(long)expRngList[_taskType][1].sample();
		length = (long)expRngList[_taskType][2].sample();

		pesNumber = (int)SimSettings.getInstance().getTaskLookUpTable()[_taskType][8];   //所需的核数
	}

	public TaskProperty(int mobileDeviceId, double startTime, ExponentialDistribution[] expRngList) {
		this.mobileDeviceId = mobileDeviceId;
		this.startTime = startTime;
		taskType = 0;
		inputFileSize = (long)expRngList[0].sample();
		outputFileSize = (long)expRngList[1].sample();
		length = (long) expRngList[2].sample();
		pesNumber = (int)SimSettings.getInstance().getTaskLookUpTable()[0][8];
	}

	public double getStartTime(){
		return startTime;
	}

	public long getLength(){
		return length;
	}

	public long getInputFileSize(){
		return inputFileSize;
	}

	public long getOutputFileSize(){
		return outputFileSize;
	}

	public int getTaskType(){
		return taskType;
	}

	public int getPesNumber(){
		return pesNumber;
	}

	public int getMobileDeviceId(){
		return mobileDeviceId;
	}
}
