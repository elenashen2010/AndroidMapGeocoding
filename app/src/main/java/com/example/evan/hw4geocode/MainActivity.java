package com.example.evan.hw4geocode;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends ActionBarActivity implements GoogleMap.OnMapLongClickListener {
    public final static String EXTRA_MESSAGE = "com.mycompany.myfirstapp.MESSAGE";

    Button mBtnFind;
    Button mBtnMyLoc;
    GoogleMap mMap;
    EditText etPlace;

    /** Called when the user clicks the Send button */
    /*public void sendMessage(View view){
        // Do something in response to button
        Intent intent =new Intent(this,DisplayMessageActivity.class);
        EditText editText =(EditText) findViewById(R.id.edit_message);
        String message = editText.getText().toString();
        intent.putExtra(EXTRA_MESSAGE, message);
        startActivity(intent);
        //MapsActivity.getLatLongFromAddress("Austin, TX");
    }*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Getting reference to the find button
        mBtnFind = (Button) findViewById(R.id.btn_show);
        mBtnMyLoc = (Button) findViewById(R.id.btn_myloc);

        // Getting reference to the SupportMapFragment
        SupportMapFragment mapFragment = (SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map);

        // Getting reference to the Google Map
        mMap = mapFragment.getMap();
        //mMap.setMyLocationEnabled(true);
        mMap.setOnMapLongClickListener(this);

        // Getting reference to EditText
        etPlace = (EditText) findViewById(R.id.et_place);

        // Setting click event listener for the find button
        mBtnFind.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // Getting the place entered
                String location = etPlace.getText().toString();

                if(location==null || location.equals("")){
                    Toast.makeText(getBaseContext(), "No Place is entered", Toast.LENGTH_SHORT).show();
                    return;
                }

                String url = "https://maps.googleapis.com/maps/api/geocode/json?";

                try {
                    // encoding special characters like space in the user input place
                    location = URLEncoder.encode(location, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                String address = "address=" + location;

                LatLngBounds viewportBias = mMap.getProjection().getVisibleRegion().latLngBounds;
                System.out.println(viewportBias);
                String bias = "bounds="+viewportBias.southwest.latitude+","+viewportBias.southwest.longitude+"|"+viewportBias.northeast.latitude+","+viewportBias.northeast.longitude;
                //String sensor = "sensor=false";
                String sensor = "key=AIzaSyCQCqQlwyXeqf-piBWHi2lzbkwUGvF5n68";

                // url , from where the geocoding data is fetched
                url = url + address + "&" + bias + "&" + sensor;

                // Instantiating DownloadTask to get places from Google Geocoding service
                // in a non-ui thread
                DownloadTask downloadTask = new DownloadTask();

                // Start downloading the geocoding places
                downloadTask.execute(url);
            }
        });

        // Setting click event listener for the me button
        mBtnMyLoc.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
                Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                double longitude = location.getLongitude();
                double latitude = location.getLatitude();

                reverseGeocode(latitude, longitude);

            }
        });
    }


    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try{
            URL url = new URL(strUrl);
            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();

            // Connecting to url
            urlConnection.connect();

            // Reading data from url
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while( ( line = br.readLine()) != null){
                sb.append(line);
            }

            data = sb.toString();
            br.close();

        }catch(Exception e){
            Log.d("Download exception: ", e.toString());
        }finally{
            iStream.close();
            urlConnection.disconnect();
        }

        return data;
    }

    @Override
    public void onMapLongClick(LatLng latLng) {

        reverseGeocode(latLng.latitude, latLng.longitude);

    }

    /** A class, to download Places from Geocoding webservice */
    private class DownloadTask extends AsyncTask<String, Integer, String> {

        String data = null;

        // Invoked by execute() method of this object
        @Override
        protected String doInBackground(String... url) {
            try{
                data = downloadUrl(url[0]);
            }catch(Exception e){
                Log.d("Background Task",e.toString());
            }
            return data;
        }

        // Executed after the complete execution of doInBackground() method
        @Override
        protected void onPostExecute(String result){

            // Instantiating ParserTask which parses the json data from Geocoding webservice
            // in a non-ui thread
            ParserTask parserTask = new ParserTask();

            // Start parsing the places in JSON format
            // Invokes the "doInBackground()" method of the class ParseTask
            parserTask.execute(result);
        }
    }

    /** A class to parse the Geocoding Places in non-ui thread */
    class ParserTask extends AsyncTask<String, Integer, List<HashMap<String,String>>>{

        JSONObject jObject;

        // Invoked by execute() method of this object
        @Override
        protected List<HashMap<String,String>> doInBackground(String... jsonData) {

            List<HashMap<String, String>> places = null;
            GeocodeJSONParser parser = new GeocodeJSONParser();

            try{
                jObject = new JSONObject(jsonData[0]);

                /** Getting the parsed data as a an ArrayList */
                places = parser.parse(jObject);

            }catch(Exception e){
                Log.d("Exception",e.toString());
            }
            return places;
        }

        // Executed after the complete execution of doInBackground() method
        @Override
        protected void onPostExecute(List<HashMap<String,String>> list){

            // Clears all the existing markers
            mMap.clear();

            //for(int i=0;i<list.size();i++){
            if(list.size() > 0){

                // Creating a marker
                MarkerOptions markerOptions = new MarkerOptions();

                // Getting a place from the places list
                HashMap<String, String> hmPlace = list.get(0);

                // Getting latitude of the place
                double lat = Double.parseDouble(hmPlace.get("lat"));

                // Getting longitude of the place
                double lng = Double.parseDouble(hmPlace.get("lng"));

                // Getting name
                String name = hmPlace.get("formatted_address");

                LatLng latLng = new LatLng(lat, lng);

                // Setting the position for the marker
                markerOptions.position(latLng);

                // Setting the title for the marker
                markerOptions.title(name);

                // Placing a marker on the touched position
                mMap.addMarker(markerOptions);

                // Getting viewport of marker
                lat = Double.parseDouble(hmPlace.get("neBoundlat"));
                lng = Double.parseDouble(hmPlace.get("neBoundlng"));
                LatLng neBound = new LatLng(lat, lng);
                lat = Double.parseDouble(hmPlace.get("swBoundlat"));
                lng = Double.parseDouble(hmPlace.get("swBoundlng"));
                LatLng swBound = new LatLng(lat, lng);
                LatLngBounds viewport = new LatLngBounds(swBound, neBound);

                // Locate the first location
                //if(i==0) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(viewport, 0));
                    System.out.println("Zooming to " + lat + ", " + lng);
                    //mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                //}
            }
        }
    }

    private void reverseGeocode(double latitude, double longitude){
        String myLocation = latitude+","+longitude;

        String url = "https://maps.googleapis.com/maps/api/geocode/json?";

        try {
            // encoding special characters like space in the user input place
            myLocation = URLEncoder.encode(myLocation, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String coord = "latlng=" + myLocation;

        String sensor = "key=AIzaSyCQCqQlwyXeqf-piBWHi2lzbkwUGvF5n68";

        // url , from where the geocoding data is fetched
        url = url + coord + "&" + sensor;

        // Instantiating DownloadTask to get places from Google Geocoding service
        // in a non-ui thread
        DownloadTask downloadTask = new DownloadTask();

        // Start downloading the geocoding places
        downloadTask.execute(url);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
