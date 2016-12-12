package com.example.fernando.mypersonalscale;

import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.text.format.Time;
import android.Manifest;

import com.example.fernando.mypersonalscale.common.logger.Log;
import com.example.fernando.mypersonalscale.common.logger.LogView;
import com.example.fernando.mypersonalscale.common.logger.LogWrapper;
import com.example.fernando.mypersonalscale.common.logger.MessageOnlyLogFilter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessActivities;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.request.SessionInsertRequest;
import com.google.android.gms.fitness.request.SessionReadRequest;
import com.google.android.gms.fitness.result.SessionReadResult;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.text.DateFormat;

import static java.text.DateFormat.getTimeInstance;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

	private final String DEVICE_NAME="BT-820";
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    public static final String SAMPLE_SESSION_NAME = "Peso";
    Button connectButton;
    TextView textView;
    boolean deviceConnected=false;
    byte buffer[];
    boolean stopThread;
    private GoogleApiClient mClient;


    private final String TAG = "MyScale";
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    float weight = 80;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = (TextView) findViewById(R.id.textView);
        connectButton = (Button) findViewById(R.id.connectButton);

        initializeLogging();
        if (!checkPermissions()) {
            requestPermissions();
        }
    }


    void buildClient() {
        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.SENSORS_API)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(@Nullable Bundle bundle) {
                                

                            }

                            @Override
                            public void onConnectionSuspended(int i) {

                            }

                            @Override
                            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

                            }
                        }







        )
                .addOnConnectionFailedListener(this)
                .build();
        mClient.connect();
    }


    private void initializeLogging() {
        // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper);
        // Filter strips out everything except the message text.
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
        Log.i(TAG, "Ready");
    }

    private void requestPermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION);

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
        } else {
            Log.i(TAG, "Requesting permission");
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

	public boolean BTinit()
	    {
	        boolean found=false;
	        BluetoothAdapter bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
	        if (bluetoothAdapter == null) {
	            Toast.makeText(getApplicationContext(),"Device doesnt Support Bluetooth",Toast.LENGTH_SHORT).show();
	        }
	        if(!bluetoothAdapter.isEnabled())
	        {
	            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	            startActivityForResult(enableAdapter, 0);
	            try {
	                Thread.sleep(1000);
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
	        }
	        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
	        if(bondedDevices.isEmpty())
	        {
	            Toast.makeText(getApplicationContext(),"Please Pair the Device first",Toast.LENGTH_SHORT).show();
	        }
	        else
	        {
	            for (BluetoothDevice iterator : bondedDevices)
	            {
                    if(iterator.getName().equals(DEVICE_NAME))
	                {
	                    device=iterator;
	                    found=true;
	                    break;
	                }
	            }
	        }
	        return found;
	    }

    public boolean BTconnect()
    {
        boolean connected=true;
        try {
            socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket",new Class[] {int.class}).invoke(device,1);
            socket.connect();
        } catch (Exception e) {
            e.printStackTrace();
            connected = false;
        }
        if(connected)
        {
            try {
                outputStream=socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStream=socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return connected;
    }

    void beginListenForData()
    {
        final Handler handler = new Handler();
        stopThread = false;
        buffer = new byte[1024];
        Thread thread  = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopThread)
                {
                    try
                    {
                        int byteCount = inputStream.available();
                        if(byteCount > 0)
                        {
                            byte[] rawBytes = new byte[byteCount];
                            inputStream.read(rawBytes);
                            final String string = new String(rawBytes,"UTF-8");
                            handler.post(new Runnable() {
                                        public void run()
                                        {
                                            textView.setText(string);;
                                        }
                                    });

                        }
                    }
                    catch (IOException ex)
                    {
                        stopThread = true;
                    }
                }
            }
        });

        thread.start();
    }


    public void onClickConnect(View view) {
        if(BTinit())
        {
            if(BTconnect())
            {
                deviceConnected=true;
                connectButton.setEnabled(false);
                beginListenForData();
            }
        }
    }

    private DataSet createDataForRequest(DataType dataType, int dataSourceType, Object values,
                                         long startTime, long endTime, TimeUnit timeUnit) {
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName("MyPersonalScale")
                .setDataType(dataType)
                .setType(dataSourceType)
                .build();

        DataSet dataSet = DataSet.create(dataSource);
        DataPoint dataPoint = dataSet.createDataPoint().setTimeInterval(startTime, endTime, timeUnit);

        if (values instanceof Integer) {
            dataPoint = dataPoint.setIntValues((Integer)values);
        } else {
            dataPoint = dataPoint.setFloatValues((Float)values);
        }

        dataSet.add(dataPoint);

        return dataSet;
    }



    public void onClickSend (View view){
        Time now = new Time(Time.getCurrentTimezone());
        now.setToNow();
        DataSet dataSet = createDataForRequest(DataType.TYPE_WEIGHT,0,weight,now.toMillis(true),now.toMillis(true),TimeUnit.MILLISECONDS);
        Fitness.HistoryApi.insertData(mClient,dataSet).await(1, TimeUnit.MINUTES);
    }

}
