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
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hijacker.MainActivity.FRAGMENT_CRACK;
import static com.hijacker.MainActivity.PROCESS_AIRCRACK;
import static com.hijacker.MainActivity.aircrack_dir;
import static com.hijacker.MainActivity.background;
import static com.hijacker.MainActivity.cap_path;
import static com.hijacker.MainActivity.currentFragment;
import static com.hijacker.MainActivity.debug;
import static com.hijacker.MainActivity.notification;
import static com.hijacker.MainActivity.path;
import static com.hijacker.MainActivity.stop;
import static com.hijacker.MainActivity.wl_path;

public class CrackFragment extends Fragment{
    static final int WPA = 2, WEP = 1;
    static CrackTask task;
    View fragmentView, optionsContainer;
    Button startBtn, speedTestBtn, capFeBtn, wordlistFeBtn;
    ImageButton wordlistDownloadBtn;
    TextView consoleView;
    EditText capfileView, wordlistView;
    RadioGroup wepRG, securityRG;
    RadioButton wepRB, wpaRB;
    ScrollView consoleScrollView;
    //Dimensions to restore animated views
    int normalOptHeight = -1, normalTestBtnWidth = -1;
    //User options
    static String console_text = "", capfile_text = null, wordlist_text = null;
    static int securityChecked = -1, wepChecked = -1;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState){
        fragmentView = inflater.inflate(R.layout.crack_fragment, container, false);

        optionsContainer = fragmentView.findViewById(R.id.options_container);
        consoleView = fragmentView.findViewById(R.id.console);
        consoleScrollView = fragmentView.findViewById(R.id.console_scroll_view);
        capfileView = fragmentView.findViewById(R.id.capfile);
        wordlistView = fragmentView.findViewById(R.id.wordlist);
        capFeBtn = fragmentView.findViewById(R.id.cap_fe_btn);
        wordlistFeBtn = fragmentView.findViewById(R.id.wordlist_fe_btn);
        wordlistDownloadBtn = fragmentView.findViewById(R.id.wordlist_download_btn);
        wepRG = fragmentView.findViewById(R.id.wep_rg);
        securityRG = fragmentView.findViewById(R.id.radio_group);
        wepRB = fragmentView.findViewById(R.id.wep_rb);
        wpaRB = fragmentView.findViewById(R.id.wpa_rb);
        startBtn = fragmentView.findViewById(R.id.start);
        speedTestBtn = fragmentView.findViewById(R.id.speed_test_btn);

        capfileView.setOnEditorActionListener((v, actionId, event) -> {
            if(actionId == EditorInfo.IME_ACTION_NEXT){
                wordlistView.requestFocus();
                return true;
            }
            return false;
        });

        for (int i = 0; i < wepRG.getChildCount(); i++) {
            //Disable all the WEP options, wepRG.setEnabled(false) doesn't work
            wepRG.getChildAt(i).setEnabled(false);
        }

        if(task==null) task = new CrackTask(CrackTask.JOB_CRACK, null, null);

        wepRB.setOnCheckedChangeListener((compoundButton, b) -> {
            for (int i = 0; i < wepRG.getChildCount(); i++) {
                //If wep is now checked, enable the wep options, otherwise disable them
                wepRG.getChildAt(i).setEnabled(b);
            }
            wordlistView.setEnabled(!b);
        });
        startBtn.setOnClickListener(view -> {
            if(!isRunning()){
                attemptStart();
            }else{
                task.cancel(true);
            }
        });
        speedTestBtn.setOnClickListener(view -> startSpeedTest());
        capFeBtn.setOnClickListener(v -> {
            final FileExplorerDialog dialog = new FileExplorerDialog();
            dialog.setStartingDir(new RootFile(cap_path));
            dialog.setToSelect(FileExplorerDialog.SELECT_EXISTING_FILE);
            dialog.setOnSelect(() -> {
                capfileView.setText(dialog.result.getAbsolutePath());
                capfileView.setError(null);
            });
            dialog.show(requireActivity().getSupportFragmentManager(), "FileExplorerDialog");
        });
        wordlistFeBtn.setOnClickListener(v -> {
            final FileExplorerDialog dialog = new FileExplorerDialog();
            dialog.setStartingDir(new RootFile(wl_path));
            dialog.setToSelect(FileExplorerDialog.SELECT_EXISTING_FILE);
            dialog.setOnSelect(() -> {
                wordlistView.setText(dialog.result.getAbsolutePath());
                wordlistView.setError(null);
            });
            dialog.show(requireActivity().getSupportFragmentManager(), "FileExplorerDialog");
        });
        wordlistDownloadBtn.setOnClickListener(view -> {
            WordlistDownloadDialog dlg = new WordlistDownloadDialog();
            dlg.show(requireActivity().getSupportFragmentManager(), "WordlistDownloadDialog");
        });

        if(capfile_text==null){
            //Retrieve the last captured handshake
            long latest = 0;
            File result = null;

            File[] files = new File(cap_path).listFiles((file, s) -> s.startsWith("handshake-") && s.endsWith(".cap"));

            if(files!=null){    //Only if the directory is deleted while the app is running, apparently it happens
                for(File f : files){
                    if(f.lastModified()>latest){
                        latest = f.lastModified();
                        result = f;
                    }
                }

                if(result!=null){
                    capfileView.setText(result.getAbsolutePath());
                }
            }
        }

        if(wordlist_text==null){
            //Retrieve the last downloaded wordlist, if any
            long latest = 0;
            File result = null;

            File[] files = new File(wl_path).listFiles();

            if(files!=null){    //Only if the directory is deleted while the app is running, apparently it happens
                for(File f : files){
                    if(f.lastModified()>latest){
                        latest = f.lastModified();
                        result = f;
                    }
                }
            }

            if(result!=null){
                wordlistView.setText(result.getAbsolutePath());
            }
        }

        return fragmentView;
    }
    @Override
    public void onResume() {
        super.onResume();
        currentFragment = FRAGMENT_CRACK;
        ((MainActivity) requireActivity()).refreshDrawer();

        //Console text is saved/restored on pause/resume
        consoleView.setText(console_text);
        consoleView.post(() -> consoleScrollView.fullScroll(View.FOCUS_DOWN));
    }
    @Override
    public void onPause(){
        super.onPause();

        //Console text is saved/restored on pause/resume
        console_text = consoleView.getText().toString();
    }
    @Override
    public void onStart(){
        super.onStart();

        //Restore options
        if(capfile_text!=null) capfileView.setText(capfile_text);
        if(wordlist_text!=null) wordlistView.setText(wordlist_text);
        if(securityChecked!=-1) securityRG.check(securityChecked);
        if(wepChecked!=-1) wepRG.check(wepChecked);
        for (int i = 0; i < wepRG.getChildCount(); i++) {
            //Reset wep options
            wepRG.getChildAt(i).setEnabled(wepRB.isChecked());
        }
        wordlistView.setEnabled(!wepRB.isChecked());
        startBtn.setText(isRunning() ? getString(R.string.stop) : getString(R.string.start));

        //Restore animated views
        if(task.isRunning()){
            ViewGroup.LayoutParams layoutParams = optionsContainer.getLayoutParams();
            layoutParams.height = 0;
            optionsContainer.setLayoutParams(layoutParams);

            layoutParams = speedTestBtn.getLayoutParams();
            layoutParams.width = 0;
            speedTestBtn.setLayoutParams(layoutParams);
        }else if(normalOptHeight!=-1 && normalTestBtnWidth!=-1){
            ViewGroup.LayoutParams params = optionsContainer.getLayoutParams();
            params.height = normalOptHeight;
            optionsContainer.setLayoutParams(params);

            params = speedTestBtn.getLayoutParams();
            params.width = normalTestBtnWidth;
            speedTestBtn.setLayoutParams(params);

            consoleScrollView.fullScroll(View.FOCUS_DOWN);
        }
    }
    @Override
    public void onStop(){
        if(task!=null){
            if(task.animator!=null){
                task.animator.cancel();
            }
        }

        //Backup options
        capfile_text = capfileView.getText().toString();
        wordlist_text = wordlistView.getText().toString();
        securityChecked = securityRG.getCheckedRadioButtonId();
        wepChecked = wepRG.getCheckedRadioButtonId();

        super.onStop();
    }
    static boolean isRunning(){
        if(task==null) return false;
        return task.isRunning();
    }
    static void stopCracking(){
        //Does NOT completely stop the cracking process, only the app's task
        //MainActivity.stop(PROCESS_AIRCRACK) should be also called
        if(task!=null){
            task.cancel(true);
        }
    }
    void attemptStart(){
        capfileView.setError(null);
        wordlistView.setError(null);
        String capfile = capfileView.getText().toString();
        String wordlist = wordlistView.getText().toString();

        if(!capfile.startsWith("/")){
            capfileView.setError(getString(R.string.capfile_invalid));
            capfileView.requestFocus();
            return;
        }
        RootFile cap = new RootFile(capfile);
        if(!cap.exists() || !cap.isFile()){
            capfileView.setError(getString(R.string.cap_notfound));
            capfileView.requestFocus();
            return;
        }
        if(wpaRB.isChecked()){
            //Check wordlist only if we are cracking WPA
            if(!wordlist.startsWith("/")){
                wordlistView.setError(getString(R.string.wordlist_invalid));
                wordlistView.requestFocus();
                return;
            }

            RootFile word = new RootFile(wordlist);
            if(!word.exists() || !word.isFile()){
                wordlistView.setError(getString(R.string.wordlist_notfound));
                wordlistView.requestFocus();
                return;
            }
        }

        int secChecked = securityRG.getCheckedRadioButtonId();
        if(secChecked == -1){
            //Mode not selected
            Snackbar.make(fragmentView, getString(R.string.select_wpa_wep), Snackbar.LENGTH_SHORT).show();
            return;
        } else if(secChecked == R.id.wep_rb){
            if(wepRG.getCheckedRadioButtonId()==-1){
                //WEP is selected but we need to have a wep bit length selection
                Snackbar.make(fragmentView, getString(R.string.select_wep_bits), Snackbar.LENGTH_SHORT).show();
                return;
            }
        }

        task = new CrackTask(CrackTask.JOB_CRACK, capfile, wordlist);
        task.start();
    }
    void startSpeedTest(){
        capfileView.setError(null);
        wordlistView.setError(null);
        task = new CrackTask(CrackTask.JOB_TEST, null, null);
        task.start();
    }
    class CrackTask {
        static final int JOB_CRACK = 0, JOB_TEST = 1;
        int mode, job;
        String cmd, key;
        long startTime = -1;
        AnimatorSet animator;
        String capfile, wordlist;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "CrackTaskThread"));
        private volatile boolean cancelled = false;

        CrackTask(int job, String capfile, String wordlist){
            this.job = job;
            this.capfile = capfile;
            this.wordlist = wordlist;
        }
        @SuppressLint("WrongThread")
        void preExecute(){
            startTime = System.currentTimeMillis();
            MainActivity.setProgressIndeterminate(true);
            startBtn.setText(R.string.stop);
            postProgress("\nRunning...");
            consoleScrollView.fullScroll(View.FOCUS_DOWN);

            switch(job){
                case JOB_CRACK:
                    int secId = securityRG.getCheckedRadioButtonId();
                    if(secId == R.id.wpa_rb){
                        //WPA
                        mode = WPA;
                    } else if(secId == R.id.wep_rb){
                        //WEP
                        mode = WEP;
                    }
                    //Create command
                    cmd = "su -c " + aircrack_dir + " " + capfile + " -l " + path + "/aircrack-out.txt -a " + mode;
                    if(wordlist!=null)
                        cmd += " -w " + wordlist;
                    if(mode==WEP){
                        cmd += " -n ";
                        int wepId = wepRG.getCheckedRadioButtonId();
                        if(wepId == R.id.wep_64) cmd += "64";
                        else if(wepId == R.id.wep_128) cmd += "128";
                        else if(wepId == R.id.wep_152) cmd += "152";
                        else if(wepId == R.id.wep_256) cmd += "256";
                        else if(wepId == R.id.wep_512) cmd += "512";
                    }
                    break;

                case JOB_TEST:
                    cmd = "su -c " + aircrack_dir + " -S";
                    break;

                default:
                    Log.e("HIJACKER/CrackTask", "Unknown Job");
                    cancelled = true;
                    return;
            }
            if(debug) Log.d("HIJACKER/CrackTask", cmd);

            normalOptHeight = optionsContainer.getHeight();
            normalTestBtnWidth = speedTestBtn.getWidth();

            ValueAnimator optionsAnimator = ValueAnimator.ofInt(optionsContainer.getHeight(), 0);
            optionsAnimator.setTarget(optionsContainer);
            optionsAnimator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams layoutParams = optionsContainer.getLayoutParams();
                layoutParams.height = (int)animation.getAnimatedValue();
                optionsContainer.setLayoutParams(layoutParams);
            });

            ValueAnimator testBtnAnimator = ValueAnimator.ofInt(speedTestBtn.getWidth(), 0);
            testBtnAnimator.setTarget(speedTestBtn);
            testBtnAnimator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams layoutParams = speedTestBtn.getLayoutParams();
                layoutParams.width = (int)animation.getAnimatedValue();
                speedTestBtn.setLayoutParams(layoutParams);
            });

            animator = new AnimatorSet();
            animator.playTogether(optionsAnimator, testBtnAnimator);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.start();
        }
        void start(){
            executor.submit(() -> {
                preExecute();
                Boolean result = doInBackground();
                if(cancelled) postCancel(); else postDone(result);
                return null;
            });
        }
        void cancel(boolean mayInterrupt){
            cancelled = true;
        }
        Boolean doInBackground(){
            if(cancelled) return false;
            try{
                //Run aircrack and wait to either finish or be cancelled
                Process dc = Runtime.getRuntime().exec(cmd);
                BufferedReader out = new BufferedReader(new InputStreamReader(dc.getInputStream()));
                switch(job){
                    case JOB_CRACK:
                        while(!cancelled && out.readLine()!=null);
                        break;

                    case JOB_TEST:
                        String str = out.readLine();
                        while(!cancelled && str!=null) {
                            postProgress(str);
                            str = out.readLine();
                        }
                        break;
                }
            }catch(Exception e) {
                Log.e("HIJACKER/CrackTask", e.toString());
                return false;
            }

            if(job==JOB_CRACK){
                if(new File(path + "/aircrack-out.txt").exists()){
                    Shell shell = Shell.getFreeShell();     //Using root shell because "new File(path + "/aircrack-out.txt");" throws FileNotFoundException (permission denied)
                    BufferedReader out = shell.getShell_out();
                    shell.run("cat " + path + "/aircrack-out.txt; echo ");              //No newline at the end of the file, readLine will hang
                    try{
                        key = out.readLine();
                    }catch(IOException ignored){
                    }
                    shell.run("rm " + path + "/aircrack-out.txt");
                    shell.done();
                    return true;
                }
            }

            return false;
        }
        void postProgress(String... progress) {
            String s = progress[0] + '\n';
            MainActivity.runInHandler(() -> {
                if(currentFragment==FRAGMENT_CRACK && !background){
                    consoleView.append(s);
                    consoleScrollView.fullScroll(View.FOCUS_DOWN);
                }else{
                    console_text += s;
                }
            });
        }
        void postDone(Boolean success) {
            MainActivity.runInHandler(() -> {
                done();
                String str = "";
                if(job==JOB_CRACK){
                    if(success){
                        str = "Key found: " + key + '\n';
                    }else{
                        str = "Key not found\n";
                        if(mode==WEP)
                            str += "Try with different wep bit selection or more IVs\n";
                    }
                }
                str += "Time: " + (System.currentTimeMillis() - startTime)/1000 + "s\n";
                if(currentFragment==FRAGMENT_CRACK && !background){
                    consoleScrollView.fullScroll(View.FOCUS_DOWN);
                    consoleView.append(str);
                }else{
                    console_text += str;
                }
            });
        }
        void postCancel() {
            MainActivity.runInHandler(() -> {
                done();
                consoleView.append("Interrupted");      //publishProgress doesn't work here
                consoleScrollView.fullScroll(View.FOCUS_DOWN);
                stop(PROCESS_AIRCRACK);
            });
        }
        void done() {
            startBtn.setText(R.string.start);
            MainActivity.setProgressIndeterminate(false);

            ValueAnimator optionsAnimator = ValueAnimator.ofInt(0, normalOptHeight);
            optionsAnimator.setTarget(optionsContainer);
            optionsAnimator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams layoutparams = optionsContainer.getLayoutParams();
                layoutparams.height = (int)animation.getAnimatedValue();
                optionsContainer.setLayoutParams(layoutparams);
            });

            ValueAnimator testBtnAnimator = ValueAnimator.ofInt(0, normalTestBtnWidth);
            testBtnAnimator.setTarget(speedTestBtn);
            testBtnAnimator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams layoutparams = speedTestBtn.getLayoutParams();
                layoutparams.width = (int)animation.getAnimatedValue();
                speedTestBtn.setLayoutParams(layoutparams);
            });

            animator = new AnimatorSet();
            animator.playTogether(optionsAnimator, testBtnAnimator);
            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {}
                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    consoleScrollView.fullScroll(View.FOCUS_DOWN);
                }
                @Override
                public void onAnimationCancel(@NonNull Animator animation) {}
                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {}
            });
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.start();

            notification();
        }
        int getStatus(){
            return cancelled ? 0 : 1;
        }
        boolean isRunning(){
            return !cancelled;
        }
    }
}
