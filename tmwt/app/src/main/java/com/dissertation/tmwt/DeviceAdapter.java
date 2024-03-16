package com.dissertation.tmwt;

import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private final List<BluetoothDevice> devices;
    private final ArrayList<BluetoothDevice> selectedDevices = new ArrayList<>();

    public DeviceAdapter(List<BluetoothDevice> devices) {
        this.devices = devices;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName;
        LinearLayout rootView;
        CheckBox checkBox;


        public ViewHolder(View view) {
            super(view);
            deviceName = view.findViewById(R.id.device_name);
            rootView = view.findViewById(R.id.device_item_root_view);
            checkBox = itemView.findViewById(R.id.selectedDevice);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.device_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        BluetoothDevice device = devices.get(position);
        holder.deviceName.setText(device.getName() + "\n" + device.getAddress());
        holder.rootView.setSelected(selectedDevices.contains(device));

        holder.checkBox.setOnCheckedChangeListener(null); // Clear listener

        holder.checkBox.setChecked(selectedDevices.contains(device));

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            System.out.println(getSelectedDevicesCount());
            if (isChecked) {
                if (!selectedDevices.contains(device)) {
                    selectedDevices.add(device);
                }
            } else {
                selectedDevices.remove(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public int getSelectedDevicesCount() { return selectedDevices.size(); }

    public ArrayList<BluetoothDevice> getSelectedDevices() {
        return selectedDevices;
    }
}
