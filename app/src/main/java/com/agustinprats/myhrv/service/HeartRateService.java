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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.agustinprats.myhrv.MainActivity;
import com.agustinprats.myhrv.R;
import com.agustinprats.myhrv.model.CoherenceZone;
import com.agustinprats.myhrv.model.HeartRateDevice;
import com.agustinprats.myhrv.model.RrInterval;
import com.agustinprats.myhrv.model.RrIntervalList;
import com.agustinprats.myhrv.model.RrIntervalListListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class that defines the basic functionality of a service that connects
 * to a heart rate device and keeps a list with all R-R intervals measured
 */
public abstract class HeartRateService extends Service implements RrIntervalListListener {

    private static final String TAG = HeartRateService.class.toString();

    // connection states
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    // Notifications IDs
    private static final int COHERENCE_NOTIFICATION_ID = 0;
    private static final int MESSAGE_NOTIFICATION_ID = 1;

    /** Shared preferences key that holds the last connected heart rate address. */
    private final static String DEVICE_ADDRESS_KEY = "heart_rate_device_address_key";

    /** Max dropped intervals in a row before disconnecting. */
    private static final int MAX_DROPPED_IN_A_ROW = 5;

    // Default device values
    private static final String DEFAULT_DEVICE_MANUFACTURER = "na";
    private static final String DEFAULT_DEVICE_MODEL = "na";

    /** Current connection state. */
    private int _connectionState = STATE_DISCONNECTED;

    /** Stored valid R-R intervals. */
    private RrIntervalList _intervals;

    /** True if the app is in foreground. False otherwise. */
    private boolean _inForeground = false;

    /** List of listener to be notified when new values are available. */
    private List<HeartRateServiceListener> _listeners = new ArrayList<HeartRateServiceListener>();

    /** Current device address. */
    protected String _deviceAddress = null;

    /** Current battery level. */
    private int _batteryLevel = -1;

    // Current device info
    private String _deviceManufacturer = DEFAULT_DEVICE_MANUFACTURER;
    private String _deviceModel = DEFAULT_DEVICE_MODEL;

    /** Last error code. */
    protected Integer _errorCode = null;

    @Override
    public IBinder onBind(Intent intent) {

        if (_intervals == null)
            resetIntervals();

        return _binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder _binder = new LocalBinder();

    public class LocalBinder extends Binder {

        public HeartRateService getService() {

            return HeartRateService.this;
        }
    }

    /** Returns true if the device is connected. False otherwise. */
    public boolean isConnected() {

        return _connectionState == STATE_CONNECTED;
    }

    /** Returns true if the device is connecting. False otherwise. */
    public boolean isConnecting() {

        return _connectionState == STATE_CONNECTING;
    }

    /** Returns true if the device is disconnected. False otherwise. */
    public boolean isDisconnected() {

        return _connectionState == STATE_DISCONNECTED;
    }

    /** Retrieves the last connected device address from the SharedPreferences. */
    private void retrieveDeviceAddress() {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        _deviceAddress = sharedPrefs.getString(DEVICE_ADDRESS_KEY, null);
        Log.d(TAG, "Retrieved address: " + _deviceAddress);
    }

    /** Saves the last connected device address to the SharedPreferences. */
    private void storeDeviceAddress() {
        if (_deviceAddress == null || _deviceAddress.length() == 0) return;

        Log.d(TAG, "Storing device address = " + _deviceAddress);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putString(DEVICE_ADDRESS_KEY, _deviceAddress);
        edit.apply();
    }

    /** Returns the stored valid R-R intervals. */
    public RrIntervalList getIntervals() {

        return _intervals;
    }

    /** Called when connected to a device. */
    public void onConnected() {

        Log.d(TAG, "onHeartRateServiceConnected()");
        _connectionState = STATE_CONNECTED;
        resetIntervals();

        for (HeartRateServiceListener listener : _listeners)
            listener.onHeartRateServiceConnected();
    }

    /** Clears all the stored R-R intervals. */
    public void resetIntervals() {
        Log.d(TAG, "resetIntervals");
        _intervals = new RrIntervalList();
        _intervals.setListener(this);
        _errorCode = null;
    }

    /** Called when connecting to a device. */
    public void onConnecting(String address) {

        Log.d(TAG, "onHeartRateServiceConnecting(" + address + ")");
        _connectionState = STATE_CONNECTING;
        _deviceAddress = address;
        storeDeviceAddress();

        for (HeartRateServiceListener listener : _listeners)
            listener.onHeartRateServiceConnecting();
    }

    /** Called when diconnected from a device. */
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected()");

        _connectionState = STATE_DISCONNECTED;
        notifyCoherenceZone(false, false);

        if (!_inForeground) {

            String message;
            if (_errorCode != null) {

                message = getString(_errorCode);
            }
            else {

                message = getString(R.string.connection_lost);
            }

            notifyMessage(true, getString(R.string.disconnected), message);
        }

        for (HeartRateServiceListener listener : _listeners)
            listener.onHeartRateServiceDisconnected(_errorCode);

        resetIntervals();

        _batteryLevel = -1;
        _deviceModel = DEFAULT_DEVICE_MODEL;
        _deviceManufacturer = DEFAULT_DEVICE_MANUFACTURER;
    }

    /** Called when the battery level changes. */
    public void onBatteryLevelChanged(int newLevel) {

        Log.d(TAG, "onBatteryLevelChanged(" + newLevel + ")");
        _batteryLevel = newLevel;

        for (HeartRateServiceListener listener : _listeners) listener.onHeartRateServiceBatteryLevelChanged(newLevel);
    }

    /** Called when the device manufacturer is received. */
    public void onDeviceManufacturerRead(String manufacturer) {

        Log.d(TAG, "onDeviceManufacturerRead: " + manufacturer);
        _deviceManufacturer = manufacturer;
    }

    /** Called when the device model is received. */
    public void onDeviceModelRead(String model) {

        Log.d(TAG, "onDeviceModelRead: " + model);
        _deviceModel = model;
    }

    /** Called when new R-R intervals are received from the device. */
    public void onNewRRIntervals(long timestamp, ArrayList<Integer> rrIntervalList) {

        int dropped = 0;
        int added = 0;
        ArrayList<Long> timestamps = new ArrayList<Long>(rrIntervalList.size());
        for (int i = rrIntervalList.size() - 1; i >= 0 && !rrIntervalList.isEmpty(); i--) {

            Integer rrInterval = rrIntervalList.get(i);
            timestamps.add(0, timestamp);
            timestamp -= rrInterval;
        }
        for (int i = 0; i < rrIntervalList.size(); i++) {

            RrInterval rrInterval = new RrInterval(timestamps.get(i), rrIntervalList.get(i));
            if (_intervals.add(rrInterval)) {
                added++;
            }
        }

        if (_intervals.getDroppedInARow() >= MAX_DROPPED_IN_A_ROW) {

            disconnect(Integer.valueOf((R.string.unstable_connection)));
        }
        else {

            // Notifying _listeners
            if (added > 0)
                for (HeartRateServiceListener listener : _listeners)
                    listener.onHeartRateServiceNewRrIntervals(_intervals, added);
            if (dropped > 0)
                for (HeartRateServiceListener listener : _listeners)
                    listener.onHeartRateServiceDroppedIntervals(dropped);
        }
    }

    /** Publish a notification with the coherence zone. */
    private void notifyCoherenceZone(boolean notify, boolean playSound) {
        Log.d(TAG, "notifyCoherenceZone(" + notify + " ," + playSound + ")");

        NotificationManager mgr = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (_intervals != null) {

            CoherenceZone currentCoherenceZone = _intervals.getCoherenceZone();
            if (notify && currentCoherenceZone != null) {
                Notification.Builder builder = new Notification.Builder(getApplicationContext())
                        .setAutoCancel(true)
                        .setPriority(Notification.PRIORITY_DEFAULT)
                        .setSmallIcon(currentCoherenceZone.getIcon())
                        .setContentTitle(getApplicationContext().getString(R.string.coherence))
                        .setContentText(getString(currentCoherenceZone.getStringId()));

                if (playSound) {

                    Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    builder.setSound(alarmSound);
                }

                Intent noteIntent = new Intent(getApplicationContext(), MainActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, noteIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                builder.setContentIntent(pendingIntent);

                Notification notification = builder.build();
                notification.defaults = 0;
                mgr.notify(COHERENCE_NOTIFICATION_ID, notification);
            }
            else {

                mgr.cancel(COHERENCE_NOTIFICATION_ID);
            }
        }
    }

    /** Publish a notification with the specified message. */
    private void notifyMessage(boolean notify, String title, String message) {

        Log.d(TAG, "notifyMessage(" + notify + " ," + message + ")");

        NotificationManager mgr = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (notify && message != null) {

            Notification.Builder builder = new Notification.Builder(getApplicationContext())
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_DEFAULT)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(message);

            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            builder.setSound(alarmSound);

            Intent noteIntent = new Intent(getApplicationContext(), MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, noteIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            builder.setContentIntent(pendingIntent);

            Notification notification = builder.build();
            notification.defaults = 0;
            mgr.notify(MESSAGE_NOTIFICATION_ID, notification);
        }
        else {

            mgr.cancel(MESSAGE_NOTIFICATION_ID);
        }
    }

    /** Called when the coherence zone changes. */
    public void onCoherenceZoneChanged(CoherenceZone oldz, CoherenceZone newz) {

        notifyCoherenceZone(!_inForeground, oldz.getIndex() > newz.getIndex());
    }

    /**
     * Sets if the app is in foreground.
     * @param value True if the app is in foreground. False otherwise.
     */
    public void setInForeground(boolean value) {

        Log.d(TAG, "app in foreground: " + value);
        this._inForeground = value;
        notifyCoherenceZone(!_inForeground && _connectionState == STATE_CONNECTED, false);
    }

    /** Adds a new listener to be notified when new values are available. */
    public void addServiceListener(HeartRateServiceListener listener) {

        if (listener != null && !_listeners.contains(listener))
            _listeners.add(listener);
    }

    /** Removes the specified listener. */
    public void removeServiceListener(HeartRateServiceListener listener) {

        _listeners.remove(listener);
    }

    /** Called when the heart rate device scan stops. */
    public void onHeartRateDeviceScanStopped() {

        for (HeartRateServiceListener listener : _listeners)
            listener.onHeartRateServiceDeviceScanStopped();
    }

    /** Called when a new heart rate device is found while scanning. */
    public void onDeviceFound(HeartRateDevice device) {

        Log.d(TAG, "onDeviceFound");
        // Notifying Listeners
        for (HeartRateServiceListener listener : _listeners)
            listener.onHeartRateServiceDeviceFound(device);
    }

    /** Disconnects from the current device if connected or connecting. */
    public void disconnect() {

        disconnect(-1);
    }

    /** Reconnects to the last connected heart rate device. */
    public void reconnect() {
        Log.d(TAG, "reconnect");
        if (reconnectAvailable())
            connect(_deviceAddress);
        else
            Log.e(TAG, "Invalid address");
    }

    /** Returns true if it's possible to reconnect. False otherwise. */
    public boolean reconnectAvailable() {

        return _deviceAddress != null && !_deviceAddress.isEmpty();
    }

    /** Retrieves the last connected device address and tries to connect to it if available. */
    public boolean initialize() {

        retrieveDeviceAddress();
        if (_deviceAddress != null && _deviceAddress.length() > 0) {

            Log.d(TAG, "connecting from initialize()");
            connect(_deviceAddress);
        }

        return true;
    }

    /** Removes the posted notifications. */
    public void close(){

        Log.d(TAG, "close");
        notifyCoherenceZone(false, false);
    }

    /** Returns the current heart rate device battery level. */
    public int getBatteryLevel() {

        return _batteryLevel;
    }

    /**
     * Device Manufacturer
     * @return Current device manufacturer or DEFAULT_DEVICE_MANUFACTURER if not set
     */
    public String getDeviceManufacturer() {

        return _deviceManufacturer;
    }

    /**
     * Device Model
     * @return Current device model or DEFAULT_DEVICE_MODEL if not set
     */
    public String getDeviceModel() {

        return _deviceModel;
    }

    /** Disconnects from the current device if connected or connecting.
     * @param errorCode Error message id to be displayed in the app
     */
    public void disconnect(int errorCode) {

    }

    /** Starts scanning for heart rate devices. */
    public abstract void startScanningDevices();

    /** Stops scanning for heart rate devices. */
    public abstract void stopScanningDevices();

    /** Returns true if currently scanning for heart rate devices. */
    public abstract boolean isScanning();

    /**
     * Connects to a heart rate device.
     * @param address Heart rate device address to connect to.
     * @return Returns true if the connection is initiated successfully
     */
    public abstract boolean connect(final String address);
}