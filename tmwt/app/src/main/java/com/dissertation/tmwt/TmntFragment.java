package com.dissertation.tmwt;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TmntFragment extends Fragment {

    private ArrayList<BluetoothDevice> selectedDevices;
    private String fileName;
    boolean isRunning = false;
    private ExecutorService executorService;

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
            fileName = args.getString("fileName");
            selectedDevices = args.getParcelableArrayList("selectedDevices");

            // Now you can use 'selectedDevices' within your fragment
        }

        return inflater.inflate(R.layout.fragment_tmwt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button startTMWT = view.findViewById(R.id.start);
        Button stopTMWT = view.findViewById(R.id.stop);

        int numberOfThreads = this.selectedDevices.size();
        startTMWT.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                System.out.println(isRunning);
                if (isRunning) {
                    System.out.println("Good Try, brother");
                    return;
                }

                isRunning = true;
                if (executorService == null || executorService.isShutdown()) {
                    executorService = Executors.newFixedThreadPool(numberOfThreads);
                }

                for (BluetoothDevice selectedDevice : selectedDevices) {
                    BLEConnectionTask task = new BLEConnectionTask(getActivity(), selectedDevice, fileName + "_" + selectedDevice.getName());
                    executorService.submit(task);
                }
            }
        });

        stopTMWT.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (isRunning && executorService != null && !executorService.isShutdown()) {
                    executorService.shutdownNow(); // Attempt to stop all actively executing tasks
                    // Optionally, you can handle the list of runnable tasks that were awaiting execution if needed
                    isRunning = false;
                }
            }
        });
    }
}