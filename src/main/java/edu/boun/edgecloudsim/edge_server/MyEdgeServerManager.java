package edu.boun.edgecloudsim.edge_server;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
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
import edu.boun.edgecloudsim.edge_server.DefaultEdgeServerManager.Edge;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.*;


public class MyEdgeServerManager extends EdgeServerManager {
	private int hostIdCounter;	
	double edge[][] = new double[45][45]; //用矩阵存放主机的网络拓扑,边的权重表示距离/传输延迟。因为EdegeHost是40台,但id是从5到45.
	int origin,dst;    
    // ==================== EUA数据相关 ====================
    private static final double REF_LON = 144.9631;
    private static final double REF_LAT = -37.8136;
    private static final double EARTH_RADIUS = 6378137.0;
    
    /** EUA边缘服务器数据文件路径 */
    private static final String EUA_SERVER_FILE = "scripts/sample_app6/config/site-optus-melbCBD.csv";
    
    /** 从EUA加载的服务器位置列表 (x, y坐标，单位: 米) */
    private List<double[]> euaServerPositions;
    
    public MyEdgeServerManager() {
        super();
        hostIdCounter = 0;  //对所有的host统一编号 
        // ★ 在构造函数中加载EUA数据
        loadEUAServerData();
    }
    
    public void initialize() { //利用这个方法对edge矩阵初始化
  	  for (int i=0;i<edge.length;i++)
  		  Arrays.fill(edge[i],Integer.MAX_VALUE);
  	  for(int i=0;i<edge[0].length;i++)   //对角线上的元素赋0值
  		 edge[i][i] = 0;                  
  	}
    
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
  	
	
    /**
     * 加载EUA边缘服务器位置数据
     */
    
    private void loadEUAServerData() {
        euaServerPositions = new ArrayList<>();
        
        // 构建完整路径（与users-melbcbd-generated.csv同目录）
        String basePath = "scripts/sample_app6/config/";
        String filePath = basePath + "site-optus-melbCBD.csv";
        
        // 如果相对路径不行，尝试绝对路径
        // String filePath = "I:/EdgeCloudSim-master/EdgeCloudSim-master/scripts/sample_app6/config/site-optus-melbCBD.csv";
        
        SimLogger.printLine("EUA Server: Loading from " + filePath);
        
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;
            int loadedCount = 0;
            
            while ((line = br.readLine()) != null) {
                lineNumber++;
                
                if (line.trim().isEmpty()) continue;
                
                // 去除BOM
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                
                // 跳过表头（如果存在）
                if (lineNumber == 1 && (line.toLowerCase().contains("lat") || 
                                        line.toLowerCase().contains("long") ||
                                        line.toLowerCase().contains("site") ||
                                        line.toLowerCase().contains("id"))) {
                    SimLogger.printLine("EUA Server: Skipping header: " + line);
                    continue;
                }
                
                // ★ 处理可能的多种分隔符
                String[] parts = null;
                if (line.contains(",")) {
                    parts = line.split(",");
                } else if (line.contains("\t")) {
                    parts = line.split("\t");
                } else if (line.contains(";")) {
                    parts = line.split(";");
                }
                
                if (parts == null || parts.length < 2) {
                    SimLogger.printLine("EUA Server: Line " + lineNumber + " has only " + 
                                       (parts == null ? 0 : parts.length) + " parts, skipping");
                    continue;
                }
                
                try {
                    // ★ 尝试解析格式: site_id, latitude, longitude
                    // 或: latitude, longitude
                    double lat, lon;
                    int startIdx = 0;
                    
                    // 检测第一个字段是否是数字（纬度应该是负数或小数）
                    try {
                        double testVal = Double.parseDouble(parts[0].trim());
                        // 如果第一个字段是纬度（约-37.8），直接使用
                        if (testVal < 0 && testVal > -90) {
                            startIdx = 0;
                        } else {
                            // 否则可能是site_id，从第1个字段开始
                            startIdx = 1;
                        }
                    } catch (NumberFormatException e) {
                        // 第一个字段不是数字，尝试从第1个字段开始
                        startIdx = 1;
                    }
                    
                    // 确保有足够的字段
                    if (parts.length < startIdx + 2) {
                        SimLogger.printLine("EUA Server: Line " + lineNumber + " insufficient fields");
                        continue;
                    }
                    
                    lat = Double.parseDouble(parts[startIdx].trim());
                    lon = Double.parseDouble(parts[startIdx + 1].trim());
                    
                    // 墨卡托投影转换（与EUARandomWalkMobility保持一致）
                    double[] xy = toMercator(lon, lat);
                    euaServerPositions.add(xy);
                    loadedCount++;
                    
                } catch (NumberFormatException e) {
                    // 忽略解析失败的行
                    if (lineNumber < 10) {
                        SimLogger.printLine("EUA Server: Line " + lineNumber + " parse error: " + e.getMessage());
                    }
                    continue;
                }
            }
            
            SimLogger.printLine("EUA Server: Loaded " + loadedCount + " server positions from " + filePath);
            
        } catch (IOException e) {
            SimLogger.printLine("EUA Server: Failed to load file: " + e.getMessage());
            SimLogger.printLine("EUA Server: Using fallback random positions");
            generateFallbackServerPositions();
        }
        
        if (euaServerPositions.isEmpty()) {
            SimLogger.printLine("EUA Server: No data loaded, using fallback random positions");
            generateFallbackServerPositions();
        }
    }
    
    /**
     * 墨卡托投影转换
     */
    private double[] toMercator(double lon, double lat) {
        double lonRad = Math.toRadians(lon);
        double latRad = Math.toRadians(lat);
        double refLonRad = Math.toRadians(REF_LON);
        
        double x = EARTH_RADIUS * (lonRad - refLonRad);
        double y = EARTH_RADIUS * Math.log(Math.tan(Math.PI / 4 + latRad / 2));
        
        return new double[]{x, y};
    }
    
    /**
     * 生成备用随机位置
     */
    private void generateFallbackServerPositions() {
        int numOfServers = SimSettings.getInstance().getNumOfEdgeDatacenters();
        for (int i = 0; i < numOfServers; i++) {
            double x = SimUtils.getRandomNumber(-5000, 5000);
            double y = SimUtils.getRandomNumber(-5000, 5000);
            euaServerPositions.add(new double[]{x, y});
        }
        SimLogger.printLine("EUA Server: Generated " + numOfServers + " fallback random positions");
    }
    
    /**
     * 获取EUA服务器位置
     */
    public List<double[]> getEUAServerPositions() {
        return euaServerPositions;
    }
    
    // ==================== 重写 createHosts 方法 ====================
    
    
    protected List<EdgeHost> createHosts(Element datacenterElement) {
        List<EdgeHost> hostList = new ArrayList<EdgeHost>();
        
        // 获取位置信息
        Element location = (Element)datacenterElement.getElementsByTagName("location").item(0);
        String attractiveness = location.getElementsByTagName("attractiveness").item(0).getTextContent();
        int wlan_id = Integer.parseInt(location.getElementsByTagName("wlan_id").item(0).getTextContent());
        int placeTypeIndex = Integer.parseInt(attractiveness);
        
        // ★ 从EUA获取坐标（使用wlan_id作为索引，从EUA数据中取对应的位置）
        double x_pos, y_pos;
        if (euaServerPositions != null && euaServerPositions.size() > wlan_id) {
            double[] pos = euaServerPositions.get(wlan_id % euaServerPositions.size());
            x_pos = pos[0];
            y_pos = pos[1];
        } else {
            // 备选：从XML读取位置
            x_pos = Double.parseDouble(location.getElementsByTagName("x_pos").item(0).getTextContent());
            y_pos = Double.parseDouble(location.getElementsByTagName("y_pos").item(0).getTextContent());
        }
        
        // 创建主机
        NodeList hostNodeList = datacenterElement.getElementsByTagName("host");
        for (int j = 0; j < hostNodeList.getLength(); j++) {
            Node hostNode = hostNodeList.item(j);
            Element hostElement = (Element) hostNode;
            
            int numOfCores = Integer.parseInt(hostElement.getElementsByTagName("core").item(0).getTextContent());
            double mips = Double.parseDouble(hostElement.getElementsByTagName("mips").item(0).getTextContent());
            int ram = Integer.parseInt(hostElement.getElementsByTagName("ram").item(0).getTextContent());
            long storage = Long.parseLong(hostElement.getElementsByTagName("storage").item(0).getTextContent());
            long bandwidth = SimSettings.getInstance().getWlanBandwidth() / hostNodeList.getLength();
            
            List<Pe> peList = new ArrayList<Pe>();
            for (int i = 0; i < numOfCores; i++) {
                peList.add(new Pe(i, new PeProvisionerSimple(mips)));
            }
            
            EdgeHost host = new EdgeHost(
                hostIdCounter,
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bandwidth),
                storage,
                peList,
                new VmSchedulerSpaceShared(peList)
            );
            
            // ★ 设置主机的wlan_id和位置（使用从EUA获取的坐标）
            host.setPlace(new Location(placeTypeIndex, wlan_id, (int)x_pos, (int)y_pos));
            hostList.add(host);
            hostIdCounter++;
        }
        
        return hostList;
    }
    
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
