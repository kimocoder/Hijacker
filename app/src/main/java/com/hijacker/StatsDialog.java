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

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

import static com.hijacker.MainActivity.background;

public class StatsDialog extends DialogFragment {
    static boolean isResumed = false;
    TextView wpa_count, wpa2_count, wep_count, opn_count, wps_count, hidden_count, connected_count;
    static Runnable runnable;
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        View view = requireActivity().getLayoutInflater().inflate(R.layout.ap_stats, null);

        if(wpa_count==null) {
            wpa_count = view.findViewById(R.id.wpa_count);
            wpa2_count = view.findViewById(R.id.wpa2_count);
            wep_count = view.findViewById(R.id.wep_count);
            opn_count = view.findViewById(R.id.opn_count);
            wps_count = view.findViewById(R.id.wps_count);
            hidden_count = view.findViewById(R.id.hidden_count);
            connected_count = view.findViewById(R.id.connected_count);
        }

        runnable = () -> {
            wpa_count.setText(String.format(Locale.getDefault(), "%d", AP.wpa));
            wpa2_count.setText(String.format(Locale.getDefault(), "%d", AP.wpa2));
            wep_count.setText(String.format(Locale.getDefault(), "%d", AP.wep));
            opn_count.setText(String.format(Locale.getDefault(), "%d", AP.opn));
            wps_count.setText(String.format(Locale.getDefault(), "%d", AP.wps_enabled));
            hidden_count.setText(String.format(Locale.getDefault(), "%d", AP.hidden));
            connected_count.setText(String.format(Locale.getDefault(), "%d/%d", ST.connected, ST.STs.size()));
        };
        runnable.run();

        builder.setView(view);
        builder.setTitle(R.string.ap_stats);
        builder.setNegativeButton(R.string.close, (dialog, which) -> {
            //close
        });
        return builder.create();
    }
    @Override
    public void show(@NonNull FragmentManager fragmentManager, String tag){
        if(!background) super.show(fragmentManager, tag);
    }
    @Override
    public void onResume(){
        super.onResume();
        isResumed = true;
    }
    @Override
    public void onPause(){
        super.onPause();
        isResumed = false;
        // Ensure the activity toolbar reflects the true running state after the dialog closes
        try{
            MainActivity.updateRunMenuIcon();
        }catch(Exception ignored){}
    }
}
