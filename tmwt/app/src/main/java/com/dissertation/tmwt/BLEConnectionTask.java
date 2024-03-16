package com.dissertation.tmwt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class BLEConnectionTask implements Runnable {
    private static final int REQUEST_BLUETOOTH_CONNECT_PERMISSION = 1;
    private BluetoothDevice device;
    private Context context;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService service;
    private String sensorDataString;

    private String fileName;

    public BLEConnectionTask(Context context, BluetoothDevice device, String fileName) {
        this.context = context;
        this.device = device;
        this.fileName = fileName;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            connectToDevice(device);

            if (Thread.currentThread().isInterrupted()) {
                break; // Exit the loop, leading to task termination
            }
        }
        disconnect();
    }

    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    UUID serviceUUID = UUID.fromString("00000000-000e-11e1-9ab4-0002a5d5c51b");
                    service = gatt.getService(serviceUUID);
                    if (service != null) {
                        // Iterate over each characteristic in the service and print out information
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            System.out.println("Characteristic UUID: " + characteristic.getUuid());
                            // Perform operations like read or notify on the characteristic...

                            // Example: Reading the characteristic value
                            boolean readOperationSuccess = gatt.readCharacteristic(characteristic);
                            System.out.println("Read operation initiated: " + readOperationSuccess);

                        }
                    } else {
                        System.out.println("Service not found");
                    }
                }
            }
        });
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName + ".txt");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);

        Uri uri = context.getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            outputStream.write(sensorDataString.getBytes());
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
