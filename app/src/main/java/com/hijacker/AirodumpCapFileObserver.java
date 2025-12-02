package com.hijacker;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import static com.hijacker.MainActivity.busybox;
import static com.hijacker.MainActivity.cap_path;
import static com.hijacker.MainActivity.cap_tmp_path;
import static com.hijacker.Shell.getFreeShell;

class AirodumpCapFileObserver {
    static final String TAG = "HIJACKER/CapFileObs";
    final String master_path;
    Shell shell = null;
    boolean found_cap_file = false;
    private java.util.concurrent.ScheduledExecutorService scheduler = null;
    private volatile boolean watching = false;
    private final java.util.Map<String, Long> mtimes = new java.util.HashMap<>();
    public AirodumpCapFileObserver(String path, int mask) {
        master_path = path;
    }

    public void startWatching(){
        shell = getFreeShell();
        found_cap_file = false;

        try{
            // Initialize mtimes snapshot
            java.io.File dir = new java.io.File(master_path);
            if(dir.exists() && dir.isDirectory()){
                java.io.File[] files = dir.listFiles();
                if(files!=null){
                    for(java.io.File f : files){
                        mtimes.put(f.getName(), f.lastModified());
                    }
                }
            }

            watching = true;
            scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "CapFilePoller"));
            scheduler.scheduleWithFixedDelay(() -> {
                try{
                    java.io.File d = new java.io.File(master_path);
                    java.io.File[] list = d.listFiles();
                    if(list==null) return;
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for(java.io.File f : list){
                        String name = f.getName();
                        seen.add(name);
                        long lm = f.lastModified();
                        Long prev = mtimes.get(name);
                        if (prev==null) {
                            // created
                            mtimes.put(name, lm);
                            boolean isPcap = name.endsWith(".pcap");
                            if (isPcap) {
                                Airodump.capFile = master_path + '/' + name;
                                found_cap_file = true;
                            }
                        } else if (lm>prev) {
                            // modified
                            mtimes.put(name, lm);
                            boolean isPcap = name.endsWith(".pcap");
                            if (!isPcap) {
                                readCsv(master_path + '/' + name, shell);
                            }
                        }
                    }
                    // Remove deleted files from mtimes
                    // remove entries not in seen (manual iterator to avoid API 24+ removeIf)
                    java.util.Iterator<java.util.Map.Entry<String, Long>> it = mtimes.entrySet().iterator();
                    while(it.hasNext()) {
                        java.util.Map.Entry<String, Long> e = it.next();
                        if (!seen.contains(e.getKey())) it.remove();
                    }
                } catch(Throwable t) {
                    Log.e(TAG, "CapFilePoller error: " + t);
                }
            }, 0, MainActivity.airodump_update_interval, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch(Exception e) {
            Log.e(TAG, "Failed to start CapFile poller: " + e);
            watching = false;
        }
    }

    public void stopWatching() {
        watching = false;
        if (scheduler!=null) {
            try { scheduler.shutdownNow(); } catch(Exception ignored) {}
            scheduler = null;
        }

        if(shell!=null) {
            if (Airodump.writingToFile()) {
                shell.run(busybox + " mv " + Airodump.capFile + " " + cap_path + '/');
            }
            shell.run(busybox + " rm " + cap_tmp_path + "/*");
            shell.done();
            shell = null;
        }
    }

    boolean found_cap_file(){
        return found_cap_file;
    }

    void readCsv(String csv_path, @NonNull Shell shell) {
        shell.clearOutput();
        shell.run(busybox + " cat " + csv_path + "; echo ENDOFCAT");
        BufferedReader out = shell.getShell_out();
        try {

            int type = 0;           // 0 = AP, 1 = ST
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            while(true) {
                String line = out.readLine();
                if(line==null) break;
                Log.d(TAG, line);
                if("ENDOFCAT".equals(line)) break;
                if(line.isEmpty()) continue;
                if(line.startsWith("BSSID")) { type = 0; continue; }
                else if(line.startsWith("Station")) { type = 1; continue; }

                line = line.replace(", ", ",");
                String[] fields = line.split(",");
                Log.i(TAG, line);
                if (type == 0) {
                    String bssid = fields.length>0 ? fields[0] : "";
                    try { if(fields.length>1) sdf.parse(fields[1]); if(fields.length>2) sdf.parse(fields[2]); }catch(ParseException e){ Log.e(TAG, "Date parse error: " + e); }
                    int ch = 0, pwr = 0, beacons = 0, data = 0, id_length = 0;
                    String enc = null, cipher = null, auth = null, wps = null;
                    try{ if(fields.length>3) ch = Integer.parseInt(fields[3].replace(" ", "")); }catch(Exception ignored){ }
                    try{ if(fields.length>8) pwr = Integer.parseInt(fields[8].replace(" ", "")); }catch(Exception ignored){ }
                    try{ if(fields.length>9) beacons = Integer.parseInt(fields[9].replace(" ", "")); }catch(Exception ignored){ }
                    try{ if(fields.length>10) data = Integer.parseInt(fields[10].replace(" ", "")); }catch(Exception ignored){ }
                    try{ if(fields.length>12) id_length = Integer.parseInt(fields[12].replace(" ", "")); }catch(Exception ignored){ }
                    if(fields.length>5) enc = fields[5];
                    if(fields.length>6) cipher = fields[6];
                    if(fields.length>7) auth = fields[7];

                    // Check for WPS information (typically in field 11 with --wps flag)
                    // WPS field format is usually empty or contains WPS version like "2.0"
                    if(fields.length>11) {
                        String wpsField = fields[11].trim();
                        if(!wpsField.isEmpty() && !wpsField.equals("0")) {
                            wps = wpsField;
                        }
                    }

                    String essid = null;
                    if(id_length > 0 && fields.length>13) essid = fields[13];

                    // Add AP and mark WPS status if detected
                    Airodump.addAP(essid, bssid, enc, cipher, auth, pwr, beacons, data, 0, ch);

                    // If WPS is detected, mark the AP as WPS-enabled
                    if(wps != null) {
                        AP ap = AP.getAPByMac(bssid);
                        if(ap != null) {
                            ap.setWpsEnabled();
                            if(MainActivity.debug) Log.d(TAG, "WPS detected for " + bssid + " (version: " + wps + ")");
                        }
                    }
                } else {
                    String mac = fields.length>0 ? fields[0] : null;
                    try{ if(fields.length>1) sdf.parse(fields[1]); if(fields.length>2) sdf.parse(fields[2]); }catch(ParseException e){ Log.e(TAG, "Date parse error: " + e); }
                    int pwr = 0, packets = 0;
                    if(fields.length>3){ try{ pwr = Integer.parseInt(fields[3].replace(" ", "")); }catch(Exception ignored){} }
                    if(fields.length>4){ try{ packets = Integer.parseInt(fields[4].replace(" ", "")); }catch(Exception ignored){} }
                    String bssid = null; if(fields.length>5){ bssid = fields[5]; if(bssid!=null && !bssid.isEmpty() && bssid.charAt(0)=='(') bssid = null; }
                    StringBuilder probes = new StringBuilder(); if(fields.length>6){ for(int i=6;i<fields.length;i++){ if(fields[i]!=null && !fields[i].isEmpty()){ if(probes.length()>0) probes.append(", "); probes.append(fields[i]); } } }
                    Airodump.addST(mac, bssid, probes.toString(), pwr, 0, packets);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading CSV: " + e);
        }
    }
}
