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
import android.view.View;
import android.widget.TextView;

import static com.hijacker.MainActivity.background;

public class LoadingDialog extends DialogFragment {
    String title = null;
    View dialogView;
    TextView loadingDescription;
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        setCancelable(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        dialogView = requireActivity().getLayoutInflater().inflate(R.layout.loading_dialog, null);
        loadingDescription = dialogView.findViewById(R.id.loadingDescription);

        if(title!=null) loadingDescription.setText(title);

        builder.setView(dialogView);
        return builder.create();
    }
    @Override
    public void show(@NonNull FragmentManager fragmentManager, String tag){
        if(!background) super.show(fragmentManager, tag);
    }
    void setInitText(String str){
        title = str;
    }
    void setText(String str){
        // The dialog view may not be created yet (loadingDescription null). Store to title and update when view is ready.
        title = str;
        if(loadingDescription!=null){
            loadingDescription.setText(str);
        }
    }
}
