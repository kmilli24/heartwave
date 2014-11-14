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

import android.graphics.Color;

import com.agustinprats.myhrv.R;

import java.util.Arrays;
import java.util.List;

/**
 * Class that stores information about coherence zones.
 *
 * Coherence is a score ranging from 0 to 100 where 0 represents very low parasympathetic activation (relax)
 * and very high sympathetic activation (stress) and 100 represents very high parasympathetic activation (relax)
 * and very high sympathetic activation (stress)
 *
 * A coherence zone is an interval of coherence.
 * Current intervals defined:
 *      Very Low     = [0, 20]
 *      Low          = [20, 35]
 *      Medium Low   = [35, 50]
 *      High         = [50, 65]
 *      Very High    = [80 - 100]
 */
public class CoherenceZone {

    /**
     * All the zen zones available. Sorted from lower to higher.
     */
    public static final List<CoherenceZone> getList() {

        return _list;
    }

    /**
     * All the zen zones available. Sorted from lower to higher.
     */
    private static final List<CoherenceZone> _list = Arrays.asList(
            new CoherenceZone(0, 0, R.string.veryLow, R.color.red, Color.WHITE, R.drawable.ic_red, R.drawable.glass_red, R.drawable.round_red, R.color.red),
            new CoherenceZone(1, 20, R.string.low, R.color.orange, Color.WHITE, R.drawable.ic_orange, R.drawable.glass_orange, R.drawable.round_orange, R.color.orange),
            new CoherenceZone(2, 35, R.string.mediumLow, R.color.yellow, Color.WHITE, R.drawable.ic_yellow, R.drawable.glass_yellow, R.drawable.round_yellow, R.color.yellow),
            new CoherenceZone(3, 50, R.string.mediumHigh, R.color.green, Color.WHITE, R.drawable.ic_green, R.drawable.glass_green, R.drawable.round_green, R.color.green),
            new CoherenceZone(4, 65, R.string.high, R.color.blue_light, Color.WHITE, R.drawable.ic_blue, R.drawable.glass_blue_light, R.drawable.round_blue_light, R.color.blue_light),
            new CoherenceZone(5, 80, R.string.verHigh, R.color.blue, Color.WHITE, R.drawable.ic_blue, R.drawable.glass_blue, R.drawable.round_blue, R.color.blue));

    /**
     * Calculates a zen zone based on a zen value and the current zone.
     * The calculated zone CAN NOT be more than TWO zones higher
     * or lower than the current one.
     *
     * @param zenValue Zen value to calculate the zen zone.
     * @param currentZone Current zen zone. It can be null if no zone was set.
     * @return Calculated zen zone
     */
    public static CoherenceZone get(int zenValue, CoherenceZone currentZone) {

        for (int i = _list.size() - 1; i >= 0; i--) {

            CoherenceZone coherenceZone = _list.get(i);
            if (zenValue >= coherenceZone.getMinimumValue()) {

                if (currentZone != null && i > currentZone.getIndex() && i < _list.size() - 1) {

                    return _list.get(currentZone.getIndex()+1);
                }
                else if (currentZone != null && i < currentZone.getIndex() && i > 0) {

                    return _list.get(currentZone.getIndex()-1);
                }
                return coherenceZone;
            }
        }
        return null;
    }

    private int _index;
    private int _minimumValue;
    private int _stringId;
    private int _color;
    private int _textColor;
    private int _icon;
    private int _glassImage;
    private int _hits = 0;
    private int _roundDrawable;
    private int _notificationColor;

    /**
     * Private constructor
     * @param index Index of the zone
     * @param minimumValue Minimum value of the zone
     * @param stringId String id of the name of the zone
     * @param color Color id of the zone
     * @param oppsiteColor Opposite color id of the zone
     * @param icon Icon id of the zone
     * @param glassImage Glass image id of the zone
     * @param roundDrawable Round drawable of the zone
     * @param notificationColor Notification color id of the zone
     */
    private CoherenceZone(int index, int minimumValue, int stringId, int color, int oppsiteColor, int icon, int glassImage, int roundDrawable, int notificationColor) {

        _index = index;
        _minimumValue = minimumValue;
        _stringId = stringId;
        _color = color;
        _textColor = oppsiteColor;
        _icon = icon;
        _glassImage = glassImage;
        _hits = 0;
        _roundDrawable = roundDrawable;
        _notificationColor = notificationColor;
    }

    /**
     * Returns the index of the zen zone in the _list
     */
    public int getIndex() {

        return _index;
    }

    /**
     * Returns the minimum value of the zen zone
     */
    public int getMinimumValue() {

        return _minimumValue;
    }

    /**
     * Returns the string id of the zen zone name
     */
    public int getStringId() {

        return _stringId;
    }

    /**
     * Returns the color of the zen zone
     */
    public int getColor() {

        return _color;
    }

    /**
     * Returns the color to be used in contrast of the zen zone.
     * For example, text printed over the zen zone color
     */
    public int getTextColor() {

        return _textColor;
    }

    /**
     * Icon resource id of the zone zen
     */
    public int getIcon() {

        return _icon;
    }

    /**
     * Drawable resource id of the glass in the basic monitor
     */
    public int getGlassImage() {

        return _glassImage;
    }

    /**
     * Increment the number of times in a row that the zen zone is being currently used
     */
    public void incrementHits() {

        _hits++;
    }

    /**
     * Number of times in a row that the zen zone is being currently used
     */
    public int getHits() {

        return _hits;
    }

    /**
     * Resets the number of times in a row that the zen zone is being currently used
     */
    public void resetHits() {

        _hits = 0;
    }

    /** Returns a colored drawable with round borders */
    public int getRoundDrawable() {

        return _roundDrawable;
    }

    /** Returns the notification color */
    public int getNotificationColor() {

        return _notificationColor;
    }
}
