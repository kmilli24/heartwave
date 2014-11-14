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

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.agustinprats.myhrv.model.HeartRateDevice;
import com.agustinprats.myhrv.util.Utils;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Service that mocks a @HeartRateDevice connection for UI debugging and testing purposes
 */
public class MockHeartRateService extends HeartRateService {

    private final static String TAG = MockHeartRateService.class.getSimpleName();
    private final static String DEVICE_ADDRESS_KEY = "mock_device_address_key";
    private static final int MIN_RR_INTERVAL = 900;
    private static final int MAX_RR_INTERVAL = 1200;

    private boolean _scanning = false;
    private Timer _timer = null;
    private long _lastTimestamp = 0;

    @Override
    public void onConnected() {
        Log.d(TAG, "onConnected");
        super.onConnected();

        onBatteryLevelChanged(Utils.getRandom(0, 100));
        scheduleTimerTask();
    }

    private void scheduleTimerTask() {

        if (_timer != null)
            _timer.cancel();

        _timer = new Timer();
        _timer.schedule(new TimerTask() {

            @Override
            public void run() {

                if (_timer != null) {

                    Log.d(TAG, "timerTask");
                    long now = System.currentTimeMillis();

                    if (_lastTimestamp > 0) {

                        ArrayList<Integer> intervals = new ArrayList<Integer>();
                        intervals.add((int) (now - _lastTimestamp));
                        onNewRRIntervals(now, intervals);
                    }

                    _lastTimestamp = now;

                    if (_timer != null)
                        scheduleTimerTask();
                }
            }
        }, Utils.getRandom(MIN_RR_INTERVAL, MAX_RR_INTERVAL));
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected");
        super.onDisconnected();
        resetIntervals();
        _lastTimestamp = 0;
    }

    @Override
    public boolean initialize() {
        super.initialize();
        return true;
    }

    @Override
    public boolean connect(final String address) {
        Log.d(TAG, "connect(" + address + ")");

        onConnecting(address);
        onConnected();

        return true;
    }

    @Override
    public void onConnecting(String address) {
        Log.d(TAG, "onConnecting");
        super.onConnecting(address);

        saveDeviceAddress(address);
    }

    private void saveDeviceAddress(String address) {
        Log.d(TAG, "Saving device address = " + address);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putString(DEVICE_ADDRESS_KEY, address);
        edit.commit();
    }

    @Override
    public void disconnect() {
        super.disconnect();
        Log.d(TAG, "disconnect");

        if (_timer != null) {
            Log.d(TAG, "Canceling Timer");
            _timer.cancel();
            _timer = null;
        }

        onDisconnected();
    }

    @Override
    public void close() {
        super.close();
        Log.d(TAG, "close");

        disconnect();
    }

    public void startScanningDevices() {

        onDeviceFound(new HeartRateDevice("Mock Device 1", "550e8400-e29b-41d4-a716-446655440000"));
        onDeviceFound(new HeartRateDevice("Mock Device 2", "550e8400-e29b-41d4-a716-446655440001"));
        onDeviceFound(new HeartRateDevice("Mock Device 3", "550e8400-e29b-41d4-a716-446655440002"));
        onDeviceFound(new HeartRateDevice("Mock Device 4", "550e8400-e29b-41d4-a716-446655440003"));

        stopScanningDevices();
    }

    public void stopScanningDevices() {

        if (_scanning) {

            _scanning = false;
        }

        onHeartRateDeviceScanStopped();
    }

    public boolean isScanning() {

        return _scanning;
    }
}
