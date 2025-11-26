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
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import android.widget.Button;

import static com.hijacker.MainActivity.isArchValid;
import static com.hijacker.MainActivity.background;

public class FirstRunDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setCancelable(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setMessage(R.string.first_run);
        builder.setTitle(R.string.first_run_title);
        builder.setPositiveButton(R.string.install_firmware, (dialog, id) -> {});
        builder.setNegativeButton(R.string.home, (dialog, id) -> {
            // Go to Home
            dismissAllowingStateLoss();
        });
        builder.setNeutralButton(R.string.exit, (dialog, which) -> requireActivity().finish());
        return builder.create();
    }
    @Override
    public void show(@NonNull FragmentManager fragmentManager, String tag){
        if(!background) super.show(fragmentManager, tag);
    }
    @Override
    public void onStart(){
        super.onStart();
        // Disable "Install Nexmon" button if arch is not valid
        AlertDialog d = (AlertDialog) getDialog();
        if(d==null) return;

        Button positiveButton = d.getButton(Dialog.BUTTON_POSITIVE);
        if(!isArchValid()){
            positiveButton.setEnabled(false);
        }else{
            positiveButton.setOnClickListener(v -> {
                // Open InstallFirmwareDialog to install Nexmon
                new InstallFirmwareDialog().show(requireActivity().getSupportFragmentManager(), "InstallFirmwareDialog");
            });
        }
    }
    @Override
    public void onDismiss(@NonNull DialogInterface dialogInterface) {
        super.onDismiss(dialogInterface);

        synchronized(this) {
            notify();
        }
    }

    public void _wait() {
        // No-op: do not block the setup thread. Continue setup and handle actions via callbacks.
    }
}
