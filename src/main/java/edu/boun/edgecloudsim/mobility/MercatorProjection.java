package edu.boun.edgecloudsim.mobility;

public class MercatorProjection {
	private static final double R = 6378137.0; // 地球半径（米）
    private double refLon; // 参考经度（墨尔本：144.9631°）
    private double refLat; // 参考纬度（墨尔本：-37.8136°）

    public MercatorProjection(double refLon, double refLat) {
        this.refLon = Math.toRadians(refLon);
        this.refLat = Math.toRadians(refLat);        
    }

    // 经纬度 → (x, y) 平面坐标（米）
    public double[] toMeters(double lon, double lat) {
        double lonRad = Math.toRadians(lon);
        double latRad = Math.toRadians(lat);
        
        double x = R * (lonRad - refLon);
        double y = R * Math.log(Math.tan(Math.PI/4 + latRad/2));
        // 注意：南半球 y 为负值
        
        return new double[]{x, y};
    }

    // (x, y) 平面坐标 → 经纬度（反向转换）
    public double[] toLatLon(double x, double y) {
        double lon = refLon + x / R;
        double lat = 2 * Math.atan(Math.exp(y / R)) - Math.PI/2;
        return new double[]{Math.toDegrees(lon), Math.toDegrees(lat)};
    }
}


