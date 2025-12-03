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
import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

import static com.hijacker.CustomAction.TYPE_AP;
import static com.hijacker.CustomAction.TYPE_ST;
import static com.hijacker.CustomAction.cmds;
import static com.hijacker.MainActivity.FRAGMENT_CUSTOM;
import static com.hijacker.MainActivity.background;
import static com.hijacker.MainActivity.busybox;
import static com.hijacker.MainActivity.currentFragment;
import static com.hijacker.MainActivity.debug;
import static com.hijacker.MainActivity.getPIDs;
import static com.hijacker.MainActivity.notification;
import static com.hijacker.Shell.runOne;

public class CustomActionFragment extends Fragment {
    static CustomActionTask task;
    View fragmentView;
    View optionsContainer;
    Button startBtn, targetBtn, actionBtn;
    TextView consoleView;
    ScrollView consoleScrollView;
    //Dimensions to restore animated views
    int normalOptHeight = -1;

    //User options
    // Keep legacy static fields for compatibility but primary state lives in ViewModel
    static CustomAction selectedAction = null; // kept in sync with ViewModel
    static Device targetDevice;
    static String console_text = ""; // fallback cache

    CustomActionViewModel viewModel;

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState){
        fragmentView = inflater.inflate(R.layout.custom_action_fragment, container, false);

        optionsContainer = fragmentView.findViewById(R.id.options_container);
        consoleView = fragmentView.findViewById(R.id.console);
        consoleScrollView = fragmentView.findViewById(R.id.console_scroll_view);
        startBtn = fragmentView.findViewById(R.id.start_button);
        targetBtn = fragmentView.findViewById(R.id.select_target);
        actionBtn = fragmentView.findViewById(R.id.select_action);

        viewModel = new ViewModelProvider(requireActivity()).get(CustomActionViewModel.class);

        // Restore ViewModel-backed state into UI
        if(task==null) task = new CustomActionTask();

        // Observe ViewModel
        viewModel.getSelectedAction().observe(getViewLifecycleOwner(), a -> {
            selectedAction = a; // keep legacy static in sync
            if(a!=null){ actionBtn.setText(a.getTitle()); targetBtn.setEnabled(true); }
            else { actionBtn.setText(getString(R.string.select_action)); targetBtn.setEnabled(false); }
        });
        viewModel.getTargetDevice().observe(getViewLifecycleOwner(), d -> {
            targetDevice = d; // legacy sync
            if(d!=null){ targetBtn.setText(d.toString()); startBtn.setEnabled(true); }
        });
        viewModel.getConsoleText().observe(getViewLifecycleOwner(), s -> {
            console_text = s == null ? "" : s; // cache
            if(consoleView!=null){ consoleView.setText(console_text); consoleView.post(() -> consoleScrollView.fullScroll(View.FOCUS_DOWN)); }
        });
        viewModel.isRunning().observe(getViewLifecycleOwner(), r -> {
            boolean running = r != null && r;
            if(startBtn!=null) startBtn.setText(running ? R.string.stop : R.string.start);
        });
        viewModel.getOptionsHeight().observe(getViewLifecycleOwner(), h -> {
            if(h!=null && h!=-1) normalOptHeight = h;
        });

        actionBtn.setOnClickListener(view -> showActionSelector());
        targetBtn.setOnClickListener(view -> showTargetSelector());
        startBtn.setOnClickListener(view -> {
            if(isRunning()){
                //Stop
                startBtn.setEnabled(false);
                task.requestCancel();
            }else{
                task = new CustomActionTask();
                task.start();
            }
        });

        return fragmentView;
    }
    @Override
    public void onResume(){
        super.onResume();
        currentFragment = FRAGMENT_CUSTOM;
        ((MainActivity) requireActivity()).refreshDrawer();

        //Console text is saved/restored on pause/resume
        consoleView.setText(viewModel.getConsoleTextValue());
        consoleView.post(() -> consoleScrollView.fullScroll(View.FOCUS_DOWN));
    }
    @Override
    public void onPause(){
        super.onPause();

        // Save console text to ViewModel
        viewModel.setConsoleText(consoleView.getText().toString());
    }
    @Override
    public void onStart(){
        super.onStart();

        //Restore options
        // ViewModel observers will update UI appropriately; ensure buttons reflect cached values
        CustomAction sa = viewModel.getSelectedActionValue();
        Device td = viewModel.getTargetDeviceValue();
        if(sa!=null){ actionBtn.setText(sa.getTitle()); targetBtn.setEnabled(true); }
        if(td!=null){ targetBtn.setText(td.toString()); startBtn.setEnabled(true); }
        startBtn.setText(viewModel.isRunningValue() ? R.string.stop : R.string.start);

        //Restore animated views
        if(task!=null && task.isRunning()){
            ViewGroup.LayoutParams layoutParams = optionsContainer.getLayoutParams();
            layoutParams.height = 0;
            optionsContainer.setLayoutParams(layoutParams);
        }else if(normalOptHeight!=-1){
            ViewGroup.LayoutParams params = optionsContainer.getLayoutParams();
            params.height = normalOptHeight;
            optionsContainer.setLayoutParams(params);

            consoleScrollView.fullScroll(View.FOCUS_DOWN);
        }
    }
    @Override
    public void onStop(){
        if(task!=null){ if(task.sizeAnimator!=null) task.sizeAnimator.cancel(); }
        super.onStop();
    }
    static boolean isRunning(){
        // Prefer ViewModel's running state if available
        // Fall back to task runtime flag if ViewModel unavailable
        try{
            MainActivity ma = MainActivity.getInstance();
            if (ma != null){
                CustomActionViewModel vm = new ViewModelProvider(ma).get(CustomActionViewModel.class);
                return vm.isRunningValue();
            }
            // No activity instance available; fall through to task check
        }catch(Exception e){
            if(task==null) return false;
            return task.isRunning();
        }
        if(task==null) return false;
        return task.isRunning();
    }

    void showActionSelector(){
        PopupMenu popup = new PopupMenu(getActivity(), actionBtn);
        popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());

        //add(groupId, itemId, order, title)
        int i;
        for(i=0;i<cmds.size();i++){
            popup.getMenu().add(cmds.get(i).getType(), i, i, cmds.get(i).getTitle());
        }
        popup.getMenu().add(-1, 0, i+1, getString(R.string.manage_actions));

        popup.setOnMenuItemClickListener(item -> {
            if(item.getGroupId()==-1){
                //Open actions manager via NavController if available, otherwise fall back
                try{
                    android.app.Activity act = getActivity();
                    if(act instanceof MainActivity){
                        MainActivity main = (MainActivity)act;
                        if(main.getNavController()!=null){
                            main.getNavController().navigate(R.id.nav_custom_manager);
                        }else{
                            Log.w("HIJACKER/Navigation", "NavController not available: cannot navigate to CustomActionManager");
                        }
                    }else{
                        Log.w("HIJACKER/Navigation", "Activity is not MainActivity: cannot navigate to CustomActionManager");
                    }
                }catch(Exception e){
                    Log.w("HIJACKER/Navigation", "Exception while navigating to CustomActionManager", e);
                }
            }else{
                onActionSelected(cmds.get(item.getItemId()));
            }
            return true;
        });
        popup.show();
    }
    void showTargetSelector(){
        PopupMenu popup = new PopupMenu(getActivity(), targetBtn);
        popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());

        //add(groupId, itemId, order, title)
        int i;
        if(selectedAction.getType()==TYPE_AP){
            i = 0;
            for(AP ap : AP.APs){
                popup.getMenu().add(TYPE_AP, i, i, ap.toString());
                if(selectedAction.requiresClients() && ap.clients.isEmpty()){
                    popup.getMenu().findItem(i).setEnabled(false);
                }
                i++;
            }
        }else{
            i = 0;
            for(ST st : ST.STs){
                popup.getMenu().add(TYPE_ST, i, i, st.toString());
                if(selectedAction.requiresConnected() && st.bssid==null){
                    popup.getMenu().findItem(i).setEnabled(false);
                }
                i++;
            }
        }

        popup.setOnMenuItemClickListener(item -> {
            switch(item.getGroupId()){
                case TYPE_AP:
                    //ap
                    onTargetSelected(AP.APs.get(item.getItemId()));
                    break;
                case TYPE_ST:
                    //st
                    onTargetSelected(ST.STs.get(item.getItemId()));
                    break;
            }
            return true;
        });
        if(popup.getMenu().size()>0) popup.show();
    }

    void onActionSelected(CustomAction newAction){
        targetBtn.setEnabled(true);

        if(selectedAction!=null){
            if(newAction.getType()!=selectedAction.getType()){
                //Different types
                targetDevice = null;
                targetBtn.setText(getString(R.string.select_target));

                startBtn.setEnabled(false);
            }
        }

        selectedAction = newAction;
        actionBtn.setText(selectedAction.getTitle());
    }
    void onTargetSelected(Device newDevice){
        targetDevice = newDevice;

        targetBtn.setText(targetDevice.toString());
        startBtn.setEnabled(true);
    }

    class CustomActionTask {
        Shell shell;
        ValueAnimator sizeAnimator;
        // Local cancellation flag to avoid calling deprecated AsyncTask.cancel(boolean)
        private volatile boolean userRequestedCancel = false;
        // Track running state to avoid calling deprecated AsyncTask.getStatus()/Status
        private volatile boolean running = false;
        private java.util.concurrent.ExecutorService executor;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        @SuppressLint("WrongThread")
        private void onPreExecute(){
            running = true;
            viewModel.setRunning(true);
            viewModel.setOptionsHeight(optionsContainer.getHeight());
            startBtn.setText(R.string.stop);
            MainActivity.setProgressIndeterminate(true);

            postProgress("\nRunning: " + (selectedAction != null ? selectedAction.getStartCmd() : "<unknown>"));
            consoleScrollView.fullScroll(View.FOCUS_DOWN);
            if(debug) Log.d("HIJACKER/CustomCMDFrag", "Running: " + selectedAction.getStartCmd());

            normalOptHeight = optionsContainer.getHeight();
            viewModel.setOptionsHeight(normalOptHeight);

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

        private Boolean doInBackground(){
            shell = Shell.getFreeShell();

            //Start the action
            selectedAction.run(shell, targetDevice);

            //Read output until it's finished or cancelled
            BufferedReader out = shell.getShell_out();
            try{
                String end = "ENDOFCUSTOM";
                String buffer = out.readLine();
                // Respect our own requestCancel() flag (avoid deprecated AsyncTask.isCancelled())
                while(!end.equals(buffer) && !userRequestedCancel){
                    postProgress(buffer);
                    buffer = out.readLine();
                }
                if(debug) Log.d("HIJACKER/CustomCMDFrag", "thread done");
            }catch(IOException ignored){
                return false;
            }

            // Use our own cancellation flag instead of the deprecated AsyncTask.isCancelled()
            if(userRequestedCancel){
                if(selectedAction.hasProcessName()){
                    if(debug) Log.d("HIJACKER/CustomCMDFrag", "Killing process named " + selectedAction.getProcessName());
                    postProgress("Killing process named " + selectedAction.getProcessName());

                    ArrayList<Integer> list = getPIDs(selectedAction.getProcessName());
                    for(int i=0;i<list.size();i++){
                        runOne(busybox + " kill " + list.get(i));
                    }
                }

                if(selectedAction.hasStopCmd()){
                    if(debug) Log.d("HIJACKER/CustomCMDFrag", "Running: " + selectedAction.getStopCmd());
                    postProgress("Running: " + selectedAction.getStopCmd());

                    runOne(selectedAction.getStopCmd());
                }
                postProgress("Interrupted");
            }else{
                postProgress("Done");
            }

            if(shell!=null) shell.done();

            // Update ViewModel running state
            viewModel.setRunning(false);
            return true;
        }

        private void onPostExecute(final Boolean success){
            if(debug) Log.d("HIJACKER/CustomCMDFrag", "onPostExecute success=" + success);
            mainHandler.post(this::done);
        }

        private void done(){
            // mark not running before restoring UI
            running = false;
            viewModel.setRunning(false);
            startBtn.setEnabled(true);
            startBtn.setText(R.string.start);
            MainActivity.setProgressIndeterminate(false);

            sizeAnimator = ValueAnimator.ofInt(0, normalOptHeight);
            sizeAnimator.setTarget(optionsContainer);
            sizeAnimator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams layoutParams = optionsContainer.getLayoutParams();
                layoutParams.height = (int)animation.getAnimatedValue();
                optionsContainer.setLayoutParams(layoutParams);
            });
            sizeAnimator.addListener(new Animator.AnimatorListener() {
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
            sizeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            sizeAnimator.start();

            notification();
        }

        // Request cancellation from the UI without calling the deprecated cancel(boolean)
        void requestCancel(){
            userRequestedCancel = true;
            // Try to interrupt the AsyncTask by calling cancel(true) was deprecated; instead
            // attempt to close the shell to make the loop exit faster and let AsyncTask finish.
            try{
                if(shell!=null){
                    shell.done();
                }
            }catch(Exception ignored){}
        }

        // Post progress updates to the UI thread without using deprecated AsyncTask APIs
        private void postProgress(final String text){
            final String s = text + '\n';
            new Handler(Looper.getMainLooper()).post(() -> {
                if(currentFragment==FRAGMENT_CUSTOM && !background){
                    consoleView.append(s);
                    consoleScrollView.fullScroll(View.FOCUS_DOWN);
                }
                // Always append to ViewModel console so it survives rotation
                viewModel.appendConsole(s);
            });
        }

        // Expose running state so callers don't need to use deprecated AsyncTask APIs
        boolean isRunning(){
            return running;
        }

        void start(){
            if(running) return;
            executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "CustomActionTaskThread"));
            // run pre-execute on main
            new Handler(Looper.getMainLooper()).post(this::onPreExecute);
            executor.submit(() -> {
                Boolean res = doInBackground();
                onPostExecute(res);
                return null;
            });
        }
    }
}
