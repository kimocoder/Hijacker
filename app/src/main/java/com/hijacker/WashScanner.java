package com.hijacker;

/*
    WPS Scanner using wash tool

    This class demonstrates how to scan for WPS-enabled networks using the wash tool.
    It should be run in parallel with Airodump to detect which networks have WPS enabled.

    Usage:
    WashScanner scanner = new WashScanner();
    scanner.start();  // Start scanning
    // ... scanning happens in background ...
    scanner.stop();   // Stop scanning
*/

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.hijacker.MainActivity.busybox;
import static com.hijacker.MainActivity.debug;
import static com.hijacker.MainActivity.iface;
import static com.hijacker.MainActivity.prefix;

public class WashScanner {
    private static final String TAG = "HIJACKER/WashScanner";
    private static boolean running = false;
    private static Process washProcess = null;
    private static Thread readerThread = null;

    /**
     * Start the wash scanner to detect WPS-enabled networks
     */
    public static void start() {
        if(running) {
            if(debug) Log.d(TAG, "Wash scanner already running");
            return;
        }

        running = true;

        // Build wash command
        // wash -i interface -C (continuous mode) -s (suppress header)
        String washCmd = "wash -i " + iface + " -C -s";

        // Add prefix if needed
        String fullCmd;
        if(prefix != null && !prefix.trim().isEmpty()) {
            fullCmd = "su -c " + prefix.trim() + " " + washCmd;
        } else {
            fullCmd = "su -c " + washCmd;
        }

        if(debug) Log.d(TAG, "Starting wash: " + fullCmd);

        try {
            washProcess = Runtime.getRuntime().exec(fullCmd);

            // Start thread to read wash output
            readerThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(washProcess.getInputStream())
                    );

                    String line;
                    while(running && (line = reader.readLine()) != null) {
                        parsWashLine(line);
                    }
                } catch (IOException e) {
                    if(running) {
                        Log.e(TAG, "Error reading wash output: " + e);
                    }
                }
            }, "WashReaderThread");

            readerThread.start();

        } catch (IOException e) {
            Log.e(TAG, "Failed to start wash: " + e);
            running = false;
        }
    }

    /**
     * Stop the wash scanner
     */
    public static void stop() {
        if(!running) {
            return;
        }

        if(debug) Log.d(TAG, "Stopping wash scanner");
        running = false;

        // Kill wash process
        if(washProcess != null) {
            try {
                washProcess.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error destroying wash process: " + e);
            }
            washProcess = null;
        }

        // Wait for reader thread to finish
        if(readerThread != null) {
            try {
                readerThread.join(1000);
            } catch (InterruptedException e) {
                if(debug) Log.d(TAG, "Reader thread interrupted");
            }
            readerThread = null;
        }

        // Alternative: use busybox to kill wash by name
        try {
            Runtime.getRuntime().exec("su -c " + busybox + " killall wash");
        } catch (IOException e) {
            if(debug) Log.d(TAG, "Failed to killall wash: " + e);
        }
    }

    /**
     * Parse a line of wash output
     * Expected format: BSSID               Ch  dBm  WPS  Lck  Vendor    ESSID
     * Example:         74:3A:EF:CF:52:AF    1  -87  2.0  No   AtherosC  MyNetwork
     */
    private static void parsWashLine(String line) {
        if(line == null || line.trim().isEmpty()) {
            return;
        }

        // Skip header line
        if(line.startsWith("BSSID") || line.startsWith("---")) {
            return;
        }

        if(debug) Log.d(TAG, "Wash: " + line);

        try {
            // Parse line - format is space-separated but with variable spacing
            String[] parts = line.trim().split("\\s+");

            if(parts.length < 6) {
                if(debug) Log.d(TAG, "Invalid wash line (too few fields): " + line);
                return;
            }

            // Extract BSSID (first field, MAC address format)
            String bssid = parts[0];

            // Validate BSSID format (XX:XX:XX:XX:XX:XX)
            if(!bssid.matches("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) {
                if(debug) Log.d(TAG, "Invalid BSSID format: " + bssid);
                return;
            }

            // Get the AP object
            AP ap = AP.getAPByMac(bssid);
            if(ap != null) {
                // Mark this AP as WPS-enabled
                ap.setWpsEnabled();
                if(debug) Log.d(TAG, "Marked AP as WPS-enabled: " + bssid);
            } else {
                if(debug) Log.d(TAG, "AP not found for WPS BSSID: " + bssid);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing wash line: " + e);
            if(debug) Log.d(TAG, "Line was: " + line);
        }
    }

    /**
     * Check if wash scanner is running
     */
    public static boolean isRunning() {
        return running;
    }
}

