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

package com.agustinprats.myhrv.util;

import java.util.Date;

/**
 * Convenient methods.
 */
public class Utils {

    /**
     * Converts seconds to a string representing the duration in the format 1h 1' 1''
     * @param seconds Total amount of seconds
     * @return a string representing the duration in the format 1h 1' 1''
     */
    public static String getDuration(int seconds) {

        return getDuration(seconds, 1);
    }

    /**
     * Converts seconds to a string representing the duration in the format 1h 1' 1''
     * @param seconds Total amount of seconds
     * @param percent factor to multiply the amount of seconds
     * @return a string representing the duration in the format 1h 1' 1''
     */
    public static String getDuration(int seconds, float percent) {

        int[] durationArray = getDurationArray(seconds, percent);

        String duration = "";

        if (durationArray[0] > 0) {

            duration = durationArray[0] + "h " + durationArray[1] + "' " + durationArray[2] + "''";
        }
        else if (durationArray[1] > 0) {

            duration = durationArray[1] + "' " + durationArray[2] + "''";
        }
        else {

            duration = durationArray[2] + "''";
        }

        return duration;
    }

    /**
     * Converts seconds to a string representing the duration in the format hh:mm:ss
     * @param seconds Total amount of seconds
     * @return a string representing the duration in the format hh:mm:ss
     */
    public static String getDigitalDuration(int seconds) {

        return getDigitalDuration(seconds, 1);
    }

    /**
     * Converts seconds to a string representing the duration in the format hh:mm:ss
     * @param seconds Total amount of seconds
     * @param percent Factor to multiply the amount of seconds
     * @return a string representing the duration in the format hh:mm:ss
     */
    public static String getDigitalDuration(int seconds, float percent) {

        int[] durationArray = getDurationArray(seconds, percent);

        String duration = "";

        if (durationArray[0] > 0) {

            duration = (durationArray[0]<10?"0"+durationArray[0]:durationArray[0]) + ":" + (durationArray[1]<10?"0"+durationArray[1]:durationArray[1]) + ":" + (durationArray[2]<10?"0"+durationArray[2]:durationArray[2]);
        }
        else  {

            duration = (durationArray[1]<10?"0"+durationArray[1]:durationArray[1]) + ":" + (durationArray[2]<10?"0"+durationArray[2]:durationArray[2]);
        }

        return duration;
    }

    /**
     * Receives an amount of seconds an returns hours, minutes and seconds in an array
     * @return result[0] = hours; result[1] = minutes; result[2] = seconds;
     */
    public static int[] getDurationArray(int seconds) {

        return getDurationArray(seconds, 1);
    }

    /**
     * Receives an amount of seconds an returns hours, minutes and seconds in an array
     * @percent factor to multiply the inpunt
     * @return result[0] = hours; result[1] = minutes; result[2] = seconds;
     */
    public static int[] getDurationArray(int seconds, float percent) {

        int hours = (int) ((seconds / (3600)) * percent);
        seconds = (int) seconds % (3600);
        int minutes = (int) ((seconds / (60)) * percent);
        seconds = (int) ((seconds % (60)) * percent);

        int[] result = new int[3];
        result[0] = hours;
        result[1] = minutes;
        result[2] = seconds;
        return result;
    }

    /**
     * Returns the string adding spaces in the front until the length of the string matches value
     * @param value String to process
     * @param padding Length of the final string
     * @return String with padding in the front
     */
    public static String getPaddedString(String value, int padding) {

        for (int i = value.length(); i < padding; i++) {

            value = " " + value;
        }
        return value;
    }

    /**
     * Returns the number of days between two dates
     */
    public static int daysBetween(Date d1, Date d2){
        return (int)( (d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24));
    }

    /** Returns a random number between two values */
    public static int getRandom(int min, int max) {

        return min + (int)(Math.random() * ((max - min) + 1));
    }
}
