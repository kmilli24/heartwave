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

import com.agustinprats.myhrv.model.RrIntervalList;
import com.agustinprats.myhrv.model.HeartRateDevice;

/**
 * Listener callbacks of the HeartRateService
 */
public interface HeartRateServiceListener {

    /** Called when connects to a HeartRateDevice */
    void onHeartRateServiceConnected();

    /** Called when it's connecting to a HeartRateDevice */
    void onHeartRateServiceConnecting();

    /** Called when disconnect from a HeartRateDevice */
    void onHeartRateServiceDisconnected(Integer errorCode);

    /** Called when
     * the battery level is received from a HeartRateDevice */
    void onHeartRateServiceBatteryLevelChanged(int newLevel);

    /** Called when new RR intervals are received
     *  @intervals All intervals received while the device's been connected
     *  @count Number of RR intervals that are new. They can be found at the end of the list */
    void onHeartRateServiceNewRrIntervals(RrIntervalList intervals, int count);

    /** Called when intervals are dropped because
     *  are out of range of because changed to fast */
    void onHeartRateServiceDroppedIntervals(int count);

    /** Called when a new @HeartRateDevice is found while scanning */
    void onHeartRateServiceDeviceFound(final HeartRateDevice device);

    /** Called when stopped scanning for new devices */
    void onHeartRateServiceDeviceScanStopped();
}