package com.dissertation.tmwt;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TmntFragment extends Fragment {
    private TextView runningProcessCount;
    private TextView startAndStopText;
    private int expectedThreadCount = 0;
    private ArrayList<BluetoothDevice> selectedDevices;
    private String fileName;
    boolean isRunning = false;
    private ExecutorService executorService;
    private List<Runnable> tasks = new ArrayList<>();
    private TaskMonitor taskMonitor = new TaskMonitor();
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
        View view = inflater.inflate(R.layout.fragment_tmwt, container, false);

        runningProcessCount = view.findViewById(R.id.runningProcessCount);
        startAndStopText = view.findViewById(R.id.startAndStopInfo);

        if (args != null) {
            // Retrieve the ArrayList from the bundle
            selectedDevices = args.getParcelableArrayList("selectedDevices");

            // Now you can use 'selectedDevices' within your fragment
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText fileName = view.findViewById(R.id.fileId);

        Button startTMWT = view.findViewById(R.id.start);
        Button stopTMWT = view.findViewById(R.id.stop);

        handler.post(updateTaskCountRunnable);

        startTMWT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRunning) {
                    System.out.println("Already running");
                    return;
                }

                isRunning = true;
                expectedThreadCount = selectedDevices.size() * 4; // Since you have 4 types of characteristics

                CharacteristicInfo[] characteristics = {
                        new CharacteristicInfo(TEMP_CHARACTERISTIC_UUID, "Temperature"),
                        new CharacteristicInfo(MAGN_CHARACTERISTIC_UUID, "Magnetometer"),
                        new CharacteristicInfo(GYRO_CHARACTERISTIC_UUID, "Gyroscope"),
                        new CharacteristicInfo(ACCEL_CHARACTERISTIC_UUID, "Accelerometer")
                };

                // Calculate the number of tasks to determine the size of the thread pool
                int numberOfTasks = expectedThreadCount;

                // Initialize the ExecutorService with the fixed number of threads
                if (executorService == null || executorService.isShutdown()) {
                    executorService = Executors.newFixedThreadPool(numberOfTasks);
                }

                tasks.clear(); // Clear previous tasks if any
                for (CharacteristicInfo characteristic : characteristics) {
                    for (BluetoothDevice selectedDevice : selectedDevices) {
                        BLEConnectionTask task = new BLEConnectionTask(getActivity(), selectedDevice, fileName.getText().toString(), characteristic.uuid, characteristic.name, taskMonitor);
                        Runnable taskWrapper = () -> {
                            try {
                                task.run();
                            } catch (Exception e) {
                                Log.e("BLE", "Task interrupted: ", e);
                            }
                        };
                        tasks.add(taskWrapper);
                    }
                }

                tasks.forEach(executorService::submit);

                // Schedule a check after 1 second
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    int activeCount = taskMonitor.getNumberOfTasks();
                    System.out.println("Active threads after 1 second: " + activeCount);
                    if (activeCount != expectedThreadCount) {
                        System.out.println("Incorrect number of threads, attempting to restart tasks.");
                        restartTasks();
                    }
                }, 1000); // Delay of 1 second

            }
        });

        stopTMWT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("Actually running tasks - " + taskMonitor.getNumberOfTasks());

                if (!isRunning) {
                    System.out.println("Not running");
                    return;
                }

                if (executorService != null) {
                    executorService.shutdownNow(); // Interrupt all running tasks
                    try {
                        // Wait a while for existing tasks to terminate
                        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                            Log.e("BLE", "Tasks did not terminate; forcing shutdown");
                            for (Runnable pendingTask : executorService.shutdownNow()) { // Second call to force
                                if (pendingTask instanceof BLEConnectionTask) {
                                    ((BLEConnectionTask) pendingTask).disconnect(); // Ensure disconnection
                                }
                            }
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // Handle thread interruption
                        Log.e("BLE", "Thread interrupted during shutdown", ie);
                    } finally {
                        isRunning = false; // Update the running status
                    }
                }
            }
        });
    }

    private void restartTasks() {
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.e("TMWT", "Executor did not terminate in the allotted time.");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                Log.e("TMWT", "Thread interrupted during shutdown.", ie);
            }
        }
        executorService = Executors.newFixedThreadPool(selectedDevices.size() * 4);
        tasks.forEach(executorService::submit);
        isRunning = true;
    }

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateTaskCountRunnable = new Runnable() {
        @Override
        public void run() {
            refreshTaskCount();
            handler.postDelayed(this, 1000); // schedule the next run in 1 second
        }
    };

    private void refreshTaskCount() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                var numberOfRunningProcesses = taskMonitor.getNumberOfTasks();
                runningProcessCount.setText("Running processes: " + numberOfRunningProcesses);

                if (numberOfRunningProcesses == expectedThreadCount && expectedThreadCount != 0) {
                    startAndStopText.setText("Start the test!");
                    startAndStopText.setTextColor(getResources().getColor(R.color.colorGreen));
                } else {
                    startAndStopText.setText("Test is not ready");
                    startAndStopText.setTextColor(getResources().getColor(R.color.colorRed));
                }
            });
        }
    }
}