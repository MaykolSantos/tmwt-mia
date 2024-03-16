package com.dissertation.tmwt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeviceListFragment extends Fragment {

    private RecyclerView recyclerView;
    private DeviceAdapter deviceAdapter;
    private Button startTMWT;
    private TextView fileIdInputText;
    private ArrayList<BluetoothDevice> deviceList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_list, container, false);
        recyclerView = view.findViewById(R.id.devices_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        deviceAdapter = new DeviceAdapter(deviceList);

        recyclerView.setAdapter(deviceAdapter);
        fetchPairedDevices();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        deviceAdapter = new DeviceAdapter(deviceList);

        recyclerView.setAdapter(deviceAdapter);
        fetchPairedDevices(); // Reload paired devices
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button startTMWT = view.findViewById(R.id.startTMWT);
        fileIdInputText = view.findViewById(R.id.fileId);

        startTMWT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String helperText = "";
                if (fileIdInputText.getText().length() == 0) {
                    helperText = "No File Id";
                }

                if (deviceAdapter.getSelectedDevicesCount() == 0) {
                    if (!isTextEmpty(helperText)) {
                        helperText += " and ";
                    }

                    helperText += "No devices selected";
                }

                if (!isTextEmpty(helperText)) {
                    Toast.makeText(getActivity(), helperText, Toast.LENGTH_SHORT).show();

                    return;
                }

                TmntFragment tmntFragment = new TmntFragment();
                FragmentManager fragmentManager = getActivity().getSupportFragmentManager(); // Or getChildFragmentManager() if within a fragment
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

                Bundle args = new Bundle();
                args.putParcelableArrayList("selectedDevices", deviceAdapter.getSelectedDevices());
                args.putString("fileName", fileIdInputText.getText().toString());

                // Set the arguments for the fragment
                tmntFragment.setArguments(args);

                fragmentTransaction.replace(R.id.device_list_fragment, tmntFragment); // Use the container ID
                fragmentTransaction.addToBackStack(null); // Optional, for back navigation
                fragmentTransaction.commit();

            }
        });
    }

    private boolean isTextEmpty(String text) {
        return text.equals("");
    }

    private void fetchPairedDevices() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            deviceList.clear();
            if (pairedDevices != null && !pairedDevices.isEmpty()) {
                deviceList.addAll(pairedDevices);
            }

            deviceAdapter.notifyDataSetChanged();
        }
    }
}
