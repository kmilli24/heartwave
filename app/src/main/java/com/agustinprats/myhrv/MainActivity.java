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

package com.agustinprats.myhrv;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.agustinprats.myhrv.fragment.BasicMonitorFragment;
import com.agustinprats.myhrv.fragment.MonitorFragment;
import com.agustinprats.myhrv.service.BleHeartRateService;
import com.agustinprats.myhrv.service.HeartRateService;


public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    /** Request code for requesting enabling bluetooth. */
    private static final int REQUEST_ENABLE_BT = 1;

    /** SharedPreferences key that holds the selected theme. */
    private static final String LIGHT_THEME_KEY = "light_theme_key";

    /** Boolean storing if the activity is in the foreground. */
    private boolean _inForeground = false;

    /** Service that connects in the background to a heart rate device and notifies its changes. */
    private HeartRateService _heartRateService = null;

    /** Fragment that shows the values notified by the heart rate device. */
    private MonitorFragment _monitorFragment = null;


    /** Manages the connection to the heart rate device service. */
    private final ServiceConnection _heartRateServiceConn = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            // Init service
            _heartRateService = ((HeartRateService.LocalBinder) service).getService();
            _heartRateService.setInForeground(MainActivity.this._inForeground);

            // Check if bluetooth is enabled
            boolean bleInit = _heartRateService.initialize();
            if (!bleInit) {

                showUnableToInitBleDialog();
            }

            // notify service is ready
            onHeartRateServiceBinded(_heartRateService);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

            // notify service inbinded
            onHeartRateServiceUnbinded(_heartRateService);
        }
    };

    /** Called when the service is binded to the activity */
    public void onHeartRateServiceBinded(final HeartRateService service) {

        _monitorFragment.onHeartRateServiceBinded(service);
    }

    /** Called when the service is unbinded from the activity */
    public void onHeartRateServiceUnbinded(final HeartRateService service) {

        _heartRateService = null;
        _monitorFragment.onHeartRateServiceUnbinded(service);
    }

    /** Shows unable to init BLE message and exit app. */
    private void showUnableToInitBleDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage(R.string.unable_init_ble);
        builder.setPositiveButton(R.string.exit, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

                finish();
            }
        });
        builder.create().show();
    }

    /** Shows BLE is not supported and exit app. */
    public void showBleRequireDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage(R.string.ble_required)
                .setNeutralButton(R.string.exit,  new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        finish();
                    }
                }).create().show();
    }

    /** Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
     *  fire an intent to display a dialog asking the user to grant permission to enable it. */
    public void requestEnableBt() {

        if (!isBluetoothEnabled()) {

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    /** Returns true if bluetooth is enabled. False otherwise. */
    public boolean isBluetoothEnabled() {

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        return bluetoothManager.getAdapter().isEnabled();
    }

    @Override
    public void onPause() {
        super.onPause();

        _inForeground = false;
        if (_heartRateService != null) {

            _heartRateService.setInForeground(_inForeground);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        _inForeground = true;
        if (_heartRateService != null) {

            _heartRateService.setInForeground(_inForeground);
        }

        // If the device doesn't support BLE show message and exit
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {

            showBleRequireDialog();
        }
    }

    @Override
    public  void onDestroy() {

        if (_heartRateServiceConn != null) {

            unbindService(_heartRateServiceConn);
            _heartRateService = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            Log.d(TAG, "User declined to enable BT: closing app");
            finish();
        }
        else if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Bluetooth enabled");

            _heartRateService.reconnect();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set previously saved theme
        setTheme(isLightTheme() ? R.style.LightTheme : R.style.DarkTheme);

        setContentView(R.layout.activity_main);

        // Connect to Bluetooth service
        Intent serviceIntent = new Intent(this, BleHeartRateService.class);
        bindService(serviceIntent, _heartRateServiceConn, BIND_AUTO_CREATE);
        Log.d(TAG, "binding with BleHeartRateService service");

        // Init monitor fragment
        if (savedInstanceState == null) {
            _monitorFragment = new BasicMonitorFragment();
            getFragmentManager().beginTransaction()
                    .add(R.id.container, _monitorFragment)
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);

        boolean lightTheme = isLightTheme();
        menu.findItem(R.id.menu_dark_theme).setVisible(lightTheme);
        menu.findItem(R.id.menu_light_theme).setVisible(!lightTheme);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Handle action buttons
        switch(item.getItemId()) {

            case R.id.menu_light_theme:
                setLightTheme(true);
                return true;
            case R.id.menu_dark_theme:
                setLightTheme(false);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** Returns the heart rate service. */
    public HeartRateService getHeartRateService() {

        return _heartRateService;
    }

    /** Changes the theme to light if passed a true value or to dark theme if passed a false value. */
    private void setLightTheme(boolean lightTheme) {

        // Save to preferences
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        sharedPrefs.edit().putBoolean(LIGHT_THEME_KEY, lightTheme).commit();

        // Restart activity
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    /** Returns true if light theme was previously selected. False otherwise. */
    public boolean isLightTheme() {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return sharedPrefs.getBoolean(LIGHT_THEME_KEY, true);
    }
}
