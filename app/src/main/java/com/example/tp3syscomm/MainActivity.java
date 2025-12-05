package com.example.tp3syscomm;

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
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

public class MainActivity extends AppCompatActivity {
    //etape 9
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS_CODE = 2;
    private BluetoothAdapter bluetoothAdapter;
    private ListView devicesListView;
    private ArrayAdapter<String> devicesArrayAdapter;
    private ArrayList<String> deviceList;
    private static final long SCAN_PERIOD = 10000; // Recherche pendant 10 secondes

    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning;
    private Handler handler = new Handler();
    private ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private BluetoothGatt bluetoothGatt;

    // --- Étape 4: Remplacez ces UUID par ceux de votre capteur ---
    private static final UUID SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"); // Exemple: Heart Rate Service
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"); // Exemple: Heart Rate Measurement
// UUID pour le Client Characteristic Configuration Descriptor (CCCD)
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        //etape 10
        final BluetoothManager bluetoothManager = (BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        devicesListView = findViewById(R.id.devices_list);
        deviceList = new ArrayList<>();
        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        devicesListView.setAdapter(devicesArrayAdapter);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.devices_list), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            if (scanning) {
                scanLeDevice(false); // Arrêter la recherche avant de connecter
            }
            BluetoothDevice device = discoveredDevices.get(position);
            connectToDevice(device);
        });

        findViewById(R.id.btn_activate_bt).setOnClickListener(v -> {
            activateBluetooth();
        });
    }

    //etape 11

    private void activateBluetooth() {
        if (!checkAndRequestPermissions()) {
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            startActivityForResult (enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            // Étape 2: Le Bluetooth est déjà activé, on lance la recherche
            scanLeDevice(true);
        }
    }

    // Méthode pour démarrer/arrêter la recherche BLE
    private void scanLeDevice(final boolean enable) {
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        if (enable) {
            // Arrête la recherche après une période définie (SCAN_PERIOD)
            handler.postDelayed(() -> {
                scanning = false;
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner.stopScan(leScanCallback);
                    // Fin de la recherche
                }
            }, SCAN_PERIOD);

            scanning = true;
            discoveredDevices.clear();
            //listAdapter.clear(); // Vider la liste avant de commencer une nouvelle recherche
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.startScan(leScanCallback);
                // Recherche en cours
            }
        } else {
            scanning = false;
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        }
    }

    // Callback pour recevoir les résultats de la recherche
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (ActivityCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                String deviceName = device.getName();
                if (deviceName != null && !discoveredDevices.contains(device)) {
                    // Étape 2: Affichage des appareils dans la liste
                    discoveredDevices.add(device);
                    devicesArrayAdapter.add(deviceName + "\n" + device.getAddress());
                    devicesArrayAdapter.notifyDataSetChanged();
                }
            }
        }
    };


    private boolean checkAndRequestPermissions() {
        String[] permissions;
        // Détermine les permissions nécessaires en fonction de la version d'Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION};
        } else {
            permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new
                    String[0]), REQUEST_PERMISSIONS_CODE);
            return false; // Les permissions ne sont pas encore accordées
        }

        return true; // Toutes les permissions nécessaires sont accordées
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
                //Permissions accordées
                // Maintenant que les permissions sont accordées, on relance le processus d'activation
                activateBluetooth();
            } else {
                //Les permissions sont nécessaires pour utiliser le Bluetooth
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth a été activé", Toast.LENGTH_SHORT).show();
                // Étape 2: Lancer la recherche après l'activation du Bluetooth
                scanLeDevice(true);
            } else {
                Toast.makeText(this, "L'activation du Bluetooth a été annulée", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Étape 3: Méthode pour se connecter à un appareil GATT
    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            // Connexion à l’appareil choisi
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        }
    }

    // Étape 3: Callback pour les événements GATT
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                //Connecté au serveur GATT
                // Étape 4: Une fois connecté, découvrir les services
                if (ActivityCompat.checkSelfPermission(MainActivity.this,Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                {
                    gatt.discoverServices();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //Déconnecté du serveur GATT
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                            return;
                        // Étape 4: S'abonner aux notifications
                        gatt.setCharacteristicNotification(characteristic, true);
                        BluetoothGattDescriptor descriptor =
                                characteristic.getDescriptor(CCCD_UUID);

                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    } else {
                        // Caractéristique non trouvée
                    }
                } else {
                    // Service non trouvé
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor
                descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
// Abonnement réussi
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data) {
                    stringBuilder.append(String.format("%02X ", byteChar));
                }
                final String hex = stringBuilder.toString();
                runOnUiThread(() -> {
                    TextView tvData = findViewById(R.id.tv_data);
                    //tvData.setText(hex); // ou parser selon ton capteur (HeartRate -> bpm)
                    tvData.setText("Nouvelles données : " + hex);

                });
            }
        }


    };

    // Étape 3: Nettoyer les ressources
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        }
    }
}