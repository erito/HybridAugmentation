package com.discreteit.hybridaugmentation;

import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;
import android.os.AsyncTask;
import org.json.*;
import com.discreteit.hybridaugmentation.Haversine;
import com.discreteit.hybridaugmentation.Vector;
import com.discreteit.hybridaugmentation.NVector;

import android.location.Location;
import android.location.LocationManager;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.GoogleMap;
import android.content.Context;
import android.location.LocationListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/*TODO:  Need to bind the RESTful services query, and as well get a map working, probably Google Maps.
 * after that we'll need to bind the call that updates the location and bearing with the call to get 
 * adjacency.  Then have some indication of adjacency in the view. 
 *
 */

public class MainActivity extends Activity {
	
	private GoogleMap map;
	private LocationManager lManager;
	private ArrayList<AdjacentPoint> adjacentPoints;
	private double currentBearing;
	private double[] currentLocation;
	
	private final LocationListener listener = new LocationListener () {

		@Override
		public void onLocationChanged(Location location) {
			double lat = location.getLatitude();
			double lon = location.getLongitude();
			currentLocation = new double[] {lat, lon};
			currentBearing = location.getBearing();
			
		}

		@Override
		public void onProviderDisabled(String provider) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onProviderEnabled(String provider) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			// TODO Auto-generated method stub
			
		}
		
	};
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		lManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
		lManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, listener);
		map = ((MapFragment)getFragmentManager().findFragmentById(R.id.map)).getMap();
		map.setMyLocationEnabled(true);
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		lManager.removeUpdates(listener);
	}
	
	private class Point {
		public String description;
		public LatLng googleCoords;
		public double lat;
		public double lon;
		public String name;
		
		public Point() {
			
		}
		
	}
	
	private Point fromJson(JSONObject obj) {
		Point rp = new Point();
		try {
			rp.name = obj.getString("place");
			rp.description = obj.getString("description");
			JSONObject geometry = obj.getJSONObject("geometry");
			rp.lat = geometry.getJSONArray("coordinates").getDouble(1);
			rp.lon = geometry.getJSONArray("coordinates").getDouble(0);
			rp.googleCoords = new LatLng(rp.lat, rp.lon);
			
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
		return rp;
	}
	
	//from current bearing.
	private double getBearing(int offset) {
		double os = 360 + offset;
		if (os > 360) {
			return os -360;
		}
		else if (os < 0) {
			return os + 360;
		}
		else {
			return os;
		}
	}
	/* So if thats the case heres what we need:  A radius (always 60 in this case)
	 * a line from bearing to line of sight.given radius 
	 * find the distance from the point, determine if its larger than the radius.
	 */
	
	private class AdjacentPoint {
		public Point point;
		public double distanceToLOS;
		public double distanceToOrigin;
		public double angleFromLOS;
		public AdjacentPoint(Point p, double losdistance, double origindistance, double theta) {
			point = p;
			distanceToLOS = losdistance;
			distanceToOrigin = origindistance;
			angleFromLOS = theta;
		}
	}
	
	private ArrayList<AdjacentPoint> AdjacentList(ArrayList<Point> points) {
		double[] losVertice = Haversine.getPoint(currentLocation, currentBearing, 60);
		NVector losNVector = new NVector(losVertice);
		NVector centralNVector = new NVector(currentLocation);
		//now we compute the cross product to acquire geometry.
		Vector los = Vector.computeCrossProduct(centralNVector, losNVector);
		//Now we can compare the list.
		ArrayList<AdjacentPoint> returnList = new ArrayList<AdjacentPoint>();
		for (Point p : points) {
			double dr = Haversine.computeDistance(currentLocation, new double[] {p.lat, p.lon});
			if (dr > 60) { continue;}
			//next lets see if its within the bounds of our semicircle
			//treating them like vectors
			NVector pointNVector = new NVector(new double[] {p.lat, p.lon });
			Vector pointVector = Vector.computeCrossProduct(centralNVector, pointNVector);
			double theta = Vector.computeTheta(los, pointVector);
			if (theta < 90 && theta > -90) {
				//how do I compute distance to LOS?
				//first compute the distance from the LOS Vector by taking the magnitude
				//of the pointVector, then we'll compute the distance from origin.
				Vector losPoint = losNVector.computeCrossProduct(pointNVector);
				AdjacentPoint close = new AdjacentPoint(p, losPoint.getMagnitude(), pointVector.getMagnitude(), theta);
				if (returnList.size() == 0) {
					returnList.add(close);
				}
				else if (returnList.get(0).angleFromLOS > close.angleFromLOS) {
					returnList.add(0, close);
				}
				else {
					returnList.add(close);
				}
			}
		}
		return returnList; 
	}
	
	//TODO:  
	private void processAdjacency() {
		if (adjacentPoints.size() > 0) {
			AdjacentPoint closest = adjacentPoints.get(0);
			
		}
		else {
			Toast.makeText(getApplicationContext(), "Nothing here", Toast.LENGTH_SHORT).show();
		}
	}
	
	private class PointStore extends AsyncTask<double[], Integer, JSONObject> {
		@Override
		protected JSONObject doInBackground(double[]... latlons) {
			publishProgress(0);
			String urlString = "http://192.168.1.148:6555/place";
			JSONObject jobj = null;
			for (double[] latlon : latlons) {
				String params = String.format("?lat={0}&lon={1}", Double.toString(latlon[0]), Double.toString(latlon[1]));
				String full = urlString + params;
				HttpURLConnection conn;
				try {
					URL url = new URL(full);
					conn = (HttpURLConnection) url.openConnection();
					InputStream stream = conn.getInputStream();
					publishProgress(50);
					jobj = new JSONObject(stream.toString());
					 
				}
				catch (Exception ex) {
					ex.printStackTrace();
				
				}
			}
			publishProgress(100);
			return jobj;
		}
		
		@Override
		protected void onProgressUpdate(Integer... progress) {
			Log.d("AGRDemo", String.format("{0}% finished", Integer.toString(progress[0])));
		}
		
		@Override
		protected void onPostExecute(JSONObject response) {
			//Here we will need to check if there are any objects, if there are
			//we'll probably need to order them by location and adjacency to bearing
			//we'll probably need to notify either the MainControl or do all the checks here and force update the main control from
			//here....that works for me, personally.
			try {
				JSONArray features = response.getJSONArray("FeatureCollection");
				ArrayList<Point> newlist = new ArrayList<Point>();
				for (int i = 0; i < features.length(); i++) {
					JSONObject entity = features.getJSONObject(i);
					newlist.add(fromJson(entity));
				}
				adjacentPoints = AdjacentList(newlist);
				processAdjacency(); //will update the UI.
			}
			catch (Exception ex){
				
			}
		}
	}

}
