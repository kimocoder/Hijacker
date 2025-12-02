package com.hijacker;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class DialogRefreshTask {
    @SuppressLint("StaticFieldLeak")
    final        // This object will exist as long as the device dialog exists
    DeviceDialog deviceDialog;
    private ExecutorService executor;
    private volatile boolean shouldStop = false;

    DialogRefreshTask(@NonNull DeviceDialog deviceDialog){
        this.deviceDialog = deviceDialog;
    }

    void start(){
        if(executor!=null) return;
        shouldStop = false;
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DialogRefreshThread"));
        executor.submit(() -> {
            try{
                while(!shouldStop && deviceDialog.isResumed()){
                    deviceDialog.onRefresh();
                    Thread.sleep(1000);
                }
            }catch(InterruptedException ignored){}
            finally{
                stop();
            }
        });
    }

    void stop(){
        shouldStop = true;
        if(executor!=null){
            try{ executor.shutdownNow(); }catch(Exception ignored){}
            executor = null;
        }
    }
}
