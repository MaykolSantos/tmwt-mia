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

import com.st.blue_sdk.board_catalog.models.BoardFirmware;
import com.st.blue_sdk.bt.advertise.BleAdvertiseInfo;
import com.st.blue_sdk.features.Feature;
import com.st.blue_sdk.features.FeatureUpdate;
import com.st.blue_sdk.features.acceleration.Acceleration;
import com.st.blue_sdk.features.acceleration.AccelerationInfo;
import com.st.blue_sdk.features.gyroscope.Gyroscope;
import com.st.blue_sdk.features.gyroscope.GyroscopeInfo;
import com.st.blue_sdk.features.magnetometer.Magnetometer;
import com.st.blue_sdk.features.magnetometer.MagnetometerInfo;
import com.st.blue_sdk.features.temperature.Temperature;
import com.st.blue_sdk.features.temperature.TemperatureInfo;
import com.st.blue_sdk.logger.CsvFileLogger;
import com.st.blue_sdk.models.ConnectionStatus;
import com.st.blue_sdk.models.Node;
import com.st.blue_sdk.models.RssiData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BLEConnectionTask implements Runnable {
    private static final int REQUEST_BLUETOOTH_CONNECT_PERMISSION = 1;
    private final static UUID SERVICE_UUID = UUID.fromString("00000000-0001-11e1-9ab4-0002a5d5c51b");
    private BluetoothDevice device;
    private Context context;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService service;
    private String sensorDataString;
    private final static String ACCEL_CHARACTERISTIC_NAME = "Accelerometer";
    private final static String MAGN_CHARACTERISTIC_NAME = "Magnetometer";
    private final static String TEMP_CHARACTERISTIC_NAME = "Temperature";
    private final static String GYRO_CHARACTERISTIC_NAME = "Gyroscope";
    public CsvFileLogger csvFileLogger;
    public UUID characteristicUUID;
    public String characteristicName;
    private String fileName;
    private TaskMonitor taskMonitor;

    public BLEConnectionTask(Context context, BluetoothDevice device, String fileName, UUID characteristicUUID, String characteristicName, TaskMonitor taskMonitor) {
        this.context = context;
        this.device = device;
        this.fileName = fileName;
        this.characteristicUUID = characteristicUUID;
        this.characteristicName = characteristicName;
        this.taskMonitor = taskMonitor;

        // Construct the full path
        File logDirectory = new File(context.getExternalFilesDir(null) + File.separator + "10MWT" + File.separator + fileName);
        if (!logDirectory.exists()) {
            logDirectory.mkdirs(); // Ensure the directory structure exists
        }

        Log.d("Path", logDirectory.getAbsolutePath());

        // Initialize CsvFileLogger with the full path
        this.csvFileLogger = new CsvFileLogger(logDirectory.getAbsolutePath());
        // Assuming there's a method to enable logging
        this.csvFileLogger.setEnabled(true);

    }

    @Override
    public void run() {
        try {
            taskMonitor.incrementTasks();
            connectToDevice(device);

            // Example of a task doing periodic work
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    break; // Exit the loop
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        } finally {
            taskMonitor.decrementTasks();
            disconnect(); // Ensure resources are cleaned up properly
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Connected to the GATT server on the device
//                    Log.d("BLE", "Connected to GATT server.");
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Disconnected from the GATT server
//                    Log.d("BLE", "Disconnected from GATT server.");
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
//                    Log.d("BLE", "MTU changed to " + mtu);
                } else {
//                    Log.e("BLE", "MTU change failed with status: " + status);
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
//                    Log.d("BLE", "Services discovered.");
                    subscribeToCharacteristic(gatt);
                } else {
//                    Log.d("BLE", "Service discovery failed, status: " + status);
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
//                    Log.d("BLE", "Successfully subscribed to notifications for " + descriptor.getCharacteristic().getUuid().toString());
                } else {
//                    Log.d("BLE", "Failed to subscribe to notifications, status: " + status);
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                UUID currentCharacteristicUUID = characteristic.getUuid();
                if (characteristicUUID.equals(currentCharacteristicUUID)) {
                    byte[] data = characteristic.getValue();

                    // Retrieve the device address or name from the gatt instance
                    String deviceAddress = gatt.getDevice().getAddress();

                    // Assume timestamp is now; in real applications, this might come from the device or be more accurately tracked
                    long timestamp = System.currentTimeMillis();

                    if (characteristicName.equals("Accelerometer")) {
                        getAccelerationData(data, deviceAddress, timestamp);
                    } else if (characteristicName.equals("Magnetometer")) {
                        getMagnetometerData(data, deviceAddress, timestamp);
                    } else if (characteristicName.equals("Temperature")) {
                        getTemperatureData(data, deviceAddress, timestamp);
                    } else if (characteristicName.equals("Gyroscope")) {
                        getGyroscopeData(data, deviceAddress, timestamp);
                    } else {
                        // Optionally handle unknown characteristics or log an error
                        System.out.println("Unknown characteristic: " + characteristicName);
                    }
                }
            }
        });
    }

    private void subscribeToCharacteristic(BluetoothGatt gatt) {
        BluetoothGattService service = gatt.getService(SERVICE_UUID);

        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);

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
//                            Log.d("BLE", "Failed to set descriptor value");
                        }
                        gatt.requestMtu(158); // Request to change the MTU size
                    } else {
//                        Log.d("BLE", "Descriptor for enabling notifications not found");
                    }
                } else {
                    // The characteristic does not support notifications
//                    Log.d("BLE", "Characteristic does not support notifications");
                }
            } else {
//                Log.d("BLE", "Characteristic not found");
            }
        } else {
//            Log.d("BLE", "Service not found");
        }
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
    }

    private void writeToCsv(String data, String sensorType) {
        File logDirectory = new File(context.getExternalFilesDir(null) + File.separator + "10MWT" + File.separator + fileName);
        if (!logDirectory.exists()) {
            boolean isDirectoryCreated = logDirectory.mkdirs();
//            Log.d("BLE", "Directory creation " + (isDirectoryCreated ? "successful" : "failed") + " at " + logDirectory.getAbsolutePath());
        } else {
//            Log.d("BLE", "Directory already exists.");
        }

        File csvFile = new File(logDirectory, device.getName().toString().trim() + "_" + sensorType + ".csv");
        if (!csvFile.exists()) {
            try {
                boolean isFileCreated = csvFile.createNewFile();
//                Log.d("BLE", "File creation " + (isFileCreated ? "successful" : "failed") + " at " + csvFile.getAbsolutePath());
            } catch (IOException e) {
//                Log.e("BLE", "Error creating CSV file", e);
            }
        } else {
//            Log.d("BLE", "File already exists.");
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvFile, true))) {
            bw.write(data);
            bw.newLine();
        } catch (IOException e) {
//            Log.e("BLE", "Error writing to CSV file", e);
        }
    }

    private void getGyroscopeData(byte[] rawData, String deviceAddress, long timestamp) {
        Gyroscope gyroscopeFeature = new Gyroscope(GYRO_CHARACTERISTIC_NAME, Feature.Type.STANDARD, true, 1);
        FeatureUpdate<GyroscopeInfo> update = gyroscopeFeature.extractData(timestamp, rawData, 2);

        GyroscopeInfo gyroscopeInfo = update.getData();
        String csvData = String.format(Locale.getDefault(), "%d, %f, %f, %f", timestamp, gyroscopeInfo.getX().getValue(), gyroscopeInfo.getY().getValue(), gyroscopeInfo.getZ().getValue());

        writeToCsv(csvData, characteristicName);

//        Log.i("TAG", "Gyroscope data: " + csvData);
//        Log.d("BLE", "Device: " + deviceAddress + " Gyroscope X: " + gyroscopeInfo.getX().getValue() + " " + gyroscopeInfo.getX().getUnit());
//        Log.d("BLE", "Device: " + deviceAddress + " Gyroscope Y: " + gyroscopeInfo.getY().getValue() + " " + gyroscopeInfo.getY().getUnit());
//        Log.d("BLE", "Device: " + deviceAddress + " Gyroscope Z: " + gyroscopeInfo.getZ().getValue() + " " + gyroscopeInfo.getZ().getUnit());
    }

    private void getTemperatureData(byte[] rawData, String deviceAddress, long timestamp) {
        Temperature temperatureFeature = new Temperature(TEMP_CHARACTERISTIC_NAME, Feature.Type.STANDARD, true, 1);
        FeatureUpdate<TemperatureInfo> update = temperatureFeature.extractData(timestamp, rawData, 8);

        TemperatureInfo temperatureInfo = update.getData();
        String csvData = String.format(Locale.getDefault(), "%d, %f", timestamp, temperatureInfo.getTemperature().getValue());

        writeToCsv(csvData, characteristicName);

//        Log.i("TAG", "Temperature data: " + csvData);
//        Log.d("BLE", "Device: " + deviceAddress + " Temperature: " + temperatureInfo.getTemperature().getValue() + " " + temperatureInfo.getTemperature().getUnit());
    }

    private void getMagnetometerData(byte[] rawData, String deviceAddress, long timestamp) {
        Magnetometer magnetometerFeature = new Magnetometer(MAGN_CHARACTERISTIC_NAME, Feature.Type.STANDARD, true, 1);
        FeatureUpdate<MagnetometerInfo> update = magnetometerFeature.extractData(timestamp, rawData, 2);

        MagnetometerInfo magnetometerInfo = update.getData();
        String csvData = String.format(Locale.getDefault(), "%d, %f, %f, %f", timestamp, magnetometerInfo.getX().getValue(), magnetometerInfo.getY().getValue(), magnetometerInfo.getZ().getValue());

        writeToCsv(csvData, characteristicName);

//        Log.i("TAG", "Magnetometer data: " + csvData);
//        Log.d("BLE", "Device: " + deviceAddress + " Magnetometer X: " + magnetometerInfo.getX().getValue() + " " + magnetometerInfo.getX().getUnit());
//        Log.d("BLE", "Device: " + deviceAddress + " Magnetometer Y: " + magnetometerInfo.getY().getValue() + " " + magnetometerInfo.getY().getUnit());
//        Log.d("BLE", "Device: " + deviceAddress + " Magnetometer Z: " + magnetometerInfo.getZ().getValue() + " " + magnetometerInfo.getZ().getUnit());
    }

    private void getAccelerationData(byte[] rawData, String deviceAddress, long timestamp) {
        // Process the data through the Acceleration feature
        Acceleration accelerationFeature = new Acceleration(ACCEL_CHARACTERISTIC_NAME, Feature.Type.STANDARD, true, 1);

        FeatureUpdate<AccelerationInfo> update = accelerationFeature.extractData(timestamp, rawData, 2);

        // Log the interpreted acceleration values with device address
        AccelerationInfo accelerationInfo = update.getData();

        // Format the acceleration data as a CSV string
        String csvData = String.format(Locale.getDefault(), "%d, %f, %f, %f", timestamp, accelerationInfo.getX().getValue(), accelerationInfo.getY().getValue(), accelerationInfo.getZ().getValue());

        // Write the CSV data to file
        writeToCsv(csvData, characteristicName);

//        Log.i("TAG", "Characteristic with UUID " + characteristicUUID + " has been updated with data " + Arrays.toString(rawData));
//        Log.d("BLE", "Device: " + deviceAddress + " Acceleration X: " + accelerationInfo.getX().getValue() + " " + accelerationInfo.getX().getUnit());
//        Log.d("BLE", "Device: " + deviceAddress + " Acceleration Y: " + accelerationInfo.getY().getValue() + " " + accelerationInfo.getY().getUnit());
//        Log.d("BLE", "Device: " + deviceAddress + " Acceleration Z: " + accelerationInfo.getZ().getValue() + " " + accelerationInfo.getZ().getUnit());
    }
}
