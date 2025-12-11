package com.example.tp3syscomm;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, SensorEventListener {
    // Constants
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS_CODE = 2;
    private static final long SCAN_PERIOD = 10000;

    // Location and Navigation Service UUIDs
    private static final UUID SERVICE_UUID = UUID.fromString("00001819-0000-1000-8000-00805f9b34fb");
    private static final UUID LN_FEATURE_UUID = UUID.fromString("00002a6a-0000-1000-8000-00805f9b34fb");
    private static final UUID LOCATION_SPEED_UUID = UUID.fromString("00002a67-0000-1000-8000-00805f9b34fb");
    private static final UUID POSITION_QUALITY_UUID = UUID.fromString("00002a69-0000-1000-8000-00805f9b34fb");
    private static final UUID LN_CONTROL_POINT_UUID = UUID.fromString("00002a6b-0000-1000-8000-00805f9b34fb");
    private static final UUID NAVIGATION_UUID = UUID.fromString("00002a68-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

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
    private ImageView ivCompass;
    private TextView tvHeading;
    private FrameLayout mapContainer;
    private GoogleMap googleMap;
    private TextView tvDistance;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Handler handler = new Handler();
    private ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private boolean scanning = false;

    // Sensors
    private SensorManager sensorManager;
    private Sensor magnetometer;
    private Sensor accelerometer;
    private float[] mGravity;
    private float[] mGeomagnetic;
    private float currentBearing = 0f;
    private double currentLatitude = 0f;
    private double currentLongitude = 0f;
    private double targetLatitude;
    private double targetLongitude;

    // LN Features flags
    private boolean hasInstantSpeed = false;
    private boolean hasTotalDistance = false;
    private boolean hasLocation = false;
    private boolean hasElevation = false;
    private boolean hasHeading = false;
    private boolean hasNavigation = false;
    private boolean userIsMovingMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeBluetoothAdapter();
        initializeSensors();
        initializeUIComponents();
        setupEventListeners();
        initializeMap();

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

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGravity = new float[3];
        mGeomagnetic = new float[3];
    }

    private void initializeUIComponents() {
        devicesListView = findViewById(R.id.devices_list);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvLnFeature = findViewById(R.id.tv_ln_feature);
        tvLocationSpeed = findViewById(R.id.tv_location_speed);
        tvPositionQuality = findViewById(R.id.tv_position_quality);
        btnStartScan = findViewById(R.id.btn_activate_bt);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        dataPanel = findViewById(R.id.data_panel);
        ivCompass = findViewById(R.id.iv_compass);
        tvHeading = findViewById(R.id.tv_heading);
        mapContainer = findViewById(R.id.map_container);
        tvDistance = findViewById(R.id.tv_distance);

        // Hide all cards initially
        findViewById(R.id.map_card).setVisibility(View.GONE);
        findViewById(R.id.compass_card).setVisibility(View.GONE);

        deviceList = new ArrayList<>();
        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        devicesListView.setAdapter(devicesArrayAdapter);

        dataPanel.setVisibility(LinearLayout.GONE);
        updateConnectionStatus("Disconnected", false);
    }

    private void initializeMap() {
        SupportMapFragment mapFragment = new SupportMapFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.map_container, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.getUiSettings().setZoomControlsEnabled(true);


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);

            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (lastLocation != null) {
                LatLng myPos = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                currentLongitude = myPos.longitude;
                currentLatitude = myPos.latitude;
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myPos, 16f));
            }
        }
    }

    private void updateUIBasedOnFeatures() {
        runOnUiThread(() -> {
            // Map visibility: only if Location is supported
            if (hasLocation) {
                findViewById(R.id.map_card).setVisibility(View.VISIBLE);
                Log.d("LN_FEATURES", "Map enabled - Location supported");
            } else {
                findViewById(R.id.map_card).setVisibility(View.GONE);
                Log.d("LN_FEATURES", "Map disabled - Location not supported");
            }

            // Compass visibility: only if Heading or Navigation is supported
            if (hasHeading || hasNavigation) {
                findViewById(R.id.compass_card).setVisibility(View.VISIBLE);
                Log.d("LN_FEATURES", "Compass enabled - Heading/Navigation supported");
            } else {
                findViewById(R.id.compass_card).setVisibility(View.GONE);
                Log.d("LN_FEATURES", "Compass disabled - Heading/Navigation not supported");
            }

            // Distance visibility: only if Location is supported
            if (hasLocation && tvDistance != null) {
                tvDistance.setVisibility(View.VISIBLE);
            } else if (tvDistance != null) {
                tvDistance.setVisibility(View.GONE);
            }
        });
    }

    private float computeAutoBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1)*Math.sin(lat2) -
                Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon);

        return (float)((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    private double computeDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] result = new float[3];
        Location.distanceBetween(lat1, lon1, lat2, lon2, result);
        return result[0]; // meters
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

        // Reset features
        hasInstantSpeed = false;
        hasTotalDistance = false;
        hasLocation = false;
        hasElevation = false;
        hasHeading = false;
        hasNavigation = false;

        dataPanel.setVisibility(LinearLayout.GONE);
        findViewById(R.id.map_card).setVisibility(View.GONE);
        findViewById(R.id.compass_card).setVisibility(View.GONE);
        updateConnectionStatus("Disconnected", false);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                updateConnectionStatus("Connected", true);
                runOnUiThread(() -> {
                    findViewById(R.id.devices_card).setVisibility(View.VISIBLE);
                });

                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices();
                    Log.d("BLE", "discoverServices() called");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                updateConnectionStatus("Disconnected", false);
                dataPanel.setVisibility(LinearLayout.GONE);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    runOnUiThread(() -> dataPanel.setVisibility(LinearLayout.VISIBLE));

                    // Read LN Features first
                    readCharacteristic(gatt, service, LN_FEATURE_UUID);

                    handler.postDelayed(() -> {
                        if (hasLocation) {
                            subscribeToNotifications(gatt, service, LOCATION_SPEED_UUID);
                        }
                    }, 500);

                    handler.postDelayed(() -> {
                        readCharacteristic(gatt, service, POSITION_QUALITY_UUID);
                    }, 1000);

                    handler.postDelayed(() -> {
                        if (hasNavigation) {
                            subscribeToNotifications(gatt, service, NAVIGATION_UUID);
                        }
                    }, 1500);

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
                            updateUIBasedOnFeatures();

                            if ((hasHeading || hasNavigation) && mGravity != null && mGeomagnetic != null) {
                                float[] R = new float[9];
                                float[] I = new float[9];
                                boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
                                if (success) {
                                    float[] orientation = new float[3];
                                    SensorManager.getOrientation(R, orientation);
                                    float bearing = (float) Math.toDegrees(orientation[0]);
                                    updateCompassFromServer(bearing);
                                }
                            }
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
            } else {
                Log.e("BLE", "✗ CCCD Write FAILED for: " + charUUID);
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
            boolean notificationEnabled = gatt.setCharacteristicNotification(characteristic, true);
            Log.d("BLE", "setCharacteristicNotification(" + characteristicUUID + "): " + notificationEnabled);

            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (descriptor == null) {
                Log.e("BLE", "CCCD descriptor not found for: " + characteristicUUID);
                return;
            }

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

            descriptor.setValue(descriptorValue);
            boolean writeSuccess = gatt.writeDescriptor(descriptor);
            Log.d("BLE", "writeDescriptor(" + characteristicUUID + "): " + writeSuccess);

        } catch (Exception e) {
            Log.e("BLE", "Exception in subscribeToNotifications: " + e.getMessage());
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values.clone();
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values.clone();

        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                float bearing = (float) Math.toDegrees(orientation[0]);

                if (hasHeading || hasNavigation) {
                    updateCompassFromServer(bearing);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void updateCompassFromServer(float bearing) {
        RotateAnimation rotateAnimation = new RotateAnimation(
                currentBearing,
                -bearing,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setDuration(500);
        rotateAnimation.setFillAfter(true);
        ivCompass.startAnimation(rotateAnimation);
        currentBearing = -bearing;
        tvHeading.setText(String.format("Heading: %.1f°", bearing));
        Log.d("BLE", "Compass updated with bearing: " + bearing);
    }

    private String parseLocationAndSpeed(byte[] data) {
        Log.d("BLE", "parseLocationAndSpeed - Raw data length: " + data.length + ", hex: " + bytesToHex(data));

        StringBuilder sb = new StringBuilder();

        try {
            byte flags = data[0];
            Log.d("BLE", "Flags: 0x" + String.format("%02X", flags));

            double latitude = 0;
            double longitude = 0;

            if ((flags & 0x01) != 0 && data.length >= 17) {
                latitude = readDouble(data, 1);
                longitude = readDouble(data, 9);
                Log.d("BLE", "✓ Location found - Latitude: " + latitude + ", Longitude: " + longitude);
            } else {
                latitude = 48.8566;
                longitude = 2.3522;
                Log.w("BLE", "Location flag not set, using default coordinates");
            }

            targetLatitude = (float) latitude;
            targetLongitude = (float) longitude;

            double distanceToTarget = computeDistance(
                    currentLatitude,
                    currentLongitude,
                    targetLatitude,
                    targetLongitude
            );
            Log.d("BLE", "Distance to target: " + distanceToTarget + " m");

            runOnUiThread(() -> tvDistance.setText(
                    String.format("Distance: %.1f m", distanceToTarget)
            ));

            sb.append("Latitude: ").append(String.format("%.6f", latitude)).append("°\n");
            sb.append("Longitude: ").append(String.format("%.6f", longitude)).append("°\n");
            sb.append("Distance to target: ").append(String.format("%.1f m", distanceToTarget)).append("\n");

            if (data.length >= 19) {
                int speed = readUint16(data, 17);
                sb.append("Speed: ").append(String.format("%.2f", speed / 100.0)).append(" m/s\n");
            }

            runOnUiThread(this::updateMapWithLocation);

        } catch (Exception e) {
            Log.e("BLE", "Error parsing Location & Speed: " + e.getMessage(), e);
            return "Parse error: " + e.getMessage();
        }

        return sb.toString();
    }

    private void updateMapWithLocation() {
        Log.d("MAP", "Updating map: Lat=" + currentLatitude + ", Lon=" + currentLongitude);

        if (googleMap == null) {
            Log.e("MAP", "✗ GoogleMap is NULL!");
            return;
        }

        if (!hasLocation) {
            Log.d("MAP", "Location feature not supported, skipping map update");
            return;
        }

        if (userIsMovingMap) {
            return; // Ne pas recentrer la carte
        }

        LatLng deviceLocation = new LatLng(currentLatitude, currentLongitude);
        LatLng targetLocation = new LatLng(targetLatitude, targetLongitude);

        googleMap.clear(); // Appelez clear() UNE SEULE FOIS au début

        // Add marker for target location
        googleMap.addMarker(new MarkerOptions()
                .position(targetLocation)
                .title("Target Location")
                .snippet("Lat: " + String.format("%.6f", targetLatitude) + "\nLon: " + String.format("%.6f", targetLongitude)));

        // Draw blue line connecting current location to target
        PolylineOptions polylineOptions = new PolylineOptions()
                .add(deviceLocation)
                .add(targetLocation)
                .width(10)
                .color(Color.BLUE)
                .geodesic(true);

        googleMap.addPolyline(polylineOptions);
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 16f), 300, null);

        Log.d("MAP", "✓ Map updated successfully with line to target");
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
            int satellites = data[1] & 0xFF;
            int dopRaw = readUint16(data, 2);
            double dop = dopRaw / 100.0;
            Log.d("BLE", "Satellites: " + satellites + ", DOP: " + dop);
            sb.append("Satellites: ").append(satellites).append("\n");
            sb.append("DOP: ").append(String.format("%.2f", dop));

        } catch (Exception e) {
            Log.e("BLE", "Error parsing Position Quality: " + e.getMessage());
            return "Parse error: " + e.getMessage();
        }

        return sb.toString();
    }

    private String parseNavigation(byte[] data) {
        Log.d("BLE", "parseNavigation - Raw data length: " + data.length + ", hex: " + bytesToHex(data));

        if (data.length < 15) {
            Log.e("BLE", "Navigation data too short: " + data.length + " bytes");
            return "Invalid data length";
        }

        StringBuilder sb = new StringBuilder();

        try {
            byte flags = data[0];
            Log.d("BLE", "Navigation Flags: 0x" + String.format("%02X", flags));

            int bearingRaw = readUint16(data, 1);
            double serverBearing = bearingRaw / 100.0;

            long serverDistance = readUint32(data, 3);

            Log.d("BLE", "Server Bearing: " + serverBearing + "°");
            Log.d("BLE", "Server Distance: " + serverDistance + " m");

            float finalBearing = computeAutoBearing(
                    currentLatitude,
                    currentLongitude,
                    targetLatitude,
                    targetLongitude
            );
            Log.d("BLE", "Bearing to server: " + finalBearing + "°");

            if (hasHeading || hasNavigation) {
                runOnUiThread(() -> updateCompassFromServer(finalBearing));
            }

            sb.append("Bearing: ").append(String.format("%.2f°", finalBearing)).append("\n");

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
            hasInstantSpeed = (data[0] & 0x01) != 0;
            hasTotalDistance = (data[0] & 0x02) != 0;
            hasLocation = (data[0] & 0x04) != 0;
            hasElevation = (data[0] & 0x08) != 0;
            hasHeading = (data[0] & 0x10) != 0;

            // Navigation is bit 6 (0x40) according to spec
            hasNavigation = (data[0] & 0x40) != 0;

            Log.d("LN_FEATURES", "Speed:" + hasInstantSpeed + " Distance:" + hasTotalDistance +
                    " Location:" + hasLocation + " Elevation:" + hasElevation +
                    " Heading:" + hasHeading + " Navigation:" + hasNavigation);

            sb.append("Instantaneous Speed: ").append(hasInstantSpeed ? "Yes" : "No").append("\n");
            sb.append("Total Distance: ").append(hasTotalDistance ? "Yes" : "No").append("\n");
            sb.append("Location: ").append(hasLocation ? "Yes" : "No").append("\n");
            sb.append("Elevation: ").append(hasElevation ? "Yes" : "No").append("\n");
            sb.append("Heading: ").append(hasHeading ? "Yes" : "No").append("\n");
            sb.append("Navigation: ").append(hasNavigation ? "Yes" : "No");

        } catch (Exception e) {
            Log.e("BLE", "Error parsing Features: " + e.getMessage());
            return "Parse error: " + e.getMessage();
        }

        return sb.toString();
    }

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

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            // Toujours enregistrer les capteurs
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }


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