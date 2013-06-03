package com.discreteit.hybridaugmentation;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


/**
 * Created by erikb on 6/2/13.
 */

public class AddPointsActivity extends Activity {

    GoogleMap map;
    ProgressBar progressBar;
    private String latCoords;
    private String lonCoords;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_points);
        map = ((MapFragment)getFragmentManager().findFragmentById(R.id.addmap)).getMap();
        map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        map.setMyLocationEnabled(true);
        map.animateCamera(CameraUpdateFactory.zoomTo(20));
        map.setInfoWindowAdapter(new AddInfoWindow());
        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {

            @Override
            public void onMapClick(LatLng latLng) {
                MarkerOptions marker = new MarkerOptions();
                marker.position(latLng);
                latCoords = Double.toString(latLng.latitude);
                lonCoords = Double.toString(latLng.longitude);
                map.clear();
                map.addMarker(marker);
            }
        });
    }

    private class AddPoint {
        public String name;
        public String description;
        public String lat;
        public String lon;


        public AddPoint(String pName, String pDescription, String[] latlon) {
            name = pName;
            description = pDescription;
            lat = latlon[0];
            lon = latlon[1];
        }
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

    private class AddInfoWindow implements GoogleMap.InfoWindowAdapter {

        @Override
        public View getInfoContents(Marker arg0) {

            return null;
        }


        @Override
        public View getInfoWindow(Marker marker) {
            LayoutInflater inflater = (LayoutInflater)getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
            View v = inflater.inflate(R.layout.add_callout, null);
            Button addButton = (Button)v.findViewById(R.id.addbutton);
            addButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    View parent = (View)view.getParent();
                    EditText name = (EditText)parent.findViewById(R.id.pointname);
                    EditText description = (EditText)parent.findViewById(R.id.pointdescr);
                    String lattext = latCoords;
                    String lontext = lonCoords;
                    String nameText = name.getText().toString();
                    String descrText = description.getText().toString();

                    if (nameText.equals("") || descrText.equals("")) {
                        Toast.makeText(getApplicationContext(), "Enter some info for the name", Toast.LENGTH_LONG);
                        return;
                    }
                    AddPoint ap = new AddPoint(nameText, descrText, new String[] {lattext, lontext});
                    new AddPointTask().execute(ap);
                }
            });
            return v;
        }
    }

    private class AddPointTask extends AsyncTask<AddPoint, Boolean, String> {

        @Override
        protected String doInBackground(AddPoint... points) {

            publishProgress(true);
            String urlString = "http://test.discreteit.com:6555/place";
            String response = "";
            for (AddPoint p : points) {
                String params = String.format("lat=%s&lon=%s&name=%s&descr=%s", p.lat, p.lon, p.name, p.description);
                String full = urlString;
                HttpURLConnection conn;
                try {
                    URL url = new URL(full);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Length", "" + Integer.toString(params.getBytes().length));
                    DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                    dos.writeBytes(params);
                    dos.flush();
                    dos.close();
                    BufferedReader is = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String streamline;
                    StringBuilder fullStream = new StringBuilder();
                    while ((streamline = is.readLine()) != null) {
                        fullStream.append(streamline);
                    }
                    is.close();
                    response = fullStream.toString();
                    conn.disconnect();
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    return null;
                }
            }
            publishProgress(false);
            return response;
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
        protected void onPostExecute(String response) {
            if (response == null) {
                Toast.makeText(getApplicationContext(), "Sorry, Couldn't add the awesome point you added", Toast.LENGTH_LONG);
            }
            else {
                Toast.makeText(getApplicationContext(), "Added your point, keep addin' them!", Toast.LENGTH_LONG);
            }

        }
    }

}