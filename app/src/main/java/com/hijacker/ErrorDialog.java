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

import static com.hijacker.MainActivity.mNotificationManager;
import static com.hijacker.MainActivity.background;
import androidx.core.app.NotificationCompat;
import android.app.NotificationManager;
import android.content.Context;

public class ErrorDialog extends DialogFragment {
    String message;
    String title;
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if(title==null) title = getString(R.string.error);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setPositiveButton(R.string.ok, (dialog, id) -> {});
        builder.setNeutralButton(R.string.exit, (dialog, which) -> requireActivity().finish());
        if(message!=null) {
            builder.setTitle(title);
            builder.setMessage(this.message);
        }else{
            builder.setTitle("");
            builder.setMessage("");
        }
        return builder.create();
    }
    public void setMessage(String msg){ this.message = msg; }
    public void setTitle(String title){ this.title = title; }
    public void _wait(){
        // No-op: do not block the setup thread. Error handling is performed via notification when backgrounded.
    }
    @Override
    public void show(@NonNull FragmentManager fragmentManager, String tag){
        if(!background) super.show(fragmentManager, tag);
        else{
            NotificationCompat.Builder error_notif = new NotificationCompat.Builder(requireActivity(), "error_channel")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);

            // Use MainActivity's NotificationManager if available, otherwise get one from application context
            if(mNotificationManager != null){
                mNotificationManager.notify(1, error_notif.build());
            }else{
                NotificationManager nm = (NotificationManager) requireActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                if(nm!=null) nm.notify(1, error_notif.build());
            }
        }
    }
    @Override
    public void onDismiss(@NonNull DialogInterface dialogInterface){
        super.onDismiss(dialogInterface);

        synchronized(this){
            notify();
        }
    }
}
