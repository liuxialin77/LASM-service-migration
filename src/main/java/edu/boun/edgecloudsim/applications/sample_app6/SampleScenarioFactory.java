/*
 * Title:        EdgeCloudSim - Scenario Factory
 * 
 * Description:  Sample scenario factory providing the default
 *               instances of required abstract classes
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app6;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.edge_orchestrator.BasicEdgeOrchestrator;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.*;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_client.DefaultMobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.DefaultMobileServerManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.mobility.*;
import edu.boun.edgecloudsim.task_generator.IdleActiveLoadGenerator;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.*;

public class SampleScenarioFactory implements ScenarioFactory {
	private int numOfMobileDevice;       //移动设备的数目
	private double simulationTime;
	private String orchestratorPolicy;
	private String simScenario;
	
	SampleScenarioFactory(int _numOfMobileDevice,
			double _simulationTime,
			String _orchestratorPolicy,
			String _simScenario){
		orchestratorPolicy = _orchestratorPolicy;
		numOfMobileDevice = _numOfMobileDevice;    
		simulationTime = _simulationTime;
		simScenario = _simScenario;
	}
	
	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		return new IdleActiveLoadGenerator(numOfMobileDevice, simulationTime, simScenario);
	}

	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		return new BasicEdgeOrchestrator(orchestratorPolicy, simScenario);
	}

	@Override
	public MobilityModel getMobilityModel() {
	//	return new NomadicMobility(numOfMobileDevice,simulationTime);
		return new EUARandomWalkMobility(numOfMobileDevice,simulationTime);
	}

	@Override
	public NetworkModel getNetworkModel() {
		 return new MyNetworkModel(numOfMobileDevice, simScenario);
		// return new MM1Queue(numOfMobileDevice, simScenario);
	}

	@Override
	public EdgeServerManager getEdgeServerManager() {
	//	return new DefaultEdgeServerManager();
		return new MyEdgeServerManager();
	}

	@Override
	public CloudServerManager getCloudServerManager() {
		return new DefaultCloudServerManager();
	}
	
	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		return new DefaultMobileDeviceManager();
	}

	@Override
	public MobileServerManager getMobileServerManager() {
		return new DefaultMobileServerManager();
	}
}
