package minils.de.bikesensor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    public static final int REQUEST_ENABLE_BT = 100;
    public static final String DEVICE_NAME = "Bike Sensor";
    public static final String TAG = MainActivity.class.getSimpleName();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothLeScanner bluetoothLeScanner;

    private TextView tvTextDisplay;
    private TextView tvStatus;
    private TextView tvBTStatus;
    private TextView tvConnStatus;
    private TextView tvMACStatus;
    private TextView tvDataStatus;

    private ProgressBar progressBar;

    private RFduinoService rFduinoService;

    private boolean bluetoothOn = false;
    private boolean connected = false;
    private String MAC = "";

    private Measurements measurements;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_ON) {
                changeBluetoothStatus(true);
                initScan();
            } else if (state == BluetoothAdapter.STATE_OFF) {
                changeBluetoothStatus(false);
            }
        }
    };

    private void changeBluetoothStatus(boolean b) {
        bluetoothOn = b;
        tvBTStatus.setText(getText(bluetoothOn ? R.string.btStatusOn : R.string.btStatusOff));
    }

    private void changeConnectionStatus(boolean c) {
        connected = c;
        tvConnStatus.setText(getText(connected ? R.string.connectionYes : R.string.connectionNo));
        if (bluetoothDevice != null) {
            MAC = bluetoothDevice.getAddress().toString();
            tvMACStatus.setText(getText(R.string.macAddress) + " " + MAC);
        }
    }

    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateStatus((bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE) ? "[scanModeReceiver] scanning" : "[scanModeReceiver] stopped scanning");
            progressBar.setVisibility(View.GONE);
        }
    };

    private final ServiceConnection rfduinoServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rFduinoService = ((RFduinoService.LocalBinder) service).getService();
            if (rFduinoService.initialize()) {
                if (rFduinoService.connect(bluetoothDevice.getAddress())) {
                    updateStatus("[rfduinoServiceConnection] connecting...");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            rFduinoService = null;
            updateStatus("[rfduinoServiceConnection] disconnected");
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                updateStatus("[rfduinoServiceConnection] connected");
                changeConnectionStatus(true);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                updateStatus("[rfduinoServiceConnection] disconnected");
                changeConnectionStatus(false);
                scanDevices(true);
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte[] d = intent.getByteArrayExtra(RFduinoService.EXTRA_DATA);
                //int temp = HexAsciiHelper.byteArrayToInt(d);
                Log.d(TAG, "###received: " + HexAsciiHelper.byteArrayToInt(d));
                measurements.interpretIncome(d);
            }
        }
    };

    private int amount = 0;

    public void incAmount() {
        amount++;
        String status = (String) getString(R.string.dataCounter, amount);
        tvDataStatus.setText(status);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            bluetoothLeScanner.stopScan(scanCallback);
            progressBar.setVisibility(View.GONE);
            updateStatus("Found Device: " + BluetoothHelper.getDeviceInfoText(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes()));
            Log.d(TAG, "Binding service");
            bluetoothDevice = result.getDevice();
            if (bluetoothDevice == null) {
                updateStatus("Did not find any device.. Restart scanning.");
                scanDevices(true);
                return;
            }
            if (rFduinoService == null) {
                Intent rfduinoIntent = new Intent(MainActivity.this, RFduinoService.class);
                bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
            } else {
                rFduinoService.connect(bluetoothDevice.getAddress());
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        measurements = Measurements.getMeasurements(this);

        tvTextDisplay = (TextView) findViewById(R.id.textDisplay);
        tvStatus = (TextView) findViewById(R.id.textStatus);
        tvBTStatus = (TextView) findViewById(R.id.textBTStatus);
        tvConnStatus = (TextView) findViewById(R.id.textConnStatus);
        tvMACStatus = (TextView) findViewById(R.id.textMACStatus);
        tvDataStatus = (TextView) findViewById(R.id.textDataStatus);

        String status = (String) getString(R.string.dataCounter, 0);
        tvDataStatus.setText(status);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // SETUP BLUETOOTH
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            changeBluetoothStatus(true);
            initScan();
        }

    }

    private void initScan() {
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        updateStatus("Bluetooth enabled");
        updateStatus("Looking for device...");

        // FIND DEVICE

        scanDevices(true);
    }

    private void scanDevices(boolean fast) {
        ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder();
        scanFilterBuilder.setDeviceName(DEVICE_NAME);
        //scanFilterBuilder.setServiceUuid(new ParcelUuid(RFduinoService.UUID_SERVICE));

        List<ScanFilter> filters = new ArrayList<ScanFilter>(1);
        filters.add(scanFilterBuilder.build());

        ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
        scanSettingsBuilder.setReportDelay(0);
        scanSettingsBuilder.setScanMode(fast? ScanSettings.SCAN_MODE_LOW_POWER : ScanSettings.SCAN_MODE_LOW_POWER);

        try {
            bluetoothLeScanner.startScan(filters, scanSettingsBuilder.build(), scanCallback);
            progressBar.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            // bLEScanner is dead
            if (e instanceof DeadObjectException) {
                Toast.makeText(this, "RESTART BLUETOOTH", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());
    }

    @Override
    protected void onStop() {
        super.onStop();

        bluetoothLeScanner.stopScan(scanCallback);

        unregisterReceiver(scanModeReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
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

    public void updateStatus(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvTextDisplay.setText(tvTextDisplay.getText() + "\n" + text);
            }
        });
    }
}
