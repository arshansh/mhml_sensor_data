package net.mandown;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

//import android.support.v7.app.AlertDialog;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import net.mandown.db.DBService;
import net.mandown.db.IntoxicationService;
import net.mandown.games.GameMenuActivity;
import net.mandown.history.HistoryActivity;
import net.mandown.journal.JournalActivity;
import net.mandown.sensors.SensorService;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import net.mandown.sensors.SensorSample;
import net.mandown.sensors.SensorType;
import net.mandown.sensors.WalkSensorService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        View.OnClickListener {

    // Define constants
    private static final int REQUEST_PHONE_CALL = 1;
    private static final String BEER_KEY = "net.mandown.key.beer";
    private static final String WINE_KEY = "net.mandown.key.wine";
    private static final String COCKTAIL_KEY = "net.mandown.key.cocktail";
    private static final String SHOT_KEY = "net.mandown.key.shot";
    private static final String WATCH_RX_KEY = "net.mandown.key.watchrx";
    private static final String WATCH_TX_FLOAT_KEY = "net.mandown.key.watchtxfloat";
    private static final String WATCH_TX_LONG_KEY = "net.mandown.key.watchtxlong";
    private static final String WATCH_SOS_KEY = "net.mandown.key.SOS";
    private static final String INTOX_KEY = "net.mandown.key.intox";
    private static final long CONNECTION_TIME_OUT_MS = 100;
    private static final int RC_SIGN_IN = 9001;
    private static final String TAG = "MainActivity";

    private Vibrator myVibes;

    // Define member variables
    private final String mDisclaimerText =
            "This app is distributed for the collection of accelerometer, gyroscope, and " +
            "magnetometer data over time. The use of the app's games will collect information " +
            "and store it online. The app does not accept responsibility for inaccurate readings " +
            "or results for intoxication levels.\n\nIf you wish to opt out, please uninstall " +
            "the application.";
    private GoogleApiClient mGoogleApiClient;

    // Define UI Control Variables
    private Toolbar mToolbar;
    private ImageButton btnBeerGlass;
    private TextView txtBeer;

    private TextView mStatusTextView;


    // Setup intoxication receiver for updated levels
    private BroadcastReceiver mIntoxReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            float intoxLevel = intent.getFloatExtra("ml", -1);
            if (intoxLevel >= 0) {
                update_drunk_level(Math.round(intoxLevel));
            }
        }
    };



    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //setting the orientation to portrait
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        myVibes = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        mAuth = FirebaseAuth.getInstance();


        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                }
                // ...
            }
        };

        // Set up toolbar with options
        mToolbar = (Toolbar) findViewById(R.id.tool_bar);
        setSupportActionBar(mToolbar);

        // Only display disclaimer if the app is running for the first time
        if (isFirstTime()) {
            AlertDialog dialog = (new AlertDialog.Builder(this))
                    .setTitle("ManDown Disclaimer")
                    .setMessage(mDisclaimerText)
                    .setPositiveButton("I understand", null)
                    .create();
            dialog.show();
        }


        // Configure sign-in to request the user's ID, email address, and basic
// profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        // Build a GoogleApiClient with access to the Google Sign-In API and the
// options specified by gso.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .addApi(Plus.API)
                .addScope(new Scope(Scopes.PROFILE))
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addOnConnectionFailedListener(this)
                .build();



        SignInButton signInButton = (SignInButton) findViewById(R.id.sign_in_button);
        signInButton.setSize(SignInButton.SIZE_STANDARD);

        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out).setOnClickListener(this);
        mStatusTextView = (TextView) findViewById(R.id.status);


        // Start the sensor service to collect data
        if (getApplicationContext() != null) {
            startService(new Intent(this, SensorService.class));
            startService(new Intent(this, IntoxicationService.class));
            startService(new Intent(this, DBService.class));
        }

        // Initialise Wearable connection

        // Connect UI components
        txtBeer = (TextView) findViewById(R.id.watchtext);
        btnBeerGlass = (ImageButton) findViewById(R.id.BeerGlass);

    }


    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
        // Register the broadcast receiver again
        registerReceiver(mIntoxReceiver, new IntentFilter(getString(R.string.intox_broadcast)));

        // Check for any intoxication level already there
        if (IntoxicationService.sInstance != null) {
            if (IntoxicationService.sInstance.getLastTimestamp() > 0) {
                update_drunk_level(Math.round(IntoxicationService.sInstance.getIntoxLevel()));
            }
        } else {
            update_drunk_level(0);
        }

        Log.i("RConnected","ResumeConnected!");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        unregisterReceiver(mIntoxReceiver);
        mGoogleApiClient.disconnect();
        Log.i("dis","Disconnected");
    }



    private boolean isFirstTime()
    {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        boolean ranBefore = preferences.getBoolean("RanBefore", false);
        if (!ranBefore) {
            // first time
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("RanBefore", true);
            editor.apply();
        }
        return !ranBefore;
    }

    private void update_drunk_level(int d_lvl){

        // Anchor the level in allowed limits
        int drunk_level = Math.max(Math.min(d_lvl, 2), 0);
        // TODO update the textview beside the beer glass

        if (drunk_level == 0) {
            btnBeerGlass.setImageResource(R.drawable.empty_beer_glass);
            txtBeer.setText("Sober");
        } else if (drunk_level == 1) {
            btnBeerGlass.setImageResource(R.drawable.glass_beer);
            txtBeer.setText("Tipsy");
        } else if (drunk_level == 2) {
            btnBeerGlass.setImageResource(R.drawable.full_glass_beer);
            txtBeer.setText("Drunk!");
            myVibes.vibrate(1000);
            AlertDialog dialog = (new AlertDialog.Builder(this))
                    .setTitle("You're drunk!")
                    .setMessage("It's time to put down the bottle and go home")
                    .setPositiveButton("I understand", null)
                    .create();
            dialog.show();
        }

        //Update watch with intoxication data
        sendIntoxData(drunk_level);
    }



    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sign_in_button:
                signIn();

                break;
            case R.id.sign_out:
                signOut();
                break;

        }
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        // [START_EXCLUDE]
                        updateUI(false);
                        // [END_EXCLUDE]
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_menu1:
                // User chose the "Settings" item, show the app settings UI...
                return true;

            case R.id.action_menu2:
                AlertDialog dialog = (new AlertDialog.Builder(this))
                        .setTitle("ManDown Disclaimer")
                        .setMessage(mDisclaimerText)
                        .setPositiveButton("I understand", null)
                        .create();
                dialog.show();
                return true;
            case R.id.action_menu3:
                startService(new Intent(this, WalkSensorService.class));
                startWatchAccel();
                return true;
            case R.id.emergency:
                new AlertDialog.Builder(this)
                        .setTitle("Contact Emergency Help")
                        .setMessage("Are you sure you want to send a distress call")
                        .setPositiveButton(android.R.string.yes,
                                new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // continue with action

                                Intent intent = new Intent(Intent.ACTION_CALL,
                                        Uri.parse("tel:" + "999"));

                                if (ContextCompat.checkSelfPermission(MainActivity.this,
                                        Manifest.permission.CALL_PHONE) !=
                                        PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            new String[]{Manifest.permission.CALL_PHONE},
                                            REQUEST_PHONE_CALL);
                                } else {
                                    startActivity(intent);
                                }

                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(result);
        }
    }

    private void handleSignInResult(GoogleSignInResult result) {
        Log.d(TAG, "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            // Signed in successfully, show authenticated UI.
            GoogleSignInAccount acct = result.getSignInAccount();
            firebaseAuthWithGoogle(acct);

            mStatusTextView.setText(getString(R.string.signed_in_fmt, acct.getDisplayName()));
            updateUI(true);
        } else {
            // Signed out, show unauthenticated UI.
            updateUI(false);
        }
    }


    private void updateUI(boolean signedIn) {
        if (signedIn) {
            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            findViewById(R.id.sign_out).setVisibility(View.VISIBLE);
            findViewById(R.id.status).setVisibility(View.VISIBLE);
        } else {
            mStatusTextView.setText(R.string.signed_out);
            findViewById(R.id.status).setVisibility(View.GONE);
            findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            findViewById(R.id.sign_out).setVisibility(View.GONE);
        }
    }

    /* Image Button Listeners */
    public void startActivityGameMenu(View view){
        Intent intent = new Intent(this, GameMenuActivity.class);
        startActivity(intent);
    }
    public void startActivityHistory(View view){
        Intent intent = new Intent(this, HistoryActivity.class);
        startActivity(intent);
    }
    public void startActivityJournal(View view){
        Intent intent = new Intent(this, JournalActivity.class);
        startActivity(intent);
	}


    //Communications code below
    @Override
    public void onConnected(Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Log.i("listener Connected","Mobile Connected!");
    }
    @Override
    public void onConnectionSuspended(int cause){
        Log.d("Connection suspended", "onConnectionSuspended: " + cause);
    }
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d("connect failed", "onConnectionFailed: " + result);
    }

    private boolean watchbool = false;
    //Tell watch to start measuring
    private void startWatchAccel(){
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/watchaccel");
        putDataMapReq.getDataMap().putBoolean(WATCH_RX_KEY, watchbool);
        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        putDataReq.setUrgent();
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
        Log.d("watchaccel","TRIGGER WATCH!");
        // Start sensorservice here instead of constantly starting in background and make it
        // last approx 11seconds
        watchbool = !watchbool;
    }

    //Send updated intoxication data to watch
    private void sendIntoxData(int drunk_level){
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/intoxication");
        putDataMapReq.getDataMap().putInt(INTOX_KEY, drunk_level);
        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        putDataReq.setUrgent();
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
        Log.d("intoxwatch", "SENT INTOXICATION DATA");
    }

    //In case of user input from watch
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.i("data","data CHANGED!");
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo("/beer") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    ((TextView) findViewById(R.id.watchtext)).setText(dataMap.getString(BEER_KEY));
                    //dataMap.remove(BEER_KEY); //Delete item so the next write is a "new" entry to trigger onDataChanged
                    dataMap.putString(BEER_KEY, "unknown");
                }
                else if (item.getUri().getPath().compareTo("/wine") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    ((TextView) findViewById(R.id.watchtext)).setText(dataMap.getString(WINE_KEY));
                    //dataMap.remove(WINE_KEY);
                    dataMap.putString(WINE_KEY, "unknown");
                }
                else if (item.getUri().getPath().compareTo("/cocktail") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    ((TextView) findViewById(R.id.watchtext)).setText(dataMap.getString(COCKTAIL_KEY));
                    dataMap.remove(COCKTAIL_KEY);
                }
                else if (item.getUri().getPath().compareTo("/shot") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    ((TextView) findViewById(R.id.watchtext)).setText(dataMap.getString(SHOT_KEY));
                    dataMap.remove(SHOT_KEY);
                }
                else if (item.getUri().getPath().compareTo("/watchdata") == 0) { //Watch reported back accel readings
                    //Log.d("datachanged", "GOT WATCH DATA");
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    long[] watchTimeStamps = dataMap.getLongArray(WATCH_TX_LONG_KEY);
                    float[] watchAccelValues = dataMap.getFloatArray(WATCH_TX_FLOAT_KEY);

                    ((TextView) findViewById(R.id.watchsamplenum)).setText(Integer.toString(watchTimeStamps.length) + ' ' + Integer.toString(watchAccelValues.length));

                    List<SensorSample> watchCombinedData = new ArrayList<SensorSample>(watchTimeStamps.length);

                    for (int i=0; i<watchTimeStamps.length; i++) {
                        watchCombinedData.add(new SensorSample(watchTimeStamps[i], watchAccelValues[3*i], watchAccelValues[3*i+1], watchAccelValues[3*i+2]));
                    }
                    //Log.d("datachanged", Integer.toString(watchCombinedData.size()));
                    //Send watch data to database here
                    DBService.startPutActionWatchAccel(getApplicationContext(), watchCombinedData, SensorType.ACCELEROMETER);
                }
                else if (item.getUri().getPath().compareTo("/SOS") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    long sosTime = dataMap.getLong(WATCH_SOS_KEY);
                    Log.d("soswatch", Long.toString(sosTime));
                    Log.d("soswatch", Long.toString(System.currentTimeMillis()));
                    //Just to be absolutely sure this was not triggered erroneously,
                    //if time which SOS was sent was less than 15 seconds ago (to generously account for transmission delay), treat as real SOS
                    if (System.currentTimeMillis() - sosTime <= 15000) {
                        Log.d("soswatch", "Times are OK");
                        Intent intent = new Intent(Intent.ACTION_CALL,
                                Uri.parse("tel:" + "SOS")); //supposed to be 999 but for testing purposes this is used in place

                        if (ContextCompat.checkSelfPermission(MainActivity.this,
                                Manifest.permission.CALL_PHONE) !=
                                PackageManager.PERMISSION_GRANTED) {
                            Log.d("soswatch", "CHECK PASSED");
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.CALL_PHONE},
                                    REQUEST_PHONE_CALL);
                        } else {
                            Log.d("soswatch", "CHECK FAILED");
                            startActivity(intent);
                        }
                    }
                }

            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                // DataItem deleted
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }
    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());

                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInWithCredential", task.getException());
                            Toast.makeText(MainActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                        // ...
                    }
                });
    }
}

