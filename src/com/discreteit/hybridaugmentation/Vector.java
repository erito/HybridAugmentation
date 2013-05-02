package com.discreteit.hybridaugmentation;

public class Vector {
	public double x; //scalar proj. to X axis
	public double y; //scalar proj. to Y axis
	public double z; //scalar proj. to Z axis
	
	public double getMagnitude() {
		double returnPoint = Math.sqrt(Math.pow(this.x, 2) + Math.pow(this.y, 2) + Math.pow(this.z, 2));
		return returnPoint;
	}
	
	//convenience functions to compute functions with respect to current vector object.
	public Vector computeCrossProduct(Vector a) {
		return Vector.computeCrossProduct(this, a);
	}
	
	public double computeDotProduct(Vector a) {
		return Vector.computeDotProduct(this, a);
	}
	
	public static double computeDotProduct(Vector a, Vector b) {
		return a.x*b.x + a.y*b.y + a.z*b.z; //in our case we really don't care about Z
	}
	
	public static Vector computeCrossProduct(Vector a, Vector b) {
		Vector returnVector = new Vector();
		returnVector.x = a.y*b.z-a.z*b.y;
		returnVector.y = a.x*b.z-a.z*b.x;
		returnVector.z = a.x*b.y-a.y*b.x;
		return returnVector;
	}
	
	//Surprise!  Computes the theta between the two vectors.
	public static double computeTheta(Vector a, Vector b) {
		double absa = a.getMagnitude();
		double absb = b.getMagnitude();
		double dp = Vector.computeDotProduct(a, b);
		double quot = dp/(absa*absb);
		return Math.acos(quot);
	}
	
	public Vector() {
		
	}
	
}
