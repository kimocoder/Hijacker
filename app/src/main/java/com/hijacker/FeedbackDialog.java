package com.hijacker;

/*
    Copyright (C) 2019  Christos Kyriakopoulos

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

import android.app.AlertDialog;
import android.app.Dialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hijacker.MainActivity.background;
import static com.hijacker.MainActivity.debug;
import static com.hijacker.MainActivity.path;

public class FeedbackDialog extends DialogFragment{
    View dialogView;
    EditText feedbackView;
    ProgressBar progress;
    CheckBox include_report;
    File report;
    private ReportTask reportTask;
    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        dialogView = requireActivity().getLayoutInflater().inflate(R.layout.feedback_dialog, null);

        include_report = dialogView.findViewById(R.id.include_report);
        feedbackView = dialogView.findViewById(R.id.feedback_et);
        progress = dialogView.findViewById(R.id.progress);

        report = null;
        include_report.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked && report==null){
                reportTask = new ReportTask();
                reportTask.start();
            }
        });

        builder.setView(dialogView);
        builder.setTitle(getString(R.string.feedback));
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> {});
        builder.setPositiveButton(R.string.send_email, (dialog, which) -> {
            Intent intent = new Intent (Intent.ACTION_SEND);
            intent.setType("plain/text");
            intent.putExtra(Intent.EXTRA_EMAIL, new String[] {"kiriakopoulos44@gmail.com"});
            intent.putExtra(Intent.EXTRA_SUBJECT, "Hijacker feedback");
            intent.putExtra(Intent.EXTRA_TEXT, feedbackView.getText().toString());
            if(report!=null){
                Uri attachment = FileProvider.getUriForFile(requireActivity().getApplicationContext(), BuildConfig.APPLICATION_ID + ".provider", report);
                intent.putExtra(Intent.EXTRA_STREAM, attachment);
            }
            startActivity(intent);
        });
        return builder.create();
    }
    @Override
    public void show(@NonNull FragmentManager fragmentManager, @Nullable String tag){
        if(!background) super.show(fragmentManager, tag);
    }
    private class ReportTask {
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "FeedbackReportThread"));
        void start(){
            mainHandler.post(() -> progress.setIndeterminate(true));
            executor.submit(() -> {
                report = new File(Environment.getExternalStorageDirectory() + "/report.txt");
                boolean result = MainActivity.createReport(report, path, null);
                mainHandler.post(() -> {
                    progress.setIndeterminate(false);
                    if(!result){
                        if(debug) Log.e("HIJACKER/feedbackDialog", "Report not generated");
                        report = null;
                    }
                });
                return null;
            });
        }
    }
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
}
