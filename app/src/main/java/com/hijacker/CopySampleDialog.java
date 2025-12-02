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

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;

import static com.hijacker.MainActivity.aircrack_dir;
import static com.hijacker.MainActivity.aireplay_dir;
import static com.hijacker.MainActivity.airodump_dir;
import static com.hijacker.MainActivity.cap_path;
import static com.hijacker.MainActivity.copy;
import static com.hijacker.MainActivity.iface;
import static com.hijacker.MainActivity.background;
import static com.hijacker.MainActivity.mdk4bf_dir;
import static com.hijacker.MainActivity.prefix;
import static com.hijacker.MainActivity.reaver_dir;
import static com.hijacker.MainActivity.pixiewps_dir;

public class CopySampleDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(R.string.copy_sample_title);
        builder.setNegativeButton(R.string.back, (dialog, id) -> {
            //return
        });
        builder.setItems(R.array.test_cmd, (dialog, which) -> {
            switch(which){
                case 0:
                    copy(aircrack_dir + " " + cap_path + "/wpa.cap-01.cap", getView());
                    break;
                case 1:
                    String pfx = (prefix==null) ? "" : prefix.trim();
                    String prefPart = pfx.isEmpty() ? "" : pfx + " ";
                    copy(prefPart + airodump_dir + " " + iface, getView());
                    break;
                case 2:
                    pfx = (prefix==null) ? "" : prefix.trim();
                    prefPart = pfx.isEmpty() ? "" : pfx + " ";
                    copy(prefPart + aireplay_dir + " --ignore-negative-one --deauth 0 -a 00:11:22:33:44:55 -c 01:23:45:67:89:0a " + iface, getView());
                    break;
                case 3:
                    pfx = (prefix==null) ? "" : prefix.trim();
                    prefPart = pfx.isEmpty() ? "" : pfx + " ";
                    copy(prefPart + mdk4bf_dir + " " + iface + " b -m", getView());
                    break;
                case 4:
                    pfx = (prefix==null) ? "" : prefix.trim();
                    prefPart = pfx.isEmpty() ? "" : pfx + " ";
                    copy(prefPart + reaver_dir + " -i " + iface + " -vv -b 00:11:22:33:44:55 --channel 6 -L -E -S", getView());
                    break;
                case 5:
                    // Pixiewps attack using pixiewps from assets
                    pfx = (prefix==null) ? "" : prefix.trim();
                    prefPart = pfx.isEmpty() ? "" : pfx + " ";
                    String binPath = pixiewps_dir.substring(0, pixiewps_dir.lastIndexOf('/'));
                    copy("export PATH=" + binPath + ":$PATH && " + prefPart + reaver_dir + " -i " + iface + " -vv -b 00:11:22:33:44:55 --channel 6 -K 1", getView());
                    break;
            }
            dismissAllowingStateLoss();
        });
        return builder.create();
    }
    @Override
    public void show(@NonNull FragmentManager fragmentManager, String tag){
        if(!background) super.show(fragmentManager, tag);
    }
}
