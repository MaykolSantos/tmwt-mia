package com.dissertation.tmwt;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

public class BLEConnectionTask implements Runnable {
    private final static UUID SERVICE_UUID = UUID.fromString("00000000-0001-11e1-9ab4-0002a5d5c51b");
    private BluetoothDevice device;
    private Context context;
    private BluetoothGatt bluetoothGatt;
    private int numberOfCurrentProcesses;
    private final static String ACCEL_CHARACTERISTIC_NAME = "Accelerometer";
    private final static String MAGN_CHARACTERISTIC_NAME = "Magnetometer";
    private final static String TEMP_CHARACTERISTIC_NAME = "Temperature";
    private final static String GYRO_CHARACTERISTIC_NAME = "Gyroscope";
    public UUID characteristicUUID;
    public String characteristicName;
    private String fileName;
    private TaskMonitor taskMonitor;

    public BLEConnectionTask(Context context, BluetoothDevice device, String fileName, UUID characteristicUUID, String characteristicName, TaskMonitor taskMonitor, int numberOfCurrentProcesses) {
        this.context = context;
        this.device = device;
        this.fileName = fileName;
        this.characteristicUUID = characteristicUUID;
        this.characteristicName = characteristicName;
        this.taskMonitor = taskMonitor;
        this.numberOfCurrentProcesses = numberOfCurrentProcesses;
    }

    @Override
    public void run() {
        try {
            connectToDevice(device);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        } finally {
            disconnect();
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();
//                    Log.d("BLE", "Connected to GATT server.");
                    taskMonitor.incrementTasks();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
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

                    String deviceAddress = gatt.getDevice().getAddress();
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
                if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
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
            taskMonitor.decrementTasks();
        }
    }

    private void writeToCsv(String data, String sensorType) {
        if (this.verifyProcessCount()) {
            // Define the directory path for the logs
            File logDirectory = new File(context.getExternalFilesDir(null) + File.separator + "10MWT" + File.separator + fileName);

            // Create directory if it does not exist
            if (!logDirectory.exists()) {
                boolean isDirectoryCreated = logDirectory.mkdirs();
                // Log.d("BLE", "Directory creation " + (isDirectoryCreated ? "successful" : "failed") + " at " + logDirectory.getAbsolutePath());
            } else {
                // Log.d("BLE", "Directory already exists.");
            }

            // Define the CSV file path
            File csvFile = new File(logDirectory, device.getName().toString().trim() + "_" + sensorType + ".csv");

            boolean isNewFile = false;
            if (!csvFile.exists()) {
                try {
                    isNewFile = csvFile.createNewFile();
                    // Log.d("BLE", "File creation " + (isNewFile ? "successful" : "failed") + " at " + csvFile.getAbsolutePath());
                } catch (IOException e) {
                    // Log.e("BLE", "Error creating CSV file", e);
                }
            } else {
                // Log.d("BLE", "File already exists.");
            }

            // Add header
            String header = "";
            switch (sensorType) {
                case "Accelerometer":
                case "Gyroscope":
                case "Magnetometer":
                    header = "timestamp,X,Y,Z";
                    break;
                case "Temperature":
                    header = "timestamp,temp";
                    break;
                default:
                    // Log.d("BLE", "Unknown sensor type.");
                    break;
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvFile, true))) {
                if (isNewFile && !header.isEmpty()) {
                    bw.write(header);
                    bw.newLine();
                }
                bw.write(data);
                bw.newLine();
            } catch (IOException e) {
                // Log.e("BLE", "Error writing to CSV file", e);
            }
        }
    }

    private void getGyroscopeData(byte[] rawData, String deviceAddress, long timestamp) {
        Gyroscope gyroscopeFeature = new Gyroscope(GYRO_CHARACTERISTIC_NAME, Feature.Type.STANDARD, true, 1);
        FeatureUpdate<GyroscopeInfo> update = gyroscopeFeature.extractData(timestamp, rawData, 2);

        GyroscopeInfo gyroscopeInfo = update.getData();
        String csvData = String.format(Locale.getDefault(), "%d,%f,%f,%f", timestamp, gyroscopeInfo.getX().getValue(), gyroscopeInfo.getY().getValue(), gyroscopeInfo.getZ().getValue());

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
        String csvData = String.format(Locale.getDefault(), "%d,%f", timestamp, temperatureInfo.getTemperature().getValue());

        writeToCsv(csvData, characteristicName);

//        Log.i("TAG", "Temperature data: " + csvData);
//        Log.d("BLE", "Device: " + deviceAddress + " Temperature: " + temperatureInfo.getTemperature().getValue() + " " + temperatureInfo.getTemperature().getUnit());
    }

    private void getMagnetometerData(byte[] rawData, String deviceAddress, long timestamp) {
        Magnetometer magnetometerFeature = new Magnetometer(MAGN_CHARACTERISTIC_NAME, Feature.Type.STANDARD, true, 1);
        FeatureUpdate<MagnetometerInfo> update = magnetometerFeature.extractData(timestamp, rawData, 2);

        MagnetometerInfo magnetometerInfo = update.getData();
        String csvData = String.format(Locale.getDefault(), "%d,%f,%f,%f", timestamp, magnetometerInfo.getX().getValue(), magnetometerInfo.getY().getValue(), magnetometerInfo.getZ().getValue());

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
        String csvData = String.format(Locale.getDefault(), "%d,%f,%f,%f", timestamp, accelerationInfo.getX().getValue(), accelerationInfo.getY().getValue(), accelerationInfo.getZ().getValue());

        // Write the CSV data to file
        writeToCsv(csvData, characteristicName);

//        Log.i("TAG", "Characteristic with UUID " + characteristicUUID + " has been updated with data " + Arrays.toString(rawData));
//        Log.d("BLE", "Device: " + deviceAddress + " Acceleration X: " + accelerationInfo.getX().getValue() + " " + accelerationInfo.getX().getUnit());
//        Log.d("BLE", "Device: " + deviceAddress + " Acceleration Y: " + accelerationInfo.getY().getValue() + " " + accelerationInfo.getY().getUnit());
//        Log.d("BLE", "Device: " + deviceAddress + " Acceleration Z: " + accelerationInfo.getZ().getValue() + " " + accelerationInfo.getZ().getUnit());
    }

    private boolean verifyProcessCount() {
        var numberOfRunningProcesses = taskMonitor.getNumberOfTasks();

        return numberOfRunningProcesses == numberOfCurrentProcesses && numberOfCurrentProcesses != 0;
    }
}
