package com.example.tp3syscomm;

import static android.content.ContentValues.TAG;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

public class MainActivity extends AppCompatActivity {
    // Constants
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS_CODE = 2;
    private static final long SCAN_PERIOD = 10000; // Scan for 10 seconds

    // Location and Navigation Service UUIDs
    // TODO: Replace with your actual Nordic nRF service UUIDs
    private static final UUID SERVICE_UUID = UUID.fromString("00001819-0000-1000-8000-00805f9b34fb"); // Location and Navigation Service
    private static final UUID LN_FEATURE_UUID = UUID.fromString("00002a6a-0000-1000-8000-00805f9b34fb"); // LN Feature (Mandatory)
    private static final UUID LOCATION_SPEED_UUID = UUID.fromString("00002a67-0000-1000-8000-00805f9b34fb"); // Location and Speed (Mandatory)
    private static final UUID POSITION_QUALITY_UUID = UUID.fromString("00002a69-0000-1000-8000-00805f9b34fb"); // Position Quality (Optional)
    private static final UUID LN_CONTROL_POINT_UUID = UUID.fromString("00002a6b-0000-1000-8000-00805f9b34fb"); // LN Control Point (Optional)
    private static final UUID NAVIGATION_UUID = UUID.fromString("00002a68-0000-1000-8000-00805f9b34fb"); // Navigation (Optional)
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); // CCCD

    // UI Components
    private ListView devicesListView;
    private ArrayAdapter<String> devicesArrayAdapter;
    private ArrayList<String> deviceList;
    private TextView tvConnectionStatus;
    private TextView tvLnFeature;
    private TextView tvLocationSpeed;
    private TextView tvPositionQuality;
    private TextView tvNavigation;
    private Button btnStartScan;
    private Button btnDisconnect;
    private LinearLayout dataPanel;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Handler handler = new Handler();
    private ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private boolean scanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        initializeBluetoothAdapter();
        initializeUIComponents();
        setupEventListeners();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.devices_list), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void initializeBluetoothAdapter() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    private void initializeUIComponents() {
        devicesListView = findViewById(R.id.devices_list);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvLnFeature = findViewById(R.id.tv_ln_feature);
        tvLocationSpeed = findViewById(R.id.tv_location_speed);
        tvPositionQuality = findViewById(R.id.tv_position_quality);
        tvNavigation = findViewById(R.id.tv_navigation);
        btnStartScan = findViewById(R.id.btn_activate_bt);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        dataPanel = findViewById(R.id.data_panel);

        deviceList = new ArrayList<>();
        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        devicesListView.setAdapter(devicesArrayAdapter);

        // Initially hide data panel
        dataPanel.setVisibility(LinearLayout.GONE);
        updateConnectionStatus("Disconnected", false);
    }

    private void setupEventListeners() {
        btnStartScan.setOnClickListener(v -> activateBluetooth());

        btnDisconnect.setOnClickListener(v -> disconnectDevice());

        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            if (scanning) {
                scanLeDevice(false);
            }
            BluetoothDevice device = discoveredDevices.get(position);
            connectToDevice(device);
        });
    }

    private void activateBluetooth() {
        if (!checkAndRequestPermissions()) {
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            scanLeDevice(true);
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        if (enable) {
            Toast.makeText(this, "Scanning for BLE devices...", Toast.LENGTH_SHORT).show();
            handler.postDelayed(() -> {
                scanning = false;
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            scanning = true;
            discoveredDevices.clear();
            deviceList.clear();
            devicesArrayAdapter.notifyDataSetChanged();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.startScan(leScanCallback);
            }
        } else {
            scanning = false;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                String deviceName = device.getName();
                if (deviceName != null && !discoveredDevices.contains(device)) {
                    discoveredDevices.add(device);
                    devicesArrayAdapter.add(deviceName + "\n" + device.getAddress());
                    devicesArrayAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    private boolean checkAndRequestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION};
        } else {
            permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), REQUEST_PERMISSIONS_CODE);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                activateBluetooth();
            } else {
                Toast.makeText(this, "Permissions are required for Bluetooth", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                scanLeDevice(true);
            } else {
                Toast.makeText(this, "Bluetooth activation cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
            Toast.makeText(this, "Connecting to " + device.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectDevice() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        }
        dataPanel.setVisibility(LinearLayout.GONE);
        updateConnectionStatus("Disconnected", false);
        //clearDataDisplay();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                updateConnectionStatus("Connected", true);
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    {
                        gatt.discoverServices();
                        String result = null;
                        Log.d("BLE", "discoverServices() called, result: " + result);
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                updateConnectionStatus("Disconnected", false);
                dataPanel.setVisibility(LinearLayout.GONE);
                //clearDataDisplay();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            //Log.d(TAG, "onServicesDiscovered(): status=" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    runOnUiThread(() -> dataPanel.setVisibility(LinearLayout.VISIBLE));

                    // Read LN Feature (Mandatory)
                    readCharacteristic(gatt, service, LN_FEATURE_UUID);

                    // Subscribe to Location and Speed (Mandatory - Indicate)
                    subscribeToNotifications(gatt, service, LOCATION_SPEED_UUID);

                    // Subscribe to Position Quality (Optional - Notify)
                    subscribeToNotifications(gatt, service, POSITION_QUALITY_UUID);

                    // Subscribe to Navigation (Optional - Notify)
                    subscribeToNotifications(gatt, service, NAVIGATION_UUID);
                } else {
                    runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Location and Navigation Service not found", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                if (data != null) {
                    if (characteristic.getUuid().equals(LN_FEATURE_UUID)) {
                        String featureInfo = parseFeatures(data);
                        runOnUiThread(() -> tvLnFeature.setText("LN Features:\n" + featureInfo));
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                if (characteristic.getUuid().equals(LOCATION_SPEED_UUID)) {
                    String locationData = parseLocationAndSpeed(data);
                    runOnUiThread(() -> tvLocationSpeed.setText("Location & Speed:\n" + locationData));
                } else if (characteristic.getUuid().equals(POSITION_QUALITY_UUID)) {
                    String qualityData = parsePositionQuality(data);
                    runOnUiThread(() -> tvPositionQuality.setText("Position Quality:\n" + qualityData));
                } else if (characteristic.getUuid().equals(NAVIGATION_UUID)) {
                    String navData = parseNavigation(data);
                    runOnUiThread(() -> tvNavigation.setText("Navigation:\n" + navData));
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Subscription successful
                Log.d("BLE", "Descriptor written: " + descriptor.getUuid() + " status=" + status);
            }
        }
    };

    private void readCharacteristic(BluetoothGatt gatt, BluetoothGattService service, UUID characteristicUUID) {
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
        if (characteristic != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                gatt.readCharacteristic(characteristic);
            }
        }
    }

    private void subscribeToNotifications(BluetoothGatt gatt, BluetoothGattService service, UUID characteristicUUID) {
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
        if (characteristic != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                gatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
                if (descriptor != null) {
                    if (characteristicUUID.equals(LOCATION_SPEED_UUID)) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    } else {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    }
                    //descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
            }
        }
    }

    private String parseLocationAndSpeed(byte[] data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Latitude: ").append(String.format("%.6f", readDouble(data, 1))).append("°\n");
        sb.append("Longitude: ").append(String.format("%.6f", readDouble(data, 9))).append("°\n");
        sb.append("Speed: ").append(String.format("%.2f", readUint16(data, 17))).append(" m/s");
        return sb.toString();
    }

    private String parsePositionQuality(byte[] data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Number of Satellites: ").append(data[1] & 0xFF).append("\n");
        sb.append("DOP (Dilution of Precision): ").append(String.format("%.2f", readUint16(data, 2) / 100.0));
        return sb.toString();
    }

    private String parseNavigation(byte[] data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bearing: ").append(String.format("%.1f", readUint16(data, 1) / 100.0)).append("°\n");
        sb.append("Distance: ").append(String.format("%.1f", readUint32(data, 3))).append(" m");
        return sb.toString();
    }

    private String parseFeatures(byte[] data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Instantaneous Speed: ").append((data[0] & 0x01) != 0 ? "Yes" : "No").append("\n");
        sb.append("Total Distance: ").append((data[0] & 0x02) != 0 ? "Yes" : "No").append("\n");
        sb.append("Location: ").append((data[0] & 0x04) != 0 ? "Yes" : "No").append("\n");
        sb.append("Elevation: ").append((data[0] & 0x08) != 0 ? "Yes" : "No");
        return sb.toString();
    }

    private double readDouble(byte[] data, int offset) {
        long bits = readInt64(data, offset);
        return Double.longBitsToDouble(bits);
    }

    private long readInt64(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF) |
                (((long) data[offset + 1] & 0xFF) << 8) |
                (((long) data[offset + 2] & 0xFF) << 16) |
                (((long) data[offset + 3] & 0xFF) << 24) |
                (((long) data[offset + 4] & 0xFF) << 32) |
                (((long) data[offset + 5] & 0xFF) << 40) |
                (((long) data[offset + 6] & 0xFF) << 48) |
                (((long) data[offset + 7] & 0xFF) << 56);
    }

    private int readUint16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8));
    }

    private long readUint32(byte[] data, int offset) {
        return ((long)(data[offset] & 0xFF) |
                ((long)(data[offset + 1] & 0xFF) << 8) |
                ((long)(data[offset + 2] & 0xFF) << 16) |
                ((long)(data[offset + 3] & 0xFF) << 24));
    }

    private void updateConnectionStatus(String status, boolean isConnected) {
        runOnUiThread(() -> {
            tvConnectionStatus.setText("Status: " + status);
            tvConnectionStatus.setTextColor(isConnected ? 0xFF00AA00 : 0xFFAA0000);
            btnStartScan.setEnabled(!isConnected);
            btnDisconnect.setEnabled(isConnected);
        });
    }

    /*private void clearDataDisplay() {
        tvLnFeature.setText("LN Features: —");
        tvLocationSpeed.setText("Location & Speed: —");
        tvPositionQuality.setText("Position Quality: —");
        tvNavigation.setText("Navigation: —");
    }*/

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        }
    }
}