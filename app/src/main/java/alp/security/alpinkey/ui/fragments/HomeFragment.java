package alp.security.alpinkey.ui.fragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.util.Preconditions;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import alp.security.alpinkey.R;
import alp.security.alpinkey.MainActivity;
import alp.security.alpinkey.ui.HidDeviceAdapter;
import alp.security.alpinkey.utils.DeviceManagementEvent;
import alp.security.alpinkey.utils.FirebaseManager;
import alp.security.alpinkey.utils.MutableSet;
import alp.security.alpinkey.utils.UiUpdateEvent;

import static alp.security.alpinkey.utils.FirebaseManager.EVENT_ONPOPUP;

public class HomeFragment extends Fragment {

    private static final String TAG = "AlpinKey|HomeFragment";
    private static final int REQUEST_DISCOVERABLE = 1002;
    private static final int DISCOVERABLE_DURATION_S = 60;

    Dialog mDialog;
    RecyclerView mHIDDevices;
    LinearLayout mRegister;

    LinearLayout mError;
    TextView mErrorText;
    ImageView mPowerButton;
    TextView mConnectedHost;
    AnimatedVectorDrawable animation;
    AlertDialog mDeviceDeleteDialog;

    private BluetoothDevice defaultDevice;
    private int currentConnectionState = BluetoothProfile.STATE_DISCONNECTED;
    private Set<BluetoothDevice> availableDevices;
    private static final Handler animationHandler = new Handler(Looper.getMainLooper());

    HidDeviceAdapter.ViewHolderListener onSelectDevice = new HidDeviceAdapter.ViewHolderListener() {
        @Override
        public void onClick(int position) {
            onSelectDevice(position);
        }

        @Override
        public void onConnectToggle(int position) {
            onToggleConnect(position);
        }

        @Override
        public boolean onDelete(int position) {
            if (position >= 0 && position < availableDevices.size()) {
                BluetoothDevice device = ((MutableSet<BluetoothDevice>) availableDevices).get(position);
                return onRemoveDevice(device);
            }
            return false;
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        // We register the HomeFragment here to the event bus so we can get the messages as the
        // fragment becomes available and visible to the user.
        EventBus.getDefault().register(this);
        updateHidDefaultHostDevice();
    }

    @Override
    public void onPause() {
        super.onPause();

        // We deregister the HomeFragment here from the even bus as it gets paused and non-visible
        // to the user - this is done to avoid memory leaks given old fragments that have no more
        // context but are still referenced by the bus system.
        EventBus.getDefault().unregister(this);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        mError = root.findViewById(R.id.error_dialog);
        mErrorText = root.findViewById(R.id.error_dialog_text);
        mConnectedHost = root.findViewById(R.id.text_connectedclient);
        mPowerButton = root.findViewById(R.id.img_power);
        mPowerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onShowPopup();
            }
        });

        availableDevices = new MutableSet<>();
        mDialog = new Dialog(Objects.requireNonNull(getActivity()));

        if(MainActivity.bluetoothUnavailable){
            mError.setVisibility(View.VISIBLE);
            mErrorText.setText(R.string.nobluetooth);
            mError.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });
        } else if(MainActivity.bluetoothOff){
            mError.setVisibility(View.VISIBLE);
            mErrorText.setText(R.string.bluetoothoff);
            mError.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((MainActivity)getActivity()).turnBluetoothOn();
                }
            });
        }

        SwitchCompat switchKeepBackground = root.findViewById(R.id.switch_keep_background);
        if (switchKeepBackground != null && getContext() != null) {
            SharedPreferences appPreferences = requireContext().getSharedPreferences(MainActivity.PREFERENCES, Context.MODE_PRIVATE);
            boolean keepBackground = appPreferences.getBoolean("keep_background_service", false);
            switchKeepBackground.setChecked(keepBackground);
            switchKeepBackground.setOnCheckedChangeListener((buttonView, isChecked) -> {
                appPreferences.edit().putBoolean("keep_background_service", isChecked).apply();
                if (isChecked) {
                    Toast.makeText(getActivity(), "Arka planda çalışma hizmeti etkinleştirildi", Toast.LENGTH_SHORT).show();
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).startService();
                    }
                } else {
                    Toast.makeText(getActivity(), "Arka planda çalışma hizmeti kapatıldı", Toast.LENGTH_SHORT).show();
                }
            });
        }

        return root;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUiUpdateEvent(UiUpdateEvent event) {
        switch (event.mType) {
            case UPDATE_CONN_ANIMATION:
                // We will only have one entry here so get it
                Map.Entry<BluetoothDevice, Integer> entry = event.mDevices.entrySet().iterator().next();
                Log.d(TAG,"Foreground UI -> " + event.mType
                        + " Device: " + entry.getKey() + " State: " + entry.getValue());
                renderAnimation(entry.getKey(), entry.getValue());
                break;
            case UPDATE_DEFAULT_HID_DEVICE:
                entry = event.mDevices.entrySet().iterator().next();
                Log.d(TAG,"Foreground UI -> " + event.mType
                        +" Device: " + entry.getKey() + " State: " + entry.getValue());
                onReceiveDefaultHidDevice(entry.getKey(), entry.getValue());
                renderAnimation(entry.getKey(), entry.getValue());
                break;
            case UPDATE_HID_DEVICES:
                Log.d(TAG,"Foreground UI -> " + event.mType
                        +" Devices: " + event.mDevices.keySet());
                onReceiveAvailableHidDevices(new ArrayList<>(event.mDevices.keySet()));
                break;
            case UPDATE_TOAST:
                Log.d(TAG,"Foreground UI -> " + event.mType + ": " + event.mToast);
                Toast.makeText(this.getActivity(), event.mToast, Toast.LENGTH_LONG).show();
                break;
            case UPDATE_SCREEN_KEEP_ALIVE_ON:
                Log.d(TAG,"Foreground UI -> " + event.mType);
                try {
                    Objects.requireNonNull(this.getActivity()).getWindow()
                            .addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to keep screen ON - window is null");
                }
                break;
            case UPDATE_SCREEN_KEEP_ALIVE_OFF:
                Log.d(TAG,"Foreground UI -> " + event.mType);
                try {
                    Objects.requireNonNull(this.getActivity()).getWindow()
                            .clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Failed to keep screen OFF");
                }
            default:
                Log.w(TAG,"Unknown UI updated event " + event.mType);
        }
    }

    public void onClickWarning(boolean visible){
        if(visible){
            mError.setVisibility(View.VISIBLE);
            mErrorText.setText(R.string.bluetoothoff);
            mError.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((MainActivity)getActivity()).turnBluetoothOn();
                }
            });
        } else{
            mError.setVisibility(View.INVISIBLE);
        }
    }

    public void onShowPopup(){
        if (getActivity() == null) return;
        FirebaseManager.sendLogEvent(EVENT_ONPOPUP,null);
        mDialog = new Dialog(requireActivity());
        mDialog.setContentView(R.layout.popup_deviceselection);

        mRegister = mDialog.findViewById(R.id.textview_registernewdevice);
        if (mRegister != null) {
            mRegister.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onRegisterNewDevice();
                }
            });
        }
        mHIDDevices = mDialog.findViewById(R.id.listview_hiddevices);

        updateHidDefaultHostDevice();
        updateHidAvailableDevices();

        HidDeviceAdapter hidDeviceAdapter = new HidDeviceAdapter(
                getActivity(), availableDevices, defaultDevice, currentConnectionState, onSelectDevice);

        if (mHIDDevices != null) {
            mHIDDevices.setAdapter(hidDeviceAdapter);
            mHIDDevices.setLayoutManager(new LinearLayoutManager(getActivity()));
        }

        mDialog.show();
    }

    void onRegisterNewDevice() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(
                BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                DISCOVERABLE_DURATION_S
        );
        startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DISCOVERABLE && resultCode > 0) {
            EventBus.getDefault().post(new DeviceManagementEvent(
                    DeviceManagementEvent.Type.REGISTER_NEW_DEVICE
            ));
        }
    }

    void onSelectDevice(int position) {
        if (position < 0 || position >= availableDevices.size()) return;
        BluetoothDevice device = ((MutableSet<BluetoothDevice>)availableDevices).get(position);
        if (device == null) return;

        // If pressed device is default device, reconnect
        if (defaultDevice  != null && defaultDevice.equals(device)) {
            EventBus.getDefault().post(new DeviceManagementEvent(
                    DeviceManagementEvent.Type.MANUAL_CONNECT_DEVICE
            ));
        } else {
            // Else, set as default
            EventBus.getDefault().post(new DeviceManagementEvent(
                    DeviceManagementEvent.Type.SELECT_DEFAULT_DEVICE, device
            ));
        }
    }

    void onToggleConnect(int position) {
        if (position < 0 || position >= availableDevices.size()) return;
        BluetoothDevice device = ((MutableSet<BluetoothDevice>) availableDevices).get(position);
        if (device == null) return;

        if (defaultDevice != null && defaultDevice.equals(device)) {
            if (currentConnectionState == BluetoothProfile.STATE_CONNECTED) {
                EventBus.getDefault().post(new DeviceManagementEvent(
                        DeviceManagementEvent.Type.DISCONNECT_DEVICE
                ));
            } else {
                EventBus.getDefault().post(new DeviceManagementEvent(
                        DeviceManagementEvent.Type.MANUAL_CONNECT_DEVICE
                ));
            }
        } else {
            EventBus.getDefault().post(new DeviceManagementEvent(
                    DeviceManagementEvent.Type.SELECT_DEFAULT_DEVICE, device
            ));
        }
    }

    @SuppressLint("RestrictedApi")
    private void renderAnimation(BluetoothDevice device, int connectionState) {
        Preconditions.checkNotNull(mPowerButton);

        Log.d(TAG, device + " -> " + connectionState);
        // Select image and text combo to display
        if (device != null &&
                (connectionState == BluetoothProfile.STATE_CONNECTING ||
                connectionState == BluetoothProfile.STATE_DISCONNECTING)) {
            mPowerButton.setImageResource(R.drawable.connecting_icon_animated);
            mConnectedHost.setText(getString(R.string.connectinghostmessage, device.getName()));
        } else if (device != null &&
                connectionState == BluetoothProfile.STATE_CONNECTED) {
            mPowerButton.setImageResource(R.drawable.connected_icon_animated);
            mConnectedHost.setText(getString(R.string.connectedhostmessage, device.getName()));
        } else if (device != null &&
                connectionState == BluetoothProfile.STATE_DISCONNECTED) {
            mPowerButton.setImageResource(R.drawable.disconnected_icon_animated);
            mConnectedHost.setText(getString(R.string.disconnectedhostmessage, device.getName()));
        } else if (device == null &&
                connectionState == BluetoothProfile.STATE_CONNECTING) {
            mPowerButton.setImageResource(R.drawable.connecting_icon_animated);
            mConnectedHost.setText(R.string.pairingnewdevice);
        } else {
            mPowerButton.setImageResource(R.drawable.disconnected_icon_animated);
            mConnectedHost.setText(R.string.nodefaulthostmessage);
        }

        // Now re-render animation
        animation = (AnimatedVectorDrawable) mPowerButton.getDrawable();
        animation.registerAnimationCallback(new Animatable2.AnimationCallback() {
            @Override
            public void onAnimationEnd(Drawable drawable) {
                super.onAnimationEnd(drawable);
                animationHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        animation.start();
                    }
                });
            }
        });
        animation.start();

        if (device != null && connectionState == BluetoothProfile.STATE_DISCONNECTED &&
            MainActivity.autoConnectFlag) {

            EventBus.getDefault().post(new DeviceManagementEvent(
                    DeviceManagementEvent.Type.MANUAL_CONNECT_DEVICE
            ));
            MainActivity.autoConnectFlag = false;
        }
    }

    private void updateHidAvailableDevices() {
        EventBus.getDefault().post(new UiUpdateEvent(
                UiUpdateEvent.Type.UPDATE_HID_DEVICES_REQ
        ));
    }

    private void updateHidDefaultHostDevice() {
        EventBus.getDefault().post(new UiUpdateEvent(
                UiUpdateEvent.Type.UPDATE_DEFAULT_HID_DEVICE_REQ
        ));
    }

    private void onReceiveAvailableHidDevices(List<BluetoothDevice> devices){
        if (devices != null) {
            // Sync available devices - first add all in the list and then retain just current list
            availableDevices.addAll(devices);
            availableDevices.retainAll(devices);
        }
        if (mDialog != null &&  mHIDDevices != null && mDialog.isShowing()) {
            HidDeviceAdapter hidDeviceAdapter = new HidDeviceAdapter(
                    getActivity(), availableDevices, defaultDevice, currentConnectionState, onSelectDevice);

            mHIDDevices.setAdapter(hidDeviceAdapter);
            mDialog.show();
        }
    }

    private void onReceiveDefaultHidDevice(BluetoothDevice device, int connectionState) {
        defaultDevice = device;
        currentConnectionState = connectionState;
        if (mDialog != null && mHIDDevices != null && mDialog.isShowing()) {
            if (mHIDDevices.getAdapter() instanceof HidDeviceAdapter) {
                ((HidDeviceAdapter) mHIDDevices.getAdapter()).setDefaultDevice(defaultDevice, currentConnectionState);
            }
        }
    }

    private boolean onRemoveDevice(BluetoothDevice device) {
        if (device != null) {
            final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            // Post to event bus to graciously remove this device
                            EventBus.getDefault().post(new DeviceManagementEvent(
                                    DeviceManagementEvent.Type.REMOVE_DEVICE,
                                    device
                            ));
                            break;

                        case DialogInterface.BUTTON_NEGATIVE:
                            // Nothing to do here
                            break;
                    }
                }
            };


            mDeviceDeleteDialog = new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                    .setTitle(getString(R.string.dialog_title_removedevice,device.getName()))
                    .setCancelable(true)
                    .setIcon(R.drawable.thrash_icon_white)
                    .setMessage(getString(R.string.dialog_body_removedevice,device.getName(), device.getAddress()))
                    .setPositiveButton(getString(R.string.yes), listener)
                    .setNegativeButton(getString(R.string.no), listener)
                    .show();

            return true;
        }
        return false;
    }

    public void onCleanUpDialogs() {
        if (mDialog != null) mDialog.dismiss();
        if (mDeviceDeleteDialog != null) mDeviceDeleteDialog.dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        onCleanUpDialogs();
    }
}
