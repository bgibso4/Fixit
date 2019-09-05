package com.honeywell.fixit;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.NestedScrollView;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MWHomeScreen extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback {

    private GoogleMap mMap;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted = false;
    private Location mLastKnownLocation = null;
    private int DEFAULT_ZOOM = 18;
    GeoDataClient mGeoDataClient;
    PlaceDetectionClient mPlaceDetectionClient;
    FusedLocationProviderClient mFusedLocationProviderClient;
    private String TAG = "Error in map";
    private LatLng mDefaultLocation = new LatLng(12.344, 23.4454);

    private FirebaseAuth mAuth;
    private Context appContext;

    private FirebaseFirestore db;
    private DocumentSnapshot request, queue;

    private TextView cardTitle;
    private TextView cardContent;
    private TextView cardImage;
    private View div1;
    private View div2;
    private NestedScrollView pullUpBar;
    private final int pullUpBarMinHeight = 55; //max and min heights in dp
    private final int pullUpBarMaxHeight = 350;
    private String activeRequest = "none";
    private Button completeBtn;
    private static JSONObject responseJson;

    private double driverLat = 0;
    private double driverlng = 0;

    private double destinationLat = 0;
    private double destinationLng = 0;

    Thread driverUpdateThread = null;
    CurrentLocationThread currentLocationThread = null;
    boolean singleDriverThread = false;
    boolean allDriverThread = false;

    private BroadcastReceiver workerNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context,final Intent intent) {
            // Get extra data included in the Intent


            final String requestId = intent.getStringExtra("requestId");

            DocumentReference userRef = db.collection("users").document(mAuth.getCurrentUser().getUid());
            userRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()) {
                        DocumentSnapshot user = task.getResult();
                        if(user.getData().get("activeRequest").equals("none")){

                            final DocumentReference requestRef = db.collection("requests").document(requestId);
                            final DocumentReference queueRef = db.collection("queues").document(requestId);
                            final DocumentReference userRef = db.collection("users").document(mAuth.getCurrentUser().getUid());

                            requestRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                    if (task.isSuccessful()) {
                                        request = task.getResult();

                                        byte[] decodedString = Base64.decode(request.get("image").toString(), Base64.DEFAULT);
                                        Bitmap decodedImg = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                                        ImageView image = new ImageView(appContext);
                                        image.setImageBitmap(decodedImg);

                                        new AlertDialog.Builder(appContext)
                                                .setTitle(request.getData().get("title").toString())
                                                .setMessage(request.getData().get("description").toString())

                                                // Specifying a listener allows you to take an action before dismissing the dialog.
                                                // The dialog is automatically dismissed when a dialog button is clicked.
                                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        hasActiveRequestActions(request.getData());
                                                        requestRef.update("worker", mAuth.getCurrentUser().getUid());
                                                        userRef.update("activeRequest", requestId); 
                                                    }
                                                })

                                                // A null listener allows the button to dismiss the dialog and take no further action.
                                                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        queueRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                                            @Override
                                                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                                if (task.isSuccessful()) {
                                                                    queue = task.getResult();
                                                                    List<String> usersQueue = (List<String>)queue.getData().get("queue");
                                                                    List<String> visited = (List<String>)queue.getData().get("visited");
                                                                    String user = usersQueue.remove(0);
                                                                    visited.add(user);
                                                                    Map<String, Object> updateQueue = new HashMap<>();
                                                                    updateQueue.put("queue", usersQueue);
                                                                    updateQueue.put("visited", visited);
                                                                    queueRef.update(updateQueue);
                                                                } else {
                                                                    Log.d("tag", "get failed with ", task.getException());
                                                                }
                                                            }
                                                        });
                                                    }
                                                })
                                                .setIcon(android.R.drawable.ic_dialog_alert)
                                                .setCancelable(false)
                                                .setView(image)
                                                .show();

                                    } else {
                                        Log.d("tag", "get failed with ", task.getException());
                                    }
                                }
                            });
                        }
                        else {
                            final DocumentReference queueRef = db.collection("queues").document(requestId);
                            queueRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                    if (task.isSuccessful()) {
                                        queue = task.getResult();
                                        List<String> usersQueue = (List<String>)queue.getData().get("queue");
                                        List<String> visited = (List<String>)queue.getData().get("visited");
                                        String user1 = usersQueue.remove(0);
                                        visited.add(user1);
                                        Map<String, Object> updateQueue = new HashMap<>();
                                        updateQueue.put("queue", usersQueue);
                                        updateQueue.put("visited", visited);
                                        queueRef.update(updateQueue);
                                    } else {
                                        Log.d("tag", "get failed with ", task.getException());
                                    }
                                }
                            });
                        }
                    }
                }
            });


        }
    };

    private BroadcastReceiver closedRequestReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context,final Intent intent) {
            Toast.makeText(getApplicationContext(),"Request has been closed",Toast.LENGTH_LONG).show();
            // set request to null
            noActiveRequestActions();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mwhome_screen);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        LocalBroadcastManager.getInstance(this).registerReceiver(
                workerNotificationReceiver, new IntentFilter("workerNotification"));
        LocalBroadcastManager.getInstance(this).registerReceiver(
                closedRequestReciever, new IntentFilter("closedNotification"));

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        getLocationPermission();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapView);
        if(mapFragment!=null){
            mapFragment.getMapAsync(this);
        }

        // Construct a GeoDataClient.
        mGeoDataClient = Places.getGeoDataClient(this);

        // Construct a PlaceDetectionClient.
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this);

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        mAuth = FirebaseAuth.getInstance();
        appContext = this;
        db = FirebaseFirestore.getInstance();

        driverUpdateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                double count = 0.0001;
                while (true) {
                    if(singleDriverThread){
                        try {
                            updateDriverOnMap(mAuth.getCurrentUser().getUid(), count);
                            count += 0.0001;
                            try {
                                int timeInterval = 10000;
                                Thread.sleep(timeInterval);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } catch (Exception e) {
                            Log.e("Exception", e.toString());
                        }
                    }


                }

            }
        });
        driverUpdateThread.start();

        final FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            final DocumentReference userDoc = db.collection("users").document(user.getUid());
            userDoc.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {

                            TextView userName = findViewById(R.id.userName);
                            TextView userEmail = findViewById(R.id.userEmail);

                            userName.setText(document.getData().get("fullName").toString());
                            userEmail.setText(user.getEmail());
                        }
                    }
                }
            });
        }

        cardTitle = findViewById(R.id.cardTitle);
        cardContent = findViewById(R.id.cardContent);
        cardImage = findViewById(R.id.cardImage);
        div1 = findViewById(R.id.cardBar);
        div2 = findViewById(R.id.cardBar2);
        pullUpBar = findViewById(R.id.nestedScrollView);
        completeBtn = findViewById(R.id.completeBtn);
        completeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                completeBtn.setEnabled(false);
                DocumentReference workerDoc = db.collection("users").document(user.getUid());
                workerDoc.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                String actRequest = document.getData().get("activeRequest").toString();
                                Log.d("here", actRequest);
                                DocumentReference requestDoc = db.collection("requests").document(actRequest);
                                requestDoc.update("status", "completed").addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Toast.makeText(MWHomeScreen.this, "Job verification in progress...", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }
                });

            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        cardTitle.setText("Loading...");

        //check for active request
        FirebaseUser user = mAuth.getCurrentUser();
        DocumentReference userDoc = db.collection("users").document(user.getUid());
        userDoc.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        activeRequest = document.getData().get("activeRequest").toString();
                        if (activeRequest.matches("none") || activeRequest.matches("")){
                            noActiveRequestActions();
                            return;
                        } else {
                            //display worker's active request
                            final DocumentReference requestDoc = db.collection("requests").document(activeRequest);
                            requestDoc.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                    if (task.isSuccessful()) {
                                        DocumentSnapshot document = task.getResult();
                                        if (document.exists()) {
                                            hasActiveRequestActions(document.getData());
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            }
        });

    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.mwhome_screen, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_pastRequests) {
            Intent openPastRequests = new Intent(this, PastRequests.class);
            startActivityForResult(openPastRequests, 0);
        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        } else if (id == R.id.nav_logout) {
            logoutAction();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();

        String addy1= "3390+Stratford+Road+NE+Atlanta+GA";
        String addy2 = "715+Peachtree+Street+NE+Atlanta+GA";
        //String stringUrl = FixitUtilities.GetUrl(addy1, addy2, "driving");

    }

    private void getLocationPermission() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                    updateLocationUI();
                    getDeviceLocation();
                }
            }

        }
    }


    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                LocationManager locationManager = (LocationManager)
                        getSystemService(Context.LOCATION_SERVICE);
                currentLocationThread = new CurrentLocationThread(locationManager);
                Thread thread = new Thread(currentLocationThread);
                thread.start();
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void getDeviceLocation() {
        try {
            if (mLocationPermissionGranted) {
                Task locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = (Location) task.getResult();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }
    
    private void logoutAction() {
        DocumentReference userRef = db.collection("users").document(mAuth.getCurrentUser().getUid());
        userRef.update("token", "");
        mAuth.signOut();
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Successfully signed out",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void noActiveRequestActions() {
        driverUpdateThread.interrupt();
        mMap.clear();
        cardTitle.setText("No active request at this time");
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) pullUpBar.getLayoutParams();
        params.height =  (int) (pullUpBarMinHeight * MWHomeScreen.this.getResources().getDisplayMetrics().density); // convert to dp
        pullUpBar.setLayoutParams(params);
        div1.setVisibility(View.GONE);
        cardContent.setVisibility(View.GONE);
        div2.setVisibility(View.GONE);
        cardImage.setVisibility(View.GONE);
        completeBtn.setVisibility(View.GONE);
    }

    private void hasActiveRequestActions(Map request) {
        //driverUpdateThread.start();
        singleDriverThread = true;
        displayRequestInfo(request);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) pullUpBar.getLayoutParams();
        params.height =  (int) (pullUpBarMaxHeight * MWHomeScreen.this.getResources().getDisplayMetrics().density); // convert to dp
        pullUpBar.setLayoutParams(params);
        div1.setVisibility(View.VISIBLE);
        cardContent.setVisibility(View.VISIBLE);
        div2.setVisibility(View.VISIBLE);
        cardImage.setVisibility(View.VISIBLE);
        completeBtn.setVisibility(View.VISIBLE);
        completeBtn.setEnabled(true);
    }

    private void displayRequestInfo(final Map request) {
        DocumentReference customerDoc = db.collection("users").document(request.get("customer").toString());
        customerDoc.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        Map customer = document.getData();
                        String title = "Client: " + customer.get("fullName");
                        String content = "Phone: " + customer.get("phone")
                                + "\nJob Title: " + request.get("title")
                                + "\nTag: " + request.get("tag")
                                + "\nComplexity: " + request.get("complexity")
                                + "\nTools Provided: " + request.get("toolsProvided")
                                + "\nDescription: " + request.get("description");

                        byte[] decodedString = Base64.decode(request.get("image").toString(), Base64.DEFAULT);
                        Bitmap decodedImg = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                        SpannableStringBuilder builder = new SpannableStringBuilder();
                        builder.append("Image:\n ");
                        builder.setSpan(new ImageSpan(decodedImg), builder.length() - 1, builder.length(), 0);

                        cardTitle.setText(title);
                        cardContent.setText(content);
                        cardImage.setText(builder);
                    }
                }
            }
        });
    }

    private void updateDriverOnMap(final String driverID, final double count){
        final DocumentReference userDoc = db.collection("users").document(driverID);
        userDoc.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        final DocumentReference worker = db.collection("requests").document(document.getData().get("activeRequest").toString());
                        worker.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                if (task.isSuccessful()) {
                                    DocumentSnapshot document = task.getResult();
                                    if (document.exists()) {
                                        final DocumentReference request = db.collection("customers").document(document.getData().get("customer").toString());
                                        request.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                if (task.isSuccessful()) {
                                                    DocumentSnapshot document = task.getResult();
                                                    if (document.exists()) {
                                                        HashMap<String, Double> hMap = (HashMap<String, Double>) document.getData().get("location");
                                                        destinationLat = hMap.get("lat") + count;
                                                        destinationLng = hMap.get("lng");

                                                        mMap.clear();

                                                        if (currentLocationThread.lat != 0) {

                                                            LatLng driverPosition = new LatLng(currentLocationThread.lat, currentLocationThread.lng);

                                                            GenerateRoute(driverPosition, new LatLng(destinationLat, destinationLng));
                                //                            VectorDrawable drawable = (VectorDrawable)ContextCompat.getDrawable(appContext, R.drawable.ic_tools_circle);
                                //                            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                                //                                    drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                                                            Bitmap img = BitmapFactory.decodeResource(appContext.getResources(), R.drawable.ic_tools);
                                                            mMap.addMarker(new MarkerOptions().position(driverPosition).title("Maintenance Worker").icon(BitmapDescriptorFactory.fromBitmap(img)));
                                                        }
                                                    }
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        });

                    }
                }
            }
        });
    }

    private void GenerateRoute(LatLng addy1, LatLng addy2){

        String stringUrl = FixitUtilities.GetUrl(addy1, addy2, "driving");
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, stringUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        try {
                            responseJson = new JSONObject(response);
                            String points = responseJson.getJSONArray("routes").getJSONObject(0).getJSONObject("overview_polyline").getString("points");
                            List<LatLng> latLngs = PolyUtil.decode(points);
                            PolylineOptions polylineOptions = new PolylineOptions().width(10).color(Color.BLUE).addAll(latLngs);

                            JSONArray legsArray = responseJson.getJSONArray("routes").getJSONObject(0).getJSONArray("legs");

                            //DONT NEED DRIVER MARKER
//                            LatLng start = new LatLng(legsArray.getJSONObject(0).getJSONObject("start_location").getDouble("lat"), legsArray.getJSONObject(0).getJSONObject("start_location").getDouble("lng"));
//                            mMap.addMarker(new MarkerOptions().position(start).title("Start"));

                            LatLng end = new LatLng(legsArray.getJSONObject(0).getJSONObject("end_location").getDouble("lat"), legsArray.getJSONObject(0).getJSONObject("end_location").getDouble("lng"));
                            mMap.addMarker(new MarkerOptions().position(end).title("Destination"));

                            mMap.addPolyline(polylineOptions);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //testText.setText("That didn't work!");
            }
        });

// Add the request to the RequestQueue.
        queue.add(stringRequest);
    }
}
