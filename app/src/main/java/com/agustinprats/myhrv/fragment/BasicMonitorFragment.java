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

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.agustinprats.myhrv.R;
import com.agustinprats.myhrv.model.CoherenceZone;
import com.agustinprats.myhrv.model.RrIntervalList;
import com.agustinprats.myhrv.service.HeartRateService;
import com.agustinprats.myhrv.util.Utils;
import com.agustinprats.myhrv.view.ProgressWheel;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

/**
 * Implements a basic MonitorFragment that displays heart rate from all R-R intervals and heart coherence
 * in a moving wheel.
 */
public class BasicMonitorFragment extends MonitorFragment {

    private final static String TAG = BasicMonitorFragment.class.toString();

    /** SharedPreferences key used to save if the instructions are visible. */
    public static final String INSTRUCTIONS_VISIBLE_KEY = "pref_instructions_visible_key";

    // Glass and wheel constants
    public static final int GLASS_FADIN_DURATION = 350;
    public static final float GLASS_FADIN_INIT = 0.75f;
    private final static int WHEEL_MIN = 0;
    private final static int WHEEL_MAX = 360;
    private final static int CHART_SPAN = 20000;

    // UI
    private ImageView _glassImage;
    private int _glassImageId;
    private ProgressWheel _wheel;
    private TextView _instructionsText;
    private LinearLayout _chartLayout;
    private TextView _zoneText;
    private RelativeLayout _instructionsLayout;
    protected TextView _heartRateText;

    // Task to update the wheel periodically in the background
    private AsyncTask<Integer, Integer, Void> _updateWheelProgressTask = null;

    // Charts
    private GraphicalView _chart;
    private XYMultipleSeriesRenderer _renderer;

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // UI Init
        View rootView = inflater.inflate(R.layout.fragment_basic_monitor, container, false);

        super.initUI(rootView);

        _zoneText = (TextView) rootView.findViewById(R.id.zoneText);
        _heartRateText = (TextView) rootView.findViewById(R.id.heartRateText);
        _wheel = (ProgressWheel) rootView.findViewById(R.id.progressWheel);
        _instructionsText = (TextView) rootView.findViewById(R.id.instructionsText);
        _instructionsLayout = (RelativeLayout) rootView.findViewById(R.id.instructionsLayout);
        _chartLayout = (LinearLayout) rootView.findViewById(R.id.chart);
        _glassImage = (ImageView) rootView.findViewById(R.id.glassImage);
        _glassImageId = -1;
        _heartRateText.setTypeface(_digitalTypeface);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getMainActivity().getApplicationContext());
        Boolean instructionsVisible = sharedPref.getBoolean(INSTRUCTIONS_VISIBLE_KEY, true);
        if (instructionsVisible) {

            _instructionsLayout.setVisibility(View.VISIBLE);
        }
        else {

            _instructionsLayout.setVisibility(View.GONE);
        }

        return rootView;
    }

    /** Inits the charts that displays heart rate. */
    private void initChart() {

        Log.d(TAG, "initChart");
        removeChart();
        _renderer = newTimeRenderer();

        HeartRateService service = getMainActivity().getHeartRateService();
        XYMultipleSeriesDataset heartRateDataset = new XYMultipleSeriesDataset();
        heartRateDataset.addSeries(service.getIntervals().getHeartRateSeries());

        _chart = ChartFactory.getCubeLineChartView(getMainActivity(), heartRateDataset, _renderer, 0.2f);
        _chartLayout.addView(_chart);

        updateCoherenceZone();

        updateChart();
    }

    /** Creates a new time renderer for the heart rate chart. */
    private XYMultipleSeriesRenderer newTimeRenderer() {

        XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();

        // Line 1
        XYSeriesRenderer lineRenderer;
        lineRenderer = new XYSeriesRenderer();
        lineRenderer.setPointStyle(PointStyle.CIRCLE);
        lineRenderer.setFillPoints(true);
        lineRenderer.setColor(Color.WHITE);
        lineRenderer.setLineWidth(3.0f);
        renderer.addSeriesRenderer(lineRenderer);

        renderer.setMargins(new int[]{0, 0, 0, 0});
        renderer.setPointSize(0.5f);
        renderer.setBackgroundColor(Color.TRANSPARENT);
        renderer.setMarginsColor(Color.argb(0x00, 0x01, 0x01, 0x01));
        renderer.setLabelsColor(Color.TRANSPARENT);
        renderer.setXLabelsColor(Color.TRANSPARENT);
        renderer.setYLabelsColor(0, Color.WHITE);
        renderer.setLabelsTextSize(20.0f);
        renderer.setYLabelsAlign(Paint.Align.LEFT);
        renderer.setShowLegend(false);
        renderer.setAxesColor(Color.TRANSPARENT);
        renderer.setClickEnabled(false);
        renderer.setPanEnabled(false);
        renderer.setZoomEnabled(false, false);
        return renderer;
    }

    /** Removes the chart that displays the heart rate chart. */
    private void removeChart() {

        if (_chart != null && _chartLayout.indexOfChild(_chart) >= 0) {

            _chartLayout.removeView(_chart);
        }
    }

    /** Updates the chart with new R-R intervals. */
    private void updateChart() {

        if (isChartVisible()) {

            long minX = System.currentTimeMillis() - CHART_SPAN;
            if (minX < 0)
                minX = 0;
            _renderer.setXAxisMin(minX);

            HeartRateService service = getMainActivity().getHeartRateService();
            RrIntervalList list = service.getIntervals();
            _renderer.setYAxisMax(list.getInstantMaxHeartRate());
            _renderer.setYAxisMin(list.getInstantMinHeartRate());
            _chart.repaint();

            updateCoherenceZone();
        }
        else {

            // Charts
            initChart();
        }
    }

    /** Updates the displayed heart coherence. */
    private void updateCoherenceZone() {

        HeartRateService service = getMainActivity().getHeartRateService();
        CoherenceZone coherenceZone = service.getIntervals().getCoherenceZone();
        if (coherenceZone != null) {

            updateGlass(coherenceZone.getGlassImage());
            _zoneText.setText(getString(coherenceZone.getStringId()) + " " + getString(R.string.coherence));
        }
        else {

            _zoneText.setText(R.string.no_value);
        }
    }

    private boolean isChartVisible() {

        return _chartLayout.indexOfChild(_chart) >= 0;
    }

    /**
     * This callback is executed when  the connection status changes to connected
     */
    @Override
    protected void onStatusConnected() {
        super.onStatusConnected();
        _instructionsText.setText(getMainActivity().getString(R.string.instructions_connected));
        resetWheels();
        removeChart();
        updateHeartRate();
        updateWheel();
        updateChart();
    }

    /**
     * This callback is executed when the connection status changes to connecting
     */
    @Override
    protected void onStatusConnecting() {
        super.onStatusConnecting();
        setHeartRate(0);
        _instructionsText.setText(getMainActivity().getString(R.string.instructions_connecting));
        updateGlass(R.drawable.glass_gray);
        _wheel.spin();
        removeChart();
        _zoneText.setText(R.string.no_value);
    }

    /**
     * This callback is executed when the connection status changes to disconnected
     */
    @Override
    protected void onStatusDisconnected() {
        super.onStatusDisconnected();
        setHeartRate(0);
        _instructionsText.setText(getMainActivity().getString(R.string.instructions_disconnected));
        _zoneText.setText(R.string.no_value);
        updateGlass(R.drawable.glass_gray);
        resetWheels();
        removeChart();
    }

    /** Changes the heart rate text. */
    public void setHeartRate(double heartRate) {

        if (heartRate <= 0)
            _heartRateText.setText(Utils.getPaddedString(getMainActivity().getString(R.string.no_heart_rate), 2));
        else
            _heartRateText.setText(Utils.getPaddedString(String.valueOf((int) heartRate), 2));
    }

    /** Resets the coherence wheel. */
    private void resetWheels() {

        _wheel.setProgress(WHEEL_MIN);
    }

    /** Updates the coherence glass. */
    private void updateGlass(final int glassImageId) {

        if (_glassImageId != glassImageId) {

            _glassImageId = glassImageId;
            _glassImage.setImageResource(glassImageId);
            AlphaAnimation fadeIn = new AlphaAnimation(GLASS_FADIN_INIT, 1.0f);
            fadeIn.setDuration(GLASS_FADIN_DURATION);
            _glassImage.startAnimation(fadeIn);
        }
    }

    /** Called when new RR intervals are received
     *  @intervals All intervals received while the device's been connected
     *  @count Number of RR intervals that are new. They can be found at the end of the list */
    @Override
    public void onHeartRateServiceNewRrIntervals(final RrIntervalList intervals, final int count) {

        super.onHeartRateServiceNewRrIntervals(intervals, count);
        if (isAdded() && _inForeground)
            getMainActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    updateHeartRate();
                    updateWheel();
                    updateChart();
                }
            });
    }

    /** Updates the heart rate text from the heart rate service. */
    public void updateHeartRate() {

        HeartRateService service = getMainActivity().getHeartRateService();
        RrIntervalList list = service.getIntervals();
        setHeartRate(list.getInstantHeartRate());
    }

    /** Updates the coherence wheel from from heart rate service. */
    public void updateWheel() {

        HeartRateService service = getMainActivity().getHeartRateService();
        RrIntervalList list = service.getIntervals();

        int progress = getWheelProgress(list.getInstantCoherence());
        setWheelProgress(progress);
    }

    /** Converts a percentage to a wheel progress value. */
    private int getWheelProgress(double percentage) {

        return WHEEL_MIN + (int) ((percentage * (WHEEL_MAX - WHEEL_MIN)) / 100);
    }

    /** Changes the coherence wheel progress. */
    private void setWheelProgress(int progress) {

        if (_updateWheelProgressTask == null) {

            _updateWheelProgressTask = new AsyncTask<Integer, Integer, Void>() {

                private final static int PERIOD = 50;
                private final static int DURATION = 500;
                private final static int ITERATIONS = DURATION / PERIOD;
                private final static int MIN_STEP = 1;

                @Override
                protected Void doInBackground(Integer... finalProgress) {

                    final int initialProgress = _wheel.getProgress();
                    final int step = Math.abs(finalProgress[0] - initialProgress) / ITERATIONS;

                    if (step >= MIN_STEP) {

                        if (finalProgress[0] > initialProgress) {

                            for (int i = initialProgress; i < finalProgress[0] && !isCancelled(); i += step) {

                                publishProgress(i);
                                sleep();
                            }
                        } else if (finalProgress[0] < initialProgress) {

                            for (int i = initialProgress; i > finalProgress[0] && !isCancelled(); i -= step) {

                                publishProgress(i);
                                sleep();
                            }
                        }
                    }
                    _updateWheelProgressTask = null;
                    return null;
                }

                private void sleep() {

                    try {
                        Thread.sleep(PERIOD);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Exception sleeping update wheel progress task");
                    }
                }

                @Override
                protected void onProgressUpdate(Integer... progress) {

                    _wheel.setProgress(progress[0]);
                }
            };
            _updateWheelProgressTask.execute(progress);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        super.onOptionsItemSelected(item);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getMainActivity().getApplicationContext());
        switch (item.getItemId()) {
            case R.id.menu_show_instructions:

                _instructionsLayout.setVisibility(View.VISIBLE);
                sharedPref.edit().putBoolean(INSTRUCTIONS_VISIBLE_KEY, true).commit();
                getMainActivity().invalidateOptionsMenu();
                break;
            case R.id.menu_hide_instructions:

                _instructionsLayout.setVisibility(View.GONE);
                sharedPref.edit().putBoolean(INSTRUCTIONS_VISIBLE_KEY, false).commit();
                getMainActivity().invalidateOptionsMenu();
                break;
        }
        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        super.onCreateOptionsMenu(menu, inflater);
        if (_instructionsLayout.getVisibility() == View.VISIBLE) {

            menu.findItem(R.id.menu_show_instructions).setVisible(false);
            menu.findItem(R.id.menu_hide_instructions).setVisible(true);
        }
        else {

            menu.findItem(R.id.menu_show_instructions).setVisible(true);
            menu.findItem(R.id.menu_hide_instructions).setVisible(false);
        }
    }
}
