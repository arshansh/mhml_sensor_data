package net.mandown;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.media.Image;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import net.mandown.db.DBService;
import net.mandown.games.GameMenuActivity;
import net.mandown.sensors.SensorService;


public class MainActivity extends AppCompatActivity {

    private final String mDisclaimerText =
            "This app is distributed for the collection of accelerometer, gyroscope, and " +
            "magnetometer data over time. The use of the app's games will collect information " +
            "and store it online. The app does not accept responsibility for inaccurate readings " +
            "or results for intoxication levels.\n\nIf you wish to opt out, please uninstall " +
            "the application.";

    // Set up a new handler to update the home textview with number of DB entries every 100ms
    private final Handler mDbUpdateHandler = new Handler();
    private Runnable mUpdateDBTxt = new Runnable() {
        @Override
        public void run() {
            if (DBService.sInstance != null) {
                TextView txtDbInfo = (TextView) findViewById(R.id.txtDbView);
                txtDbInfo.setText(String.format("%d accel data readings",
                        DBService.sInstance.getNumAccelReadings()));
                mDbUpdateHandler.postDelayed(mUpdateDBTxt, 100);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //setting the orientation to landscape
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        //getting the button
        ImageButton btnGamePlay = (ImageButton) findViewById(R.id.JoyStick);
        //adding a click listener
        btnGamePlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //starting game activity
                startActivity(new Intent(getApplicationContext(), GameMenuActivity.class));
            }
        });

        // Start the sensor service to collect data
        startService(new Intent(this, SensorService.class));

        // Post event to handler to begin DB updates
        mDbUpdateHandler.postDelayed(mUpdateDBTxt, 100);

        // Create a disclaimer window using AlertDialog
        AlertDialog dialog = (new AlertDialog.Builder(this))
                .setTitle("ManDown Disclaimer")
                .setMessage(mDisclaimerText)
                .setPositiveButton("I understand", null)
                .create();
        dialog.show();
    }

}

