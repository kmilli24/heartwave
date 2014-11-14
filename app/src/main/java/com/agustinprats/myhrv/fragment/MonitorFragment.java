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

package com.agustinprats.myhrv.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.agustinprats.myhrv.MainActivity;
import com.agustinprats.myhrv.R;
import com.agustinprats.myhrv.adapter.HeartRateDeviceListAdapter;
import com.agustinprats.myhrv.model.HeartRateDevice;
import com.agustinprats.myhrv.model.RrIntervalList;
import com.agustinprats.myhrv.service.HeartRateService;
import com.agustinprats.myhrv.service.HeartRateServiceListener;
import com.agustinprats.myhrv.util.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Base Fragment used to display the common things in a heart rate variability monitor.
 */
public abstract class MonitorFragment extends Fragment implements HeartRateServiceListener {

    private final static String TAG = MonitorFragment.class.toString();

    // Connection status to the heart rate monitor
    private final static int STATUS_DISCONNECTED = 0;
    private final static int STATUS_CONNECTING = 1;
    private final static int STATUS_CONNECTED = 2;
    private int status = 0;

    // Heart Beat Animation
    private final static float BEATUP_SCALE = 1f;
    private final static float BEATDOWN_SCALE = 1.3f;
    private final static int BEATUP_MS = 80;
    private final static float BEATUP_ALPHA = 1f;
    private final static float BEATDOWN_ALPHA = 0.75f;
    private final static int BEATDOWN_MS = 160;
    final Animation _beatUp = new ScaleAnimation(BEATUP_SCALE, BEATDOWN_SCALE, BEATUP_SCALE, BEATDOWN_SCALE, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
    final Animation _beatDown = new ScaleAnimation(BEATDOWN_SCALE, BEATUP_SCALE, BEATDOWN_SCALE, BEATUP_SCALE, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
    boolean _isHeartBeating = false;

    // UI
    protected ImageView _heartImage;
    protected TextView _statusText;
    protected TextView _searchingDevicesText = null;
    protected HeartRateDeviceListAdapter _heartRateDeviceListAdapter;
    protected TextView _timeText;
    protected boolean _inForeground = false;
    protected Typeface _digitalTypeface;
    protected ImageView _batteryLevelImage;
    protected boolean _lightBattery = false;

    /** This method should be called when the fragment is initialised in the onCreate */
    protected void initUI(View rootView) {

        _heartImage = (ImageView) rootView.findViewById(R.id.heartImage);
        _statusText = (TextView) rootView.findViewById(R.id.statusText);
        _timeText = (TextView) rootView.findViewById(R.id.timeText);
        _batteryLevelImage = (ImageView) rootView.findViewById(R.id.batteryLevelImage);
        _digitalTypeface = Typeface.createFromAsset(getMainActivity().getAssets(), "fonts/digital7.ttf");
        _timeText.setTypeface(_digitalTypeface);
        _heartImage.setAlpha(BEATDOWN_ALPHA);

        setHasOptionsMenu(true);
    }

    /** Sets the connection status of the hrv monitor.
     * It will call the connection callbacks.
     * See @onStatusDisconnected, @onStatusConnecting, @onStatusConnected
     */
    public void setStatus(final int status) {

        if (isAdded() && getMainActivity() != null) {

            getMainActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    if (isAdded() && getMainActivity() != null) {
                        Log.d(TAG, "setStatus=" + status);

                        switch (status) {
                            case STATUS_DISCONNECTED:
                                onStatusDisconnected();
                                break;
                            case STATUS_CONNECTING:
                                onStatusConnecting();
                                break;
                            case STATUS_CONNECTED:
                                onStatusConnected();
                                break;
                        }

                        getMainActivity().invalidateOptionsMenu();
                    }
                    MonitorFragment.this.status = status;
                }
            });
        }
    }

    /** Returns the connection status to the hrv monitor */
    public int getStatus() {

        return status;
    }

    /** This callback is executed when  the connection status changes to connected */
    protected void onStatusConnected() {
        Log.d(TAG, "onStatusConnected");
        _statusText.setText(getMainActivity().getString(R.string.status_connected));
        onHeartRateServiceBatteryLevelChanged(getMainActivity().getHeartRateService().getBatteryLevel());

    }

    /** This callback is executed when the connection status changes to connecting */
    protected void onStatusConnecting() {
        Log.d(TAG, "onStatusConnecting");
        _statusText.setText(getMainActivity().getString(R.string.status_connecting));
        _timeText.setText("00:00");
        _timeText.setText(R.string.no_value);
        _batteryLevelImage.setVisibility(View.INVISIBLE);
    }

    /** This callback is executed when the connection status changes to disconnected */
    protected void onStatusDisconnected() {
        Log.d(TAG, "onStatusDisconnected");
        _statusText.setText(getMainActivity().getString(R.string.status_disconnected));
        _timeText.setText("00:00");
        _batteryLevelImage.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        _inForeground = true;

        getMainActivity().requestEnableBt();
        getMainActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        updateStatus();

        if (getMainActivity().getHeartRateService() != null)
            getMainActivity().getHeartRateService().addServiceListener(this);
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();

        _inForeground = false;

        getMainActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        _heartImage.clearAnimation();
        _isHeartBeating = false;

        HeartRateService service = getMainActivity().getHeartRateService();
        if (service != null)
            service.removeServiceListener(this);
    }

    /** This method will set the connection status in the fragment
     *  based on the connection status of the @HeartRateService */
    private void updateStatus() {

        HeartRateService service = getMainActivity().getHeartRateService();

        if (service == null || service.isDisconnected()) {

            setStatus(STATUS_DISCONNECTED);
        }
        else if (service.isConnecting()) {

            setStatus(STATUS_CONNECTING);
        }
        else if (service.isConnected()) {

            setStatus(STATUS_CONNECTED);
        }
    }

    /** Starts a single heart beat animation */
    public void heartBeat() {

        if (!_isHeartBeating && isAdded()) {

            // Start heart beat
            _beatUp.setDuration(BEATUP_MS);
            _beatDown.setDuration(BEATDOWN_MS);
            Animation.AnimationListener beatingListener = new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationRepeat(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {

                    if (animation == _beatUp) {

                        _heartImage.startAnimation(_beatDown);
                    }
                    else if (animation == _beatDown) {

                        _heartImage.setAlpha(BEATDOWN_ALPHA);
                        _isHeartBeating = false;
                    }
                }
            };
            _beatUp.setAnimationListener(beatingListener);
            _beatDown.setAnimationListener(beatingListener);
            _heartImage.startAnimation(_beatUp);
            _heartImage.setAlpha(BEATUP_ALPHA);
            _isHeartBeating = true;
        }
    }

    /** Shows the connect dialog displaying a list of available devices to connect */
    private void showConnectDialog() {

        LayoutInflater inflater = getMainActivity().getLayoutInflater();

        // Init view
        RelativeLayout devicesDialogView = (RelativeLayout) inflater.inflate(R.layout.devices_dialog, null);

        AlertDialog.Builder devicesDialogBuilder = new AlertDialog.Builder(getMainActivity());
        devicesDialogBuilder.setTitle(R.string.devices_available);
        devicesDialogBuilder.setView(devicesDialogView);
        final AlertDialog devicesDialog = devicesDialogBuilder.create();

        _searchingDevicesText = (TextView) devicesDialogView.findViewById(R.id.searchingText);

        ListView listView = (ListView) devicesDialogView.findViewById(R.id.listView);
        listView.setEmptyView(_searchingDevicesText);
        final HeartRateDeviceListAdapter mHeartRateDeviceListAdapter = new HeartRateDeviceListAdapter(inflater);
        _heartRateDeviceListAdapter = mHeartRateDeviceListAdapter;

        final HeartRateService service = getMainActivity().getHeartRateService();
        service.startScanningDevices();
        listView.setAdapter(mHeartRateDeviceListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                final HeartRateDevice device = mHeartRateDeviceListAdapter.getDevice(position);
                if (device != null) {

                    if (service.isScanning())
                        service.stopScanningDevices();

                    Log.d(TAG, "connecting from devices dialog");
                    service.connect(device.getAddress());
                }
                devicesDialog.dismiss();
            }
        });

        devicesDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {

                service.stopScanningDevices();
                _heartRateDeviceListAdapter = null;
            }
        });

        devicesDialog.show();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        final HeartRateService service = getMainActivity().getHeartRateService();
        inflater.inflate(R.menu.monitor, menu);
        menu.findItem(R.id.menu_reconnect).setVisible(service != null && service.isDisconnected() && service.reconnectAvailable());
        menu.findItem(R.id.menu_scan).setVisible(service != null && service.isDisconnected());
        menu.findItem(R.id.menu_disconnect).setVisible(service != null && !service.isDisconnected());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        final HeartRateService service = getMainActivity().getHeartRateService();

        switch (item.getItemId()) {
            case R.id.menu_reconnect:

                service.reconnect();
                break;
            case R.id.menu_scan:

                showConnectDialog();
                break;
            case R.id.menu_disconnect:

                service.disconnect();
                service.stopScanningDevices();
                break;
            case R.id.menu_help:

                showHelp();
                break;
        }
        return true;
    }

    /** Displays help to the user. */
    private void showHelp() {

        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
        alert.setTitle(getActivity().getString(R.string.Help));

        WebView wv = new WebView(getActivity());

        // loading the html file
        String htmlData = readAsset("help.html");

        // selecting the css based on the activity theme
        htmlData = htmlData.replaceFirst("CSS_FILE_NAME", ((MainActivity) getActivity()).isLightTheme() ? "light" : "dark");

        // loading html in the webview
        wv.loadDataWithBaseURL("file:///android_asset/", htmlData, "text/html", "UTF-8", null);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        alert.setView(wv);
        alert.setNegativeButton(getActivity().getString(R.string.Close), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        alert.show();
    }

    private String readAsset(String name) {

        StringBuilder buf = new StringBuilder();
        try {
            InputStream json = getMainActivity().getAssets().open(name);
            BufferedReader in = new BufferedReader(new InputStreamReader(json, "UTF-8"));
            String str;
            while ((str = in.readLine()) != null) {
                buf.append(str);
            }
            in.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return buf.toString();
    }

    /** Starts so many heart beat animations as specified in the parameter @count.
     *  Each animation duration is based on the RR intervals
     *  passed in the @intervals parameter */
    private void heartBeats(RrIntervalList intervals, int count) {

        heartBeat();
        for (int i = intervals.size() - count; i < intervals.size(); i++) {

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {

                    heartBeat();
                }
            }, intervals.get(i).getRRInterval());
        }
    }

    //--------- HeartRateService Listener Implementation

    /** Called when connects to a HeartRateDevice */
    public void onHeartRateServiceConnected() {

        setBatteryLevel(getMainActivity().getHeartRateService().getBatteryLevel());
    }

    /** Called when it's connecting to a HeartRateDevice */
    public void onHeartRateServiceConnecting() {

        if (isAdded() && _inForeground)
            getMainActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    setStatus(STATUS_CONNECTING);
                }
            });
    }

    /** Called when disconnect from a HeartRateDevice */
    public void onHeartRateServiceDisconnected(final Integer errorCode) {

        if (isAdded() && _inForeground)
            getMainActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    setStatus(STATUS_DISCONNECTED);

                    MainActivity mainActivity = getMainActivity();
                    if (errorCode != null && mainActivity != null) {

                        if (!mainActivity.isDestroyed() || !mainActivity.isFinishing()) {

                            AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
                            builder.setTitle(R.string.disconnected);
                            builder.setMessage(getString(errorCode));
                            AlertDialog dialog = builder.create();
                            dialog.show();
                        }
                    }
                }
            });
    }

    /** Sets a new battery level. */
    private void setBatteryLevel(final int newLevel) {

        if (isAdded() && _inForeground)
            getMainActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    if (newLevel < 0) {

                        _batteryLevelImage.setVisibility(View.INVISIBLE);
                    }
                    else if (newLevel < 25) {

                        if (_lightBattery)
                            _batteryLevelImage.setImageResource(R.drawable.battery_0_light);
                        else
                            _batteryLevelImage.setImageResource(R.drawable.battery_0_dark);
                    }
                    else if (newLevel < 50) {

                        if (_lightBattery)
                            _batteryLevelImage.setImageResource(R.drawable.battery_25_light);
                        else
                            _batteryLevelImage.setImageResource(R.drawable.battery_25_dark);
                    }
                    else if (newLevel < 75) {

                        if (_lightBattery)
                            _batteryLevelImage.setImageResource(R.drawable.battery_50_light);
                        else
                            _batteryLevelImage.setImageResource(R.drawable.battery_50_dark);
                    }
                    else if (newLevel < 90) {

                        if (_lightBattery)
                            _batteryLevelImage.setImageResource(R.drawable.battery_75_light);
                        else
                            _batteryLevelImage.setImageResource(R.drawable.battery_75_dark);
                    }
                    else {

                        if (_lightBattery)
                            _batteryLevelImage.setImageResource(R.drawable.battery_100_light);
                        else
                            _batteryLevelImage.setImageResource(R.drawable.battery_100_dark);
                    }
                    _batteryLevelImage.setVisibility(View.VISIBLE);
                }
            });
    }

    /** Called when
     * the battery level is received from a HeartRateDevice */
    public void onHeartRateServiceBatteryLevelChanged(final int newLevel) {

        setBatteryLevel(newLevel);
    }

    /** Called when new RR intervals are received
     *  @intervals All intervals received while the device's been connected
     *  @count Number of RR intervals that are new. They can be found at the end of the list */
    public void onHeartRateServiceNewRrIntervals(final RrIntervalList intervals, final int count) {

        if (isAdded() && _inForeground)
            getMainActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    if (getStatus() == STATUS_DISCONNECTED || getStatus() == STATUS_CONNECTING) {
                        setStatus(STATUS_CONNECTED);
                    }
                    heartBeats(intervals, count);
                    _timeText.setText(Utils.getDigitalDuration(intervals.getMeasuredTime()/1000));
                }
            });
    }

    /** Called when intervals are dropped because
     *  are out of range of because changed to fast */
    public void onHeartRateServiceDroppedIntervals(final int count) {

        if (isAdded() && _inForeground)
            getMainActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {


                }
            });
    }

    /** Called when a new @HeartRateDevice is found while scanning */
    public void onHeartRateServiceDeviceFound(final HeartRateDevice device) {

        if (isAdded() && _inForeground)
            getMainActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    if (isAdded() && getMainActivity() != null && _heartRateDeviceListAdapter != null) {

                        _heartRateDeviceListAdapter.addDevice(device);
                    }
                }
            });
    }

    /** Called when stopped scanning for new devices */
    public void onHeartRateServiceDeviceScanStopped() {

        if (isAdded() && _inForeground)
            getMainActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    if (_searchingDevicesText != null) {

                        _searchingDevicesText.setText("No devices found.");
                    }
                }
            });
    }

    /** Called when the service is binded to the activity */
    public void onHeartRateServiceBinded(final HeartRateService service) {

        service.addServiceListener(MonitorFragment.this);
        updateStatus();
    }

    /** Called when the service is unbinded from the activity */
    public void onHeartRateServiceUnbinded(final HeartRateService service) {

        service.removeServiceListener(MonitorFragment.this);
    }

    public MainActivity getMainActivity() {

        Activity activity = getActivity();
        if (activity != null && activity instanceof MainActivity) {
            return (MainActivity) activity;
        }
        return null;
    }
}
