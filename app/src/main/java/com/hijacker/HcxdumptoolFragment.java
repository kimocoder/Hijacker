package com.hijacker;

/*
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

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;

import static com.hijacker.MainActivity.FRAGMENT_HCXDUMPTOOL;
import static com.hijacker.MainActivity.PROCESS_HCXDUMPTOOL;
import static com.hijacker.MainActivity.background;
import static com.hijacker.MainActivity.cap_path;
import static com.hijacker.MainActivity.currentFragment;
import static com.hijacker.MainActivity.hcxdumptool_dir;
import static com.hijacker.MainActivity.iface;
import static com.hijacker.MainActivity.isProcessRunning;
import static com.hijacker.MainActivity.notification;
import static com.hijacker.MainActivity.prefix;
import static com.hijacker.MainActivity.pref;
import static com.hijacker.MainActivity.runInHandler;
import static com.hijacker.MainActivity.startHcxdumptool;
import static com.hijacker.MainActivity.stop;

public class HcxdumptoolFragment extends Fragment {
    private static final String TAG = "HIJACKER/Hcxdumptool";

    View fragmentView;
    Button startBtn;
    EditText outputFileEdit, channelEdit, timeoutEdit;
    TextView statusText;
    ScrollView scrollView;

    // view model to persist state across config changes
    private HcxdumptoolViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
         fragmentView = inflater.inflate(R.layout.hcxdumptool_fragment, container, false);

         // initialize ViewModel
         viewModel = new ViewModelProvider(requireActivity()).get(HcxdumptoolViewModel.class);

         startBtn = fragmentView.findViewById(R.id.hcx_start_btn);
         outputFileEdit = fragmentView.findViewById(R.id.hcx_output_file);
         channelEdit = fragmentView.findViewById(R.id.hcx_channel);
         timeoutEdit = fragmentView.findViewById(R.id.hcx_timeout);
         statusText = fragmentView.findViewById(R.id.hcx_status_text);
         scrollView = fragmentView.findViewById(R.id.hcx_scroll);

         // Set default output file
         String defaultFile = cap_path + "/hcxdump_" + System.currentTimeMillis() + ".pcapng";
         // restore saved output file if present
         if (viewModel.getDefaultOutputFile() != null && !viewModel.getDefaultOutputFile().isEmpty()) {
             outputFileEdit.setText(viewModel.getDefaultOutputFile());
         } else {
             outputFileEdit.setText(defaultFile);
             viewModel.setDefaultOutputFile(defaultFile);
         }

         // Set default values
         channelEdit.setText(viewModel.getFilterMode());
         timeoutEdit.setText("300"); // 5 minutes default

         startBtn.setOnClickListener(v -> onStartStopClick());

         // Update button state
         updateButtonState();

         return fragmentView;
     }

     @Override
     public void onResume() {
         super.onResume();
         currentFragment = FRAGMENT_HCXDUMPTOOL;
         updateButtonState();
         ((MainActivity) requireActivity()).refreshDrawer();
     }

     private void onStartStopClick() {
         if (isProcessRunning(PROCESS_HCXDUMPTOOL)) {
             // Stop hcxdumptool
             stop(PROCESS_HCXDUMPTOOL);
             viewModel.setTaskRunning(false);
             updateButtonState();
             updateStatus(getString(R.string.hcx_stopped));
         } else {
             // Validate inputs
             String outputFile = outputFileEdit.getText().toString().trim();
             if (outputFile.isEmpty()) {
                 Snackbar.make(fragmentView, R.string.hcx_no_output_file, Snackbar.LENGTH_SHORT).show();
                 return;
             }

             // Create output directory if needed
             File outFile = new File(outputFile);
             File parentDir = outFile.getParentFile();
             if (parentDir != null && !parentDir.exists()) {
                 if (!parentDir.mkdirs()) {
                     Snackbar.make(fragmentView, R.string.hcx_cant_create_dir, Snackbar.LENGTH_SHORT).show();
                     return;
                 }
             }

             String channel = channelEdit.getText().toString().trim();
             String timeout = timeoutEdit.getText().toString().trim();

             // Start hcxdumptool
             startHcxdumptool(outputFile, channel, timeout);
             viewModel.setTaskRunning(true);
             updateButtonState();
             updateStatus(getString(R.string.hcx_running));
         }
     }

     // instance method to refresh UI button state; safe to call from any thread
     public void updateButtonState() {
         runInHandler(() -> {
             if (startBtn != null) {
                 boolean running = isProcessRunning(PROCESS_HCXDUMPTOOL);
                 viewModel.setTaskRunning(running);
                 if (running) {
                     startBtn.setText(R.string.stop);
                     startBtn.setBackgroundColor(0xFFCC0000); // Red
                 } else {
                     startBtn.setText(R.string.start);
                     startBtn.setBackgroundColor(0xFF00AA00); // Green
                 }
             }
         });
     }

     private void updateStatus(String message) {
         runInHandler(() -> {
             if (statusText != null) {
                 String currentText = statusText.getText().toString();
                 String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                     .format(new java.util.Date());
                 String newLine = "[" + timestamp + "] " + message + "\n";
                 statusText.setText(newLine + currentText);

                 // Auto-scroll to top
                 if (scrollView != null) {
                     scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP));
                 }
             }
         });
     }

     public static boolean isRunning() {
         // Prefer the process tracker; ViewModel state is instance-scoped.
         return isProcessRunning(PROCESS_HCXDUMPTOOL);
     }
 }
