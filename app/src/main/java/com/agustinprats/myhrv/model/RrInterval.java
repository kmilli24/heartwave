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

import java.util.Date;

/**
 * Class that stores information about a R-R interval collected from a heart rate device.
 */
public class RrInterval {

    private static final String TAG = RrInterval.class.toString();

    /** Minimum valid heart rate value. */
    public static final int MIN_HR = 25;

    /** Maximum valid heart rate value. */
    public static final int MAX_HR = 190;

    /** Ratio between two consecutives R-R intervals so the interval is considered valid. */
    public static final float HEART_RATE_RATIO = 1.5f;

    private long _timestamp = 0;
    private int _rrInterval = 0;
    private Date _date = null;

    /** Difference between this interval and the previous one. */
    private Integer _diff = null;

    /** BFS value from the coherence algorithm:
     * http://www.wseas.us/e-library/conferences/2011/Florence/AIASABEBI/AIASABEBI-62.pdf */
    private boolean _bfs = false;

    /** Public constructor
     *
     * @param timestamp timestamp of the interval
     * @param rrInterval interval value
     */
    public RrInterval(long timestamp, int rrInterval) {

        _timestamp = timestamp;
        _date = new Date(timestamp);
        _rrInterval = rrInterval;
    }

    /** Returns the heart rate. */
    public double getHeartRate() {

        return (60000.0 / (double) _rrInterval);
    }

    /** Returns the R-R interval. */
    public int getRRInterval() {

        return _rrInterval;
    }

    /** Returns the timestamp. */
    public long getTimestamp() {

        return _timestamp;
    }

    /** Returns the date. */
    public Date getDate() {

        return _date;
    }

    /** Sets the previous interval used to validate this new interval. */
    public void setPrevious(RrInterval previous) {

        _diff = _rrInterval - previous.getRRInterval();
        if (_diff != null && previous.getDiff() != null) {

            _bfs = (_diff > 0 && previous.getDiff() > 0) || (_diff < 0 && previous.getDiff() < 0);
        }
    }

    /** BFS value from the coherence algorithm:
     * http://www.wseas.us/e-library/conferences/2011/Florence/AIASABEBI/AIASABEBI-62.pdf */
    public boolean getBFS() {

        return _bfs;
    }

    /** Returns the difference between this interval and the previous one.
     *  Null if there is no previous one. */
    public Integer getDiff() {

        return _diff;
    }

    /** Returns true if the interval is out of range. False otherwise. */
    public boolean isOutOfRange() {

        if (this.getHeartRate() < MIN_HR || this.getHeartRate() > MAX_HR) {

            return true;
        }
        return false;
    }

    /** Returns true is this interval is outlier. An outlier interval is when there is too much
     * difference with the previous interval
     *
     * @param previous Previous R-R interval
     * @return
     */
    public boolean isOutlier(RrInterval previous) {

        if (previous != null) {

            double ratio;
            if (previous.getHeartRate() > this.getHeartRate()) {

                ratio = (previous.getHeartRate()/this.getHeartRate());
            }
            else {

                ratio = (this.getHeartRate()/previous.getHeartRate());
            }

            if (ratio > HEART_RATE_RATIO) {

                return true;
            }
        }
        return false;
    }

    /** Returns the interval as a String. */
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append(getTimestamp());
        sb.append(":");
        sb.append(getRRInterval());
        return sb.toString();
    }
}
