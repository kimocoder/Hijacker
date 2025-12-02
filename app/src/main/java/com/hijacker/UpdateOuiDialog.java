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

import android.app.Activity;
import android.app.Dialog;
import android.util.Log;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hijacker.MainActivity.curl;
import static com.hijacker.MainActivity.debug;
import static com.hijacker.MainActivity.manufDBFile;
import static com.hijacker.MainActivity.manufHashMap;
import static com.hijacker.MainActivity.path;
import static com.hijacker.Shell.getFreeShell;

public class UpdateOuiDialog {
    private static final String OUI_URL = "https://standards-oui.ieee.org/oui/oui.txt";
    private final Activity activity;
    private final View view;
    private ExecutorService executor;
    private Dialog progressDialog;
    private volatile boolean downloadInProgress = false;

    public UpdateOuiDialog(Activity activity, View view) {
        this.activity = activity;
        this.view = view;
    }

    public void startUpdate() {
        if (downloadInProgress) {
            Snackbar.make(view, R.string.oui_update_in_progress, Snackbar.LENGTH_SHORT).show();
            return;
        }

        // Show progress dialog with cancel option
        progressDialog = new ProgressIndicators.Builder(activity)
                .setMessage(activity.getString(R.string.oui_downloading))
                .setCancelable(true)
                .setOnCancel(() -> {
                    downloadInProgress = false;
                    if (executor != null && !executor.isShutdown()) {
                        executor.shutdownNow();
                    }
                })
                .show();

        executor = Executors.newSingleThreadExecutor();
        downloadInProgress = true;
        executor.submit(() -> {
            boolean success = downloadAndProcessOui();
            downloadInProgress = false;

            activity.runOnUiThread(() -> {
                ProgressIndicators.dismissDialog(progressDialog);

                if (success) {
                    int newCount = manufHashMap != null ? manufHashMap.size() : 0;
                    Snackbar.make(view, activity.getString(R.string.oui_update_success) + " (" + newCount + " entries)", Snackbar.LENGTH_LONG).show();
                    if (debug) Log.d("HIJACKER/UpdateOui", "Updated database with " + newCount + " entries");
                } else {
                    Snackbar.make(view, R.string.oui_update_failed, Snackbar.LENGTH_LONG).show();
                }

                // Clean up executor after download completes
                if (executor != null && !executor.isShutdown()) {
                    executor.shutdown();
                }
            });
        });
    }

    private boolean downloadAndProcessOui() {
        BufferedReader reader = null;
        FileWriter writer = null;
        Shell shell = null;

        try {
            // Download OUI database using curl
            if (debug) Log.d("HIJACKER/UpdateOui", "Downloading from: " + OUI_URL);

            String tempFile = path + "/oui_temp.txt";
            shell = getFreeShell();

            // Use curl to download the file
            // --insecure: skip certificate verification (needed on Android where CA certs may not be accessible)
            // -L: follow redirects
            // -s: silent mode (no progress bar)
            // -S: show errors even in silent mode
            // -o: output to file
            shell.run(curl + " --insecure -L -s -S -o " + tempFile + " " + OUI_URL + " 2>&1; echo ENDOFCURL");

            // Read all output from curl to ensure command has completed
            StringBuilder curlOutput = getStringBuilder(shell);

            // Fix file permissions to match app's files (rw-------)
            shell.run("chmod 600 " + tempFile);
            shell.run("chown $(stat -c '%u:%g' " + path + ") " + tempFile);

            // Check if download was successful
            File tempOuiFile = new File(tempFile);
            if (!tempOuiFile.exists() || tempOuiFile.length() == 0) {
                Log.e("HIJACKER/UpdateOui", "Download failed - file doesn't exist or is empty");
                if (curlOutput.length() > 0) {
                    Log.e("HIJACKER/UpdateOui", "curl error output:\n" + curlOutput);
                }
                if (tempOuiFile.exists()) {
                    tempOuiFile.delete();
                }
                return false;
            }

            if (debug) Log.d("HIJACKER/UpdateOui", "Downloaded " + tempOuiFile.length() + " bytes");


            // Read and process the OUI data
            reader = new BufferedReader(new FileReader(tempOuiFile));
            HashMap<String, String> newManufMap = new HashMap<>();

            // Delete old database file
            File dbFile = new File(manufDBFile);
            if (dbFile.exists()) {
                if (!dbFile.delete()) {
                    Log.e("HIJACKER/UpdateOui", "Failed to delete old database");
                }
            }

            // Create new database file
            if (!dbFile.createNewFile()) {
                Log.e("HIJACKER/UpdateOui", "Failed to create new database file");
                return false;
            }

            writer = new FileWriter(dbFile);

            String line;
            int processedLines = 0;
            int addedEntries = 0;

            while ((line = reader.readLine()) != null) {
                processedLines++;

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                // IEEE OUI format:
                // Line 1: XX-XX-XX   (hex)    ORGANIZATION_NAME
                // Line 2: (tab)(tab)ADDRESS_LINE_1
                // Line 3: (tab)(tab)ADDRESS_LINE_2
                // ...
                // We only need the first line with the hex identifier and organization name

                // Check if line starts with hex identifier (format: XX-XX-XX)
                if (line.length() >= 8 && line.charAt(2) == '-' && line.charAt(5) == '-') {
                    String macPrefix = line.substring(0, 8).replace("-", "").toUpperCase();

                    // Validate it's a valid hex string
                    if (macPrefix.matches("[0-9A-F]{6}")) {
                        // Extract organization name (after "(hex)" or "(base 16)")
                        int hexIndex = line.indexOf("(hex)");
                        if (hexIndex == -1) {
                            hexIndex = line.indexOf("(base 16)");
                        }

                        if (hexIndex != -1) {
                            String manufacturer = line.substring(hexIndex + 5).trim();
                            if (manufacturer.startsWith("(base 16)")) {
                                manufacturer = manufacturer.substring(9).trim();
                            }

                            // Only add if we have a valid manufacturer name and not already in map
                            if (!manufacturer.isEmpty() && !newManufMap.containsKey(macPrefix)) {
                                newManufMap.put(macPrefix, manufacturer);
                                writer.write(macPrefix + ";" + manufacturer + '\n');
                                addedEntries++;
                            }
                        }
                    }
                }

                // Update progress every 1000 lines
                if (debug && processedLines % 1000 == 0) {
                    Log.d("HIJACKER/UpdateOui", "Processed " + processedLines + " lines, added " + addedEntries + " entries");
                }
            }

            writer.flush();

            // Delete temp file
            tempOuiFile.delete();

            // Update the global HashMap
            manufHashMap = newManufMap;

            if (debug) {
                Log.d("HIJACKER/UpdateOui", "Update complete: " + processedLines + " lines processed, " +
                      addedEntries + " unique manufacturers added");
            }

            return true;

        } catch (IOException e) {
            Log.e("HIJACKER/UpdateOui", "Error updating OUI database", e);
            return false;
        } finally {
            // Clean up resources
            try {
                if (writer != null) writer.close();
                if (reader != null) reader.close();
                if (shell != null) shell.done();
            } catch (IOException e) {
                Log.e("HIJACKER/UpdateOui", "Error closing resources", e);
            }
        }
    }

    private static StringBuilder getStringBuilder(Shell shell) {
        BufferedReader shellOut = shell.getShell_out();
        StringBuilder curlOutput = new StringBuilder();
        if (shellOut != null) {
            try {
                String line;
                while ((line = shellOut.readLine()) != null && !line.equals("ENDOFCURL")) {
                    if (!line.trim().isEmpty()) {
                        curlOutput.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {}
        }
        return curlOutput;
    }
}

