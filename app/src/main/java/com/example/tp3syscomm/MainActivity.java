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
                    handler.postDelayed(() -> {
                    subscribeToNotifications(gatt, service, LOCATION_SPEED_UUID);
                    }, 500);

                    handler.postDelayed(() -> {
                    readCharacteristic(gatt, service, POSITION_QUALITY_UUID);
                    }, 1000);

                    // Subscribe to Navigation (Optional - Notify)
                    handler.postDelayed(() -> {
                    subscribeToNotifications(gatt, service, NAVIGATION_UUID);
                    }, 1500);

                    handler.postDelayed(() -> {
                    subscribeToNotifications(gatt, service, LN_CONTROL_POINT_UUID);
                    }, 2000);

                } else {
                    runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Location and Navigation Service not found", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            UUID charUUID = characteristic.getUuid();
            Log.d("BLE", "onCharacteristicRead - UUID: " + charUUID + ", Status: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    Log.d("BLE", "Data received - Length: " + data.length + ", Hex: " + bytesToHex(data));

                    if (charUUID.equals(LN_FEATURE_UUID)) {
                        String featureInfo = parseFeatures(data);
                        Log.d("BLE", "Parsed LN Features: " + featureInfo);
                        runOnUiThread(() -> {
                            tvLnFeature.setText("LN Features:\n" + featureInfo);
                            Toast.makeText(MainActivity.this, "LN Features read", Toast.LENGTH_SHORT).show();
                        });

                    } else if (charUUID.equals(POSITION_QUALITY_UUID)) {
                        String qualityData = parsePositionQuality(data);
                        Log.d("BLE", "Parsed Position Quality: " + qualityData);
                        runOnUiThread(() -> {
                            tvPositionQuality.setText("Position Quality:\n" + qualityData);
                            Toast.makeText(MainActivity.this, "Position Quality read", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    Log.e("BLE", "Received empty data for: " + charUUID);
                }
            } else {
                Log.e("BLE", "Read failed for " + charUUID + " with status: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            UUID charUUID = characteristic.getUuid();

            Log.d("BLE", "onCharacteristicChanged - UUID: " + charUUID);

            if (data != null && data.length > 0) {
                Log.d("BLE", "Data received - Length: " + data.length + ", Hex: " + bytesToHex(data));

                if (charUUID.equals(LOCATION_SPEED_UUID)) {
                    String locationData = parseLocationAndSpeed(data);
                    Log.d("BLE", "Parsed Location & Speed: " + locationData);
                    runOnUiThread(() -> {
                        tvLocationSpeed.setText("Location & Speed:\n" + locationData);
                        Toast.makeText(MainActivity.this, "Location updated", Toast.LENGTH_SHORT).show();
                    });

                } else if (charUUID.equals(POSITION_QUALITY_UUID)) {
                    String qualityData = parsePositionQuality(data);
                    Log.d("BLE", "Parsed Position Quality: " + qualityData);
                    runOnUiThread(() -> {
                        tvPositionQuality.setText("Position Quality:\n" + qualityData);
                        Toast.makeText(MainActivity.this, "Quality updated", Toast.LENGTH_SHORT).show();
                    });

                } else if (charUUID.equals(NAVIGATION_UUID)) {
                    String navData = parseNavigation(data);
                    Log.d("BLE", "Parsed Navigation: " + navData);
                    runOnUiThread(() -> {
                        tvNavigation.setText("Navigation:\n" + navData);
                        Toast.makeText(MainActivity.this, "Navigation updated", Toast.LENGTH_SHORT).show();
                    });

                } else if (charUUID.equals(LN_CONTROL_POINT_UUID)) {
                    Log.d("BLE", "LN Control Point data received (indication response)");
                } else {
                    Log.w("BLE", "Unknown characteristic changed: " + charUUID);
                }
            } else {
                Log.e("BLE", "Received empty data for: " + charUUID);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            UUID charUUID = descriptor.getCharacteristic().getUuid();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "✓ CCCD Write SUCCESS for: " + charUUID);
                Log.d("BLE", "  Descriptor UUID: " + descriptor.getUuid());
                Log.d("BLE", "  Value: " + java.util.Arrays.toString(descriptor.getValue()));

                // Optional: Subscribe to next characteristic if needed
                if (charUUID.equals(LOCATION_SPEED_UUID)) {
                    Log.d("BLE", "Location & Speed is now subscribed!");
                } else if (charUUID.equals(NAVIGATION_UUID)) {
                    Log.d("BLE", "Navigation is now subscribed!");
                } else if (charUUID.equals(LN_CONTROL_POINT_UUID)) {
                    Log.d("BLE", "LN Control Point is now subscribed!");
                }
            } else {
                Log.e("BLE", "✗ CCCD Write FAILED for: " + charUUID);
                Log.e("BLE", "  Status code: " + status);
                Log.e("BLE", "  GATT_SUCCESS=0, GATT_READ_NOT_PERMITTED=2, GATT_WRITE_NOT_PERMITTED=3, GATT_INSUFFICIENT_AUTHENTICATION=5");
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
        if (gatt == null || service == null) {
            Log.e("BLE", "gatt or service is null");
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
        if (characteristic == null) {
            Log.e("BLE", "Characteristic not found: " + characteristicUUID);
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BLE", "Missing BLUETOOTH_CONNECT permission");
            return;
        }

        try {
            // Step 1: Enable local notifications
            boolean notificationEnabled = gatt.setCharacteristicNotification(characteristic, true);
            Log.d("BLE", "setCharacteristicNotification(" + characteristicUUID + "): " + notificationEnabled);

            // Step 2: Get CCCD descriptor
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (descriptor == null) {
                Log.e("BLE", "CCCD descriptor not found for: " + characteristicUUID);
                return;
            }

            // Step 3: Determine notification or indication
            byte[] descriptorValue;
            int properties = characteristic.getProperties();

            if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                descriptorValue = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                Log.d("BLE", "Using INDICATION for: " + characteristicUUID);
            } else if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                descriptorValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                Log.d("BLE", "Using NOTIFICATION for: " + characteristicUUID);
            } else {
                Log.e("BLE", "Characteristic doesn't support notify/indicate: " + characteristicUUID);
                return;
            }

            // Step 4: Write descriptor
            descriptor.setValue(descriptorValue);
            boolean writeSuccess = gatt.writeDescriptor(descriptor);
            Log.d("BLE", "writeDescriptor(" + characteristicUUID + "): " + writeSuccess);

            if (!writeSuccess) {
                Log.e("BLE", "Failed to queue descriptor write for: " + characteristicUUID);
            }

        } catch (Exception e) {
            Log.e("BLE", "Exception in subscribeToNotifications: " + e.getMessage());
        }
    }


    private String parseLocationAndSpeed(byte[] data) {
        Log.d("BLE", "parseLocationAndSpeed - Raw data length: " + data.length + ", hex: " + bytesToHex(data));

        if (data.length < 19) {
            Log.e("BLE", "Location & Speed data too short: " + data.length + " bytes");
            return "Invalid data length";
        }

        StringBuilder sb = new StringBuilder();

        try {
            byte flags = data[0];
            Log.d("BLE", "Flags: 0x" + String.format("%02X", flags));

            // Check if Location is present (bit 0)
            if ((flags & 0x01) != 0) {
                // Latitude at bytes 1-8 (double, little-endian)
                double latitude = readDouble(data, 1);
                Log.d("BLE", "Latitude: " + latitude);
                sb.append("Latitude: ").append(String.format("%.6f", latitude)).append("°\n");

                // Longitude at bytes 9-16 (double, little-endian)
                double longitude = readDouble(data, 9);
                Log.d("BLE", "Longitude: " + longitude);
                sb.append("Longitude: ").append(String.format("%.6f", longitude)).append("°\n");
            } else {
                sb.append("Latitude: N/A\n");
                sb.append("Longitude: N/A\n");
            }

            // Check if Speed is present (bit 1)
            if ((flags & 0x02) != 0) {
                // Speed at bytes 17-18 (uint16, little-endian)
                int speed = readUint16(data, 17);
                Log.d("BLE", "Speed: " + speed);
                sb.append("Speed: ").append(String.format("%.2f", speed / 100.0)).append(" m/s");
            } else {
                // Try reading speed anyway (might be present without flag)
                if (data.length >= 19) {
                    int speed = readUint16(data, 17);
                    Log.d("BLE", "Speed (no flag): " + speed);
                    sb.append("Speed: ").append(String.format("%.2f", speed / 100.0)).append(" m/s");
                }
            }

        } catch (Exception e) {
            Log.e("BLE", "Error parsing Location & Speed: " + e.getMessage());
            return "Parse error: " + e.getMessage();
        }

        return sb.toString();
    }

    private String parsePositionQuality(byte[] data) {
        Log.d("BLE", "parsePositionQuality - Raw data length: " + data.length + ", hex: " + bytesToHex(data));

        if (data.length < 4) {
            Log.e("BLE", "Position Quality data too short: " + data.length + " bytes");
            return "Invalid data length";
        }

        StringBuilder sb = new StringBuilder();

        try {
            byte flags = data[0];
            Log.d("BLE", "Flags: 0x" + String.format("%02X", flags));

            // Number of satellites at byte 1
            int satellites = data[1] & 0xFF;
            Log.d("BLE", "Satellites: " + satellites);
            sb.append("Satellites: ").append(satellites).append("\n");

            // DOP at bytes 2-3 (uint16, little-endian, divided by 100)
            int dopRaw = readUint16(data, 2);
            double dop = dopRaw / 100.0;
            Log.d("BLE", "DOP (raw): " + dopRaw + ", (calculated): " + dop);
            sb.append("DOP: ").append(String.format("%.2f", dop));

        } catch (Exception e) {
            Log.e("BLE", "Error parsing Position Quality: " + e.getMessage());
            return "Parse error: " + e.getMessage();
        }

        return sb.toString();
    }

    private String parseNavigation(byte[] data) {
        Log.d("BLE", "parseNavigation - Raw data length: " + data.length + ", hex: " + bytesToHex(data));

        if (data.length < 7) {
            Log.e("BLE", "Navigation data too short: " + data.length + " bytes");
            return "Invalid data length";
        }

        StringBuilder sb = new StringBuilder();

        try {
            byte flags = data[0];
            Log.d("BLE", "Flags: 0x" + String.format("%02X", flags));

            // Bearing at bytes 1-2 (uint16, little-endian, divided by 100)
            int bearingRaw = readUint16(data, 1);
            double bearing = bearingRaw / 100.0;
            Log.d("BLE", "Bearing (raw): " + bearingRaw + ", (calculated): " + bearing);
            sb.append("Bearing: ").append(String.format("%.1f", bearing)).append("°\n");

            // Distance at bytes 3-6 (uint32, little-endian)
            long distance = readUint32(data, 3);
            Log.d("BLE", "Distance: " + distance);
            sb.append("Distance: ").append(distance).append(" m");

        } catch (Exception e) {
            Log.e("BLE", "Error parsing Navigation: " + e.getMessage());
            return "Parse error: " + e.getMessage();
        }

        return sb.toString();
    }

    private String parseFeatures(byte[] data) {
        Log.d("BLE", "parseFeatures - Raw data length: " + data.length + ", hex: " + bytesToHex(data));

        if (data.length < 4) {
            Log.e("BLE", "Features data too short: " + data.length + " bytes");
            return "Invalid data length";
        }

        StringBuilder sb = new StringBuilder();

        try {
            // Each feature is a bit in different bytes
            boolean instantSpeed = (data[0] & 0x01) != 0;
            boolean totalDistance = (data[0] & 0x02) != 0;
            boolean location = (data[0] & 0x04) != 0;
            boolean elevation = (data[0] & 0x08) != 0;

            Log.d("BLE", "Features - Speed:" + instantSpeed + " Distance:" + totalDistance + " Location:" + location + " Elevation:" + elevation);

            sb.append("Instantaneous Speed: ").append(instantSpeed ? "Yes" : "No").append("\n");
            sb.append("Total Distance: ").append(totalDistance ? "Yes" : "No").append("\n");
            sb.append("Location: ").append(location ? "Yes" : "No").append("\n");
            sb.append("Elevation: ").append(elevation ? "Yes" : "No");

        } catch (Exception e) {
            Log.e("BLE", "Error parsing Features: " + e.getMessage());
            return "Parse error: " + e.getMessage();
        }

        return sb.toString();
    }

    // Helper function to convert bytes to hex for logging
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
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