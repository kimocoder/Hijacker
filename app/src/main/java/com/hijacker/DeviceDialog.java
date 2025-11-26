package com.hijacker;

/*
    Copyright (C) 2019  Christos Kyriakopoulos
    Copyright (C) 2024  Christian <kimocoder> Bremvaag

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import static com.hijacker.MainActivity.background;

abstract public class DeviceDialog extends DialogFragment{
    abstract void onRefresh();

    private DialogRefreshTask refresher;

    @Override
    public void show(@NonNull FragmentManager fragmentManager, String tag){
        if(!background) super.show(fragmentManager, tag);
    }
    @Override
    public void onResume(){
        super.onResume();
        refresher = new DialogRefreshTask(this);
        refresher.start();
    }

    @Override
    public void onPause(){
        if(refresher!=null){
            refresher.stop();
            refresher = null;
        }
        super.onPause();
    }

    @Override
    public void onDestroyView(){
        if(refresher!=null){
            refresher.stop();
            refresher = null;
        }
        super.onDestroyView();
    }
}
