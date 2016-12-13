package com.example.dpereira14.scale;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataUpdateRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private static final int RC_SIGN_IN = 1;
    private static final String TAG = "Scale";
    private static boolean connected = false;
    private GoogleApiClient mGoogleApiClient;
    TextView textView;
    TextView weightView;
    Button connectButton;
    private static float weight = 98;
    private final String DEVICE_NAME = "HC-05";
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    boolean deviceConnected = false;
    byte buffer[];
    boolean stopThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = (TextView) findViewById(R.id.textView);
        weightView = (TextView) findViewById(R.id.weightView);
        textView.setText("Login to continue");
        findViewById(R.id.sign_in_button).setOnClickListener(this);
        connectButton = (Button) findViewById(R.id.connectButton);


        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(Scopes.FITNESS_BODY_READ_WRITE))
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* MainActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_BODY_READ_WRITE))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected To Google Client");
                                connected = true;
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .build();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;
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
            textView.setText(getString(R.string.signed_in_fmt, acct.getDisplayName()));
        } else {
            // Signed out, show unauthenticated UI.
            textView.setText("Login Failed");
        }

    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }



    private DataSet createWeightDataForRequest(DataType dataType, int dataSourceType, Object values,
                                         long startTime, long endTime, TimeUnit timeUnit) {
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName("com.example.dpereira14.scale")
                .setDataType(dataType)
                .setType(dataSourceType)
                .build();

        DataSet dataSet = DataSet.create(dataSource);
        DataPoint dataPoint = dataSet.createDataPoint().setTimeInterval(startTime, endTime, timeUnit.MILLISECONDS);

        if (values instanceof Integer) {
            dataPoint = dataPoint.setIntValues((Integer) values);
        } else {
            dataPoint = dataPoint.setFloatValues((Float) values);
        }

        dataSet.add(dataPoint);

        return dataSet;
    }

    private DataSet insertWeight() {
        Log.i(TAG, "Creating a new data insert request.");
        // set insertion time
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        cal.add(Calendar.MILLISECOND, 0);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();

        // Create a data set
        DataSet dataSet = createWeightDataForRequest(DataType.TYPE_WEIGHT, 0, weight, startTime, endTime, TimeUnit.MILLISECONDS);
        return dataSet;
    }

    private class InsertAndVerifyWeight extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            DataSet dataSet = insertWeight();

            Log.i(TAG, "Inserting the dataset in the History API.");
            com.google.android.gms.common.api.Status insertStatus =
                    Fitness.HistoryApi.insertData(mGoogleApiClient, dataSet).await(1, TimeUnit.MINUTES);


            if (!insertStatus.isSuccess()) {
                Log.i(TAG, "There was a problem inserting the dataset.");
                String msg = insertStatus.toString(); // vem vazio!!
                Log.i(TAG, msg);
                return null;
            }

            Log.i(TAG, "Data insert was successful!");
            return null;
        }
    }

    public class UpdateAndVerifyWeight extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            // Create a new dataset and update request.
            DataSet dataSet = updateFitnessData();
            long startTime = 0;
            long endTime = 0;

            // Get the start and end times from the dataset.
            for (DataPoint dataPoint : dataSet.getDataPoints()) {
                startTime = dataPoint.getStartTime(TimeUnit.MILLISECONDS);
                endTime = dataPoint.getEndTime(TimeUnit.MILLISECONDS);
            }

            Log.i(TAG, "Updating the dataset in the History API.");


            DataUpdateRequest request = new DataUpdateRequest.Builder()
                    .setDataSet(dataSet)
                    .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                    .build();

            com.google.android.gms.common.api.Status updateStatus =
                    Fitness.HistoryApi.updateData(mGoogleApiClient, request)
                            .await(1, TimeUnit.MINUTES);

            if (!updateStatus.isSuccess()) {
                Log.i(TAG, "There was a problem updating the dataset.");
                return null;
            }
            Log.i(TAG, "Data update was successful.");

            return null;
        }
    }


        private DataSet updateFitnessData() {
            Log.i(TAG, "Creating a new data update request.");
            Calendar cal = Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
            cal.add(Calendar.MINUTE, 0);
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar.MINUTE, -50);
            long startTime = cal.getTimeInMillis();

            // Create a data source
            DataSource dataSource = new DataSource.Builder()
                    .setAppPackageName("com.example.dpereira14.scale")
                    .setDataType(DataType.TYPE_WEIGHT)
                    .setType(DataSource.TYPE_RAW)
                    .build();

            // Create a data set
            DataSet dataSet = DataSet.create(dataSource);
            DataPoint dataPoint = dataSet.createDataPoint()
                    .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS);
            dataPoint.getValue(Field.FIELD_WEIGHT).setFloat(weight);
            dataSet.add(dataPoint);

            return dataSet;
        }

    public void onClickSendWeight(View view) {
        if (connected) {
            new UpdateAndVerifyWeight().execute();
        }
        else {
            textView.setText("Login to send weight");
        }
    }



    // BLUTOOTH

    public boolean BTinit() {
        boolean found = false;
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Device doesnt Support Bluetooth", Toast.LENGTH_SHORT).show();
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableAdapter, 0);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        if (bondedDevices.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Please Pair the Device first", Toast.LENGTH_SHORT).show();
        } else {
            for (BluetoothDevice iterator : bondedDevices) {
                if (iterator.getName().equals(DEVICE_NAME)) {
                    device = iterator;
                    found = true;
                    break;
                }
            }
        }
        return found;
    }

    public boolean BTconnect() {
        boolean connected = true;
        try {
            socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(device, 1);
            socket.connect();
        } catch (Exception e) {
            e.printStackTrace();
            connected = false;
        }
        if (connected) {
            try {
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return connected;
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        stopThread = false;
        buffer = new byte[1024];
        Thread thread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopThread) {
                    try {
                        int byteCount = inputStream.available();
                        if (byteCount > 0) {
                            byte[] rawBytes = new byte[byteCount];
                            inputStream.read(rawBytes);
                            final String string = new String(rawBytes, "UTF-8");
                            handler.post(new Runnable() {
                                public void run() {
                                    weightView.setText(string);
                                    ;
                                }
                            });

                        }
                    } catch (IOException ex) {
                        stopThread = true;
                    }
                }
            }
        });

        thread.start();
    }


    public void onClickConnect(View view) {
        if (BTinit()) {
            if (BTconnect()) {
                deviceConnected = true;
                connectButton.setEnabled(false);
                beginListenForData();
            }
        }
    }

}
