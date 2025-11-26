package com.hijacker;

/*
    Copyright (C) 2019  Christos Kyriakopoulos
    Copyright (C) 2025  Christian <kimocoder> Bremvaag

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
import androidx.fragment.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import static com.hijacker.MainActivity.FRAGMENT_AIRODUMP;
import static com.hijacker.MainActivity.currentFragment;

public class MyListFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View ret = inflater.inflate(R.layout.list_fragment, container, false);
        // Find the listview inside the layout and set the adapter
        ListView list = ret.findViewById(R.id.listview);
        if(list!=null) list.setAdapter(MainActivity.adapter);
        if(list!=null){
            list.setOnItemClickListener((parent, v, position, id) -> Tile.tiles.get(position).device.getPopupMenu((MainActivity)getActivity(), v).show());
        }
        return ret;
    }
    @Override
    public void onResume(){
        super.onResume();
        currentFragment = FRAGMENT_AIRODUMP;
        ((MainActivity) requireActivity()).refreshDrawer();
    }
}
