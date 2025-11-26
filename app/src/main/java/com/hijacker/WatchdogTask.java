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

import android.content.Context;
import android.app.NotificationManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hijacker.MainActivity.PROCESS_AIREPLAY;
import static com.hijacker.MainActivity.PROCESS_AIRODUMP;
import static com.hijacker.MainActivity.PROCESS_MDK_BF;
import static com.hijacker.MainActivity.PROCESS_MDK_DOS;
import static com.hijacker.MainActivity.aireplay_running;
import static com.hijacker.MainActivity.background;
import static com.hijacker.MainActivity.debug;
import static com.hijacker.MainActivity.getPIDs;
import static com.hijacker.MainActivity.last_action;
import static com.hijacker.MainActivity.mFragmentManager;
import static com.hijacker.MainActivity.mNotificationManager;
import static com.hijacker.MainActivity.stop;

/**
 * WatchdogRunnable replaces the deprecated AsyncTask-based WatchdogTask.
 * It exposes start(), requestStop() and isRunning() to be compatible with callers.
 */
class WatchdogTask {
    static final int SLEEP_TIME = 5000, PAUSE_TIME = 1000;
    // store application context to avoid leaking an Activity
    Context con;
    // Use our own stop flag to avoid calling the deprecated cancel(true) externally.
    private volatile boolean shouldStop = false;
    // Track whether task is running
    private volatile boolean running = false;
    // Reference to the background thread so we can interrupt it
    private volatile Thread watchdogThread = null;
    // Executor for background work
    private ExecutorService executor = null;

    WatchdogTask(Context context){
        // use application context to prevent leaking the Activity
        this.con = context.getApplicationContext();
    }

    // Start the watchdog on a single background thread.
    public synchronized void start(){
        if(running) return;
        shouldStop = false;
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WatchdogThread");
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            try{
                watchdogThread = Thread.currentThread();
                running = true;
                while(!shouldStop){
                    if(debug) Log.d("HIJACKER/watchdog", "Watchdog watching...");

                    check(PROCESS_AIRODUMP, Airodump.isRunning(), con.getString(R.string.airodump_still_running), con.getString(R.string.airodump_not_running));
                    check(PROCESS_AIREPLAY, aireplay_running!=0, con.getString(R.string.aireplay_still_running), con.getString(R.string.aireplay_not_running));
                    check(PROCESS_MDK_BF, MDKFragment.bf, con.getString(R.string.mdk_still_running), con.getString(R.string.mdk_not_running));
                    check(PROCESS_MDK_DOS, MDKFragment.ados, con.getString(R.string.mdk_still_running), con.getString(R.string.mdk_not_running));
                    //Can't check Reaver, it normally stops on its own, no way to know if there is a problem

                    Thread.sleep(SLEEP_TIME);
                    if(shouldStop) throw new InterruptedException();
                }
            }catch(InterruptedException e){
                Log.d("HIJACKER/watchdog", "Watchdog interrupted");
            }finally{
                running = false;
                watchdogThread = null;
                // shutdown executor
                if(executor!=null){
                    try{ executor.shutdownNow(); }catch(Exception ignored){}
                    executor = null;
                }
            }
        });
    }

    // Non-deprecated way to request the task to stop.
    public void requestStop(){
        shouldStop = true;
        // interrupt the background thread if active
        if(watchdogThread != null){
            try{ watchdogThread.interrupt(); }catch(Exception ignored){}
        }
        if(executor!=null){
            try{ executor.shutdownNow(); }catch(Exception ignored){}
            executor = null;
        }
    }
    void sleep() throws InterruptedException{
        while(System.currentTimeMillis()-last_action < 1000){
            if(debug) Log.d("HIJACKER/watchdog", "Watchdog waiting for 1 sec...");
            Thread.sleep(PAUSE_TIME);
            if(shouldStop) throw new InterruptedException();
        }
    }
    void check(int process, boolean running, String stillRunning, String notRunning) throws InterruptedException{
        sleep();
        if(shouldStop) throw new InterruptedException();

        List<Integer> list = getPIDs(process);
        if(running && list.isEmpty()){
            //process not running
            // Post UI update to main thread
            MainActivity.runInHandler(() -> {
                if(background){
                    NotificationCompat.Builder nb = new NotificationCompat.Builder(con, "error_channel")
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(con.getString(R.string.watchdog_notif_title))
                            .setContentText(notRunning)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true);
                    if(mNotificationManager != null){
                        mNotificationManager.notify(1, nb.build());
                    }else{
                        NotificationManager nm = (NotificationManager) con.getSystemService(Context.NOTIFICATION_SERVICE);
                        if(nm!=null) nm.notify(1, nb.build());
                    }
                }else{
                    ErrorDialog dialog = new ErrorDialog();
                    dialog.setTitle(notRunning);
                    dialog.setMessage(con.getString(R.string.watchdog_message));
                    dialog.show(mFragmentManager, "ErrorDialog");
                }
            });
            stop(process);
        }else if(!running && !list.isEmpty()){
            //process still running
            stop(process);      //Try to stop it
            if(!getPIDs(process).isEmpty()){
                //Didn't work
                MainActivity.runInHandler(() -> {
                    if(background){
                        NotificationCompat.Builder nb = new NotificationCompat.Builder(con, "error_channel")
                                .setSmallIcon(R.drawable.ic_notification)
                                .setContentTitle(con.getString(R.string.watchdog_notif_title))
                                .setContentText(stillRunning)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setAutoCancel(true);
                        if(mNotificationManager != null){
                            mNotificationManager.notify(1, nb.build());
                        }else{
                            NotificationManager nm = (NotificationManager) con.getSystemService(Context.NOTIFICATION_SERVICE);
                            if(nm!=null) nm.notify(1, nb.build());
                        }
                    }else{
                        ErrorDialog dialog = new ErrorDialog();
                        dialog.setTitle(stillRunning);
                        dialog.setMessage(con.getString(R.string.watchdog_message));
                        dialog.show(mFragmentManager, "ErrorDialog");
                    }
                });
            }
        }
    }
    boolean isRunning(){
        return running && !shouldStop;
    }
}
