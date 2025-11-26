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

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.hijacker.AP.getAPByMac;
import static com.hijacker.MainActivity.BAND_2;
import static com.hijacker.MainActivity.BAND_5;
import static com.hijacker.MainActivity.BAND_BOTH;
import static com.hijacker.MainActivity.airodump_dir;
import static com.hijacker.MainActivity.always_cap;
import static com.hijacker.MainActivity.band;
import static com.hijacker.MainActivity.busybox;
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
import static com.hijacker.ST.getSTByMac;
import static com.hijacker.Shell.getFreeShell;
import static com.hijacker.Shell.runOne;

class Airodump{
    static final String TAG = "HIJACKER/Airodump";
    // Tracks whether airodump explicitly toggled the driver con_mode to monitor (1)
    private static boolean conModeToggled = false;
    private static int channel = 0;
    private static boolean forWPA = false, forWEP = false, running = false;
    private static String mac = null;
    static String capFile = null;
    static AirodumpCapFileObserver capFileObserver = null;

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
    static void setMac(String new_mac) {
        if(isRunning()){
            Log.e(TAG, "Can't change settings while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        mac = new_mac;
    }
    static void setForWPA() {
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
    static void setForWEP() {
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
    static String getMac(){ return mac; }
    static String getCapFile(){
        while(capFileObserver == null || (!capFileObserver.found_cap_file() && writingToFile())){}
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
    static void start(){
        // Construct the command
        String cmd = getString();

        // Always log key runtime settings to help debugging when 'debug' flag may be off
        Log.d(TAG, "start(): enable_on_airodump=" + enable_on_airodump + " iface='" + iface + "' prefix='" + prefix + "'");

        // Force monitor mode attempt regardless of preference (for diagnostics/testing)
        Log.w(TAG, "Forcing con_mode probe/write regardless of enable_on_airodump preference");

        try{
            if(iface!=null && iface.startsWith("wlan0")){
                Shell probeShell = getFreeShell();
                Log.d(TAG, "Probing for /sys/module/wlan/parameters/con_mode (iface='" + iface + "')");
                probeShell.run("if [ -e /sys/module/wlan/parameters/con_mode ]; then echo EXISTS; else echo NO; fi; echo ENDCHK");
                String probeResult = MainActivity.getLastLine(probeShell.getShell_out(), "ENDCHK");
                probeShell.done();
                Log.d(TAG, "Probe result: '" + probeResult + "'");
                if(probeResult != null && "EXISTS".equals(probeResult.trim())){
                    Log.d(TAG, "con_mode found, attempting direct su -c write to enable monitor via sysfs (iface='" + iface + "')");
                    try{
                        String suWrite = "ip link set " + iface + " down; sh -c 'echo 4 > /sys/module/wlan/parameters/con_mode' 2>&1; cat /sys/module/wlan/parameters/con_mode; ip link set " + iface + " up";
                        String out = runSuAndCapture(suWrite);
                        Log.d(TAG, "Direct su write output: '" + out + "'");
                        String verifyVal = null;
                        if(out!=null){
                            String[] lines = out.split("\\r?\\n");
                            for(int i=lines.length-1;i>=0;i--){
                                String l = lines[i].trim();
                                if(!l.isEmpty()){ verifyVal = l; break; }
                            }
                        }

                        if(verifyVal==null || !"4".equals(verifyVal.trim())){
                            if(busybox!=null && !busybox.isEmpty()){
                                String suBusy = "ip link set " + iface + " down; echo 4 | " + busybox + " tee /sys/module/wlan/parameters/con_mode 2>&1; cat /sys/module/wlan/parameters/con_mode; ip link set " + iface + " up";
                                String out2 = runSuAndCapture(suBusy);
                                Log.d(TAG, "Direct busybox write output: '" + out2 + "'");
                                if(out2!=null){
                                    String[] lines = out2.split("\\r?\\n");
                                    for(int i=lines.length-1;i>=0;i--){
                                        String l = lines[i].trim();
                                        if(!l.isEmpty()){ verifyVal = l; break; }
                                    }
                                }
                            }
                        }

                        if(verifyVal != null && verifyVal.trim().matches("\\d+") && !"0".equals(verifyVal.trim())){
                            conModeToggled = true;
                            Log.d(TAG, "conModeToggled set to true (verified via direct su), con_mode='" + verifyVal.trim() + "'");
                        }else{
                            Log.e(TAG, "Direct su writes failed to set con_mode (value='" + verifyVal + "'), falling back to enable_monMode");
                            runOne(enable_monMode);
                        }
                    }catch(Exception w){
                        Log.e(TAG, "Direct su -c attempt failed: " + w);
                        runOne(enable_monMode);
                    }
                }else{
                    Log.d(TAG, "con_mode not present, falling back to enable_monMode");
                    runOne(enable_monMode);
                }
            }else{
                runOne(enable_monMode);
            }
        }catch(Exception e){
            Log.e(TAG, "Failed to probe/enable sysfs monitor mode: " + e);
            runOne(enable_monMode);
        }

        capFile = null;
        running = true;
        if(capFileObserver==null) capFileObserver = new AirodumpCapFileObserver(cap_tmp_path, 0);
        capFileObserver.startWatching();

        if(debug) Log.d("HIJACKER/Airodump.start", cmd);
        try{
            Runtime.getRuntime().exec(cmd);
            last_action = System.currentTimeMillis();
            last_airodump = cmd;
        }catch(IOException e){
            e.printStackTrace();
            Log.e("HIJACKER/Exception", "Caught Exception in Airodump.start() read thread: " + e);
        }

        runInHandler(() -> {
            if(menu!=null){
                menu.getItem(1).setIcon(R.drawable.stop_drawable);
                menu.getItem(1).setTitle(R.string.stop);
            }
            refreshState();
            notification();
        });
    }

    private static String getString() {
        String cmd = "su -c " + prefix + " " + airodump_dir + " --update 9999999 --write-interval 1 --band ";

        if(band==BAND_5 || band==BAND_BOTH || channel>20) cmd += "a";
        if((band==BAND_2 || band==BAND_BOTH) && channel<=20) cmd += "bg";

        cmd += " -w " + cap_tmp_path;

        if(forWPA) cmd += "/handshake --output-format pcap,csv ";
        else if(forWEP) cmd += "/wep_ivs  --output-format pcap,csv ";
        else if(always_cap) cmd += "/cap  --output-format pcap,csv ";
        else cmd += "/cap  --output-format csv ";

        if(forWEP) cmd += "--ivs ";

        if(channel>0 && channel<20) cmd += "--channel " + channel + " ";

        if(mac!=null) cmd += "--bssid " + mac + " ";

        cmd += iface;
        return cmd;
    }

    static void stop(){
        last_action = System.currentTimeMillis();
        running = false;
        if(capFileObserver!=null) capFileObserver.stopWatching();
        runInHandler(() -> {
            if(menu!=null){
                menu.getItem(1).setIcon(R.drawable.start_drawable);
                menu.getItem(1).setTitle(R.string.start);
            }
        });
        stopWPA();
        runOne(busybox + " kill $(" + busybox + " pidof airodump-ng)");
        try{
            if(iface!=null && iface.startsWith("wlan0")){
                Shell probeShell = getFreeShell();
                probeShell.run("if [ -e /sys/module/wlan/parameters/con_mode ]; then echo EXISTS; else echo NO; fi; echo ENDCHK");
                String probeResult = MainActivity.getLastLine(probeShell.getShell_out(), "ENDCHK");
                probeShell.done();
                if(probeResult != null && "EXISTS".equals(probeResult.trim())){
                    boolean shouldReset = conModeToggled;
                    if(!shouldReset){
                        Shell checkShell = getFreeShell();
                        Log.d(TAG, "Checking current con_mode value");
                        checkShell.run("cat /sys/module/wlan/parameters/con_mode; echo ENDCHK");
                        String val = MainActivity.getLastLine(checkShell.getShell_out(), "ENDCHK");
                        checkShell.done();
                        Log.d(TAG, "Current con_mode value: '" + val + "'");
                        if(val != null && val.trim().matches("\\d+") && !"0".equals(val.trim())) shouldReset = true;
                    }
                    if(shouldReset){
                        Log.d(TAG, "Resetting monitor mode via sysfs for wlan0 (shouldReset=" + shouldReset + ")");
                        try{
                            String suReset = "ip link set " + iface + " down; sh -c 'echo 0 > /sys/module/wlan/parameters/con_mode' 2>&1; cat /sys/module/wlan/parameters/con_mode; ip link set " + iface + " up";
                            String outR = runSuAndCapture(suReset);
                            if(debug) Log.d(TAG, "Direct su reset output: '" + outR + "'");
                            String verifyValR = null;
                            if(outR!=null){
                                String[] lines = outR.split("\\r?\\n");
                                for(int i=lines.length-1;i>=0;i--){
                                    String l = lines[i].trim();
                                    if(!l.isEmpty()){ verifyValR = l; break; }
                                }
                            }

                            if(verifyValR==null || !"0".equals(verifyValR.trim())){
                                if(busybox!=null && !busybox.isEmpty()){
                                    String suBusyR = "ip link set " + iface + " down; echo 0 | " + busybox + " tee /sys/module/wlan/parameters/con_mode 2>&1; cat /sys/module/wlan/parameters/con_mode; ip link set " + iface + " up";
                                    String outR2 = runSuAndCapture(suBusyR);
                                    if(debug) Log.d(TAG, "Direct busybox reset output: '" + outR2 + "'");
                                    if(outR2!=null){
                                        String[] lines = outR2.split("\\r?\\n");
                                        for(int i=lines.length-1;i>=0;i--){
                                            String l = lines[i].trim();
                                            if(!l.isEmpty()){ verifyValR = l; break; }
                                        }
                                    }
                                }
                            }

                            if(verifyValR != null && "0".equals(verifyValR.trim())){
                                conModeToggled = false;
                                try{
                                    String wifiOut = runSuAndCapture("svc wifi enable; sleep 2; echo WIFI_DONE");
                                    Log.d(TAG, "svc wifi enable output: '" + wifiOut + "'");
                                }catch(Exception ex){
                                    Log.e(TAG, "Failed to run 'svc wifi enable': " + ex);
                                }
                            }else{
                                Log.e(TAG, "Failed to reset con_mode to 0 (value='" + verifyValR + "')");
                            }
                        }catch(Exception w){
                            Log.e(TAG, "Failed to reset con_mode via direct su: " + w);
                        }
                    }
                }
            }
        }catch(Exception e){
            Log.e(TAG, "Failed to probe/reset con_mode: " + e);
        }
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
    static void analyzeAirodumpString(String buffer, int mode){
        int i, j;

        // Remove trailing spaces
        while(buffer.endsWith(" "))
            buffer = buffer.substring(0, buffer.length()-1);

        if(buffer.length()<3) return;
        if( buffer.charAt(3)==':' || buffer.charAt(3)=='o' ){
            //logd("Found ':' or 'o' @ 3");
            while(buffer.charAt(buffer.length()-1)=='\n'){
                buffer = buffer.substring(0, buffer.length()-1);
            }

            //Clear spaces
            for(i=123; i<buffer.length(); i++){
                if(buffer.charAt(i)==' ' && buffer.charAt(i+1)==' '){
                    for(j=i;j<buffer.length();j++){
                        buffer = buffer.substring(0, 123) + buffer.substring(124);
                    }
                    i--;
                }
            }
            if(buffer.charAt(22)==':'){
                //logd("0         1         2         3         4         5         6");
                //logd("0123456789012345678901234567890123456789012345678901234567890");
                //logd(buffer);
                //st
                String st_mac, bssid, probes;
                int pwr, lost, frames;

                st_mac = buffer.substring(20, 37);

                if(buffer.charAt(1)=='(') bssid = "na";
                else bssid = buffer.substring(1, 18);

                pwr = Integer.parseInt(buffer.substring(37, 43).replace(" ", ""));
                lost = Integer.parseInt(buffer.substring(52, 58).replace(" ", ""));
                frames = Integer.parseInt(buffer.substring(58, 67).replace(" ", ""));
                if(buffer.length()>=69) probes = buffer.substring(69);
                else probes = "";

                addST(st_mac, bssid, probes, pwr, lost, frames);
            }else{
                //ap
                String bssid, enc, cipher, auth, essid;
                int pwr, beacons, data, ivs, ch;

                bssid = buffer.substring(1, 17);

                pwr = Integer.parseInt(buffer.substring(18, 23).replace(" ", ""));

                //if mode is not 0 then airodump-ng is running for a specific channel
                //so we need to bypass 4 characters after pwr to get the correct results because there is one extra column
                int offset = mode==0 ? 0 : 4;
                buffer = buffer.substring(offset);

                beacons = Integer.parseInt(buffer.substring(23, 32).replace(" ", ""));
                data = Integer.parseInt(buffer.substring(32, 41).replace(" ", ""));
                ivs = Integer.parseInt(buffer.substring(41, 46).replace(" ", ""));
                ch = Integer.parseInt(buffer.substring(48, 50).replace(" ", ""));
                enc = buffer.substring(57, 61).replace(" ", "");
                cipher = buffer.substring(62, 66);
                auth = buffer.substring(69, 73).replace(" ", "");

                if(buffer.charAt(74)!='<') essid = buffer.substring(74);
                else essid = "<hidden>";

                addAP(essid, bssid, enc, cipher, auth, pwr, beacons, data, ivs, ch);
            }
        }
    }

    // Run a single root command via su -c and capture stdout (returns combined stdout text)
    private static String runSuAndCapture(String command){
        try{
            Process p = Runtime.getRuntime().exec(new String[]{"su","-c",command});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();

            // Start a waiter thread that blocks on p.waitFor(); we will poll that thread to implement a timeout
            Thread waiter = new Thread(() -> {
                try{ p.waitFor(); }catch(InterruptedException ignored){}
            });
            waiter.start();

            long start = System.currentTimeMillis();
            long timeoutMs = 3000;

            // Poll for output and for process termination until timeout
            while(System.currentTimeMillis() - start < timeoutMs){
                try{
                    while(r.ready()){
                        String line = r.readLine();
                        if(line==null) break;
                        sb.append(line).append('\n');
                    }
                }catch(IOException ignored){}

                if(!waiter.isAlive()) break;

                try{ Thread.sleep(50); }catch(InterruptedException ignored){}
            }

            try{ while(r.ready()){ String line = r.readLine(); if(line==null) break; sb.append(line).append('\n'); } }catch(IOException ignored){}

            if(waiter.isAlive()){ try{ p.destroy(); }catch(Exception ignored){} waiter.interrupt(); }

            return sb.toString();
        }catch(Exception e){
            Log.e(TAG, "runSuAndCapture exception: " + e);
            return null;
        }
    }
}
