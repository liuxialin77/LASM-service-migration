/*
 * Title:        EdgeCloudSim - Edge Server Manager
 * 
 * Description: 
 * DefaultEdgeServerManager is responsible for creating datacenters, hosts and VMs.
 * It also provides the list of VMs running on the hosts.
 * This information is critical for the edge orchestrator.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

//import org.apache.commons.math3.util.MultidimensionalCounter.Iterator;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;


//该类以datacener为单位对EdgeServer进行管理
public class DefaultEdgeServerManager extends EdgeServerManager{     //localDatacenters和vmList，cloudletList均在父类中，换句话说，所有边缘数据中心和所有VM都由DefaultEdgeServerManager统一管理
	private int hostIdCounter;  //边缘主机的编号，从0开始
	
	double edge[][] = new double[45][45]; //用矩阵存放主机的网络拓扑,边的权重表示距离/传输延迟。因为EdegeHost是40台,但id是从5到45.
	int origin,dst;
	public DefaultEdgeServerManager() {
		hostIdCounter = 0; //对所有的host统一编号   
	}

	//原版@Override
	/* 
	public void initialize() {
	}
    */
	
	public void initialize() { //利用这个方法对edge矩阵初始化
	  for (int i=0;i<edge.length;i++)
		  Arrays.fill(edge[i],Integer.MAX_VALUE);
	  for(int i=0;i<edge[0].length;i++)   //对角线上的元素赋0值
		 edge[i][i] = 0;                  
	}
	//原版
	/*
	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> hostList, int dataCenterIndex) {
		return new EdgeVmAllocationPolicy_Custom(hostList,dataCenterIndex);
	}
	*/
	
	@Override
	public VmAllocationPolicy getVmAllocationPolicy(List<? extends Host> hostList,List<? extends Cloudlet> cloudletList,int dataCenterIndex) {
		return new EdgeVmAllocationPolicy_Custom(hostList,cloudletList,dataCenterIndex);  //编号为dataCenterIndex的数据中心的主机所使用的分配策略
	}
	
	//创建所有的数据中心。从边缘设备文档中读取特征
	public void startDatacenters() throws Exception{
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");  //获取所有的“datacenter”节点
		
		for (int i = 0; i < datacenterList.getLength(); i++) {
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			localDatacenters.add(createDatacenter(i, datacenterElement));//localDatacenters在父类EdgeServerManager中。i是数据中心的编号，datacenterElement是文档中的数据中心节点元素
		}	
		
	    //	SimLogger.printLine("localDatacenters.size= "+localDatacenters.size());		
		//建立主机间的网络拓扑
		initialize();//初始化边的权重
		String file = "I:\\EdgeCloudSim-master\\EdgeCloudSim-master\\scripts\\sample_app6\\config\\link.csv"; //注意：应该读本项目所在的目录，暂且将就
		BufferedReader input = new BufferedReader(new FileReader(file));
        String line = input.readLine();       
        while (line != null){   //得到的edge是稀疏矩阵 	
      	    String[] res = line.split(",");
      	    origin = Integer.valueOf(res[1]);   //起点的主机id
    	    dst = Integer.valueOf(res[2]);      //终点的主机id   
      	    edge[origin][dst] = Double.valueOf(res[3]).doubleValue();  //权重，即欧几里得距离（看作传输延迟应该也可以）        
      	    line = input.readLine();
        }
        /*SimLogger.printLine("edge开始");
        for(int i=0;i<40;i++)
           for(int j=0;j<40;j++)
        	   if(j!=39)
        	     SimLogger.print(" edge["+i+"]["+j+"]= "+edge[i][j]);
        	   else SimLogger.printLine(" edge["+i+"]["+j+"]= "+edge[i][j]);
        */
	    input.close();
	   //dist(0); //求主机0到其它主机的最短距离
	}
	
	//从边缘设备文档（默认是edge_devices.xml）中读取特征,创建相应的边缘VM，添加到EdgeServerManager的vmList上
	public void createVmList(int brokerId){
		int hostCounter=0;  
		int vmCounter=0;
		
		//Create VMs for each hosts
		Document doc = SimSettings.getInstance().getEdgeDevicesDocument();
		NodeList datacenterList = doc.getElementsByTagName("datacenter");  //边缘数据中心的特征从文档中读取，获取的是全部"datacenter"元素
		for (int i = 0; i < datacenterList.getLength(); i++) {  //遍历每一个datacenter节点
			Node datacenterNode = datacenterList.item(i);
			Element datacenterElement = (Element) datacenterNode;
			NodeList hostNodeList = datacenterElement.getElementsByTagName("host");//获取全部"host"元素
			for (int j = 0; j < hostNodeList.getLength(); j++) {  //遍历每一个host节点，注意：每个datacenter包含一个host
				
				vmList.add(hostCounter, new ArrayList<EdgeVM>());  //在vmList的下标hostCounter处插入ArrayList<EdgeVM>
				
				Node hostNode = hostNodeList.item(j);
				Element hostElement = (Element) hostNode;
				NodeList vmNodeList = hostElement.getElementsByTagName("VM");//获取全部"VM"元素
				for (int k = 0; k < vmNodeList.getLength(); k++) {   //遍历每一个VM节点
					Node vmNode = vmNodeList.item(k);					
					Element vmElement = (Element) vmNode;

					String vmm = vmElement.getAttribute("vmm");
					int numOfCores = Integer.parseInt(vmElement.getElementsByTagName("core").item(0).getTextContent());
					double mips = Double.parseDouble(vmElement.getElementsByTagName("mips").item(0).getTextContent());
					int ram = Integer.parseInt(vmElement.getElementsByTagName("ram").item(0).getTextContent());
					long storage = Long.parseLong(vmElement.getElementsByTagName("storage").item(0).getTextContent());
					long bandwidth = SimSettings.getInstance().getWlanBandwidth() / (hostNodeList.getLength()+vmNodeList.getLength());
					
					//VM Parameters	vmCounter就作了id	
					EdgeVM vm = new EdgeVM(vmCounter, brokerId, mips, numOfCores, ram, bandwidth, storage, vmm, new CloudletSchedulerTimeShared());
					vmList.get(hostCounter).add(vm);  //把vm添加到主机hostCounter的ArrayList<EdgeVM>上
					vmCounter++;
				}

				hostCounter++;
			}
		}
	}
	
	public void terminateDatacenters(){
		for (Datacenter datacenter : localDatacenters) {
			datacenter.shutdownEntity();
		}
	}

	//average utilization of all VMs.把所有边缘数据中心上的主机的全部VM读取出来，求VM的CPU平均利用率
	public double getAvgUtilization(){
		double totalUtilization = 0;
		double vmCounter = 0;
		
		// for each datacenter...
		for(int i= 0; i<localDatacenters.size(); i++) {
			List<? extends Host> list = localDatacenters.get(i).getHostList();
			// for each host...
			for (int j=0; j < list.size(); j++) {
				Host host = list.get(j);
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(host.getId());
				//for each vm...
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					totalUtilization += vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					vmCounter++;
				}
			}
		}
		return totalUtilization / vmCounter;
	}
	
	
    //创建一个边缘数据中心，参数值从边缘设备文档中获取,index是数据中心的编号
	private Datacenter createDatacenter(int index, Element datacenterElement) throws Exception{
		String arch = datacenterElement.getAttribute("arch");
		String os = datacenterElement.getAttribute("os");
		String vmm = datacenterElement.getAttribute("vmm");
		double costPerBw = Double.parseDouble(datacenterElement.getElementsByTagName("costPerBw").item(0).getTextContent());
		double costPerSec = Double.parseDouble(datacenterElement.getElementsByTagName("costPerSec").item(0).getTextContent());
		double costPerMem = Double.parseDouble(datacenterElement.getElementsByTagName("costPerMem").item(0).getTextContent());
		double costPerStorage = Double.parseDouble(datacenterElement.getElementsByTagName("costPerStorage").item(0).getTextContent());
		
		List<EdgeHost> hostList=createHosts(datacenterElement);
		
		String name = "Datacenter_" + Integer.toString(index);
		double time_zone = 3.0;         // time zone this resource located
		LinkedList<Storage> storageList = new LinkedList<Storage>();	//we are not adding SAN devices by now

		// 5. Create a DatacenterCharacteristics object that stores the
		//    properties of a data center: architecture, OS, list of
		//    Machines, allocation policy: time- or space-shared, time zone
		//    and its price (G$/Pe time unit).
		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, costPerSec, costPerMem, costPerStorage, costPerBw);
		// 6. Finally, we need to create a PowerDatacenter object.
		Datacenter datacenter = null;
				
		//获取VM分配策略，index是数据中心编号，hostList是数据中心的所有主机，cloudletList是父类EdgeServerManager的成员（不一定所有cloudlet都在该数据中心运行），已由SimManager正确复制
		VmAllocationPolicy vm_policy = getVmAllocationPolicy(hostList,cloudletList,index);  
		//SimLogger.printLine("index= "+index);
		datacenter = new Datacenter(name, characteristics, vm_policy, storageList, 0);		
		
		return datacenter;
	}
	
	//为数据中心创建主机，主机的参数从边缘设备文档中获取
	private List<EdgeHost> createHosts(Element datacenterElement){

		// Here are the steps needed to create a PowerDatacenter:
		// 1. We need to create a list to store one or more Machines
		List<EdgeHost> hostList = new ArrayList<EdgeHost>();
		
		Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
		String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
		int wlan_id = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
		int x_pos = Integer.parseInt(location.getElementsByTagName("x_pos").item(0).getTextContent());
		int y_pos = Integer.parseInt(location.getElementsByTagName("y_pos").item(0).getTextContent());
		int placeTypeIndex = Integer.parseInt(attractiveness);

		NodeList hostNodeList = datacenterElement.getElementsByTagName("host"); //边缘设备文档中host节点只有1个，可自行添加
		for (int j = 0; j < hostNodeList.getLength(); j++) {
			Node hostNode = hostNodeList.item(j);
			
			Element hostElement = (Element) hostNode;
			int numOfCores = Integer.parseInt(hostElement.getElementsByTagName("core").item(0).getTextContent());
			double mips = Double.parseDouble(hostElement.getElementsByTagName("mips").item(0).getTextContent());
			int ram = Integer.parseInt(hostElement.getElementsByTagName("ram").item(0).getTextContent());
			long storage = Long.parseLong(hostElement.getElementsByTagName("storage").item(0).getTextContent());
			long bandwidth = SimSettings.getInstance().getWlanBandwidth() / hostNodeList.getLength();//wlan带宽由所有主机平分？
			
			// 2. A Machine contains one or more PEs or CPUs/Cores. Therefore, should
			//    create a list to store these PEs before creating
			//    a Machine.
			List<Pe> peList = new ArrayList<Pe>();

			// 3. Create PEs and add these into the list.
			//for a quad-core machine, a list of 4 PEs is required:
			for(int i=0; i<numOfCores; i++){
				peList.add(new Pe(i, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating
			}
			
			//4. Create Hosts with its id and list of PEs and add them to the list of machines
			EdgeHost host = new EdgeHost(
					hostIdCounter,    //主机id
					new RamProvisionerSimple(ram),
					new BwProvisionerSimple(bandwidth), //kbps
					storage,
					peList,
					new VmSchedulerSpaceShared(peList)
				);
			
			host.setPlace(new Location(placeTypeIndex, wlan_id, x_pos, y_pos));//设置主机的wlan_id,地理位置
			hostList.add(host);
			hostIdCounter++;  //hostIdCounter是成员变量，这里相当于全局变量
		}
		//SimLogger.printLine("hostList.size= " + hostList.size());

		return hostList;
	}
	
	//新添加的代码从这里开始
	static class Edge {    //内部类
        int target;    //终点
        double  weight;   //边的权重

        Edge(int target, double weight) {
            this.target = target;
            this.weight = weight;
        }
    }    
	
	//求起点start到图graph上每一个节点的距离，以数组返回。
    public static double[] dijkstra(List<List<Edge>> graph, int start) {
        int n = graph.size();
        double[] dist = new double[n];  
        Arrays.fill(dist, Integer.MAX_VALUE);//初始化start距离其它节点的距离
        dist[start] = 0;  //修改start距离起点start的距离
        
        //这里的PriorityQueue<int[]>意味着队列中的元素是整型数组。通过构造函数传入的Comparator.comparingInt(a -> a[1])指定了一个比较器，用于确定队列中元素的排序方式。
        //这个比较器告诉PriorityQueue如何比较两个int[]数组。它基于数组中索引为1的元素（即第二个元素，因为数组索引从0开始）的整数值来进行比较。
        //这意味着队列会根据每个数组第二个元素的值来排序，第二个元素值较小的数组会被认为“较小”，从而排在队列的前面。
        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
        pq.offer(new double[]{start, 0});  //将起点和当前最小距离插入队列，由于该算法中，int[]数组中存放的是两个元素，起点和距离，所以这两个元素必须是同类型

        while (!pq.isEmpty()) {
            double[] current = pq.poll();
            int u = (int)current[0];

            for (Edge edge : graph.get(u)) {
                int v = edge.target;
                double weight = edge.weight;
                if (dist[u] + weight < dist[v]) {
                    dist[v] = dist[u] + weight;                             
                    pq.offer(new double[]{v, dist[v]});
                }
            }
        }
       
        return dist;
    }   
    
    //基于dijkstra方法，计算两点的最短距离
    public double dist(int start,int end) {
        int n = 40; // 图的顶点数
        List<List<Edge>> graph = new ArrayList<>();  //第一维，表示边的起点
        for (int i = 0; i < n; i++) 
            graph.add(new ArrayList<>());     //第二维，表示边的终点
                
        for (int i=0;i<n;i++)   //i是边的起点
          for (int j=0;j<n;j++){  //j是边的终点      	
        	 if (edge[i][j]!=Integer.MAX_VALUE)        	 
        	  graph.get(i).add(new Edge(j,edge[i][j]));   //给图中添加一条边，端点是i和j，权重是 edge[i][j]。将矩阵edge转换成了图
        	 
          }
        double[] dist = dijkstra(graph, start);    //求从start到graph上所有其它顶点的最短距离
        /*
        for (int i = 0; i < dist.length; i++) {
        	if (dist[i] >= Integer.MAX_VALUE)
        		SimLogger.printLine("到顶点 " + i + " 的最短距离：" + "无穷大");
        	else
                SimLogger.printLine("到顶点 " + i + " 的最短距离：" + dist[i]);
        }
        */
        //SimLogger.printLine("从顶点 " + start + " 到顶点"+end+"的最短距离："+dist[end]); //end是终点
        if (end>=40)  {
        	SimLogger.printLine("终点非法！");
        	return Integer.MAX_VALUE; 
        }
        return dist[end];
       
    }  
    double[][] getEdge(){
    	return edge;    	
    }

}
