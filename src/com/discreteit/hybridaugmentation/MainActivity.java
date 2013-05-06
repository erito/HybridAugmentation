package com.discreteit.hybridaugmentation;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.view.View;
import android.view.LayoutInflater;
import android.os.AsyncTask;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import org.json.*;
import com.discreteit.hybridaugmentation.Haversine;
import com.discreteit.hybridaugmentation.Vector;
import com.discreteit.hybridaugmentation.NVector;

import android.location.Location;

import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.GoogleMap;
import android.content.Context;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.LatLng;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

//Where all the magic happens.
public class MainActivity extends Activity implements SensorEventListener {
	
	private GoogleMap map;
	private ArrayList<AdjacentPoint> adjacentPoints;
	private ArrayList<AdjacentPoint> currentList;
	private SensorManager sensorManager;
	private Sensor accelerometer;
	private Sensor allAboutTheTeslas; 
	private double currentYHeading;
	private double lastUsedHeading;
	private Boolean throttleOn;
	private float[] currentGravity;
	private float[] teslaReadings;
	private double[] currentLocation;
	private float[] orientation;
	
	@Override
	protected void onStart() {
		super.onStart();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		orientation = new float[] {
			0,
			0,
			0
		};
		throttleOn = false;
		currentList = new ArrayList<AdjacentPoint>();
		currentGravity = new float[3];
		teslaReadings = new float[3];
		sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		map = ((MapFragment)getFragmentManager().findFragmentById(R.id.map)).getMap();
		map.setMyLocationEnabled(true);
		map.animateCamera(CameraUpdateFactory.zoomTo(20));
		map.setInfoWindowAdapter(new CustomInfoWindow());
		allAboutTheTeslas = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(this, allAboutTheTeslas, 200);
		sensorManager.registerListener(this, accelerometer, 200);

		map.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener () {

			@Override
			public void onMyLocationChange(Location location) {
				double lat = location.getLatitude();
				double lon = location.getLongitude();
				if (currentGravity != null && teslaReadings != null) {
					calculateOrientation();
				}
				CameraPosition cp = new CameraPosition.Builder().
						target(new LatLng(lat, lon))
						.zoom(20)
						.bearing((float)currentYHeading)
						.tilt(60)
						.build();
				map.animateCamera(CameraUpdateFactory.newCameraPosition(cp));
				if (currentLocation == null || (Math.abs(currentYHeading) + 30 < Math.abs(lastUsedHeading) || 
						Math.abs(currentYHeading) - 30 > Math.abs(lastUsedHeading))) {
					//This probably needs to be updated to reflect a bearing as well
					//but without documentation I'm pretty lost right now.
					currentLocation = new double[] {lat, lon};
					PointStore pointStore = new PointStore();
					pointStore.execute(currentLocation);		
				}
				
			}
			
		});
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

	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			System.arraycopy(event.values, 0, currentGravity, 0, event.values.length);
		}
		else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
			System.arraycopy(event.values, 0, teslaReadings, 0, event.values.length);
		}
	}
	
	private void calculateOrientation() {
		float[] R = new float[9];//rotation matrix
		float[] I = new float[9];
		SensorManager.getRotationMatrix(R, I, currentGravity, teslaReadings);
		SensorManager.getOrientation(R, orientation);
		currentYHeading = Math.toDegrees(orientation[0]);
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
			JSONObject properties = obj.getJSONObject("properties");
			rp.name = properties.getString("place"); 
			rp.description = properties.getString("description");
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
		double[] losVertice = Haversine.getPoint(currentLocation, currentYHeading, 60);
		NVector losNVector = new NVector(losVertice);
		NVector centralNVector = new NVector(currentLocation);
		//now we compute the cross product to acquire geometry.
		Vector los = Vector.computeCrossProduct(centralNVector, losNVector);
		//Now we can compare the list.
		ArrayList<AdjacentPoint> returnList = new ArrayList<AdjacentPoint>();
		for (Point p : points) {
			double dr = Haversine.computeDistance(currentLocation, new double[] {p.lat, p.lon});
			if (dr > 60) { continue; }
			//next lets see if its within the bounds of our semicircle
			//treating them like vectors
			NVector pointNVector = new NVector(new double[] {p.lat, p.lon });
			Vector pointVector = Vector.computeCrossProduct(centralNVector, pointNVector);
			double theta = Math.toDegrees(Vector.computeTheta(los, pointVector));
			if (theta < 90 && theta > -90) {
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
	
	private class CustomInfoWindow implements InfoWindowAdapter {

		@Override
		public View getInfoContents(Marker arg0) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public View getInfoWindow(Marker marker) {
			LayoutInflater inflater = (LayoutInflater)getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
			View v = inflater.inflate(R.layout.callout_view, null);
			TextView title = (TextView)v.findViewById(R.id.titleText);
			TextView desc = (TextView)v.findViewById(R.id.description);
			title.setText(marker.getTitle());
			desc.setText(marker.getSnippet());
			return v;
		}
		
	}
	
	private class PointStore extends AsyncTask<double[], Boolean, JSONObject> {
		@Override
		protected JSONObject doInBackground(double[]... latlons) {
			if (!throttleOn) {
				throttleOn = true;
			}
			else { return null; }
			publishProgress(true);
			String urlString = "http://192.168.1.148:6555/place";
			JSONObject jobj = null;
			for (double[] latlon : latlons) {
				String params = String.format("?lat=%s&lon=%s", Double.toString(latlon[0]), Double.toString(latlon[1]));
				String full = urlString + params;
				HttpURLConnection conn;
				try {
					URL url = new URL(full);
					conn = (HttpURLConnection) url.openConnection();
					conn.connect();
					BufferedReader is = new BufferedReader(new InputStreamReader(conn.getInputStream()));
					String streamline;
					StringBuilder fullStream = new StringBuilder();
					while ((streamline = is.readLine()) != null) {
						fullStream.append(streamline);
					}
					is.close();
					jobj = new JSONObject(fullStream.toString());
					conn.disconnect();
				}
				catch (Exception ex) {
					ex.printStackTrace();
				
				}
			}
			publishProgress(false);
			return jobj;
		}
		
		@Override
		protected void onProgressUpdate(Boolean... progress) {
			ProgressBar prog = (ProgressBar)findViewById(R.id.progress);
			if (progress[0]) {
				prog.setVisibility(View.VISIBLE);
			}
			else {
				prog.setVisibility(View.INVISIBLE);
			}
		}
		
		@Override
		protected void onPostExecute(JSONObject response) {
			//Here we will need to check if there are any objects, if there are
			//we'll probably need to order them by location and adjacency to bearing
			//we'll probably need to notify either the MainControl or do all the checks here
			if (response == null) {
				return;
			}
			try {
				JSONArray features = response.getJSONArray("FeatureCollection");
				ArrayList<Point> newlist = new ArrayList<Point>();
				for (int i = 0; i < features.length(); i++) {
					JSONObject entity = features.getJSONObject(i);
					newlist.add(fromJson(entity));
				}
				adjacentPoints = AdjacentList(newlist);
				if (adjacentPoints.size() == 0) {
					map.clear();
					Toast.makeText(getApplicationContext(), "Nothing around here", Toast.LENGTH_LONG).show();
					return;
				}
				AdjacentPoint closest = adjacentPoints.get(0);
				//first we'll check the list, nothing fancy for now this is a prototype.
				MarkerOptions marker = new MarkerOptions();
				if (currentList.size() != 0 && closest.equals(currentList.get(0))) {
					String snippet = String.format("%s \n You are ~ %s meters from this place", currentList.get(0).point.description, 
							Double.toString(currentList.get(0).distanceToOrigin));
					marker.snippet(snippet);
					return;
				}
				currentList = adjacentPoints;
				AdjacentPoint ajp = currentList.get(0);
				String snippet = String.format("%s \n You are ~ %s meters from this place", ajp.point.description, Double.toString(ajp.distanceToOrigin));
				lastUsedHeading = currentYHeading;
				marker.position(ajp.point.googleCoords);
				marker.snippet(snippet);
				marker.title(ajp.point.name);
				map.clear();
				map.addMarker(marker);
			}
			catch (Exception ex){
				ex.printStackTrace();
			}
			finally {
				throttleOn = false;
			}
		}
	}
}
