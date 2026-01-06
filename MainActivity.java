package com.example.brochetteview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity {

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
                            new Thread(() -> {
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
                            }).start();

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

}




