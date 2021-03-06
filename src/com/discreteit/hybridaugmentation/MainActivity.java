package com.discreteit.hybridaugmentation;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
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
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.LatLng;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/*TODO:  
 * Create list fragment that contains the list of closes points by distance.
 * Contain list item click that will generate blue, green marker and popup.
 * create tablet friendly layout.
 * create web portal for awesomeness.
 */

//Where all the magic happens.
public class MainActivity extends Activity implements SensorEventListener {
	
	private GoogleMap map;
	private ProximityList currentList;
	private SensorManager sensorManager;
	private Sensor accelerometer;
	private Sensor allAboutTheTeslas;
	private ProgressBar progressBar;
	private ListView listView;
    private LinearLayout listLayout;
    private WindowManager windowManager;
    private EntityAdapter entityAdapter;
	private double currentYHeading;
	private double lastUsedHeading;
	private double[] lastUsedLocation;
	
	private float[] currentGravity;
	private float[] teslaReadings;
	private double[] currentLocation;
	private float[] orientation;
	private int LOCATION_TOLERANCE = 5;//specified to ensure we don't make too many useless queries, the lower the tolerance the more often we query
	//the server.
	
	
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
		
		currentList = new ProximityList();
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
		sensorManager.registerListener(this, allAboutTheTeslas, 300);
		sensorManager.registerListener(this, accelerometer, 300);
        windowManager = (WindowManager)this.getSystemService(WINDOW_SERVICE);
        map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

        //Listview setup stuff
        listView = (ListView)findViewById(R.id.list);
        listLayout = (LinearLayout)findViewById(R.id.listLayout);
        if (listView != null) {
            entityAdapter = new EntityAdapter(MainActivity.this);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> adapter, View v, int position, long something) {
                    MarkerOptions options = new MarkerOptions();
                    TextView intPos = (TextView)v.findViewById(R.id.posIndex);
                    int posIndex = Integer.parseInt(intPos.getText().toString());
                    AdjacentPoint p = currentList.ByLineOfSight.get(posIndex);
                    String colorText = (String)((TextView)v.findViewById(R.id.color)).getText();
                    if (colorText == "Yellow") {
                        options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
                    }
                    else if (colorText == "Green") {
                        options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                    }
                    String snippet = String.format("%s \n You are ~ %s meters from this place", p.description, Double.toString(Math.round(p.distanceToOrigin)));
                    options.position(p.googleCoords);
                    options.snippet(snippet);
                    options.title(p.name);
                    Marker theMark = map.addMarker(options);
                    theMark.showInfoWindow();

                    CameraPosition cp = new CameraPosition.Builder().
                            target(new LatLng(p.lat, p.lon))
                            .zoom(20)
                            .bearing((float)currentYHeading)
                            .tilt(60)
                            .build();
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(cp));
                }
            });
            listView.setAdapter(entityAdapter);
        }

		map.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener () {

			@Override
			public void onMyLocationChange(Location location) {
				double lat = location.getLatitude();
				double lon = location.getLongitude();
                if (teslaReadings != null && currentGravity != null) {
                    calculateOrientation();
                }
                CameraPosition cp = new CameraPosition.Builder().
                        target(new LatLng(lat, lon))
                        .zoom(20)
                        .bearing((float)currentYHeading)
                        .tilt(60)
                        .build();
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cp));
				if (currentLocation == null || (Math.abs(currentYHeading) + 15 < Math.abs(lastUsedHeading) ||
						Math.abs(currentYHeading) - 15 > Math.abs(lastUsedHeading))) {
					currentLocation = new double[] {lat, lon};

					if (lastUsedLocation == null || Math.abs(Haversine.computeDistance(lastUsedLocation, new double[] {lat, lon})) > LOCATION_TOLERANCE) {
						PointStore pointStore = new PointStore();
						pointStore.execute(currentLocation);
					}
					else {
                        map.clear();
						currentList = buildAdjacentList(null);
                        populateList(MainActivity.this);
                        if (currentList.ByLineOfSight.size() > 0) {
						    placeMarker(currentList.ByLineOfSight.get(0));
                        }
					}
				}
				
			}
			
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		progressBar = (ProgressBar)menu.findItem(R.id.progress).getActionView();
		progressBar.setIndeterminate(true);
		progressBar.setVisibility(View.INVISIBLE);
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	protected void onStop() {
		super.onStop();

	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

		
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
        int offset = 0;
        Log.d("com.discreteit.HybridAugmentation", String.format("orientation %s", Double.toString(Math.toDegrees(orientation[0]))));
        if (windowManager.getDefaultDisplay().getRotation() == 1) {
            offset += 90;
        }
		currentYHeading = Math.toDegrees(orientation[0]) + offset;
        Log.d("com.discreteit.HybridAugmentation", String.format("orientation %s", Double.toString(currentYHeading)));
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
	
	private void placeMarker(AdjacentPoint point) {
		//first we'll check the list, nothing fancy for now this is a prototype.
		MarkerOptions marker = new MarkerOptions();
		String snippet = String.format("%s \n You are ~ %s meters from this place", point.description, Double.toString(Math.round(point.distanceToOrigin)));
		marker.position(point.googleCoords);
		marker.snippet(snippet);
		marker.title(point.name);
		map.clear();
		map.addMarker(marker);
	}

    //Method populates list depending on what is present.
    private synchronized void populateList(Context context) {
        if (listView != null) {
            if (!entityAdapter.isEmpty()) {
                entityAdapter.clear();
            }
            entityAdapter.addAll(currentList.ByLineOfSight);
            entityAdapter.notify();
        }
        else {
            if (listLayout.getChildCount() > 0) {
                listLayout.removeAllViews();
            }
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            //keep track of array position.
            int i = 0;
            for (AdjacentPoint p : currentList.ByLineOfSight) {
                View view = inflater.inflate(R.layout.list_layout, null);
                TextView labelView = (TextView)view.findViewById(R.id.placeText);
                TextView distText = (TextView)view.findViewById(R.id.distanceText);
                TextView posIndex = (TextView)view.findViewById(R.id.posIndex);
                TextView colorText = (TextView)view.findViewById(R.id.color);
                posIndex.setText(Integer.toString(i));
                i++;
                labelView.setText(p.name);
                ImageView imageView = (ImageView)view.findViewById(R.id.iconView);
                if (currentList.ByLineOfSight.get(0) == p) {
                    imageView.setImageResource(R.drawable.red);
                    colorText.setText("Red");
                }
                else if (p.angleFromLOS < 90 && p.angleFromLOS > -90) {
                    imageView.setImageResource(R.drawable.yellow);
                    colorText.setText("Yellow");
                }
                else {
                    imageView.setImageResource(R.drawable.green);
                    colorText.setText("Green");
                }
                distText.setText(Long.toString(Math.round(p.distanceToOrigin)) + " Meters");
                view.setOnClickListener(new View.OnClickListener() {

                    public void onClick(final View v) {
                        Drawable original = v.getBackground();
                        v.setBackgroundColor(0xff33b5e5);
                        MarkerOptions options = new MarkerOptions();
                        TextView intPos = (TextView)v.findViewById(R.id.posIndex);
                        int posIndex = Integer.parseInt(intPos.getText().toString());
                        AdjacentPoint p = currentList.ByLineOfSight.get(posIndex);
                        String colorText = (String)((TextView)v.findViewById(R.id.color)).getText();
                        if (colorText == "Yellow") {
                            options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
                        }
                        else if (colorText == "Green") {
                            options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                        }
                        String snippet = String.format("%s \n You are ~ %s meters from this place", p.description, Double.toString(Math.round(p.distanceToOrigin)));
                        options.position(p.googleCoords);
                        options.snippet(snippet);
                        options.title(p.name);
                        Marker theMark = map.addMarker(options);
                        theMark.showInfoWindow();
                        v.setBackground(original);
                        CameraPosition cp = new CameraPosition.Builder().
                                target(new LatLng(p.lat, p.lon))
                                .zoom(20)
                                .bearing((float)currentYHeading)
                                .tilt(60)
                                .build();
                        map.animateCamera(CameraUpdateFactory.newCameraPosition(cp));
                    }

                });
                listLayout.addView(view);
            }
        }
    }

	private ProximityList buildAdjacentList(ArrayList<Point> points) {
		double[] losVertice = Haversine.getPoint(currentLocation, currentYHeading, 60);
		NVector losNVector = new NVector(losVertice);
		NVector centralNVector = new NVector(currentLocation);
		//now we compute the cross product to acquire geometry.
		Vector los = Vector.computeCrossProduct(centralNVector, losNVector);
		//Now we can compare the list.
		ProximityList returnList = new ProximityList();
		if (points == null) {
			points = currentList.byDistanceToPoints();
		}
		for (Point p : points) {
			double dr = Haversine.computeDistance(currentLocation, new double[] {p.lat, p.lon});
			if (dr > 60) { continue; }
			//next lets see if its within the bounds of our semicircle
			//treating them like vectors
			NVector pointNVector = new NVector(new double[] {p.lat, p.lon });
			Vector pointVector = Vector.computeCrossProduct(centralNVector, pointNVector);
			double theta = Math.toDegrees(Vector.computeTheta(los, pointVector));
			//first compute the distance from the LOS Vector by taking the magnitude
			//of the pointVector, then we'll compute the distance from origin.
			Vector losPoint = losNVector.computeCrossProduct(pointNVector);
			AdjacentPoint close = new AdjacentPoint(losPoint.getMagnitude(), dr, theta, p);
			close.lat = p.lat;
			close.lon = p.lon;
			close.googleCoords = p.googleCoords;
			close.name = p.name;
			close.description = p.description;
			//From here we do an insertion sort on the proximity list
			int k, j;
			k = j = returnList.size();
			if (k ==0) {
				returnList.ByDistance.add(close);
				returnList.ByLineOfSight.add(close);
				continue;
			}
			while (k > 0 && returnList.ByDistance.get(k-1).distanceToOrigin > close.distanceToOrigin) {
				if (k == returnList.size()) {
					returnList.ByDistance.add(returnList.ByDistance.get(k-1));
				}
				else {
					returnList.ByDistance.set(k, returnList.ByDistance.get(k-1));
				}
				k--;
			}
			if (k == returnList.size()) {
				returnList.ByDistance.add(close);	
			}
			else {
				returnList.ByDistance.set(k, close);
			}
			while (j > 0 && returnList.ByLineOfSight.get(j-1).angleFromLOS > close.angleFromLOS) {
				if (j == returnList.ByLineOfSight.size()) {
					returnList.ByLineOfSight.add(returnList.ByLineOfSight.get(j-1));
				}
				else {
					returnList.ByLineOfSight.set(j, returnList.ByLineOfSight.get(j-1));
				}
				j--;
			}
			if (j == returnList.ByLineOfSight.size()) {
				returnList.ByLineOfSight.add(close);
			}
			else {
				returnList.ByLineOfSight.set(j, close);
			}
		}
		return returnList; 
	}
	
	private class CustomInfoWindow implements InfoWindowAdapter {

		@Override
		public View getInfoContents(Marker arg0) {
			
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
	
	private class Point {
		public String description;
		public LatLng googleCoords;
		public double lat;
		public double lon;
		public String name;
		
		public Point() {
			
		}
		
		public Point(String desc, LatLng gcoords, double lati, double loni, String nam) {
			description = desc;
			googleCoords = gcoords;
			lat = lati;
			lon = loni;
			name = nam;

		}
		
	}

	private class AdjacentPoint extends Point {
		public double distanceToLOS;
		public double distanceToOrigin;
		public double angleFromLOS;

		public AdjacentPoint(double losdistance, double origindistance, double theta, Point p) {
			googleCoords = p.googleCoords;
			distanceToLOS = losdistance;
			distanceToOrigin = origindistance;
			angleFromLOS = theta;
			name = p.name;
			description = p.description;
		}
	}
	
	private class ProximityList {
		public ArrayList<AdjacentPoint> ByDistance;
		public ArrayList<AdjacentPoint> ByLineOfSight;
		
		public int size() {
			return ByDistance.size();
		}
		
		public ArrayList<Point> byDistanceToPoints() {
			ArrayList<Point> returnPoints = new ArrayList<Point>();
			for (AdjacentPoint ap : this.ByDistance){
				Point p = new Point(ap.description, ap.googleCoords, ap.lat, ap.lon, ap.name);
				returnPoints.add(p);
			}
			return returnPoints;
		}
		
		public ProximityList() {
			ByDistance = new ArrayList<AdjacentPoint>();
			ByLineOfSight = new ArrayList<AdjacentPoint>();
		}
	}

	
	private class PointStore extends AsyncTask<double[], Boolean, JSONObject> {
		
		@Override
		protected JSONObject doInBackground(double[]... latlons) {
			lastUsedHeading = currentYHeading;
			publishProgress(true);
			String urlString = "http://test.discreteit.com:6555/place";
			JSONObject jobj = null;
			for (double[] latlon : latlons) {
				lastUsedLocation = latlon;
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
			if (progress[0]) {
				progressBar.setVisibility(View.VISIBLE);
			}
			else {
				progressBar.setVisibility(View.INVISIBLE);
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
				if (features.length() == 0) {
					map.clear();
					Toast.makeText(MainActivity.this, "Nothing around here", Toast.LENGTH_LONG).show();
					return;
				}
				ProximityList adjacentPoints = buildAdjacentList(newlist);
				currentList = adjacentPoints;
                populateList(MainActivity.this);
				AdjacentPoint closest = adjacentPoints.ByLineOfSight.get(0); //by los
				placeMarker(closest);
			}
			catch (Exception ex){
				ex.printStackTrace();
			}
			
		}
	}

    //Used for the listview of images for the expandable grid view.
    private class EntityAdapter extends ArrayAdapter<AdjacentPoint> {

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater)this.getContext().getSystemService(this.getContext().LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.list_layout, null);
            AdjacentPoint p = this.getItem(position);
            TextView labelView = (TextView)view.findViewById(R.id.placeText);
            TextView distText = (TextView)view.findViewById(R.id.distanceText);
            TextView colorText = (TextView)view.findViewById(R.id.color);
            labelView.setText(p.name);
            TextView posIndex = (TextView)view.findViewById(R.id.posIndex);
            posIndex.setText(Integer.toString(position));
            ImageView imageView = (ImageView)view.findViewById(R.id.iconView);
            if (currentList.ByLineOfSight.get(0) == p) {
                imageView.setImageResource(R.drawable.red);
                colorText.setText("Red");
            }
            else if (p.angleFromLOS < 90 && p.angleFromLOS > -90) {
                imageView.setImageResource(R.drawable.yellow);
                colorText.setText("Yellow");
            }
            else {
                imageView.setImageResource(R.drawable.green);
                colorText.setText("Green");
            }
            distText.setText(Long.toString(Math.round(p.distanceToOrigin)) + " Meters");
            return view;
        }


        public EntityAdapter(Context context) {
            super(context, R.layout.list_layout);
        }

    }
}
