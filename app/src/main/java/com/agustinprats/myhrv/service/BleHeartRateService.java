/* Copyright (c) 2013-2014 Agustín Prats
 *
 * This file is part of HeartWave.
 *
 *  HeartWave is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  HeartWave is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with HeartWave.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.agustinprats.myhrv.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.agustinprats.myhrv.R;
import com.agustinprats.myhrv.model.HeartRateDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BleHeartRateService extends HeartRateService {

    private final static String TAG = BleHeartRateService.class.getSimpleName();

    /** Scanning time out. */
    public static final long SCAN_PERIOD = 10000;

    /** Connecting time out. */
    public static final int CONNECTING_TIMEOUT = 10000;

    // Bluetooth 4.0 protocol constants
    // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
    private final static UUID UUID_HEART_RATE_RECORDING = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private final static UUID UUID_HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private final static UUID UUID_BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private final static UUID UUID_BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private final static UUID UUID_DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    private final static UUID UUID_DEVICE_MANUFACTURER = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
    private final static UUID UUID_DEVICE_MODEL = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    private final static UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Bluetooth API objects
    private BluetoothManager _bluetoothManager;
    private BluetoothAdapter _bluetoothAdapter;
    private BluetoothGatt _bluetoothGatt;
    private BluetoothGattCharacteristic _notifyCharacteristic;

    /** Connection time out handler. */
    private Handler _connectingHandler;

    /** Scan time out handler. */
    private Handler _scanningHandler;

    /** True if currently scanning for heart rate devices. False otherwise. */
    private boolean _scanning = false;

    String _disconnectMessage;

    /** Implements callback methods for GATT events that the app cares about.
     * For example, connection change and services discovered. */
    private final BluetoothGattCallback _gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {

                onConnected();
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                onDisconnected();
                closeBleGatt();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            Log.d(TAG, "onServicesDiscovered");
            requestCharacteristic(UUID_DEVICE_INFORMATION_SERVICE, UUID_DEVICE_MANUFACTURER);
        }

        /** Looks for values from the ble device. */
        private void requestCharacteristic(UUID service, UUID characteristic) {

            Log.d(TAG, "requestCharacteristic(" + service.toString() + ", " + characteristic.toString() + ")");

            List<BluetoothGattService> gattServices = getSupportedGattServices();
            if (gattServices == null) return;

            // Loops through available GATT Services.
            for (BluetoothGattService gattService : gattServices) {

                // Subscribe only specified service
                if (gattService.getUuid().equals(service)) {

                    List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();

                    // Loops through available Characteristics.
                    for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {

                        // Subscribe only to specified characteristic
                        if (gattCharacteristic.getUuid().equals(characteristic)) {

                            Log.d(TAG, "Found battery characteristic");
                            final int charaProp = gattCharacteristic.getProperties();
                            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                                // If there is an active notification on a characteristic, clear
                                // it first so it doesn't update the data field on the user interface.
                                if (_notifyCharacteristic != null) {

                                    Log.d(TAG, "Clearing previous notification");
                                    setCharacteristicNotification(_notifyCharacteristic, false);
                                    _notifyCharacteristic = null;
                                }
                                Log.d(TAG, "Reading characteristic");
                                readCharacteristic(gattCharacteristic);
                            }
                            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {

                                _notifyCharacteristic = gattCharacteristic;
                                Log.d(TAG, "Subscribing to characteristic");
                                setCharacteristicNotification(gattCharacteristic, true);
                            }
                        }
                    }
                }
            }
        }

        /** Reads values from the ble device. */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d(TAG, "onCharacteristicRead");
            if (status == BluetoothGatt.GATT_SUCCESS) {

                if (UUID_BATTERY_LEVEL.equals(characteristic.getUuid())) {

                    int format = BluetoothGattCharacteristic.FORMAT_UINT8;
                    Integer batteryLevel = characteristic.getIntValue(format, 0);
                    onBatteryLevelChanged(batteryLevel);

                    requestCharacteristic(UUID_HEART_RATE_SERVICE, UUID_HEART_RATE_RECORDING);
                }
                else if (UUID_DEVICE_MANUFACTURER.equals(characteristic.getUuid())) {

                    String manufacturer = characteristic.getStringValue(0);
                    onDeviceManufacturerRead(manufacturer);

                    requestCharacteristic(UUID_DEVICE_INFORMATION_SERVICE, UUID_DEVICE_MODEL);
                }
                else if (UUID_DEVICE_MODEL.equals(characteristic.getUuid())) {

                    String model = characteristic.getStringValue(0);
                    onDeviceModelRead(model);

                    requestCharacteristic(UUID_BATTERY_SERVICE, UUID_BATTERY_LEVEL);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            // Handling Heart Rate Measurement profile. Specs:
            // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
            if (UUID_HEART_RATE_RECORDING.equals(characteristic.getUuid()))
                processHeartRateMeasurement(characteristic);
            else
                Log.e(TAG, "Unknown characteristic changed: " + characteristic.getUuid().toString());
        }
    };

    @Override
    public void onConnected() {
        super.onConnected();

        _disconnectMessage = null;
        _bluetoothGatt.discoverServices();
    }

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        cancelConnectingHandler();
    }

    /** Reads R-R intervals from the ble device. */
    private void processHeartRateMeasurement(final BluetoothGattCharacteristic characteristic) {

        long timeStamp = System.currentTimeMillis();
        int flag = characteristic.getProperties();

        // Heart rate format
        int heartRateFormat = BluetoothGattCharacteristic.FORMAT_UINT8;
        if ((flag & 0x01) != 0) heartRateFormat = BluetoothGattCharacteristic.FORMAT_UINT16;

        // Energy expended status (0 == not present) (1 == present): Unit: kilo Joules
        final boolean energyExpendedPresent = ((flag & 0x08) >> 3) > 0;

        // RR-Interval bit (0 == Not present) (1 == Present)
        final boolean containRrIntervals = ((flag & 0x16) >> 4) > 0;
        if (containRrIntervals) {

            int offset = 2;
            if (heartRateFormat == BluetoothGattCharacteristic.FORMAT_UINT16) offset++;
            if (energyExpendedPresent) offset += 2;

            ArrayList<Integer> rrIntervalList = new ArrayList<Integer>();
            while (rrIntervalList.size() < 9) {

                Integer rrInterval = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                if (rrInterval == null || rrInterval <= 0) break;
                rrIntervalList.add(rrInterval);
                offset += 2;
            }

            if (!rrIntervalList.isEmpty()) {

                cancelConnectingHandler();
                onNewRRIntervals(timeStamp, rrIntervalList);
            }
            else
                Log.e(TAG, "RR interval list empty!");
        }
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    @Override
    public boolean initialize() {

        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (_bluetoothManager == null) {
            _bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (_bluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        _bluetoothAdapter = _bluetoothManager.getAdapter();
        if (_bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        super.initialize();

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Returns true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    @Override
    public boolean connect(final String address) {
        Log.d(TAG, "connect(" + address + ")");

        if (_bluetoothAdapter == null || address == null || !_bluetoothAdapter.isEnabled()) {

            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address or bt not enabled.");
            return false;
        }

        if (isConnected()) {

            Log.e(TAG, "Already connected to address");
            return false;
        }

        // Previously connected device. Try to reconnect
        if (_deviceAddress != null && _deviceAddress.equals(address) && _bluetoothGatt != null) {

            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (_bluetoothGatt.connect()) {

                onConnecting(_deviceAddress);
                return true;
            }
            else {

                return false;
            }
        }
        // Create new connection
        else {

            final BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                Log.w(TAG, "Device not found.  Unable to connect.");
                return false;
            }
            // We want to directly connect to the device, so we are setting the reconnect
            // parameter to false.
            _bluetoothGatt = device.connectGatt(this, false, _gattCallback);
            if (_bluetoothGatt == null) {

                Log.e(TAG, "BluetoothGatt is null");
            }

            onConnecting(address);

            return true;
        }
    }

    @Override
    public void onConnecting(String address) {
        super.onConnecting(address);

        startConnectingHandler();
    }

    private void startConnectingHandler() {
        cancelConnectingHandler();
        try {
            _connectingHandler = new Handler();
            // Stops connecting after a pre-defined scan period.
            _connectingHandler.postDelayed(cancelConnectingRunnable, CONNECTING_TIMEOUT);
        }
        catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void cancelConnectingHandler() {

        if (_connectingHandler != null) {

            _connectingHandler.removeCallbacks(cancelConnectingRunnable);
            _connectingHandler.removeCallbacksAndMessages(null);
            _connectingHandler = null;
        }
    }

    private Runnable cancelConnectingRunnable = new Runnable() {
        @Override
        public void run() {

            _errorCode = R.string.no_rr_intervals;
            disconnect();
            close();
        }
    };

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    @Override
    public void disconnect() {
        super.disconnect();

        Log.d(TAG, "disconnect");

        if (_bluetoothAdapter == null || _bluetoothGatt == null) {

            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        _bluetoothGatt.disconnect();

        if (isConnecting()) {

            onDisconnected();
        }
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    @Override
    public void close() {

        closeBleGatt();
        super.close();
    }

    private void closeBleGatt() {

        Log.d(TAG, "closeBleGatt");
        if (_bluetoothGatt == null) {
            return;
        }
        _bluetoothGatt.close();
        _bluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
     private void readCharacteristic(BluetoothGattCharacteristic characteristic) {

        if (_bluetoothAdapter == null || _bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        _bluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {

        if (_bluetoothAdapter == null || _bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        _bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_RECORDING.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                _bluetoothGatt.writeDescriptor(descriptor);
            }
            else {

                Log.e(TAG, "Null heart rate measurement descriptor");
            }
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    private List<BluetoothGattService> getSupportedGattServices() {

        if (_bluetoothGatt == null) return null;

        return _bluetoothGatt.getServices();
    }

    public BluetoothAdapter getBluetoothAdapter() {

        return _bluetoothAdapter;
    }

    private Runnable _cancelScanRunnable = new Runnable() {
        @Override
        public void run() {
            _scanning = false;
            if (getBluetoothAdapter() != null)
                getBluetoothAdapter().stopLeScan(_bleScanCallback);
            onHeartRateDeviceScanStopped();
        }
    };

    public void startScanningDevices() {

        Log.d(TAG, "startScanningDevices");
        // Stops _scanning after a pre-defined scan period.
        _scanningHandler = new Handler();
        _scanningHandler.postDelayed(_cancelScanRunnable, SCAN_PERIOD);

        _scanning = true;
        _bluetoothAdapter.startLeScan(_bleScanCallback);
    }

    public void stopScanningDevices() {

        if (_scanning) {

            Log.d(TAG, "stopScanningDevices");
            _scanning = false;
            _bluetoothAdapter.stopLeScan(_bleScanCallback);
            _scanningHandler.removeCallbacks(_cancelScanRunnable);
            onHeartRateDeviceScanStopped();
        }
    }

    public boolean isScanning() {

        return _scanning;
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback _bleScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

                    HeartRateDevice heartRateDevice = new HeartRateDevice(device.getName(), device.getAddress());
                    BleHeartRateService.this.onDeviceFound(heartRateDevice);
                }
            };

}
