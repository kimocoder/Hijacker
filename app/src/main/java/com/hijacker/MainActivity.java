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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.util.JsonReader;
import androidx.core.content.pm.PackageInfoCompat;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import static com.hijacker.AP.getAPByMac;
import static com.hijacker.CustomAction.TYPE_ST;
import static com.hijacker.Device.trimMac;
import static com.hijacker.IsolatedFragment.is_ap;
import static com.hijacker.Shell.getFreeShell;
import static com.hijacker.Shell.runOne;

public class MainActivity extends AppCompatActivity{
    static final String NETHUNTER_BOOTKALI_BASH = "/data/data/com.offsec.nethunter/files/scripts/bootkali_bash";
    static final String RELEASES_LINK = "https://api.github.com/repos/chrisk44/Hijacker/releases";
    static final String WORDLISTS_LINK = "https://api.github.com/repos/chrisk44/Hijacker/contents/wordlists";
    static final int BUFFER_SIZE = 1048576;
    static final int MAX_READLINE_SIZE = 10000;
    static final int AIREPLAY_DEAUTH = 1, AIREPLAY_WEP = 2;
    static final int BAND_2 = 1, BAND_5 = 2, BAND_BOTH = 3;
    static final int FRAGMENT_AIRODUMP = R.id.nav_airodump, FRAGMENT_MDK = R.id.nav_mdk3, FRAGMENT_CRACK = R.id.nav_crack,
            FRAGMENT_REAVER = R.id.nav_reaver, FRAGMENT_CUSTOM = R.id.nav_custom_actions, FRAGMENT_SETTINGS = R.id.nav_settings;
    static final int PROCESS_AIRODUMP=0, PROCESS_AIREPLAY=1, PROCESS_MDK_BF=2, PROCESS_MDK_DOS=3, PROCESS_AIRCRACK=4, PROCESS_REAVER=5;
    static final int SORT_NOSORT = 0, SORT_ESSID = 1, SORT_BEACONS_FRAMES = 2, SORT_DATA_FRAMES = 3, SORT_PWR = 4;
    static final int CHROOT_FOUND = 0, CHROOT_BIN_MISSING = 1, CHROOT_DIR_MISSING = 2, CHROOT_BOTH_MISSING = 3;
    //State variables
    static boolean wpacheckcont = false;
    static boolean notif_on = false, background = false;    //notif_on: notification should be shown, background: the app is running in the background
    static int aireplay_running = 0, currentFragment = FRAGMENT_AIRODUMP;         //Set currentFragment in onResume of each Fragment
    static String last_airodump = null, last_aireplay = null, last_mdk = null, last_reaver = null;
    //Filters
    static boolean show_ap = true, show_st = true, show_na_st = true, wpa = true, wep = true, opn = true;
    static boolean[] show_ch = {true, false, false, false, false, false, false, false, false, false, false, false, false, false, false};
    static int pwr_filter = 120;
    static String manuf_filter = "";
    //Airodump list sort 
    static int sort = SORT_NOSORT;
    static boolean sort_reverse = false;
    static boolean toSort = false;     //Variable to mark that the list must be sorted, so Tile.sort() must be called
    //Views that need to be accessible globally
    // UI views - avoid static references to Activity views to prevent leaks
    TextView ap_count, st_count;                               //AP and ST count textviews in toolbar
    ProgressBar progress;
    Toolbar toolbar;
    View rootView;
    // Keep a static weak reference to application context only
    // Do not keep NotificationCompat.Builder instances as static fields (they hold a Context and may leak an Activity).
    // Keep the application context and build NotificationCompat.Builder on-demand.
    static Context appContext;
    // Hold a static reference to the MainActivity instance so static helpers can access instance views safely.
    // This is acceptable because we store the Activity instance only while the Activity is alive; clear onDestroy if necessary.
    static MainActivity instance;
    static NotificationManager mNotificationManager;
    // Notification builders cached (constructed with application context in setup)
    static NotificationCompat.Builder notif;
    static NotificationCompat.Builder error_notif;
    static NotificationCompat.Builder handshake_notif;
    static FragmentManager mFragmentManager;
    static String path, cap_tmp_path, data_path, actions_path, wl_path, cap_path, reaver_sess_path, firm_backup_file, manufDBFile, arch, busybox;             //path: App files path (ends with .../files)
    // Restored static globals used across the codebase
    static MyListAdapter adapter;
    static CustomActionAdapter custom_action_adapter;
    static FileExplorerAdapter file_explorer_adapter;
    static SharedPreferences pref;
    static SharedPreferences.Editor pref_edit;
    static Drawable[] overflow = {null, null, null, null, null, null, null, null};
    static SparseArray<String> navTitlesMap = new SparseArray<>();
    static int progress_int;
    static ClipboardManager clipboard;
    static Thread wpa_thread;
    static Runnable wpa_runnable;
    static Menu menu;
    static long last_action;
    static File aliases_file;
    static FileWriter aliases_in;
    static final HashMap<String, String> aliases = new HashMap<>();
    static HashMap<String, String> manufHashMap;
    //App and device info
    static String versionName, deviceModel;
    static int versionCode;
    static String devChipset = "";
    static ActionBar actionBar;
    static String bootkali_init_bin = "bootkali_init";
    //Preferences - Defaults are in strings.xml
    static String iface, prefix, airodump_dir, aireplay_dir, aircrack_dir, mdk3bf_dir, mdk3dos_dir, reaver_dir, chroot_dir,
            enable_monMode, disable_monMode, custom_chroot_cmd;
    static int deauthWait, band;
    static boolean show_notif, show_details, airOnStartup, debug, show_client_count,
            monstart, always_cap, cont_on_fail, watchdog, target_deauth, enable_on_airodump, update_on_startup;

    WatchdogTask watchdogTask;
    ReaverFragment reaverFragment = new ReaverFragment();
    CrackFragment crackFragment = new CrackFragment();
    CustomActionFragment customActionFragment = new CustomActionFragment();
    DrawerLayout mDrawerLayout;
    NavigationView navigationView;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        instance = this;
        appContext = getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            throwable.printStackTrace();
            StringBuilder stackTrace = new StringBuilder();
            stackTrace.append(throwable.getMessage()).append('\n');
            for(int i=0;i<throwable.getStackTrace().length;i++){
                stackTrace.append(throwable.getStackTrace()[i].toString()).append('\n');
            }

            Intent intent = new Intent();
            intent.setAction("com.hijacker.SendLogActivity");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("exception", stackTrace.toString());
            startActivity(intent);

            finish();
            System.exit(1);
        });
        adapter = new MyListAdapter(); //ALWAYS BEFORE setContentView AND setup(), can't stress it enough...
        adapter.setNotifyOnChange(true);
        custom_action_adapter = new CustomActionAdapter();
        custom_action_adapter.setNotifyOnChange(true);
        file_explorer_adapter = new FileExplorerAdapter();
        file_explorer_adapter.setNotifyOnChange(true);
        setContentView(R.layout.activity_main);
        // Initialize FragmentManager early so background setup tasks can show dialogs safely
        mFragmentManager = getSupportFragmentManager();

        // Initialize NotificationManager early to avoid NPE when notifications are used before setup finishes
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        new SetupTask().start();
    }
    private class SetupTask {
        LoadingDialog loadingDialog;
        ErrorDialog errorDialog;
        CustomDialog customDialog;
        // Handler to post progress updates to the main thread (replacement for publishProgress)
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private java.util.concurrent.ExecutorService executor;

        private void postProgress(final String... progress){
            mainHandler.post(() -> {
                if(progress != null){
                    if(progress.length > 0 && progress[0] != null){
                        // Update loading dialog text
                        if(loadingDialog != null) loadingDialog.setText(progress[0]);
                    }else if(progress.length > 1 && progress[1] != null){
                        // Show error via ErrorDialog
                        if(errorDialog != null){
                            errorDialog.setMessage(progress[1]);
                            errorDialog.setTitle(progress.length == 3 ? progress[2] : null);
                            errorDialog.show(mFragmentManager, "ErrorDialog");
                        }
                    }
                }
            });
        }
        @SuppressLint("CommitPrefEdits")
        protected void onPreExecute(){
            pref = MainActivity.this.getSharedPreferences(MainActivity.this.getPackageName() + "_preferences", Context.MODE_PRIVATE);
            pref_edit = pref.edit();

            errorDialog = new ErrorDialog();
            loadingDialog = new LoadingDialog();
            loadingDialog.setInitText(getString(R.string.starting_hijacker));
            loadingDialog.show(mFragmentManager, "LoadingDialog");

            if(!pref.getBoolean("disclaimerAccepted", false)){
                //First start
                customDialog = new CustomDialog();
                customDialog.setCancelable(false);
                customDialog.setTitle(getString(R.string.disclaimer_title));
                customDialog.setMessage(getString(R.string.disclaimer));
                customDialog.setPositiveButton(getString(R.string.agree), () -> {
                    pref_edit.putBoolean("disclaimerAccepted", true);
                    pref_edit.apply();
                });
                // Exit
                customDialog.setNeutralButton(getString(R.string.not_agree), MainActivity.this::finish);

                customDialog.show(mFragmentManager, "CustomDialog for disclaimer");
            }

            //Initialize the drawer
            mDrawerLayout = findViewById(R.id.drawer_layout);
            navigationView = findViewById(R.id.nav_view);
            navigationView.getMenu().getItem(0).setChecked(true);
            navigationView.setNavigationItemSelectedListener(
                    menuItem -> {
                        // set item as selected to persist highlight
                        menuItem.setChecked(true);
                        // close drawer when item is tapped
                        mDrawerLayout.closeDrawers();

                        final int id = menuItem.getItemId();
                        if (currentFragment != id) {
                            FragmentTransaction ft = mFragmentManager.beginTransaction();
                            if (id == FRAGMENT_AIRODUMP) {
                                ft.replace(R.id.fragment1, is_ap==null ? new MyListFragment() : new IsolatedFragment());
                            } else if (id == FRAGMENT_MDK) {
                                ft.replace(R.id.fragment1, new MDKFragment());
                            } else if (id == FRAGMENT_REAVER) {
                                ft.replace(R.id.fragment1, reaverFragment);
                            } else if (id == FRAGMENT_CRACK) {
                                ft.replace(R.id.fragment1, crackFragment);
                            } else if (id == FRAGMENT_CUSTOM) {
                                ft.replace(R.id.fragment1, customActionFragment);
                            } else if (id == FRAGMENT_SETTINGS) {
                                ft.replace(R.id.fragment1, new SettingsFragment());
                            }
                            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                            ft.addToBackStack(null);
                            ft.commitAllowingStateLoss();
                            mFragmentManager.executePendingTransactions();
                        }

                        actionBar.setTitle(navTitlesMap.get(currentFragment));

                        return true;
                    });

            //Initialize toolbar
            toolbar = findViewById(R.id.my_toolbar);
            setSupportActionBar(toolbar);
            toolbar.setOverflowIcon(overflow[0]);

            ActionBar actionbar = getSupportActionBar();
            if(actionbar!=null){
                actionbar.setDisplayHomeAsUpEnabled(true);
                actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);
            }else{
                Log.e("HIJACKER/SetupPreEx", "actionbar is null");
            }
        }
        protected Boolean doInBackground(Void... params) {
            Looper.prepare();

            //Wait for disclaimer to be accepted
            if(customDialog!=null){
                customDialog._wait();
            }

            //Initialize managers
            postProgress(getString(R.string.loading_device_information));
            PackageManager manager = MainActivity.this.getPackageManager();
            PackageInfo info = null;
            try{
                info = manager.getPackageInfo(MainActivity.this.getPackageName(), 0);
                versionName = info.versionName.replace(" ", "_");
                // Use PackageInfoCompat to obtain the versionCode in a forward-compatible way
                versionCode = (int) PackageInfoCompat.getLongVersionCode(info);
            }catch(PackageManager.NameNotFoundException e){
                Log.e("HIJACKER/SetupTask", e.toString());
            }
            deviceModel = Build.MODEL;
            if(!deviceModel.startsWith(Build.MANUFACTURER)) deviceModel = Build.MANUFACTURER + " " + deviceModel;
            deviceModel = deviceModel.replace(" ", "_");
            //devChipset is set later because busybox needs to be extracted
            arch = System.getProperty("os.arch");

            //Find views
            postProgress(getString(R.string.init_views));
            ap_count = findViewById(R.id.ap_count);
            st_count = findViewById(R.id.st_count);
            progress = findViewById(R.id.progressBar);
            rootView = findViewById(R.id.fragment1);
            overflow[0] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow0);
            overflow[1] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow1);
            overflow[2] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow2);
            overflow[3] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow3);
            overflow[4] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow4);
            overflow[5] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow5);
            overflow[6] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow6);
            overflow[7] = AppCompatResources.getDrawable(MainActivity.this, R.drawable.overflow7);
            actionBar = getSupportActionBar();

            //Load defaults
            postProgress(getString(R.string.loading_defaults));
            iface = getString(R.string.iface);
            prefix = getString(R.string.prefix);
            enable_monMode = getString(R.string.enable_monMode);
            disable_monMode = getString(R.string.disable_monMode);
            enable_on_airodump = Boolean.parseBoolean(getString(R.string.enable_on_airodump));
            deauthWait = Integer.parseInt(getString(R.string.deauthWait));
            show_notif = Boolean.parseBoolean(getString(R.string.show_notif));
            show_details = Boolean.parseBoolean(getString(R.string.show_details));
            airOnStartup = Boolean.parseBoolean(getString(R.string.airOnStartup));
            debug = Boolean.parseBoolean(getString(R.string.debug));
            always_cap = Boolean.parseBoolean(getString(R.string.always_cap));
            chroot_dir = getString(R.string.chroot_dir);
            monstart = Boolean.parseBoolean(getString(R.string.monstart));
            custom_chroot_cmd = "";
            cont_on_fail = Boolean.parseBoolean(getString(R.string.cont_on_fail));
            watchdog = Boolean.parseBoolean(getString(R.string.watchdog));
            target_deauth = Boolean.parseBoolean(getString(R.string.target_deauth));
            update_on_startup = Boolean.parseBoolean(getString(R.string.auto_update));
            band = Integer.parseInt(getString(R.string.band));
            show_client_count = Boolean.parseBoolean(getString(R.string.show_client_count));

            //Load preferences
            postProgress(getString(R.string.loading_preferences));
            loadPreferences();

            //Initialize paths
            postProgress(getString(R.string.init_files));
            path = getFilesDir().getAbsolutePath();
            cap_tmp_path = path + "/cap_tmp";
            data_path = Environment.getExternalStorageDirectory() + "/Hijacker";
            actions_path = data_path + "/actions";
            wl_path = data_path + "/wordlists";
            cap_path = data_path + "/capture_files";
            reaver_sess_path = data_path + "/reaver_sessions";
            firm_backup_file = data_path + "/fw_bcmdhd.orig.bin";
            manufDBFile = path + "/manuf.db";
            ArrayList<File> dirs = new ArrayList<>();
            dirs.add(new File(cap_tmp_path));
            dirs.add(new File(data_path));
            dirs.add(new File(actions_path));
            dirs.add(new File(wl_path));
            dirs.add(new File(cap_path));
            dirs.add(new File(reaver_sess_path));
            for(File dir : dirs){
                if(!dir.exists()){
                    dir.mkdir();
                }
            }
            //cap file directory used to be set by the user, so move everything to the new location
            if(pref.contains("cap_dir")){
                //Move capture files to new directory
                String old_dir = pref.getString("cap_dir", null);
                Toast.makeText(MainActivity.this, "Moving cap files from " + old_dir + " to " + cap_path, Toast.LENGTH_SHORT).show();
                runOne(" mv " + old_dir + "/* " + cap_path + "/ && rmdir " + old_dir);

                pref_edit.remove("cap_dir");
                pref_edit.apply();
            }else{
                //cap directory was never changed so there may be files in /sdcard/cap/
                File old_dir = new File("/sdcard/cap");
                if(old_dir.exists() && old_dir.isDirectory()){
                    File[] files = old_dir.listFiles();
                    if(files!=null){
                        Toast.makeText(MainActivity.this, "Moving cap files from " + old_dir.getAbsolutePath() + " to " + cap_path, Toast.LENGTH_LONG).show();
                        for(File f : Objects.requireNonNull(old_dir.listFiles())){
                            //Move all the files to the new directory
                            f.renameTo(new File(cap_path, f.getName()));
                        }
                    }
                    old_dir.delete();
                }
            }

            //Initialize notifications
            postProgress(getString(R.string.init_notifications));
                //Create intents
            Intent cancel_intent = new Intent(MainActivity.this, DismissReceiver.class);
            Intent stop_intent = new Intent(MainActivity.this, StopReceiver.class);
            Intent notificationIntent = new Intent(MainActivity.this, MainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            PendingIntent click_intent = PendingIntent.getActivity(MainActivity.this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

                // Get a channel ID
            String channelID;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                channelID = NotificationChannel.DEFAULT_CHANNEL_ID;
            }else{
                channelID = getString(R.string.DEFAULT_CHANNEL_ID);
            }

             //Create 'running' notification
             notif = new NotificationCompat.Builder(MainActivity.this.getApplicationContext(), channelID);
            notif.setContentTitle(getString(R.string.notification_title));
            notif.setContentText(" ");
            notif.setSmallIcon(R.drawable.ic_notification);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                notif.setColor(getColor(R.color.colorAccent));
            }
            notif.setDeleteIntent(PendingIntent.getBroadcast(MainActivity.this.getApplicationContext(), 0, cancel_intent, PendingIntent.FLAG_IMMUTABLE));
            notif.addAction(R.drawable.stop_drawable, getString(R.string.stop_attacks), PendingIntent.getBroadcast(MainActivity.this.getApplicationContext(), 0, stop_intent, PendingIntent.FLAG_IMMUTABLE));
            notif.setContentIntent(click_intent);

                //Create 'error' notification (used by watchdog)
            error_notif = new NotificationCompat.Builder(MainActivity.this.getApplicationContext(), channelID);
            error_notif.setSmallIcon(R.drawable.ic_notification);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                error_notif.setColor(getColor(android.R.color.holo_red_dark));
            }
            error_notif.setContentIntent(click_intent);
            error_notif.setVibrate(new long[]{500, 500});

                //Create 'handshake captured' notification (used by wpa_thread)
            handshake_notif = new NotificationCompat.Builder(MainActivity.this.getApplicationContext(), channelID);
            handshake_notif.setContentTitle(getString(R.string.handshake_captured));
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                handshake_notif.setColor(getColor(android.R.color.holo_green_dark));
            }
            handshake_notif.setSmallIcon(R.drawable.ic_notification);
            handshake_notif.setContentIntent(click_intent);
            handshake_notif.setVibrate(new long[]{500, 500});

            //Load strings for when they cannot be retrieved with getString or R.string
            postProgress(getString(R.string.loading_strings));
            ST.not_connected = getString(R.string.not_connected);
            ST.paired = getString(R.string.paired) + ' ';

            //Check permissions
            postProgress(getString(R.string.checking_permissions));
            // Request only the dangerous runtime permissions using the centralized helper.
            final String[] perms = PermissionUtils.getDangerousPermissions();
            runOnUiThread(() -> PermissionUtils.requestMissingPermissions(MainActivity.this, perms, 0));
            // Also request notification permission on Android 13+
            final String notifPerm = PermissionUtils.getNotificationPermission();
            if(notifPerm!=null){
                runOnUiThread(() -> PermissionUtils.requestMissingPermissions(MainActivity.this, new String[]{notifPerm}, 1));
            }
            // Wait briefly for user to respond to permission dialogs but do not block indefinitely.
            try{
                synchronized(MainActivity.this){
                    MainActivity.this.wait(5000);
                }
            }catch(InterruptedException ignored){}

            //Check for su
            postProgress(getString(R.string.checking_su));
            int exitCode = 1;
            try{
                Process su_proc = Runtime.getRuntime().exec("which su");
                su_proc.waitFor();
                exitCode = su_proc.exitValue();
            }catch(IOException | InterruptedException e){
                e.printStackTrace();
            }
            if(exitCode != 0){
                Log.e("HIJACKER/Setup", "'which su' failed with code " + exitCode);
                postProgress(null, getString(R.string.su_notfound), getString(R.string.su_notfound_title));
                errorDialog._wait();
                return false;
            }

            //Check for root access
            postProgress(getString(R.string.requesting_root_access));
            exitCode = 1;
            try{
                Process su_proc = Runtime.getRuntime().exec("su -c id");
                su_proc.waitFor();
                exitCode = su_proc.exitValue();
            }catch(IOException | InterruptedException e){
                e.printStackTrace();
            }
            if(exitCode != 0){
                Log.e("HIJACKER/Setup", "'su -c id' failed with code " + exitCode);
                postProgress(null, getString(R.string.no_root_access), getString(R.string.no_root_access_title));
                errorDialog._wait();
                return false;
            }

            //Setup tools (requires root)
            Log.d("HIJACKER/SetupTask", "Entering tools installation block");
            postProgress(getString(R.string.setting_up_tools));
            if(arch.equals("armv7l") || arch.equals("aarch64")) {
                String tools_location = path + "/bin/";
                String lib_location = path + "/lib/";

                //Create directories
                File bin = new File(path + "/bin");
                File lib = new File(path + "/lib");
                if(!bin.exists()){
                    if(!bin.mkdir()){
                        postProgress(null, getString(R.string.bin_not_created));
                        errorDialog._wait();
                        return false;
                    }
                }
                if(!lib.exists()){
                    if(!lib.mkdir()){
                        postProgress(null, getString(R.string.lib_not_created));
                        errorDialog._wait();
                        return false;
                    }
                }

                //Extract busybox (use extractWithLog so we can time out if it blocks)
                Log.d("HIJACKER/SetupTask", "About to extract busybox");
                extractWithLog("busybox", tools_location);
                Log.d("HIJACKER/SetupTask", "Finished extracting busybox, continuing setup");
                busybox = path + "/bin/busybox";

                //Extract tools
                boolean install = true;
                if(Objects.requireNonNull(bin.list()).length==21 && Objects.requireNonNull(lib.list()).length==2 && info!=null){
                    // Compare long version codes for accuracy on modern platforms
                    if(PackageInfoCompat.getLongVersionCode(info) == pref.getLong("tools_version", 0L)){
                        if(debug) Log.d("HIJACKER/SetupTask", "Tools already installed");
                        install = false;
                    }else{
                        File manufDB = new File(manufDBFile);
                        if(manufDB.exists()) manufDB.delete();
                    }
                }
                if(install){
                    Log.d("HIJACKER/SetupTask", "Starting extraction of tools (install=true)");
                    extractWithLog("airbase-ng", tools_location);
                    extractWithLog("aircrack-ng", tools_location);
                    extractWithLog("aireplay-ng", tools_location);
                    extractWithLog("airodump-ng", tools_location);
                    extractWithLog("besside-ng", tools_location);
                    extractWithLog("ivstools", tools_location);
                    extractWithLog("iw", tools_location);
                    extractWithLog("iwconfig", tools_location);
                    extractWithLog("iwlist", tools_location);
                    extractWithLog("iwpriv", tools_location);
                    extractWithLog("kstats", tools_location);
                    extractWithLog("makeivs-ng", tools_location);
                    extractWithLog("mdk3", tools_location);
                    extractWithLog("nc", tools_location);
                    extractWithLog("packetforge-ng", tools_location);
                    extractWithLog("reaver", tools_location);
                    extractWithLog("reaver-wash", tools_location);
                    extractWithLog("wesside-ng", tools_location);
                    extractWithLog("wpaclean", tools_location);
                    extractWithLog("libfakeioctl.so", lib_location);
                    extractWithLog("libnexmon.so", lib_location);

                    runOne("cd " + path + "/bin; mv mdk3 mdk3bf; cp mdk3bf mdk3dos");

                    if(info!=null){
                        pref_edit.putLong("tools_version", PackageInfoCompat.getLongVersionCode(info));
                        pref_edit.apply();
                    }
                    Log.d("HIJACKER/SetupTask", "Tool extraction complete");
                }

                //Detect device chipset
                postProgress(getString(R.string.detecting_device_chipset));
                Log.d("HIJACKER/SetupTask", "Detecting device chipset - starting shell operations");
                Shell shell = getFreeShell();

                String firmwarePath = findFirmwarePath(shell);
                if(firmwarePath!=null){
                    //Get chipset from firmware file
                    shell.run("strings " + firmwarePath + " | " + busybox + " grep \"FWID:\"; echo ENDOFSTRINGS");
                    devChipset = getLastLine(shell.getShell_out(), "ENDOFSTRINGS");
                    int index = devChipset.indexOf('-');
                    if(index != -1){
                        devChipset = devChipset.substring(0, index);
                    }
                }
                Log.i("HIJACKER/DetectDev", "devChipset is " + devChipset);
                Log.d("HIJACKER/SetupTask", "Device chipset detection complete: " + devChipset);
                shell.done();

                //Set directories
                prefix = "LD_PRELOAD=" + path + "/lib/";
                if(devChipset.startsWith("4339")) {
                    //BCM4339
                    prefix += "libfakeioctl.so";
                }else if(devChipset.startsWith("4358")){
                    //BCM4358
                    prefix += "libnexmon.so";
                }else{
                    //Default (detected but not included)
                    SettingsFragment.allow_prefix = true;       //Allow user to change the prefix
                    prefix = pref.getString("prefix", null);    //Use user-set prefix

                    if(prefix==null){
                        //No user-set prefix, use default
                        prefix = "LD_PRELOAD=" + path + "/lib/libfakeioctl.so";
                    }
                }

                airodump_dir = path + "/bin/airodump-ng";
                aireplay_dir = path + "/bin/aireplay-ng";
                aircrack_dir = path + "/bin/aircrack-ng";
                mdk3bf_dir = path + "/bin/mdk3bf";
                mdk3dos_dir = path + "/bin/mdk3dos";
                reaver_dir = path + "/bin/reaver";
            } else {
                Log.e("HIJACKER/onCreate", "Device not armv7l or aarch64, can't install tools");
                busybox = "busybox";
                postProgress(null, getString(R.string.not_arm));
                errorDialog._wait();

                prefix = pref.getString("prefix", prefix);
                airodump_dir = "airodump-ng";
                aireplay_dir = "aireplay-ng";
                aircrack_dir = "aircrack-ng";
                mdk3bf_dir = "mdk3";
                mdk3dos_dir = "mdk3";
                reaver_dir = "reaver";
            }

            //Initialize RootFile (requires root) and Airodump
            postProgress(getString(R.string.init_rootFile_airodump));
            RootFile.init();
            // Use new API-21-safe CapFileObserver implementation
            Airodump.capFileObserver = new AirodumpCapFileObserver(cap_tmp_path, 0);
            Log.d("HIJACKER/SetupTask", "RootFile and Airodump initialized");

            //Initialize threads
            postProgress(getString(R.string.init_threads));
            wpa_runnable = () -> {
                if(debug) Log.d("HIJACKER/wpa_thread", "Started wpa_thread");

                Thread counter_thread = new Thread(() -> {
                    if(debug) Log.d("HIJACKER/wpa_subthread", "wpa_subthread started");
                    try{
                        progress_int = 0;
                        while(progress_int<=deauthWait && wpacheckcont){
                            Thread.sleep(1000);
                            progress_int++;
                            runInHandler(() -> progress.setProgress(progress_int));
                        }
                        if(wpacheckcont){
                            runInHandler(() -> {
                                if(!background) Snackbar.make(findViewById(R.id.fragment1), getString(R.string.stopped_to_capture), Snackbar.LENGTH_SHORT).show();
                                else Toast.makeText(MainActivity.this, getString(R.string.stopped_to_capture), Toast.LENGTH_SHORT).show();
                                progress.setProgress(deauthWait);
                                progress.setIndeterminate(true);
                            });
                        }
                    }catch(InterruptedException e){
                        Log.e("HIJACKER/Exception", "Caught Exception in wpa_subthread: " + e);
                        runInHandler(() -> {
                            progress.setIndeterminate(false);
                            progress.setProgress(deauthWait);
                        });
                    }finally{
                        stop(PROCESS_AIREPLAY);
                    }
                    if(debug) Log.d("HIJACKER/wpa_subthread", "wpa_subthread finished");
                });

                boolean handshake_captured = false;
                final String capfile = Airodump.getCapFile();
                Shell shell = getFreeShell();
                try{
                    if(capfile==null){
                        if(debug) Log.d("HIJACKER/wpa_thread", "cap file not found, airodump is probably not running...");
                    }else{
                        if(debug) Log.d("HIJACKER/wpa_thread", capfile);
                        wpacheckcont = true;
                        counter_thread.start();

                        BufferedReader out = shell.getShell_out();
                        String buffer;
                        while(!handshake_captured && wpacheckcont) {
                            //Check loop
                            if(debug) Log.d("HIJACKER/wpa_thread", "Checking cap file...");
                            shell.run(aircrack_dir + " " + capfile + "; echo ENDOFAIR");
                            buffer = out.readLine();
                            if(buffer==null) break;
                            else{
                                while(!buffer.equals("ENDOFAIR")){
                                    if(buffer.length()>=56){
                                        if(buffer.charAt(56)=='1' || buffer.charAt(56)=='2' || buffer.charAt(56)=='3') {
                                            handshake_captured = true;
                                            break;
                                        }
                                    }
                                    buffer = out.readLine();
                                }
                                Thread.sleep(700);
                            }
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    Log.e("HIJACKER/Exception", "Caught Exception in wpa_thread: " + e);
                } finally {
                    wpacheckcont = false;
                    counter_thread.interrupt();
                    shell.done();
                    final boolean found = handshake_captured;
                    if(found) Airodump.startClean(is_ap);
                    runInHandler(() -> {
                        Button crack_btn = findViewById(R.id.crack);
                        if(crack_btn!=null){
                            //We are in IsolatedFragment
                            crack_btn.setText(getString(R.string.crack));
                        }

                        if(found){
                            if(!background){
                                Snackbar s = Snackbar.make(findViewById(R.id.fragment1), getString(R.string.handshake_captured) + ' ' + capfile, Snackbar.LENGTH_LONG);
                                s.setAction(R.string.crack, v -> {
                                    CrackFragment.capfile_text = capfile;
                                    FragmentTransaction ft = mFragmentManager.beginTransaction();
                                    ft.replace(R.id.fragment1, MainActivity.this.crackFragment);
                                    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                                    ft.addToBackStack(null);
                                    ft.commitAllowingStateLoss();
                                });
                                s.show();
                            } else {
                                // Build transient notification for handshake captured
                                NotificationCompat.Builder nb = new NotificationCompat.Builder(appContext, NotificationChannel.DEFAULT_CHANNEL_ID)
                                        .setContentTitle(getString(R.string.handshake_captured))
                                        .setContentText(getString(R.string.saved_in_file) + ' ' + capfile)
                                        .setSmallIcon(R.drawable.ic_notification)
                                        .setAutoCancel(true)
                                        .setPriority(NotificationCompat.PRIORITY_HIGH);

                                if (mNotificationManager != null) mNotificationManager.notify(2, nb.build());
                            }
                            progress.setIndeterminate(false);
                        }
                        if(debug) Log.d("HIJACKER/wpa_thread", "wpa_thread finished");
                    });
                }
            };
            wpa_thread = new Thread(wpa_runnable);
            watchdogTask = new WatchdogTask(MainActivity.this);

            //Start background service so the app won't get killed if it goes to the background
            postProgress(getString(R.string.starting_pers_service));
            startService(new Intent(MainActivity.this, PersistenceService.class));

            //Load manufacturer HashMap
            postProgress(getString(R.string.loading_manuf_db));
            manufHashMap = new HashMap<>();
            File db = new File(manufDBFile);
            if(!db.exists()){
                //Database has not been created
                try{
                    //Create the database
                    if(!db.createNewFile()){
                        Log.e("HIJACKER/SetupTask", "Error creating database file");
                    }else{
                        if(debug) Log.d("HIJACKER/SetupTask", "Creating manufacturer database...");
                        postProgress(getString(R.string.building_manuf_db));
                        BufferedReader out = new BufferedReader(new InputStreamReader(getResources().getAssets().open("oui.txt")));
                        FileWriter in = new FileWriter(db);

                        //Load data from oui.txt and write to the new database file
                        String buffer = out.readLine();
                        while(buffer!=null){
                            if(buffer.length()<18 || !buffer.contains("(base 16)")){
                                buffer = out.readLine();
                                continue;
                            }

                            String mac = buffer.substring(0, 6);
                            String manuf = buffer.substring(22);
                            if(manufHashMap.get(mac)==null){
                                //Write to file only if it was added to the HashMap (it's unique)
                                manufHashMap.put(mac, manuf);
                                in.write(mac + ";" + manuf + '\n');
                            }

                            buffer = out.readLine();
                        }
                        in.close();
                        out.close();
                        if(debug) Log.d("HIJACKER/SetupTask", "Manufacturer database built");
                    }
                }catch(IOException e){
                    Log.e("HIJACKER/SetupTask", e.toString());
                    manufHashMap = null;
                }
            }else{
                //Load database on the HashMap
                try{
                    //Database format:
                    //01B256;Manufacturer co.\n
                    BufferedReader out = new BufferedReader(new FileReader(db));

                    String buffer = out.readLine();
                    while(buffer!=null){
                        String mac = buffer.substring(0, buffer.indexOf(';'));
                        String manuf = buffer.substring(buffer.indexOf(';')+1);
                        manufHashMap.put(mac, manuf);
                        buffer = out.readLine();
                    }
                }catch(IOException e){
                    Log.e("HIJACKER/SetupTask", e.toString());
                    manufHashMap = null;
                }
            }

            //Load navigation titles to HashMap
            navTitlesMap.put(R.id.nav_airodump, getString(R.string.nav_airodump));
            navTitlesMap.put(R.id.nav_mdk3, getString(R.string.nav_mdk3));
            navTitlesMap.put(R.id.nav_reaver, getString(R.string.nav_reaver));
            navTitlesMap.put(R.id.nav_crack, getString(R.string.nav_crack));
            navTitlesMap.put(R.id.nav_custom_actions, getString(R.string.nav_custom_actions));
            navTitlesMap.put(R.id.nav_settings, getString(R.string.nav_settings));

            //Load custom actions and aliases (requires storage access)
            if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED){
                postProgress(getString(R.string.loading_custom_actions));
                CustomAction.load();

                postProgress(getString(R.string.loading_aliases));
                loadAliases();
            }

            //Check for updates
            if(update_on_startup){
                if(internetAvailable()){
                    postProgress(getString(R.string.checking_for_updates));
                    checkForUpdate(false);
                } else {
                    //Spawn new thread to wait for internet connection
                    //This should be changed to a broadcast receiver
                    new Thread(() -> {
                        try{
                            while(!internetAvailable()) {
                                Thread.sleep(1000);
                            }
                            checkForUpdate(false);
                        } catch (InterruptedException ignored) { }
                    }).start();
                }
            }

            //Delete old report, it's not needed if no exception is thrown up to this point (requires storage access)
            postProgress(getString(R.string.deleting_bug_report));
            File report = new File(Environment.getExternalStorageDirectory() + "/report.txt");
            if (report.exists()) report.delete();

            //Show FirstRunDialog
            if (customDialog!=null) {
                FirstRunDialog frDialog = new FirstRunDialog();
                frDialog.show(mFragmentManager, "FirstRunDialog");
                Log.d("HIJACKER/SetupTask", "FirstRunDialog shown; continuing without waiting");
                frDialog._wait();
            }

            Log.d("HIJACKER/SetupTask", "doInBackground reached end - returning true");

            return true;
        }
        protected void onPostExecute(final Boolean success){
            Log.d("HIJACKER/SetupTask", "onPostExecute entered (success=" + success + ")");
            try{
                if(!success){
                    // Initialization incomplete, can't continue!!!
                    Log.e("HIJACKER/SetupTask", "SetupTask reported failure in doInBackground");
                    System.exit(1);
                }

                if(loadingDialog!=null){
                    loadingDialog.setText(getString(R.string.starting_hijacker));
                }else{
                    Log.w("HIJACKER/SetupTask", "loadingDialog is null in onPostExecute");
                }

                if(watchdog){
                    watchdogTask.start();
                }

                //Load default fragment (airodump)
                try{
                    Log.d("HIJACKER/SetupTask", "Attempting to load default fragment");
                    if(mFragmentManager.getBackStackEntryCount()==0){
                        FragmentTransaction ft = mFragmentManager.beginTransaction();
                        ft.replace(R.id.fragment1, new MyListFragment());
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                        ft.addToBackStack(null);
                        ft.commitAllowingStateLoss();
                        Log.d("HIJACKER/SetupTask", "Default fragment transaction committed");
                    }
                }catch(Exception e){
                    Log.e("HIJACKER/SetupTask", "Exception while loading default fragment", e);
                    // Show error dialog if possible
                    postProgress(null, getString(R.string.unknown_error), e.toString());
                }

                try{
                    if(loadingDialog!=null) loadingDialog.dismissAllowingStateLoss();
                }catch(Exception e){
                    Log.e("HIJACKER/SetupTask", "Exception while dismissing loading dialog", e);
                }

                if(customDialog!=null){
                    try{
                        mDrawerLayout.openDrawer(GravityCompat.START);
                    }catch(Exception e){
                        Log.e("HIJACKER/SetupTask", "Exception opening drawer", e);
                    }
                }

                //Start process cleanup/initial state
                try{
                    runOne(enable_monMode);
                    stop(PROCESS_AIRODUMP);
                    stop(PROCESS_AIREPLAY);
                    stop(PROCESS_MDK_BF);
                    stop(PROCESS_MDK_DOS);
                    stop(PROCESS_AIRCRACK);
                    stop(PROCESS_REAVER);
                    if(airOnStartup) Airodump.startClean();
                }catch(Exception e){
                    Log.e("HIJACKER/SetupTask", "Exception during post-setup start/stop operations", e);
                }
                Log.d("HIJACKER/SetupTask", "onPostExecute completed successfully");
            }catch(Throwable t){
                Log.e("HIJACKER/SetupTask", "Unhandled exception in onPostExecute", t);
                try{ if(loadingDialog!=null) loadingDialog.dismissAllowingStateLoss(); }catch(Exception ignored){}
            }
         }

        // Start the setup: run onPreExecute on main thread, then doInBackground in background,
        // post onPostExecute back on main thread. A watchdog ensures we don't hang forever.
        void start(){
            if(executor!=null) return;
            executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "SetupTaskThread"));
            final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);

            // Ensure onPreExecute runs on main thread before background work starts.
            final Runnable startWork = () -> executor.submit(() -> {
                Boolean result = doInBackground((Void)null);
                finished.set(true);
                mainHandler.post(() -> onPostExecute(result));
                return null;
            });

            if(Looper.myLooper() == Looper.getMainLooper()){
                onPreExecute();
                startWork.run();
            }else{
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                mainHandler.post(() -> { try{ onPreExecute(); } finally { latch.countDown(); } });
                executor.submit(() -> {
                    try{ latch.await(); }catch(InterruptedException ignored){}
                    Boolean result = doInBackground((Void)null);
                    finished.set(true);
                    mainHandler.post(() -> onPostExecute(result));
                    return null;
                });
            }

            // Watchdog: fail-safe if setup hangs for more than 2 minutes
            mainHandler.postDelayed(() -> {
                if(!finished.get()){
                    Log.e("HIJACKER/SetupTask", "SetupTask watchdog triggered: forcing onPostExecute(false)");
                    try{ executor.shutdownNow(); }catch(Exception ignored){}
                    finished.set(true);
                    mainHandler.post(() -> onPostExecute(false));
                }
            }, 120_000);
        }
    }

    void extract(String filename, String out_dir){
         File f = new File(out_dir, filename);
         if(f.exists()) f.delete();          //Delete file in case it's outdated
         try{
             InputStream in = getResources().getAssets().open(filename);
             FileOutputStream out = new FileOutputStream(f);

             byte[] buf = new byte[BUFFER_SIZE];
             int len;
             while((len = in.read(buf))>0){
                 out.write(buf, 0, len);
             }
             in.close();
             out.close();
             runOne("chmod 755 " + out_dir + "/" + filename);
         }catch(IOException e){
             Log.e("HIJACKER/FileProvider", "Exception copying from assets", e);
         }
     }

    // Wrapper to log start and end of asset extraction; useful to trace long/blocked extractions
    void extractWithLog(String filename, String out_dir){
        Log.d("HIJACKER/SetupTask", "extractWithLog: starting " + filename);
        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "extract-" + filename));
        Future<?> fut = exec.submit(() -> extract(filename, out_dir));
        try{
            fut.get(20, TimeUnit.SECONDS);
            Log.d("HIJACKER/SetupTask", "extractWithLog: finished " + filename);
        }catch(java.util.concurrent.TimeoutException te){
            Log.e("HIJACKER/SetupTask", "extractWithLog: timeout while extracting " + filename);
            fut.cancel(true);
        }catch(Exception e){
            Log.e("HIJACKER/SetupTask", "extractWithLog failed for " + filename, e);
        }finally{
            try{ exec.shutdownNow(); }catch(Exception ignored){}
        }
    }

     public static void _startAireplay(final String str){
        try{
            String cmd = "su -c " + prefix + " " + aireplay_dir + " -D --ignore-negative-one " + str + " " + iface;
            if(debug) Log.d("HIJACKER/_startAireplay", cmd);
            Runtime.getRuntime().exec(cmd);
            last_action = System.currentTimeMillis();
        }catch(IOException e){ Log.e("HIJACKER/Exception", "Caught Exception in _startAireplay() start block: " + e); }
        runInHandler(() -> {
            menu.getItem(3).setEnabled(true);       //Enable 'Stop aireplay' button
            refreshState();
            notification();
        });
    }
    public static void startAireplay(String mac){
        //Disconnect all clients from mac
        aireplay_running = AIREPLAY_DEAUTH;
        _startAireplay("--deauth 0 -a " + mac);
    }
    public static void startAireplay(String target, String client){
        //Disconnect client client from ap target
        aireplay_running = AIREPLAY_DEAUTH;
        _startAireplay("--deauth 0 -a " + target + " -c " + client);
    }
    public static void startAireplayWEP(AP ap){
        //Increase IV generation from ap mac to crack a wep network
        aireplay_running = AIREPLAY_WEP;
        _startAireplay("--fakeauth 0 -a " + ap.mac + (ap.isHidden() ? "" : " -e " + ap.getESSID()));
    }

    //There are 2 mdk3 binaries with different names, so the app can easily stop each one separately
    public static void startBeaconFlooding(String str){
        try{
            String cmd = "su -c " + prefix + " " + mdk3bf_dir + " " + iface + " b -m ";
            if(str!=null) cmd += str;
            if(debug) Log.d("HIJACKER/MDK3", cmd);
            last_mdk = cmd;
            Runtime.getRuntime().exec(cmd);
        }catch(IOException e){ Log.e("HIJACKER/startBF", e.toString()); }
        last_action = System.currentTimeMillis();
        MDKFragment.bf = true;

        runInHandler(() -> {
            refreshState();
            notification();
        });
    }
    public static void startAdos(String str) {
        try{
            String cmd = "su -c " + prefix + " " + mdk3dos_dir + " " + iface + " a -m";
            cmd += str==null ? "" : " -i " + str;
            if(debug) Log.d("HIJACKER/MDK3", cmd);
            last_mdk = cmd;
            Runtime.getRuntime().exec(cmd);
        }catch(IOException e){ Log.e("HIJACKER/startAdos", e.toString()); }
        last_action = System.currentTimeMillis();
        MDKFragment.ados = true;

        runInHandler(() -> {
            refreshState();
            notification();
        });
    }

    public static ArrayList<Integer> getPIDs(String process_name) {
        if(process_name==null) return null;

        Shell shell = getFreeShell();
        ArrayList<Integer> list = new ArrayList<>();
        shell.run(busybox + " pidof " + process_name + "; echo ENDOFPIDOF");
        BufferedReader out = shell.getShell_out();
        String buffer = null;
        try{
            while(buffer==null) buffer = out.readLine();
            while(!buffer.equals("ENDOFPIDOF")){
                String[] temp = buffer.split(" ");
                try{
                    for(String tmp : temp){
                        list.add(Integer.parseInt(tmp));
                    }
                }catch(NumberFormatException e){
                    Log.e("HIJACKER/getPIDs", "Exception: " + e);
                }
                buffer = out.readLine();
            }
        }catch(IOException e){
            Log.e("HIJACKER/getPIDs", "Exception: " + e);
            list = null;
        }
        shell.done();
        return list;
    }
    public static ArrayList<Integer> getPIDs(int pr) {
        switch(pr){
            case PROCESS_AIRODUMP:
                return getPIDs("airodump-ng");
            case PROCESS_AIREPLAY:
                return getPIDs("aireplay-ng");
            case PROCESS_MDK_BF:
                return getPIDs("mdk3bf");
            case PROCESS_MDK_DOS:
                return getPIDs("mdk3dos");
            case PROCESS_AIRCRACK:
                return getPIDs("aircrack-ng");
            case PROCESS_REAVER:
                return getPIDs("reaver");
            default:
                Log.e("HIJACKER/getPIDs", "Method called with invalid pr code");
                throw new UnsupportedOperationException("getPIDs() called with invalid pr code");
            }
    }
    public static void stop(int pr){
        if(debug) Log.d("HIJACKER/stop", "stop(" + pr + ") called");
        last_action = System.currentTimeMillis();
        switch(pr){
            case PROCESS_AIRODUMP:
                Airodump.stop();
                return;
            case PROCESS_AIREPLAY:
                runInHandler(() -> {
                    if(menu!=null) menu.getItem(3).setEnabled(false);
                });
                progress_int = deauthWait;
                runOne(busybox + " kill $(" + busybox + " pidof aireplay-ng)");
                if(is_ap==null && aireplay_running==AIREPLAY_DEAUTH && Airodump.isRunning()){
                    //Aireplay was just deauthenticating so airodump has locked a channel, no more needed
                    Airodump.startClean();
                }
                AP.currentTargetDeauth.clear();
                aireplay_running = 0;
                break;
            case PROCESS_MDK_BF:
                if(currentFragment==FRAGMENT_MDK){
                    runInHandler(() -> MDKFragment.bf_switch.setChecked(false));
                }

                MDKFragment.bf = false;
                runOne(busybox + " kill $(" + busybox + " pidof mdk3bf)");
                break;
            case PROCESS_MDK_DOS:
                if(currentFragment==FRAGMENT_MDK){
                    runInHandler(() -> MDKFragment.ados_switch.setChecked(false));
                }

                MDKFragment.ados = false;
                runOne(busybox + " kill $(" + busybox + " pidof mdk3dos)");
            case PROCESS_AIRCRACK:
                CrackFragment.stopCracking();
                runOne(busybox + " kill $(" + busybox + " pidof aircrack-ng)");
                break;
            case PROCESS_REAVER:
                ReaverFragment.stopReaver();
                runOne(busybox + " kill $(" + busybox + " pidof reaver)");
                break;
            default:
                runOne(busybox + " kill " + pr);
                break;
        }
        runInHandler(() -> {
            refreshState();
            notification();
        });
    }
    public static void stopWPA(){
        wpacheckcont = false;
        if(wpa_thread!=null) wpa_thread.interrupt();
    }

    public static Handler handler = new Handler(Looper.getMainLooper());
    public static void runInHandler(Runnable runnable){
        handler.post(runnable);
    }

    // UI-safe helper methods to avoid exposing Activity views as public static fields.
    // Callers should use these to update UI elements from other classes.
    public static void setProgressIndeterminate(final boolean ind){
        final MainActivity inst = instance;
        if(inst==null) return;
        inst.runOnUiThread(() -> {
            if(inst.progress!=null) inst.progress.setIndeterminate(ind);
        });
    }

    public static void setProgressValue(final int value){
        final MainActivity inst = instance;
        if(inst==null) return;
        inst.runOnUiThread(() -> {
            if(inst.progress!=null) inst.progress.setProgress(value);
        });
    }

    public static void setProgressMax(final int max) {
        final MainActivity inst = instance;
        if(inst==null) return;
        inst.runOnUiThread(() -> {
            if(inst.progress!=null) inst.progress.setMax(max);
        });
    }

    public static void updateCounts() {
        final MainActivity inst = instance;
        if(inst==null) return;
        inst.runOnUiThread(() -> {
            if(inst.ap_count!=null) inst.ap_count.setText(String.format(Locale.getDefault(), "%d", is_ap==null ? Tile.i : 1));
            if(inst.st_count!=null) inst.st_count.setText(String.format(Locale.getDefault(), "%d", Tile.tiles.size() - Tile.i));
        });
    }
    static void loadPreferences() {
        //Load Preferences
        Log.d("HIJACKER/load", "Loading preferences...");

        iface = pref.getString("iface", iface);
        if(!isArchValid()) {
            prefix = pref.getString("prefix", prefix);
        }
        deauthWait = Integer.parseInt(pref.getString("deauthWait", Integer.toString(deauthWait)));
        chroot_dir = pref.getString("chroot_dir", chroot_dir);
        monstart = pref.getBoolean("monstart", monstart);
        enable_monMode = pref.getString("enable_monMode", enable_monMode);
        disable_monMode = pref.getString("disable_monMode", disable_monMode);
        enable_on_airodump = pref.getBoolean("enable_on_airodump", enable_on_airodump);
        show_notif = pref.getBoolean("show_notif", show_notif);
        show_details = pref.getBoolean("show_details", show_details);
        airOnStartup = pref.getBoolean("airOnStartup", airOnStartup);
        debug = pref.getBoolean("debug", debug);
        watchdog = pref.getBoolean("watchdog", watchdog);
        target_deauth = pref.getBoolean("target_deauth", target_deauth);
        try{
            always_cap = pref.getBoolean("always_cap", always_cap);
        }catch(ClassCastException e){
            pref_edit.putBoolean("always_cap", false);
            pref_edit.apply();
        }
        custom_chroot_cmd = pref.getString("custom_chroot_cmd", custom_chroot_cmd);
        cont_on_fail = pref.getBoolean("cont_on_fail", cont_on_fail);
        update_on_startup = pref.getBoolean("update_on_startup", update_on_startup);
        band = Integer.parseInt(pref.getString("band", Integer.toString(band)));
        show_client_count = pref.getBoolean("show_client_count", show_client_count);

        // Use UI-safe helpers
        setProgressMax(deauthWait);
        setProgressValue(deauthWait);
    }
    static void loadAliases() {
        aliases_file = new File(data_path + "/aliases.txt");
        try{
            if(!aliases_file.exists()) {
                aliases_file.createNewFile();
            }else{
                if(debug) Log.d("HIJACKER/loadAliases", "Reading aliases file...");
                try{
                    BufferedReader aliases_out = new BufferedReader(new FileReader(aliases_file));
                    String buffer = aliases_out.readLine();
                    while(buffer!=null){
                        //Line format: 00:11:22:33:44:55 Alias
                        if(buffer.charAt(17)==' ' && buffer.length()>18){
                            String mac = buffer.substring(0, 17);
                            String alias = buffer.substring(18);
                            aliases.put(mac, alias);
                        }else{
                            Log.e("HIJACKER/loadAliases", "Aliases file format error: " + buffer);
                        }
                        buffer = aliases_out.readLine();
                    }
                    aliases_out.close();
                }catch(IOException e){
                    Log.e("HIJACKER/loadAliases1", e.toString());
                }
            }
            aliases_in = new FileWriter(aliases_file, true);
        }catch(Exception e){
            Log.e("HIJACKER/loadAliases2", e.toString());
            aliases_in = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item){
        int id = item.getItemId();
        if(id == android.R.id.home){
            mDrawerLayout.openDrawer(GravityCompat.START);
            return true;
        }else if(id == R.id.reset){
            boolean flag = Airodump.isRunning();
            if(flag) stop(PROCESS_AIRODUMP);
            Tile.clear();
            Tile.onCountsChanged();
            if(flag) Airodump.start();
            return true;
        }else if(id == R.id.stop_run){
            if(Airodump.isRunning()) stop(PROCESS_AIRODUMP);
            else Airodump.start();
            return true;
        }else if(id == R.id.stop_aireplay){
            stop(PROCESS_AIREPLAY);
            return true;
        }else if(id == R.id.filter){
            new FiltersDialog().show(mFragmentManager, "FiltersDialog");
            return true;
        }else if(id == R.id.settings){
            if(currentFragment!=FRAGMENT_SETTINGS){
                FragmentTransaction ft = mFragmentManager.beginTransaction();
                ft.replace(R.id.fragment1, new SettingsFragment());
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                ft.addToBackStack(null);
                ft.commitAllowingStateLoss();
            }
            return true;
        }else if(id == R.id.export){
            new ExportDialog().show(mFragmentManager, "ExportDialog");
            return true;
        }else if(id == R.id.copy_airodump){
            if(last_airodump==null){
                Toast.makeText(this, getString(R.string.no_last_command_available), Toast.LENGTH_SHORT).show();
            }else{
                copy(last_airodump, rootView);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onResume(){
        super.onResume();
        notif_on = false;
        background = false;
        if(mNotificationManager!=null) mNotificationManager.cancelAll();
    }
    @Override
    protected void onPause(){
        super.onPause();
        background = true;
    }
    @Override
    protected void onStop(){
        super.onStop();
        if(show_notif){
            notif_on = true;
            notification();
        }
    }
    @Override
    protected void onDestroy(){
        notif_on = false;
        if(mNotificationManager!=null) mNotificationManager.cancelAll();
         CustomAction.save();
         if(watchdogTask!=null) watchdogTask.requestStop();
         try{
             stop(PROCESS_AIRODUMP);
             stop(PROCESS_AIREPLAY);
             stop(PROCESS_MDK_BF);
             stop(PROCESS_MDK_DOS);
             stop(PROCESS_AIRCRACK);
             stop(PROCESS_REAVER);
             runOne(disable_monMode);
         }catch(Exception e){
             e.printStackTrace();
         }
         RootFile.finish();
         Shell.exitAll();
         stopService(new Intent(this, PersistenceService.class));
         super.onDestroy();
         System.exit(0);
     }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_BACK){
            if(mDrawerLayout.isDrawerOpen(GravityCompat.START)){
                mDrawerLayout.closeDrawers();
            }else if(mFragmentManager.getBackStackEntryCount()>1){
                mFragmentManager.popBackStackImmediate();
            }else{
                CustomDialog customDialog = new CustomDialog();
                customDialog.setTitle(getString(R.string.exit_dialog_title));
                customDialog.setMessage(getString(R.string.exit_dialog_message));
                customDialog.setPositiveButton(getString(R.string.exit), () -> {
                    show_notif = false;
                    finish();
                });
                customDialog.setNegativeButton(getString(R.string.cancel), null);
                customDialog.show(mFragmentManager, "CustomDialog for exit");
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MainActivity.menu = menu;
        getMenuInflater().inflate(R.menu.toolbar, menu);
        if(airOnStartup){
            menu.getItem(1).setIcon(R.drawable.stop_drawable);
            menu.getItem(1).setTitle(R.string.stop);
        }
        menu.getItem(3).setEnabled(false);
        return true;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==0){
            //The one and only request this app sends
            synchronized(this){
                this.notify();
            }
        }else if(requestCode==1){
            // Notification permission request
            boolean granted = false;
            for(int result : grantResults){
                if(result == PackageManager.PERMISSION_GRANTED){
                    granted = true;
                    break;
                }
            }
            if(granted){
                // Permission granted, you can show a message or take action if needed
                Log.i("HIJACKER/Permissions", "Notification permission granted");
            }else{
                // Permission denied, handle accordingly
                Log.i("HIJACKER/Permissions", "Notification permission denied");
            }
        }
    }
    @SuppressLint("MissingSuperCall")
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        //No call for super(), avoid IllegalStateException on FragmentManagerImpl.checkStateLoss.
    }

    class MyListAdapter extends ArrayAdapter<Tile> {
        MyListAdapter() {
            super(MainActivity.this, R.layout.listitem);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            // get a view to work with
            View itemview = convertView;
            if(itemview==null) {
                itemview = getLayoutInflater().inflate(R.layout.listitem, parent, false);
            }

            // find the item to work with
            Tile current = Tile.tiles.get(position);

            TextView upperLeft = itemview.findViewById(R.id.upperLeft);
            upperLeft.setText(current.device.upperLeft);
            upperLeft.setTextColor(ContextCompat.getColor(getContext(), current.device.isMarked ? R.color.colorAccent : android.R.color.white));

            TextView lowerLeft = itemview.findViewById(R.id.lowerLeft);
            lowerLeft.setText(current.device.lowerLeft);

            TextView lowerRight = itemview.findViewById(R.id.lowerRight);
            lowerRight.setText(current.device.lowerRight);

            TextView upperRight = itemview.findViewById(R.id.upperRight);
            upperRight.setText(current.device.upperRight);

            //Image and count views
            ImageView iv = itemview.findViewById(R.id.iv);
            TextView icon_count_view = itemview.findViewById(R.id.icon_count_view);
            if (current.device instanceof AP) {
                if (((AP)current.device).isHidden) iv.setImageResource(R.drawable.ap_hidden);
                else iv.setImageResource(R.drawable.ap2);

                if (show_client_count) {
                    icon_count_view.setText(String.format(Locale.getDefault(), "%d", ((AP) (current.device)).clients.size()));
                    icon_count_view.setVisibility(View.VISIBLE);
                } else {
                    icon_count_view.setVisibility(View.GONE);
                }
            } else {
                iv.setImageResource(R.drawable.st2);

                icon_count_view.setVisibility(View.GONE);
            }

            return itemview;
        }

        @Override
        public int getCount(){
            return Tile.tiles.size();
        }
    }
    class CustomActionAdapter extends ArrayAdapter<CustomAction>{
        CustomActionAdapter(){
            super(MainActivity.this, R.layout.listitem);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent){
            // get a view to work with
            View itemview = convertView;
            if(itemview==null){
                itemview = getLayoutInflater().inflate(R.layout.listitem, parent, false);
            }

            // find the item to work with
            CustomAction currentItem = CustomAction.cmds.get(position);

            TextView upperLeft = itemview.findViewById(R.id.upperLeft);
            upperLeft.setText(currentItem.getTitle());

            TextView lowerLeft = itemview.findViewById(R.id.lowerLeft);
            lowerLeft.setText(currentItem.getStartCmd());

            TextView lowerRight = itemview.findViewById(R.id.lowerRight);
            lowerRight.setText("");

            TextView upperRight = itemview.findViewById(R.id.upperRight);
            upperRight.setText("");

            TextView icon_count_view = itemview.findViewById(R.id.icon_count_view);
            icon_count_view.setVisibility(View.GONE);

            //Image
            ImageView iv = itemview.findViewById(R.id.iv);
            if(currentItem.getType()==TYPE_ST){
                iv.setImageResource(R.drawable.st2);
            }else{
                iv.setImageResource(R.drawable.ap2);
            }

            return itemview;
        }

        @Override
        public int getCount(){
            return CustomAction.cmds.size();
        }
    }
    class FileExplorerAdapter extends ArrayAdapter<RootFile>{
        FileExplorerAdapter(){
            super(MainActivity.this, R.layout.explorer_item);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent){
            // get a view to work with
            View itemview = convertView;
            if(itemview==null){
                itemview = getLayoutInflater().inflate(R.layout.explorer_item, parent, false);
            }

            // find the item to work with
            RootFile currentItem = FileExplorerDialog.list.get(position);

            TextView firstText = itemview.findViewById(R.id.explorer_item_tv);
            firstText.setText(currentItem.getName());

            //Image
            ImageView iv = itemview.findViewById(R.id.explorer_iv);
            if(currentItem.isFile()){
                iv.setImageResource(R.drawable.file);
            }else{
                iv.setImageResource(R.drawable.folder);
            }

            return itemview;
        }

        @Override
        public int getCount(){
            return FileExplorerDialog.list.size();
        }
    }
    public void onAPStats(View v){ new StatsDialog().show(mFragmentManager, "StatsDialog"); }
    public void onCrack(View v){
        //Clicked crack with isolated ap
        if(wpa_thread.isAlive()){
            //Clicked stop
            stopWPA();
            Airodump.startClean(is_ap);
        }else{
            //Clicked crack
            is_ap.crack();
            ((TextView)v).setText(R.string.stop);
        }
    }
    public void onDisconnect(View v){
        //Clicked disconnect all with isolated ap
        stop(PROCESS_AIREPLAY);
        startAireplay(is_ap.mac);
        Toast.makeText(this, R.string.disconnect_started, Toast.LENGTH_SHORT).show();
    }
    public void onDos(View v){
        //Clicked dos with isolated ap
        if(MDKFragment.ados){
            ((TextView)v).setText(R.string.dos);
            stop(PROCESS_MDK_DOS);
            MDKFragment.ados = false;
        }else{
            ((TextView)v).setText(R.string.stop);
            startAdos(is_ap.mac);
        }
    }
    public void onCopy(View v){
        copy(((TextView)v).getText().toString(), v);
    }
    static void copy(String str, View view){
        clipboard.setPrimaryClip(ClipData.newPlainText("label", str));
        if(view!=null)
            Toast.makeText(view.getContext(), view.getContext().getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
    }
    static void notification(){
        if(notif_on && show_notif && notif!=null){
            if(show_details){
                String str;
                if(is_ap==null) str = "APs: " + Tile.i + " | STs: " + (Tile.tiles.size() - Tile.i);
                else str = is_ap.getESSID() + " | STs: " + (Tile.tiles.size() - Tile.i);

                if(aireplay_running==AIREPLAY_DEAUTH) str += " | Aireplay deauthenticating...";
                else if(aireplay_running==AIREPLAY_WEP) str += " | Aireplay replaying for wep...";
                if(wpa_thread!=null){
                    if(wpa_thread.isAlive()) str += " | WPA cracking...";
                }
                if(MDKFragment.bf) str += " | MDK3 Beacon Flooding...";
                if(MDKFragment.ados) str += " | MDK3 Authentication DoS...";
                if(ReaverFragment.isRunning()) str += " | Reaver running...";
                if(CrackFragment.isRunning()) str += " | Cracking .cap file...";
                if(CustomActionFragment.isRunning()) str += " | Running action " + CustomActionFragment.selectedAction.getTitle() + "...";

                notif.setContentText(str);
            }else notif.setContentText(null);
            if (mNotificationManager != null) mNotificationManager.notify(0, notif.build());
        }else{
            if (mNotificationManager != null) mNotificationManager.cancel(0);
        }
    }
    static void isolate(String mac) {
        is_ap = getAPByMac(mac);
        if(is_ap!=null){
            IsolatedFragment.exit_on = mFragmentManager.getBackStackEntryCount();
            FragmentTransaction ft = mFragmentManager.beginTransaction();
            ft.replace(R.id.fragment1, new IsolatedFragment());
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.addToBackStack(null);
            ft.commitAllowingStateLoss();
        }
        Tile.filter();
        if(debug){
            if(is_ap==null) Log.d("HIJACKER/Main", "No AP isolated");
            else Log.d("HIJACKER/Main", "AP with MAC " + mac + " isolated");
        }
    }
    static void refreshState() {
        //refresh overflow icon to show what is running
        final int state = (Airodump.isRunning() ? 1 : 0)
                + (aireplay_running!=0 ? 2 : 0)
                + ((MDKFragment.bf || MDKFragment.ados) ? 4 : 0);
        final MainActivity inst = instance;
        if(inst==null) return;
        inst.runOnUiThread(() -> {
            if(inst.toolbar!=null) inst.toolbar.setOverflowIcon(overflow[state]);
            if(!(ReaverFragment.isRunning() || CrackFragment.isRunning() || (inst.wpa_thread!=null && inst.wpa_thread.isAlive()))){
                if(inst.progress!=null){
                    inst.progress.setIndeterminate(false);
                    inst.progress.setProgress(deauthWait);
                }
            }
        });
    }
    void refreshDrawer(){
        navigationView.getMenu().findItem(currentFragment).setChecked(true);
        actionBar.setTitle(navTitlesMap.get(currentFragment));
    }
    static String getManuf(String mac){
        mac = trimMac(mac);
        if(manufHashMap==null) return "Unknown Manufacturer";
        String manuf = manufHashMap.get(mac);
        if(manuf==null) return "Unknown Manufacturer";
        else return manuf;
    }
    static String getLastLine(BufferedReader out, String end){
        //Returns the last line printed in out BEFORE end. If no other line is present, end is returned.
        String lastline=null, buffer = null;
        try{
            while(buffer==null) buffer = out.readLine();
            lastline = buffer;
            while(!end.equals(buffer) && buffer!=null){
                lastline = buffer;
                buffer = out.readLine();
            }
        }catch(IOException e){ Log.e("HIJACKER/Exception", "Exception in getLastLine: " + e); }

        return lastline;
    }
    static String getLastSeen(long lastseen){
        String str = "";
        long diff = System.currentTimeMillis() - lastseen;
        if(diff < 1000) return "Just now";
        diff = diff/1000; //diff is now seconds
        if(diff/60>0){
            //minutes = diff/60
            str += diff/60 + " minute" + (diff/60 > 1 ? "s " : " ");
            diff = diff%60;
        }
        if(diff > 0){
            str += diff + " second" + (diff > 1 ? "s " : " ");
        }
        str += "ago";
        return str;
    }
   static String getFixed(String text, int size){
        /*Returns a string of fixed length (size) that contains spaces followed by a text
           <--  size  -->
          |     ...  text|
        */
        if(text==null) return null;
        if(text.length() > size){
            text = text.substring(0, size);
        }
        StringBuilder str = new StringBuilder();
        for(int i=0;i < size-text.length();i++){
            str.append(" ");
        }
        return str + text;
    }
    static int checkChroot(){
        boolean bin = false, dir;

        Shell shell = getFreeShell();
        shell.run("echo $PATH; echo ENDOFPATH");
        String path = getLastLine(shell.getShell_out(), "ENDOFPATH");
        shell.done();
        String[] paths = path.split(":");
        for(String temp : paths){
            if(new RootFile(temp + "/bootkali_init").exists()){
                bin = true;
                bootkali_init_bin = temp + "/bootkali_init";
                break;
            }
        }
        if(!bin) {
            if(new RootFile(NETHUNTER_BOOTKALI_BASH).exists()) {
                bin = true;
                bootkali_init_bin = NETHUNTER_BOOTKALI_BASH;
            }
        }

        dir = new RootFile(chroot_dir).exists() && new RootFile(chroot_dir + "/bin/bash").exists();

        if(bin && dir) return CHROOT_FOUND;
        else if(!bin && !dir) return CHROOT_BOTH_MISSING;
        else if(dir) return CHROOT_BIN_MISSING;
        else return CHROOT_DIR_MISSING;
    }
    void checkForUpdate(final boolean showMessages) {
        //Can be called from any thread, blocks until the job is finished
        if(showMessages){
            runInHandler(() -> progress.setIndeterminate(true));
        }

        try{
            HttpsURLConnection connection = (HttpsURLConnection) (new URL(RELEASES_LINK).openConnection());
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            JsonReader reader = new JsonReader(new InputStreamReader(connection.getInputStream()));
            reader.beginArray();
            if(!reader.hasNext()) {
                //No releases
                Log.e("HIJACKER/UpdateCheck", "No releases found");
                throw new Exception();
            }

            String latestName = null, latestLink = null, latestBody = null;

            reader.beginObject();
            //Run through all the names in the release array
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "tag_name":
                        latestName = reader.nextString();
                        break;
                    case "body":
                        latestBody = reader.nextString();
                        if (latestBody.isEmpty())
                            latestBody = null;
                        break;
                    case "assets":
                        //assets is an array
                        reader.beginArray();
                        reader.beginObject();
                        //Run through all the names in the 'assets' array
                        while (reader.hasNext()) {
                            field = reader.nextName();
                            if (field.equals("browser_download_url")) {
                                latestLink = reader.nextString();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                        reader.endArray();
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.close();

            if (!versionName.equals(latestName) && latestLink!=null) {
                String text = getString(R.string.update_text) + "\n\n";
                text += getString(R.string.latest_version) + " " + latestName + "\n";
                text += getString(R.string.current_version) + " " + versionName + "\n";
                if (latestBody!=null) {
                    text += "\nExtra Information:\n" + latestBody;
                }
                final String link = latestLink;

                final CustomDialog customDialog = new CustomDialog();
                customDialog.setTitle(getString(R.string.update_title));
                customDialog.setMessage(text);
                customDialog.setPositiveButton(getString(R.string.download), new Runnable(){
                    @Override
                    public void run(){
                        String filename = link.substring(link.lastIndexOf('/') + 1);

                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(link));
                        request.setTitle(filename);
                        // allowScanningByMediaScanner is deprecated; call it only on older Android versions to retain behavior
                        allowScanningByMediaScannerIfNeeded(request);
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

                        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                        if(manager!=null) manager.enqueue(request);
                    }
                });
                customDialog.setNeutralButton(getString(R.string.cancel), null);

                runInHandler(() -> customDialog.show(mFragmentManager, "CustomDialog for update"));
            } else {
                if (showMessages) Snackbar.make(rootView, getString(R.string.already_on_latest), Snackbar.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("HIJACKER/update", e.toString());
            if(showMessages) Snackbar.make(rootView, getString(R.string.unknown_error), Snackbar.LENGTH_SHORT).show();
        } finally {
            if (showMessages) runInHandler(() -> progress.setIndeterminate(false));
        }
    }
    boolean internetAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connectivityManager==null) return false;
        // Use modern APIs on API 23+; fall back to older method on older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) return false;
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            // Deprecated on newer APIs but necessary for older devices (minSdk 21).
            @SuppressWarnings("deprecation")
            Network[] networks = connectivityManager.getAllNetworks();
            for (Network n : networks) {
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(n);
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true;
            }
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static void allowScanningByMediaScannerIfNeeded(DownloadManager.Request request) {
        // allowScanningByMediaScanner() is deprecated on newer SDKs. Only call it on older SDKs
        // where the behavior (making the downloaded file visible to MediaStore) was done via this call.
        if (request == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ){
            request.allowScanningByMediaScanner();
        }
    }

    static String findFirmwarePath(Shell shell) {
        // Blocking function, don't run on main thread. Returns the first matching firmware path or null.
        boolean createdShell = false;
        if (shell==null) {
            createdShell = true;
            shell = getFreeShell();
        }

        String[] dirs = {"/system", "/vendor", "/system/etc"};
        String[] fw_names = {"fw_bcmdhd.bin", "bcmdhd_sta.bin"};

        String firmware = null;
        for (int i=0;i<dirs.length && firmware==null;i++) {
            for (String fw_name : fw_names) {
                shell.run(busybox + " find " + dirs[i] + " -type f -name \"" + fw_name + "\"; echo ENDOFFIND");
                BufferedReader out = shell.getShell_out();
                try {
                    String result = out.readLine();
                    while (result!=null) {
                        if (result.equals("ENDOFFIND")) break;
                        // Skip obvious backup paths
                        if (!result.contains("/bac/") && !result.contains("backup")) {
                            firmware = result;
                            break;
                        }
                        result = out.readLine();
                    }
                } catch(IOException e) {
                    Log.e("HIJACKER/FFPATH", "Exception while searching for firmware", e);
                }
                if (firmware!=null) break;
            }
        }

        if (createdShell) {
            shell.done();
        }
        return firmware;
    }

    static boolean isArchValid() {
        // arch is set earlier from System.getProperty("os.arch").
        if (arch==null) return false;
        return arch.matches("(.*)arm(.*)") || arch.matches("aarch64");
    }

    static {
        System.loadLibrary("native-lib");
    }

    static boolean createReport(File report, String filesPath, String stackTrace, Process shell){
        // Create a simple text report containing app/device info, optional stack trace, a directory listing
        // and a short logcat dump. Returns true on success.
        if(report == null) return false;
        try (PrintWriter pw = new PrintWriter(new FileWriter(report))){
            pw.println("Hijacker report - " + new Date().toString());
            pw.println("Version: " + (versionName == null ? "unknown" : versionName) + " (" + versionCode + ")");
            pw.println("Device: " + (deviceModel == null ? "unknown" : deviceModel));
            pw.println("Arch: " + (arch == null ? "unknown" : arch));
            pw.println("Busybox: " + (busybox == null ? "unknown" : busybox));
            pw.println();

            if(stackTrace != null){
                pw.println("---- Stack trace ----");
                pw.println(stackTrace);
                pw.println();
            }

            if(filesPath != null){
                pw.println("---- Files in: " + filesPath + " ----");
                try{
                    File dir = new File(filesPath);
                    if(dir.exists() && dir.isDirectory()){
                        String[] list = dir.list();
                        if(list!=null){
                            for(String f : list){
                                pw.println(f);
                            }
                        }else{
                            pw.println("(empty)");
                        }
                    }else{
                        pw.println("(path not found)");
                    }
                }catch(Exception e){
                    pw.println("(error listing files: " + e.toString() + ")");
                }
                pw.println();
            }

            pw.println("---- Recent logcat (truncated) ----");
            try{
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time"});
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                int lines = 0;
                // limit the amount of logcat written to keep report reasonable
                while((line = br.readLine())!=null && lines < 1000){
                    pw.println(line);
                    lines++;
                }
                br.close();
                p.destroy();
            }catch(Exception e){
                pw.println("(failed to collect logcat: " + e.toString() + ")");
            }

            pw.flush();
            return true;
        }catch(IOException e){
            Log.e("HIJACKER/createReport", "Exception creating report", e);
            return false;
        }
    }
}
