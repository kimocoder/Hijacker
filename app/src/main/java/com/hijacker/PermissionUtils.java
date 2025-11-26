package com.hijacker;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;

public final class PermissionUtils {
    private PermissionUtils(){}

    // Return true if all provided permissions are currently granted
    public static boolean hasPermissions(Context ctx, String[] permissions){
        if(ctx==null) return false;
        for(String p : permissions){
            if(ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    // Request only the permissions that are missing. Must be called on UI thread.
    public static void requestMissingPermissions(Activity activity, String[] permissions, int requestCode){
        if(activity==null) return;
        if(permissions==null || permissions.length==0) return;
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        for(String p : permissions){
            if(ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED){
                missing.add(p);
            }
        }
        if(!missing.isEmpty()){
            String[] req = missing.toArray(new String[0]);
            ActivityCompat.requestPermissions(activity, req, requestCode);
        }
    }

    // Helper: returns the recommended runtime permissions used by the app that are dangerous.
    // Note: INTERNET and ACCESS_NETWORK_STATE are normal permissions and don't need runtime requests.
    public static String[] getDangerousPermissions(){
        return new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE
        };
    }

    // Notification permission for Android 13+
    public static String getNotificationPermission(){
        if(Build.VERSION.SDK_INT >= 33){
            return Manifest.permission.POST_NOTIFICATIONS;
        }
        return null;
    }
}

