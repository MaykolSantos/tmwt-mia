package com.dissertation.tmwt;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TmntFragment extends Fragment {

    private ArrayList<BluetoothDevice> selectedDevices;
    private String fileName;
    boolean isRunning = false;
    private ExecutorService executorService;
    private List<Runnable> tasks = new ArrayList<>();
    private final static UUID ACCEL_CHARACTERISTIC_UUID = UUID.fromString("00800000-0001-11e1-ac36-0002a5d5c51b");
    private final static UUID MAGN_CHARACTERISTIC_UUID = UUID.fromString("00200000-0001-11e1-ac36-0002a5d5c51b");
    private final static UUID TEMP_CHARACTERISTIC_UUID = UUID.fromString("00190000-0001-11e1-ac36-0002a5d5c51b");
    private final static UUID GYRO_CHARACTERISTIC_UUID = UUID.fromString("00400000-0001-11e1-ac36-0002a5d5c51b");

    public TmntFragment() {
        // Required empty public constructor
    }

    public static TmntFragment newInstance(String param1, String param2) {
        TmntFragment fragment = new TmntFragment();
        Bundle args = new Bundle();

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            this.selectedDevices = getArguments().getParcelableArrayList("selectedDevices");
            executorService = Executors.newFixedThreadPool(this.selectedDevices.size());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Bundle args = getArguments();
        if (args != null) {
            // Retrieve the ArrayList from the bundle
            selectedDevices = args.getParcelableArrayList("selectedDevices");

            // Now you can use 'selectedDevices' within your fragment
        }

        return inflater.inflate(R.layout.fragment_tmwt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText fileName = view.findViewById(R.id.fileId);

        Button startTMWT = view.findViewById(R.id.start);
        Button stopTMWT = view.findViewById(R.id.stop);

        startTMWT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRunning) {
                    System.out.println("Already running");
                    return;
                }

                isRunning = true;

                // Define the UUIDs for the characteristics
                CharacteristicInfo[] characteristics = {
                        new CharacteristicInfo(TEMP_CHARACTERISTIC_UUID, "Temperature"),
                        new CharacteristicInfo(MAGN_CHARACTERISTIC_UUID, "Magnetometer"),
                        new CharacteristicInfo(GYRO_CHARACTERISTIC_UUID, "Gyroscope"),
                        new CharacteristicInfo(ACCEL_CHARACTERISTIC_UUID, "Accelerometer")
                };

                // Calculate the number of tasks to determine the size of the thread pool
                int numberOfTasks = selectedDevices.size() * characteristics.length;

                // Initialize the ExecutorService with the fixed number of threads
                if (executorService == null || executorService.isShutdown()) {
                    executorService = Executors.newFixedThreadPool(numberOfTasks);
                }

                // Collect tasks
                tasks.clear(); // Clear previous tasks if any
                for (CharacteristicInfo characteristic : characteristics) {
                    for (BluetoothDevice selectedDevice : selectedDevices) {
                        BLEConnectionTask task = new BLEConnectionTask(getActivity(), selectedDevice, fileName.getText().toString(), characteristic.uuid, characteristic.name);
                        Runnable taskWrapper = () -> {
                            try {
                                Log.i("BEST TAG", characteristic.name + " " + selectedDevice.getName());
                                task.run();
                            } catch (Exception e) {
                                Log.e("BLE", "Task interrupted", e);
                            }
                        };
                        tasks.add(taskWrapper);
                    }
                }

                // Log the number of active threads after submission
                int activeCount = ((ThreadPoolExecutor) executorService).getActiveCount();
                System.out.println("Number of active threads: " + activeCount);
                System.out.println("Number of tasks submitted: " + tasks.size());


                tasks.forEach(executorService::submit);
            }
        });

        stopTMWT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRunning) {
                    System.out.println("Not running");
                    return;
                }

                if (executorService != null) {
                    executorService.shutdownNow(); // Attempt to stop all actively executing tasks
                    try {
                        // Wait a while for existing tasks to terminate
                        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                            Log.e("BLE", "Not all tasks terminated");
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        Log.e("BLE", "Thread interrupted during shutdown", ie);
                    } finally {
                        isRunning = false; // Update the running status
                    }
                }
            }
        });
    }
}