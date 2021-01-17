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

import android.annotation.SuppressLint;
import android.os.FileObserver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import static com.hijacker.AP.getAPByMac;
import static com.hijacker.MainActivity.BAND_2;
import static com.hijacker.MainActivity.BAND_5;
import static com.hijacker.MainActivity.BAND_BOTH;
import static com.hijacker.MainActivity.airodump_dir;
import static com.hijacker.MainActivity.always_cap;
import static com.hijacker.MainActivity.band;
import static com.hijacker.MainActivity.busybox;
import static com.hijacker.MainActivity.cap_path;
import static com.hijacker.MainActivity.cap_tmp_path;
import static com.hijacker.MainActivity.debug;
import static com.hijacker.MainActivity.enable_monMode;
import static com.hijacker.MainActivity.enable_on_airodump;
import static com.hijacker.MainActivity.iface;
import static com.hijacker.MainActivity.last_action;
import static com.hijacker.MainActivity.last_airodump;
import static com.hijacker.MainActivity.notification;
import static com.hijacker.MainActivity.prefix;
import static com.hijacker.MainActivity.refreshState;
import static com.hijacker.MainActivity.runInHandler;
import static com.hijacker.MainActivity.menu;
import static com.hijacker.MainActivity.stopWPA;
import static com.hijacker.R.string.*;
import static com.hijacker.ST.getSTByMac;
import static com.hijacker.Shell.getFreeShell;
import static com.hijacker.Shell.runOne;

public class Airodump{
    static final String TAG = "HIJACKER/Airodump";
    private static int channel = 0;
    private static boolean forWPA = false, forWEP = false, running = false;
    private static String mac = null;
    private static String capFile = null;
    static CapFileObserver capFileObserver = null;

    static void reset(){
        stop();
        channel = 0;
        forWPA = false;
        forWEP = false;
        mac = null;
        capFile = null;
    }
    static void setChannel(int ch){
        if(isRunning()){
            Log.e(TAG, "Can't change settings while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        channel = ch;
    }
    public static void setMac(String new_mac){
        if(isRunning()){
            Log.e(TAG, "Can't change settings while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        mac = new_mac;
    }
    static void setForWPA(){
        if(isRunning()){
            Log.e(TAG, "Can't change settings while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        if(forWEP){
            Log.e(TAG, "Can't set forWPA when forWEP is enabled");
            throw new IllegalStateException("Tried to set forWPA when forWEP is enabled");
        }
        forWPA = true;
    }
    static void setForWEP(){
        if(isRunning()){
            Log.e(TAG, "Can't change setting while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        if(forWPA){
            Log.e(TAG, "Can't set forWEP when forWPA is enabled");
            throw new IllegalStateException("Tried to set forWEP when forWPA is enabled");
        }
        forWEP = true;
    }
    static void setAP(AP ap){
        if(isRunning()){
            Log.e(TAG, "Can't change setting while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        mac = ap.mac;
        channel = ap.ch;
    }
    static int getChannel(){ return channel; }
    public static String getMac(){ return mac; }
    static String getCapFile(){
        capFileObserver.found_cap_file();
        return capFile;
    }
    static boolean writingToFile(){ return (forWEP || forWPA || always_cap) && isRunning(); }
    static void startClean(){
        reset();
        start();
    }
    static void startClean(AP ap){
        reset();
        setAP(ap);
        start();
    }
    static void startClean(int ch){
        reset();
        setChannel(ch);
        start();
    }
    public static void start(){
        // Construct the command
        String cmd = "su -c " + prefix + " " + airodump_dir + " --update 9999999 --write-interval 1 --band ";

        if(band==BAND_5 || band==BAND_BOTH || channel>20) cmd += "a";
        if((band==BAND_2 || band==BAND_BOTH) && channel<=20) cmd += "bg";

        cmd += " -w " + cap_tmp_path;

        if(forWPA) cmd += "/handshake --output-format pcap,csv ";
        else if(forWEP) cmd += "/wep_ivs  --output-format pcap,csv ";
        else if(always_cap) cmd += "/cap  --output-format pcap,csv ";
        else cmd += "/cap  --output-format csv ";

        // If we are starting for WEP capture, capture only IVs
        if(forWEP) cmd += "--ivs ";

        // If we have a valid channel, select it (airodump does not recognize 5ghz channels here)
        if(channel>0 && channel<20) cmd += "--channel " + channel + " ";

        // If we have a specific MAC, listen for it
        if(mac!=null) cmd += "--bssid " + mac + " ";

        cmd += iface;

        // Enable monitor mode
        if(enable_on_airodump) runOne(enable_monMode);

        // Stop any airodump instances
        stop();

        capFile = null;
        running = true;
        capFileObserver.startWatching();

        if(debug) Log.d("HIJACKER/Airodump.start", cmd);
        try{
            Runtime.getRuntime().exec(cmd);
            last_action = System.currentTimeMillis();
            last_airodump = cmd;
        }catch(IOException e){
            e.printStackTrace();
            Log.e("HIJACKER/Exception", "Caught Exception in Airodump.start() read thread: " + e.toString());
        }

        runInHandler(() -> {
            if(menu!=null){
                menu.getItem(1).setIcon(R.drawable.stop_drawable);
                menu.getItem(1).setTitle(stop);
            }
            refreshState();
            notification();
        });
    }
    static void stop(){
        last_action = System.currentTimeMillis();
        running = false;
        capFileObserver.stopWatching();
        runInHandler(() -> {
            if(menu!=null){
                menu.getItem(1).setIcon(R.drawable.start_drawable);
                menu.getItem(1).setTitle(start);
            }
        });
        stopWPA();
        runOne(busybox + " kill $(" + busybox + " pidof airodump-ng)");
        AP.saveAll();
        ST.saveAll();

        runInHandler(() -> {
            refreshState();
            notification();
        });
    }
    static boolean isRunning(){
        return running;
    }
    public static void addAP(String essid, String mac, String enc, String cipher, String auth,
                             int pwr, int beacons, int data, int ivs, int ch){
        AP temp = getAPByMac(mac);

        if(temp==null) new AP(essid, mac, enc, cipher, auth, pwr, beacons, data, ivs, ch);
        else temp.update(essid, enc, cipher, auth, pwr, beacons, data, ivs, ch);
    }
    public static void addST(String mac, String bssid, String probes, int pwr, int lost, int frames){
        ST temp = getSTByMac(mac);

        if (temp == null) new ST(mac, bssid, pwr, lost, frames, probes);
        else temp.update(bssid, pwr, lost, frames, probes);
    }


    static class CapFileObserver extends FileObserver{
        static final String TAG = "HIJACKER/CapFileObs";
        public final String master_path;
        Shell shell = null;
        public boolean found_cap_file = false;
        public CapFileObserver(String path, int mask) {
            super(path, mask);
            master_path = path;
        }
        @Override
        public void onEvent(int event, @Nullable String path){
            if(path==null){
                Log.e(TAG, "Received event " + event + " for null path");
                return;
            }
            boolean isPcap = path.endsWith(".pcap");

            switch(event){
                case FileObserver.CREATE:
                    // Airodump started, pcap or csv file was just created

                    if(isPcap){
                        capFile = master_path + '/' + path;
                        found_cap_file = true;
                    }
                    break;

                case FileObserver.MODIFY:
                    // Airodump just updated pcap or csv
                    if(!isPcap){
                        readCsv(master_path + '/' + path, shell);
                    }
                    break;

                default:
                    // Unknown event received (should never happen)
                    Log.e(TAG, "Unknown event received: " + event);
                    Log.e(TAG, "for file " + path);
                    break;
            }
        }
        @Override
        public void startWatching(){
            super.startWatching();
            shell = getFreeShell();

            found_cap_file = false;
        }
        @Override
        public void stopWatching(){
            super.stopWatching();

            if(shell!=null) {
                if (writingToFile()) {
                    shell.run(busybox + " mv " + capFile + " " + cap_path + '/');
                }
                shell.run(busybox + " rm " + cap_tmp_path + "/*");
                shell.done();
                shell = null;
            }
        }
        public void found_cap_file(){
        }
        public void readCsv(String csv_path, @NonNull Shell shell){
            shell.clearOutput();
            shell.run(busybox + " cat " + csv_path + "; echo ENDOFCAT");
            BufferedReader out = shell.getShell_out();
            try {

                int type = 0;           // 0 = AP, 1 = ST
                @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                do {
                    String line = out.readLine();
                    Log.d(TAG, line);
                    if (line.equals("ENDOFCAT"))
                        break;

                    if (line.equals(""))
                        continue;
                    if (line.startsWith("BSSID")) {
                        type = 0;
                        continue;
                    } else if (line.startsWith("Station")) {
                        type = 1;
                        continue;
                    }

                    line = line.replace(", ", ",");
                    String[] fields = line.split(",");
                    Log.i(TAG, line);
                    if (type == 0) {
                        // Parse AP
                        // BSSID, First time seen, Last time seen, channel, Speed, Privacy,Cipher,
                        // Authentication, Power, # beacons, # IVs (or data??), LAN IP, ID-length, ESSID, Key

                        String bssid = fields[0];
                        try {
                            sdf.parse(fields[1]);
                            sdf.parse(fields[2]);
                        } catch (ParseException e) {
                            e.printStackTrace();
                            Log.e(TAG, e.toString());
                        }
                        int ch = Integer.parseInt(fields[3].replace(" ", ""));
                        Integer.parseInt(fields[4].replace(" ", ""));
                        String enc = fields[5];
                        String cipher = fields[6];
                        String auth = fields[7];
                        int pwr = Integer.parseInt(fields[8].replace(" ", ""));
                        int beacons = Integer.parseInt(fields[9].replace(" ", ""));
                        int data = Integer.parseInt(fields[10].replace(" ", ""));
                        fields[11].replace(" ", "");
                        int id_length = Integer.parseInt(fields[12].replace(" ", ""));
                        String essid = id_length > 0 ? fields[13] : null;

                        addAP(essid, bssid, enc, cipher, auth, pwr, beacons, data, 0, ch);
                    } else {
                        // Parse ST
                        //Station MAC, First time seen, Last time seen, Power, # packets, BSSID, Probed ESSIDs

                        String mac = fields[0];
                        try {
                            sdf.parse(fields[1]);
                            sdf.parse(fields[2]);
                        } catch (ParseException e) {
                            e.printStackTrace();
                            Log.e(TAG, e.toString());
                        }
                        int pwr = Integer.parseInt(fields[3].replace(" ", ""));
                        int packets = Integer.parseInt(fields[4].replace(" ", ""));
                        String bssid = fields[5];
                        if (bssid.charAt(0) == '(') bssid = null;

                        StringBuilder probes = new StringBuilder();
                        if (fields.length == 7) {
                            probes = new StringBuilder(fields[6]);
                        } else if (fields.length > 7) {
                            // Multiple probes are separated by comma, so concatenate them
                            probes = new StringBuilder();
                            for (int i = 6; i < fields.length; i++) {
                                probes.append(fields[i]).append(", ");
                            }
                            probes = new StringBuilder(probes.substring(0, probes.length() - 2));
                        }

                        addST(mac, bssid, probes.toString(), pwr, 0, packets);
                    }
                } while (true);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        }
    }
}