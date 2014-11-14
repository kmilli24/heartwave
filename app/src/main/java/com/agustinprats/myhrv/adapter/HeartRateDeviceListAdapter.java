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

package com.agustinprats.myhrv.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.agustinprats.myhrv.R;
import com.agustinprats.myhrv.model.HeartRateDevice;

import java.util.ArrayList;

/** Adapter for holding heart rate devices found through scanning. */
public class HeartRateDeviceListAdapter extends BaseAdapter {
    private final static String TAG = HeartRateDeviceListAdapter.class.toString();
    private ArrayList<HeartRateDevice> _devices;
    private LayoutInflater _inflator;

    public HeartRateDeviceListAdapter(LayoutInflater inflater) {
        super();
        _devices = new ArrayList<HeartRateDevice>();
        _inflator = inflater;
    }

    private boolean isAdded(HeartRateDevice device) {

        for (HeartRateDevice i : _devices) {

            if (i.getAddress().equals(device.getAddress()))
                return true;
        }
        return false;
    }

    public void addDevice(HeartRateDevice device) {

        if(!isAdded(device) && device != null) {

            _devices.add(device);
            notifyDataSetChanged();
        }
    }

    public HeartRateDevice getDevice(int position) {

        return _devices.get(position);
    }

    public void clear() {
        _devices.clear();
    }

    @Override
    public int getCount() {
        return _devices.size();
    }

    @Override
    public Object getItem(int i) {
        return _devices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        Log.d(TAG, "getView");

        ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = _inflator.inflate(R.layout.listitem_device, null);
            viewHolder = new ViewHolder();
            viewHolder.deviceName = (TextView) view.findViewById(R.id.deviceName);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        HeartRateDevice device = _devices.get(i);
        final String deviceName = device.getName();
        if (deviceName != null && deviceName.length() > 0) {
            viewHolder.deviceName.setText(deviceName);
        }
        else {
            viewHolder.deviceName.setText(R.string.unknown_device);
        }

        return view;
    }

    static class ViewHolder {
        TextView deviceName;
    }
}