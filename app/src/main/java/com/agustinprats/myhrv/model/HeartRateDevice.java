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

/**
 * Class that stores information about a heart rate device.
 */
public class HeartRateDevice {

    private String _name;
    private String _address;

    /**
     * Public constructor
     * @param name Name of the heart rate device.
     * @param address Address of the heart rate device.
     */
    public HeartRateDevice(String name, String address) {

        _name = name;
        _address = address;
    }

    /** Returns the name of the heart rate device. */
    public String getName() {

        return _name;
    }

    /** Returns the address of the heart rate device. */
    public String getAddress() {

        return _address;
    }
}
