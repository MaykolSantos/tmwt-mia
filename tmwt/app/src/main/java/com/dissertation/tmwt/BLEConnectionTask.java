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
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.st.blue_sdk.features.Feature;
import com.st.blue_sdk.features.FeatureUpdate;
import com.st.blue_sdk.features.acceleration.Acceleration;
import com.st.blue_sdk.features.acceleration.AccelerationInfo;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

public class BLEConnectionTask implements Runnable {
    private static final int REQUEST_BLUETOOTH_CONNECT_PERMISSION = 1;
    private BluetoothDevice device;
    private Context context;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService service;
    private String sensorDataString;
    private Acceleration accelerationFeature;

    private final static UUID SERVICE_UUID = UUID.fromString("00000000-0001-11e1-9ab4-0002a5d5c51b");
    private final static UUID CHARACTERISTIC_UUID = UUID.fromString("00800000-0001-11e1-ac36-0002a5d5c51b");


    private String fileName;

    public BLEConnectionTask(Context context, BluetoothDevice device, String fileName) {
        this.context = context;
        this.device = device;
        this.fileName = fileName;
        this.accelerationFeature = new Acceleration("Acceleration", Feature.Type.STANDARD, true, 1);

    }

    @Override
    public void run() {
        try {
            connectToDevice(device);

            // Example of a task doing periodic work
            while (!Thread.currentThread().isInterrupted()) {
                // Your repeated task logic here

                // Simulate some ongoing work
                try {
                    Thread.sleep(1000); // Pause to simulate work and provide a point to check for interruption
                } catch (InterruptedException e) {
                    // InterruptedException is thrown when the thread is interrupted during sleep or wait
                    Log.d("BLE", "Thread was interrupted, stopping task.");
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    break; // Exit the loop
                }

                // Additional work or checks can go here
            }
        } catch (Exception e) {
            Log.e("BLE", "Exception in BLEConnectionTask", e);
            // Additional exception handling as needed
        } finally {
            disconnect(); // Ensure resources are cleaned up properly
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Connected to the GATT server on the device
                    Log.d("BLE", "Connected to GATT server.");
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Disconnected from the GATT server
                    Log.d("BLE", "Disconnected from GATT server.");
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    for (BluetoothGattService service : gatt.getServices()) {
                        Log.d("BLE", "Service: " + service.getUuid().toString());

                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            Log.d("BLE", "  Characteristic: " + characteristic.getUuid().toString());
                        }
                    }
                    subscribeToCharacteristic(gatt);
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "Successfully subscribed to notifications for " + descriptor.getCharacteristic().getUuid().toString());
                } else {
                    Log.d("BLE", "Failed to subscribe to notifications, status: " + status);
                }
            }

//            @Override
//            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
//                UUID characteristicUUID = characteristic.getUuid();
//                if (CHARACTERISTIC_UUID.equals(characteristicUUID)) {
//                    byte[] data = characteristic.getValue();
//                    Log.d("BLE", "Notification received for " + characteristicUUID + ", data: " + Arrays.toString(data));
//
//                    // Optionally, convert the data into a more readable or useful format depending on your application's needs
//                    // For example, converting byte array to string, integer, etc.
//                }
//            }
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                UUID characteristicUUID = characteristic.getUuid();
                if (CHARACTERISTIC_UUID.equals(characteristicUUID)) {
                    byte[] data = characteristic.getValue();

                    // Retrieve the device address or name from the gatt instance
                    String deviceAddress = gatt.getDevice().getAddress(); // For device name, use getDevice().getName();

                    Log.d("BLE", "Notification received from device: " + deviceAddress + " for " + characteristicUUID + ", data: " + Arrays.toString(data));

                    // Assume timestamp is now; in real applications, this might come from the device or be more accurately tracked
                    long timestamp = System.currentTimeMillis();

                    // Process the data through the Acceleration feature
                    FeatureUpdate<AccelerationInfo> update = accelerationFeature.extractData(timestamp, data, 0);

                    // Log the interpreted acceleration values with device address
                    AccelerationInfo accelerationInfo = update.getData();
                    Log.d("BLE", "Device: " + deviceAddress + " Acceleration X: " + accelerationInfo.getX().getValue() + " " + accelerationInfo.getX().getUnit());
                    Log.d("BLE", "Device: " + deviceAddress + " Acceleration Y: " + accelerationInfo.getY().getValue() + " " + accelerationInfo.getY().getUnit());
                    Log.d("BLE", "Device: " + deviceAddress + " Acceleration Z: " + accelerationInfo.getZ().getValue() + " " + accelerationInfo.getZ().getUnit());
                }
            }
        });
    }

    // Helper method to convert byte array to hex string
    private final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void subscribeToCharacteristic(BluetoothGatt gatt) {
        BluetoothGattService service = gatt.getService(SERVICE_UUID);

        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);

            if (characteristic != null) {
                // Check if the characteristic supports notifications
                if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    // The characteristic supports notifications, proceed to enable them
                    gatt.setCharacteristicNotification(characteristic, true);

                    // Find the notification descriptor and write the value to enable notifications
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        boolean success = gatt.writeDescriptor(descriptor);
                        if (!success) {
                            Log.d("BLE", "Failed to set descriptor value");
                        }
                    } else {
                        Log.d("BLE", "Descriptor for enabling notifications not found");
                    }
                } else {
                    // The characteristic does not support notifications
                    Log.d("BLE", "Characteristic does not support notifications");
                }
            } else {
                Log.d("BLE", "Characteristic not found");
            }
        } else {
            Log.d("BLE", "Service not found");
        }
    }

//    private void connectToDevice(BluetoothDevice device) {
//        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
//
//            @Override
//            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//                super.onConnectionStateChange(gatt, status, newState);
//
//                if (newState == BluetoothProfile.STATE_CONNECTED) {
//                    gatt.discoverServices();
//                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
//
//                }
//            }
//
//            @Override
//            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
//                super.onServicesDiscovered(gatt, status);
//                System.out.println("GATTTT");
//                if (status == BluetoothGatt.GATT_SUCCESS) {
//                    System.out.println("GATTTT22222222222");
//                    UUID serviceUUID = UUID.fromString("00000000-0001-11e1-9ab4-0002a5d5c51b");
//                    service = gatt.getService(serviceUUID);
//                    if (service != null) {
//                        System.out.println("GATTTT333333333333333");
//                        var charact = service.getCharacteristic(UUID.fromString("00800000-0001-11e1-ac36-0002a5d5c51b"));
//                        System.out.println("Characterist value -> " + charact.getValue());
//                    } else {
//                        System.out.println("Service not found");
//                    }
//                }
//            }
//        });
//    }

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
