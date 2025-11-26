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

import static com.hijacker.MainActivity.background;

public class CustomDialog extends DialogFragment {
    String title, message;
    String positiveText, neutralText, negativeText;
    boolean cancelable = true;
    Runnable onPositiveClick, onNeutralClick, onNegativeClick;
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setCancelable(cancelable);
        AlertDialog.Builder builder = getBuilder();
        if(neutralText!=null){
            builder.setNeutralButton(neutralText, (dialog, which) -> {
                if(onNeutralClick!=null) onNeutralClick.run();
                synchronized(CustomDialog.this){
                    CustomDialog.this.notify();
                }
            });
        }
        if(negativeText!=null){
            builder.setNegativeButton(negativeText, (dialog, id) -> {
                if(onNegativeClick!=null) onNegativeClick.run();
                synchronized(CustomDialog.this){
                    CustomDialog.this.notify();
                }
            });
        }
        return builder.create();
    }

    private AlertDialog.Builder getBuilder() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        if(title!=null) builder.setTitle(title);
        if(message!=null) builder.setMessage(message);
        if(positiveText!=null){
            builder.setPositiveButton(positiveText, (dialog, id) -> {
                if(onPositiveClick!=null) onPositiveClick.run();
                synchronized(CustomDialog.this){
                    CustomDialog.this.notify();
                }
            });
        }
        return builder;
    }

    @Override
    public void show(@NonNull FragmentManager fragmentManager, String tag){
        if(!background) super.show(fragmentManager, tag);
    }
    @Override
    public void onDismiss(@NonNull DialogInterface dialogInterface){
        super.onDismiss(dialogInterface);

        synchronized(this){
            notify();
        }
    }

    public void setTitle(String title){ this.title = title; }
    public void setMessage(String message){ this.message = message; }
    public void setPositiveButton(@NonNull String text, Runnable runnable){
        this.positiveText = text;
        this.onPositiveClick = runnable;
    }
    public void setNeutralButton(@NonNull String text, Runnable runnable){
        this.neutralText = text;
        this.onNeutralClick = runnable;
    }
    public void setNegativeButton(@NonNull String text, Runnable runnable){
        this.negativeText = text;
        this.onNegativeClick = runnable;
    }
    public void setCancelable(boolean cancelable){ this.cancelable = cancelable; }
    public void _wait() {
        // No-op: avoid blocking the background setup thread. Dialogs are handled via callbacks.
    }
}
