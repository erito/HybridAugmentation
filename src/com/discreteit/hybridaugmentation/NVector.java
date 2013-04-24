package com.discreteit.hybridaugmentation;

public class NVector extends Vector {
	
	public NVector(double[] latlon) {
		x = Math.cos(latlon[0])*Math.cos(latlon[1]); //scalar proj. of X
		y = Math.cos(latlon[0])*Math.sin(latlon[1]); // ditto Y
		z = Math.sin(latlon[0]); //ditto Z
	}
}
