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
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;

import java.io.IOException;
import java.io.BufferedReader;
import java.util.List;
import java.util.ArrayList;
import com.hijacker.ToolStatus;
import com.hijacker.ToolStatusAdapter;

import static com.hijacker.MainActivity.CHROOT_BIN_MISSING;
import static com.hijacker.MainActivity.CHROOT_DIR_MISSING;
import static com.hijacker.MainActivity.CHROOT_FOUND;
import static com.hijacker.MainActivity.PROCESS_AIREPLAY;
import static com.hijacker.MainActivity.PROCESS_AIRODUMP;
import static com.hijacker.MainActivity.PROCESS_MDK_BF;
import static com.hijacker.MainActivity.PROCESS_MDK_DOS;
import static com.hijacker.MainActivity.PROCESS_REAVER;
import static com.hijacker.MainActivity.checkChroot;
import static com.hijacker.MainActivity.iface;
import static com.hijacker.MainActivity.last_action;
import static com.hijacker.MainActivity.loadPreferences;
import static com.hijacker.MainActivity.mdk4bf_dir;
import static com.hijacker.MainActivity.notif_on;
import static com.hijacker.MainActivity.prefix;
import static com.hijacker.MainActivity.airodump_dir;
import static com.hijacker.MainActivity.aireplay_dir;
import static com.hijacker.MainActivity.reaver_dir;
import static com.hijacker.MainActivity.enable_monMode;
import static com.hijacker.MainActivity.getPIDs;
import static com.hijacker.MainActivity.runInHandler;
import static com.hijacker.MainActivity.stop;
import static com.hijacker.Shell.runOne;
import static com.hijacker.Shell.getFreeShell;

public class TestDialog extends DialogFragment {
    static final int TEST_WAIT = 500;
    View dialogView;
    TextView test_cur_cmd;
    ProgressBar test_progress;
    // Prevent concurrent retries
    private volatile boolean enableRetryRunning = false;
    // Track whether we enabled monitor mode via con_mode or via fallback command
    private boolean conModeEnabled = false;
    private boolean fallbackMonEnabled = false;
    Thread thread;
    ToolStatusAdapter toolAdapter;

    // Build the command body that will be passed to 'su -c'. Include prefix only when non-empty.
    private String buildBody(String exe, String args) {
        String trimmedPrefix = (prefix == null) ? "" : prefix.trim();
        String formattedArgs = (args == null || args.isEmpty()) ? "" : " " + args;
        return trimmedPrefix.isEmpty() ? exe + formattedArgs : trimmedPrefix + " " + exe + formattedArgs;
    }

    // Helper to retry enabling monitor mode from UI (runs in background)
    private void retryEnableMonitor(){
        if(enableRetryRunning) return;
        enableRetryRunning = true;
        new Thread(() -> {
            final String cmdMonMode = enable_monMode;
            boolean monEnabled = false;
            runInHandler(() -> test_cur_cmd.setText(getString(R.string.mon_status_enabling, cmdMonMode)));
            Log.d("HIJACKER/test_thread", "Manual retry: " + cmdMonMode);
            try{
                // First try the kernel driver sysfs 'con_mode' method (direct su + busybox fallback)
                boolean conOk = enableViaConMode();
                if(!conOk){
                    // Fallback to configured enable command
                    runOne(cmdMonMode);
                }
                final int maxWaitMs = 3000;
                final int intervalMs = 200;
                int waited = 0;
                while(waited < maxWaitMs){
                    try{ Thread.sleep(intervalMs); }catch(InterruptedException ie){ Thread.currentThread().interrupt(); break; }
                    waited += intervalMs;
                    try{
                        if(MainActivity.isInterfaceInMonitor(iface)){
                            monEnabled = true;
                            break;
                        }
                    }catch(Exception ignored){ }
                }
                if(monEnabled) runInHandler(() -> test_cur_cmd.setText(getString(R.string.mon_status_enabled, cmdMonMode)));
                else runInHandler(() -> test_cur_cmd.setText(getString(R.string.mon_status_not_confirmed, cmdMonMode)));
            }catch(Exception e){
                Log.e("HIJACKER/test_thread", "Retry enable failed: " + e);
                runInHandler(() -> test_cur_cmd.setText(getString(R.string.mon_status_not_confirmed, cmdMonMode)));
            }finally{
                enableRetryRunning = false;
            }
        }, "RetryEnableThread").start();
    }

    // Attempt to enable monitor mode using driver sysfs con_mode write (returns true if write appeared successful)
    private boolean enableViaConMode(){
        if(iface==null || !iface.startsWith("wlan")) return false;
        try{
            Shell probeShell = getFreeShell();
            probeShell.run("if [ -e /sys/module/wlan/parameters/con_mode ]; then echo EXISTS; else echo NO; fi; echo ENDCHK");
            String probeResult = MainActivity.getLastLine(probeShell.getShell_out(), "ENDCHK");
            probeShell.done();
            if(probeResult!=null && "EXISTS".equals(probeResult.trim())){
                // try direct su write
                String suWrite = "ip link set " + iface + " down; sh -c 'echo 4 > /sys/module/wlan/parameters/con_mode' 2>&1; cat /sys/module/wlan/parameters/con_mode; ip link set " + iface + " up";
                String out = runSuAndCapture(suWrite);
                String verifyVal = null;
                if(out!=null){
                    String[] lines = out.split("\\r?\\n");
                    for(int i=lines.length-1;i>=0;i--){
                        String l = lines[i].trim();
                        if(!l.isEmpty()){ verifyVal = l; break; }
                    }
                }
                if(verifyVal==null || !"4".equals(verifyVal.trim())){
                    // try busybox tee if available
                    if(MainActivity.busybox!=null && !MainActivity.busybox.isEmpty()){
                        String suBusy = "ip link set " + iface + " down; echo 4 | " + MainActivity.busybox + " tee /sys/module/wlan/parameters/con_mode 2>&1; cat /sys/module/wlan/parameters/con_mode; ip link set " + iface + " up";
                        String out2 = runSuAndCapture(suBusy);
                        if(out2!=null){
                            String[] lines = out2.split("\\r?\\n");
                            for(int i=lines.length-1;i>=0;i--){
                                String l = lines[i].trim();
                                if(!l.isEmpty()){ verifyVal = l; break; }
                            }
                        }
                    }
                }
                if(verifyVal!=null && verifyVal.trim().matches("\\d+") && !"0".equals(verifyVal.trim())){
                    Log.d("HIJACKER/test_thread", "con_mode set to " + verifyVal.trim());
                    return true;
                }else{
                    Log.d("HIJACKER/test_thread", "con_mode write failed or not set (value='" + verifyVal + "'), will fall back to configured command");
                    return false;
                }
            }
        }catch(Exception e){
            Log.e("HIJACKER/test_thread", "enableViaConMode exception: " + e);
        }
        return false;
    }

    // Run a single root command via su -c and capture stdout (returns combined stdout text)
    private static String runSuAndCapture(String command){
        try{
            Process p = Runtime.getRuntime().exec(new String[]{"su","-c",command});
            BufferedReader r = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();

            Thread waiter = new Thread(() -> { try{ p.waitFor(); }catch(InterruptedException ignored){} });
            waiter.start();

            long start = System.currentTimeMillis();
            long timeoutMs = 3000;

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
            Log.e("HIJACKER/test_thread", "runSuAndCapture exception: " + e);
            return null;
        }
    }

    final Runnable runnable = new Runnable(){
        @Override
        public void run(){
            final boolean[] results = {false, false, false, false, false, false, false, false};
            final String cmdMonMode = enable_monMode;
            // Build tool command bodies (include prefix only when non-empty via buildBody)
            final String cmdAirodumpBody = buildBody(airodump_dir, iface);
            final String cmdAireplayBody = buildBody(aireplay_dir, "--deauth 0 -a 11:22:33:44:55:66 " + iface);
            final String cmdMdkBody = buildBody(mdk4bf_dir, iface + " b -m");
            final String cmdReaverBody = buildBody(reaver_dir, "-i " + iface + " -b 00:11:22:33:44:55 -c 2");
            // Process holders declared here so they are visible in finally
            final Process[] pAirodump = {null};
            final Process[] pAireplay = {null};
            final Process[] pMdk = {null};
            final Process[] pReaver = {null};
            try{
                // Prevent Airodump from resetting con_mode while we manage it for the full tools test
                MainActivity.preventConModeReset = true;

                stop(PROCESS_AIRODUMP);
                stop(PROCESS_AIREPLAY);
                stop(PROCESS_MDK_BF);
                stop(PROCESS_MDK_DOS);
                stop(PROCESS_REAVER);
                last_action = System.currentTimeMillis() + 10000;       //Make watchdog wait until the test is over

            }catch(Exception e){
                Log.e("HIJACKER/test_thread", "Error stopping processes: " + e);
            }

            try{
                // Single enable attempt for monitor mode before running all tools
                runInHandler(() -> test_cur_cmd.setText(enable_monMode));
                Log.d("HIJACKER/test_thread", "Preparing monitor mode for tools: " + cmdMonMode);
                boolean monEnabled = false;
                conModeEnabled = false;
                fallbackMonEnabled = false;
                try{
                    if(MainActivity.isInterfaceInMonitor(iface)){
                        Log.d("HIJACKER/test_thread", "Interface " + iface + " already in monitor mode");
                        runInHandler(() -> test_cur_cmd.setText(getString(R.string.mon_status_already, enable_monMode)));
                        monEnabled = true;
                    } else {
                        // Try con_mode via sysfs first (driver support). If successful, mark conModeEnabled.
                        boolean conOk = enableViaConMode();
                        if(conOk){
                            conModeEnabled = true;
                        } else {
                            // Fallback to configured enable command
                            if(cmdMonMode!=null && !cmdMonMode.trim().isEmpty()){
                                try{ runOne(cmdMonMode); fallbackMonEnabled = true; }catch(Exception e){ Log.e("HIJACKER/test_thread", "Fallback enable failed: " + e); }
                            }
                        }
                        final int maxWaitMs = 3000;
                        final int intervalMs = 200;
                        int waited = 0;
                        while(waited < maxWaitMs){
                            try{ Thread.sleep(intervalMs); }catch(InterruptedException ie){ Thread.currentThread().interrupt(); break; }
                            waited += intervalMs;
                            try{
                                if(MainActivity.isInterfaceInMonitor(iface)){
                                    monEnabled = true;
                                    break;
                                }
                            }catch(Exception ignored){ }
                        }
                        if(monEnabled) runInHandler(() -> test_cur_cmd.setText(getString(R.string.mon_status_enabled, enable_monMode)));
                        else runInHandler(() -> test_cur_cmd.setText(getString(R.string.mon_status_not_confirmed, enable_monMode)));
                    }
                }catch(Exception e){
                    Log.e("HIJACKER/test_thread", "Error checking or enabling monitor mode: " + e);
                    try{ if(cmdMonMode!=null && !cmdMonMode.trim().isEmpty()) runOne(cmdMonMode); fallbackMonEnabled = true; }catch(Exception ex){ Log.e("HIJACKER/test_thread", "Fallback enable failed: " + ex); }
                }
                // Give the driver a brief moment if monitor mode was just enabled
                try{ Thread.sleep(monEnabled ? 300 : 700); }catch(InterruptedException ie){ Thread.currentThread().interrupt(); }
                //stop everything and turn on monitor mode
                runInHandler(() -> {
                    toolAdapter.updateStatusById("airodump", R.drawable.testing_drawable, getString(R.string.status_testing));
                    test_cur_cmd.setText(cmdAirodumpBody);
                });

                //Airodump
                Log.d("HIJACKER/test_thread", "su -c " + cmdAirodumpBody);
                pAirodump[0] = Runtime.getRuntime().exec(new String[]{"su","-c", cmdAirodumpBody});
                Thread.sleep(TEST_WAIT);

                if(getPIDs(PROCESS_AIRODUMP).isEmpty()) thread.interrupt();
                else{
                    stop(PROCESS_AIRODUMP);
                    last_action = System.currentTimeMillis() + 10000;
                    results[0] = true;
                }
                runInHandler(() -> {
                    toolAdapter.updateStatusById("airodump", results[0] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[0] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    test_progress.setProgress(1);

                    test_cur_cmd.setText(cmdAireplayBody);
                    toolAdapter.updateStatusById("aireplay", R.drawable.testing_drawable, getString(R.string.status_testing));
                });

                //Aireplay
                Log.d("HIJACKER/test_thread", "su -c " + cmdAireplayBody);
                pAireplay[0] = Runtime.getRuntime().exec(new String[]{"su","-c", cmdAireplayBody});
                Thread.sleep(TEST_WAIT);

                if(getPIDs(PROCESS_AIREPLAY).isEmpty()) results[1] = false;
                else{
                    stop(PROCESS_AIREPLAY);
                    last_action = System.currentTimeMillis() + 10000;
                    results[1] = true;
                }
                runInHandler(() -> {
                    toolAdapter.updateStatusById("aireplay", results[1] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[1] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    test_progress.setProgress(2);

                    toolAdapter.updateStatusById("mdk4", R.drawable.testing_drawable, getString(R.string.status_testing));
                    test_cur_cmd.setText(cmdMdkBody);
                });

                //MDK
                // Ensure interface is in monitor mode before starting MDK; try con_mode first if needed
                Log.d("HIJACKER/test_thread", "Preparing to start MDK: su -c " + cmdMdkBody);
                // Build fallback (without prefix) body for runToolWithPrefixFallback
                String bareMdkBody = mdk4bf_dir + " " + iface + " b -m";
                pMdk[0] = MainActivity.runToolWithPrefixFallback("su -c " + cmdMdkBody, "su -c " + bareMdkBody, "mdk4bf");
                Thread.sleep(TEST_WAIT);

                if(getPIDs(PROCESS_MDK_BF).isEmpty()) results[2] = false;
                else{
                    stop(PROCESS_MDK_BF);
                    last_action = System.currentTimeMillis() + 10000;
                    results[2] = true;
                }
                runInHandler(() -> {
                    toolAdapter.updateStatusById("mdk4", results[2] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[2] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    test_progress.setProgress(3);

                    toolAdapter.updateStatusById("reaver", R.drawable.testing_drawable, getString(R.string.status_testing));
                    test_cur_cmd.setText(cmdReaverBody);
                });

                //Reaver
                Log.d("HIJACKER/test_thread", "su -c " + cmdReaverBody);
                // Run reaver via su, use same approach to avoid crashes when prefix causes issues
                String bareReaverBody = reaver_dir + " -i " + iface + " -b 00:11:22:33:44:55 -c 2";
                pReaver[0] = MainActivity.runToolWithPrefixFallback("su -c " + cmdReaverBody, "su -c " + bareReaverBody, "reaver");
                Thread.sleep(TEST_WAIT);

                if(getPIDs(PROCESS_REAVER).isEmpty()) results[3] = false;
                else{
                    stop(PROCESS_REAVER);
                    last_action = System.currentTimeMillis() + 10000;
                    results[3] = true;
                }
                runInHandler(() -> {
                    toolAdapter.updateStatusById("reaver", results[3] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[3] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    test_progress.setProgress(4);

                    // Start hcxdumptool quick check
                    toolAdapter.updateStatusById("hcxdumptool", R.drawable.testing_drawable, getString(R.string.status_testing));
                    test_cur_cmd.setText(getString(R.string.test_hcxdumptool));
                });

                // hcxdumptool: quick --version check
                try{
                    final String cmdHcxBody = buildBody(MainActivity.hcxdumptool_dir, "--version");
                    String outHcx = runSuAndCapture(cmdHcxBody);
                    results[4] = outHcx!=null && !outHcx.trim().isEmpty();
                }catch(Exception e){ results[4] = false; }

                runInHandler(() -> {
                    toolAdapter.updateStatusById("hcxdumptool", results[4] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[4] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    test_progress.setProgress(5);

                    // Start pixiewps test (non-persistent binary)
                    toolAdapter.updateStatusById("pixiewps", R.drawable.testing_drawable, getString(R.string.status_testing));
                    test_cur_cmd.setText(getString(R.string.test_pixiewps));
                });

                // Pixiewps: run a quick --version (or equivalent) check and consider success if output is produced
                try{
                    final String cmdPixBody = buildBody(MainActivity.pixiewps_dir, "--version");
                    String outPix = runSuAndCapture(cmdPixBody);
                    results[5] = outPix!=null && !outPix.trim().isEmpty();
                }catch(Exception e){ results[5] = false; }

                runInHandler(() -> {
                    toolAdapter.updateStatusById("pixiewps", results[5] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[5] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    test_progress.setProgress(6);

                    // Start cowpatty check
                    toolAdapter.updateStatusById("cowpatty", R.drawable.testing_drawable, getString(R.string.status_testing));
                    test_cur_cmd.setText(getString(R.string.test_cowpatty));
                });

                // Cowpatty quick --help check (outputs usage), treat any output as success
                try{
                    final String cmdCowBody = buildBody(MainActivity.cowpatty_dir, "-h");
                    String outCow = runSuAndCapture(cmdCowBody);
                    results[6] = outCow!=null && !outCow.trim().isEmpty();
                }catch(Exception e){ results[6] = false; }

                runInHandler(() -> {
                    toolAdapter.updateStatusById("cowpatty", results[6] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[6] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    test_progress.setProgress(7);

                    // Now run chroot check
                    toolAdapter.updateStatusById("chroot", R.drawable.testing_drawable, getString(R.string.status_testing));
                    test_cur_cmd.setText(R.string.checking_chroot);
                });

                //Chroot
                final int chroot_check = checkChroot();
                results[7] = chroot_check==CHROOT_FOUND;
                runInHandler(() -> {
                    if(chroot_check!=CHROOT_FOUND){
                        toolAdapter.updateStatusById("chroot", R.drawable.failed_drawable, getString(R.string.status_failed));
                        if(chroot_check==CHROOT_DIR_MISSING) test_cur_cmd.setText(R.string.chroot_notfound);
                        else if(chroot_check==CHROOT_BIN_MISSING) test_cur_cmd.setText(R.string.kali_notfound);
                        else test_cur_cmd.setText(R.string.chroot_both_notfound);
                    }else{
                        test_cur_cmd.setText(R.string.done);
                        toolAdapter.updateStatusById("chroot", R.drawable.done_drawable, getString(R.string.status_done));
                    }
                    test_progress.setProgress(8);
                });

            }catch(IOException | InterruptedException e){
                Log.e("HIJACKER/test_thread", e.toString());
                runInHandler(() -> {
                    toolAdapter.updateStatusById("airodump", results[0] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[0] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    toolAdapter.updateStatusById("aireplay", results[1] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[1] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    toolAdapter.updateStatusById("mdk4", results[2] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[2] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    toolAdapter.updateStatusById("reaver", results[3] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[3] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    toolAdapter.updateStatusById("hcxdumptool", results[4] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[4] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    toolAdapter.updateStatusById("pixiewps", results[5] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[5] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    toolAdapter.updateStatusById("cowpatty", results[6] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[6] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    toolAdapter.updateStatusById("chroot", results[7] ? R.drawable.done_drawable : R.drawable.failed_drawable, results[7] ? getString(R.string.status_done) : getString(R.string.status_failed));
                    test_progress.setProgress(8);
                });
            }finally{
                // Best-effort: destroy any spawned processes
                try{ if(pAirodump[0]!=null) pAirodump[0].destroy(); }catch(Exception ignored){}
                try{ if(pAireplay[0]!=null) pAireplay[0].destroy(); }catch(Exception ignored){}
                try{ if(pMdk[0]!=null) pMdk[0].destroy(); }catch(Exception ignored){}
                try{ if(pReaver[0]!=null) pReaver[0].destroy(); }catch(Exception ignored){}

                stop(PROCESS_AIRODUMP);
                stop(PROCESS_AIREPLAY);
                stop(PROCESS_MDK_BF);
                stop(PROCESS_REAVER);
                // Disable monitor mode once all tests are finished
                try{
                    if(conModeEnabled){
                        // attempt to disable via sysfs and log the result
                        boolean disabled = disableViaConMode();
                        if(!disabled) Log.e("HIJACKER/test_thread", "disableViaConMode returned false");
                        conModeEnabled = false;
                    } else if(fallbackMonEnabled){
                        // run configured disable command if available
                        String disableCmd = MainActivity.disable_monMode;
                        if(disableCmd!=null && !disableCmd.trim().isEmpty()) runOne(disableCmd);
                        fallbackMonEnabled = false;
                    }
                }catch(Exception e){
                    Log.e("HIJACKER/test_thread", "Error disabling monitor mode after tests: " + e);
                }
                // Clear the prevent flag so Airodump can reset con_mode again in normal flows
                MainActivity.preventConModeReset = false;
            }
        }
    };
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState){
        loadPreferences();
        dialogView = requireActivity().getLayoutInflater().inflate(R.layout.test, null);

        test_progress = dialogView.findViewById(R.id.test_progress);
        RecyclerView toolsList = dialogView.findViewById(R.id.test_tools_list);
        toolAdapter = new ToolStatusAdapter();
        toolsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        toolsList.setAdapter(toolAdapter);
        test_cur_cmd = dialogView.findViewById(R.id.current_cmd);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());

        test_progress.setMax(8);
        test_progress.setProgress(0);
        // Initialize adapter items for the five tools
        List<ToolStatus> initial = new java.util.ArrayList<>();
        initial.add(new ToolStatus("airodump", getString(R.string.test_airodump), R.drawable.testing_drawable, getString(R.string.status_testing)));
        initial.add(new ToolStatus("aireplay", getString(R.string.test_aireplay), R.drawable.testing_drawable, getString(R.string.status_testing)));
        initial.add(new ToolStatus("mdk4", getString(R.string.test_mdk4), R.drawable.testing_drawable, getString(R.string.status_testing)));
        initial.add(new ToolStatus("reaver", getString(R.string.test_reaver), R.drawable.testing_drawable, getString(R.string.status_testing)));
        initial.add(new ToolStatus("hcxdumptool", getString(R.string.test_hcxdumptool), R.drawable.testing_drawable, getString(R.string.status_testing)));
        initial.add(new ToolStatus("pixiewps", getString(R.string.test_pixiewps), R.drawable.testing_drawable, getString(R.string.status_testing)));
        initial.add(new ToolStatus("cowpatty", getString(R.string.test_cowpatty), R.drawable.testing_drawable, getString(R.string.status_testing)));
        initial.add(new ToolStatus("chroot", getString(R.string.test_kali_chroot), R.drawable.testing_drawable, getString(R.string.status_testing)));
        toolAdapter.setItems(initial);

        thread = new Thread(runnable);
        thread.start();

        builder.setView(dialogView);
        builder.setTitle(R.string.testing);
        builder.setPositiveButton(R.string.retry_enable, (dialog, which) -> {}); // wired in onStart
        builder.setNegativeButton(R.string.back, (dialog, which) -> thread.interrupt());
        builder.setNeutralButton(R.string.stop, (dialog, which) -> {});
        return builder.create();
    }
    @Override
    public void show(@NonNull FragmentManager fragmentManager, String tag){
        if(!notif_on) super.show(fragmentManager, tag);
    }
    @Override
    public void onCancel(@NonNull DialogInterface dialog){
        super.onCancel(dialog);
        thread.interrupt();
    }
    @Override
    public void onStart() {
        super.onStart();
        AlertDialog d = (AlertDialog)getDialog();
        if(d != null) {
            d.getButton(Dialog.BUTTON_NEUTRAL).setOnClickListener(v -> thread.interrupt());
            // Wire Retry Enable button to perform manual enable attempt without dismissing the dialog
            d.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                // Disable the button while retry is running
                if(enableRetryRunning) return;
                d.getButton(Dialog.BUTTON_POSITIVE).setEnabled(false);
                retryEnableMonitor();
                // Re-enable after a short delay or when the retry completes (best-effort)
                new Thread(() -> {
                    try{ Thread.sleep(3500); }catch(InterruptedException ignored){}
                    runInHandler(() -> { try{ d.getButton(Dialog.BUTTON_POSITIVE).setEnabled(true); }catch(Exception ignored){} });
                }).start();
            });
        }
    }

    // Attempt to disable monitor mode using driver sysfs con_mode write (returns true if write appeared successful)
    private boolean disableViaConMode(){
        if(iface==null || !iface.startsWith("wlan")) return false;
        try{
            Shell probeShell = getFreeShell();
            probeShell.run("if [ -e /sys/module/wlan/parameters/con_mode ]; then echo EXISTS; else echo NO; fi; echo ENDCHK");
            String probeResult = MainActivity.getLastLine(probeShell.getShell_out(), "ENDCHK");
            probeShell.done();
            if(probeResult!=null && "EXISTS".equals(probeResult.trim())){
                // try direct su write of 0
                String suWrite = "ip link set " + iface + " down; sh -c 'echo 0 > /sys/module/wlan/parameters/con_mode' 2>&1; cat /sys/module/wlan/parameters/con_mode; ip link set " + iface + " up";
                String out = runSuAndCapture(suWrite);
                String verifyVal = null;
                if(out!=null){
                    String[] lines = out.split("\\r?\\n");
                    for(int i=lines.length-1;i>=0;i--){
                        String l = lines[i].trim();
                        if(!l.isEmpty()){ verifyVal = l; break; }
                    }
                }
                if(verifyVal==null || !"0".equals(verifyVal.trim())){
                    // try busybox tee if available
                    if(MainActivity.busybox!=null && !MainActivity.busybox.isEmpty()){
                        String suBusy = "ip link set " + iface + " down; echo 0 | " + MainActivity.busybox + " tee /sys/module/wlan/parameters/con_mode 2>&1; cat /sys/module/wlan/parameters/con_mode; ip link set " + iface + " up";
                        String out2 = runSuAndCapture(suBusy);
                        if(out2!=null){
                            String[] lines = out2.split("\\r?\\n");
                            for(int i=lines.length-1;i>=0;i--){
                                String l = lines[i].trim();
                                if(!l.isEmpty()){ verifyVal = l; break; }
                            }
                        }
                    }
                }
                if(verifyVal!=null && verifyVal.trim().matches("\\d+") && "0".equals(verifyVal.trim())){
                    Log.d("HIJACKER/test_thread", "con_mode reset to " + verifyVal.trim());
                    return true;
                }else{
                    Log.d("HIJACKER/test_thread", "con_mode reset failed or not 0 (value='" + verifyVal + "')");
                    return false;
                }
            }
        }catch(Exception e){
            Log.e("HIJACKER/test_thread", "disableViaConMode exception: " + e);
        }
        return false;
    }
}
