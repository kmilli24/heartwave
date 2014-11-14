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

package com.agustinprats.myhrv.model;

import android.util.Log;

import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYSeries;
import java.util.ArrayList;

/**
 * Class that stores a list of R-R intervals and calculates coherence and hrv scores.
 */
public class RrIntervalList {

    private final static String TAG = RrIntervalList.class.toString();

    /** Number of milliseconds used to calculate values */
    private final static int TIME_WINDOW = 20000;

    /** Number of hits before moving to the next coherence zone */
    public static final int ZONE_HITS = 3;

    /** R-R interval list. */
    private ArrayList<RrInterval> _list = new ArrayList<RrInterval>();

    /** Total measured time in seconds. */
    private int _measuredTime = 0;

    /** Number of R-R intervals dropped for being invalid. */
    private int _droppedCount = 0;

    /** Current coherence zone. */
    private CoherenceZone _coherenceZone = null;

    /** Next coherence zone candidate. */
    private CoherenceZone _nextCoherenceZone = null;
    private RrIntervalListListener _listener = null;

    /** R-R intervals out of range in a row. */
    private int _outOfRangeInARow = 0;

    /** R-R outliers intervals in a row. */
    private int _outliersInARow = 0;

    /** High accuracy series including all the points. */
    TimeSeries _heartRateSeries = new TimeSeries("");

    // Caching calculated values
    double _cachedHeartRate;
    double _cachedInstantHeartRate;
    double _cachedHRV;
    double _cachedInstantHRV;
    double _cachedCoherence;
    double _cachedInstantCoherence;
    double _instantMax;
    double _instantMin;

    /** Public constructor. */
    public RrIntervalList() {

        clear();
    }

    /** Clears the list. */
    public void clear() {

        _measuredTime = 0;
        _list.clear();
        _heartRateSeries.clear();
        _droppedCount = 0;
        _coherenceZone = CoherenceZone.get(0, null);
        _coherenceZone.resetHits();
        _nextCoherenceZone = null;
        resetCachedValues();
    }

    private void resetCachedValues() {

        _cachedHeartRate = -1;
        _cachedInstantHeartRate = -1;
        _cachedHRV = -1;
        _cachedInstantHRV = -1;
        _cachedCoherence = -1;
        _cachedInstantCoherence = -1;
        _instantMax = -1;
        _instantMin = -1;
    }

    /**
     * Adds a R-R Interval to the list
     * @param rrInterval R-R to add to the list
     * @return True if added or false if discarded
     */
    public boolean add(RrInterval rrInterval) {

        // Checks if it's valid
        RrInterval prevRRInterval = getLast();
        boolean isOutlier = rrInterval.isOutlier(prevRRInterval);
        boolean isOutOfRange = rrInterval.isOutOfRange();
        if ((isOutlier && _outliersInARow < 3) || isOutOfRange) { // invalid interval

            Log.e(TAG, "Discarding interval: " + rrInterval.getHeartRate());
            incrementDroppedCount(1);
            if (isOutOfRange)
                _outOfRangeInARow++;
            if (isOutlier)
                _outliersInARow++;

            return false;
        }
        else { // valid interval

            //Log.d(TAG, "Adding interval: " + rrInterval.getHeartRate());

            _outOfRangeInARow = 0;
            _outliersInARow = 0;

            resetCachedValues();

            // Update Coherence vars for Coherence algorithm
            if (_list.size() > 0) {

                rrInterval.setPrevious(_list.get(_list.size() - 1));
            }

            _list.add(rrInterval);
            _measuredTime += rrInterval.getRRInterval();

            updateTimeSeries(rrInterval);

            updateCoherenceZone();

            return true;
        }
    }

    /** Returns dropped intervals in a row. */
    public int getDroppedInARow() {

        return getOutOfRangeInARow() + getOutliersInARow();
    }

    /** Returns out of range intervals in a row. */
    public int getOutOfRangeInARow() {

        return _outOfRangeInARow;
    }

    /** Returns outliers intervals in a row. */
    public int getOutliersInARow() {

        return _outliersInARow;
    }

    /** Adds the R-R interval to the time series. */
    private void updateTimeSeries(RrInterval rrInterval) {

        // Update heart rate series
        double heartRate = rrInterval.getHeartRate();
        if (heartRate > 0)
            _heartRateSeries.add(rrInterval.getDate(), heartRate);
    }

    /** Increments the total dropped R-R intervals counter. */
    public void incrementDroppedCount(int dropped) {

        _droppedCount += dropped;
    }

    /** Returns the total R-R intervals dropped counter. */
    public int getDroppedCount() {

        return _droppedCount;
    }

    /** Returns the dropped R-R intervals rate. */
    public float getDroppedRate() {

        float total = (float) _droppedCount + size();
        return _droppedCount / total;
    }

    /** Returns the number of valid R-R intervals stored. */
    public int size() {

        return _list.size();
    }

    /** Returns the heart rate time series. */
    public XYSeries getHeartRateSeries() {

        return _heartRateSeries;
    }

    /** Returns the total measured time in seconds. */
    public int getMeasuredTime() {

        return _measuredTime;
    }

    /** Returns the valid R-R interval stored in the given position. */
    public RrInterval get(int position) {

        return _list.get(position);
    }

    /** Returns the average R-R interval in the last specified milli seconds. */
    private double getAverageRR(long millis) {

        double result = 0;
        int sum = 0;
        int count = 0;
        for (int i = _list.size() - 1; i >= 0; i--) {

            RrInterval rrInterval = _list.get(i);
            sum += rrInterval.getRRInterval();
            count++;

            if (millis > 0 && sum >= millis) break;
        }
        if (count > 0) {

            result = sum / count;
        }
        return result;
    }

    /** Updates the min and max values in the time defined in the TIME_WINDOW. */
    private void updateInstantMinMax() {

        int sum = 0;
        for (int i = _list.size() - 1; i >= 0; i--) {

            RrInterval rrInterval = _list.get(i);
            sum += rrInterval.getRRInterval();

            if (_instantMin == -1 || rrInterval.getHeartRate() < _instantMin)
                _instantMin = rrInterval.getHeartRate();

            if (_instantMax == -1 || rrInterval.getHeartRate() > _instantMax)
                _instantMax = rrInterval.getHeartRate();

            if (TIME_WINDOW > 0 && sum >= TIME_WINDOW) break;
        }
    }

    /** Returns the max value in the time defined in the TIME_WINDOW. */
    public double getInstantMaxHeartRate() {

        if (_instantMax == -1)
            updateInstantMinMax();

        return _instantMax;
    }

    /**
     * Calculates the max and min Heart rate between two timestamps. Begin should be smaller than end.
     * @param begin Timestamp of the begin window to calculate max and min
     * @param end Timestamp of the end window to calculate max and min
     * @return Array in which first position it's min and in seconds position max
     */
    public double[] getMinMaxHeartRate(long begin, long end) {

        // Iterate over intervals
        double result[] = new double[] {300.0, 0.0};
        for (int i = _list.size() - 1; i >= 0; i--) {

            long timestamp = _list.get(i).getTimestamp();
            if (timestamp < end && timestamp > begin) {
                Double current = _list.get(i).getHeartRate();
                if (current > result[1]) result[1] = current;
                if (current < result[0]) result[0] = current;
            }
            else if ( timestamp < begin) {

                break;
            }
        }

        return result;
    }

    /** Returns the min value in the time defined in the TIME_WINDOW. */
    public double getInstantMinHeartRate() {

        if (_instantMin == -1)
            updateInstantMinMax();

        return _instantMin;
    }

    /** Returns the total average R-R interval. */
    private double getAverageRR() {

        return getAverageRR(0);
    }

    /** Returns the average R-R interval in the last specified milli seconds. */
    private double getHeartRate(long millis) {

        double result = 0;
        double averageRR = getAverageRR(millis);
        if (averageRR > 0) {

            result = 60000 / getAverageRR(millis);
        }
        return result;
    }

    /** Returns the average heart rate in the last time defined in TIME_WINDOW. */
    public double getInstantHeartRate() {

        if (_cachedInstantHeartRate >= 0)
            return _cachedInstantHeartRate;

        _cachedInstantHeartRate = getHeartRate(TIME_WINDOW);

        return _cachedInstantHeartRate;
    }

    /** Returns the total average heart rate. */
    public double getHeartRate() {

        if (_cachedHeartRate >= 0)
            return _cachedHeartRate;

        _cachedHeartRate = getHeartRate(0);

        return _cachedHeartRate;
    }

    /** Returns the total average hrv. */
    public double getHRV() {

        if (_cachedHRV >= 0)
            return _cachedHRV;

        _cachedHRV = getHRV(0);

        return _cachedHRV;
    }

    /** Returns the hrv in the last time defined in TIME_WINDOW. */
    public double getInstantHRV() {

        if (_cachedInstantHRV >= 0)
            return _cachedInstantHRV;

        _cachedInstantHRV = getHRV(TIME_WINDOW);

        return _cachedInstantHRV;
    }

    /** Returns the hrv in the last specified milli seconds. */
    private double getHRV(long millis) {

        double rmssd = getRMSSD(millis);
        if (rmssd <= 0) {

            return 0;
        }
        return Math.log(rmssd) * 20;
    }

    /** Returns the total RMSSD. */
    private double getRMSSD() {

        return getRMSSD(0);
    }

    /** Returns the RMSSD for the last time defined TIME_WINDOW. */
    private double getInstantRMSSD() {

        return getRMSSD(TIME_WINDOW);
    }

    /** Returns the RMSSD for the last specified milli seconds. */
    private double getRMSSD(long millis) {

        double result = 0;
        if (_list.size() >= 2) {

            int sum = 0;
            int count = 0;
            int temp = 0;
            for (int i = _list.size() - 1; i >= 1; i--) {

                int rr1 = _list.get(i).getRRInterval();
                int rr = _list.get(i - 1).getRRInterval();
                temp += Math.pow(rr1 - rr, 2);
                sum += rr + rr1;
                count += 2;

                if (millis > 0 && sum >= millis) break;
            }
            if (count > 0) {

                result = Math.sqrt(temp / count);
            }
        }
        return result;
    }

    /** Returns the coherence score for the last time defined in TIME_WINDOW. */
    public double getInstantCoherence() {

        if (_cachedInstantCoherence >= 0)
            return _cachedInstantCoherence;

        _cachedInstantCoherence = getCoherence(TIME_WINDOW);

        return _cachedInstantCoherence;
    }

    /** Returns the total coherence score. */
    public double getCoherence() {

        if (_cachedCoherence >= 0)
            return _cachedCoherence;

        _cachedCoherence = getCoherence(-1);

        return _cachedCoherence;
    }

    /** Returns the coherence score for the last specified milli seconds.
     * Coherence Algorithm
     * http://www.wseas.us/e-library/conferences/2011/Florence/AIASABEBI/AIASABEBI-62.pdf
     */
    private double getCoherence(long millis) {

        double result = 0;
        int bfs = 0;
        int sum = 0;
        int count = 0;
        for (int i = _list.size() - 1; i >= 0; i--) {

            RrInterval rrInterval = _list.get(i);
            if (rrInterval.getBFS()) {

                bfs++;
            }
            sum += rrInterval.getRRInterval();
            count++;

            if (millis > 0 && sum >= millis) break;
        }
        if (count > 0) {

            result = (100 * bfs) / count;
        }
        return result;
    }

    /** Returns the last R-R interval. */
    public RrInterval getLast() {

        if (!_list.isEmpty())
            return get(_list.size() - 1);
        else
            return null;
    }

    /** Returns true if no valid R-R intervals are stored. False otherwise. */
    public boolean isEmpty() {

        return size() == 0;
    }

    /** Returns the current coherence zone. */
    public CoherenceZone getCoherenceZone() {

        return _coherenceZone;
    }

    /** Updates the coherence zone.
     * To change to a new coherence zone at least 3 values should be in the new one.
     * This prevents too much fluctuation between zones leading to confusion. */
    private void updateCoherenceZone() {

        processNextCoherenceZone();

        if (_coherenceZone == null ||
                (_coherenceZone.getIndex() != _nextCoherenceZone.getIndex() && _nextCoherenceZone.getHits() >= ZONE_HITS)) {

            CoherenceZone oldCoherenceZone = _coherenceZone;
            _coherenceZone = _nextCoherenceZone;
            _coherenceZone.resetHits();
            _nextCoherenceZone = null;

            if (_listener != null)
                _listener.onCoherenceZoneChanged(oldCoherenceZone, _coherenceZone);
        }
    }

    /** Selects the new coherence zone candidate. */
    private void processNextCoherenceZone() {

        double coherence = getInstantCoherence();
        CoherenceZone coherenceZone = CoherenceZone.get((int) coherence, _coherenceZone);

        if (_nextCoherenceZone == null || _nextCoherenceZone.getIndex() != coherenceZone.getIndex()) {

            _nextCoherenceZone = coherenceZone;
            _nextCoherenceZone.resetHits();
        }
        else {

            _nextCoherenceZone.incrementHits();
        }
    }

    /** Sets the R-R interval listener to be notified of coherence zone changes. */
    public void setListener(RrIntervalListListener listener) {

        _listener = listener;
    }
}