package alp.security.alpinkey.ui;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Set;

import alp.security.alpinkey.R;
import alp.security.alpinkey.utils.MutableSet;

public class HidDeviceAdapter extends RecyclerView.Adapter<HidDeviceAdapter.ViewHolder> {

    private Set<BluetoothDevice> bluetoothDevices;
    private BluetoothDevice defaultDevice;
    private int connectionState;
    private Context context;
    private ViewHolderListener listener;

    public interface ViewHolderListener {
        void onClick(int position);

        void onConnectToggle(int position);

        boolean onDelete(int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {

        TextView devName, devAddr;
        ImageView devIcon;
        ConstraintLayout devGroup;
        ImageView devConnect;
        ImageView devDelete;
        private ViewHolderListener listener;

        ViewHolder(@NonNull View itemView, ViewHolderListener listener){
            super(itemView);
            this.listener = listener;
            devName = itemView.findViewById(R.id.text1);
            devAddr = itemView.findViewById(R.id.text2);
            devGroup = itemView.findViewById(R.id.device_select_group);
            devIcon = itemView.findViewById(R.id.img_device);
            devConnect = itemView.findViewById(R.id.img_device_connect);
            devDelete = itemView.findViewById(R.id.img_device_delete);

            devGroup.setOnClickListener(this);
            if (devConnect != null) devConnect.setOnClickListener(this);
            if (devDelete != null) devDelete.setOnClickListener(this);
        }

        @Override
        public void onClick(View view){
            if (listener != null) {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                if (view.equals(devConnect)) {
                    listener.onConnectToggle(pos);
                } else if (view.equals(devDelete)) {
                    listener.onDelete(pos);
                } else {
                    listener.onClick(pos);
                }
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (listener != null) {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    return listener.onDelete(pos);
                }
            }
            return false;
        }
    }

    public HidDeviceAdapter(Context ctx,
                            Set<BluetoothDevice> devices,
                            BluetoothDevice defaultDevice,
                            int connectionState,
                            ViewHolderListener listener) {
        context = ctx;
        bluetoothDevices = devices;
        this.defaultDevice = defaultDevice;
        this.connectionState = connectionState;
        this.listener = listener;
    }

    public HidDeviceAdapter(Context ctx,
                            Set<BluetoothDevice> devices,
                            ViewHolderListener listener) {
        this(ctx, devices, null, BluetoothProfile.STATE_DISCONNECTED, listener);
    }

    public void setDefaultDevice(BluetoothDevice defaultDevice, int connectionState) {
        this.defaultDevice = defaultDevice;
        this.connectionState = connectionState;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HidDeviceAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.recycler_row,parent,false);
        return new HidDeviceAdapter.ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull HidDeviceAdapter.ViewHolder holder, int position) {
        if (bluetoothDevices == null) return;
        BluetoothDevice device = null;
        if (bluetoothDevices instanceof MutableSet) {
            device = ((MutableSet<BluetoothDevice>) bluetoothDevices).get(position);
        } else {
            int i = 0;
            for (BluetoothDevice dev : bluetoothDevices) {
                if (i == position) {
                    device = dev;
                    break;
                }
                i++;
            }
        }

        if (device != null) {
            String name = null;
            try {
                name = device.getName();
            } catch (Exception ignored) {}
            String addr = null;
            try {
                addr = device.getAddress();
            } catch (Exception ignored) {}

            holder.devName.setText(name != null && !name.isEmpty() ? name : (addr != null ? addr : "Unknown Device"));
            holder.devAddr.setText(addr != null ? addr : "");
        } else {
            holder.devName.setText(R.string.nodefaultdevicename);
            holder.devAddr.setText(R.string.nodefaultdeviceaddr);
        }

        if (holder.devConnect != null) {
            if (device != null && defaultDevice != null && defaultDevice.equals(device)) {
                if (connectionState == BluetoothProfile.STATE_CONNECTED) {
                    holder.devConnect.setImageResource(R.drawable.ic_connected_plugs);
                } else {
                    holder.devConnect.setImageResource(R.drawable.ic_disconnected_plugs);
                }
            } else {
                holder.devConnect.setImageResource(R.drawable.ic_disconnected_plugs);
            }
        }
    }

    @Override
    public int getItemCount() {
        return bluetoothDevices != null ? bluetoothDevices.size() : 0;
    }
}
