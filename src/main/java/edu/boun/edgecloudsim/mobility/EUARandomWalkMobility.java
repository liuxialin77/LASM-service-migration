/*
 * Title: EdgeCloudSim - EUA-based Random Walk Mobility Model
 */
package edu.boun.edgecloudsim.mobility;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.commons.math3.distribution.UniformRealDistribution;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class EUARandomWalkMobility extends MobilityModel {
    
    // ==================== Configuration Parameters ====================
    private double speedMps = 1.4;
    private double minSpeedMps = 0.5;
    private double maxSpeedMps = 3.0;
    private double directionChangeProb = 0.15;
    private double maxTurnAngle = Math.PI / 2;
    private double pauseProb = 0.05;
    private double minPauseDuration = 5.0;
    private double maxPauseDuration = 30.0;
    private double timeStep = 1.0;
    private boolean useFixedSpeed = true;
    private boolean enforceBoundary = true;
    private double boundaryRadius = 20000.0;
    
    // ==================== EUA Data and Projection ====================
    private static final double REF_LON = 144.9631;
    private static final double REF_LAT = -37.8136;
    private static final double EARTH_RADIUS = 6378137.0;
    
    /** EUA数据文件路径 - 在initialize()中写死 */
    private static final String EUA_DATA_FILE = "I:\\EdgeCloudSim-master\\EdgeCloudSim-master\\scripts\\sample_app6\\config\\users-melbcbd-generated.csv";
    
    private List<double[]> euaStartPositions;
    private List<TreeMap<Double, Location>> treeMapArray;
    
    private UniformRealDistribution angleRng;
    private UniformRealDistribution speedRng;
    private UniformRealDistribution pauseRng;
    private UniformRealDistribution directionChangeRng;
    private UniformRealDistribution pauseDurationRng;
    
    // ==================== Constructors ====================
    
    public EUARandomWalkMobility(int numberOfMobileDevices, double simulationTime) {
        super(numberOfMobileDevices, simulationTime);
        euaStartPositions = new ArrayList<>();
        treeMapArray = new ArrayList<>();
    }
    
    // ==================== EUA Data Loading ====================
    
    /**
     * 加载EUA数据集（文件路径在方法中写死）
     */
    private void loadEUAData() {
        euaStartPositions.clear();
        
        String filePath = "I:/EdgeCloudSim-master/EdgeCloudSim-master/scripts/sample_app6/config/users-melbcbd-generated.csv";
        
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;
            int loadedCount = 0;
            
            while ((line = br.readLine()) != null) {
                lineNumber++;
                
                // 跳过空行
                if (line.trim().isEmpty()) continue;
                
                // ★ 去除BOM
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                
                // ★ 判断是否是表头行（包含 "Latitude" 或 "Longitude" 或 "lat"）
                if (line.toLowerCase().contains("latitude") || line.toLowerCase().contains("longitude") || line.toLowerCase().contains("lat")) {
                    SimLogger.printLine("EUA: Skipping header: " + line);
                    continue;
                }
                
                // ★ 先尝试用逗号分割，如果分割后只有一个字段，尝试用制表符
                String[] parts = line.split(",");
                if (parts.length < 2) {
                    // 用制表符分割
                    parts = line.split("\t");
                }
                
                if (parts.length < 2) {
                    SimLogger.printLine("EUA: Line " + lineNumber + " has only " + parts.length + " parts, skipping");
                    continue;
                }
                
                try {
                    // 数据格式: Latitude, Longitude
                    double lat = Double.parseDouble(parts[0].trim());
                    double lon = Double.parseDouble(parts[1].trim());
                    
                    double[] xy = toMercator(lon, lat);
                    euaStartPositions.add(xy);
                    loadedCount++;
                    
                } catch (NumberFormatException e) {
                    SimLogger.printLine("EUA: Line " + lineNumber + " parse error: " + e.getMessage());
                    continue;
                }
            }
            
            SimLogger.printLine("EUA: Loaded " + loadedCount + " user positions");
            
        } catch (IOException e) {
            SimLogger.printLine("EUA: Failed to load file: " + e.getMessage());
            generateFallbackPositions();
        }
        
        if (euaStartPositions.isEmpty()) {
            SimLogger.printLine("EUA: No data loaded, using random positions");
            generateFallbackPositions();
        }
    }
    
    
    private void generateFallbackPositions() {
        for (int i = 0; i < numberOfMobileDevices; i++) {
            double x = SimUtils.getRandomNumber(-5000, 5000);
            double y = SimUtils.getRandomNumber(-5000, 5000);
            euaStartPositions.add(new double[]{x, y});
        }
    }
    
    // ==================== Mercator Projection ====================
    
    private double[] toMercator(double lon, double lat) {
        double lonRad = Math.toRadians(lon);
        double latRad = Math.toRadians(lat);
        double refLonRad = Math.toRadians(REF_LON);
        
        double x = EARTH_RADIUS * (lonRad - refLonRad);
        double y = EARTH_RADIUS * Math.log(Math.tan(Math.PI / 4 + latRad / 2));
        
        return new double[]{x, y};
    }
    
    // ==================== Mobility Model Implementation ====================
     
    @Override
    public void initialize() {
        treeMapArray.clear();
        
        // 加载EUA数据
        loadEUAData();
        
        angleRng = new UniformRealDistribution(0, 2 * Math.PI);
        speedRng = new UniformRealDistribution(minSpeedMps, maxSpeedMps);
        pauseRng = new UniformRealDistribution(0, 1);
        directionChangeRng = new UniformRealDistribution(0, 1);
        pauseDurationRng = new UniformRealDistribution(minPauseDuration, maxPauseDuration);
        
        // 确保有足够的起始位置
        while (euaStartPositions.size() < numberOfMobileDevices) {
            double[] lastPos = euaStartPositions.get(euaStartPositions.size() - 1);
            double offset = 50.0;
            euaStartPositions.add(new double[]{
                lastPos[0] + (Math.random() * 2 - 1) * offset,
                lastPos[1] + (Math.random() * 2 - 1) * offset
            });
        }
        
        // ★ 获取边缘服务器数量
        int numOfEdgeDatacenters = SimSettings.getInstance().getNumOfEdgeDatacenters();
        
        // 初始化每个设备
        for (int deviceId = 0; deviceId < numberOfMobileDevices; deviceId++) {
            treeMapArray.add(new TreeMap<>());
            TreeMap<Double, Location> treeMap = treeMapArray.get(deviceId);
            
            double[] startPos = euaStartPositions.get(deviceId % euaStartPositions.size());
            double startX = startPos[0];
            double startY = startPos[1];
            
            if (deviceId >= euaStartPositions.size()) {
                startX += (Math.random() * 2 - 1) * 100;
                startY += (Math.random() * 2 - 1) * 100;
            }
            
            // ★ 分配一个有效的边缘服务器ID（0 到 numOfEdgeDatacenters-1）
            int wlanId = SimUtils.getRandomNumber(0, numOfEdgeDatacenters - 1);
            int placeTypeIndex = 0;
            
            treeMap.put(
                SimSettings.CLIENT_ACTIVITY_START_TIME,
                new Location(placeTypeIndex, wlanId, (int)startX, (int)startY)
            );
            
            double currentAngle = angleRng.sample();
            double currentSpeed = useFixedSpeed ? speedMps : speedRng.sample();
            boolean isPaused = false;
            double pauseEndTime = 0;
            
            double currentTime = SimSettings.CLIENT_ACTIVITY_START_TIME;
            double currentX = startX;
            double currentY = startY;
            
            while (currentTime < simulationTime) {
                if (isPaused) {
                    if (currentTime >= pauseEndTime) {
                        isPaused = false;
                        currentAngle = angleRng.sample();
                        currentSpeed = useFixedSpeed ? speedMps : speedRng.sample();
                    } else {
                        currentTime += timeStep;
                        continue;
                    }
                }
                
                if (!isPaused && pauseRng.sample() < pauseProb) {
                    isPaused = true;
                    pauseEndTime = currentTime + pauseDurationRng.sample();
                    treeMap.put(currentTime, new Location(
                        placeTypeIndex, wlanId, (int)currentX, (int)currentY
                    ));
                    currentTime += timeStep;
                    continue;
                }
                
                if (directionChangeRng.sample() < directionChangeProb) {
                    double turnAngle = (Math.random() * 2 - 1) * maxTurnAngle;
                    currentAngle += turnAngle;
                }
                
                double stepDistance = currentSpeed * timeStep;
                double deltaX = stepDistance * Math.cos(currentAngle);
                double deltaY = stepDistance * Math.sin(currentAngle);
                double newX = currentX + deltaX;
                double newY = currentY + deltaY;
                
                if (enforceBoundary) {
                    double distanceFromCenter = Math.sqrt(newX * newX + newY * newY);
                    if (distanceFromCenter > boundaryRadius) {
                        currentAngle += Math.PI;
                        deltaX = stepDistance * Math.cos(currentAngle);
                        deltaY = stepDistance * Math.sin(currentAngle);
                        newX = currentX + deltaX;
                        newY = currentY + deltaY;
                    }
                }
                
                currentX = newX;
                currentY = newY;
                
                treeMap.put(currentTime, new Location(
                    placeTypeIndex, wlanId, (int)currentX, (int)currentY
                ));
                
                currentTime += timeStep;
            }
        }
        
        int loadedCount = euaStartPositions.size();
        SimLogger.printLine("EUA Random Walk Mobility initialized for " + 
                           numberOfMobileDevices + " devices");
        SimLogger.printLine("  EUA positions loaded: " + loadedCount);
        SimLogger.printLine("  Speed: " + (useFixedSpeed ? 
                           String.format("%.1f", speedMps) : 
                           String.format("%.1f-%.1f", minSpeedMps, maxSpeedMps)) + " m/s");
        SimLogger.printLine("  Direction change probability: " + directionChangeProb);
        SimLogger.printLine("  Max turn angle: " + Math.toDegrees(maxTurnAngle) + "°");
    }
    
    @Override
    public Location getLocation(int deviceId, double time) {
        TreeMap<Double, Location> treeMap = treeMapArray.get(deviceId);
        
        if (treeMap == null) {
            return new Location(0, -1, 0, 0);
        }
        
        Entry<Double, Location> e = treeMap.floorEntry(time);
        
        if (e == null) {
            e = treeMap.firstEntry();
            if (e == null) {
                return new Location(0, -1, 0, 0);
            }
        }
        
        return e.getValue();
    }
    
    // ==================== Getter and Setter Methods ====================
    
    public void setSpeed(double speedMps) {
        this.speedMps = speedMps;
        this.useFixedSpeed = true;
    }
    
    public void setSpeedRange(double minSpeedMps, double maxSpeedMps) {
        this.minSpeedMps = minSpeedMps;
        this.maxSpeedMps = maxSpeedMps;
        this.useFixedSpeed = false;
    }
    
    public void setDirectionChangeProbability(double prob) {
        this.directionChangeProb = Math.min(1.0, Math.max(0.0, prob));
    }
    
    public void setMaxTurnAngle(double angleDegrees) {
        this.maxTurnAngle = Math.toRadians(angleDegrees);
    }
    
    public void setPauseProbability(double prob) {
        this.pauseProb = Math.min(1.0, Math.max(0.0, prob));
    }
    
    public void setPauseDurationRange(double minSeconds, double maxSeconds) {
        this.minPauseDuration = minSeconds;
        this.maxPauseDuration = maxSeconds;
    }
    
    public void setBoundaryRadius(double radiusMeters) {
        this.boundaryRadius = radiusMeters;
    }
    
    public void setEnforceBoundary(boolean enforce) {
        this.enforceBoundary = enforce;
    }
    
    public void setTimeStep(double stepSeconds) {
        this.timeStep = stepSeconds;
    }
    
    public int getNumberOfLoadedUsers() {
        return euaStartPositions.size();
    }
}