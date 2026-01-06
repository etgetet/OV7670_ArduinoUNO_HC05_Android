package com.example.brochetteview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.nio.ByteBuffer;
import java.util.UUID;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FrugalLogs";

    private static final String TARGET_MAC = "B8:BA:97:87:60:F5";

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";

    // TON SERVICE UART BLE
    private static final UUID SERVICE_UUID   = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");

    // RX (notifications)
    private static final UUID CHAR_UUID_RX   = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    // TX (write)
    //private static final UUID CHAR_UUID_TX   = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb");

    BluetoothAdapter bluetoothAdapter;
    BluetoothGatt bluetoothGatt;
    public static Handler handler;
    private final static int TEXT1 = 1;
    private final static int TEXT2 = 2;

    //ReadValue readValue;

    int r, g, l;


    /*int width = 0;
    int cont = 0;
    boolean i = false;
    int[] array = new int[76800];
    int[] img = new int[76800];

    int r, g, l;

    byte[] data = new byte[153600];*/


    ByteBuffer bufferObj = ByteBuffer.allocate(1534000);
    int cont = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main2), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Log.d(TAG, "begin");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    1
            );
        }

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = manager.getAdapter();

        TextView btReadings = findViewById(R.id.btReadings);
        TextView btInfo = findViewById(R.id.btDevices);
        Button searchDevices = findViewById(R.id.seachDevices);
        Button clearValues = findViewById(R.id.refresh);
        //ImageView image = findViewById(R.id.image);

        handler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what){
                    case TEXT1:
                        String arduinoMsg1 = msg.obj.toString(); // Read message from Arduino
                        btReadings.setText(arduinoMsg1);
                    /*case IMAGE:
                        Log.d(TAG, "========================COLOR========================");
                        int[] colors = (int[]) msg.obj;
                        Log.d(TAG, "image colors: " + colors.length);
                        Bitmap bitmap = Bitmap.createBitmap(colors, 320, 240, RGB_565);
                        image.setImageBitmap(bitmap);*/
                    case TEXT2:
                        String arduinoMsg2 = msg.obj.toString(); // Read message from Arduino
                        btInfo.setText(arduinoMsg2);
                }
            }
        };

        clearValues.setOnClickListener(View -> {
            btInfo.setText("");
            btReadings.setText("");
        });

        searchDevices.setOnClickListener(View -> startScan());

    }

    // ========================= SCAN BLE =========================

    private void startScan() {
        TextView btInfo = findViewById(R.id.btDevices);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            btInfo.setText("Permission BLUETOOTH_SCAN missing");
            return;
        }

        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        scanner.startScan(scanCallback);

        btInfo.setText("Scan BLE begin...");
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            TextView btInfo = findViewById(R.id.btDevices);
            BluetoothDevice device = result.getDevice();

            if (device.getAddress().equals(TARGET_MAC)) {

                btInfo.setText("Device trouvé : " + device.getName());
                bluetoothAdapter.getBluetoothLeScanner().stopScan(this);
                connectGatt(device);
            }
        }
    };

    // ========================= CONNEXION GATT =========================
    private void connectGatt(BluetoothDevice device) {
        TextView btInfo = findViewById(R.id.btDevices);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            btInfo.setText("Permission BLUETOOTH_CONNECT missing");
            return;
        }

        btInfo.setText("Connexion à GATT...");
        bluetoothGatt = device.connectGatt(this, true, gattCallback);

    }

    // ========================= CALLBACK GATT =========================
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            TextView btInfo = findViewById(R.id.btDevices);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                btInfo.setText("Connect...");
                broadcastUpdate(ACTION_GATT_CONNECTED);
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                Log.d(TAG, "disconnected from the GATT Server ");
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }

            Log.d(TAG, "status: " + status + " / newState: " + newState);
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            TextView btInfo = findViewById(R.id.btDevices);
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT)!= PackageManager.PERMISSION_GRANTED) {
                btInfo.setText("Permission BLUETOOTH_CONNECT missing !");
                return;
            }

            BluetoothGattService uartService = gatt.getService(SERVICE_UUID);

            if (uartService == null) {
                btInfo.setText("Service UART didn't fond !");
                return;
            }

            btInfo.setText("Ready à read");
            BluetoothGattCharacteristic rxChar = gatt.getService(SERVICE_UUID).getCharacteristic(CHAR_UUID_RX);
            gatt.setCharacteristicNotification(rxChar, true);
            gatt.requestMtu(246);
            //readValue = new ReadValue(handler);
            //handlerIn = readValue.sendHandler();
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            bufferObj.put(characteristic.getValue());
            cont += characteristic.getValue().length;
            Log.d(TAG, ":" + cont);

            if (cont >= 153600) {
                bufferObj.flip();
                Log.d(TAG, "--------------Image showed-------------");
                buildBitmap(bufferObj.array());
                bufferObj.clear();
                cont = 0;
            }
        }
    };

    private void buildBitmap ( byte[] data){
        int[] pixels = new int[320 * 240];

        for (int i = 0; i < pixels.length; i++) {
            //int byte1 = data[i] & 0xFF;
            //int byte2 = data[i+1] & 0xFF;
            //Log.d(TAG, "i: " + String.format("%8s", Integer.toBinaryString(data[2*i] & 0xFF)).replace(" ", "0") + "  /  i+1: " + String.format("%8s", Integer.toBinaryString(data[2*i + 1] & 0xFF)).replace(" ", "0"));

            r = (data[2*i] & 0xF8);
            g = (((data[2*i] & 0x07) << 5) | ((data[2*i + 1] & 0xE0) >> 3));
            l = ((data[2*i + 1] & 0x1F) << 3);

            pixels[i] = Color.rgb(r, g, l);
        }
        Log.d(TAG, "convert");

        Bitmap bmp = Bitmap.createBitmap(pixels, 320, 240, Bitmap.Config.RGB_565);

        runOnUiThread(() -> {
            ImageView image = findViewById(R.id.image);
            image.setImageBitmap(bmp);
        });
    }
}

/*package com.example.brochetteview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FrugalLogs";
    private static final String TARGET_MAC = "B8:BA:97:87:60:F5";
    // TON SERVICE UART BLE
    private static final UUID SERVICE_UUID   = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    BluetoothAdapter bluetoothAdapter;
    int r, g, l;
    ByteBuffer bufferObj = ByteBuffer.allocate(153601);
    int cont = 0;
    BluetoothGatt bluetoothGatt;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main2), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    1
            );
        }
        Log.d(TAG, "begin");
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = manager.getAdapter();

        TextView btReadings = findViewById(R.id.btReadings);
        TextView btInfo = findViewById(R.id.btDevices);
        Button searchDevices = findViewById(R.id.seachDevices);
        Button clearValues = findViewById(R.id.refresh);

        clearValues.setOnClickListener(View -> {
            btInfo.setText("");
            btReadings.setText("");
        });

        searchDevices.setOnClickListener(View -> {
            startScan();
        });
    }
    // ========================= SCAN BLE =========================
    private void startScan() {
        TextView btInfo = findViewById(R.id.btDevices);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission BLUETOOTH_SCAN manquante");
            return;
        }
        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        scanner.startScan(scanCallback);
        Log.d(TAG, "Scan BLE démarré...");
        btInfo.setText("Scan BLE démarré...");
    }
    private final ScanCallback scanCallback = new ScanCallback() {
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            TextView btInfo = findViewById(R.id.btDevices);
            BluetoothDevice device = result.getDevice();
            if (device.getAddress().equals(TARGET_MAC)) {
                bluetoothAdapter.getBluetoothLeScanner().stopScan(this);
                connectGatt(device);
                btInfo.setText("connected");
            }
        }
    };
    // ========================= CONNEXION GATT =========================
    private void connectGatt(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission BLUETOOTH_CONNECT manquante");
            return;
        }
        Log.d(TAG, "Connexion à GATT...");
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }
    // ========================= CALLBACK GATT =========================
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            }
        }
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            TextView btInfo = findViewById(R.id.btDevices);
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission BLUETOOTH_CONNECT manquante !");
                return;
            }
            BluetoothGattService uartService = gatt.getService(SERVICE_UUID);
            if (uartService == null) {
                Log.e(TAG, "❌ Service UART introuvable !");
                return;
            }
            Log.d(TAG, "✅ Service UART trouvé");
            btInfo.setText("Ready to read");
            bluetoothGatt.requestMtu(246);

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "rez");
            byte[] chunk = characteristic.getValue();

            bufferObj.put(chunk);
            cont += chunk.length;
            Log.d(TAG, "cont: "+cont);

            if (cont >= 153600) {
                bufferObj.flip();
                buildBitmap(bufferObj.array());
                bufferObj.clear();
                cont = 0;
            }
        }
    };

    private void buildBitmap ( byte[] data){
        int[] pixels = new int[320 * 240];

        for (int i = 0; i < pixels.length; i++) {
            //int byte1 = data[i] & 0xFF;
            //int byte2 = data[i+1] & 0xFF;
            Log.d(TAG, "i: " + String.format("%8s", Integer.toBinaryString(data[2*i] & 0xFF)).replace(" ", "0") + "  /  i+1: " + String.format("%8s", Integer.toBinaryString(data[2*i + 1] & 0xFF)).replace(" ", "0"));

            r = (data[2*i] & 0xF8);
            g = (((data[2*i] & 0x07) << 5) | ((data[2*i + 1] & 0xE0) >> 3));
            l = ((data[2*i + 1] & 0x1F) << 3);

            pixels[i] = Color.rgb(r, g, l);
        }
        Log.d(TAG, "convert");

        Bitmap bmp = Bitmap.createBitmap(pixels, 320, 240, Bitmap.Config.RGB_565);

        runOnUiThread(() -> {
            ImageView image = findViewById(R.id.image);
            image.setImageBitmap(bmp);
        });
    }

}

/*
    private static final String TAG = "FrugalLogs";

    private static final int REQUEST_ENABLE_BT = 1;
    BluetoothDevice arduinoBTModule = null;
    UUID arduinoUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //We declare a default UUID to create the global variable

    int r, g, l;
    ByteBuffer bufferObj = ByteBuffer.allocate(153601);
    int cont = 0;
    int check;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main2), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    1
            );
        }
        Log.d(TAG, "begin");
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        TextView btReadings = findViewById(R.id.btReadings);
        TextView btInfo = findViewById(R.id.btDevices);
        Button searchDevices = findViewById(R.id.seachDevices);
        Button clearValues = findViewById(R.id.refresh);
        //ImageView image = findViewById(R.id.image);

        clearValues.setOnClickListener(View -> {
            btInfo.setText("");
            btReadings.setText("");
        });

        searchDevices.setOnClickListener(View -> {
            if (bluetoothAdapter == null) {
                // Device doesn't support Bluetooth
                Log.d(TAG, "Device doesn't support Bluetooth");
            } else {
                Log.d(TAG, "Device support Bluetooth");
                //Check BT enabled. If disabled, we ask the user to enable BT
                if (!bluetoothAdapter.isEnabled()) {
                    Log.d(TAG, "Bluetooth is disabled");
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "We don't BT Permissions");
                    } else {
                        Log.d(TAG, "We have BT Permissions");
                    }
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    Log.d(TAG, "Bluetooth is enabled now");

                } else {
                    Log.d(TAG, "Bluetooth is enabled");
                }
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

                if (!pairedDevices.isEmpty()) {
                    for (BluetoothDevice device : pairedDevices) {
                        String deviceName = device.getName();
                        if (deviceName.equals("BROCHETTE VIEW")) {
                            Log.d(TAG, "HC-05 found");
                            arduinoUUID = device.getUuids()[0].getUuid();
                            arduinoBTModule = device;
                            BluetoothSocket tmp = null;
                            try {
                                tmp = device.createRfcommSocketToServiceRecord(arduinoUUID);
                            } catch (IOException e) {
                                Log.e(TAG, "Socket's create() method failed", e);
                            }
                            BluetoothSocket mmSocket = tmp;
                            try {
                                assert mmSocket != null;
                                mmSocket.connect();
                                btInfo.setText("Ready to read");
                            } catch (IOException connectException) {
                                Log.e(TAG, "connectException: " + connectException);
                                try {
                                    mmSocket.close();
                                } catch (IOException closeException) {
                                    Log.e(TAG, "Could not close the client socket", closeException);
                                }
                                return;
                            }
                            final InputStream mmInStream;
                            InputStream tmpIn = null;

                            try {
                                tmpIn = mmSocket.getInputStream();
                            } catch (IOException e) {
                                Log.e(TAG, "Error occurred when creating input stream", e);
                            }
                            mmInStream = tmpIn;
                            runOnUiThread(() -> {
                                while(mmSocket.isConnected()) {
                                    try {
                                        check = mmInStream.read();
                                        if (check != -1) {
                                            bufferObj.put((byte) check);
                                            cont++;
                                            Log.d(TAG, ":" + cont);
                                        }

                                        if (cont >= 153600) {
                                            bufferObj.flip();
                                            btInfo.setText("convert!");
                                            Log.d(TAG, "--------------Image showed-------------");
                                            buildBitmap(bufferObj.array());
                                            bufferObj.clear();
                                            cont = 0;
                                        }
                                        //}
                                    } catch (IOException e) {
                                        Log.e(TAG, "Reading error: ", e);
                                    }
                                }
                            });

                        }
                    }
                }
            }
            Log.d(TAG, "Button Pressed");
        });
    }
    private void buildBitmap ( byte[] data){
            int[] pixels = new int[320 * 240];

            for (int i = 0; i < pixels.length; i++) {
                //int byte1 = data[i] & 0xFF;
                //int byte2 = data[i+1] & 0xFF;
                Log.d(TAG, "i: " + String.format("%8s", Integer.toBinaryString(data[2*i] & 0xFF)).replace(" ", "0") + "  /  i+1: " + String.format("%8s", Integer.toBinaryString(data[2*i + 1] & 0xFF)).replace(" ", "0"));

                r = (data[2*i] & 0xF8);
                g = (((data[2*i] & 0x07) << 5) | ((data[2*i + 1] & 0xE0) >> 3));
                l = ((data[2*i + 1] & 0x1F) << 3);

                pixels[i] = Color.rgb(r, g, l);
            }
            Log.d(TAG, "convert");

            Bitmap bmp = Bitmap.createBitmap(pixels, 320, 240, Bitmap.Config.RGB_565);

            runOnUiThread(() -> {
                ImageView image = findViewById(R.id.image);
                image.setImageBitmap(bmp);
            });
        }

}*/




