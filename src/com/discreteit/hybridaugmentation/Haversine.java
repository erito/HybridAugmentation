package com.discreteit.hybridaugmentation;

public class Haversine {
	private static int r = 6371000;
	
	//assumes lat lon order returns distance in meters.
	public static double computeDistance(double[] xy1, double[] xy2) {
		double dlat = Math.toRadians(xy2[0]-xy1[0]);
		double dlon = Math.toRadians(xy2[1]-xy1[1]);
		double a = Math.pow(Math.sin(dlat/2), 2) + Math.pow(Math.sin(dlon/2), 2) * Math.cos(xy1[0])*Math.cos(xy2[0]); 
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
		double distance = c*r;
		return distance;
	}
	//distance traveled in meters, bearing in degrees.
	public static double[] getPoint(double[] xy, double bearing, double distance) {
		double radbear = Math.toRadians(bearing);
		double lat2 = Math.asin(Math.sin(xy[0])*Math.cos(distance/r) + Math.cos(xy[0])*Math.sin(distance/r)*Math.cos(radbear));
		double lon2 = xy[1] + Math.atan2(Math.sin(radbear)*Math.sin(distance/r)*Math.cos(xy[0]), Math.cos(distance/r)-Math.sin(xy[0])*Math.sin(lat2));
		return new double[] {lat2,lon2};
	}
	
	
}
