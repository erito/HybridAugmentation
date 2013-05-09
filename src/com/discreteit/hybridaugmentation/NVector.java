package com.discreteit.hybridaugmentation;

public class NVector extends Vector {
	
	public NVector(double[] latlon) {
		double lambda = Math.atan(Math.tan(latlon[0])); //geocentric latitude at sea level.
		double r = Math.sqrt(Math.pow(Haversine.r, 2)/(1+(Math.pow(Math.sin(lambda), 2)))); //
		x = r*Math.cos(lambda)*Math.cos(latlon[1]); //scalar proj. of X  Note we're assuming altitude = 0 for all calculations
		y = r*Math.cos(lambda)*Math.sin(latlon[1]); // ditto Y
		z = r*Math.sin(lambda); //ditto Z
	}
}
