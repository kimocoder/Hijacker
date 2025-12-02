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

import android.animation.Animator;
import android.animation.ValueAnimator;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hijacker.AP.OPN;
import static com.hijacker.AP.UNKNOWN;
import static com.hijacker.MainActivity.FRAGMENT_REAVER;
import static com.hijacker.MainActivity.PROCESS_AIRODUMP;
import static com.hijacker.MainActivity.PROCESS_REAVER;
import static com.hijacker.MainActivity.background;
import static com.hijacker.MainActivity.currentFragment;
import static com.hijacker.MainActivity.debug;
import static com.hijacker.MainActivity.iface;
import static com.hijacker.MainActivity.last_action;
import static com.hijacker.MainActivity.last_reaver;
import static com.hijacker.MainActivity.mFragmentManager;
import static com.hijacker.MainActivity.notification;
import static com.hijacker.MainActivity.prefix;
import static com.hijacker.MainActivity.reaver_dir;
import static com.hijacker.MainActivity.pixiewps_dir;
import static com.hijacker.MainActivity.runInHandler;
import static com.hijacker.MainActivity.stop;

public class ReaverFragment extends Fragment{
    static ReaverTask task;
    private ReaverViewModel viewModel;
    View fragmentView, optionsContainer;
    Button start_button, select_button;
    TextView consoleView;
    EditText pinDelayView, lockedDelayView;
    CheckBox pixie_dust_cb, ignored_locked_cb, eap_fail_cb, small_dh_cb, no_nack_cb;
    ScrollView consoleScrollView;
    boolean autostart = false;
    //Dimensions to restore animated views
    int normalOptHeight = -1;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        fragmentView = inflater.inflate(R.layout.reaver_fragment, container, false);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(ReaverViewModel.class);

        optionsContainer = fragmentView.findViewById(R.id.options_container);
        consoleView = fragmentView.findViewById(R.id.console);
        consoleScrollView = fragmentView.findViewById(R.id.console_scroll_view);
        pinDelayView = fragmentView.findViewById(R.id.pin_delay);
        lockedDelayView = fragmentView.findViewById(R.id.locked_delay);
        pixie_dust_cb = fragmentView.findViewById(R.id.pixie_dust);
        ignored_locked_cb = fragmentView.findViewById(R.id.ignore_locked);
        eap_fail_cb = fragmentView.findViewById(R.id.eap_fail);
        small_dh_cb = fragmentView.findViewById(R.id.small_dh);
        no_nack_cb = fragmentView.findViewById(R.id.no_nack);
        select_button = fragmentView.findViewById(R.id.select_ap);
        start_button = fragmentView.findViewById(R.id.start_button);

        pinDelayView.setOnEditorActionListener((v, actionId, event) -> {
            if(actionId == EditorInfo.IME_ACTION_NEXT){
                lockedDelayView.requestFocus();
                return true;
            }
            return false;
        });

        if(task==null) task = new ReaverTask();

        // Pixie dust now works without chroot using pixiewps from assets
        pixie_dust_cb.setEnabled(true);
        viewModel.setPixieDustEnabled(true);

        select_button.setOnClickListener(view -> {
            PopupMenu popup = new PopupMenu(getActivity(), view);

            popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());
            int i = 0;
            for(AP ap : AP.APs){
                popup.getMenu().add(0, i, i, ap.toString());
                if(ap.sec==UNKNOWN  || ap.sec==OPN){
                    popup.getMenu().getItem(i).setEnabled(false);
                }
                i++;
            }
            popup.getMenu().add(1, i, i, "Custom");
            popup.setOnMenuItemClickListener(item -> {
                //ItemId = i in for()
                if(item.getGroupId()==0){
                    viewModel.setCustomMac(null);
                    AP temp = AP.APs.get(item.getItemId());
                    if(viewModel.getSelectedAp()!=temp){
                        viewModel.setSelectedAp(temp);
                    }
                    select_button.setText(viewModel.getSelectedAp().toString());
                }else{
                    //Clicked custom
                    final EditTextDialog dialog = new EditTextDialog();
                    dialog.setTitle(getString(R.string.custom_ap_title));
                    dialog.setHint(getString(R.string.mac_address));
                    dialog.setRunnable(() -> {
                        viewModel.setSelectedAp(null);
                        viewModel.setCustomMac(dialog.result);
                        select_button.setText(dialog.result);
                    });
                    dialog.show(mFragmentManager, "EditTextDialog");
                }
                return true;
            });
            popup.show();
        });
        start_button.setOnClickListener(view -> {
            if(!isRunning()){
                attemptStart();
            }else{
                stop(PROCESS_REAVER);
                stopReaver();
            }
        });

        return fragmentView;
    }
    void attemptStart(){
        pinDelayView.setError(null);
        lockedDelayView.setError(null);

        if(viewModel.getSelectedAp()==null && viewModel.getCustomMac()==null){
            Snackbar.make(fragmentView, getString(R.string.select_ap), Snackbar.LENGTH_LONG).show();
        }else{
            if(pinDelayView.getText().toString().isEmpty()){
                pinDelayView.setError(getString(R.string.field_required));
                pinDelayView.requestFocus();
                return;
            }
            if(lockedDelayView.getText().toString().isEmpty()){
                lockedDelayView.setError(getString(R.string.field_required));
                lockedDelayView.requestFocus();
                return;
            }

            task = new ReaverTask();
            task.start();
        }
    }
    ReaverFragment setAutostart(){
        this.autostart = true;
        return this;
    }
    static boolean isRunning(){
        if(task==null) return false;
        return task.isRunning();
    }
    static void stopReaver(){
        //Does NOT completely stop reaver, only the app's task
        //MainActivity.stop(PROCESS_REAVER) should be also called
        if(task!=null){
            task.cancel();
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        currentFragment = FRAGMENT_REAVER;
        ((MainActivity) requireActivity()).refreshDrawer();

        //Console text is saved/restored from ViewModel
        consoleView.setText(viewModel.getConsoleText());
        consoleView.post(() -> consoleScrollView.fullScroll(View.FOCUS_DOWN));
    }
    @Override
    public void onPause(){
        super.onPause();

        //Console text is saved to ViewModel
        viewModel.setConsoleText(consoleView.getText().toString());
    }
    @Override
    public void onStart(){
        super.onStart();

        //Restore options from ViewModel
        pinDelayView.setText(viewModel.getPinDelay());
        lockedDelayView.setText(viewModel.getLockedDelay());
        pixie_dust_cb.setChecked(viewModel.isPixieDust());
        pixie_dust_cb.setEnabled(viewModel.isPixieDustEnabled());
        ignored_locked_cb.setChecked(viewModel.isIgnoreLocked());
        eap_fail_cb.setChecked(viewModel.isEapFail());
        small_dh_cb.setChecked(viewModel.isSmallDh());
        no_nack_cb.setChecked(viewModel.isNoNack());
        if(viewModel.getCustomMac()!=null) select_button.setText(viewModel.getCustomMac());
        else if(viewModel.getSelectedAp()!=null) select_button.setText(viewModel.getSelectedAp().toString());
        else if(!AP.marked.isEmpty()){
            viewModel.setSelectedAp(AP.marked.get(AP.marked.size()-1));
            select_button.setText(viewModel.getSelectedAp().toString());
        }
        start_button.setText(isRunning() ? R.string.stop : R.string.start);

        //Restore animated views
        if(isRunning()){
            ViewGroup.LayoutParams layoutParams = optionsContainer.getLayoutParams();
            layoutParams.height = 0;
            optionsContainer.setLayoutParams(layoutParams);
        }else if(normalOptHeight!=-1){
            ViewGroup.LayoutParams params = optionsContainer.getLayoutParams();
            params.height = normalOptHeight;
            optionsContainer.setLayoutParams(params);

            consoleScrollView.fullScroll(View.FOCUS_DOWN);
        }

        if(autostart){
            optionsContainer.post(this::attemptStart);
            autostart = false;
        }
    }
    @Override
    public void onStop(){
        if(task!=null){
            if(task.sizeAnimator!=null){
                task.sizeAnimator.cancel();
            }
        }

        //Backup options to ViewModel
        viewModel.setPinDelay(pinDelayView.getText().toString());
        viewModel.setLockedDelay(lockedDelayView.getText().toString());
        viewModel.setPixieDust(pixie_dust_cb.isChecked());
        viewModel.setPixieDustEnabled(pixie_dust_cb.isEnabled());
        viewModel.setIgnoreLocked(ignored_locked_cb.isChecked());
        viewModel.setEapFail(eap_fail_cb.isChecked());
        viewModel.setSmallDh(small_dh_cb.isChecked());
        viewModel.setNoNack(no_nack_cb.isChecked());

        super.onStop();
    }

    class ReaverTask {
        String pinDelay, lockedDelay;
        boolean ignoreLocked, eapFail, smallDH, pixieDust, noNack;
        ValueAnimator sizeAnimator;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "ReaverTaskThread"));
        private volatile boolean cancelled = false;

        void preExecute(){
            pinDelay = pinDelayView.getText().toString();
            lockedDelay = lockedDelayView.getText().toString();
            ignoreLocked = ignored_locked_cb.isChecked();
            eapFail = eap_fail_cb.isChecked();
            smallDH = small_dh_cb.isChecked();
            pixieDust = pixie_dust_cb.isChecked();
            noNack = no_nack_cb.isChecked();

            start_button.setText(R.string.stop);
            MainActivity.setProgressIndeterminate(true);

            normalOptHeight = optionsContainer.getHeight();

            sizeAnimator = ValueAnimator.ofInt(optionsContainer.getHeight(), 0);
            sizeAnimator.setTarget(optionsContainer);
            sizeAnimator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams layoutParams = optionsContainer.getLayoutParams();
                layoutParams.height = (int)animation.getAnimatedValue();
                optionsContainer.setLayoutParams(layoutParams);
            });
            sizeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            sizeAnimator.start();
        }

        void start(){
            executor.submit(() -> {
                preExecute();
                doInBackground();
                if(cancelled) postCancel(); else postDone();
                return null;
            });
        }

        void cancel(){
            cancelled = true;
        }

        void doInBackground(){
            last_action = System.currentTimeMillis();
            stop(PROCESS_AIRODUMP);            //Can't have channels changing from anywhere else

            // Set con_mode to 4 (monitor mode) if wlan0 is selected and con_mode exists
            try{
                if(iface!=null && iface.startsWith("wlan0")){
                    Shell probeShell = Shell.getFreeShell();
                    if(debug) Log.d("HIJACKER/Reaver", "Probing for /sys/module/wlan/parameters/con_mode (iface='" + iface + "')");
                    probeShell.run("if [ -e /sys/module/wlan/parameters/con_mode ]; then echo EXISTS; else echo NO; fi; echo ENDCHK");
                    String probeResult = MainActivity.getLastLine(probeShell.getShell_out(), "ENDCHK");
                    probeShell.done();
                    if(debug) Log.d("HIJACKER/Reaver", "Probe result: '" + probeResult + "'");
                    if(probeResult != null && "EXISTS".equals(probeResult.trim())){
                        if(debug) Log.d("HIJACKER/Reaver", "con_mode found, setting to 4 for monitor mode");
                        try{
                            String suWrite = "ip link set " + iface + " down; sh -c 'echo 4 > /sys/module/wlan/parameters/con_mode' 2>&1; cat /sys/module/wlan/parameters/con_mode; ip link set " + iface + " up";
                            String out = runSuAndCapture(suWrite);
                            if(debug) Log.d("HIJACKER/Reaver", "Direct su write output: '" + out + "'");
                            String verifyVal = null;
                            if(out!=null){
                                String[] lines = out.split("\\r?\\n");
                                for(int i=lines.length-1;i>=0;i--){
                                    String l = lines[i].trim();
                                    if(!l.isEmpty()){ verifyVal = l; break; }
                                }
                            }

                            if(verifyVal==null || !"4".equals(verifyVal.trim())){
                                if(MainActivity.busybox!=null && !MainActivity.busybox.isEmpty()){
                                    String suBusy = "ip link set " + iface + " down; echo 4 | " + MainActivity.busybox + " tee /sys/module/wlan/parameters/con_mode 2>&1; cat /sys/module/wlan/parameters/con_mode; ip link set " + iface + " up";
                                    String out2 = runSuAndCapture(suBusy);
                                    if(debug) Log.d("HIJACKER/Reaver", "Direct busybox write output: '" + out2 + "'");
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
                                if(debug) Log.d("HIJACKER/Reaver", "Successfully set con_mode to '" + verifyVal.trim() + "'");
                            }else{
                                Log.e("HIJACKER/Reaver", "Failed to set con_mode (value='" + verifyVal + "')");
                            }
                        }catch(Exception w){
                            Log.e("HIJACKER/Reaver", "Direct su -c attempt failed: " + w);
                        }
                    }
                }
            }catch(Exception e){
                Log.e("HIJACKER/Reaver", "con_mode check failed: " + e);
            }

            try{
                BufferedReader out;
                String args = "-i " + iface + " -vv";
                args += viewModel.getSelectedAp()==null ? " -b " + viewModel.getCustomMac() : " -b " + viewModel.getSelectedAp().mac + " --channel " + viewModel.getSelectedAp().ch;
                args += " -d " + pinDelay;
                args += " -l " + lockedDelay;
                if(ignoreLocked) args += " -L";
                if(eapFail) args += " -E";
                if(smallDH) args += " -S";
                if(noNack) args += " -N";

                String cmd;
                if(pixieDust){
                    // Use pixiewps from assets instead of chroot
                    args += " -K 1";
                    // Set PATH to include our bin directory so reaver can find pixiewps
                    String binPath = pixiewps_dir.substring(0, pixiewps_dir.lastIndexOf('/'));
                    cmd = "su -c 'export PATH=" + binPath + ":$PATH && " + prefix + " " + reaver_dir + " " + args + "'";
                    postProgress("\nRunning: " + cmd);
                    postProgress("Using pixiewps from: " + pixiewps_dir);
                    Process dc = Runtime.getRuntime().exec(new String[]{"su", "-c", "export PATH=" + binPath + ":$PATH && " + prefix + " " + reaver_dir + " " + args});
                    out = new BufferedReader(new InputStreamReader(dc.getInputStream()));
                }else{
                    cmd = "su -c " + prefix + " " + reaver_dir + " " + args;
                    postProgress("\nRunning: " + cmd);
                    Process dc = Runtime.getRuntime().exec(cmd);
                    out = new BufferedReader(new InputStreamReader(dc.getInputStream()));
                }
                if(debug) Log.d("HIJACKER/ReaverFragment", cmd);
                last_reaver = cmd;

                String buffer;
                while(!cancelled && (buffer = out.readLine())!=null){
                    postProgress(buffer);
                }
                postProgress("Done");
            }catch(IOException e){
                Log.e("HIJACKER/Exception", "Caught Exception in ReaverFragment: " + e);
            }

        }

        void postProgress(String... text){
            String s = text[0] + '\n';
            runInHandler(() -> {
                if(currentFragment==FRAGMENT_REAVER && !background){
                    consoleView.append(s);
                    consoleScrollView.fullScroll(View.FOCUS_DOWN);
                }else{
                    // Save to ViewModel when fragment is not visible
                    viewModel.appendConsoleText(s);
                }
            });
        }

        void postDone(){
            runInHandler(this::done);
        }
        void postCancel(){
            runInHandler(this::done);
        }

        void done(){
            start_button.setText(R.string.start);
            MainActivity.setProgressIndeterminate(false);

            sizeAnimator = ValueAnimator.ofInt(0, normalOptHeight);
            sizeAnimator.setTarget(optionsContainer);
            sizeAnimator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams layoutparams = optionsContainer.getLayoutParams();
                layoutparams.height = (int)animation.getAnimatedValue();
                optionsContainer.setLayoutParams(layoutparams);
            });
            sizeAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(@NonNull Animator animation){}
                @Override
                public void onAnimationEnd(@NonNull Animator animation){
                    consoleScrollView.fullScroll(View.FOCUS_DOWN);
                }
                @Override
                public void onAnimationCancel(@NonNull Animator animation){}
                @Override
                public void onAnimationRepeat(@NonNull Animator animation){}
            });
            sizeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            sizeAnimator.start();

            notification();
        }

        @SuppressWarnings("unused")
        int getStatus(){
            // Deprecated-style status kept for compatibility; 1 means running.
            return cancelled ? 0 : 1;
        }

        boolean isRunning() {
            return !cancelled;
        }
    }

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

            if(waiter.isAlive()){
                try{ p.destroy(); }catch(Exception ignored){}
                waiter.interrupt();
            }

            return sb.toString();
        }catch(Exception e){
            Log.e("HIJACKER/Reaver", "runSuAndCapture exception: " + e);
            return null;
        }
    }
}
